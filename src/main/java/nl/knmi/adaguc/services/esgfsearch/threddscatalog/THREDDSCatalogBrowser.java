package nl.knmi.adaguc.services.esgfsearch.threddscatalog;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import nl.knmi.adaguc.services.esgfsearch.ESGFSearchRequestMapper;
import nl.knmi.adaguc.services.esgfsearch.search.Search;
import nl.knmi.adaguc.tools.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import nl.knmi.adaguc.tools.MyXMLParser.XMLElement;

public class THREDDSCatalogBrowser {
    //    static boolean measureTime = false;
    static ServiceProvider serviceProvider;

    static ServiceProvider serviceProviderInstance() {
        if (serviceProvider == null) {
            serviceProvider = new ServiceProvider();
        }

        return serviceProvider;
    }

    /**
     * @param catalog Catalog to flatten
     *
     * @return Flattened catalog
     */
    public static JSONArray makeFlat(JSONArray catalog) {
        JSONArray result = new JSONArray();
        _rec(catalog, result);
        return result;
    }

    /**
     * Recursively flattens catalog. Steps into children when encountered
     *
     * @param catalog Catalog to flatten
     * @param result  Result-object where items are inserted into
     */
    private static void _rec(JSONArray catalog, JSONArray result) {
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject a = catalog.getJSONObject(i);
            JSONObject b = new JSONObject();
            JSONArray names = a.names();

            IntStream.range(0, names.length())
                     .mapToObj(names::getString)
                     .filter(key -> !key.equals("children"))
                     .forEach(key -> b.put(key, a.get(key)));

            result.put(b);

            try {
                _rec(a.getJSONArray("children"), result);
            } catch (JSONException ignored) {}
        }
    }


    public static JSONArray browseThreddsCatalog(HttpServletRequest request, String variableFilter, String textFilter) throws MalformedURLException, Exception {
        Debug.println("variableFilter=" + variableFilter);
        String nodeStr = request.getParameter("node");

        if (nodeStr == null) {
            throw new Exception("Invalid node argument given");
        }

        nodeStr = URLDecoder.decode(nodeStr, "UTF-8");

        if (nodeStr.indexOf("http") != 0) {
            throw new MalformedURLException("Invalid URL given");
        }

        return browseThreddsCatalog(nodeStr, variableFilter, textFilter);
    }

    public static JSONArray browseThreddsCatalog(String catalogURL, String variableFilter, String textFilter) throws MalformedURLException, Exception {
//        long startTimeInMillis = Calendar.getInstance().getTimeInMillis();
//        long timeStampBefore = startTimeInMillis;
//        long timeStampAfter;
        String _catalogURL = HTTPTools.makeCleanURL(catalogURL);
        String rootCatalog = new URL(_catalogURL).toString();
        String path = new URL(rootCatalog).getFile();
        String hostPath = rootCatalog.substring(0, rootCatalog.length() - path.length());

//        if (measureTime) {
//            timeStampAfter = Calendar.getInstance().getTimeInMillis();
//            Debug.println(String.format("TIME: URL and HOST check: (%d ms)", timeStampAfter - timeStampBefore));
//            timeStampBefore = timeStampAfter;
//        }

        MyXMLParser.XMLElement catalogElement = new MyXMLParser.XMLElement();
        try {
            Debug.println("Getting " + rootCatalog);
            Debug.println("Getting " + catalogURL);

            //Try to get from local storage:
            Search esgfSearch = ESGFSearchRequestMapper.getESGFSearchInstance(); //TODO use DI

            String result = esgfSearch.catalogChecker.getCatalog(catalogURL);

            if (catalogURL.endsWith(".catalog")) {
                JSONObject o = ((JSONObject) new JSONTokener(result).nextValue());
                JSONArray a = new JSONArray();
                a.put(o);
                return a;
            }
//            if (measureTime) {
//                timeStampAfter = Calendar.getInstance().getTimeInMillis();
//                Debug.println("TIME: Catalog Get: (" + (timeStampAfter - timeStampBefore) + " ms)");
//                timeStampBefore = timeStampAfter;
//            }

            catalogElement.parseString(result);
        } catch (WebRequestBadStatusException web) {
            Debug.errprintln("Unable to load catalog: unable to GET:" + web.getMessage());
            throw web;
        } catch (Exception e1) {
            Debug.errprintln("Unable to load catalog: invalid XML");
            Debug.printStackTrace(e1);
            throw e1;
        }

//        if (measureTime) {
//            timeStampAfter = Calendar.getInstance().getTimeInMillis();
//            Debug.println("TIME: XML String parsing: (" + (timeStampAfter - timeStampBefore) + " ms)");
//            timeStampBefore = timeStampAfter;
//        }

        Collection<Service> supportedServices = serviceProviderInstance().getSupportedServices(catalogElement.get("catalog"));

        //NOTE below is legacy code, not sure what it's for but it was commented out
    /*DebugConsole.println("SupportedServices:");
    for(int j=0;j<supportedServices.size();j++){
      DebugConsole.println(supportedServices.get(j).name);
      for(int i=0;i<supportedServices.get(j).accessTypes.size();i++){
        DebugConsole.println("--"+supportedServices.get(j).accessTypes.get(i).serviceType+" with base "+supportedServices.get(j).accessTypes.get(i).base);
      }
    }*/

//        if (measureTime) {
//            timeStampAfter = Calendar.getInstance().getTimeInMillis();
//            Debug.println("TIME: Supported Services: (" + (timeStampAfter - timeStampBefore) + " ms)");
//            timeStampBefore = timeStampAfter;
//        }

        JSONArray catalogJSON = new JSONArray();

        addDatasets(rootCatalog, hostPath, supportedServices, catalogJSON, catalogElement.get("catalog"), variableFilter, textFilter);

//        if (measureTime) {
//            timeStampAfter = Calendar.getInstance().getTimeInMillis();
//            Debug.println("TIME: JSON Generated: (" + (timeStampAfter - timeStampBefore) + " ms)");
//            timeStampBefore = timeStampAfter;
//            long stopTimeInMillis = Calendar.getInstance().getTimeInMillis();
//            Debug.println("TIME:  Finished parsing THREDDS catalog to JSON in (" + (stopTimeInMillis - startTimeInMillis) + " ms)");
//        }

        return catalogJSON;
    }

    private static boolean checkNodeNameForFilter(String nodeName, String textFilter) {
        if (textFilter != null && nodeName != null) {
            textFilter = textFilter.toLowerCase();
            nodeName = nodeName.toLowerCase();
            if (textFilter.length() > 0) {
                String[] filters = textFilter.split("[|+ ]");
                for (String filter : filters) {
                    if (nodeName.contains(filter)) continue;
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean addDatasets(String rootCatalog, String hostPath, Collection<Service> supportedServices, JSONArray catalogJSON, XMLElement xmlElement, String variableFilter, String textFilter) throws Exception {

        Parser variableParser = new Parser();
        Vector<XMLElement> datasets = xmlElement.getList("dataset");
        for (XMLElement dataset : datasets) {
            JSONArray c = new JSONArray();

            boolean succeeded = addDatasets(rootCatalog, hostPath, supportedServices, c, dataset, variableFilter, textFilter);

            if (c.length() > 0) {
                //Make a folder
                JSONObject folderObject = new JSONObject();
                String name = dataset.getAttrValue("name");
                catalogJSON.put(folderObject);
                folderObject.put("text", name);
                folderObject.put("catalogurl", rootCatalog);
                folderObject.put("children", c);
                folderObject.put("expanded", true);
                folderObject.put("cls", "folder");
                folderObject.put("variables", variableParser.parseVariables(dataset));

                if (!succeeded) {
                    Debug.errprintln("Did not succeed!");
                    return false;
                }
                continue;
            }
            //This node has no childs

            //Create a leaf by default
            JSONObject leaf = new JSONObject();
            if (checkMaxChildren(catalogJSON)) return false;

            leaf.put("leaf", true);

            //Try to find defined serviceName, if not defined pick the first occuring one.
            String serviceName;
            Service service;
            StringBuilder supportedServicesString = new StringBuilder();

            try {
                serviceName = dataset.get("serviceName").getValue();
                //Debug.println("--serviceName:"+serviceName);
                service = serviceProviderInstance().getServiceByName(serviceName, supportedServices);
            } catch (Exception e) {
                service = supportedServices.stream()
                                           .findFirst()
                                           .orElse(null);
            }

            if (service != null) {
                for (Service.AccessType accessType : service.accessTypes) {
                    try {
                        leaf.put(accessType.serviceType, hostPath + accessType.base + dataset
                                .getAttrValue("urlPath"));

                        if (supportedServicesString.length() > 0) supportedServicesString.append(",");
                        supportedServicesString.append(accessType.serviceType);
                    } catch (Exception ignored) { }
                }
            }

            serviceProviderInstance().findServiceNames(dataset).forEach((currentService) -> {
                String urlPath;
                try {
                    urlPath = dataset.getAttrValue("urlPath");
                } catch (Exception e) { return; }
                currentService.accessTypes.forEach(accessType -> {
                    String serviceType = accessType.serviceType;
                    String base = accessType.base;
                    leaf.put(serviceType, hostPath + base + urlPath);
                });
            });


            String nodeName = dataset.getAttrValue("name");
            leaf.put("text", nodeName);// - ("+supportedServicesString+")"); //NOTE this is the only time supportedServicesString is queried.

            String dataSize = "-";
            long fileSize = 0;

            try {
                String units = dataset.get("dataSize").getAttrValue("units");
                String dataSizeValue = dataset.get("dataSize").getValue();

                Tuple<String, Integer> dataSizeTuple = variableParser.parseDataSize(units);
                units = dataSizeTuple.getFirst();
                int power = dataSizeTuple.getSecond();

                fileSize = (long) (Double.parseDouble(dataSizeValue) * Math.pow(1024, power));

                dataSize = dataSizeValue + "" + units;
            } catch (Exception ignored) {}

            leaf.put("dataSize", dataSize);
            leaf.put("fileSize", String.valueOf(fileSize));


            JSONArray variables = variableParser.parseVariables(dataset);
            if (variables.length() == 0) {
                variables = variableParser.parseVariables(xmlElement);
            }
            leaf.put("variables", variables);

            boolean shouldPut = true;

            if (variableFilter != null) {
                try {
                    if (variableFilter.length() > 0 && variables.length() != 0) {
                        shouldPut = false;

                        for (int v = 0; v < variables.length(); v++) {
                            try {
                                if (!variables.getJSONObject(v)
                                              .getString("name")
                                              .matches(variableFilter)) {continue;}
                                shouldPut = true;
                                break;
                            } catch (Exception ignored) { }
                        }
                    }
                } catch (Exception e) {
                    Debug.errprintln(e.getMessage());
                    shouldPut = true;
                }
            }

            if (shouldPut && checkNodeNameForFilter(nodeName, textFilter)) {
                catalogJSON.put(leaf);
            }
        }

        Vector<XMLElement> catalogRefs = xmlElement.getList("catalogRef");

        for (XMLElement catalogRef : catalogRefs) {
            JSONObject b = new JSONObject();
            if (checkMaxChildren(catalogJSON)) return false;

            String nodeName = catalogRef.getAttrValue("xlink:title");
            boolean shouldPut = checkNodeNameForFilter(nodeName, textFilter);
            if (!shouldPut) continue;

            catalogJSON.put(b);

            b.put("text", nodeName);
            b.put("expanded", true);

            String href = catalogRef.getAttrValue("xlink:href");

            boolean startsWithStroke = href.startsWith("/"); // If it starts with a stroke, it's a file
            String base = (!startsWithStroke) ? rootCatalog.substring(0, rootCatalog.lastIndexOf("/")) + "/" : hostPath;
            String cls = (!startsWithStroke) ? "folder" : "file";

            String url = HTTPTools.makeCleanURL(base + href);

            b.put("cls", cls);
            b.put("leaf", false);
            b.put("href", "?catalog=" + url);
            b.put("catalogurl", url);
        }
        return true;
    }

    /**
     * @param a Array to check children of
     *
     * @return whether the max has been exceeded
     */
    private static boolean checkMaxChildren(JSONArray a) throws JSONException {
        final int maxNumberOfItems = 25000;
        final Predicate<JSONArray> maxChildrenExceeded = (array) -> (array.length() > maxNumberOfItems);

        if (!maxChildrenExceeded.test(a)) return false;

        JSONObject b = new JSONObject();
        b.put("text", String.format("..too many items for catalog browser! Only %d items shown ...", maxNumberOfItems));
        b.put("cls", "leaf");
        b.put("leaf", true);

        a.put(b);
        return true;
    }

}
