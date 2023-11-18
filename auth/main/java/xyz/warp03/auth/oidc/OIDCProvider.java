/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;

import org.json.JSONObject;

import xyz.warp03.auth.oauth2.OAuth2Client;
import xyz.warp03.auth.oauth2.OAuth2Provider;
import xyz.warp03.auth.util.RequestUtil;

public class OIDCProvider extends OAuth2Provider {

	private static final long serialVersionUID = 1L;


	private final String issuerName;

	private URI userinfoEndpointURL;
	private URI jwksURI;

	public OIDCProvider(URI authorizationURL, URI tokenURL, String issuerName){
		super(authorizationURL, tokenURL);
		this.issuerName = issuerName;
	}


	@Override
	public OIDCSession newSession(OAuth2Client client, String requestedScope){
		OIDCSession session = new OIDCSession(this, client);
		session.setScope(requestedScope);
		return session;
	}


	public String getIssuerName(){
		return this.issuerName;
	}

	public URI getUserinfoEndpointURL(){
		return this.userinfoEndpointURL;
	}

	public URI getJwksURI(){
		return this.jwksURI;
	}


	public static OIDCProvider fromDiscovery(String providerURL) throws IOException, URISyntaxException {
		String issuerName = providerURL;
		if(!providerURL.endsWith("/"))
			providerURL += "/";
		providerURL += ".well-known/openid-configuration";
		JSONObject discoveryJson = RequestUtil.getJsonObject(new URL(providerURL));
		OIDCProvider provider = new OIDCProvider(new URI(discoveryJson.getString("authorization_endpoint")), new URI(discoveryJson.getString("token_endpoint")), issuerName);
		String userinfoEndpointStr = discoveryJson.optString("userinfo_endpoint");
		if(userinfoEndpointStr != null)
			provider.userinfoEndpointURL = new URI(userinfoEndpointStr);
		String jwksUriStr = discoveryJson.optString("jwks_uri");
		if(jwksUriStr != null)
			provider.jwksURI = new URI(jwksUriStr);
		return provider;
	}
}
