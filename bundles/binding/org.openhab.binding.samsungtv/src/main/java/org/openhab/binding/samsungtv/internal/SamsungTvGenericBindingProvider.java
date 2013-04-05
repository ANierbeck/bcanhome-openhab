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
package org.openhab.binding.samsungtv.internal;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.samsungtv.SamsungTvBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.quist.samy.remocon.Key;

/**
 * <p>
 * This class can parse information from the generic binding format and provides
 * Samsung device binding information from it.
 * 
 * <p>
 * The syntax of the binding configuration strings accepted is the following:
 * <p>
 * <code>
 * 	samsungtv="&lt;openHAB-command>:&lt;device-id>:&lt;device-command>[,&lt;openHAB-command>:&lt;device-id>:&lt;device-command>][,...]"
 * </code>
 * <p>
 * where parts in brackets [] signify an optional information.
 * </p>
 * 
 * <p>
 * Examples for valid binding configuration strings:
 * 
 * <ul>
 * <li><code>samsungtv="1:Livingroom:KEY_1, 2:Livingroom:KEY_2, 3:Livingroom:KEY_3"</code></li>
 * <li><code>samsungtv="INCREASE:Livingroom:KEY_CHUP, DECREASE:Livingroom:KEY_CHDOWN"</code></li>
 * <li><code>samsungtv="UP:Kitchen:KEY_VOLUP, DOWN:Kitchen:KEY_VOLDOWN"</code></li>
 * </ul>
 * 
 * @author Pauli Anttila
 * @since 1.2.0
 */
public class SamsungTvGenericBindingProvider extends
		AbstractGenericBindingProvider implements SamsungTvBindingProvider {

	static final Logger logger = 
		LoggerFactory.getLogger(SamsungTvGenericBindingProvider.class);

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "samsungtv";
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig)
			throws BindingConfigParseException {
		if (!(item instanceof SwitchItem 
				|| item instanceof NumberItem
				|| item instanceof DimmerItem
				|| item instanceof RollershutterItem 
				|| item instanceof StringItem)) {
			throw new BindingConfigParseException(
					"item '"
							+ item.getName()
							+ "' is of type '"
							+ item.getClass().getSimpleName()
							+ "', only SwitchItem, NumberItem, DimmerItem, RollershutterItem and StringItem are allowed - please check your *.items configuration");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item,
			String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);

		SamsungTvBindingConfig config = new SamsungTvBindingConfig();
		parseBindingConfig(bindingConfig, config);
		addBindingConfig(item, config);
	}

	protected void parseBindingConfig(String bindingConfigs,
			SamsungTvBindingConfig config) throws BindingConfigParseException {

		String bindingConfig = StringUtils.substringBefore(bindingConfigs, ",");
		String bindingConfigTail = StringUtils.substringAfter(bindingConfigs, ",");

		String[] configParts = bindingConfig.trim().split(":");

		if (configParts.length != 3) {
			throw new BindingConfigParseException(
					"Samsung TV binding must contain three parts separated by ':'");
		}

		String command = StringUtils.trim(configParts[0]);
		String tvId = StringUtils.trim(configParts[1]);
		String tvCommand = StringUtils.trim(configParts[2]);

		Key key = Key.valueOf(tvCommand);

		if (key == null) {
			throw new BindingConfigParseException("Unregonized value '" + tvCommand + "'");
		}

		// if there are more commands to parse do that recursively ...
		if (StringUtils.isNotBlank(bindingConfigTail)) {
			parseBindingConfig(bindingConfigTail, config);
		}

		config.put(command, tvId + ":" + tvCommand);
	}

	@Override
	public String getTVCommand(String itemName, String command) {
		SamsungTvBindingConfig config = (SamsungTvBindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.get(command) : null;
	}

	/**
	 * This is an internal data structure to store information from the binding
	 * config strings and use it to answer the requests to the Samsung TV
	 * binding provider.
	 */
	static class SamsungTvBindingConfig extends HashMap<String, String> implements BindingConfig {

		/** generated serialVersion UID */
		private static final long serialVersionUID = 861870438027351568L;
	}

}
