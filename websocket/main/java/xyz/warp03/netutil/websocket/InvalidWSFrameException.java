/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

import java.io.IOException;

public class InvalidWSFrameException extends IOException {

	private static final long serialVersionUID = 1L;


	public InvalidWSFrameException() {
		super();
	}

	public InvalidWSFrameException(String msg) {
		super(msg);
	}

	public InvalidWSFrameException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
