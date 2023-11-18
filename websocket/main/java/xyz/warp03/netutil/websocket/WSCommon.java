/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

public final class WSCommon {

	public static final String WS_ACCEPT_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	public static final int WS_OPCODE_CONTINUATION = 0x0;
	public static final int WS_OPCODE_TEXT = 0x1;
	public static final int WS_OPCODE_BINARY = 0x2;
	public static final int WS_OPCODE_CLOSE = 0x8;
	public static final int WS_OPCODE_PING = 0x9;
	public static final int WS_OPCODE_PONG = 0xa;

	public static final int WS_STATUS_NORMAL = 1000;
	public static final int WS_STATUS_GOING_AWAY = 1001;
	public static final int WS_STATUS_PROTOCOL_ERROR = 1002;
	public static final int WS_STATUS_NOT_ACCEPTABLE = 1003;
	public static final int WS_STATUS_NO_STATUS = 1005;
	public static final int WS_STATUS_ABNORMAL_CLOSE = 1006;
	public static final int WS_STATUS_INVALID_DATA = 1007;
	public static final int WS_STATUS_POLICY_VIOLATION = 1008;
	public static final int WS_STATUS_MSG_TOO_BIG = 1009;
	public static final int WS_STATUS_EXTENSION_MISSING = 1010;
	public static final int WS_STATUS_UNEXPECTED_ERROR = 1011;

	private WSCommon() {
	}
}
