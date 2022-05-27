/*
 * Copyright (C) 2022 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.auth.oidc;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolver;
import io.jsonwebtoken.io.Decoders;

import xyz.user94729.auth.oauth2.OAuth2Client;
import xyz.user94729.auth.oauth2.OAuth2Provider;
import xyz.user94729.auth.util.RequestUtil;

public class OIDCProviderSigningKeyResolver implements SigningKeyResolver {


	private final URI jwksURI;
	private final Map<String, Key> keys = new java.util.HashMap<>();

	public OIDCProviderSigningKeyResolver(URI jwksURI){
		this.jwksURI = Objects.requireNonNull(jwksURI);
	}


	@SuppressWarnings("rawtypes")
	@Override
	public Key resolveSigningKey(JwsHeader header, Claims claims) {
		return this.getKey(header.getKeyId());
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Key resolveSigningKey(JwsHeader header, String plaintext) {
		return this.getKey(header.getKeyId());
	}


	public synchronized void updateKeys() throws IOException {
		JSONArray keysJson = RequestUtil.getJsonArray(this.jwksURI.toURL());
		for(Object o : keysJson){
			if(!(o instanceof JSONObject))
				throw new JSONException("JWKS array contains non-object element");
			JSONObject jwk = (JSONObject) o;
			if(!jwk.getString("use").equals("sig"))
				continue;
			String keyType = jwk.getString("kty");
			if(!keyType.equals("RSA"))
				throw new UnsupportedOperationException("Unsupported key type: " + keyType);
			String keyId = jwk.getString("kid");
			BigInteger modulus = new BigInteger(1, Decoders.BASE64URL.decode(jwk.getString("n")));
			BigInteger exponent = new BigInteger(1, Decoders.BASE64URL.decode(jwk.getString("e")));
			try{
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				PublicKey key = keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
				this.keys.put(keyId, key);
			}catch(NoSuchAlgorithmException | InvalidKeySpecException e){
				throw new IllegalArgumentException("Invalid key", e);
			}
		}
	}

	public synchronized Key getKey(String keyId){
		return this.keys.get(keyId);
	}


	public URI getJwksURI(){
		return this.jwksURI;
	}
}
