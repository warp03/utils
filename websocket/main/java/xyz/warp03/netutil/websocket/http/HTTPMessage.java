/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class HTTPMessage {

	private final String startLine;
	private final Map<String, String> headers = new HashMap<>();
	private final byte[] data;

	private String[] startLineSplit;

	public HTTPMessage(String startLine) {
		this(startLine, null);
	}

	public HTTPMessage(String startLine, byte[] data) {
		this.startLine = startLine;
		this.data = data;
	}


	public void setHeader(String key, String value) {
		Objects.requireNonNull(key);
		if(value != null)
			this.headers.put(key, value);
		else
			this.headers.remove(key);
	}

	public String getHeader(String key) {
		return this.headers.get(Objects.requireNonNull(key));
	}

	public boolean headerExists(String key) {
		return this.headers.containsKey(Objects.requireNonNull(key));
	}

	public String getStartLine() {
		return this.startLine;
	}

	public String getStartLinePart(int index) {
		if(this.startLineSplit == null)
			this.startLineSplit = this.startLine.split(" ");
		return this.startLineSplit[index];
	}

	public byte[] getData() {
		return this.data;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.startLine).append("\r\n");
		for(Entry<String, String> header : this.headers.entrySet()){
			sb.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
		}
		sb.append("\r\n");
		if(this.data != null)
			sb.append(new String(this.data));
		return sb.toString();
	}

	public byte[] toBytes() {
		return this.toString().getBytes(StandardCharsets.ISO_8859_1);
	}
}
