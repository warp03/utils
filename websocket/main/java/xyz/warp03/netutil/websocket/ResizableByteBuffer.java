/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.NoSuchElementException;

public class ResizableByteBuffer extends ByteArrayOutputStream {

	private int start = 0;

	public ResizableByteBuffer() {
	}


	public void compact() {
		System.arraycopy(super.buf, this.start, super.buf, 0, super.count - this.start);
		super.count -= this.start;
		this.start = 0;
	}

	public int remaining() {
		return super.count - this.start;
	}

	public byte read() {
		if(this.start >= super.count)
			throw new NoSuchElementException();
		return super.buf[this.start++];
	}

	public long readNumberBE(int length) {
		long num = 0;
		for(int i = 0; i < length; i++){
			num |= (((long) this.read()) & 0xff) << (length - i - 1) * 8;
		}
		return num;
	}

	public void readIntoOutputStream(OutputStream target, int length) throws IOException {
		if(length > super.count - this.start)
			throw new IndexOutOfBoundsException();
		target.write(super.buf, this.start, length);
		this.start += length;
	}
}
