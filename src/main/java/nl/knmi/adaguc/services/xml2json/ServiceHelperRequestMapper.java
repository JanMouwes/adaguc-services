package nl.knmi.adaguc.services.xml2json;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.ietf.jgss.GSSException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import nl.knmi.adaguc.config.MainServicesConfigurator;
import nl.knmi.adaguc.security.AuthenticatorFactory;
import nl.knmi.adaguc.security.AuthenticatorInterface;
import nl.knmi.adaguc.security.PemX509Tools;
import nl.knmi.adaguc.security.PemX509Tools.X509UserCertAndKey;
import nl.knmi.adaguc.security.SecurityConfigurator;
import nl.knmi.adaguc.security.user.User;
import nl.knmi.adaguc.security.user.UserManager;
import nl.knmi.adaguc.services.adagucserver.ADAGUCServer;
import nl.knmi.adaguc.services.basket.Basket;
import nl.knmi.adaguc.services.joblist.JobListRequestMapper;
import nl.knmi.adaguc.services.pywpsserver.PyWPSServer;
import nl.knmi.adaguc.services.pywpsserver.PyWPSServer.WPSStatus;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.JSONResponse;
import nl.knmi.adaguc.tools.MyXMLParser;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.adaguc.tools.MyXMLParser.Options;
import nl.knmi.adaguc.tools.MyXMLParser.XMLElement;


