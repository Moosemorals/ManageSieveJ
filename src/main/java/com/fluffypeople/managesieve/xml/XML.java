/*
 * The MIT License
 *
 * Copyright 2013-2015 "Osric Wilkinson" <osric@fluffypeople.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.fluffypeople.managesieve.xml;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

/**
 * XML builder class.
 *
 * @author Osric Wilkinson &lt;osric@fluffypeople.com&gt;
 */
public class XML {

    private static final Logger log = LoggerFactory.getLogger(XML.class);
    private Node current;
    private Document root;
    private List<String> cdatas;

    /**
     * Creates a new instance of XML
     */
    public XML() {
        root = newDocument();
        current = root;
        cdatas = new ArrayList<String>();
    }

    /**
     * Create a new document with a reference to an xslt stylesheet
     *
     * @param xsltRef URL pointing to a style sheet
     */
    public XML(String xsltRef) {
        root = newDocument(xsltRef);
        current = root;
        cdatas = new ArrayList<String>();
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
            log.error("Can't create new Document: {}", ex.getMessage(), ex);

            return null;
        }
    }

    /**
     * Create a new XML document with a refrence to the given stylesheet
     *
     * @param xstRef the stylesheet to return
     * @return the new document
     */
    public static Document newDocument(String xstRef) {
        Document doc = newDocument();

        ProcessingInstruction pi = doc.createProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\"" + xstRef + "\"");
        doc.appendChild(pi);

        return doc;
    }

    public void setStylesheet(String xstRef) {
        ProcessingInstruction pi = root.createProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\"" + xstRef + "\"");
        Node child = root.getFirstChild();
        if (child != null) {
            root.insertBefore(pi, child);
        } else {
            root.appendChild(pi);
        }
    }

    /**
     * Start an XML element
     */
    public XML start(String tag) {
        return start(tag, (Map<String, String>)null);
    }

    /**
     * Start an XML element, including attributes if any
     *
     * @param tag String name of the element
     * @param attrib Map<String, String> holding key, value attribute pairs.
     * Ignored if null.
     * @return this XML document
     */
    public XML start(String tag, Map<String, String> attrib) {
        Element e = root.createElement(tag);

        if (attrib != null) {
            for (String key : attrib.keySet()) {
                String value = attrib.get(key);
                Attr a;
                try {
                    a = root.createAttribute(key);
                } catch (DOMException ex) {
                    log.error("Error trying to create attribue named {}", key);
                    throw ex;
                }

                a.setValue(value);
                e.setAttributeNode(a);
            }
        }

        current.appendChild(e);
        current = e;

        return this;
    }

    /**
     * Start an element, with optional attributes.
     *
     * @param tag String name of the elements
     * @param attributes String list of attribute name, value pairs. Must be an even
     * length list, null values will be ignored.
     * @return XML object, for chaining
     */
    public XML start(final String tag, final String... attributes) {
        Element e = root.createElement(tag);

        if (attributes != null) {
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
     * End an XML element
     *
     * @deprecated 
     * @param tag String specifying the element to close. This is ignored, as
     * the document keeps track of the current element
     * @return this XML document
     */
    public XML end(String tag) {
        return end();
    }
    
    /**
     * End an XML element,
     * @return this XML document.
     */
    public XML end() {
        current = current.getParentNode();
        return this;
        
    }

    /**
     * Gets the org.w3c.dom.Document that represents this XML
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
        return add(tag, text, (Map<String, String>)null);
    }

    /**
     * Add an XML element that holds text, with attributes
     *
     * @param tag String giving the name of the element
     * @param text String contents of the element.
     * @param attrib Map<String, String> holding key, value attribute pairs.
     * Ignored if null.
     * @return this XML document
     */
    public XML add(String tag, String text, Map<String, String> attrib) {
        start(tag, attrib);

        if (text != null) {
            Text t = root.createTextNode(text);

            current.appendChild(t);
        }

        end(tag);

        return this;
    }

    public XML add(final String tag, final String text, final String... attr) {
        start(tag, attr);
        if (text != null) {
            current.appendChild(root.createTextNode(text));
        }
        end(tag);
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

    /**
     * Add a copy of an existing node to the tree. May be from this document or
     * another one.
     *
     * @param source Source node
     * @param deep boolean true if child nodes are to be copied too
     */
    public XML add(Node source, boolean deep) {
        Node copy;
        if (source.getOwnerDocument() != root) {
            copy = root.importNode(source, deep);
        } else {
            copy = source.cloneNode(deep);
        }
        current.appendChild(copy);
        return this;
    }

    /**
     * Helper method to add an element with an int content
     *
     * @param tag String giving the name of the element
     * @param number int contents of the element
     * @return this XML document
     */
    public XML add(String tag, int number) {
        return add(tag, Integer.toString(number));
    }

    /**
     * Helper method to add an element with an float content
     *
     * @param tag String giving the name of the element
     * @param number float contents of the element
     * @return this XML document
     */
    public XML add(String tag, float number) {
        return add(tag, Float.toString(number));
    }

    /**
     * Helper method to add an element with a double content
     *
     * @param tag String giving the name of the element
     * @param number double content of the element
     * @return this XML document
     */
    public XML add(String tag, double number) {
        return add(tag, Double.toString(number));
    }

    /**
     * Helper method to add an element with a boolean content
     *
     * @param tag String giving the name of the element
     * @param bool boolean content of the element
     * @return this XML document
     */
    public XML add(String tag, boolean bool) {
        return add(tag, Boolean.toString(bool));
    }

    /**
     * Helper method to add an element with a long content
     *
     * @param tag String giving the name of the element
     * @param number long content of the element
     * @return this XML document
     */
    public XML add(String tag, long number) {
        return add(tag, Long.toString(number));
    }

    public XML add(XML xml) {
        Node copy = root.importNode(xml.root.getDocumentElement(), true);

        current.appendChild(copy);

        return this;
    }

    /**
     * Helper method to add an element with an Object content. The method
     * depends on the Object having a useful toString.
     *
     * @param tag String giving the name of the element
     * @param object Object contents of the element
     * @return this XML document
     */
    public XML add(String tag, Object object) {
        if (object != null) {
            return add(tag, object.toString());
        } else {
            return add(tag, null);
        }

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
     * Add to list of elements that should be emmitted as CDATA sections
     */
    public void addCdata(String elementName) {
        cdatas.add(elementName);
    }

    /**
     * Transform the XML to a string reprensentation, making sure that the
     * output is well formed
     *
     * @return a string reprenentation of the XML document
     */
    @Override
    public String toString() {
        return toString(false, true);
    }

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
            if (cdatas.size() > 0) {
                StringBuilder cdata = new StringBuilder();
                for (int i = 0; i < cdatas.size(); i++) {
                    cdata.append(cdatas.get(i));
                    cdata.append(" ");
                }
                transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, cdata.toString());
            }

            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(root);

            transformer.transform(source, result);

            return result.getWriter().toString();
        } catch (TransformerException ex) {
            log.error("Can't convert to string: {}", ex.getMessage(), ex);

            return "<error>Can't produce xml</error>";
        }
    }
}
