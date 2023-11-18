/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.omegazero.common.util.ArrayUtil;

public class HTTPUtil {

	private static final byte[] HTTP1_HEADER_END = new byte[] { 0x0d, 0x0a, 0x0d, 0x0a };

	public static HTTPMessage newRequest(String method, String path) {
		return new HTTPMessage(method + " " + path + " HTTP/1.1");
	}

	public static HTTPMessage newResponse(int status) {
		return newResponse(status, null);
	}

	public static HTTPMessage newResponse(int status, byte[] data) {
		return new HTTPMessage("HTTP/1.1 " + status, data);
	}

	public static HTTPMessage parseMessage(byte[] data) {
		int headerEnd = ArrayUtil.byteArrayIndexOf(data, HTTP1_HEADER_END);
		if(headerEnd < 0)
			return null;

		String headerData = new String(data, 0, headerEnd);
		int startLineEnd = headerData.indexOf("\r\n");
		if(startLineEnd < 0)
			return null;
		String startLine = headerData.substring(0, startLineEnd);

		for(int i = 0; i < startLine.length(); i++){
			char c = startLine.charAt(i);
			if(c < 32 || c >= 127)
				return null;
		}

		byte[] payload = null;
		int dataStart = headerEnd + HTTP1_HEADER_END.length;
		if(dataStart < data.length)
			payload = Arrays.copyOfRange(data, dataStart, data.length);

		HTTPMessage http = new HTTPMessage(startLine, payload);

		String[] headerLines = headerData.substring(startLineEnd + 2).split("\r\n");
		for(String headerLine : headerLines){
			int sep = headerLine.indexOf(':');
			if(sep < 0)
				return null;
			http.setHeader(headerLine.substring(0, sep).trim().toLowerCase(), headerLine.substring(sep + 1).trim());
		}

		return http;
	}

	public static int getResponseStatusCode(HTTPMessage msg) throws InvalidMessageException {
		String version;
		int status;
		try{
			version = msg.getStartLinePart(0);
			status = Integer.parseInt(msg.getStartLinePart(1));
		}catch(IndexOutOfBoundsException | NumberFormatException e){
			throw new InvalidMessageException("Invalid HTTP response", e);
		}
		if(!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0"))
			throw new InvalidMessageException("Invalid HTTP version: " + version);
		return status;
	}

	public static URI getGETRequestURI(HTTPMessage msg) throws InvalidMessageException {
		URI uri;
		String version;
		try{
			if(!msg.getStartLinePart(0).equals("GET"))
				throw new InvalidMessageException("Expected request method GET");
			uri = new URI(msg.getStartLinePart(1));
			version = msg.getStartLinePart(2);
		}catch(IndexOutOfBoundsException | URISyntaxException e){
			throw new InvalidMessageException("Invalid HTTP request", e);
		}
		if(!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0"))
			throw new InvalidMessageException("Invalid HTTP version: " + version);
		return uri;
	}
}
