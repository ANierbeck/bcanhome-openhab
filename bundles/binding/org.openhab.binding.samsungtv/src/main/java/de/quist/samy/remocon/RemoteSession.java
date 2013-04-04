/*
 *  Copyright (C) 2011  Tom Quist
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You can get the GNU General Public License at
 *  http://www.gnu.org/licenses/gpl.html
 */
package de.quist.samy.remocon;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import org.apache.commons.codec.binary.Base64;

import org.openhab.binding.samsungtv.internal.SamsungTvConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copied from https://github.com/keremkusmezer/SamyGo-Android-Remote/tree/master/src/de/quist/samy/remocon,
 * since there is no binary build available anymore. Thanks to Tom Quist!
 *  
 * @author Tom Quist
 */
public class RemoteSession {

	private static Logger logger = LoggerFactory.getLogger(SamsungTvConnection.class);

	public static final String REPORT_TAG = "report";
	
	private static final String APP_STRING = "iphone.iapp.samsung";
	private static final String TV_APP_STRING = "iphone..iapp.samsung";
	
	private static final char[] ALLOWED_BYTES = new char[] {0x64, 0x00, 0x01, 0x00};
	private static final char[] DENIED_BYTES = new char[] {0x64, 0x00, 0x00, 0x00};
	private static final char[] TIMEOUT_BYTES = new char[] {0x65, 0x00};

	public static final String ALLOWED = "ALLOWED";
	public static final String DENIED = "DENIED";
	public static final String TIMEOUT = "TIMEOUT";
	private static final String TAG = RemoteSession.class.getSimpleName();
	
	private String applicationName;
	private String uniqueId;
	private String host;
	private int port;

	private Socket socket;

	private InputStreamReader reader;

	private BufferedWriter writer;
	

	private RemoteSession(String applicationName, String uniqueId, String host, int port) {
		this.applicationName = applicationName;
		this.uniqueId = uniqueId;
		if (uniqueId == null) {
			uniqueId = "";
		}
		this.host = host;
		this.port = port;
	}
	
	public static RemoteSession create(String applicationName, String uniqueId, String host, int port) throws IOException, ConnectionDeniedException, TimeoutException {
		RemoteSession session = new RemoteSession(applicationName, uniqueId, host, port);
		String result = session.initialize();
		if (result.equals(ALLOWED)) {
			return session;
		} else if (result.equals(DENIED)) {
			throw new ConnectionDeniedException();
		} else if (result.equals(TIMEOUT)) {
			throw new TimeoutException();
		} else {
			 // for now we just assume to be connected
			return session;
		}
	}
	
	private String initialize() throws UnknownHostException, IOException {
		if (logger != null) logger.debug(TAG, "Creating socket for host " + host + " on port " + port);
		socket = new Socket(host, port);
		if (logger != null) logger.debug(TAG, "Socket successfully created and connected");
		InetAddress localAddress = socket.getLocalAddress();
		if (logger != null) logger.debug(TAG, "Local address is " + localAddress.getHostAddress());
		
		if (logger != null) logger.debug(TAG, "Sending registration message");
		writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		writer.append((char)0x00);
		writeText(writer, APP_STRING);
		writeText(writer, getRegistrationPayload(localAddress.getHostAddress()));
		writer.flush();
		
		InputStream in = socket.getInputStream();
		reader = new InputStreamReader(in);
		String result = readRegistrationReply(reader);
		//sendPart2();
		int i;
		while ((i = in.available()) > 0) {
			in.skip(i);
		}
		return result;
	}
	
	@SuppressWarnings("unused")
	private void sendPart2() throws IOException {
		writer.append((char)0x00);
		writeText(writer, TV_APP_STRING);
		writeText(writer, new String(new char[] {0xc8, 0x00}));
	}
	
