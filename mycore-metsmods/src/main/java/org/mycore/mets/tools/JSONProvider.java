/* $Revision: 3033 $ 
 * $Date: 2010-10-22 13:41:12 +0200 (Fri, 22 Oct 2010) $ 
 * $LastChangedBy: thosch $
 * Copyright 2010 - Thüringer Universitäts- und Landesbibliothek Jena
 *  
 * Mets-Editor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mets-Editor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mets-Editor.  If not, see http://www.gnu.org/licenses/.
 */
package org.mycore.mets.tools;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.dom4j.DocumentException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.xpath.XPath;
import org.mycore.mets.model.IMetsElement;
import org.mycore.mets.tools.model.Directory;
import org.mycore.mets.tools.model.Entry;
import org.mycore.mets.tools.model.MetsTree;

/**
 * @author Silvio Hermann (shermann)
 *
 */
public class JSONProvider {
    final private static Logger LOGGER = Logger.getLogger(JSONProvider.class);

    private String derivate;

    private Element structLink;

    private Document mets;

    /**
     * @param mets the Mets document
     * @param derivate the derivate id
     */
    @SuppressWarnings("unchecked")
    public JSONProvider(Document mets, String derivate) throws DocumentException {
        this.derivate = derivate;
        this.mets = mets;

        /*set the struct link*/
        Iterator<Element> it = mets.getDescendants(new ElementFilter("structLink", IMetsElement.METS));
        if (!it.hasNext()) {
            throw new DocumentException("Mets document is invalid, no structLink element found");
        }
        /* assuming only one struct map is existing */
        structLink = it.next();
    }

    /**
     * @return
     */
    public String getDerivate() {
        return derivate;

    }

    /**
     * @param mets
     * @return String a String in JSON format suitable for a client side dijit tree
     */
    @SuppressWarnings("unchecked")
    public String toJSON() {
        Element logStructMap = getLogicalStructMapElement(this.mets);
        Element parentDiv = logStructMap.getChild("div", IMetsElement.METS);
        if (parentDiv == null) {
            LOGGER
                    .error("Invalid mets document, as there is no div container in the logical structure map <mets:structMap TYPE=\"LOGICAL\"> ");
            return null;
        }

        XPath xpath = null;
        List nodes = null;
        try {
            xpath = XPath.newInstance("mets:mets/mets:structMap[@TYPE='LOGICAL']/mets:div/mets:div");
            xpath.addNamespace(IMetsElement.METS);
            nodes = xpath.selectNodes(this.mets);
        } catch (JDOMException e) {
            LOGGER.error(e);
        }

        if (nodes == null) {
            LOGGER.error("Cannot create JSONObject as there is no logical structMap");
            return null;
        }

        MetsTree tree = new MetsTree(derivate);
        /* setting document type and title */
        String docType = parentDiv.getAttributeValue("TYPE");
        String docTitle = parentDiv.getAttributeValue("LABEL");

        if (docType != null && docType.length() > 0) {
            tree.setDocType(docType);
        }
        if (docTitle != null && docTitle.length() > 0) {
            tree.setDocTitle(docTitle);
        }

        Iterator<Element> divIterator = nodes.iterator();
        while (divIterator.hasNext()) {
            Element currentLogicalDiv = divIterator.next();
            String logicalId = currentLogicalDiv.getAttributeValue("ID");
            String label = currentLogicalDiv.getAttributeValue("LABEL");
            String structureType = currentLogicalDiv.getAttributeValue("TYPE");

            /* current element is about to be an item in the digit tree */
            if (structureType.equals("page")) {
                String physId = getPhysicalIdsForLogical(logicalId)[0];
                String itemId = physId.substring(physId.indexOf("_") + 1);
                Entry page = new Entry(itemId, physId, label, "page");
                int order = Integer.valueOf(getOrderAttribute(physId));
                page.setOrder(order);
                String orderLabel = getOrderLabelAttribute(physId);
                page.setOrderLabel(orderLabel);
                tree.addEntry(page);
            }
            /* current element is about to be a category/parent in the digit tree */
            else {
                try {
                    XPath kiddies = XPath.newInstance("mets:div");
                    kiddies.addNamespace(IMetsElement.METS);
                    /* list of children */
                    List<Element> list = kiddies.selectNodes(currentLogicalDiv);

                    Directory dir = new Directory(logicalId, label, structureType);
                    tree.addDirectory(dir);

                    boolean flag = firstIsFile(logicalId);
                    String physId = null;
                    if (flag) {
                        physId = getPhysicalIdsForLogical(logicalId)[0];
                    } else {
                        String firstDivWithFiles = getFirstDivWithFiles(logicalId, list);
                        physId = getPhysicalIdsForLogical(firstDivWithFiles)[0];
                    }
                    int order = Integer.valueOf(getOrderAttribute(physId));
                    dir.setOrder(order);
                    addFiles(dir);
                    buildTree(dir, list);

                } catch (JDOMException ex) {
                    LOGGER.error(ex);
                }
            }
        }
        LOGGER.debug(tree.asJson());
        return tree.asJson();
    }

