package org.sdci.sdk.models;

import java.util.HashMap;
import java.util.Map;

public class Response extends MessageContent {
	Map<String, String> headers;
	String statusCode;

	public Response(Map<String, String> headers, String statusCode, String body) {
		this.headers = headers;
		this.statusCode = statusCode;
		this.body = body;
	}
	public Response( String statusCode, String body) {
		this.headers = new HashMap<String, String>();
		this.statusCode = statusCode;
		this.body = body;
	}

	public String getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(HashMap<String, String> headers) {
		this.headers = headers;
	}


}