	private void checkConnection() throws UnknownHostException, IOException {
		if (socket.isClosed() || !socket.isConnected()) {
			if (logger != null) logger.debug(TAG, "Connection closed, trying to reconnect...");
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore any exception
			}
			initialize();
			if (logger != null) logger.debug(TAG, "Reconnected to server");
		}
	}
	
	public void destroy() {
		try {
			socket.close();
		} catch (IOException e) {
			// Ignore exception
		}
	}
	
	private String readRegistrationReply(Reader reader) throws IOException {
		if (logger != null) logger.debug(TAG, "Reading registration reply");
		reader.read(); // Unknown byte 0x02
		String text1 = readText(reader); // Read "unknown.livingroom.iapp.samsung" for new RC and "iapp.samsung" for already registered RC
		if (logger != null) logger.debug(TAG, "Received ID: " + text1);
		char[] result = readCharArray(reader); // Read result sequence
		if (Arrays.equals(result, ALLOWED_BYTES)) {
			if (logger != null) logger.debug(TAG, "Registration successfull");
			return ALLOWED;
		} else if (Arrays.equals(result, DENIED_BYTES)) {
			if (logger != null) logger.warn(TAG, "Registration denied");
			return DENIED;
		} else if (Arrays.equals(result, TIMEOUT_BYTES)) {
			if (logger != null) logger.warn(TAG, "Registration timed out");
			return TIMEOUT;
		} else {
			StringBuilder sb = new StringBuilder();
			for (char c : result) {
				sb.append(Integer.toHexString(c));
				sb.append(' ');
			}
			String hexReturn = sb.toString();
			if (logger != null) {
				logger.error(REPORT_TAG, "Received unknown registration reply: "+hexReturn);
			}
			return hexReturn;
		}
	}
	
	private String getRegistrationPayload(String ip) throws IOException {
		StringWriter writer = new StringWriter();
		writer.append((char)0x64);
		writer.append((char) 0x00);
		writeBase64Text(writer, ip);
		writeBase64Text(writer, uniqueId);
		writeBase64Text(writer, applicationName);
		writer.flush();
		return writer.toString();
	}
	
	private static String readText(Reader reader) throws IOException {
		char[] buffer = readCharArray(reader);
		return new String(buffer);
	}
	
	private static char[] readCharArray(Reader reader) throws IOException {
		if (reader.markSupported()) reader.mark(1024);
		int length = reader.read();
		int delimiter = reader.read();
		if (delimiter != 0) {
			if (reader.markSupported()) reader.reset();
			throw new IOException("Unsupported reply exception");
		}
		char[] buffer = new char[length];
		reader.read(buffer);
		return buffer;
	}
	
	private static Writer writeText(Writer writer, String text) throws IOException {
		return writer.append((char)text.length()).append((char) 0x00).append(text);
	}
	
	private static Writer writeBase64Text(Writer writer, String text) throws IOException {
		String b64 = new String(Base64.encodeBase64(text.getBytes()));
		return writeText(writer, b64);
	}
	
	@SuppressWarnings("unused")
	private void internalSendKey(Key key) throws IOException {
		writer.append((char)0x00);
		writeText(writer, TV_APP_STRING);
		writeText(writer, getKeyPayload(key));
		writer.flush();
		int i = reader.read(); // Unknown byte 0x00
		String t = readText(reader);  // Read "iapp.samsung"
		char[] c = readCharArray(reader);
		//System.out.println(i);
		//System.out.println(t);
		//for (char a : c) System.out.println(Integer.toHexString(a));
		//System.out.println(c);
	}
	
	public void sendKey(Key key) throws IOException {
		if (logger != null) logger.debug(TAG, "Sending key " + key.getValue() + "...");
		checkConnection();
		try {
			internalSendKey(key);
		} catch (SocketException e) {
			if (logger != null) logger.debug(TAG, "Could not send key because the server closed the connection. Reconnecting...");
			initialize();
			if (logger != null) logger.debug(TAG, "Sending key " + key.getValue() + " again...");
			internalSendKey(key);
		}
		if (logger != null) logger.debug(TAG, "Successfully sent key " + key.getValue());
	}

	private String getKeyPayload(Key key) throws IOException {
		StringWriter writer = new StringWriter();
		writer.append((char)0x00);
		writer.append((char)0x00);
		writer.append((char)0x00);
		writeBase64Text(writer, key.getValue());
		writer.flush();
		return writer.toString();
	}
	
	@SuppressWarnings("unused")
	private void internalSendText(String text) throws IOException {
		writer.append((char)0x01);
		writeText(writer, TV_APP_STRING);
		writeText(writer, getTextPayload(text));
		writer.flush();
		if (!reader.ready()) {
			return;
		}
		int i = reader.read(); // Unknown byte 0x02
		//System.out.println(i);
		String t = readText(reader); // Read "iapp.samsung"
		char[] c = readCharArray(reader);
		//System.out.println(i);
		//System.out.println(t);
		//for (char a : c) System.out.println(Integer.toHexString(a));
	}
	
	public void sendText(String text) throws IOException {
		if (logger != null) logger.debug(TAG, "Sending text \"" + text + "\"...");
		checkConnection();
		try {
			internalSendText(text);
		} catch (SocketException e) {
			if (logger != null) logger.debug(TAG, "Could not send key because the server closed the connection. Reconnecting...");
			initialize();
			if (logger != null) logger.debug(TAG, "Sending text \"" + text + "\" again...");
			internalSendText(text);
		}
		if (logger != null) logger.debug(TAG, "Successfully sent text \"" + text + "\"");
	}

	private String getTextPayload(String text) throws IOException {
		StringWriter writer = new StringWriter();
		writer.append((char)0x01);
		writer.append((char)0x00);
		writeBase64Text(writer, text);
		writer.flush();
		return writer.toString();
	}

}
