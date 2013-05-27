/*
 * Copyright 2013 Robert Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.collir24.policyextractor;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

/**
 * 
 * 
 */
@Mojo(name = "policyextractor", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class PolicyExtractorReport extends AbstractMavenReport {
	private static final Logger LOGGER = Logger
			.getLogger(PolicyExtractorReport.class.getName());

	/**
	 * The output directory for the report. Note that this parameter is only
	 * evaluated if the goal is run directly from the command line. If the goal
	 * is run indirectly as part of a site generation, the output directory
	 * configured in Maven Site Plugin is used instead.
	 */
	@Parameter(defaultValue = "${project.reporting.outputDirectory}", required = true)
	private File outputDirectory;

	/**
	 * Specifies the path and filename to save the policyextractor output. The
	 * format of the output file is determined by the
	 * <code>outputFileFormat</code> parameter.
	 */
	@Parameter(property = "policyextractor.output.file", defaultValue = "${project.build.directory}/policyextractor-report.xml")
	private File outputFile;

	/**
	 * The Maven Project Object.
	 */
	@Component
	protected MavenProject project;

	/**
	 * Link the violation line numbers to the source xref. Will link
	 * automatically if Maven JXR plugin is being used.
	 * 
	 * @since 2.1
	 */
	@Parameter(property = "linkXRef", defaultValue = "true")
	private boolean linkXRef;

	/**
	 * Location of the Xrefs to link to.
	 */
	@Parameter(defaultValue = "${project.reporting.outputDirectory}/xref")
	private File xrefLocation;

	/**
     */
	@Component
	private Renderer siteRenderer;

	@Override
	protected void executeReport(Locale locale) throws MavenReportException {
		ResourceBundle localizedResources = getBundle(locale);
		Set<String> policySet = new TreeSet<String>();
		Sink sink = getSink();
		sink.head();
		sink.title();
		sink.text(localizedResources.getString("report.pagetitle"));
		sink.title_();
		sink.head_();
		sink.body();
		MavenProject project = getProject();
		sink.sectionTitle1();
		sink.text(localizedResources.getString("report.sectiontitle"));
		sink.sectionTitle1_();
		Build build = project.getBuild();

		sink.sectionTitle2();
		sink.text(MessageFormat.format(
				localizedResources.getString("report.reportfor"),
				project.getName()));
		sink.sectionTitle2_();
		sink.paragraph();
		sink.text(localizedResources.getString("report.local.description"));
		sink.lineBreak();
		sink.italic();
		sink.text(localizedResources.getString("report.disclaimer"));
		sink.italic_();
		sink.paragraph_();
		Set<String> localProjectPolicySet = generateProjectPermissions(sink,
				project, build);
		generatePolicy(localProjectPolicySet, sink, null);
		policySet.addAll(localProjectPolicySet);

		sink.sectionTitle2();
		sink.text(localizedResources.getString("report.allpermissions.title"));
		sink.sectionTitle2_();
		sink.paragraph();
		sink.text(localizedResources
				.getString("report.dependencies.description"));
		sink.paragraph_();

		@SuppressWarnings("unchecked")
		Set<Artifact> artefacts = project.getArtifacts();
		for (Artifact a : artefacts) {
			if (a.getScope().equals("test")) {
				continue;
			}
			JarFile jf;
			try {
				jf = new JarFile(a.getFile().getAbsolutePath());
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Can't open file at: "
						+ a.getFile().getAbsolutePath(), e);
				continue;
			}
			localProjectPolicySet = generateArtifactPermissions(sink, a, jf);
			generatePolicy(localProjectPolicySet, sink, a.getFile().getName());
			policySet.addAll(localProjectPolicySet);
		}
		sink.sectionTitle2();
		sink.text(localizedResources
				.getString("report.generatedpolicy.title"));
		sink.sectionTitle2_();
		sink.paragraph();
		sink.text(localizedResources
				.getString("report.generatedpolicy.description"));
		sink.paragraph_();
		generatePolicy(policySet, sink, null);
		sink.body_();
		sink.flush();
		sink.close();
	}

	private static void generatePolicy(Set<String> policySet, Sink sink,
			String filename) {
		sink.verbatim(true);
		StringBuilder sb = new StringBuilder();
		if (filename != null && !filename.isEmpty()) {
			sb.append("grant codebase ").append("\"file:/").append(filename)
					.append("\"").append(" {\n");
		} else {
			sb.append("grant {\n");
		}
		for (String policyLine : policySet) {
			sb.append("\t").append(policyLine).append("\n");
		}
		sb.append("};\n");
		sink.monospaced();
		sink.text(sb.toString());
		sink.monospaced_();
		sink.verbatim_();
	}

	/**
	 * Get the permissions required for the artifact and write them out to the
	 * sink.
	 * 
	 * @param sink
	 *            the doxia sink
	 * @param a
	 *            the artifact
	 * @param jf
	 *            the jar file for the artifact
	 * @return
	 */
	private Set<String> generateArtifactPermissions(Sink sink, Artifact a,
			JarFile jf) {
		Set<String> policySet = new TreeSet<String>();
		sink.sectionTitle3();
		sink.text("Permissions for " + a.getArtifactId());
		sink.sectionTitle3_();
		ModulePermissions mps = Extract.examineFile(jf);
		sink.table();
		sink.tableRow();
		sink.tableHeaderCell();
		sink.text("Class");
		sink.tableHeaderCell_();
		sink.tableHeaderCell();
		sink.text("Line");
		sink.tableHeaderCell_();
		sink.tableHeaderCell();
		sink.text("Permission");
		sink.tableHeaderCell_();
		sink.tableRow_();
		for (ModulePermission mp : mps.getPermissions()) {
			sink.tableRow();
			sink.tableCell();
			sink.monospaced();
			sink.text(mp.getClassName().replace('/', '.'));
			sink.monospaced_();
			sink.tableCell_();
			sink.tableCell();
			sink.text(Integer.toString(mp.getLine()));
			sink.tableCell_();
			sink.tableCell();
			sink.monospaced();
			for (String policy : mp.getPolicy()) {
				sink.text(policy);
				sink.lineBreak();
			}
			sink.monospaced_();
			sink.tableCell_();
			sink.tableRow_();
			policySet.addAll(mp.getPolicy());
		}
		sink.tableCaption();
		sink.text(mps.getModuleName());
		sink.tableCaption_();
		sink.table_();
		return policySet;
	}

	/**
	 * Gets the permissions required for the source of this maven project and
	 * writes them to the report sink
	 * 
	 * @param sink
	 *            a Doxia sink to write to
	 * @param project
	 *            the Maven project
	 * @param build
	 *            the build details of the maven project
	 * @return A set of permissions required by this project
	 * @throws IOException
	 */
	private Set<String> generateProjectPermissions(Sink sink,
			MavenProject project, Build build) {
		Set<String> policySet = new TreeSet<String>();
		if (project.getPackaging().equals("war")
				|| project.getPackaging().equals("jar")) {
			String packagedPath = getProjectPackagePath(project, build);
			JarFile jf;
			try {
				jf = new JarFile(packagedPath);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Can't open file at: " + packagedPath,
						e);
				return policySet;
			}

			ModulePermissions mps = Extract.examineFile(jf);
			sink.table();
			sink.tableRow();
			sink.tableHeaderCell();
			sink.text("Class");
			sink.tableHeaderCell_();
			sink.tableHeaderCell();
			sink.text("Line");
			sink.tableHeaderCell_();
			sink.tableHeaderCell();
			sink.text("Permission");
			sink.tableHeaderCell_();
			sink.tableRow_();
			for (ModulePermission mp : mps.getPermissions()) {
				sink.tableRow();
				sink.tableCell();
				sink.monospaced();
				sink.text(mp.getClassName().replace('/', '.'));
				sink.monospaced_();
				sink.tableCell_();
				sink.tableCell();
				if (linkXRef) {
					StringBuilder linkBuilder = new StringBuilder();
					linkBuilder.append(xrefLocation).append("/")
							.append(mp.getClassName()).append(".html#")
							.append(mp.getLine());
					sink.link(linkBuilder.toString());
					sink.text(Integer.toString(mp.getLine()));
					sink.link_();
				} else {
					sink.text(Integer.toString(mp.getLine()));
				}
				sink.tableCell_();
				sink.tableCell();
				sink.monospaced();
				for (String policy : mp.getPolicy()) {
					sink.text(policy);
					sink.lineBreak();
				}
				sink.monospaced_();
				sink.tableCell_();
				sink.tableRow_();
				policySet.addAll(mp.getPolicy());
			}

			sink.tableCaption();
			sink.text(mps.getModuleName());
			sink.tableCaption_();
			sink.table_();
		}
		return policySet;
	}

	private static String getProjectPackagePath(MavenProject project,
			Build build) {
		StringBuilder sb = new StringBuilder();
		String packagedPath = sb.append(build.getDirectory())
				.append(File.separatorChar).append(build.getFinalName())
				.append('.').append(project.getPackaging()).toString();
		return packagedPath;
	}

	@Override
	protected String getOutputDirectory() {
		return outputDirectory.getAbsolutePath();
	}

	@Override
	protected MavenProject getProject() {
		return project;
	}

	@Override
	protected Renderer getSiteRenderer() {
		return siteRenderer;
	}

	private static ResourceBundle getBundle(Locale locale) {
		return ResourceBundle.getBundle(PolicyExtractorReport.class.getName(),
				locale);

	}

	public String getDescription(Locale locale) {
		ResourceBundle localizedResources = getBundle(locale);
		return localizedResources.getString("report.description");
	}

	public String getName(Locale locale) {
		ResourceBundle localizedResources = getBundle(locale);
		return localizedResources.getString("report.name");
	}

	public String getOutputName() {
		return "policyextractor";
	}

}
