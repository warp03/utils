/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.oidc;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.json.JSONObject;

import xyz.warp03.auth.oauth2.OAuth2Client;
import xyz.warp03.auth.oauth2.OAuth2Session;
import xyz.warp03.auth.util.RequestUtil;

public class OIDCSession extends OAuth2Session {

	private static final long serialVersionUID = 1L;


	private String display;
	private String prompt;

	public OIDCSession(OIDCProvider provider, OAuth2Client client){
		super(provider, client);
	}


	public void setDisplay(String display){
		this.display = display;
	}

	public void setPrompt(String prompt){
		this.prompt = prompt;
	}


	@Override
	public URI getAuthRequestURL(){
		URI uri = super.getAuthRequestURL();
		int params = 0;
		if(this.display != null)
			params++;
		if(this.prompt != null)
			params++;
		if(params > 0){
			String[] query = new String[params * 2];
			int i = 0;
			if(this.display != null){
				query[i++] = "display";
				query[i++] = display;
			}
			if(this.prompt != null){
				query[i++] = "prompt";
				query[i++] = prompt;
			}
			return RequestUtil.appendQueryParameters(uri, query);
		}else
			return uri;
	}


	public Map<String, String> requestUserInfo() throws IOException {
		JSONObject response = RequestUtil.getJsonObjectAuthed(((OIDCProvider) super.getProvider()).getUserinfoEndpointURL().toURL(), super.accessToken);
		response.getString("sub"); // require sub
		Map<String, String> data = new java.util.HashMap<>();
		for(String k : response.keySet()){
			String v = response.optString(k, null);
			if(v != null)
				data.put(k, v);
		}
		return data;
	}
}
