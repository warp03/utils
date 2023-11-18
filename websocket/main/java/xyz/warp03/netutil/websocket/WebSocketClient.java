/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.omegazero.net.socket.SocketConnection;

import xyz.warp03.netutil.websocket.http.HTTPMessage;
import xyz.warp03.netutil.websocket.http.HTTPUtil;
import xyz.warp03.netutil.websocket.http.InvalidMessageException;

public class WebSocketClient extends WebSocketChannel {

	private final URL target;

	private String webSocketProtocol;
	private Map<String, String> additionalHeaders = new HashMap<>();

	private String wskeyStr;

	private Runnable onConnect;

	/**
	 * Creates a new WebSocket client based on the given <b>connection</b>.<br>
	 * <br>
	 * The <code>onData</code> and <code>onClose</code> events of the given <code>SocketConnection</code> are taken over by this class and must not be used or set after
	 * calling this constructor.<br>
	 * <br>
	 * To start the WebSocket handshake, {@link #start()} must be called.<br>
	 * <br>
	 * Note: To be able to create <code>URL</code> instances with <code>ws:</code> or <code>wss:</code> protocol schemes, an appropriate {@link java.net.URLStreamHandler} must
	 * be set, for example using {@link WSUtil#setDummyURLStreamHandlerFactory()}. If this is not possible because another factory is already set or other restrictions, the
	 * <code>http:</code> and <code>https:</code> protocol schemes may be used instead.
	 * 
	 * @param connection The connection to create a WebSocket connection on
	 * @param target     The URL where this WebSocket client should connect to
	 * @see WSUtil#createClient(org.omegazero.net.client.NetClientManager, URL)
	 */
	public WebSocketClient(SocketConnection connection, URL target) {
		super(connection, true, toResourceURI(target));
		this.target = target;

		this.connection.setOnData(this::responseData);
		this.connection.setOnClose(() -> {
			WebSocketClient.super.close0(-1);
		});

	}


	/**
	 * 
	 * @param webSocketProtocol The value of the <code>Sec-WebSocket-Protocol</code> header sent in the initial HTTP request. If <code>null</code> or not set, the header is
	 *                          omitted.
	 */
	public void setWebSocketProtocol(String webSocketProtocol) {
		this.webSocketProtocol = webSocketProtocol;
	}

	/**
	 * Sets a HTTP header which is sent in the initial HTTP request in addition to the default headers.
	 * 
	 * @param key   The name of the header
	 * @param value The value of the header
	 */
	public void setAdditionalHeader(String key, String value) {
		if(value != null)
			this.additionalHeaders.put(Objects.requireNonNull(key), value);
		else
			this.additionalHeaders.remove(key);
	}


	/**
	 * 
	 * @param onConnect The callback that is called when the WebSocket handshake completes (Server returns 101 response)
	 */
	public void setOnConnect(Runnable onConnect) {
		this.onConnect = onConnect;
	}



	/**
	 * Starts the WebSocket handshake by sending the initial HTTP request.
	 * 
	 * @throws IllegalStateException If the <code>SocketConnection</code> passed in the constructor is not connected
	 * @throws IllegalStateException If this method was called before
	 */
	public synchronized void start() {
		if(!super.connection.isConnected())
			throw new IllegalStateException("connection must be connected");
		if(this.wskeyStr != null)
			throw new IllegalStateException("start() was already called");
		byte[] wskey = new byte[16];
		new Random().nextBytes(wskey);
		this.wskeyStr = Base64.getEncoder().encodeToString(wskey);

		String rp = super.getResource().getRawPath();
		if(super.getResource().getRawQuery() != null)
			rp += "?" + super.getResource().getRawQuery();
		HTTPMessage request = HTTPUtil.newRequest("GET", rp);
		request.setHeader("user-agent", "u949-websocket-java");
		request.setHeader("host", this.target.getAuthority());
		request.setHeader("upgrade", "websocket");
		request.setHeader("connection", "upgrade");
		request.setHeader("sec-websocket-key", this.wskeyStr);
		request.setHeader("sec-websocket-version", "13");
		if(this.webSocketProtocol != null)
			request.setHeader("sec-websocket-protocol", this.webSocketProtocol);
		for(Map.Entry<String, String> header : this.additionalHeaders.entrySet()){
			request.setHeader(header.getKey(), header.getValue());
		}
		super.connection.write(request.toBytes());
	}

	private void responseData(byte[] data) throws IOException {
		HTTPMessage response = HTTPUtil.parseMessage(data);
		this.validateServerResponse(response);

		String wsProto = response.getHeader("sec-websocket-protocol");

		super.handshakeComplete(wsProto);
		if(this.onConnect != null)
			this.onConnect.run();

		// server may have sent data immediately after the response and it ended up in the same packet (onData event) as the HTTP response
		// (cant be a HTTP response body because 1xx responses must not have one)
		if(response.getData() != null)
			super.incomingData(response.getData());
	}

	private void validateServerResponse(HTTPMessage response) throws IOException {
		if(response == null)
			throw new InvalidMessageException("Invalid HTTP response");
		int status = HTTPUtil.getResponseStatusCode(response);
		if(status != 101)
			throw new IOException("Unexpected HTTP response code: " + status);
		if(!"websocket".equalsIgnoreCase(response.getHeader("upgrade")))
			throw new InvalidMessageException("Server response does not have upgrade websocket header");

		String serverAccept = response.getHeader("sec-websocket-accept");
		if(serverAccept == null)
			throw new InvalidMessageException("Server response is missing Sec-WebSocket-Accept header");
		String acceptStr = this.wskeyStr + WSCommon.WS_ACCEPT_STRING;
		MessageDigest md;
		try{
			md = MessageDigest.getInstance("SHA-1");
		}catch(NoSuchAlgorithmException e){
			throw new IOException(e);
		}
		md.update(acceptStr.getBytes(StandardCharsets.ISO_8859_1));
		String keyhash = Base64.getEncoder().encodeToString(md.digest());
		if(!serverAccept.equals(keyhash))
			throw new InvalidMessageException("Sec-WebSocket-Accept value is invalid: Got " + serverAccept + ", expected " + keyhash);

		String extStr = response.getHeader("sec-websocket-extensions");
		if(extStr != null && extStr.length() > 0)
			throw new InvalidMessageException("Sec-WebSocket-Extensions is set");
	}


	private static URI toResourceURI(URL target) {
		String p = target.getPath().replace(" ", "%20");
		if(p.length() < 1)
			p = "/";
		String query = target.getQuery();
		if(query != null)
			p += "?" + query;
		try{
			return new URI(p);
		}catch(URISyntaxException e){
			throw new IllegalArgumentException(e);
		}
	}
}
