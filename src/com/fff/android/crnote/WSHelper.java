package com.fff.android.crnote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.commons.ssl.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;

public class WSHelper {
	public WSHelper(String url, String username, String password) {
		this.url = url;
		this.username = username;
		this.password = password;
		
		client = new DefaultHttpClient();

        String userAgent = "CrNote/" + CrNoteApp.appVersionName + "_" + CrNoteApp.appVersionCode +
        	" (Android " + CrNoteApp.androidVersion + ")";
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, userAgent);
	}
	
	public String getResponse() {
		return response;
	}
	
	public byte[] getBinResponse() {
		return binresponse;
	}

	public String getErrorMsg() {
		return errormsg;
	}
	
	public void setErrorMsg(String msg) {
		errormsg = msg;
	}

	public int getResponseCode() {
		return respcode;
	}
	
	public void addQueryParam(String name, String value) {
		queryparams.add(new BasicNameValuePair(name, value));
	}
	
	public void addParam(String name, String value) {
		params.add(new BasicNameValuePair(name, value));
	}
	
	public boolean get() {
		respcode = -1;
		errormsg = "";
		response = "";
		
		String queryString = "";
		if (!params.isEmpty()) {
			queryString += "?";
			for (NameValuePair p: params) {
				queryString = queryString.concat(p.getName() + "=" + 
						URLEncoder.encode(p.getValue()) + "&");
			}
		}
		if (!queryparams.isEmpty()) {
			if (params.isEmpty()) {
				queryString += "?";
			}
			for (NameValuePair qp: queryparams) {
				queryString = queryString.concat(qp.getName() + "=" + 
						URLEncoder.encode(qp.getValue()) + "&");
			}
		}
		
		HttpGet request = new HttpGet(url + queryString);
		Util.dLog("WSHelper-GET", url /* + queryString*/);
		request.addHeader("Authorization", "Basic " + getCredentials());
		return doRequest(request, url);
	}
	
	public boolean post() {
		respcode = -1;
		errormsg = "";
		response = "";
		
		String queryString = "";
		if (!queryparams.isEmpty()) {
			queryString += "?";
			for (NameValuePair qp: queryparams) {
				queryString = queryString.concat(qp.getName() + "=" + 
						URLEncoder.encode(qp.getValue()) + "&");
			}
		}
		Util.dLog("Posting:", url /* + queryString*/);
		HttpPost request = new HttpPost(url + queryString);
		request.addHeader("Authorization", "Basic " + getCredentials());
		
		if (!params.isEmpty()) {
			try {
				request.setEntity(new UrlEncodedFormEntity(params));
			} catch (UnsupportedEncodingException e) {
				Util.dLog("HTTP POST", "UnsupportedEncodingException " + e.getMessage());
				//e.printStackTrace();
				return false;
			}
		}
		request.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		return doRequest(request, url);
	}
	
	public boolean postFile(byte[] data) {
		respcode = -1;
		errormsg = "";
		response = "";
		
		String queryString = "";
		if (!queryparams.isEmpty()) {
			queryString += "?";
			for (NameValuePair qp: queryparams) {
				queryString = queryString.concat(qp.getName() + "=" + 
						URLEncoder.encode(qp.getValue()) + "&");
			}
		}
		
		//Util.dLog("Posting File:", url + queryString);
		
		HttpPost request = new HttpPost(url + queryString);
		request.addHeader("Authorization", "Basic " + getCredentials());
		
		ByteArrayEntity baentity = new ByteArrayEntity(data);
		baentity.setContentType("application/octet-stream");
		request.setEntity(baentity);
		request.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		return doRequest(request, url);
	}
	
	public boolean postFile(InputStream data, long len) {
		respcode = -1;
		errormsg = "";
		response = "";
		
		String queryString = "";
		if (!queryparams.isEmpty()) {
			queryString += "?";
			for (NameValuePair qp: queryparams) {
				queryString = queryString.concat(qp.getName() + "=" + 
						URLEncoder.encode(qp.getValue()) + "&");
			}
		}
		
		//Util.dLog("Posting File:", url + queryString);
		
		HttpPost request = new HttpPost(url + queryString);
		request.addHeader("Authorization", "Basic " + getCredentials());
		
		InputStreamEntity entity = new InputStreamEntity(data, len);
		entity.setContentType("application/octet-stream");
		request.setEntity(entity);
		request.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
		return doRequest(request, url);
	}
	
	private String getCredentials() {
		return Base64.encodeBase64StringNoChunk(username.concat(":").concat(password).getBytes());
	}

	private boolean doRequest(HttpUriRequest request, String url) {
		HttpResponse httpResponse;

		response = null;
		binresponse = null;

		try {
			httpResponse = client.execute(request);
		} catch (IOException e) {
			respcode = -1;
			errormsg = "Connection error";

			Util.dLog("HTTP doReq", "Connection Error: IOException " + e.getMessage());
			//e.printStackTrace();
			
			return false;
		}

		respcode = httpResponse.getStatusLine().getStatusCode();
		errormsg = httpResponse.getStatusLine().getReasonPhrase();

		HttpEntity entity = httpResponse.getEntity();

		if (entity != null) {
			Header ctype = entity.getContentType();
			String contentType = "";
			if (ctype != null) {
				contentType = ctype.getValue();
			}
			
			//Util.dLog("Content-Type received", contentType);

			// API MUST return content length; there is no streaming contents
			// for CrNote API.
			long clen = entity.getContentLength();

			if (clen < 0) {
				response = null;
				binresponse = null;
				//Util.dLog("HTTP resp-len", "Invalid value of content-length");
				//return false;
			} else if (clen == 0) {
				response = null;
				binresponse = null;
				return true;
			}

			if (contentType.endsWith("octet-stream")) {
				isTextResponse = false;
			} else {
				isTextResponse = true;
			}

			InputStream is;
			try {
				is = entity.getContent();
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				try {
					for (int i = 0; /*i < clen*/; i++) {
						int b = is.read();
						if (b < 0) {
							break;
						}
						output.write((byte)(b & 0xff));
					}
				} catch (IOException e) {
					errormsg = "Error while extracting response";
				}
				binresponse = output.toByteArray();
				response = new String(binresponse);
			} catch (IllegalStateException e1) {
				errormsg = "IllegalStateException while processing response";
				Util.dLog("HTTP doReq", "IllegalStateException " + e1.getMessage());
				//e1.printStackTrace();
				return false;
			} catch (IOException e1) {
				errormsg = "Error while processing response";
				Util.dLog("HTTP doReq", "IOException " + e1.getMessage());
				//e1.printStackTrace();
				return false;
			}
		}

		return true;
	}
	
	
	// URL to connect to
	private String url;
	
	// preemeptive auth parameters
	private String username;
	private String password;
	
	// request (GET/POST) parameters
	private ArrayList <NameValuePair> params = new ArrayList <NameValuePair>();
	
	// API Query string params
	private ArrayList <NameValuePair> queryparams = new ArrayList <NameValuePair>();
	
	// response
	private int respcode;
	private String response;
	private byte[] binresponse;
	public boolean isTextResponse = true;
	private String errormsg;
	
	// http client
	private HttpClient client;
 }
