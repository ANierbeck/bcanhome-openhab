/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
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

package org.openhab.binding.rfxcom.internal;

import javax.xml.bind.DatatypeConverter;

import org.openhab.binding.rfxcom.RFXComValueSelector;
import org.openhab.binding.rfxcom.internal.messages.*;
import org.openhab.binding.rfxcom.internal.messages.RFXComBaseMessage.PacketType;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides utilities to convert OpenHAB data types to RFXCOM data
 * types and vice verse.
 * 
 * @author Pauli Anttila, Evert van Es
 * @since 1.2.0
 */
public class RFXComDataConverter {

	
	private static final Logger logger = LoggerFactory
			.getLogger(RFXComDataConverter.class);

	/**
	 * Generate device id from RFXCOM object data.
	 * 
	 * @param obj
	 *            RFXCOM data object.
	 * 
	 * @return device id.
	 */
	public static String generateDeviceId(Object obj) {
		String id = null;

		if (obj instanceof RFXComBaseMessage) {
			id = ((RFXComBaseMessage) obj).generateDeviceId();
			
		} else {
			logger.warn("Error generate device id.");
		}

		return id;
	}

	/**
	 * Convert RFXCOM objects to OpenHAB state.
	 * 
	 * @param obj
	 *            RFXCOM data object.
	 * @param valueSelector
	 *            value selector.
	 * 
	 * @return openHAB state.
	 */
	public static State convertRFXCOMValueToOpenHABValue(Object obj,
			RFXComValueSelector valueSelector) throws NumberFormatException {

		if (obj instanceof RFXComLighting1Message)
			return convertLighting1ToState((RFXComLighting1Message) obj,
					valueSelector);

		else if (obj instanceof RFXComLighting2Message)
			return convertLighting2ToState((RFXComLighting2Message) obj,
					valueSelector);

		else if (obj instanceof RFXComTemperatureHumidityMessage)
			return convertTemperatureHumidity2ToState(
					(RFXComTemperatureHumidityMessage) obj, valueSelector);

		else if (obj instanceof RFXComCurtain1Message)
			return convertCurtain1ToState(
					(RFXComCurtain1Message) obj, valueSelector);

		
		throw new NumberFormatException("Can't convert " + obj.getClass()
				+ " to " + valueSelector.getItemClass());
	}

	private static State convertLighting1ToState(RFXComLighting1Message obj,
			RFXComValueSelector valueSelector) {

		org.openhab.core.types.State state = UnDefType.UNDEF;

		if (valueSelector.getItemClass() == SwitchItem.class) {

			if (valueSelector == RFXComValueSelector.COMMAND) {

				switch (obj.command) {
				case OFF:
				case GROUP_OFF:
				case DIM:
					state = OnOffType.OFF;
					break;

				case ON:
				case GROUP_ON:
				case BRIGHT:
					state = OnOffType.ON;
					break;

				case CHIME:
					throw new NumberFormatException("Can't convert "
							+ obj.command + " to SwitchItem");
				}

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to SwitchItem");
			}

		} else if (valueSelector.getItemClass() == ContactItem.class) {

			if (valueSelector == RFXComValueSelector.COMMAND) {

				switch (obj.command) {
				case OFF:
				case GROUP_OFF:
				case DIM:
					state = OpenClosedType.OPEN;
					break;

				case ON:
				case GROUP_ON:
				case BRIGHT:
					state = OpenClosedType.CLOSED;
					break;

				case CHIME:
					throw new NumberFormatException("Can't convert "
							+ obj.command + " to ContactItem");
				}

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to ContactItem");
			}

		} else if (valueSelector.getItemClass() == StringItem.class) {

			if (valueSelector == RFXComValueSelector.RAW_DATA) {

				state = new StringType(
						DatatypeConverter.printHexBinary(obj.rawMessage));

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to StringItem");
			}

		} else {

			throw new NumberFormatException("Can't convert " + valueSelector
					+ " to " + valueSelector.getItemClass());

		}

		return state;
	}


