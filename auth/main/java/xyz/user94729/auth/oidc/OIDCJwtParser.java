/*
 * Copyright (C) 2022 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.auth.oidc;

import java.io.IOException;
import java.util.Objects;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.security.SignatureException;

public class OIDCJwtParser {


	private final OIDCProvider provider;
	private final OIDCProviderSigningKeyResolver resolver;
	private final JwtParser jwtParser;

	public OIDCJwtParser(OIDCProvider provider) throws IOException {
		this.provider = Objects.requireNonNull(provider);
		this.resolver = new OIDCProviderSigningKeyResolver(provider.getJwksURI());
		this.updateKeys();
		this.jwtParser = Jwts.parserBuilder().setSigningKeyResolver(this.resolver).build();
	}


	public void updateKeys() throws IOException {
		this.resolver.updateKeys();
	}


	@SuppressWarnings("rawtypes")
	public Jwt<Header, Claims> parseJwt(String jwt){
		return this.jwtParser.parseClaimsJwt(jwt);
	}

	public Jws<Claims> parseJws(String jws){
		Jws<Claims> jwsP = this.jwtParser.parseClaimsJws(jws);
		Claims claims = jwsP.getBody();
		if(!claims.getIssuer().equals(this.provider.getIssuerName()))
			throw new SignatureException("The JWT issuer name does not match the OpenID Connect provider issuer name");
		return jwsP;
	}
}
