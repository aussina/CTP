/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.TransferHandler;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.w3c.dom.*;

public class ConfigPanel extends BasePanel {

	static final String templateFilename = "ConfigurationTemplates.xml";

	MenuPane menuPane;
	TreePane treePane;
	DataPane dataPane;
	JSplitPane split;
	JScrollPane jspData;
	boolean loaded = false;

	Hashtable<String,Template> templateTable = null;
	Template server = null;
	Template pipeline = null;
	LinkedList<Template> plugins = null;
	LinkedList<Template> importServices = null;
	LinkedList<Template> processors = null;
	LinkedList<Template> storageServices = null;
	LinkedList<Template> exportServices = null;
	Hashtable<String,String> defaultHelpText = new Hashtable<String,String>();

	public ConfigPanel() {
		super();

		loadTemplates();

		menuPane = new MenuPane();
		this.add(menuPane, BorderLayout.NORTH);

		treePane = new TreePane();
		JScrollPane jspTree = new JScrollPane();
		jspTree.getVerticalScrollBar().setUnitIncrement(12);
		jspTree.setViewportView(treePane);
		jspTree.getViewport().setBackground(Color.white);
		jspTree.getVerticalScrollBar().setUnitIncrement(30);

		jspData = new JScrollPane();
		jspData.getVerticalScrollBar().setUnitIncrement(12);
		jspData.getViewport().setBackground(Color.white);
		jspData.getVerticalScrollBar().setUnitIncrement(30);
		dataPane = new DataPane();
		jspData.setViewportView(dataPane);

		split = new JSplitPane();
		split.setContinuousLayout(true);
		split.setResizeWeight(0.1D);
		split.setLeftComponent(jspTree);
		split.setRightComponent(jspData);

		this.add(split, BorderLayout.CENTER);
	}

	public void load() {
		if (!loaded) {
			try {
				Document configXML = Util.getDocument( new File("config.xml") );
				loaded = treePane.load(configXML);
			}
			catch (Exception ex) { }
		}
	}