    /**
     * @return the orderlabel attribute of the div with the given physical id, 
     * returns an empty string if there is no such label
     * */
    @SuppressWarnings("unchecked")
    private String getOrderLabelAttribute(String physId) {
        XPath xpath = null;
        List<Element> nodes = null;
        try {
            xpath = XPath.newInstance("mets:mets/mets:structMap[@TYPE='PHYSICAL']/mets:div/mets:div[@ID='" + physId + "']");
            xpath.addNamespace(IMetsElement.METS);
            nodes = xpath.selectNodes(this.mets);
            Element e = nodes.get(0);
            String orderLabel = e.getAttributeValue("ORDERLABEL");
            return orderLabel == null ? "" : orderLabel;
        } catch (Exception e) {
            LOGGER.error("Error getting orderlabel attribute for file with physical id=" + physId, e);
        }
        return "";
    }

    /**
     * @return true if the first child is a file/page or false otherwise
     */
    private boolean firstIsFile(String logicalDivId) {
        String[] array = this.getPhysicalIdsForLogical(logicalDivId);
        return array.length == 0 ? false : true;
    }

    /**@return the id of the first logical div where the 1st child is a file */
    @SuppressWarnings("unchecked")
    private String getFirstDivWithFiles(String parentLogicalDivId, List<Element> children) throws JDOMException {
        Iterator<Element> iterator = children.iterator();
        while (iterator.hasNext()) {
            Element div = iterator.next();
            String divId = div.getAttributeValue("ID");
            if (firstIsFile(divId)) {
                return divId;
            } else {
                XPath kiddies = XPath.newInstance("mets:div");
                kiddies.addNamespace(IMetsElement.METS);

                return getFirstDivWithFiles(divId, kiddies.selectNodes(div));
            }
        }
        return null;
    }

    /** This methods builds the tree with the mets document as a base */
    @SuppressWarnings("unchecked")
    private void buildTree(Directory parent, List<Element> children) {
        Iterator<Element> it = children.iterator();
        while (it.hasNext()) {
            Element logicalDiv = it.next();
            String divType = logicalDiv.getAttributeValue("TYPE");
            String logicalDivId = logicalDiv.getAttributeValue("ID");
            String logicalDivLabel = logicalDiv.getAttributeValue("LABEL");

            Directory dir = new Directory(logicalDivId, logicalDivLabel, divType);
            int order = Integer.valueOf(getOrderAttribute(getPhysicalIdsForLogical(logicalDivId)[0]));
            dir.setOrder(order);
            addFiles(dir);
            parent.addDirectory(dir);

            try {
                XPath kiddies = XPath.newInstance("mets:div");
                kiddies.addNamespace(IMetsElement.METS);
                buildTree(dir, kiddies.selectNodes(logicalDiv));
            } catch (Exception x) {
                LOGGER.error(x);
            }
        }
    }

    /**
     * Gets the files (physical ids) belonging to the directory, 
     * creates a entry for each file and adds it to the directory
     *  
     * @param dir
     */
    private void addFiles(Directory dir) {
        String[] physIds = getPhysicalIdsForLogical(dir.getLogicalId());
        for (int i = 0; i < physIds.length; i++) {
            String itemId = physIds[i].substring(physIds[i].indexOf("_") + 1);
            /* TODO get real label from mets */
            Entry page = new Entry(itemId, physIds[i], itemId, "page");
            page.setOrder(getOrderAttribute(physIds[i]));
            String orderLabel = getOrderLabelAttribute(physIds[i]);
            page.setOrderLabel(orderLabel);
            dir.addEntry(page);
        }
    }

    /** Returns the physical order of the file associated with the given physical id */
    @SuppressWarnings("unchecked")
    private int getOrderAttribute(String physId) {
        XPath xpath = null;
        List<Element> nodes = null;
        try {
            xpath = XPath.newInstance("mets:mets/mets:structMap[@TYPE='PHYSICAL']/mets:div/mets:div[@ID='" + physId + "']");
            xpath.addNamespace(IMetsElement.METS);
            nodes = xpath.selectNodes(this.mets);
            Element e = nodes.get(0);
            int order = Integer.valueOf(e.getAttributeValue("ORDER"));
            return order;
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return -1;
    }

    /**
     * @param mets the source mets document
     * @return the logical structure map of the mets document
     */
    @SuppressWarnings("unchecked")
    private Element getLogicalStructMapElement(Document mets) {
        Iterator<Element> iterator = mets.getDescendants(new ElementFilter("structMap", IMetsElement.METS));
        while (iterator.hasNext()) {
            Element structMap = iterator.next();
            String type = structMap.getAttributeValue("TYPE");
            if (type != null && type.equals("LOGICAL")) {
                return structMap;
            }
        }

        return null;
    }

    /**
     * @param logicalId
     * @return the list of physical ids belonging to the given logical id
     */
    @SuppressWarnings("unchecked")
    private String[] getPhysicalIdsForLogical(String logicalId) {
        Vector<String> col = new Vector<String>();
        Iterator<Element> it = this.structLink.getDescendants(new ElementFilter("smLink", IMetsElement.METS));
        while (it.hasNext()) {
            Element link = it.next();
            if (link.getAttributeValue("from", IMetsElement.XLINK).equals(logicalId)) {
                String linkTo = link.getAttributeValue("to", IMetsElement.XLINK);
                col.add(linkTo);
            }
        }
        return col.toArray(new String[0]);
    }
}