	private static State convertLighting2ToState(RFXComLighting2Message obj,
			RFXComValueSelector valueSelector) {

		org.openhab.core.types.State state = UnDefType.UNDEF;

		if (valueSelector.getItemClass() == NumberItem.class) {

			if (valueSelector == RFXComValueSelector.SIGNAL_LEVEL) {

				state = new DecimalType(obj.signalLevel);

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to NumberItem");
			}

		} else if (valueSelector.getItemClass() == DimmerItem.class
				|| valueSelector.getItemClass() == RollershutterItem.class) {

			if (valueSelector == RFXComValueSelector.DIMMING_LEVEL) {
				state = RFXComLighting2Message.getPercentTypeFromDimLevel(obj.dimmingLevel);

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to DimmerItem/RollershutterItem");
			}

		} else if (valueSelector.getItemClass() == SwitchItem.class) {

			if (valueSelector == RFXComValueSelector.COMMAND) {

				switch (obj.command) {
				case OFF:
				case GROUP_OFF:
					state = OnOffType.OFF;
					break;

				case ON:
				case GROUP_ON:
					state = OnOffType.ON;
					break;

				case SET_GROUP_LEVEL:
				case SET_LEVEL:
					throw new NumberFormatException("Can't convert "
							+ obj.command + " to SwitchItem");
				}

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to SwitchItem");
			}

		} else if (valueSelector.getItemClass() == ContactItem.class) {

			if (valueSelector == RFXComValueSelector.COMMAND) {

				switch (obj.command) {
				case OFF:
				case GROUP_OFF:
					state = OpenClosedType.OPEN;
					break;

				case ON:
				case GROUP_ON:
					state = OpenClosedType.CLOSED;
					break;

				case SET_GROUP_LEVEL:
				case SET_LEVEL:
					throw new NumberFormatException("Can't convert "
							+ obj.command + " to ContactItem");
				}

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to ContactItem");
			}

		} else if (valueSelector.getItemClass() == StringItem.class) {

			if (valueSelector == RFXComValueSelector.RAW_DATA) {

				state = new StringType(
						DatatypeConverter.printHexBinary(obj.rawMessage));

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to StringItem");
			}

		} else {

			throw new NumberFormatException("Can't convert " + valueSelector
					+ " to " + valueSelector.getItemClass());

		}

		return state;
	}	
	
	private static State convertTemperatureHumidity2ToState(
			RFXComTemperatureHumidityMessage obj,
			RFXComValueSelector valueSelector) {

		org.openhab.core.types.State state = UnDefType.UNDEF;

		if (valueSelector.getItemClass() == NumberItem.class) {

			if (valueSelector == RFXComValueSelector.SIGNAL_LEVEL) {

				state = new DecimalType(obj.signalLevel);

			} else if (valueSelector == RFXComValueSelector.BATTERY_LEVEL) {

				state = new DecimalType(obj.batteryLevel);

			} else if (valueSelector == RFXComValueSelector.TEMPERATURE) {

				state = new DecimalType(obj.temperature);

			} else if (valueSelector == RFXComValueSelector.HUMIDITY) {

				state = new DecimalType(obj.humidity);

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to NumberItem");
			}

		} else if (valueSelector.getItemClass() == StringItem.class) {

			if (valueSelector == RFXComValueSelector.RAW_DATA) {

				state = new StringType(
						DatatypeConverter.printHexBinary(obj.rawMessage));

			} else if (valueSelector == RFXComValueSelector.HUMIDITY_STATUS) {

				state = new StringType(obj.humidityStatus.toString());

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to StringItem");
			}
		} else {

			throw new NumberFormatException("Can't convert " + valueSelector
					+ " to " + valueSelector.getItemClass());

		}

		return state;
	}
	

	private static State convertCurtain1ToState(RFXComCurtain1Message obj,
			RFXComValueSelector valueSelector) {

		org.openhab.core.types.State state = UnDefType.UNDEF;

		if (valueSelector.getItemClass() == RollershutterItem.class) {

			if (valueSelector == RFXComValueSelector.COMMAND) {

				switch (obj.command) {
				case CLOSE:
					state = OpenClosedType.CLOSED;
					break;

				case OPEN:
					state = OpenClosedType.OPEN;
					break;
					
				default:
					break;
				}

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to SwitchItem");
			}

		} else if (valueSelector.getItemClass() == StringItem.class) {

			if (valueSelector == RFXComValueSelector.RAW_DATA) {

				state = new StringType(
						DatatypeConverter.printHexBinary(obj.rawMessage));

			} else {
				throw new NumberFormatException("Can't convert "
						+ valueSelector + " to StringItem");
			}

		} else {

			throw new NumberFormatException("Can't convert " + valueSelector
					+ " to " + valueSelector.getItemClass());

		}

		return state;
	}


