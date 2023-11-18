/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.oauth2;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Objects;

import xyz.warp03.auth.util.RequestUtil;

public class OAuth2Provider implements java.io.Serializable {

	private static final long serialVersionUID = 1L;


	private final URI authorizationURL;
	private final URI tokenURL;

	public OAuth2Provider(URI authorizationURL, URI tokenURL){
		this.authorizationURL = Objects.requireNonNull(authorizationURL);
		this.tokenURL = Objects.requireNonNull(tokenURL);
	}


	public OAuth2Session newSession(OAuth2Client client, String requestedScope){
		OAuth2Session session = new OAuth2Session(this, client);
		session.setScope(requestedScope);
		return session;
	}


	public URI constructAuthorizationURL(String... queryParameters){
		return RequestUtil.appendQueryParameters(this.authorizationURL, queryParameters);
	}


	public URI getAuthorizationURL(){
		return this.authorizationURL;
	}

	public URI getTokenURL(){
		return this.tokenURL;
	}
}
