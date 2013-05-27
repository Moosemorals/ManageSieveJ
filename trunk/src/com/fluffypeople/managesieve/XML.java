
/*
 * XML.java
 *
 * Created on 13 September 2009, 20:14
 */
package com.fluffypeople.managesieve;

import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.log4j.Logger;
import org.w3c.dom.*;

/**
 * XML builder class.
 *
 * @author Osric
 * @version $Id$
 */
public class XML {

    private static final Logger log = Logger.getLogger(XML.class);
    private Node current;
    private Document root;
    

    /**
     * Creates a new instance of XML
     */
    public XML() {
        root = newDocument();
        current = root;
    }

    /**
     * Helper method to create a new DOM Document
     *
     * @return an empty Document
     */
    public static Document newDocument() {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            doc.setXmlStandalone(true);

            return doc;
        } catch (ParserConfigurationException ex) {
            log.error("Can't create new Document:" + ex.getMessage());
            return null;
        }
    }


    /**
     * Start an XML element
     */
    public XML start(String tag) {
        return start(tag, (String)null);
    }

    /**
     * Start an element, with optional attributes.
     *
     * @param tag String name of the elements
     * @param attributes String list of attribute name, value pairs. Must be an even
     * length list, null values will be ignored.
     * @return
     */
    public XML start(final String tag, final String... attributes) {
        Element e = root.createElement(tag);

        if (attributes != null && attributes.length > 1) {
            if (attributes.length % 2 != 0) {
                throw new IllegalArgumentException("Attribute list must be even length, got " + attributes.length);
            }
            for (int i = 0; i < attributes.length - 1; i += 2) {
                String key = attributes[i];
                String value = attributes[i + 1];

                Attr a = root.createAttribute(key);
                if (value != null) {
                    a.setValue(value);
                }
                e.setAttributeNode(a);
            }
        }

        current.appendChild(e);
        current = e;

        return this;
    }

    /**
     * End the current XML element.
     * @return this XML document.
     */
    public XML end() {
        current = current.getParentNode();
        return this;
        
    }

    /**
     * Gets the @{code org.w3c.dom.Document} that represents this XML
     *
     * @return a Document that represents the XML
     */
    public Document getDocument() {
        return root;
    }

    /**
     * Add an XML element that holds some text, rather than other elements
     *
     * @param tag String giving the name of the element
     * @param text String contents of the element.
     * @return this XML document
     */
    public XML add(String tag, String text) {
        return add(tag, text, (String)null);
    }


    public XML add(final String tag, final String text, final String... attr) {
        start(tag, attr);
        if (text != null) {
            current.appendChild(root.createTextNode(text));
        }
        end();
        return this;
    }

    /**
     * Add an empty element to the tree
     *
     * @param tag String givng the name of the element
     * @return this XML document
     */
    public XML add(String tag) {
        return add(tag, null);
    }


    public XML add(XML xml) {
        Node copy = root.importNode(xml.root.getDocumentElement(), true);

        current.appendChild(copy);

        return this;
    }

    /**
     * Clears all content stored so far
     *
     * @return a new empty document
     */
    public XML clear() {
        root = newDocument();
        current = root;

        return this;
    }

    /**
     * Transform the XML to a string representation, ensuring the output
     * is well formed. Includes XML declaration and indenting.
     *
     * @return a string representation of the XML document
     */
    @Override
    public String toString() {
        return toString(false, true);
    }

    /**
     * Transform the XML to a string representation, ensuring the output
     * is well formed.
     * @param embed boolean, true to skip XML declaration.
     * @param indent boolean, true to include spaces and line ends between elements.
     * @return a string representation of the document.
     */
    public String toString(boolean embed, boolean indent) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, embed
                    ? "yes"
                    : "no");
            transformer.setOutputProperty(OutputKeys.METHOD, embed
                    ? "html"
                    : "xml");

            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(root);

            transformer.transform(source, result);

            return result.getWriter().toString();
        } catch (Exception e) {
            log.error("toString", e);

            return "<error>Can't produce xml</error>";
        }
    }
}