	/**
	 * Convert OpenHAB value to RFXCOM data object.
	 * 
	 * @param id
	 *            RFXCOM device ID.
	 * @param packetType
	 *            RFXCOM target packet type.
	 * @param subType
	 *            RFXCOM sub type.
	 * @param valueSelector
	 *            value selector.
	 * @param type
	 *            OpenHAB data type.
	 * @param seqNumber
	 *            sequence number.
	 * 
	 * @return RFXCOM object.
	 */
	public static Object convertOpenHABValueToRFXCOMValue(String id,
			PacketType packetType, Object subType,
			RFXComValueSelector valueSelector, Type type, byte seqNumber) {

		Object obj = null;

		switch (packetType) {
		case LIGHTING1:
			RFXComLighting1Message d1 = new RFXComLighting1Message();
			d1.subType = (RFXComLighting1Message.SubType) subType;
			d1.seqNbr = seqNumber;
			String[] ids1 = id.split("\\.");
			d1.sensorId = ids1[0].charAt(0);
			d1.unitcode = Byte.parseByte(ids1[1]);

			logger.debug(
					"convertOpenHABValueToRFXCOMValue 1 (command='{}', type='{}')",
					new Object[] { valueSelector.toString(), type.toString()});

			switch (valueSelector) {
			case COMMAND:
				if (type instanceof OnOffType) {
					d1.command = (type == OnOffType.ON ? RFXComLighting1Message.Commands.ON
							: RFXComLighting1Message.Commands.OFF);
					obj = d1;
				} else {
					throw new NumberFormatException("Can't convert " + type
							+ " to Command");
				}
				break;

			default:
				break;

			}
			break;		
		
		case LIGHTING2:
			RFXComLighting2Message d2 = new RFXComLighting2Message();
			d2.subType = (RFXComLighting2Message.SubType) subType;
			d2.seqNbr = seqNumber;
			String[] ids2 = id.split("\\.");
			d2.sensorId = Integer.parseInt(ids2[0]);
			d2.unitcode = Byte.parseByte(ids2[1]);

			logger.debug(
					"convertOpenHABValueToRFXCOMValue 2 (command='{}', type='{}')",
					new Object[] { valueSelector.toString(), type.toString()});

			
			switch (valueSelector) {
			case COMMAND:
				if (type instanceof OnOffType) {
					d2.command = (type == OnOffType.ON ? RFXComLighting2Message.Commands.ON
							: RFXComLighting2Message.Commands.OFF);
					d2.dimmingLevel = 0;
					obj = d2;
				} else {
					throw new NumberFormatException("Can't convert " + type
							+ " to Command");
				}
				break;

			case DIMMING_LEVEL:
				if (type instanceof OnOffType) {
					d2.command = (type == OnOffType.ON ? RFXComLighting2Message.Commands.ON
							: RFXComLighting2Message.Commands.OFF);
					d2.dimmingLevel = 0;
					obj = d2;
				} else if (type instanceof PercentType) {
					d2.command = RFXComLighting2Message.Commands.SET_LEVEL;
					d2.dimmingLevel = (byte) RFXComLighting2Message.getDimLevelFromPercentType((PercentType) type);
					
					if ( d2.dimmingLevel == 0) {
						d2.command = RFXComLighting2Message.Commands.OFF;
					}
					
					logger.debug(
							"dim level = '{}')",
							new Object[] {d2.dimmingLevel});
					
					obj = d2;
				} else if (type instanceof IncreaseDecreaseType) {
					d2.command = RFXComLighting2Message.Commands.SET_LEVEL;
					//Evert: I do not know how to get previous object state...
					d2.dimmingLevel = 5;
					
					obj = d2;

				} else {
					throw new NumberFormatException("Can't convert " + type
							+ " to Command");
				}
				break;

			default:
				break;

			}
			break;
			

		case CURTAIN1:
			RFXComCurtain1Message d3 = new RFXComCurtain1Message();
			d3.subType = (RFXComCurtain1Message.SubType) subType;
			d3.seqNbr = seqNumber;
			String[] ids3 = id.split("\\.");
			d3.sensorId = ids3[0].charAt(0);
			d3.unitcode = Byte.parseByte(ids3[1]);

			logger.debug(
					"convertOpenHABValueToRFXCOMValue 3 (command='{}', type='{}')",
					new Object[] { valueSelector.toString(), type.toString()});

			switch (valueSelector) {
			case SHUTTER:
				if (type instanceof OpenClosedType) {
					d3.command = (type == OpenClosedType.CLOSED ? RFXComCurtain1Message.Commands.CLOSE
							: RFXComCurtain1Message.Commands.OPEN);
					obj = d3;
				} else if (type instanceof UpDownType) {
					d3.command = (type == UpDownType.UP ? RFXComCurtain1Message.Commands.CLOSE
							: RFXComCurtain1Message.Commands.OPEN);
					obj = d3;
				} else if (type instanceof StopMoveType) {
					d3.command = RFXComCurtain1Message.Commands.STOP;
					obj = d3;
					
				} else {
					throw new NumberFormatException("Can't convert " + type
							+ " to Command");
				}
				break;

			default:
				break;

			}
			break;		

		case TEMPERATURE_HUMIDITY:
			break;
		case INTERFACE_CONTROL:
		case INTERFACE_MESSAGE:
		case UNKNOWN:
		default:
			break;
		}

		return obj;
	}

}
