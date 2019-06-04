package nl.knmi.adaguc.services.esgfsearch.xml;


import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.ElementNotFoundException;
import nl.knmi.adaguc.tools.WebRequestBadStatusException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.stream.Collectors;


@SuppressWarnings("deprecation")
public class Parser {

    public enum Options {NONE, STRIPNAMESPACES}

    ;

    /**
     * XML attribute
     *
     * @author plieger
     */
    static public class XMLAttribute {
        private String value = null;
        private String name = null;
    }

    /**
     * XML element, the base to start parsing XML documents.
     *
     * @author plieger
     */
    @SuppressWarnings("Duplicates")
    static public class XMLElement {
        private List<XMLAttribute> attributes;
        private List<XMLElement> xmlElements;
        private String value = null;
        private String name = null;

        public XMLElement() {
            attributes = new ArrayList<>();
            xmlElements = new ArrayList<>();
        }

        public XMLElement(String name) {
            this();
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public List<XMLElement> getElements() {
            return xmlElements;
        }

        public List<XMLAttribute> getAttributes() {
            return attributes;
        }

        public String getName() {
            return name;
        }

        public XMLElement get(String s, int index) throws Exception {
            int NR = 0;
            for (XMLElement xmlElement : xmlElements) {
                if (xmlElement.name.equals(s)) {
                    if (NR == index) return xmlElement;
                    NR++;
                }
            }
            if (NR != 0) {
                throw new Exception(String.format("XML element \"%s\" with index \"%d\" out of bounds (%d available)", s, index, NR));
            }
            throw new Exception(String.format("XML element \"%s\" with index \"%d\" not found", s, index));
        }

        public XMLElement get(String s) throws Exception {
            return get(s, 0);
        }

        public String getAttrValue(String name) throws Exception {
            for (int j = 0; j < attributes.size(); j++) {
                if (attributes.get(j).name.equals(name)) return attributes.get(j).value;
            }
            throw new Exception("XML Attribute \"" + name + "\" not found in element " + this.name);
        }

        public void add(XMLElement el) {
            this.xmlElements.add(el);
        }

        public void setAttr(String attrName, String attrValue) {
            for (XMLAttribute itAttr : this.attributes) {
                if (itAttr.name.equals(attrName)) {
                    itAttr.value = attrValue;
                    return;
                }
            }
            XMLAttribute at = new XMLAttribute();
            at.name = attrName;
            at.value = attrValue;
            this.attributes.add(at);
        }

        public void setValue(String value) {
            this.value = value;
        }

        /**
         * Parses document
         *
         * @param document
         */
        private void parse(Document document) {
            name = "root";
            value = "root";
            NodeList nodeLst = document.getChildNodes();
            parse(nodeLst);
        }

        /**
         * Parses a XML file on disk
         *
         * @param file XML file on disk
         *
         * @throws Exception
         */
        public void parseFile(String file) throws Exception {
            //Debug.println("Loading "+file);
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                InputStream inputStream = new FileInputStream(file);
                Document document = db.parse(inputStream);
                parse(document);
                inputStream.close();
            } catch (SAXException e) {
                String msg = "SAXException: " + e.getMessage();
                Debug.errprintln(msg);
                throw new SAXException(msg);
            } catch (IOException e) {
                String msg = "IOException:: " + e.getMessage();
                //Debug.errprintln(msg);
                throw new IOException(msg);
            } catch (Exception e) {
                String msg = "Exception: " + e.getMessage();
                Debug.errprintln(msg);
                throw new Exception(msg);
            }

        }

