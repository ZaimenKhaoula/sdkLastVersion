package org.sdci.sdk.models;

import java.util.HashMap;
import java.util.Map;

public class Request extends MessageContent {

	Map<String, String> headers;
	String method;
	String url;

	public Request(Map<String, String> headers, String url, String method, String body) {
		this.headers = headers;
		this.method = method;
		this.body = body;
		this.url = url;
	}
	public Request(String url, String method, String body) {
		this.headers = new HashMap<String, String>();
		this.method = method;
		this.body = body;
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(HashMap<String, String> headers) {
		this.headers = headers;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

}
