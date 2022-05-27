/*
 * Copyright (C) 2022 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.auth.oauth2;

import java.util.Objects;

public class OAuth2Client implements java.io.Serializable {

	private static final long serialVersionUID = 1L;


	private final String clientId;
	private final String clientSecret;

	public OAuth2Client(String clientId, String clientSecret){
		this.clientId = Objects.requireNonNull(clientId);
		this.clientSecret = clientSecret;
	}


	public String getClientId(){
		return this.clientId;
	}

	public String getClientSecret(){
		return this.clientSecret;
	}
}
