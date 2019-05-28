package nl.knmi.adaguc.services.esgfsearch.search;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import nl.knmi.adaguc.services.esgfsearch.LockOnQuery;
import nl.knmi.adaguc.services.esgfsearch.THREDDSCatalogBrowser;
import nl.knmi.adaguc.services.esgfsearch.search.cache.CacheItem;
import nl.knmi.adaguc.services.esgfsearch.search.cache.DiskCache;
import nl.knmi.adaguc.services.esgfsearch.search.catalog.CatalogChecker;
import nl.knmi.adaguc.tools.*;
import nl.knmi.adaguc.tools.http.exceptions.BadRequestException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import nl.knmi.adaguc.tools.JSONResponse.JSONResponseException;
import nl.knmi.adaguc.tools.MyXMLParser.XMLElement;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;


public class Search {

    private int maxAmountOfDataSetsInJSON = 250;
    private int searchCacheTimeValiditySec = 10 * 60;

    private int searchGetTimeOutMS = 15000;

    private String searchEndPoint;

    private final DiskCache diskCache;
    public final CatalogChecker catalogChecker;

    public Search(String searchEndPoint, String cacheLocation, ExecutorService getCatalogExecutor) {
        this.searchEndPoint = searchEndPoint;

        this.diskCache = new DiskCache(cacheLocation, searchCacheTimeValiditySec);

        this.catalogChecker = new CatalogChecker(getCatalogExecutor, diskCache);
    }

    public JSONResponse getFacets(String facets, String query, int pageNumber, int pageLimit) {

        try {
            LockOnQuery.lock(facets + query, 0);
            JSONResponse r = _getFacetsImp(facets, query, pageNumber, pageLimit);
            LockOnQuery.release(facets + query);
            return r;
        } catch (Exception e) {
            JSONResponse r = new JSONResponse();
            r.setException(e.getClass().getName(), e);

            e.printStackTrace();
            return r;
        }

    }

