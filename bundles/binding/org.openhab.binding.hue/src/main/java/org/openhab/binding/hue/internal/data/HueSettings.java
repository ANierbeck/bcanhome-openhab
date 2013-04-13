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
package org.openhab.binding.hue.internal.data;

import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains all information that is provided by the Hue bridge. There are
 * different information that can be requested about the connected bulbs.
 * <ul>
 * <li>Is a bulb switched on?</li>
 * <li>How is a bulb's color temperature?</li>
 * <li>How is the current brightness of a blub?</li>
 * <li>How is the hue of a given bulb?</li>
 * <li>How is the saturation of a given bulb?</li>
 * </ul>
 * 
 * @author Roman Hartmann
 * @since 1.2.0
 */
public class HueSettings {

	static final Logger logger = LoggerFactory.getLogger(HueSettings.class);

	private SettingsTree settingsData = null;

	/**
	 * Constructor of HueSettings. It takes the settings of the Hue bridge to
	 * enable the HueSettings to determine the needed information about the
	 * bulbs.
	 * 
	 * @param settings
	 *            This is the settings string in Json format returned by the Hue
	 *            bridge.
	 */
	@SuppressWarnings("unchecked")
	public HueSettings(String settings) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			settingsData = new SettingsTree(mapper.readValue(settings,
					Map.class));
		} catch (Exception e) {
			logger.error("Could not read Settings-Json from Hue Bridge.");
		}
	}

	/**
	 * Determines whether the given bulb is turned on.
	 * 
	 * @param deviceNumber
	 *            The bulb number the bridge has filed the bulb under.
	 * @return true if the bulb is turned on, false otherwise.
	 */
	public boolean isBulbOn(int deviceNumber) {
		if (settingsData == null) {
			logger.error("Hue bridge settings not initialized correctly.");
			return false;
		}
		return (Boolean) settingsData.node("lights")
				.node(Integer.toString(deviceNumber)).node("state").value("on");
	}

	/**
	 * Determines the color temperature of the given bulb.
	 * 
	 * @param deviceNumber
	 *            The bulb number the bridge has filed the bulb under.
	 * @return The color temperature as a value from 154 - 500
	 */
	public int getColorTemperature(int deviceNumber) {
		if (settingsData == null) {
			logger.error("Hue bridge settings not initialized correctly.");
			return 154;
		}
		Object ct = settingsData.node("lights").node(Integer.toString(deviceNumber)).node("state").value("ct");
		if(ct instanceof Integer) {
			return (Integer) ct;
		} else {
			return 154;
		}
	}

	/**
	 * Determines the brightness of the given bulb.
	 * 
	 * @param deviceNumber
	 *            The bulb number the bridge has filed the bulb under.
	 * @return The brightness as a value from 0 - 255
	 */
	public int getBrightness(int deviceNumber) {
		if (settingsData == null) {
			logger.error("Hue bridge settings not initialized correctly.");
			return 0;
		}
		return (Integer) settingsData.node("lights")
				.node(Integer.toString(deviceNumber)).node("state")
				.value("bri");
	}

	/**
	 * Determines the hue of the given bulb.
	 * 
	 * @param deviceNumber
	 *            The bulb number the bridge has filed the bulb under.
	 * @return The hue as a value from 0 - 65535
	 */
	public int getHue(int deviceNumber) {
		if (settingsData == null) {
			logger.error("Hue bridge settings not initialized correctly.");
			return 0;
		}

		Object hue = settingsData.node("lights")
				.node(Integer.toString(deviceNumber)).node("state")
				.value("hue");
		if(hue instanceof Integer) {
			return (Integer) hue;
		} else {
			return 0;
		}
	}

	/**
	 * Determines the saturation of the given bulb.
	 * 
	 * @param deviceNumber
	 *            The bulb number the bridge has filed the bulb under.
	 * @return The saturation as a value from 0 - 254
	 */
	public int getSaturation(int deviceNumber) {
		if (settingsData == null) {
			logger.error("Hue bridge settings not initialized correctly.");
			return 0;
		}

		Object sat = settingsData.node("lights")
				.node(Integer.toString(deviceNumber)).node("state")
				.value("sat");
		if(sat instanceof Integer) {
			return (Integer) sat;
		} else {
			return 0;
		}
	}

	/**
	 * The SettingsTree represents the settings Json as a tree with some
	 * convenience methods to get subtrees and the values of interest easily.
	 */
	class SettingsTree {

		private Map<String, Object> dataMap;

		/**
		 * Constructor of the SettingsTree.
		 * 
		 * @param treeData
		 *            The Json data as it has been returned by the Json object
		 *            mapper.
		 */
		public SettingsTree(Map<String, Object> treeData) {
			this.dataMap = treeData;
		}

		/**
		 * @param nodeName
		 *            The name of the child node.
		 * @return The child node named like nodeName. This will be a sub tree.
		 */
		@SuppressWarnings("unchecked")
		protected SettingsTree node(String nodeName) {
			return new SettingsTree((Map<String, Object>) dataMap.get(nodeName));
		}

		/**
		 * @param valueName
		 *            The name of the child node.
		 * @return The child node named like nodeName. This will be an object.
		 */
		protected Object value(String valueName) {
			return dataMap.get(valueName);
		}

	}

}