	//Class to encapsultate a template element
	class Template {
		Element template;
		Hashtable<String,Element> attrs;
		public Template(Element template) {
			this.template = template;
			attrs = new Hashtable<String,Element>();
			Node child = template.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element attr = (Element)child;
					String attrName = attr.getAttribute("name");
					attrs.put(attrName, attr);
				}
				child = child.getNextSibling();
			}
		}
		public String getName() {
			return template.getTagName();
		}
		public Element getTemplateElement() {
			return template;
		}
		public Element getAttrElement(String name) {
			return attrs.get(name);
		}
		public String getAttrValue(String attrName, String attrValueName) {
			Element e = attrs.get(attrName);
			if (e != null) {
				return e.getAttribute(attrValueName).trim();
			}
			return "";
		}
	}

	private void loadTemplates() {
		templateTable = new Hashtable<String,Template>();
		plugins = new LinkedList<Template>();
		importServices = new LinkedList<Template>();
		processors = new LinkedList<Template>();
		storageServices = new LinkedList<Template>();
		exportServices = new LinkedList<Template>();

		File libraries = new File("libraries");
		loadTemplates(libraries);
	}

	private void loadTemplates(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File f : files) loadTemplates(f);
		}
		else {
			String name = file.getName().toLowerCase();
			if (name.endsWith(".jar")) {
				InputStream in = null;
				try {
					ZipFile zipFile = new ZipFile(file);
					ZipEntry entry = zipFile.getEntry(templateFilename);
					if (entry != null) {
						in = new BufferedInputStream(zipFile.getInputStream(entry));
						loadTemplates(Util.getDocument(in));
					}
				}
				catch (Exception skip) { }
				finally {
					try { if (in != null) in.close(); }
					catch (Exception ignore) { }
				}
			}
		}
	}

	private void loadTemplates(Document templateXML) throws Exception {
		if (templateXML != null) {
			Element root = templateXML.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element e = (Element)child;
					String name = e.getTagName();
					if (name.equals("Components")) loadComponents(e);
					else if (name.equals("DefaultHelpText")) loadDefaultHelpText(e);
				}
				child = child.getNextSibling();
			}
		}
	}

	private void loadComponents(Element components) {
		Node child = components.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element e = (Element)child;
				Template template = new Template(e);
				String name = template.getName();
				if (name.equals("Server")) server = template;
				else if (name.equals("Pipeline")) pipeline = template;
				else if (name.equals("Plugin")) plugins.add(template);
				else if (name.equals("ImportService")) importServices.add(template);
				else if (name.equals("Processor")) processors.add(template);
				else if (name.equals("StorageService")) storageServices.add(template);
				else if (name.equals("ExportService")) exportServices.add(template);

				//Store the element in the templateTable, indexed by the class name
				String className = template.getAttrValue("class", "default");
				if (!className.equals("")) {
					templateTable.put(className, template);
				}
			}
			child = child.getNextSibling();
		}
	}

	private void loadDefaultHelpText(Element dht) {
		Node child = dht.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element attr = (Element)child;
				String attrName = attr.getTagName();
				String attrValue = attr.getTextContent().trim();
				if (!attrValue.equals("")) defaultHelpText.put(attrName, attrValue);
			}
			child = child.getNextSibling();
		}
	}
	//******** End of template loading ********

	class MenuPane extends BasePanel {

		public MenuPane() {
			super();
			setLayout( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );

			JMenuBar menuBar = new JMenuBar();
			menuBar.setBackground( bgColor );

			JMenu fileMenu = new JMenu("File");
			JMenuItem saveItem = new JMenuItem("Save");
			saveItem.setAccelerator( KeyStroke.getKeyStroke('S', InputEvent.CTRL_MASK) );
			saveItem.addActionListener( new SaveImpl() );
			fileMenu.add(saveItem);

			JMenu editMenu = new JMenu("Edit");
			JMenuItem newPipeItem = new JMenuItem("new Pipeline");
			newPipeItem.addActionListener( new NewPipeImpl() );
			editMenu.add(newPipeItem);
			JMenuItem deleteItem = new JMenuItem("Delete");
			deleteItem.setAccelerator( KeyStroke.getKeyStroke('X', InputEvent.CTRL_MASK) );
			deleteItem.addActionListener( new DeleteImpl() );
			editMenu.add(deleteItem);

			JMenu viewMenu = new JMenu("View");
			JMenuItem formItem = new JMenuItem("Form");
			formItem.setAccelerator( KeyStroke.getKeyStroke('F', InputEvent.CTRL_MASK) );
			formItem.addActionListener( new FormImpl() );
			viewMenu.add(formItem);
			JMenuItem xmlItem = new JMenuItem("XML");
			xmlItem.setAccelerator( KeyStroke.getKeyStroke('D', InputEvent.CTRL_MASK) );
			xmlItem.addActionListener( new XmlImpl() );
			viewMenu.add(xmlItem);

			JMenu pluginMenu = new JMenu("Plugin");
			ComponentImpl impl = new ComponentImpl();
			for (Template template : plugins) {
				String name = template.getAttrValue("class", "default");
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(impl);
				pluginMenu.add(item);
			}

			JMenu importServiceMenu = new JMenu("ImportService");
			for (Template template : importServices) {
				String name = template.getAttrValue("class", "default");
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(impl);
				importServiceMenu.add(item);
			}

			JMenu processorMenu = new JMenu("Processor");
			for (Template template : processors) {
				String name = template.getAttrValue("class", "default");
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(impl);
				processorMenu.add(item);
			}

			JMenu storageServiceMenu = new JMenu("StorageService");
			for (Template template : storageServices) {
				String name = template.getAttrValue("class", "default");
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(impl);
				storageServiceMenu.add(item);
			}

			JMenu exportServiceMenu = new JMenu("ExportService");
			for (Template template : exportServices) {
				String name = template.getAttrValue("class", "default");
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(impl);
				exportServiceMenu.add(item);
			}

			JMenu pipeMenu = new JMenu("Pipeline");
			pipeMenu.add( new JMenuItem("New") );

			menuBar.add(fileMenu);
			menuBar.add(editMenu);
			menuBar.add(viewMenu);
			menuBar.add(pluginMenu);
			menuBar.add(importServiceMenu);
			menuBar.add(processorMenu);
			menuBar.add(storageServiceMenu);
			menuBar.add(exportServiceMenu);
			menuBar.add(pipeMenu);

			this.add( menuBar );
		}

		class SaveImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				Element config = treePane.getXML();
				if (checkConfig(config)) {
					/*
					String xml = Util.toPrettyString(config);
					File configFile = new File("config.xml");
					backupTarget(configFile);
					try { Util.setText(configFile, xml); }
					catch (Exception ignore) { }
					*/
				}
			}
		}

		class NewPipeImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				//dataPane.setView(true);
			}
		}

		class DeleteImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				//dataPane.setView(true);
			}
		}

		class FormImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(false);
			}
		}

		class XmlImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(true);
			}
		}

		class ComponentImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				JMenuItem item = (JMenuItem)event.getSource();
				System.out.println("ComponentImpl: "+item.getText());
			}
		}
	}

	//Check the configuration, looking for duplicate ports and root directories.
	private boolean checkConfig(Element config) {
		DupTable portTable = new DupTable(config, "port");
		DupTable rootTable = new DupTable(config, "root");
		if (portTable.hasDuplicates) {
			String dups = portTable.getDuplicates();
			JOptionPane.showMessageDialog(
				this,
				"The following port values appear multiple times\n"
				+ "in the configuration. Port values must be unique.\n"
				+ "The configuration cannot be saved.\n"
				+dups,
				"Duplicate ports",
				JOptionPane.WARNING_MESSAGE);
			return false;
		}
		if (rootTable.hasDuplicates) {
			String dups = rootTable.getDuplicates();
			int yesno = JOptionPane.showConfirmDialog(
							this,
							"The following root directories appear multiple times\n"
							+ "in the configuration. Except in special situations,\n"
							+ "root directories must be unique.\n"
							+ "If you click OK, the configuration will be saved.\n"
							+dups,
							"Duplicate root directories",
							JOptionPane.OK_CANCEL_OPTION);
			return (yesno == JOptionPane.OK_OPTION);
		}
		return true;
	}

	class DupTable extends Hashtable<String,Integer> {
		String attrName;
		public boolean hasDuplicates = false;
		public DupTable(Element el, String attrName) {
			super();
			this.attrName = attrName;
			addElement(el);
		}
		public void addElement(Element el) {
			addAttribute(el);
			Node child = el.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					addElement( (Element)child );
				}
				child = child.getNextSibling();
			}
		}
		private void addAttribute(Element el) {
			String attrValue = el.getAttribute(attrName).trim();
			if (!attrValue.equals("")) {
				Integer i = get(attrValue);
				if (i == null) {
					put(attrValue, new Integer(1));
				}
				else {
					i = new Integer(i.intValue() + 1);
					put(attrValue, i);
					hasDuplicates = true;
				}
			}
		}
		public String getDuplicates() {
			StringBuffer sb = new StringBuffer();
			for (String attrValue : keySet()) {
				Integer i = get(attrValue);
				if (i.intValue() > 1) {
					sb.append(attrValue + "\n");
				}
			}
			return sb.toString();
		}
	}

	//Backup a target.
	private void backupTarget(File targetFile) {
		targetFile = targetFile.getAbsoluteFile();
		File parent = targetFile.getParentFile();
		if (targetFile.exists()) {
			String name = targetFile.getName();
			int k = name.lastIndexOf(".");
			String target = name.substring(0,k) + "[";
			int tlen = target.length();
			String ext = name.substring(k);

			int n = 0;
			File[] files = parent.listFiles();
			if (files != null) {
				for (File file : files) {
					String fname = file.getName();
					if (fname.startsWith(target)) {
						int kk = fname.indexOf("]", tlen);
						if (kk > tlen) {
							int nn = Util.getInt(fname.substring(tlen, kk), 0);
							if (nn > n) n = nn;
						}
					}
				}
			}
			n++;
			File backup = new File(parent, target + n + "]" + ext);
			backup.delete(); //shouldn't be there, but just in case.
			targetFile.renameTo(backup);
		}
	}

	//******** The left pane ********
	class TreePane extends BasePanel implements TreeSelectionListener {

		Document doc = null;
		Element root = null;
		XMLTree tree = null;
		TreeDragSource dragSource;
		TreeDropTarget dropTarget;

		public TreePane() {
			super();
		}

		public boolean load(Document doc) {
			this.doc = doc;
			root = doc.getDocumentElement();
			tree = new XMLTree(root);
			tree.getSelectionModel().addTreeSelectionListener(this);
			removeAll();
			this.add(tree);
			tree.expandAll();
			dragSource = new TreeDragSource(tree, DnDConstants.ACTION_COPY_OR_MOVE);
			dropTarget = new TreeDropTarget(tree);
			return true;
		}

		public void valueChanged(TreeSelectionEvent event) {
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			if (treeNode != null) {
				dataPane.edit( treeNode );
			}
		}

		public Element getXML() {
			return tree.getXML();
		}
	}

	class ScrollableJPanel extends JPanel implements Scrollable {
		private boolean trackWidth = true;
		public ScrollableJPanel() {
			super();
		}
		public void setTrackWidth(boolean trackWidth) { this.trackWidth = trackWidth; }
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return trackWidth; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 10; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 10; }
	}

	//******** The right pane ********
	class DataPane extends ScrollableJPanel {

		boolean viewAsXML = false;
		DefaultMutableTreeNode currentNode = null;

		public DataPane() {
			super();
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			setView(false);
		}

		public void setView(boolean viewAsXML) {
			this.viewAsXML = viewAsXML;
			if (viewAsXML) {
				setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
				setBackground(Color.white);
				jspData.getViewport().setBackground(Color.white);
			}
			else {
				setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
				setBackground(BasePanel.bgColor);
				jspData.getViewport().setBackground(BasePanel.bgColor);
			}
			if (currentNode != null) edit(currentNode);
		}

		public void edit(DefaultMutableTreeNode treeNode) {
			Object object = treeNode.getUserObject();
			if ((object != null) && (object instanceof XMLUserObject)) {
				currentNode = treeNode;
				XMLUserObject userObject = (XMLUserObject)object;
				removeAll();
				if (viewAsXML) displayXML(userObject);
				else displayForm(userObject);
				jspData.setViewportView(this);
				jspData.getVerticalScrollBar().setValue(0);
			}
		}

		private void displayForm(XMLUserObject userObject) {
			setTrackWidth(true);
			setLayout(new BorderLayout());
			add(userObject.getFormPanel(), BorderLayout.CENTER);
		}

		private void displayXML(XMLUserObject userObject) {
			setTrackWidth(false);
			setLayout(new BorderLayout());
			ColorPane cp = new ColorPane();
			cp.setEditable(false);
			cp.setScrollableTracksViewportWidth(false);
			String xml = Util.toPrettyString(userObject.getXML());
			cp.setText(xml);
			add(cp, BorderLayout.CENTER);
		}
	}

	//******** The User Object that encapsulates the CTP configuration elements ********
	class XMLUserObject {

		public Element element;
		public String className;
		public String tag;
		public Template template;

		public DefaultMutableTreeNode treeNode = null;

		public boolean isConfiguration = false;
		public boolean isServer = false;
		public boolean isPlugin = false;
		public boolean isPipeline = false;
		public boolean isStage = false;
		public boolean isChild = false;

		public FormPanel formPanel = null;

		public XMLUserObject(Element element) {
			super();
			this.element = element;
			this.className = element.getAttribute("class");
			tag = element.getTagName();

			isConfiguration = tag.equals("Configuration");
			isServer = tag.equals("Server");
			isPlugin = tag.equals("Plugin");
			isPipeline = tag.equals("Pipeline");
			if (!isConfiguration && !isServer && !isPlugin && !isPipeline) {
				isStage = element.getParentNode().getNodeName().equals("Pipeline");
				isChild = !isStage;
			}

			if (tag.equals("Plugin")) {
				String name = className.substring(className.lastIndexOf(".")+1);
				tag += " ["+name+"]";
			}
			else if (tag.equals("Pipeline")) {
				tag += " ["+element.getAttribute("name")+"]";
			}
			this.template = (isServer ? server : (isPipeline ? pipeline : templateTable.get(className)));
			this.formPanel = new FormPanel(element, template);
		}

		public void setTreeNode(DefaultMutableTreeNode treeNode) {
			this.treeNode = treeNode;
		}

		public DefaultMutableTreeNode getTreeNode() {
			return treeNode;
		}

		public Template getTemplate() {
			return template;
		}

		public FormPanel getFormPanel() {
			return formPanel;
		}

		public String getAttribute(String name) {
			return element.getAttribute(name);
		}

		public boolean isDragable() {
			return !isConfiguration && !isServer && !isPipeline && !isChild;
		}

		public Element getXML() {
			Element xml = formPanel.getXML();
			if (treeNode != null) {
				Enumeration e = treeNode.children();
				while (e.hasMoreElements()) {
					Object object = e.nextElement();
					if (object instanceof DefaultMutableTreeNode) {
						DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)object;
						Object userObject = childNode.getUserObject();
						XMLUserObject xmlUserObject = (XMLUserObject)userObject;
						Element c = xmlUserObject.getXML();
						Element cImported = (Element)xml.getOwnerDocument().importNode(c, true);
						xml.appendChild(cImported);
					}
				}
			}
			return xml;
		}

		public String toString() {
			return tag;
		}
	}

	//******** The panel that displays the form for a component ********
	class FormPanel extends ScrollableJPanel {

		Element element;
		Template template;

		public FormPanel(Element element, Template template) {
			super();
			this.element = element;
			this.template = template;
			setBackground(BasePanel.bgColor);
			setTrackWidth(true);
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			if (template != null) {
				Node child = template.getTemplateElement().getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element ch = (Element)child;
						if (ch.getTagName().equals("attr")) {
							String name = ch.getAttribute("name");
							String defValue = ch.getAttribute("default");
							String options = ch.getAttribute("options").trim();
							boolean editable = !ch.getAttribute("editable").equals("no");

							//Get the help text if possible
							String helpText = "";
							NodeList nl = ch.getElementsByTagName("helptext");
							if (nl.getLength() > 0) helpText = nl.item(0).getTextContent();
							if (helpText.equals("")) helpText = defaultHelpText.get(name);
							if (helpText == null) helpText = "";

							String configValue = element.getAttribute(name);
							if (configValue.equals("")) configValue = defValue;

							if (options.equals("")) {
								add( new TextAttrPanel(name, configValue, helpText, editable) );
							}
							else {
								//add( new ComboAttrPanel(name, configValue, options, helpText) );
								add( new ButtonAttrPanel(name, configValue, options, helpText) );
							}
							add( Box.createVerticalStrut(10) );
						}
					}
					child = child.getNextSibling();
				}
			}
		}

		public Element getXML() {
			Document doc = Util.getDocument();
			Element root = doc.createElement(element.getTagName());
			Component[] comps = getComponents();
			for (Component c : comps) {
				if (c instanceof AttrPanel) {
					AttrPanel a = (AttrPanel)c;
					String attrName = a.getName();
					String attrValue = a.getValue();
					String defaultValue = template.getAttrValue(attrName, "default");
					boolean required = template.getAttrValue(attrName, "required").equals("yes");
					if (required || (!attrValue.equals("") && !attrValue.equals(defaultValue))) {
						root.setAttribute(attrName, attrValue);
					}
				}
			}
			return root;
		}
	}

	//************** UI components for attributes in the right pane **************
	class AttrPanel extends ScrollableJPanel {
		String name;
		public AttrPanel(String name) {
			super();
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			this.name = name;
			Border empty = BorderFactory.createEmptyBorder(5,30,5,5);
			Border line = BorderFactory.createLineBorder(Color.GRAY);
			TitledBorder title = BorderFactory.createTitledBorder(line, name);
			title.setTitleFont( new Font( "Monospaced", Font.BOLD, 18 ) );
			Border compound = BorderFactory.createCompoundBorder(title, empty);
			setBorder(compound);
			setBackground(Color.white);
		}
		public String getName() {
			return name;
		}
		public String getValue() {
			return "";
		}
	}

	class TextAttrPanel extends AttrPanel {
		public ConfigTextField text;
		HelpPane help = null;
		public TextAttrPanel(String name, String value, String comment, boolean editable) {
			super(name);
			text = new ConfigTextField(value);
			text.setEditable(editable);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
			else this.add( Box.createVerticalStrut(5) );
		}
		public String getValue() {
			return text.getText().trim();
		}
	}

	class ComboAttrPanel extends AttrPanel {
		public ConfigComboBox text;
		HelpPane help = null;
		public ComboAttrPanel(String name, String value, String options, String comment) {
			super(name);
			String[] ops = options.split("\\|");
			int idx = 0;
			for (int i=0; i<ops.length; i++) {
				if (value.equals(ops[i])) {
					idx = i;
					break;
				}
			}
			text = new ConfigComboBox(ops, idx);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
		}
		public String getValue() {
			return text.getSelectedItem().toString().trim();
		}
	}

	class ButtonAttrPanel extends AttrPanel {
		public ConfigButtonGroup text;
		HelpPane help = null;
		public ButtonAttrPanel(String name, String value, String options, String comment) {
			super(name);
			String[] ops = options.split("\\|");
			int idx = 0;
			for (int i=0; i<ops.length; i++) {
				if (value.equals(ops[i])) {
					idx = i;
					break;
				}
			}
			text = new ConfigButtonGroup(ops, idx);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
		}
		public String getValue() {
			return text.getText().trim();
		}
	}

	class ConfigTextField extends JTextField {
		public ConfigTextField(String s) {
			super();
			setText(s);
			setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
			setBackground(Color.white);
		}
	}

	class ConfigComboBox extends JComboBox implements Scrollable {
		public ConfigComboBox(String[] values, int selectedIndex) {
			super(values);
			setSelectedIndex(selectedIndex);
			setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
			setBackground(Color.white);
			setEditable(false);
		}
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 200; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 40; }
	}

	class ConfigButtonGroup extends JPanel {
		ButtonGroup group;
		public ConfigButtonGroup(String[] values, int selectedIndex) {
			super();
			setLayout( new RowLayout(10, 0) );
			setBackground(Color.white);
			group = new ButtonGroup();
			for (int i=0; i<values.length; i++) {
				JRadioButton jrb = new JRadioButton( values[i] );
				jrb.setSelected( (i==selectedIndex) );
				jrb.setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
				jrb.setBackground(Color.white);
				group.add(jrb);
				add(jrb);
				add(RowLayout.crlf());
			}
		}
		public String getText() {
			Enumeration<AbstractButton> e = group.getElements();
			while (e.hasMoreElements()) {
				AbstractButton b = e.nextElement();
				if (b.isSelected()) return b.getText();
			}
			return "";
		}
	}

	class HelpPane extends JTextPane implements Scrollable {
		public HelpPane(String s) {
			super();
			setText(s);
			setFont( new Font( "SansSerif", Font.PLAIN, 12 ) );
			setEditable(false);
			setForeground(Color.gray);
		}
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 200; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 40; }
	}


	//*****************************************************************
	//The rest of the source code implements the tree for the left pane
	//*****************************************************************
	class XMLTree extends JTree {

		DefaultTreeModel model = null;
		DefaultMutableTreeNode troot = null;

		public XMLTree(Element xmlRoot) {
			super();
			XMLUserObject xmlUserObject = new XMLUserObject(xmlRoot);
			troot = new DefaultMutableTreeNode(xmlUserObject);
			xmlUserObject.setTreeNode(troot);
			model = new DefaultTreeModel(troot);
			setModel(model);
			setScrollsOnExpand(true);
			addChildren(troot, xmlRoot);

			TreeSelectionModel tsm = getSelectionModel();
			tsm.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		}

		private void addChildren(DefaultMutableTreeNode tnode, Element el) {
			Node child = el.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element cel = (Element)child;
					DefaultMutableTreeNode cnode = addChild(tnode, cel);
					addChildren(cnode, cel);
				}
				child = child.getNextSibling();
			}
		}

		public DefaultMutableTreeNode addChild(DefaultMutableTreeNode tnode, Element child) {
			XMLUserObject xmlUserObject = new XMLUserObject(child);
			DefaultMutableTreeNode cnode = new DefaultMutableTreeNode(xmlUserObject);
			xmlUserObject.setTreeNode(cnode);
			tnode.add(cnode);
			return cnode;
		}

		public Element getXML() {
			Object userObject = troot.getUserObject();
			XMLUserObject xmlUserObject = (XMLUserObject)userObject;
			return xmlUserObject.getXML();
		}

		private void appendTreeNode(Node node, DefaultMutableTreeNode tnode) {
			Document doc;
			if (node instanceof Document) doc = (Document)node;
			else doc = node.getOwnerDocument();
			XMLUserObject xmlUserObject = (XMLUserObject)tnode.getUserObject();
			Element el = (Element)doc.importNode( xmlUserObject.element, false );
			node.appendChild(el);
			try {
				TreeNode child = tnode.getFirstChild();
				while (child != null) {
					DefaultMutableTreeNode tchild = (DefaultMutableTreeNode)child;
					appendTreeNode( el, tchild );
					child = tchild.getNextSibling();
				}
			}
			catch (Exception noKids) { }
		}

		public void expandAll() {
			for (int i=0; i<getRowCount(); i++) {
				if (isCollapsed(i)) expandRow(i);
			}
		}

		public void collapseAll() {
			for (int i=getRowCount(); i>=0; i--) {
				collapseRow(i);
			}
		}
	}

	class TreeDragSource implements DragSourceListener, DragGestureListener {

		DragSource source;
		DragGestureRecognizer recognizer;
		TransferableObject transferable;
		DefaultMutableTreeNode oldNode;
		JTree sourceTree;

		public TreeDragSource(JTree tree, int actions) {
			sourceTree = tree;
			source = new DragSource();
			recognizer = source.createDefaultDragGestureRecognizer(sourceTree, actions, this);
		}

		//----------------------
		//Drag Gesture Handler
		//----------------------
		public void dragGestureRecognized(DragGestureEvent dge) {
			TreePath path = sourceTree.getSelectionPath();
			if ((path == null) || (path.getPathCount() <= 1)) {
				return;
			}
			oldNode = (DefaultMutableTreeNode) path.getLastPathComponent();
			XMLUserObject xmlUserObject = (XMLUserObject)oldNode.getUserObject();
			if (xmlUserObject.isDragable()) {
				transferable = new TransferableObject(xmlUserObject);
				source.startDrag(dge, DragSource.DefaultMoveDrop, transferable, this);
			}
		}

		//----------------------
		//Drag Event Handlers
		//----------------------
		public void dragEnter(DragSourceDragEvent dsde) {
			dragOver(dsde);
		}

		public void dragOver(DragSourceDragEvent dsde) {
			DragSourceContext dsc = dsde.getDragSourceContext();
			dsc.setCursor(DragSource.DefaultMoveDrop);
		}

		public void dragExit(DragSourceEvent dse) {
			DragSourceContext dsc = dse.getDragSourceContext();
			dsc.setCursor(DragSource.DefaultMoveNoDrop);
		}

		public void dropActionChanged(DragSourceDragEvent dsde) { }

		public void dragDropEnd(DragSourceDropEvent dsde) {
			if (dsde.getDropSuccess() && (dsde.getDropAction() == DnDConstants.ACTION_MOVE)) {
				((DefaultTreeModel) sourceTree.getModel()).removeNodeFromParent(oldNode);
			}
		}
	}

	class TreeDropTarget implements DropTargetListener {

		DropTarget target;
		JTree targetTree;

		public TreeDropTarget(JTree tree) {
			targetTree = tree;
			target = new DropTarget(targetTree, this);
		}

		//----------------------
		//Drop Event Handlers
		//----------------------
		private boolean okToDrop(Transferable tr, DefaultMutableTreeNode target) {
			XMLUserObject dest = (XMLUserObject)target.getUserObject();
			DataFlavor[] flavors = tr.getTransferDataFlavors();
			for (int i = 0; i < flavors.length; i++) {
				if (tr.isDataFlavorSupported(flavors[i])) {
					try {
						XMLUserObject src = (XMLUserObject)tr.getTransferData(flavors[i]);
						if (src.isStage && dest.isPipeline) return true;
						if (src.isStage && dest.isStage) return true;
						if (src.isPlugin && dest.isPlugin) return true;
						return false;
					}
					catch (Exception skip) { }
				}
			}
			return false;
		}

		private DefaultMutableTreeNode getNodeForEvent(DropTargetDragEvent dtde) {
			Point p = dtde.getLocation();
			DropTargetContext dtc = dtde.getDropTargetContext();
			JTree tree = (JTree) dtc.getComponent();
			TreePath path = tree.getClosestPathForLocation(p.x, p.y);
			return (DefaultMutableTreeNode) path.getLastPathComponent();
		}

		private DefaultMutableTreeNode getNodeForEvent(DropTargetDropEvent dtde) {
			Point p = dtde.getLocation();
			DropTargetContext dtc = dtde.getDropTargetContext();
			JTree tree = (JTree) dtc.getComponent();
			TreePath path = tree.getClosestPathForLocation(p.x, p.y);
			return (DefaultMutableTreeNode) path.getLastPathComponent();
		}

		public void dragEnter(DropTargetDragEvent dtde) {
			dragOver(dtde);
		}

		public void dragOver(DropTargetDragEvent dtde) {
			DefaultMutableTreeNode node = getNodeForEvent(dtde);
			XMLUserObject xmlUserObject = (XMLUserObject)node.getUserObject();
			if (okToDrop(dtde.getTransferable(), node)) {
				dtde.acceptDrag(dtde.getDropAction());
			}
			else dtde.rejectDrag();
		}

		public void dragExit(DropTargetEvent dte) { }

		public void dropActionChanged(DropTargetDragEvent dtde) { }

		public void drop(DropTargetDropEvent dtde) {
			DefaultMutableTreeNode targetNode = getNodeForEvent(dtde);
			XMLUserObject targetUO = (XMLUserObject)targetNode.getUserObject();
			Transferable tr = dtde.getTransferable();
			DataFlavor[] flavors = tr.getTransferDataFlavors();
			try {
				for (int i = 0; i < flavors.length; i++) {
					if (tr.isDataFlavorSupported(flavors[i])) {
						dtde.acceptDrop(dtde.getDropAction());

						XMLUserObject sourceUO = (XMLUserObject)tr.getTransferData(flavors[i]);
						DefaultMutableTreeNode sourceNode = new DefaultMutableTreeNode(sourceUO);

						DefaultTreeModel model = (DefaultTreeModel) targetTree.getModel();

						int index = 0;
						if (sourceUO.isStage && targetUO.isPipeline) {
							index = model.getChildCount(targetNode);
							model.insertNodeInto(sourceNode, targetNode, index);
						}
						else if (sourceUO.isStage && targetUO.isStage) {
							DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode)targetNode.getParent();
							index = targetParentNode.getIndex(targetNode);
							model.insertNodeInto(sourceNode, targetParentNode, index);
						}
						else if (sourceUO.isPlugin && targetUO.isPlugin) {
							DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode)targetNode.getParent();
							index = targetParentNode.getIndex(targetNode);
							model.insertNodeInto(sourceNode, targetParentNode, index);
						}
						else {
							dtde.rejectDrop();
							return;
						}
						dtde.dropComplete(true);
						return;
					}
				}
				dtde.rejectDrop();
			}
			catch (Exception e) {
				e.printStackTrace();
				dtde.rejectDrop();
			}
		}
	}

	public static DataFlavor XML_FLAVOR = new DataFlavor(XMLUserObject.class, "XML_FLAVOR");
	class TransferableObject implements Transferable {

		DataFlavor flavors[] = { XML_FLAVOR };
		XMLUserObject object;

		public TransferableObject(XMLUserObject object) {
			this.object = object;
		}

		public synchronized DataFlavor[] getTransferDataFlavors() {
			return flavors;
		}

		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return (flavor.getRepresentationClass() == XMLUserObject.class);
		}

		public synchronized Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (isDataFlavorSupported(flavor)) return object;
			throw new UnsupportedFlavorException(flavor);
		}
	}
}
