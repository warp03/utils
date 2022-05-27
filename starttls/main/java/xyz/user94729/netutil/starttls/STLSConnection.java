/*
 * Copyright (C) 2021 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.netutil.starttls;

import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.omegazero.net.socket.SocketConnection;
import org.omegazero.net.socket.impl.TLSConnection;

public class STLSConnection extends TLSConnection {

	/**
	 * Creates a new <code>STLSConnection</code> (StartTLS connection) based on the given <b>transport</b>, which is usually a
	 * {@link org.omegazero.net.socket.impl.PlainConnection} where a STARTTLS request was issued.<br>
	 * <br>
	 * The <code>onData</code> and <code>onClose</code> events of the <b>transport</b> are used by the STARTTLS implementation and must not be used by the application after
	 * calling this constructor; those events should be set on this instance instead. All other applicable events emitted from this instance default to being handled by the
	 * <b>transport</b>, but may be overridden.<br>
	 * <br>
	 * When a TLS handshake successfully completes, the <code>onConnect</code> event is emitted.
	 * 
	 * @param transport            The connection this StartTLS connection is based on
	 * @param sslContext           The {@link SSLContext}
	 * @param client               Whether this is a client
	 * @param alpnNames            The offered or requested ALPN protocol names
	 * @param requestedServerNames The requested server names through SNI. Only applicable if <b>client</b> is <code>true</code>
	 * @throws IOException
	 */
	public STLSConnection(SocketConnection transport, SSLContext sslContext, boolean client, String[] alpnNames, String[] requestedServerNames) throws IOException {
		super(null, new STLSProvider(transport), transport.getRemoteAddress(), sslContext, client, alpnNames, requestedServerNames);

		if(client)
			super.doTLSHandshake();
	}
}