    private StringBuilder convertToESGFQuery(String query, StringBuilder esgfBaseQuery) {
        Debug.println("QUERY is " + query);

        KVPKey kvp = HTTPTools.parseQueryString(query);
        SortedSet<String> kvpKeys = kvp.getKeys();

        StringBuilder esgfQueryBuilder = new StringBuilder(esgfBaseQuery);

        for (String k : kvpKeys) {
            Vector<String> valueVector = kvp.getValue(k);

            switch (k.toLowerCase()) {
                case "time_start_stop":
                    String timeStartStopValue = valueVector.firstElement();

                    if (timeStartStopValue.length() == 9) break;
                    String[] timeStartStopValues = timeStartStopValue.split("/");

                    if (timeStartStopValues.length != 2) break;
                    int yearStart = Integer.parseInt(timeStartStopValues[0]);
                    int yearStop = Integer.parseInt(timeStartStopValues[1]);

                    if (yearStart < 0 || yearStart > 9999 ||
                            yearStop < 0 || yearStop > 9999) break;

                    esgfQueryBuilder
                            .append("start=")
                            .append(String.format("%04d", yearStart))
                            .append("-01-01T00:00:00Z&")
                            .append("end=")
                            .append(String.format("%04d", yearStop))
                            .append("-01-01T00:00:00Z&");
                    break;
                case "bbox":
                    String bboxValue = valueVector.firstElement();

                    if (bboxValue.length() <= 3) break;
                    String[] bboxValueItems = bboxValue.split(",");

                    if (bboxValueItems.length == 4) break;
                    esgfQueryBuilder
                            .append("bbox=%5B")
                            .append(bboxValue)
                            .append("%5D&");
                    break;
                case "query":
                    String freeTextValue = valueVector.firstElement();
                    if (freeTextValue.length() <= 0) break;

                    try {
                        esgfQueryBuilder
                                .append("query=")
                                .append(URLEncoder.encode(freeTextValue, "UTF-8"))
                                .append("&");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    for (String value : valueVector) {
                        try {
                            esgfQueryBuilder
                                    .append(k)
                                    .append("=")
                                    .append(URLEncoder.encode(value, "UTF-8"))
                                    .append("&");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
        return esgfQueryBuilder;
    }

    private JSONResponse _getFacetsImp(String facets, String query, int pageNumer, int searchLimit) throws JSONException {
        final String identifierPrefix = "ESGFSearch.getFacets";

        JSONResponse r = new JSONResponse();

        StringBuilder esgfQueryBuilder = new StringBuilder();
        esgfQueryBuilder
                .append("facets=")
                .append(facets == null ? "*&offset=" + (pageNumer * searchLimit) : facets)
                .append("&limit=")
                .append(searchLimit)
                .append("&sort=true&");

        if (query != null) {
            esgfQueryBuilder = convertToESGFQuery(query, esgfQueryBuilder);
        }

        String esgfQuery = esgfQueryBuilder.toString();

        Debug.println("Query is " + searchEndPoint + esgfQuery);

        String identifier = identifierPrefix + esgfQuery;

        String XML;
        String cachedXML = diskCache.get(identifier + ".xml");

        if (cachedXML == null) {
            String url = searchEndPoint + esgfQuery;
            try {
                XML = HTTPTools.makeHTTPGetRequestWithTimeOut(url, searchGetTimeOutMS);
                diskCache.set(identifier + ".xml", XML);
            } catch (MalformedURLException e2) {
                r.setException("MalformedURLException", e2, url);
                return r;
            } catch (IOException e2) {
                r.setException("IOException", e2, url);
                return r;
            }
        } else {
            XML = cachedXML;
        }

        MyXMLParser.XMLElement el = new MyXMLParser.XMLElement();

        try {
            el.parseString(XML);
        } catch (Exception e1) {
            Debug.errprintln("Unable to parse XML", e1);
            r.setErrorMessage("Unable to parse XML", 500);
            return r;
        }

        JSONObject facetsObj = new JSONObject();


        try {
            Vector<XMLElement> lst = el.get("response").getList("lst");

            try {
                Function<XMLElement, Function<String, Predicate<String>>> elementAttributeEquals = (element) -> (attribute) -> (comparison) -> {
                    try {
                        return element.getAttrValue(attribute).equals(comparison);
                    } catch (Exception e) {
                        return false;
                    }
                };

                for (XMLElement responseListItem : lst) {
                    if (!responseListItem.getAttrValue("name").equals("facet_counts")) continue;

                    Vector<XMLElement> facet_counts = responseListItem.getList("lst");
                    for (XMLElement facet_count : facet_counts) {
                        if (!facet_count.getAttrValue("name").equals("facet_fields")) continue;

                        Vector<XMLElement> facet_fields = facet_count.getList("lst");
                        for (XMLElement facet_field : facet_fields) {

                            Vector<XMLElement> facet_names = facet_field.getList("int");
                            SortedMap<String, Integer> sortedFacetElements = new TreeMap<>();

                            for (XMLElement facet_name : facet_names) {
                                sortedFacetElements.put(facet_name.getAttrValue("name"), Integer.parseInt(facet_name.getValue()));
                            }

                            JSONObject facetObject = new JSONObject();

                            sortedFacetElements.forEach(facetObject::put);
                            facetsObj.put(facet_field.getAttrValue("name"), facetObject);
                        }
                    }
                }
            } catch (Exception e) {
                r.setErrorMessage("No name attribute", 500);
                return r;
            }

            // NOTE might be a bug?
            facetsObj.put("time_start_stop", new JSONArray("[1850%2F1950]"));
            facetsObj.put("bbox", new JSONArray("[0]"));
            facetsObj.put("query", new JSONArray("[0]"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject result = new JSONObject();
        JSONObject responseObj = new JSONObject();

        result.put("response", responseObj);
        responseObj.put("limit", searchLimit);
        responseObj.put("query", query == null || !query.contains("clear=") ? query : "");

        JSONArray searchResults = new JSONArray();

        try {
            Vector<XMLElement> result1 = el.get("response").getList("result");

            for (XMLElement a : result1) {

                try {
                    if (!a.getAttrValue("name").equals("response")) continue;
                    responseObj.put("numfound", Integer.parseInt(a.getAttrValue("numFound")));
                    Vector<XMLElement> doclist = a.getList("doc");

                    for (XMLElement doc : doclist) {
                        String esgfurl = "";
                        String esgfid = "";
                        String esgfdatanode = "";
                        JSONObject searchResult = new JSONObject();

                        searchResults.put(searchResult);

                        Vector<XMLElement> arrlist = doc.getList("arr");
                        Vector<XMLElement> strlist = doc.getList("str");

                        for (XMLElement arr : arrlist) {
                            if (!arr.getAttrValue("name").equals("url")) continue;

                            String urlToCheck = arr.get("str").getValue().split("#")[0];
                            urlToCheck = urlToCheck.split("\\|")[0];
                            searchResult.put("url", urlToCheck);
                            esgfurl = urlToCheck;
                        }

                        for (XMLElement str : strlist) {
                            String splitString = str.getValue().split("\\|")[0];
                            switch (str.getAttrValue("name").toLowerCase()) {
                                case "id":
                                    esgfid = splitString;
                                    searchResult.put("esgfid", esgfid);
                                    break;
                                case "data_node":
                                    esgfdatanode = splitString;
                                    searchResult.put("data_node", esgfdatanode);
                                    break;
                            }
                        }

                        //Compose the unique id;
                        if (esgfdatanode.length() == 0) {
                            esgfdatanode = esgfurl;
                        }
                        searchResult.put("id", esgfdatanode + "::" + esgfid);
                    }

                } catch (Exception e) {
                    r.setErrorMessage("No name attribute", 500);
                    return r;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            responseObj.put("results", searchResults);
        } catch (JSONException e) {
            r.setException("JSONException unable to put response", e);
            return r;
        }

        try {
            result.put("facets", facetsObj);
        } catch (JSONException e) {
            r.setException("JSONException unable to put facets", e);
            return r;
        }

        r.setMessage(result.toString());

        return r;
    }

    private JSONObject makeJSONFromSearchQuery(String query, HttpServletRequest request) throws JSONResponse.JSONResponseException {

        JSONObject jsonresult = new JSONObject();
        try {
            //DOSTUFF

            LockOnQuery.lock(query, 0);
            JSONResponse r = _getFacetsImp(null, query, 0, maxAmountOfDataSetsInJSON);
            LockOnQuery.release(query);

            if (r.hasError()) {
                throw new JSONResponse.JSONResponseException(r);
            }
            JSONObject searchResults = (JSONObject) new JSONTokener(r.getMessage()).nextValue();
            long numFound = searchResults.getJSONObject("response").getLong("numfound");

            if (numFound > maxAmountOfDataSetsInJSON) {
                throw new JSONResponse.JSONResponseException("Too many results, maximum of " + maxAmountOfDataSetsInJSON + " datasets allowed.", 200);
            }

            jsonresult.put("numDatasets", searchResults.getJSONObject("response").getLong("numfound"));
            jsonresult.put("ok", "ok");//For client to check whether its all OK.

            KVPKey kvp = HTTPTools.parseQueryString(query);
            Vector<String> variableList = kvp.getValue("variable");

            String variableFilter = String.join("|", variableList);

            JSONArray results = searchResults.getJSONObject("response").getJSONArray("results");
            int numFiles = 0;
            int numDap = 0;
            long totalFileSize = 0;
            JSONArray catalogAggregation = new JSONArray();
            for (int j = 0; j < results.length(); j++) {
                try {
                    String url = results.getJSONObject(j).getString("url");


                    JSONArray files = THREDDSCatalogBrowser.browseThreddsCatalog(request, url, variableFilter, null);

                    if (files == null) throw new Exception("THREDDS Files null");

                    catalogAggregation.put(files.getJSONObject(0));

                    JSONArray flat = THREDDSCatalogBrowser.makeFlat(files);

                    for (int i = 0; i < flat.length(); i++) {

                        String openDAPURL = null;
                        String httpURL = null;
                        String fileSize;
                        JSONObject a = flat.getJSONObject(i);

                        try {
                            openDAPURL = a.getString("dapurl");
                            httpURL = a.getString("httpurl");
                            openDAPURL = a.getString("opendap");
                            httpURL = a.getString("httpserver");
                        } catch (JSONException ignored) {
                        }

                        try {
                            fileSize = a.getString("fileSize");
                            totalFileSize += Long.parseLong(fileSize);
                        } catch (Exception ignored) {
                        }

                        if (openDAPURL != null) {
                            numDap++;
                        }
                        if (httpURL != null) {
                            numFiles++;
                        }


                    }
                } catch (Exception e) {
                    Debug.errprintln("CATALOG Error for nr " + j + "): " + results.getJSONObject(j)
                                                                                  .getString("id"));
                    try {
                        JSONObject b = new JSONObject();
                        JSONArray children = new JSONArray();
                        b.put("catalogurl", results.getJSONObject(j).getString("url"));
                        b.put("children", children);
                        b.put("text", "undefined");

                        catalogAggregation.put(b);
                    } catch (Exception e2) {
                        Debug.printStackTrace(e2);
                    }
                }
            }
            jsonresult.put("query", query);
            jsonresult.put("text", query);
            jsonresult.put("numFiles", numFiles);
            jsonresult.put("numDap", numDap);
            jsonresult.put("cls", "folder");
            jsonresult.put("fileSize", totalFileSize);
            jsonresult.put("children", catalogAggregation);


        } catch (JSONException e) {
            Debug.printStackTrace(e);
            throw new JSONResponse.JSONResponseException("Unable to query", 200); //NOTE why 200?
        }
        return jsonresult;
    }


    public JSONResponse getSearchResultAsJSON(String query, HttpServletRequest request) {
        JSONResponse result = new JSONResponse(request);
        JSONObject jsonresult;

        try {
            jsonresult = makeJSONFromSearchQuery(query, request);
            result.setMessage(jsonresult);
        } catch (JSONResponseException e) {
            result.setException(e);
        }

        return result;
    }

    public String getSearchResultAsCSV(String query, HttpServletRequest request) {
        StringBuilder result = new StringBuilder();
        int numdatasets = 0;
        int numcatalogsfailed = 0;
        int numopendap = 0;
        int numgridftp = 0;
        int numhttpserver = 0;
        JSONObject jsonresult;

        try {
            jsonresult = makeJSONFromSearchQuery(query, request);

            JSONArray catalogs = jsonresult.getJSONArray("children");
            Debug.println("NR of catalogs:" + catalogs.length());
            for (int j = 0; j < catalogs.length(); j++) {
                String catalogURL = null;
                try {
                    catalogURL = catalogs.getJSONObject(j).getString("catalogurl");
                    for (int mode = 0; mode < 3; mode++) {
                        String text = catalogs.getJSONObject(j).getString("text");
                        JSONArray files = catalogs.getJSONObject(j).getJSONArray("children");

                        if (mode == 0) {
                            if (files.length() <= 0) throw new Exception("No records for this set.");

                            numdatasets++;
                            result.append("catalogurl;");
                            result.append(text);
                            result.append(";");
                            result.append(catalogURL);
                            result.append("\n");
                        }
                        for (int i = 0; i < files.length(); i++) {

                            try {
                                String name;
                                String file;
                                switch (mode) {
                                    case 0:
                                        //httpserver
                                        name = "httpurl;";
                                        file = files.getJSONObject(i).getString("httpserver");
                                        numhttpserver++;

                                        break;
                                    case 1:
                                        //opendap
                                        name = "dapurl;";
                                        file = files.getJSONObject(i).getString("opendap");
                                        numopendap++;

                                        break;
                                    case 2:
                                        //gridftp
                                        name = "gridftp;";
                                        file = files.getJSONObject(i).getString("gridftp");
                                        numgridftp++;

                                        break;
                                    default:
                                        continue;
                                }
                                result.append(name);
                                result.append(text);
                                result.append(";");
                                result.append(file);
                                result.append("\n");

                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception e) {
                    Debug.errprintln(e.getMessage());
                    //Debug.printStackTrace(e);
                    numcatalogsfailed++;
                    if (catalogURL == null) {
                        catalogURL = "undefined";
                    }
                    result.append("catalog_failed;");
                    result.append("undefined");
                    result.append(";");
                    result.append(catalogURL);
                    result.append("\n");
                }
            }

        } catch (JSONException e) {
            return e.getMessage();
        } catch (JSONResponseException e) {
            return e.getMessage();
        }


        String header = "type;dataset;link\n";
        header += "info;numdataset;" + numdatasets + "\n";
        header += "info;numhttpserver;" + numhttpserver + "\n";
        header += "info;numopendap;" + numopendap + "\n";
        header += "info;numgridftp;" + numgridftp + "\n";
        header += "info;numcatalogsfailed;" + numcatalogsfailed + "\n";
        header += "info;query;" + query + "\n";
        result.insert(0, header);
        return result.toString();
    }

    public JSONResponse addtobasket(String query, HttpServletRequest request) {
        Debug.errprintln("Add to basket not yet implemented");
        return null;
//    JSONResponse result = new JSONResponse(request);
//    
//    JSONObject jsonresult;
//    try {
//      jsonresult = makeJSONFromSearchQuery(query,request);
//      String message = jsonresult.toString();
//      
//      // TODO
////      try {
////        ImpactUser user = LoginManager.getUser(request);
////        String dataDir = user.getDataDir();
////        Tools.writeFile(dataDir+"/test.catalog", message);
////        
////      } catch (Exception e) {
////        e.printStackTrace();
////      }
//      
//      
//      result.setMessage(message);
//    } catch (Exception e1) {
//      result.setException("Unable to make a file list from the search query.",e1);
//    }
//
//
//    return result;
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String service, mode;
            try {
                service = HTTPTools.getHTTPParam(request, "service");
                mode = HTTPTools.getHTTPParam(request, "request");
            } catch (Exception ignored) {
                throw new BadRequestException();
            }

            String jsonp = HTTPTools.getHTTPParam(request, "jsonp", HTTPTools.getHTTPParam(request, "callback", null));
            String query = HTTPTools.getHTTPParam(request, "query", null);
            String facets = HTTPTools.getHTTPParam(request, "facet", null);

            int pageLimit = Integer.parseInt(HTTPTools.getHTTPParam(request, "pagelimit", "25"));
            int pageNr = Integer.parseInt(HTTPTools.getHTTPParam(request, "pagenumber", "0"));

            if (!service.equalsIgnoreCase("search")) return;

            if (mode.equalsIgnoreCase("getSearchResultAsCSV")) {
                String responseString = this.getSearchResultAsCSV(query, request);

                response.setContentType("text/plain");
                response.getOutputStream().print(responseString);
                return;
            }

            JSONResponse jsonResponse;
            switch (mode.toLowerCase()) {
                case "getfacets":
                    HttpSession session = request.getSession();

                    String savedQuery = (String) session.getAttribute("savedquery");
                    String newQuery = query;

                    // NOTE
                    //  Not sure if this was intended. Might require an error if null.
                    //  Legacy did not contain a null check of any kind, so if query is null, so will newQuery be
                    if (query != null) switch (query) {
                        case "clear=onload":
                            newQuery = savedQuery != null ? savedQuery : "";
                            break;
                        case "clear=clear":
                            newQuery = "";
                            break;
                    }

                    session.setAttribute("savedquery", newQuery);

                    jsonResponse = this.getFacets(facets, newQuery, pageNr, pageLimit);
                    break;
                case "checkurl":
                    jsonResponse = catalogChecker.checkURL(query, request);
                    break;
                case "addtobasket":
                    jsonResponse = this.addtobasket(query, request);
                    break;
                case "getSearchResultAsJSON":
                    jsonResponse = this.getSearchResultAsJSON(query, request);
                    break;
                default:
                    throw new UnsupportedOperationException("mode " + mode + " not supported");
            }
            jsonResponse.setJSONP(jsonp);
            response.setContentType(jsonResponse.getMimeType());
            response.getOutputStream().print(jsonResponse.getMessage());
        } catch (HttpStatusCodeException exception) {
            throw exception;
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }


}
