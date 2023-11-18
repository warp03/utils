/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.oauth2;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Random;

import org.json.JSONObject;
import org.json.JSONException;

import org.omegazero.common.util.Util;

import xyz.warp03.auth.util.RequestUtil;

public class OAuth2Session implements java.io.Serializable {

	private static final long serialVersionUID = 1L;


	private final long createdTime = System.currentTimeMillis();

	private final OAuth2Provider provider;
	private final OAuth2Client client;

	private String scope;
	private boolean useAuthState = true;
	protected String authState;
	protected String redirectUri;

	private Object attachment;

	protected String authCode;
	protected String refreshToken;
	protected String accessToken;
	protected String accessTokenType;
	protected long accessTokenExpires;

	public OAuth2Session(OAuth2Provider provider, OAuth2Client client){
		this.provider = Objects.requireNonNull(provider);
		this.client = Objects.requireNonNull(client);
	}


	public void setScope(String scope){
		if(this.accessToken != null){ // check if new scope is subset of old scope for refresh
			String[] newScope = scope.split(" ");
			String[] oldScope = this.scope.split(" ");
			for(String s : newScope){
				boolean exists = false;
				for(String os : oldScope){
					if(os.equals(s)){
						exists = true;
						break;
					}
				}
				if(!exists)
					throw new IllegalArgumentException("New scope contains '" + s + "', which does not exist in the previous scope. You must create a new session to widen the scope");
			}
		}
		this.scope = Objects.requireNonNull(scope);
	}

	public void setUseAuthState(boolean useAuthState){
		this.useAuthState = useAuthState;
	}

	public void setAuthState(String authState){
		this.setUseAuthState(true);
		this.authState = authState;
	}

	public String getAuthState(){
		if(this.authState == null)
			this.authState = Util.randomHex(32);
		return this.authState;
	}

	public void setRedirectUri(String redirectUri){
		this.redirectUri = redirectUri;
	}

	public String getRedirectUri(){
		return this.redirectUri;
	}


	public URI getAuthRequestURL(){
		if(this.scope == null)
			throw new IllegalStateException("scope must be set using setScope");
		int qlen = 6;
		if(this.useAuthState)
			qlen += 2;
		if(this.redirectUri != null)
			qlen += 2;
		String[] query = new String[qlen];
		query[0] = "response_type";
		query[1] = "code";
		query[2] = "client_id";
		query[3] = this.client.getClientId();
		query[4] = "scope";
		query[5] = this.scope;
		int qi = 6;
		if(this.useAuthState){
			query[qi++] = "state";
			query[qi++] = this.getAuthState();
		}
		if(this.redirectUri != null){
			query[qi++] = "redirect_uri";
			query[qi++] = this.redirectUri;
		}
		return this.provider.constructAuthorizationURL(query);
	}

	public boolean authCallback(String code, String state){
		if(this.accessToken != null)
			return false;
		if(this.useAuthState && !state.equals(this.getAuthState()))
			return false;
		this.authCode = code;
		return true;
	}

	public void requestTokens() throws IOException {
		if(this.authCode == null)
			throw new IllegalStateException("No auth code available. Either authCallback was not called or tokens were requested already");
		String[] bodyparams = new String[this.client.getClientSecret() != null ? 8 : 6];
		bodyparams[0] = "grant_type";
		bodyparams[1] = "authorization_code";
		bodyparams[2] = "client_id";
		bodyparams[3] = this.client.getClientId();
		bodyparams[4] = "code";
		bodyparams[5] = this.authCode;
		if(bodyparams.length > 6){
			bodyparams[6] = "client_secret";
			bodyparams[7] = this.client.getClientSecret();
		}

		try{
			JSONObject response = RequestUtil.postJsonFormEncoded(this.provider.getTokenURL().toURL(), bodyparams);
			String error = response.optString("error", null);
			if(error == null){
				this.authCode = null;
				this.accessToken = response.getString("access_token");
				this.accessTokenType = response.getString("token_type");
				this.accessTokenExpires = System.currentTimeMillis() + response.optLong("expires_in") * 1000;
				this.refreshToken = response.optString("refresh_token", null);
				this.scope = response.optString("scope", this.scope);
				this.requestTokensResponse(response);
			}else
				throw new OAuth2Exception("Token endpoint error: " + error + ": " + response.optString("error_description", null));
		}catch(JSONException e){
			throw new OAuth2Exception("Token endpoint JSON is invalid or missing data", e);
		}
	}

	protected void requestTokensResponse(JSONObject response){
	}

	// TODO token refresh


	public void setAttachment(Object attachment){
		this.attachment = attachment;
	}

	public Object getAttachment(){
		return this.attachment;
	}


	public String getAuthCode(){
		return this.authCode;
	}

	public String getRefreshToken(){
		return this.refreshToken;
	}

	public boolean hasAccessToken(){
		return this.accessToken != null;
	}

	public String getAccessToken(){
		return this.accessToken;
	}

	public String getAccessTokenType(){
		return this.accessTokenType;
	}

	public long getAccessTokenExpires(){
		return this.accessTokenExpires;
	}


	public long getCreatedTime(){
		return this.createdTime;
	}

	public OAuth2Provider getProvider(){
		return this.provider;
	}

	public OAuth2Client getClient(){
		return this.client;
	}
}
