/*
 * Copyright (C) 2021-2023 warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.netutil.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.omegazero.common.util.PropertyUtil;
import org.omegazero.net.socket.SocketConnection;

public class WebSocketChannel {

	private static final int maxPayloadSize = PropertyUtil.getInt("xyz.warp03.netutil.websocket.maxPayloadSize", 0);

	protected final SocketConnection connection;
	private final boolean client;
	private URI resource;

	private boolean handshakeComplete = false;
	private String wsProtocol;

	private BiConsumer<byte[], Boolean> onMessage;
	private Consumer<byte[]> onPong;
	private Consumer<Throwable> onError;
	private Consumer<Integer> onClose;

	private ResizableByteBuffer frameBuffer = new ResizableByteBuffer();

	private int lastFrameFlags = -1;
	private long lastFrameLength = -1;
	private long lastFrameMKey = -1;
	private boolean frameComplete = false;
	private ByteArrayOutputStream lastFramePayload = new ByteArrayOutputStream();

	private boolean lastMessageBinary = false;
	private ByteArrayOutputStream lastMessage = new ByteArrayOutputStream();
	private boolean messageStarted = false;

	private boolean closed = false;

	public WebSocketChannel(SocketConnection connection, boolean client, URI resource) {
		this.connection = connection;
		this.client = client;
		this.resource = resource;
	}


	protected void handshakeComplete(String proto) {
		if(proto != null)
			this.wsProtocol = proto;
		else
			this.wsProtocol = "";

		this.connection.setOnData(this::incomingData);
		this.connection.setOnClose(this::connectionClose);

		this.handshakeComplete = true;
	}


	protected void incomingData(byte[] data) throws IOException {
		if(this.closed)
			return;
		this.frameBuffer.write(data);
		while(this.frameBuffer.remaining() > 0){
			if(!this.frameComplete){
				if(!this.readNextFrameHeader())
					break;
			}else{
				this.frameBuffer.readIntoOutputStream(this.lastFramePayload,
						(int) Math.min(this.frameBuffer.remaining(), this.lastFrameLength - this.lastFramePayload.size()));
			}
			if(this.lastFramePayload.size() == this.lastFrameLength){
				if(!this.handleFrame())
					break;
				this.lastFrameFlags = -1;
				this.lastFrameLength = -1;
				this.lastFrameMKey = -1;
				this.frameComplete = false;
				this.lastFramePayload.reset();
			}
		}
	}

	protected void connectionClose() {
		if(!this.closed)
			this.close0(WSCommon.WS_STATUS_ABNORMAL_CLOSE);
	}


	private boolean readNextFrameHeader() throws InvalidWSFrameException {
		if(this.lastFrameFlags < 0 && this.frameBuffer.remaining() >= 2){
			this.lastFrameFlags = this.frameBuffer.read() & 0xff | (this.frameBuffer.read() << 8);
			if((this.lastFrameFlags & 0x70) != 0)
				return this.wsProtocolError("RSV bits must be clear");
			if((this.lastFrameFlags & 0x8000) != 0){
				if(this.client)
					this.wsProtocolError("Received masked frame from server");
			}else{
				if(!this.client)
					return this.wsProtocolError("Received unmasked frame from client");
			}
			int len = (this.lastFrameFlags >> 8) & 0x7f;
			if(len == 126)
				this.lastFrameLength = -2;
			else if(len == 127)
				this.lastFrameLength = -3;
			else
				this.lastFrameLength = len;
		}
		if(this.lastFrameFlags >= 0 && this.lastFrameLength < 0){
			if(this.lastFrameLength == -2 && this.frameBuffer.remaining() >= 2)
				this.lastFrameLength = this.frameBuffer.readNumberBE(2);
			else if(this.lastFrameLength == -3 && this.frameBuffer.remaining() >= 8){
				this.lastFrameLength = this.frameBuffer.readNumberBE(8);
				if((this.lastFrameLength & 0x8000000000000000L) != 0)
					return this.wsProtocolError("MSBit of 8-byte frame length is 1");
			}
		}
		if(this.lastFrameLength >= 0){
			if(maxPayloadSize > 0 && this.lastFrameLength > maxPayloadSize)
				return this.wsFrameError("Payload too large", WSCommon.WS_STATUS_MSG_TOO_BIG);
			if((this.lastFrameFlags & 0x8000) != 0){
				if(this.lastFrameMKey < 0 && this.frameBuffer.remaining() >= 4){
					this.lastFrameMKey = this.frameBuffer.readNumberBE(4);
					this.frameComplete = true;
				}
			}else
				this.frameComplete = true;
		}
		return true;
	}

	private boolean handleFrame() throws IOException {
		boolean fin = (this.lastFrameFlags & 0x80) != 0;
		int opcode = this.lastFrameFlags & 0xf;
		if((opcode & 0x8) != 0 && !fin)
			return this.wsProtocolError("Control frame is fragmented");
		byte[] frameData = this.lastFramePayload.toByteArray();
		if(this.lastFrameMKey >= 0)
			WebSocketChannel.maskData(frameData, (int) this.lastFrameMKey);
		if(opcode == WSCommon.WS_OPCODE_TEXT || opcode == WSCommon.WS_OPCODE_BINARY){
			if(this.lastMessage.size() > 0)
				return this.wsProtocolError("Unterminated message fragment sequence");
			this.lastMessageBinary = opcode == WSCommon.WS_OPCODE_BINARY;
			this.lastMessage.write(frameData);
			this.messageStarted = true;
		}else if(opcode == WSCommon.WS_OPCODE_CONTINUATION){
			if(!this.messageStarted)
				return this.wsProtocolError("Unexpected continuation frame");
			this.lastMessage.write(frameData);
		}else if(opcode == WSCommon.WS_OPCODE_CLOSE){
			if(!this.closed){
				if(frameData.length >= 2){
					int status = (frameData[0] << 8) | frameData[1] & 0xff;
					this.close(status);
				}else
					this.close(-1);
			}
		}else if(opcode == WSCommon.WS_OPCODE_PING){
			this.write(WSCommon.WS_OPCODE_PONG, frameData);
		}else if(opcode == WSCommon.WS_OPCODE_PONG){
			if(this.onPong != null)
				this.onPong.accept(frameData);
		}
		if((opcode & 0x8) == 0 && fin){
			if(!this.messageStarted)
				return this.wsProtocolError("Unexpected FIN frame");
			if(this.onMessage != null)
				this.onMessage.accept(this.lastMessage.toByteArray(), this.lastMessageBinary);
			this.lastMessage.reset();
			this.messageStarted = false;
		}
		return true;
	}


	private boolean wsFrameError(String msg, int status) throws InvalidWSFrameException {
		InvalidWSFrameException e = new InvalidWSFrameException(msg);
		// if an exception occurs in a handler netlib will immediately close the connection
		// (because errors there are not really intended to indicate application layer protocol errors), so the close message needs to be written before the error is generated,
		// but this causes onClose to be called before onError
		// this provides a way to receive an onError before onClose (if no onError was set here, it will still be passed onto netlib)
		if(this.onError != null)
			this.onError.accept(e);
		if(!this.closed)
			this.close(status);
		if(this.onError == null)
			throw e;
		return false;
	}

	private boolean wsProtocolError(String msg) throws InvalidWSFrameException {
		return this.wsFrameError(msg, WSCommon.WS_STATUS_PROTOCOL_ERROR);
	}


	protected void close0(int status) {
		this.closed = true;
		this.connection.close();
		if(this.onClose != null)
			this.onClose.accept(status);
	}

	protected void write(int opcode, byte[] data) {
		if(!this.handshakeComplete)
			throw new IllegalStateException("Handshake not completed");
		if(this.closed)
			throw new IllegalStateException("Connection is closed");
		int payloadLen = data.length;
		byte[] payloadLenExt;
		if(data.length > 0xffff){
			payloadLenExt = numToArrayBE(payloadLen, 8);
			payloadLen = 127;
		}else if(data.length >= 126){
			payloadLenExt = numToArrayBE(payloadLen, 2);
			payloadLen = 126;
		}else{
			payloadLenExt = new byte[0];
		}
		int index = 0;
		byte[] frame = new byte[2 + payloadLenExt.length + (this.client ? 4 : 0) + data.length];
		frame[index++] = (byte) (0x80 | (opcode & 0xf));
		frame[index++] = (byte) (payloadLen | (this.client ? 0x80 : 0));
		if(payloadLenExt.length > 0){
			System.arraycopy(payloadLenExt, 0, frame, index, payloadLenExt.length);
			index += payloadLenExt.length;
		}
		if(this.client){
			int mkey = new Random().nextInt();
			System.arraycopy(numToArrayBE(mkey, 4), 0, frame, index, 4);
			index += 4;
			System.arraycopy(data, 0, frame, index, data.length);
			maskData(frame, index, frame.length, mkey);
		}else
			System.arraycopy(data, 0, frame, index, data.length);
		this.connection.write(frame);
	}


	/**
	 * Writes the given <b>data</b> to the WebSocket connection marked as "binary".
	 * 
	 * @param data The binary data
	 * @throws IllegalStateException If the WebSocket connection is not open ({@link #isOpen()} returns <code>false</code>)
	 */
	public void write(byte[] data) {
		this.write(WSCommon.WS_OPCODE_BINARY, data);
	}

	/**
	 * Writes the given <b>string</b> to the WebSocket connection marked as "text".
	 * 
	 * @param data The text data
	 * @throws IllegalStateException If the WebSocket connection is not open ({@link #isOpen()} returns <code>false</code>)
	 */
	public void write(String string) {
		this.write(WSCommon.WS_OPCODE_TEXT, string.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Sends a ping request with the given data. The peer must then send a "pong" message back with the same data, causing an <code>onPong</code> event.
	 * 
	 * @param data The data to send with the ping request
	 * @throws IllegalStateException If the WebSocket connection is not open ({@link #isOpen()} returns <code>false</code>)
	 */
	public void ping(byte[] data) {
		this.write(WSCommon.WS_OPCODE_PING, data);
	}

	/**
	 * Closes the WebSocket connection by sending a "close" frame with the given status code (See <i>RFC 6455, Section 7.4.1</i> for a list of defined status codes).
	 * 
	 * @param status The status code of the close message. If <code>0</code> or lower, no status code is sent
	 * @throws IllegalStateException If the WebSocket connection is not open ({@link #isOpen()} returns <code>false</code>)
	 */
	public void close(int status) {
		if(status > 0){
			this.write(WSCommon.WS_OPCODE_CLOSE, new byte[] { (byte) (status >> 8), (byte) status });
			this.close0(status);
		}else{
			this.write(WSCommon.WS_OPCODE_CLOSE, new byte[0]);
			this.close0(WSCommon.WS_STATUS_NO_STATUS);
		}
	}


	/**
	 * 
	 * @return <code>true</code> if this connection is open and any of the <code>write</code> methods may be used. A connection is open after the handshake has completed and
	 *         before it is closed
	 */
	public boolean isOpen() {
		return this.handshakeComplete && !this.closed;
	}


	/**
	 * 
	 * @return The underlying <code>SocketConnection</code> passed in the constructor
	 */
	public SocketConnection getConnection() {
		return this.connection;
	}

	/**
	 * 
	 * @return The resource-name of this WebSocket connection represented as a <code>URI</code> object
	 */
	public URI getResource() {
		return this.resource;
	}


	/**
	 * 
	 * @return <code>true</code> if the handshake is complete
	 */
	public boolean isHandshakeComplete() {
		return this.handshakeComplete;
	}

	/**
	 * 
	 * @return The negotiated protocol name of this WebSocket connection (value of the <code>Sec-WebSocket-Protocol</code> header in the server response), an empty string if
	 *         no negotiation occurred or <code>null</code> if the handshake has not yet completed
	 */
	public String getWsProtocol() {
		return this.wsProtocol;
	}


	/**
	 * Sets a callback that is called when a full message is received from the peer.<br>
	 * <br>
	 * The first argument of the callback is the raw data, the second argument specifies if the message was received as a WebSocket "text" (<code>false</code>) or "binary"
	 * (<code>true</code>) message.
	 * 
	 * @param onMessage The callback
	 */
	public void setOnMessage(BiConsumer<byte[], Boolean> onMessage) {
		this.onMessage = onMessage;
	}

	/**
	 * Sets a callback that is called when a WebSocket "pong" message is received from the peer, usually after a {@link #ping(byte[])} request.
	 * 
	 * @param onPong The callback
	 */
	public void setOnPong(Consumer<byte[]> onPong) {
		this.onPong = onPong;
	}

	/**
	 * Sets a callback that is called when a WebSocket protocol error occurs. This method also sets the <code>onError</code> handler of the underlying
	 * <code>SocketConnection</code> using {@link SocketConnection#setOnError(Consumer)}.
	 * 
	 * @param onError The callback
	 */
	public void setOnError(Consumer<Throwable> onError) {
		this.onError = onError;
		this.connection.setOnError(onError);
	}

	/**
	 * Sets a callback that is called when the WebSocket connection closes (a "close" frame is sent or received or the underlying connection closed).<br>
	 * <br>
	 * The first argument of the callback is the WebSocket status code. The status code is <code>-1</code> if the connection closes before the WebSocket handshake has
	 * completed.
	 * 
	 * @param onClose The callback
	 */
	public void setOnClose(Consumer<Integer> onClose) {
		this.onClose = onClose;
	}


	/**
	 * A call to this function of the form <code>maskData(data, mkey)</code> is exactly equivalent to a call of the form <code>{@link #maskData(byte[], int, int, int)
	 * maskData}(data, 0, data.length, mkey)</code>.
	 * 
	 * @param data The data to be masked
	 * @param mkey The masking key
	 * @see {@link #maskData(byte[], int, int, int)}
	 */
	public static void maskData(byte[] data, int mkey) {
		maskData(data, 0, data.length, mkey);
	}

	/**
	 * Masks all or part of the bytes in <b>data</b> with the given masking key (<b>mkey</b>) according to the algorithm described in <i>RFC 6455, Section 5.3</i>.
	 * 
	 * @param data  The data to be masked
	 * @param start At which index to start masking. This algorithm is applied as if this is where the data started, meaning the byte at index <b>start</b> is always masked
	 *              with byte 0 of the masking key
	 * @param end   At which index to stop masking
	 * @param mkey  The masking key
	 */
	public static void maskData(byte[] data, int start, int end, int mkey) {
		int mkeyI = 0;
		for(int i = start; i < end; i++){
			data[i] = (byte) (data[i] ^ ((mkey >>> (3 - mkeyI++) * 8) & 0xff));
			if(mkeyI == 4)
				mkeyI = 0;
		}
	}

	public static byte[] numToArrayBE(long num, int len) {
		byte[] a = new byte[len];
		for(int i = 0; i < len; i++){
			a[i] = (byte) (num >>> (len - i - 1) * 8);
		}
		return a;
	}
}
