/*
 * Copyright (C) 2021 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.netutil.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import org.omegazero.net.client.NetClientManager;
import org.omegazero.net.client.params.ConnectionParameters;
import org.omegazero.net.client.params.TLSConnectionParameters;
import org.omegazero.net.socket.SocketConnection;

public final class WSUtil {


	private WSUtil() {
	}


	/**
	 * Creates a WebSocket client based on the given <b>clientManager</b> and <b>target</b> URL.<br>
	 * <br>
	 * This function only creates a <code>SocketConnection</code> with several default settings derived from the given URL and using that to create a new
	 * {@link WebSocketClient}. The caller must still call {@link SocketConnection#connect(int)} and {@link WebSocketClient#start()} to start the connection.
	 * 
	 * @param clientManager The client manager to create a connection with
	 * @param target        The target URL
	 * @return The new {@link WebSocketClient} instance
	 * @throws IOException
	 * @see NetClientManager#connection(ConnectionParameters)
	 * @see WebSocketClient#WebSocketClient(SocketConnection, URL)
	 */
	public static WebSocketClient createClient(NetClientManager clientManager, URL target) throws IOException {
		SocketAddress remote = new InetSocketAddress(InetAddress.getByName(target.getHost()), target.getDefaultPort());
		ConnectionParameters params;
		if(clientManager instanceof org.omegazero.net.client.TLSClientManager){
			TLSConnectionParameters tlsparams = new TLSConnectionParameters(remote);
			tlsparams.setAlpnNames(new String[] { "http/1.1" });
			tlsparams.setSniOptions(new String[] { target.getHost() });
			params = tlsparams;
		}else{
			params = new ConnectionParameters(remote);
		}
		SocketConnection conn = clientManager.connection(params);
		return new WebSocketClient(conn, target);
	}


	/**
	 * Sets a dummy {@link URLStreamHandlerFactory} creating {@link URLStreamHandler}s for <code>ws:</code> and <code>wss:</code> URL protocol schemes.
	 * 
	 * @see URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)
	 */
	public static void setDummyURLStreamHandlerFactory() {
		URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory(){

			@Override
			public URLStreamHandler createURLStreamHandler(String protocol) {
				if("ws".equals(protocol))
					return new WSURLStreamHandler(80);
				else if("wss".equals(protocol))
					return new WSURLStreamHandler(443);
				else
					return null;
			}
		});
	}


	private static class WSURLStreamHandler extends URLStreamHandler {

		private final int dp;

		public WSURLStreamHandler(int dp) {
			this.dp = dp;
		}

		@Override
		protected int getDefaultPort() {
			return this.dp;
		}

		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			throw new UnsupportedOperationException();
		}
	}
}
