package org.openhab.binding.serial.internal;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialDevice implements SerialPortEventListener {

	private static final Logger logger = LoggerFactory.getLogger(SerialDevice.class);

	private String port;
	private int baud = 9600;
	private String stringItemName;
	private String switchItemName;

	private EventPublisher eventPublisher;

	private CommPortIdentifier portId;
	private SerialPort serialPort;

	private InputStream inputStream;

	private OutputStream outputStream;

	public SerialDevice(String port) {
		this.port = port;
	}

	public SerialDevice(String port, int baud) {
		this.port = port;
		this.baud = baud;
	}

	public void setEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	public void unsetEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = null;
	}

	public String getStringItemName() {
		return stringItemName;
	}

	public void setStringItemName(String stringItemName) {
		this.stringItemName = stringItemName;
	}

	public String getSwitchItemName() {
		return switchItemName;
	}

	public void setSwitchItemName(String switchItemName) {
		this.switchItemName = switchItemName;
	}

	public String getPort() {
		return port;
	}

	@SuppressWarnings("rawtypes")
	public void initialize() throws InitializationException {
		// parse ports and if the default port is found, initialized the reader
		Enumeration portList = CommPortIdentifier.getPortIdentifiers();
		while (portList.hasMoreElements()) {
			CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
			if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				if (id.getName().equals(port)) {
					logger.debug("Serial port '{}' has been found.", port);
					portId = id;
				}
			}
		}
		if (portId != null) {
			// initialize serial port
			try {
				serialPort = (SerialPort) portId.open("openHAB", 2000);
			} catch (PortInUseException e) {
				throw new InitializationException(e);
			}

			try {
				inputStream = serialPort.getInputStream();
			} catch (IOException e) {
				throw new InitializationException(e);
			}

			try {
				serialPort.addEventListener(this);
			} catch (TooManyListenersException e) {
				throw new InitializationException(e);
			}

			// activate the DATA_AVAILABLE notifier
			serialPort.notifyOnDataAvailable(true);

			try {
				// set port parameters
				serialPort.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
						SerialPort.PARITY_NONE);
			} catch (UnsupportedCommOperationException e) {
				throw new InitializationException(e);
			}

			try {
				// get the outputstream
				outputStream = serialPort.getOutputStream();
			} catch (IOException e) {
				throw new InitializationException(e);
			}
		} else {
			StringBuilder sb = new StringBuilder();
			portList = CommPortIdentifier.getPortIdentifiers();
			while (portList.hasMoreElements()) {
				CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
				if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					sb.append(id.getName() + "\n");
				}
			}
			throw new InitializationException("Serial port '" + port + "' could not be found. Available ports are:\n" + sb.toString());
		}
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		switch (event.getEventType()) {
		case SerialPortEvent.BI:
		case SerialPortEvent.OE:
		case SerialPortEvent.FE:
		case SerialPortEvent.PE:
		case SerialPortEvent.CD:
		case SerialPortEvent.CTS:
		case SerialPortEvent.DSR:
		case SerialPortEvent.RI:
		case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
			break;
		case SerialPortEvent.DATA_AVAILABLE:
			// we get here if data has been received
			byte[] readBuffer = new byte[20];
			try {
				// read data
				while (inputStream.available() > 0) {
					inputStream.read(readBuffer);
				}
				// send data
				String result = new String(readBuffer);
				logger.debug("Received message '{}' on serial port {}", new String[] { result, port });
				if (eventPublisher != null && stringItemName != null) {
					eventPublisher.postUpdate(stringItemName, new StringType(result));
				}
				// if we receive empty values, we treat this to be a switch operation
				if (eventPublisher != null && switchItemName != null && result.trim().isEmpty()) {
					eventPublisher.postUpdate(switchItemName, OnOffType.ON);
					eventPublisher.postUpdate(switchItemName, OnOffType.OFF);
				}
			} catch (IOException e) {
				logger.debug("Error receiving data on serial port {}: {}", new String[] { port, e.getMessage() });
			}
			break;
		}
	}

	public void writeString(String msg) {
		logger.debug("Writing ‘{}‘ to serial port {}", new String[] { msg, port });
		try {
			// write string to serial port
			outputStream.write(msg.getBytes());
		} catch (IOException e) {
			logger.error("Error writing ‘{}‘ to serial port {}: {}", new String[] { msg, port, e.getMessage() });
		}
	}

	public void close() {
		serialPort.removeEventListener();
		try {
			inputStream.close();
		} catch (IOException e) {
			logger.warn("Error closing serial port {}", port, e);
		}
		try {
			outputStream.close();
		} catch (IOException e) {
			logger.warn("Error closing serial port {}", port, e);
		}
		serialPort.close();
	}
}
