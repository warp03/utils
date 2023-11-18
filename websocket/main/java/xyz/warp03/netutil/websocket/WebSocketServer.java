/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.server.NetServer;
import org.omegazero.net.socket.SocketConnection;

import xyz.warp03.netutil.websocket.http.HTTPMessage;
import xyz.warp03.netutil.websocket.http.HTTPUtil;
import xyz.warp03.netutil.websocket.http.InvalidMessageException;

public class WebSocketServer {

	private static final Logger logger = LoggerUtil.createLogger();


	private Map<String, String> additionalHeaders = new HashMap<>();

	private Function<String[], String> onProtocolRequest;
	private BiFunction<URI, HTTPMessage, HTTPMessage> onRequest;
	private Consumer<WebSocketChannel> onClient;

	/**
	 * Creates a new WebSocket server.<br>
	 * <br>
	 * This instance is intended to be used with a {@link NetServer}, which accepts {@link SocketConnection}s that must then be passed to
	 * {@link #newConnection(SocketConnection)}.
	 */
	public WebSocketServer() {
	}


	/**
	 * Sets a HTTP header that will be sent in <i>all</i> HTTP responses by this <code>WebSocketServer</code>.<br>
	 * <br>
	 * Note that a header set here is only set in a response if it does not exist in the response already.
	 * 
	 * @param key   The HTTP header name
	 * @param value The HTTP header value
	 */
	public void setAdditionalHeader(String key, String value) {
		if(value != null)
			this.additionalHeaders.put(Objects.requireNonNull(key), value);
		else
			this.additionalHeaders.remove(key);
	}

	/**
	 * Sets a callback that is called when a handshake request containing a <code>Sec-WebSocket-Protocol</code> header is received by a client.<br>
	 * <br>
	 * The first argument of the callback is the array of protocols offered by the client. If the return value of the callback function is not <code>null</code>, it will be
	 * the value of the <code>Sec-WebSocket-Protocol</code> header in the response; otherwise, this header is not sent in the response.
	 * 
	 * @param onProtocolRequest The callback
	 */
	public void setOnProtocolRequest(Function<String[], String> onProtocolRequest) {
		this.onProtocolRequest = onProtocolRequest;
	}

	/**
	 * Sets a callback that is called when a HTTP handshake request is received by a client.<br>
	 * <br>
	 * The first argument of the callback is the request {@link URI}, the second argument is the {@link HTTPMessage} representing the request. If the callback function returns
	 * a new <code>HTTPMessage</code> object, the handshake process is aborted and the returned <code>HTTPMessage</code> is returned to the client. If the handshake process
	 * should continue, the callback function must return <code>null</code>.
	 * 
	 * @param onRequest The callback
	 * @see HTTPUtil#newResponse(int)
	 * @see HTTPUtil#newResponse(int, byte[])
	 */
	public void setOnRequest(BiFunction<URI, HTTPMessage, HTTPMessage> onRequest) {
		this.onRequest = onRequest;
	}

	/**
	 * Sets a callback that is called when a WebSocket handshake with a client completed successfully.<br>
	 * <br>
	 * The first argument is a new {@link WebSocketChannel} representing the WebSocket channel to the client.
	 * 
	 * @param onClient The callback
	 */
	public void setOnClient(Consumer<WebSocketChannel> onClient) {
		this.onClient = onClient;
	}


	/**
	 * Accepts a {@link SocketConnection} to be processed by this <code>WebSocketServer</code>. This <i>must</i> be a <code>SocketConnection</code> instance received through
	 * the callback set in {@link NetServer#setConnectionCallback(Consumer)}, otherwise behavior is undefined.<br>
	 * <br>
	 * Upon passing the <code>SocketConnection</code> instance to this method, the <code>onData</code> and <code>onClose</code> events are managed by the WebSocket
	 * implementation and must not be reset by the application.
	 * 
	 * @param connection A <code>SocketConnection</code> received through {@link NetServer#setConnectionCallback(Consumer)}
	 */
	public void newConnection(SocketConnection connection) {
		connection.setOnData((data) -> {
			if(this.onClient == null)
				return;
			WebSocketChannel channel = this.processClientRequest(connection, data);
			if(channel != null)
				this.onClient.accept(channel);
		});
	}

	private void respondHTTP(SocketConnection connection, HTTPMessage http) {
		for(Map.Entry<String, String> header : this.additionalHeaders.entrySet()){
			if(!http.headerExists(header.getKey()))
				http.setHeader(header.getKey(), header.getValue());
		}
		connection.write(http.toBytes());
	}

	private WebSocketChannel processClientRequest(SocketConnection connection, byte[] data) {
		try{
			HTTPMessage request = HTTPUtil.parseMessage(data);
			if(request == null)
				throw new InvalidMessageException("Invalid HTTP request");
			URI requestURI = HTTPUtil.getGETRequestURI(request);

			if(!"websocket".equalsIgnoreCase(request.getHeader("upgrade")))
				throw new InvalidMessageException("Expected upgrade websocket header");
			String connHeader = request.getHeader("connection");
			if(connHeader == null || !connHeader.toLowerCase().contains("upgrade"))
				throw new InvalidMessageException("Expected upgrade in connection header");

			String keyStr = request.getHeader("sec-websocket-key");
			if(keyStr == null)
				throw new InvalidMessageException("Missing sec-websocket-key header");
			try{
				if(Base64.getDecoder().decode(keyStr).length != 16)
					throw new InvalidMessageException("sec-websocket-key is not 16 bytes");
			}catch(IllegalArgumentException e){
				throw new InvalidMessageException("sec-websocket-key is invalid", e);
			}

			if(!"13".equals(request.getHeader("sec-websocket-version")))
				throw new InvalidMessageException("Unsupported WebSocket version");

			String nproto = null;
			String protos = request.getHeader("sec-websocket-protocol");
			if(protos != null && this.onProtocolRequest != null){
				String[] opts = protos.split(",");
				for(int i = 0; i < opts.length; i++)
					opts[i] = opts[i].trim();
				nproto = this.onProtocolRequest.apply(opts);
			}

			if(this.onRequest != null){
				HTTPMessage errResp = this.onRequest.apply(requestURI, request);
				if(errResp != null){
					this.respondHTTP(connection, errResp);
					return null;
				}
			}

			String acceptStr = keyStr + WSCommon.WS_ACCEPT_STRING;
			MessageDigest md;
			try{
				md = MessageDigest.getInstance("SHA-1");
			}catch(NoSuchAlgorithmException e){
				throw new RuntimeException(e);
			}
			md.update(acceptStr.getBytes(StandardCharsets.ISO_8859_1));
			String keyhash = Base64.getEncoder().encodeToString(md.digest());

			HTTPMessage response = HTTPUtil.newResponse(101);
			response.setHeader("upgrade", "websocket");
			response.setHeader("connection", "upgrade");
			response.setHeader("sec-websocket-accept", keyhash);
			if(nproto != null)
				response.setHeader("sec-websocket-protocol", nproto);
			this.respondHTTP(connection, response);

			WebSocketChannel wsc = new WebSocketChannel(connection, false, requestURI);
			wsc.handshakeComplete(nproto);
			return wsc;
		}catch(InvalidMessageException e){
			logger.debug("Invalid request from ", connection.getApparentRemoteAddress(), ": ", e.getMessage());
			HTTPMessage response = HTTPUtil.newResponse(400, "Bad Request".getBytes(StandardCharsets.UTF_8));
			response.setHeader("content-type", "text/plain; utf-8");
			this.respondHTTP(connection, response);
			return null;
		}
	}
}
