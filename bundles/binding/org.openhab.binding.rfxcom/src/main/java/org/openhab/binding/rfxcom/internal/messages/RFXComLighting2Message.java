/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.rfxcom.internal.messages;

import java.math.BigDecimal;

import org.openhab.core.library.types.PercentType;

/**
 * RFXCOM data class for lighting2 message.
 * 
 * @author Pauli Anttila
 * @since 1.2.0
 */
public class RFXComLighting2Message extends RFXComBaseMessage {

	public enum Commands {
		OFF(0),
		ON(1),
		SET_LEVEL(2),
		GROUP_OFF(3),
		GROUP_ON(4),
		SET_GROUP_LEVEL(5);

		private final int command;

		Commands(int command) {
			this.command = command;
		}

		Commands(byte command) {
			this.command = command;
		}

		public byte toByte() {
			return (byte) command;
		}
	}


	public enum SubType {
		AC(0),
		HOME_EASY_EU(1),
		ANSLUT(2);

		private final int subType;

		SubType(int subType) {
			this.subType = subType;
		}

		SubType(byte subType) {
			this.subType = subType;
		}

		public byte toByte() {
			return (byte) subType;
		}
	}

	public SubType subType = SubType.AC;
	public int sensorId = 0;
	public byte unitcode = 0;
	public Commands command = Commands.OFF;
	public byte dimmingLevel = 0;
	public byte signalLevel = 0;

	public RFXComLighting2Message() {
		packetType = PacketType.LIGHTING2;

	}

	public RFXComLighting2Message(byte[] data) {

		encodeMessage(data);
	}

	@Override
	public String toString() {
		String str = "";

		str += super.toString();
		str += "\n - Sub type = " + subType;
		str += "\n - Id = " + sensorId;
		str += "\n - Unit code = " + unitcode;
		str += "\n - Command = " + command;
		str += "\n - Dim level = " + dimmingLevel;
		str += "\n - Signal level = " + signalLevel;

		return str;
	}

	@Override
	public void encodeMessage(byte[] data) {

		super.encodeMessage(data);

		subType = SubType.values()[super.subType];
		sensorId = (data[4] & 0xFF) << 24 | (data[5] & 0xFF) << 16
				| (data[6] & 0xFF) << 8 | (data[7] & 0xFF);
		unitcode = data[8];
		command = Commands.values()[data[9]];
		dimmingLevel = data[10];
		signalLevel = (byte) ((data[11] & 0xF0) >> 4);
	}

	@Override
	public byte[] decodeMessage() {

		byte[] data = new byte[12];

		data[0] = 0x0B;
		data[1] = RFXComBaseMessage.PacketType.LIGHTING2.toByte();
		data[2] = subType.toByte();
		data[3] = seqNbr;
		data[4] = (byte) ((sensorId >> 24) & 0xFF);
		data[5] = (byte) ((sensorId >> 16) & 0xFF);
		data[6] = (byte) ((sensorId >> 8) & 0xFF);
		data[7] = (byte) (sensorId & 0xFF);

		data[8] = unitcode;
		data[9] = command.toByte();
		data[10] = dimmingLevel;
		data[11] = (byte) ((signalLevel & 0x0F) << 4);

		return data;
	}
	
	@Override
	public String generateDeviceId() {
		 return sensorId + "." + unitcode;
	}

	

	/**
	 * Convert a 0-15 scale value to a percent type.
	 * 
	 * @param pt
	 *            percent type to convert
	 * @return converted value 0-15
	 */
	public static int getDimLevelFromPercentType(PercentType pt) {
		return pt
				.toBigDecimal()
				.multiply(BigDecimal.valueOf(15))
				.divide(PercentType.HUNDRED.toBigDecimal(), 0,
						BigDecimal.ROUND_UP).intValue();
	}

	/**
	 * Convert a 0-15 scale value to a percent type.
	 * 
	 * @param pt
	 *            percent type to convert
	 * @return converted value 0-15
	 */
	public static PercentType getPercentTypeFromDimLevel(int value) {
		value = Math.min(value, 15);
		
		return new PercentType(BigDecimal
				.valueOf(value)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(15), 0,
						BigDecimal.ROUND_UP).intValue());
	}
	

}
