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
package org.openhab.binding.hue.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.hue.HueBindingProvider;
import org.openhab.binding.hue.internal.HueBindingConfig.BindingType;
import org.openhab.binding.hue.internal.hardware.HueBridge;
import org.openhab.binding.hue.internal.hardware.HueBulb;
import org.openhab.binding.hue.internal.tools.SsdpDiscovery;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This binding is able to do the following tasks with the Philips hue system:
 * <ul>
 * <li>Switching bulbs on and off.</li>
 * <li>Change color temperature of a bulb, what results in a white color.</li>
 * <li>Change the brightness of a bulb without changing the color.</li>
 * <li>Change the RGB values of a bulb.</li>
 * </ul>
 * 
 * @author Roman Hartmann
 * @since 1.2.0
 */
public class HueBinding extends AbstractBinding<HueBindingProvider> implements ManagedService {

	static final Logger logger = LoggerFactory.getLogger(HueBinding.class);

	private HueBridge activeBridge = null;
	private String bridgeIP = null;
	private String bridgeSecret = "openHAB";

	// Caches all bulbs controlled to prevent the recreation of the bulbs which
	// triggers a rereading of the settings from the bridge which is very
	// expensive.
	private HashMap<Integer, HueBulb> bulbCache = new HashMap<Integer, HueBulb>();

	/**
	 * Default constructor for the Hue binding.
	 */
	public HueBinding() {
	}

	@Override
	public void internalReceiveCommand(String itemName, Command command) {
		super.internalReceiveCommand(itemName, command);

		logger.debug("Hue binding received command '" + command
				+ "' for item '" + itemName + "'");

		if (activeBridge != null) {
			computeCommandForItemOnBridge(command, itemName, activeBridge);
		} else {
			logger.warn("Hue binding skipped command because no Hue bridge is connected.");
		}

	}

	/**
	 * Checks whether the command is for one of the configured Hue bulbs. If
	 * this is the case, the command is translated to the corresponding action
	 * which is then sent to the given bulb.
	 * 
	 * @param command
	 *            The command from the openHAB bus.
	 * @param itemName
	 *            The name of the targeted item.
	 * @param bridge
	 *            The Hue bridge the Hue bulb is connected to
	 */
	private void computeCommandForItemOnBridge(Command command,
			String itemName, HueBridge bridge) {

		HueBindingConfig deviceConfig = getConfigForItemName(itemName);

		if (deviceConfig == null) {
			return;
		}

		HueBulb bulb = bulbCache.get(deviceConfig.getDeviceNumber());
		if (bulb == null) {
			bulb = new HueBulb(bridge, deviceConfig.getDeviceNumber());
			bulbCache.put(deviceConfig.getDeviceNumber(), bulb);
		}

		if (command instanceof OnOffType) {
			bulb.setOnAtFullBrightness(OnOffType.ON.equals(command));
		}

		if (command instanceof HSBType) {
			HSBType hsbCommand = (HSBType) command;
			DecimalType hue = hsbCommand.getHue();
			PercentType sat = hsbCommand.getSaturation();
			PercentType bri = hsbCommand.getBrightness();
			bulb.colorizeByHSB(hue.doubleValue() / 360,
					sat.doubleValue() / 100, bri.doubleValue() / 100);
		}

		if (deviceConfig.getType().equals(BindingType.brightness)
				|| deviceConfig.getType().equals(BindingType.rgb)) {
			if (IncreaseDecreaseType.INCREASE.equals(command)) {
				int resultingValue = bulb.increaseBrightness(deviceConfig.getStepSize());
				eventPublisher.postUpdate(itemName, new PercentType(resultingValue));
			} else if (IncreaseDecreaseType.DECREASE.equals(command)) {
				int resultingValue = bulb.decreaseBrightness(deviceConfig.getStepSize());
				eventPublisher.postUpdate(itemName, new PercentType(resultingValue));
			} else if ((command instanceof PercentType) && !(command instanceof HSBType)) {
				bulb.setBrightness((255 / 100)
						* ((PercentType) command).intValue());
			}
		}

		if (deviceConfig.getType().equals(BindingType.colorTemperature)) {
			if (IncreaseDecreaseType.INCREASE.equals(command)) {
				int resultingValue = bulb.increaseColorTemperature(deviceConfig.getStepSize());
				eventPublisher.postUpdate(itemName, new PercentType(resultingValue));
			} else if (IncreaseDecreaseType.DECREASE.equals(command)) {
				int resultingValue = bulb.decreaseColorTemperature(deviceConfig.getStepSize());
				eventPublisher.postUpdate(itemName, new PercentType(resultingValue));
			} else if (command instanceof PercentType) {
				bulb.setColorTemperature(((346 / 100) * ((PercentType) command)
						.intValue()) + 154);
			}
		}

	}

	/**
	 * Lookup of the configuration of the named item.
	 * 
	 * @param itemName
	 *            The name of the item.
	 * @return The configuration, null otherwise.
	 */
	private HueBindingConfig getConfigForItemName(String itemName) {
		for (HueBindingProvider provider : this.providers) {
			if (provider.getItemConfig(itemName) != null) {
				return provider.getItemConfig(itemName);
			}
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void updated(Dictionary config) throws ConfigurationException {
		if (config != null) {
			String ip = (String) config.get("ip");
			if (StringUtils.isNotBlank(ip)) {
				this.bridgeIP = ip;
			} else {
				try {
					this.bridgeIP = new SsdpDiscovery()
							.findIpForResponseKeywords("description.xml",
									"FreeRTOS");
				} catch (IOException e) {
					logger.warn("Could not find hue bridge automatically. Please make sure it is switched on and connected to the same network as openHAB. If it permanently fails you may configure the IP address of your hue bridge manually in the openHAB configuration.");
				}
			}
			String secret = (String) config.get("secret");
			if (StringUtils.isNotBlank(secret)) {
				this.bridgeSecret = secret;
			}

			// connect the Hue bridge with the new configs
			if(this.bridgeIP!=null) {
				activeBridge = new HueBridge(bridgeIP, bridgeSecret);
				activeBridge.pairBridgeIfNecessary();
			}
		}

	}

}
