/*
 * Copyright (C) 2021 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.netutil.starttls;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.SocketConnection;
import org.omegazero.net.socket.provider.ChannelProvider;

public class STLSProvider implements ChannelProvider {


	private final SocketConnection transport;

	private final Deque<byte[]> readQueue = new LinkedList<>();

	public STLSProvider(SocketConnection transport) {
		this.transport = transport;
	}


	@Override
	public void init(ChannelConnection connection, SelectionKey key) {
		connection.setOnTimeout(this.transport::handleTimeout);
		connection.setOnError(this.transport::handleError);

		this.transport.setOnData((d) -> {
			synchronized(STLSProvider.this){
				this.readQueue.add(d);
				while(!this.readQueue.isEmpty()){
					byte[] td = connection.read();
					if(td != null)
						connection.handleData(td);
				}
			}
		});
		this.transport.setOnClose(connection::close);
	}


	@Override
	public boolean connect(SocketAddress remote, int timeout) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() throws IOException {
		if(this.transport.isConnected())
			this.transport.destroy();
	}


	@Override
	public synchronized int read(ByteBuffer buf) throws IOException {
		byte[] data = this.readQueue.poll();
		if(data != null){
			if(data.length > buf.remaining()){
				byte[] r = Arrays.copyOfRange(data, buf.remaining(), data.length);
				this.readQueue.addFirst(r);
				data = Arrays.copyOf(data, buf.remaining());
			}
			buf.put(data);
			return data.length;
		}else
			return 0;
	}

	@Override
	public int write(ByteBuffer buf) throws IOException {
		if(buf.hasRemaining()){
			byte[] data = new byte[buf.remaining()];
			buf.get(data);
			this.transport.write(data);
			return data.length;
		}else
			return 0;
	}


	@Override
	public void writeBacklogStarted() {
		throw new UnsupportedOperationException("writeBacklogStarted");
	}

	@Override
	public void writeBacklogEnded() {
		throw new UnsupportedOperationException("writeBacklogEnded");
	}


	@Override
	public void setReadBlock(boolean block) {
		this.transport.setReadBlock(block);
	}


	@Override
	public boolean isAvailable() {
		return this.transport.isConnected();
	}
}
