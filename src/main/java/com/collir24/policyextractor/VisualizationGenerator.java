package com.collir24.policyextractor;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.filters.api.FilterController;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.AutoLayout;
import org.gephi.layout.plugin.ForceLayoutData;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.types.EdgeColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.RankingController;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.openide.util.Lookup;

public class VisualizationGenerator {
	private static final Logger LOGGER = Logger
			.getLogger(VisualizationGenerator.class.getName());

	private List<String> PERMISSION_LIST = new ArrayList<String>();
	private final Writer gexfWriter;
	private final File gexfFile;
	private final File outputFile;

	/**
	 * 
	 * @param file
	 *            the location of the output image.
	 * @throws IOException
	 */
	protected VisualizationGenerator(File file) {
		outputFile = file;
		try {
			gexfFile = new File(outputFile.getAbsolutePath().replace(".png",
					".gexf"));
			gexfWriter = new BufferedWriter(new FileWriter(gexfFile));
		} catch (IOException e) {
			throw new IllegalArgumentException("Not a valid PNG file: "
					+ file.getAbsolutePath(), e);
		}
	}

	protected void generateVisualization(List<ModulePermissions> permissionList) {
		generateGexf(permissionList);
		generateSvg();
	}

	protected void generateGexf(List<ModulePermissions> permissionList) {
		XMLOutputter xmlOut = new XMLOutputter(Format.getCompactFormat());
		Element root = new Element("gexf", "http://www.gexf.net/1.2draft");
		root.setAttribute("version", "1.2");
		Element graph = new Element("graph", "http://www.gexf.net/1.2draft");
		Element attributes = new Element("attributes",
				"http://www.gexf.net/1.2draft");
		Element nodes = new Element("nodes", "http://www.gexf.net/1.2draft");
		Element edges = new Element("edges", "http://www.gexf.net/1.2draft");
		attributes.setAttribute("class", "node");
		Element permissionAttribute = new Element("attribute",
				"http://www.gexf.net/1.2draft");
		permissionAttribute.setAttribute("id", "0");
		permissionAttribute.setAttribute("title", "type");
		permissionAttribute.setAttribute("type", "string");
		Element defaultAttributeElement = new Element("default",
				"http://www.gexf.net/1.2draft");
		defaultAttributeElement.setText("module");
		permissionAttribute.addContent(defaultAttributeElement);
		attributes.addContent(permissionAttribute);

		graph.setAttribute("defaultedgetype", "directed");
		root.addContent(graph);
		graph.addContent(attributes);
		graph.addContent(nodes);
		graph.addContent(edges);

		addModulesAndPermsToVis(nodes, edges, permissionList);

		try {
			xmlOut.output(root, gexfWriter);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Problem writing doc", e);
		}
	}

	private void addModulesAndPermsToVis(Element nodes, Element edges,
			List<ModulePermissions> permissionList) {
		int nodeId = 10000;
		int edgeId = Integer.MAX_VALUE;
		for (ModulePermissions perms : permissionList) {
			Element moduleElement = new Element("node",
					"http://www.gexf.net/1.2draft");
			moduleElement.setAttribute("id", Integer.toString(nodeId));
			moduleElement.setAttribute("label", perms.getModuleName());

			Element redVisElement = new Element("color",
					"http://www.gexf.net/1.2draft/viz");
			redVisElement.setAttribute("r", Integer.toString(76));
			redVisElement.setAttribute("g", Integer.toString(205));
			redVisElement.setAttribute("b", Integer.toString(205));

			moduleElement.addContent(redVisElement);
			nodes.addContent(moduleElement);

			for (String s : getPermSet(perms)) {
				int permNodeId = PERMISSION_LIST.indexOf(s);
				if (permNodeId == -1) {
					PERMISSION_LIST.add(s);
					permNodeId = PERMISSION_LIST.indexOf(s);

					Element permElement = new Element("node",
							"http://www.gexf.net/1.2draft");
					permElement
							.setAttribute("id", Integer.toString(permNodeId));
					permElement.setAttribute("label", s);

					Element blueVisElement = new Element("color",
							"http://www.gexf.net/1.2draft/viz");
					blueVisElement.setAttribute("r", Integer.toString(205));
					blueVisElement.setAttribute("g", Integer.toString(76));
					blueVisElement.setAttribute("b", Integer.toString(76));

					permElement.addContent(blueVisElement);
					nodes.addContent(permElement);
				}
				Element permissionLink = new Element("edge",
						"http://www.gexf.net/1.2draft");
				permissionLink.setAttribute("id", Integer.toString(edgeId));
				permissionLink.setAttribute("source", Integer.toString(nodeId));
				permissionLink.setAttribute("target",
						Integer.toString(permNodeId));
				edges.addContent(permissionLink);
				edgeId--;
			}
			nodeId++;
		}
	}

	private static Set<String> getPermSet(ModulePermissions perms) {
		Set<String> permSet = new HashSet<String>();
		List<ModulePermission> modulePermissionList = perms.getPermissions();
		for (ModulePermission permission : modulePermissionList) {
			for (Permission p : permission.getList()) {
				permSet.add(getPermissionKey(p));
			}
		}
		return permSet;
	}

	private static String getPermissionKey(Permission permission) {
		StringBuilder keyBuilder = new StringBuilder();
		return keyBuilder.append(permission.getPermission()).append(".")
				.append(permission.getTarget()).toString();
	}

	private void generateSvg() {
		ProjectController pc = Lookup.getDefault().lookup(
				ProjectController.class);
		pc.newProject();
		Workspace workspace = pc.getCurrentWorkspace();

		AttributeModel attributeModel = Lookup.getDefault()
				.lookup(AttributeController.class).getModel();
		GraphModel graphModel = Lookup.getDefault()
				.lookup(GraphController.class).getModel();
		PreviewModel model = Lookup.getDefault()
				.lookup(PreviewController.class).getModel();
		ImportController importController = Lookup.getDefault().lookup(
				ImportController.class);
		FilterController filterController = Lookup.getDefault().lookup(
				FilterController.class);
		RankingController rankingController = Lookup.getDefault().lookup(
				RankingController.class);

		// Import file
		Container container;
		try {
			assert gexfFile.exists();
			container = importController.importFile(gexfFile);
			container.getLoader().setEdgeDefault(EdgeDefault.DIRECTED);
		} catch (Exception ex) {
			throw new IllegalStateException(
					"Couldn't import GEXF file from path: "
							+ gexfFile.getAbsolutePath(), ex);
		}
		importController.process(container, new DefaultProcessor(), workspace);
		DirectedGraph graph = graphModel.getDirectedGraph();
		
		ForceAtlas2 layout = new ForceAtlas2(new ForceAtlas2Builder());
		layout.setGraphModel(graphModel);
		layout.setAdjustSizes(true);
		layout.setGravity(5.0);
		
		layout.initAlgo();
		for (int i = 0; i < 20 && layout.canAlgo(); i++) {
		    layout.goAlgo();
		}
		layout.endAlgo();
		
		// Preview
		model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS,
				Boolean.TRUE);
		model.getProperties().putValue(PreviewProperty.EDGE_COLOR,
				new EdgeColor(Color.GRAY));
		model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS,
				new Float(0.1f));
		model.getProperties().putValue(
				PreviewProperty.NODE_LABEL_FONT,
				model.getProperties()
						.getFontValue(PreviewProperty.NODE_LABEL_FONT)
						.deriveFont(8));

		// Export
		ExportController ec = Lookup.getDefault()
				.lookup(ExportController.class);
		try {
			ec.exportFile(outputFile);
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}
	}
}
