/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class RequestUtil {


	private RequestUtil(){
	}


	public static String urlEncode(String... parameters){
		return urlEncode(false, parameters);
	}

	public static String urlEncode(boolean escape, String... parameters){
		StringBuilder queryBuilder = new StringBuilder();
		try{
			for(int i = 0; i < parameters.length - 1; i += 2){
				if(i > 0)
					queryBuilder.append('&');
				queryBuilder.append(escape ? URLEncoder.encode(parameters[i], "UTF-8") : parameters[i]);
				queryBuilder.append('=');
				queryBuilder.append(escape ? URLEncoder.encode(parameters[i + 1], "UTF-8") : parameters[i + 1]);
			}
		}catch(IOException e){
			throw new java.io.UncheckedIOException(e);
		}
		return queryBuilder.toString();
	}

	public static URI appendQueryParameters(URI oldUri, String... queryParameters){
		String newQuery = urlEncode(false, queryParameters); // escape = false needed here, weird URI constructor behavior
		String queryStr = oldUri.getQuery();
		if(queryStr == null)
			queryStr = newQuery;
		else
			queryStr += "&" + newQuery;
		try{
			return new URI(oldUri.getScheme(), oldUri.getAuthority(), oldUri.getPath(), queryStr, oldUri.getFragment());
		}catch(URISyntaxException e){
			throw new RuntimeException(e);
		}
	}


	public static JSONObject getJsonObject(URL url) throws IOException {
		return new JSONObject(getJsonAuthed(url, null));
	}

	public static JSONObject getJsonObjectAuthed(URL url, String authBearer) throws IOException {
		return new JSONObject(getJsonAuthed(url, authBearer));
	}

	public static JSONArray getJsonArray(URL url) throws IOException {
		return new JSONArray(getJsonAuthed(url, null));
	}

	public static JSONTokener getJsonAuthed(URL url, String authBearer) throws IOException {
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		httpConn.setDoInput(true);
		httpConn.setRequestMethod("GET");
		if(authBearer != null)
			httpConn.setRequestProperty("Authorization", "bearer " + authBearer);
		return new JSONTokener(httpConn.getResponseCode() < 400 ? httpConn.getInputStream() : httpConn.getErrorStream());
	}

	public static JSONObject postJsonFormEncoded(URL url, String... parameters) throws IOException {
		byte[] body = RequestUtil.urlEncode(parameters).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		httpConn.setDoInput(true);
		httpConn.setDoOutput(true);
		httpConn.setInstanceFollowRedirects(false);
		httpConn.setUseCaches(false);
		httpConn.setRequestMethod("POST");
		httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		httpConn.setRequestProperty("Content-Length", String.valueOf(body.length));
		httpConn.getOutputStream().write(body);
		return new JSONObject(new JSONTokener(httpConn.getResponseCode() < 400 ? httpConn.getInputStream() : httpConn.getErrorStream()));
	}
}