@RestController
public class ServiceHelperRequestMapper {
	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, true);
		MappingJackson2HttpMessageConverter converter = 
				new MappingJackson2HttpMessageConverter(mapper);
		return converter;
	}
	@ResponseBody
	@CrossOrigin
	@RequestMapping("xml2json")
	public void XML2JSON(
			@RequestParam(value="request")String request,
			@RequestParam(value="callback", 
			required=false)String callback, HttpServletRequest servletRequest, HttpServletResponse response){
		/**
		 * Converts XML file pointed with request to JSON file
		 * @param requestStr
		 * @param out1
		 * @param response
		 */
		JSONResponse jsonResponse = new JSONResponse(servletRequest);
		String requestStr;
		try {
			requestStr=URLDecoder.decode(request,"UTF-8");
			MyXMLParser.XMLElement rootElement = new MyXMLParser.XMLElement();
			//Remote XML2JSON request to external WMS service
			System.err.println("Converting XML to JSON for "+requestStr);

			boolean isLocal = false;
			Debug.println("xml2json " + requestStr);
			if(requestStr.startsWith(MainServicesConfigurator.getServerExternalURL()) && requestStr.toUpperCase().contains("SERVICE=WMS")){
				Debug.println("Running local adaguc for ["+requestStr+"]");
				isLocal = true;
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				String url = requestStr.substring(MainServicesConfigurator.getServerExternalURL().length());
				url = url.substring(url.indexOf("?")+1);
				Debug.println("url = ["+url+"]");
				ADAGUCServer.runADAGUCWMS(servletRequest, null, url, outputStream);
				String getCapabilities = new String(outputStream.toByteArray());
				outputStream.close();
				rootElement.parseString(getCapabilities);
			}
			
			User user = null;
			X509UserCertAndKey userCertificate = null;
			String ts = null;
			char [] tsPass = null;
			if(isLocal == false){
				
				
				if(requestStr.startsWith("https://")){
					ts = SecurityConfigurator.getTrustStore();
				}
				if(ts!=null ){
					tsPass = SecurityConfigurator.getTrustStorePassword().toCharArray();

					Debug.println("Setting up user cert with truststore");

					

					AuthenticatorInterface authenticator = AuthenticatorFactory.getAuthenticator(servletRequest);
					if(authenticator!=null){
						
						try {
							user = UserManager.getUser(authenticator);
						} catch(Exception e) {

						}
						if(user!=null){
							userCertificate = user.getCertificate();
						}
					}
					String result = new String(makeRequest(requestStr, userCertificate, ts, tsPass));

					rootElement.parseString(result);
				}else{
					Debug.println("Running remote adaguc without truststore");

					rootElement.parse(new URL(requestStr));
				}
			}
			
			/* Hookup WPS request calls */
			if (requestStr.toUpperCase().contains("SERVICE=WPS")) {
				Debug.println("This is a WPS call");
				if (requestStr.toUpperCase().contains("REQUEST=EXECUTE")) {
					Debug.println("This is a WPS Execute call, store in jobs!");
					JobListRequestMapper.saveExecuteResponseToJob(requestStr, rootElement.toString(), servletRequest);
				}
			}
			
			/* Hookup WPS response calls */
			try{
				JSONObject test = PyWPSServer.statusLocationDataAsJSONElementToWPSStatusObject(null, rootElement.toJSONObject(Options.NONE));
				String wpsID = null;
				try{
					wpsID = test.getString("id");
				}catch(Exception e){
				}
				
				if (wpsID!=null && test.getString("wpsstatus").equals(PyWPSServer.WPSStatus.PROCESSSUCCEEDED.toString())) {
					Debug.println("============== OK WPS SUCCESFULLY FINISHED, START COPY TO BASKET ================ ");
					/* Parse outputs and copy them to local basket */
					Vector<XMLElement> processOutputs = rootElement.get("wps:ExecuteResponse").get("wps:ProcessOutputs").getList("wps:Output");
					for(int j=0;j<processOutputs.size();j++){
//						Debug.println(j + ")" + processOutputs.get(j).toString());
						String identifier = processOutputs.get(j).get("ows:Identifier").getValue();
						String title = processOutputs.get(j).get("ows:Title").getValue();
						
						Debug.println("Identifying " + identifier + "/" + title);
						String processFolder = test.getString("processid")+"_"+ test.getString("creationtime").replaceAll(":", "").replaceAll("-", "")+"_"+ wpsID;
						try {
							XMLElement refObj = processOutputs.get(j).get("wps:Reference");
							String reference = refObj.getAttrValue("href");
							String destLoc = user.getDataDir() + "/" + "/" + processFolder;
							String basketLocalFilename = FilenameUtils.getBaseName(reference) + "." + FilenameUtils.getExtension(reference);
							String fullPath = destLoc + "/" + basketLocalFilename;
							if (new File(fullPath).exists() == false) {
								Debug.println("Start copy " + reference);
								// TODO: ADD SECURITY CHECKS
								Tools.mksubdirs(destLoc);
								Tools.writeFile(fullPath, makeRequest(reference, userCertificate, ts, tsPass));
							} else {
								Debug.println("Already copied " + reference);
							}
							String basketRemoteURL = Basket.GetRemotePrefix(user) + processFolder + "/" + basketLocalFilename;
							refObj.setAttr("href", basketRemoteURL);
							
						}catch(Exception e){
							Debug.printStackTrace(e);
						}
						
						
						
					}
				}
				Debug.println(test.toString());
			}catch(Exception e){
				Debug.printStackTrace(e);
			}
			jsonResponse.setMessage(rootElement.toJSON(null));
		} catch (Exception e) {
			e.printStackTrace();
			jsonResponse.setException(e.getMessage(),e);
		}

	    try {
	      jsonResponse.print(response);
	    } catch (Exception e1) {

	    }

	}
	private byte[] makeRequest(String requestStr, X509UserCertAndKey userCertificate, String ts, char[] tsPass) throws KeyManagementException, UnrecoverableKeyException, InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, NoSuchProviderException, SignatureException, IOException, GSSException {
		try {
			/* First try without user certificate */
			Debug.println("First try without user certificate");
			CloseableHttpClient httpClient = (new PemX509Tools()).
					getHTTPClientForPEMBasedClientAuth(ts, tsPass, null);
			CloseableHttpResponse httpResponse = httpClient.execute(new HttpGet(requestStr));
			return EntityUtils.toByteArray(httpResponse.getEntity());
		} catch (Exception e){
			if (userCertificate!=null) {
			/* Second, try with user certificate */
				Debug.println("Second, try with user certificate");
				CloseableHttpClient httpClient = (new PemX509Tools()).
						getHTTPClientForPEMBasedClientAuth(ts, tsPass, userCertificate);
				CloseableHttpResponse httpResponse = httpClient.execute(new HttpGet(requestStr));
				return EntityUtils.toByteArray(httpResponse.getEntity());
				
			} else{
				Debug.println("Request without user certificate failed");
				throw(e);
			}
		}
	}



}

