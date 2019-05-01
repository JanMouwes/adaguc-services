package nl.knmi.adaguc.services.esgfsearch;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import nl.knmi.adaguc.services.esgfsearch.search.Search;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.HTTPTools;
import nl.knmi.adaguc.tools.MyXMLParser;
import nl.knmi.adaguc.tools.MyXMLParser.XMLElement;
import nl.knmi.adaguc.tools.WebRequestBadStatusException;

public class THREDDSCatalogBrowser {
    static boolean measureTime = false;


    public static JSONArray makeFlat(JSONArray catalog) throws JSONException {
        JSONArray result = null;
        result = new JSONArray();
        _rec(catalog, result);
        return result;
    }

    private static void _rec(JSONArray catalog, JSONArray result) throws JSONException {
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject a = catalog.getJSONObject(i);
            JSONObject b = new JSONObject();
            JSONArray names = a.names();
            for (int j = 0; j < names.length(); j++) {
                String key = names.getString(j);
                if (key.equals("children") == false) {
                    b.put(key, a.get(key));
                    //Debug.println(a.getString(key));
                }

            }
            result.put(b);

            try {
                _rec(a.getJSONArray("children"), result);
            } catch (JSONException e) {

            }
        }
    }


    static class Service {
        String name;
        Vector<AccesType> accesTypes = new Vector<AccesType>();

        static class AccesType {
            String serviceType;
            String base;
        }

        void addAccessType(String serviceType, String base) {
            serviceType = serviceType.toLowerCase();
            if (serviceType.equals("opendap")) serviceType = "dapurl";
            if (serviceType.equals("httpserver")) serviceType = "httpurl";
            if (serviceType.equals("catalog")) serviceType = "catalogurl";
            AccesType accesType = new AccesType();
            accesType.serviceType = serviceType;
            accesType.base = base;
            //Debug.println("serviceType:"+serviceType);
            //Debug.println("base:"+base);
            accesTypes.add(accesType);
        }
    }

    public static JSONArray browseThreddsCatalog(HttpServletRequest request, String variableFilter, String textFilter) throws MalformedURLException, Exception {
        Debug.println("variableFilter=" + variableFilter);
        String nodeStr = request.getParameter("node");

        if (nodeStr != null) {nodeStr = URLDecoder.decode(nodeStr, "UTF-8");} else {
            throw new Exception("Invalid node argument given");//errorResponder.printexception("nodeStr="+nodeStr);return null;
        }
        if (nodeStr.indexOf("http") != 0) {
            throw new Exception("Invalid URL given");
        }
        return browseThreddsCatalog(request, nodeStr, variableFilter, textFilter);

    }

    public static JSONArray browseThreddsCatalog(HttpServletRequest request, String catalogURL, String variableFilter, String textFilter) throws MalformedURLException, Exception {
        long startTimeInMillis = Calendar.getInstance().getTimeInMillis();
        long timeStampBefore = startTimeInMillis;
        long timeStampAfter;
        String _catalogURL = HTTPTools.makeCleanURL(catalogURL);
        String rootCatalog = new URL(_catalogURL).toString();
        String path = new URL(rootCatalog).getFile();
        String hostPath = rootCatalog.substring(0, rootCatalog.length() - path.length());

        if (measureTime) {
            timeStampAfter = Calendar.getInstance().getTimeInMillis();
            Debug.println("TIME: URL and HOST check: (" + (timeStampAfter - timeStampBefore) + " ms)");
            timeStampBefore = timeStampAfter;
        }

        MyXMLParser.XMLElement catalogElement = new MyXMLParser.XMLElement();
        try {
            Debug.println("Getting " + rootCatalog);
            Debug.println("Getting " + catalogURL);

            //Try to get from local storage:
            Search esgfSearch = ESGFSearchRequestMapper.getESGFSearchInstance();//new Search(Configuration.VercSearchConfig.getEsgfSearchURL(),Configuration.getImpactWorkspace()+"/diskCache/");
            if (esgfSearch == null) {
                Debug.println("esgfSearch==null");
                return null;
            }
            String result = esgfSearch.catalogChecker.getCatalog(catalogURL);

            //Debug.println("catalogURL"+catalogURL);
            if (catalogURL.endsWith(".catalog")) {
                //Debug.println("OK");
                //Debug.println(result);
                JSONObject o = ((JSONObject) new JSONTokener(result).nextValue());
                JSONArray a = new JSONArray();
                a.put(o);
                return a;
            }
            if (measureTime) {
                timeStampAfter = Calendar.getInstance().getTimeInMillis();
                Debug.println("TIME: Catalog Get: (" + (timeStampAfter - timeStampBefore) + " ms)");
                timeStampBefore = timeStampAfter;
            }

            catalogElement.parseString(result);
        } catch (WebRequestBadStatusException web) {
            Debug.errprintln("Unable to load catalog: unable to GET:" + web.getMessage());
            throw web;
        } catch (Exception e1) {

            //MessagePrinters.emailFatalErrorMessage("Unable to load catalog: "+e1.getMessage(),rootCatalog);
            Debug.errprintln("Unable to load catalog: invalid XML");//+e1.getMessage());
            Debug.printStackTrace(e1);
            throw e1;
        }
        if (measureTime) {
            timeStampAfter = Calendar.getInstance().getTimeInMillis();
            Debug.println("TIME: XML String parsing: (" + (timeStampAfter - timeStampBefore) + " ms)");
            timeStampBefore = timeStampAfter;
        }
        Vector<Service> supportedServices = getSupportedServices(catalogElement.get("catalog"));
    
    /*DebugConsole.println("SupportedServices:");
    for(int j=0;j<supportedServices.size();j++){
      DebugConsole.println(supportedServices.get(j).name);
      for(int i=0;i<supportedServices.get(j).accesTypes.size();i++){
        DebugConsole.println("--"+supportedServices.get(j).accesTypes.get(i).serviceType+" with base "+supportedServices.get(j).accesTypes.get(i).base);
      }
    }*/

        if (measureTime) {
            timeStampAfter = Calendar.getInstance().getTimeInMillis();
            Debug.println("TIME: Supported Services: (" + (timeStampAfter - timeStampBefore) + " ms)");
            timeStampBefore = timeStampAfter;
        }


        JSONArray catalogJSON = new JSONArray();
    
    
    
    /*JSONObject b = new JSONObject();
    b.put("text", "bla");
    b.put("expanded", true);
    b.put("cls", "folder");
    JSONArray c = new JSONArray();
    JSONObject d = new JSONObject();
    d.put("text", "bla2");
    d.put("leaf", true);
    c.put(d);
    b.put("children",c);
    a.put(b);*/

        addDatasets(rootCatalog, hostPath, supportedServices, catalogJSON, catalogElement.get("catalog"), variableFilter, textFilter, null);
        if (measureTime) {
            timeStampAfter = Calendar.getInstance().getTimeInMillis();
            Debug.println("TIME: JSON Generated: (" + (timeStampAfter - timeStampBefore) + " ms)");
            timeStampBefore = timeStampAfter;
            long stopTimeInMillis = Calendar.getInstance().getTimeInMillis();
            Debug.println("TIME:  Finished parsing THREDDS catalog to JSON in (" + (stopTimeInMillis - startTimeInMillis) + " ms)");
        }
        return catalogJSON;

    }

    private static boolean checkNodeNameForFilter(String nodeName, String textFilter) {
        if (textFilter != null && nodeName != null) {
            textFilter = textFilter.toLowerCase();
            nodeName = nodeName.toLowerCase();
            if (textFilter.length() > 0) {
                String[] filters = textFilter.split("\\||\\+| ");
                for (int f = 0; f < filters.length; f++) {
                    if (nodeName.indexOf(filters[f]) == -1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean addDatasets(String rootCatalog, String hostPath, Vector<Service> supportedServices, JSONArray catalogJSON, XMLElement xmlElement, String variableFilter, String textFilter, XMLElement parent) throws Exception {

        Vector<XMLElement> datasets = xmlElement.getList("dataset");
        for (int j = 0; j < datasets.size(); j++) {

            XMLElement dataset = datasets.get(j);
            JSONArray c = new JSONArray();

            boolean succeeded = addDatasets(rootCatalog, hostPath, supportedServices, c, dataset, variableFilter, textFilter, xmlElement);

            if (c.length() > 0) {
                //Make a folder
                JSONObject folder = new JSONObject();
                String name = dataset.getAttrValue("name");
                catalogJSON.put(folder);
                folder.put("text", name);
                folder.put("catalogurl", rootCatalog);
                folder.put("children", c);
                folder.put("expanded", true);
                folder.put("cls", "folder");
                folder.put("variables", putVariableInfo(dataset));
                //folder.put("variables2",putVariableInfo(xmlElement));

                if (succeeded == false) {
                    Debug.errprint("Did not succeed!");
                    return false;
                }
            } else {
                //This node has no childs
                //if(dataset.getAttrValue("name").indexOf("tas")!=-1)
                {
                    //Create a leaf by default
                    JSONObject leaf = new JSONObject();
                    if (checkMaxChilds(catalogJSON)) return false;


                    leaf.put("leaf", true);

                    //DebugConsole.println("Leaf "+dataset.getAttrValue("name"));

                    //Try to find defined serviceName, if not defined pick the first occuring one.
                    String serviceName = null;
                    Service service = null;
                    String supportedServicesString = "";

                    try {
                        serviceName = dataset.get("serviceName").getValue();
                        //Debug.println("--serviceName:"+serviceName);
                        service = getServiceByName(serviceName, supportedServices);
                    } catch (Exception e) {
                        service = supportedServices.get(0);
                    }
                    ;
                    //DebugConsole.println("Service="+service.name);
                    if (service != null) {
                        for (int i = 0; i < service.accesTypes.size(); i++) {
                            try {
                                leaf.put(service.accesTypes.get(i).serviceType, hostPath + service.accesTypes.get(i).base + dataset
                                        .getAttrValue("urlPath"));
                                //Debug.println("--serviceType:"+service.accesTypes.get(i).serviceType);
                                if (supportedServicesString.length() > 0) supportedServicesString += ",";
                                supportedServicesString += service.accesTypes.get(i).serviceType;
                            } catch (Exception e) {

                            }
                        }
                    }

                    //Try to find additional servicenames based on access elements
                    try {
                        Vector<XMLElement> access = dataset.getList("access");
                        for (int k = 0; k < access.size(); k++) {
                            serviceName = access.get(k).getAttrValue("serviceName");

                            //Debug.println("serviceName:"+serviceName);
                            service = getServiceByName(serviceName, supportedServices);
                            if (service != null) {
                                for (int i = 0; i < service.accesTypes.size(); i++) {

                                    leaf.put(service.accesTypes.get(i).serviceType, hostPath + service.accesTypes.get(i).base + dataset
                                            .getAttrValue("urlPath"));
                                    if (supportedServicesString.length() > 0) supportedServicesString += ",";
                                    supportedServicesString += service.accesTypes.get(i).serviceType;
                                    //Debug.println("--accessServiceName:"+service.accesTypes.get(i).serviceType);
                                }
                            }
                        }

                    } catch (Exception e) {}

                    String nodeName = dataset.getAttrValue("name");
                    leaf.put("text", nodeName);// - ("+supportedServicesString+")");

                    String dataSize = "-";
                    long fileSize = 0;
                    try {
                        String units = dataset.get("dataSize").getAttrValue("units");
                        ;
                        String dataSizeValue = dataset.get("dataSize").getValue();
                        if (units.equals("Tbytes")) {
                            units = "T";
                            try {
                                fileSize = (long) Double.parseDouble(dataSizeValue) * 1024 * 1024 * 1024 * 1024;
                            } catch (Exception e) {}
                        } else if (units.equals("Gbytes")) {
                            units = "G";
                            try {
                                fileSize = (long) Double.parseDouble(dataSizeValue) * 1024 * 1024 * 1024;
                            } catch (Exception e) {}
                        } else if (units.equals("Mbytes")) {
                            units = "M";
                            try {
                                fileSize = (long) Double.parseDouble(dataSizeValue) * 1024 * 1024;
                            } catch (Exception e) {}
                        } else if (units.equals("Kbytes")) {
                            units = "K";
                            try {fileSize = (long) Double.parseDouble(dataSizeValue) * 1024;} catch (Exception e) {}
                        } else try {fileSize = (long) Double.parseDouble(dataSizeValue);} catch (Exception e) {}
                        dataSize = dataSizeValue + "" + units;
                    } catch (Exception e) {}
                    leaf.put("dataSize", dataSize);
                    leaf.put("fileSize", "" + fileSize);
                    // if(parent!=null){
                    // putVariableInfo(b,parent);
                    //}else{
                    JSONArray variables = putVariableInfo(dataset);
                    if (variables.length() == 0) {
                        variables = putVariableInfo(xmlElement);

                    }
                    leaf.put("variables", variables);
                    //}

                    boolean put = true;

                    if (variableFilter != null) {
                        try {
                            if (variableFilter.length() > 0) {
                                put = false;
                                JSONArray variableList = variables;
                                if (variableList.length() == 0) {
                                    put = true;
                                } else {
//                  boolean foundSomething=false;
                                    for (int v = 0; v < variableList.length(); v++) {
                                        try {
                                            if (variableList.getJSONObject(v)
                                                            .getString("name")
                                                            .matches(variableFilter)) {
                                                put = true;
//                        foundSomething=true;
                                                break;
                                            }
                                        } catch (Exception e) {
                                        }
                                    }
                                    //if(foundSomething==false)put=true;
                                }

                            }
                        } catch (Exception e) {
                            Debug.errprintln(e.getMessage());
                            put = true;
                        }
                    }

                    if (put) {
                        put = checkNodeNameForFilter(nodeName, textFilter);
                        if (put) catalogJSON.put(leaf);
                        //b.put("put",put);
                        // a.put(b);
                    }

                }
            }
            //addDatasets(c,datasets.get(j));
        }
        Vector<XMLElement> catalogRefs = xmlElement.getList("catalogRef");


        for (int j = 0; j < catalogRefs.size(); j++) {
            XMLElement catalogRef = catalogRefs.get(j);
            JSONObject b = new JSONObject();
            if (checkMaxChilds(catalogJSON)) return false;

            String nodeName = catalogRef.getAttrValue("xlink:title");
            boolean shouldPut = checkNodeNameForFilter(nodeName, textFilter);
            if (shouldPut) {
                catalogJSON.put(b);
                b.put("text", nodeName);
                b.put("expanded", true);

                String href = catalogRef.getAttrValue("xlink:href");

                b.put("cls", "folder");
                if (!href.startsWith("/")) {
                    String base = rootCatalog.substring(0, rootCatalog.lastIndexOf("/"));
                    String url = HTTPTools.makeCleanURL(base + "/" + href);
                    //b.put("id", HTTPTools.makeCleanURL(base+"/"+href));
                    b.put("cls", "folder");
                    b.put("leaf", false);
                    b.put("href", "?catalog=" + url);
                    b.put("catalogurl", url);
                } else {
                    String url = HTTPTools.makeCleanURL(hostPath + href);
                    b.put("cls", "file");
                    b.put("leaf", false);
                    b.put("href", "?catalog=" + url);
                    b.put("catalogurl", url);
                }
            }

        }
        return true;


    }

    private static JSONArray putVariableInfo(XMLElement dataset) throws JSONException {
        //Put variable info
        JSONArray variableInfos = new JSONArray();
        if (dataset != null) {
            try {

                Vector<XMLElement> variables = null;
                try {
                    variables = dataset.get("variables").getList("variable");
                } catch (Exception e) {
                }
                for (int j1 = 0; j1 < variables.size(); j1++) {
                    JSONObject varInfo = new JSONObject();
                    varInfo.put("name", variables.get(j1).getAttrValue("name"));
                    varInfo.put("vocabulary_name", variables.get(j1).getAttrValue("vocabulary_name"));
                    varInfo.put("long_name", variables.get(j1).getValue());
                    variableInfos.put(varInfo);
                }
            } catch (Exception e) {
                //e.printStackTrace()
            }
        }
        return variableInfos;
        //b.put("variables", variableInfos);

    }

    private static boolean checkMaxChilds(JSONArray a) throws JSONException {
        int maxNumberOfItems = 25000;
        if (a.length() > maxNumberOfItems) {
            JSONObject b = new JSONObject();
            a.put(b);
            b.put("text", "..too many items for catalog browser! Only " + maxNumberOfItems + " items shown ...");
            b.put("cls", "leaf");
            b.put("leaf", true);
            return true;
        }
        return false;
    }

    private static Service getServiceByName(String serviceType, Vector<Service> supportedServices) {
        for (int j = 0; j < supportedServices.size(); j++) {
            if (serviceType.equals(supportedServices.get(j).name)) return supportedServices.get(j);
        }
        return null;
    }

    private static void recursivelyWalkServiceElement(Vector<Service> supportedServices, Vector<XMLElement> v) {
        for (int j = 0; j < v.size(); j++) {
            String serviceType = "";
            try {
                serviceType = v.get(j).getAttrValue("serviceType");
            } catch (Exception e) {
            }

            if (!serviceType.equalsIgnoreCase("compound")) {
                try {
                    Service service = new Service();
                    service.name = v.get(j).getAttrValue("name");
                    service.addAccessType(serviceType, v.get(j).getAttrValue("base"));
                    supportedServices.add(service);
                } catch (Exception e) {

                }
            } else {
                try {

                    Service service = new Service();
                    service.name = v.get(j).getAttrValue("name");
                    Vector<XMLElement> w = v.get(j).getList("service");
                    for (int i = 0; i < w.size(); i++) {
                        service.addAccessType(w.get(i).getAttrValue("serviceType"), w.get(i).getAttrValue("base"));
                    }
                    supportedServices.add(service);
                } catch (Exception e) {

                }
                recursivelyWalkServiceElement(supportedServices, v.get(j).getList("service"));
            }

        }
    }

    private static Vector<Service> getSupportedServices(XMLElement catalogElement) {
        Vector<Service> supportedServices = new Vector<Service>();
        recursivelyWalkServiceElement(supportedServices, catalogElement.getList("service"));
        return supportedServices;
    }

}
