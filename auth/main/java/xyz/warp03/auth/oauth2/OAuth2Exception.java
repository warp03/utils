/*
 * Copyright (C) 2022-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.auth.oauth2;

public class OAuth2Exception extends java.io.IOException {

	private static final long serialVersionUID = 1L;


	public OAuth2Exception(String msg){
		super(msg);
	}

	public OAuth2Exception(String msg, Throwable e){
		super(msg, e);
	}
}