        /**
         * Parses XML string
         *
         * @param string The XML formatted string
         *
         * @throws Exception
         */
        public void parseString(String string) throws Exception {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();

                InputStream inputStream = new ByteArrayInputStream(string.getBytes());

                Document document = db.parse(inputStream);
                parse(document);
                inputStream.close();
            } catch (SAXException e) {
                String msg = "SAXException: " + e.getMessage() + ":\n\"";//+string+"\"";
                Debug.errprintln(msg);
                throw new SAXException(msg);
            } catch (IOException e) {
                String msg = "IOException: " + e.getMessage();
                Debug.errprintln(msg);
                throw new IOException(msg);
            } catch (Exception e) {
                String msg = "Exception: " + e.getMessage();
                Debug.errprintln(msg);
                throw new Exception(msg);
            }

        }

        /**
         * Parses remote XML file via URL
         *
         * @param url The URL to load
         *
         * @throws Exception
         */
        public void parse(URL url) throws WebRequestBadStatusException, Exception {
            //DebugConsole.println("Loading "+url);
            this.xmlElements.clear();
            HttpURLConnection connection = null;
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                long startTimeInMillis = Calendar.getInstance().getTimeInMillis();
                Debug.println("  Making XML GET: " + url.toString());
				/*if(Configuration.GlobalConfig.isInOfflineMode()==true){
		      if(url.getHost().equals("localhost")==false){
		        DebugConsole.println("Offline mode");
		        throw new Exception("Offline mode.");
		      }
		    }	*/
                connection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = connection.getInputStream();


                Document document = db.parse(inputStream);
                parse(document);
                inputStream.close();
                long stopTimeInMillis = Calendar.getInstance().getTimeInMillis();
                Debug.println("Finished XML GET: " + url.toString() + " (" + (stopTimeInMillis - startTimeInMillis) + " ms)");
            } catch (IOException e) {
                String msg = "IOException: " + e.getMessage() + " for URL " + url.toString();
                ;
                int statusCode = connection.getResponseCode();
                if (statusCode > 300) {
                    throw new WebRequestBadStatusException(statusCode);
                }
                Debug.errprintln("Status code: " + connection.getResponseCode());
                Debug.errprintln(msg);
                throw new IOException(msg);
            } catch (SAXException e) {
                //      Debug.printStackTrace(e);
                Debug.printStackTrace(e);
                String msg = "SAXException: " + e.getMessage() + " for URL " + url.toString();
                Debug.errprintln(msg);
                throw new SAXException(msg);
            } catch (Exception e) {
                String msg = "Exception: " + e.getMessage() + " for URL " + url.toString();
                ;
                Debug.errprintln(msg);
                throw new Exception(msg);
            }
        }

        /**
         * Function which does a POST request
         *
         * @param url  The URL
         * @param data The data to post
         *
         * @throws Exception
         */
        public void parse(URL url, String data) throws Exception {
            //DebugConsole.println("Loading "+url+" with data \n"+data);
            try {
                // Send data
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(data);
                wr.flush();

                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();

                Document document = db.parse(conn.getInputStream());
                parse(document);
                wr.close();

            } catch (SAXException e) {
                String msg = "SAXException: " + e.getMessage();
                Debug.errprintln(msg);
                throw new SAXException(msg);
            } catch (IOException e) {
                String msg = "IOException: " + e.getMessage();
                Debug.errprintln(msg);
                throw new IOException(msg);
            } catch (Exception e) {
                String msg = "Exception: " + e.getMessage();
                Debug.errprintln(msg);
                throw new Exception(msg);
            }
            Debug.println("Ready");
        }


        private void _parseJSON(JSONObject jsonObject, List<XMLElement> xmlElements, XMLElement child) throws Exception {
            JSONArray keys = jsonObject.names();
            if (keys == null) return;
            for (int i = 0; i < keys.length(); ++i) {
                String key = keys.getString(i);
                Object obj = jsonObject.get(key);
                if (obj instanceof JSONObject) {
                    JSONObject subJsonObject = (JSONObject) (obj);
                    if (key.equals("attr")) {
                        JSONArray attrKeys = subJsonObject.names();
                        for (int a = 0; a < attrKeys.length(); ++a) {
                            String attrKey = attrKeys.getString(a);
                            String attrValue = subJsonObject.getString(attrKey);
                            XMLAttribute attr = new XMLAttribute();
                            attr.name = attrKey;
                            attr.value = attrValue;
                            child.attributes.add(attr);
                        }
                    } else {
                        XMLElement newChild = new XMLElement();
                        xmlElements.add(newChild);
                        newChild.name = key;
                        _parseJSON(subJsonObject, newChild.xmlElements, newChild);
                    }
                } else if (obj instanceof JSONArray) {
                    JSONArray array = (JSONArray) obj;
                    for (int j = 0; j < array.length(); j++) {
                        XMLElement newChild = new XMLElement();
                        xmlElements.add(newChild);
                        newChild.name = key;
                        _parseJSON(array.getJSONObject(j), newChild.xmlElements, newChild);
                    }
                } else {
                    if (!obj.getClass().getName().equals("java.lang.String")) {
                        Debug.errprintln("JSONObject is not a string: " + key + "[" + obj.getClass().getName() + "]");
                        throw new Exception("JSONObject is not a string");
                    }
                    if (obj.getClass().getName().equals("java.lang.String") ||
                            obj.getClass().getName().equals("java.lang.JSONArray")) {
                        if (key.equals("value")) {
                            child.value = obj.toString();
                        }
                    }
                }
            }
        }

        public void parse(JSONObject jsonObject) throws Exception {
            if (jsonObject == null) {
                throw new Exception("Unable to parse empty jsonobject (is null)");
            }
            _parseJSON(jsonObject, xmlElements, null);
        }

        /**
         * Parses to our XMLElement XMLAttribute tree
         *
         * @param nodeLst
         */
        private void parse(NodeList nodeLst) {
            for (int s = 0; s < nodeLst.getLength(); s++) {
                Node fstNode = nodeLst.item(s);
                if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element fstElmnt = (Element) fstNode;
                    XMLElement child = new XMLElement();
                    xmlElements.add(child);
                    child.name = fstNode.getNodeName();
                    if (fstNode.hasAttributes()) {
                        for (int a = 0; a < fstNode.getAttributes().getLength(); a++) {
                            XMLAttribute attr = new XMLAttribute();
                            attr.name = fstNode.getAttributes().item(a).getNodeName();
                            attr.value = fstNode.getAttributes().item(a).getNodeValue();
                            child.attributes.add(attr);
                        }
                    }

                    if (fstElmnt.getChildNodes().item(0) != null) {
                        String nodeGetValue = fstElmnt.getChildNodes().item(0).getNodeValue();
                        if (nodeGetValue != null && nodeGetValue.length() > 0) {

                            child.value = nodeGetValue.trim();
                        }
                    }
                    NodeList childNodes = fstNode.getChildNodes();
                    if (childNodes.getLength() > 0) {
                        child.parse(childNodes);
                    }
                }
            }
        }

        public List<XMLElement> getList(String s) {
            return xmlElements.stream()
                              .filter(xmlElement -> xmlElement.name.equals(s))
                              .collect(Collectors.toCollection(ArrayList::new));
        }

        public XMLElement getFirst() {
            return xmlElements.get(0);
        }

        /**
         * Convert XML element to a string, can be any element from the tree. The XML header is not given.
         *
         * @param el    The element to convert to a string
         * @param depth Depth (can be zero).
         *
         * @return String of the XML element
         */
        public String toXML(XMLElement el, int depth) {
            StringBuilder data = new StringBuilder();
            if (el == null) return data.toString();
            for (int i = 0; i < depth; i++) data.append("  ");
            data.append("<")
                .append(el.name);
            for (int j = 0; j < el.getAttributes().size(); j++) {
                data.append(" ")
                    .append(el.getAttributes()
                              .get(j).name)
                    .append("=\"")
                    .append(StringEscapeUtils.escapeXml(el.getAttributes()
                                                          .get(j).value))
                    .append("\"");
            }
            data.append(">\n");
            for (int j = 0; j < el.xmlElements.size(); j++) {
                data.append(toXML(el.xmlElements.get(j), depth + 1));
            }
            if (el.getValue() != null) {
                if (el.getValue().length() > 0) {
                    for (int i = 0; i < depth; i++) data.append("  ");
                    data.append("  ").append(el.getValue()).append("\n");
                }
            }
            for (int i = 0; i < depth; i++) data.append("  ");
            data.append("</").append(el.name).append(">\n");
            return data.toString();

        }

        /**
         * Carriage returns in a JSON object are not allowed and should be replaced with the "\n" sequence
         *
         * @param in the unencoded JSON string
         *
         * @return the encoded JSON string
         */
        private String jsonEncode(String in) {
            //Debug.println(in);

            in = in.replaceAll("\r\n", ":carriagereturn:");
            in = in.replaceAll("\n", ":carriagereturn:");
            in = in.replaceAll("\\\\", "");
            in = in.replaceAll("\"", "\\\\\"");
            in = in.replaceAll(":carriagereturn:", "\\\\n");
            //      in = in.replaceAll("\\\\ ", " ");
            //      in = in.replaceAll("\\\\\\[", "[");
            //      in = in.replaceAll("\\\\\\]", "]");
            //      in = in.replaceAll("\\\\\\_", "_");
            //      in = in.replaceAll("\\\\\\:", ":");
            //      in = in.replaceAll("\\\\\\-", "-");
            //      in = in.replaceAll("\\\\\\+", "+");
            //      in = in.replaceAll("\\\\\\.", ".");
            //      in = in.replaceAll("\\\\\\,", ",");
            //      in = in.replaceAll("\\\\\\'", "'");


            // Debug.println(in);
            return in;
        }

        /**
         * Returns a String with the attributes of this XML element encoded to JSON
         *
         * @param el The XMLElement with attributes to convert to JSON formatted attributes
         *
         * @return String with JSON formatted data
         */
        private String printJSONAttributes(XMLElement el, Options options) {
            StringBuilder data = new StringBuilder();
            if (el.getAttributes().size() > 0) {
                data.append("\"attr\":{");
                for (int j = 0; j < el.getAttributes().size(); j++) {
                    if (j > 0) data.append(",");
                    String name = el.getAttributes().get(j).name;
                    if (options == Options.STRIPNAMESPACES) {
                        name = name.substring(name.indexOf(":") + 1);
                    }
                    name = jsonEncode(name);
                    data.append("\"")
                        .append(name)
                        .append("\":\"")
                        .append(jsonEncode(el.getAttributes().get(j).value))
                        .append("\"");
                }
                data.append("}");
            }
            return data.toString();
        }

        /**
         * Print the XMLElement's value in a JSON formatted way
         *
         * @param el The XMLElement value to be printed in a JSON formatted way
         *
         * @return String with JSON formatted data
         */
        private String printJSONValue(XMLElement el) {
            return el.getValue() != null && el.getValue().length() > 0 ?
                    "\"value\":\"" + jsonEncode(el.getValue()) + "\"" :
                    "";
        }

        /**
         * Converts a list of XML elements all with the same name to a JSON string.
         *
         * @param elements The list of XML elements with the same name
         * @param depth    The depth of the XML elements
         *
         * @return JSON formatted string
         */
        private String xmlElementstoJSON(List<XMLElement> elements, int depth, Options options) {
            StringBuilder data = new StringBuilder();
            String name = elements.get(0).name;

            if (options == Options.STRIPNAMESPACES) {
                name = name.substring(name.indexOf(":") + 1);
            }
            name = jsonEncode(name);

            data.append("\"").append(name).append("\":");
            boolean isArray = false;
            if (elements.size() > 1) isArray = true;

            if (isArray) {
                data.append("[\n");
            }
            for (int j = 0; j < elements.size(); j++) {
                if (j > 0) {
                    data.append(",\n");
                }
                data.append("{");
                data.append(toJSON(elements.get(j), depth + 1, options));
                data.append("}");
            }
            if (isArray) {
                data.append("]\n");
            }

            return data.toString();
        }

        /**
         * Converts a XML element to JSON string and walks through all nested XML elements
         *
         * @param el    The XML element to convert to a json string
         * @param depth The current depth of the XML element
         *
         * @return JSON string
         */
        private String toJSON(XMLElement el, int depth, Options options) {
            StringBuilder data = new StringBuilder();
            if (el == null) return data.toString();

            boolean firstDataDone = false;

            //Print the json attributes
            data.append(printJSONAttributes(el, options));
            if (el.attributes.size() > 0) firstDataDone = true;

            //Make a Set of the XML elements names
            Set<String> set = new HashSet<String>();
            for (int j = 0; j < el.xmlElements.size(); j++) {
                String name = el.xmlElements.get(j).getName();
                set.add(name);
            }

            //Loop through the XML elements with unique names
            for (String temp : set) {
                if (firstDataDone) { data.append(",\n"); }
                firstDataDone = true;
                data.append(xmlElementstoJSON(el.getList(temp), depth + 1, options));
            }
            //Clear and remove the set
            set.clear();
            set = null;

            //Print the JSON value
            String jsonValue = printJSONValue(el);
            if (jsonValue.length() > 0) {
                if (firstDataDone) { data.append(",\n"); }
                data.append(jsonValue);
            }

            return data.toString();
        }

        /**
         * Returns The XML object as a well formatted XML string.
         */
        public String toString() {
            StringBuilder data = new StringBuilder();
            data = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            for (XMLElement xmlElement : xmlElements) {
                data.append(toXML(xmlElement, 0));
            }
            return data.toString();
        }

        /**
         * Returns The XML object as a JSON string XML string.
         * Values are denoted as 'value' and attributes with 'attr'.
         */
        public String toJSON(Options options) {
            String data = "{\n";
            data += xmlElementstoJSON(xmlElements, 0, options);
            data += "\n}\n";
            return data;

        }


        /**
         * Converts the XML document to JSON
         *
         * @return JSONObject representing the XML
         *
         * @throws Exception
         */
        public JSONObject toJSONObject(Options options) throws Exception {
            //DebugConsole.println("Constructing JSON");
            String jsonString;
            try {
                jsonString = toJSON(options);
            } catch (Exception e) {
                e.printStackTrace();
                throw new Exception("Unable to convert XML to JSON: " + e.getMessage());
            }
            //DebugConsole.println("JSON constructed:"+jsonString);
            JSONObject jsonObject;
            try {
                jsonObject = (JSONObject) new JSONTokener(jsonString).nextValue();
            } catch (JSONException e) {
                Debug.printStackTrace(e);
                Debug.errprintln("Unable to tokenize JSON string to JSONObject \n" + jsonString);
                return null;
            }
            return jsonObject;
        }

        public String getNodeValue(String string) {

            String[] values = getNodeValues(string);
            if (values == null) return null;
            return values[0];
        }

        public String[] getNodeValues(String string) {
            String[] elements = string.split("\\.");
            int j = 0;
            XMLElement a = this;
            try {
                while (j < elements.length - 1) {
                    a = a.get(elements[j]);
                    j++;
                }
                ;
                List<XMLElement> b = a.getList(elements[j]);
                if (b.size() > 0) {
                    String[] results = new String[b.size()];
                    for (int i = 0; i < b.size(); i++) {
                        String value = b.get(i).getValue();

                        results[i] = value;
                    }
                    return results;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public String getNodeValueMustNotBeUndefined(String string) throws ElementNotFoundException {
            String nodeValue = getNodeValue(string);
            if (nodeValue == null) {
                throw new ElementNotFoundException(string);
            }
            return nodeValue;
        }

    }

    ;
}
