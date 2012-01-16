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
package org.openhab.binding.vdr.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.vdr.VDRBindingProvider;
import org.openhab.binding.vdr.VDRCommandType;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class can parse information from the generic binding format and provides
 * VDR binding information from it. It registers as a {@link VDRBindingProvider}
 * service as well.
 * </p>
 * 
 * <p>
 * Here are some examples for valid binding configuration strings:
 * <ul>
 * <li><code>{ vdr="LivingRoom:powerOff" }</code> - switch VDR off calls</li>
 * <li><code>{ vdr="LivingRoom:message" }</code> - show message on OSD</li>
 * <li><code>{ vdr="LivingRoom:volume" }</code> - if bound to a switch item,
 * increase (true) / decrease (false) volume</li>
 * <li><code>{ vdr="LivingRoom:volume" }</code> - if bound to a number item, set
 * volume level (0-255)</li>
 * <li><code>{ vdr="LivingRoom:channel" }</code> - if bound to a switch item,
 * increase (true) / decrease (false) channel</li>
 * <li><code>{ vdr="LivingRoom:channel" }</code> - if bound to a number item,
 * switch to channel number</li>
 * </ul>
 * These binding configurations can be used on either Switch-, String- or
 * DimmerItems
 * </p>
 * 
 * @author Wolfgang Willinghoefer
 * 
 * @since 0.9.0
 */
public class VDRGenericBindingProvider extends AbstractGenericBindingProvider
		implements VDRBindingProvider {

	static final Logger logger = LoggerFactory
			.getLogger(VDRGenericBindingProvider.class);

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "vdr";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void validateItemType(Item item) throws BindingConfigParseException {
		if (!(item instanceof SwitchItem || item instanceof DimmerItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Switch- and DimmerItems are allowed - please check your *.items configuration");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item,
			String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);

		if (bindingConfig != null) {
			VDRBindingConfig config = parseBindingConfig(item, bindingConfig,
					null);
			addBindingConfig(item, config);
		} else {
			logger.warn("bindingConfig is NULL (item=" + item
					+ ") -> processing bindingConfig aborted!");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.openhab.binding.vdr.VDRBindingProvider#getBindingItemName(java.lang
	 * .String, org.openhab.binding.vdr.internal.VDRCommandType)
	 */
	public String getBindingItemName(String vdrId, VDRCommandType vdrCommand) {
		String itemName = null;
		for (BindingConfig config : this.bindingConfigs.values()) {
			VDRBindingConfig vdrConfig = (VDRBindingConfig) config;
			if (vdrConfig.vDRId.equals(vdrId)
					&& vdrConfig.command.equals(vdrCommand.getVDRCommand())) {
				itemName = vdrConfig.item.getName();
				break;
			}
		}

		return itemName;
	}

	/**
	 * Checks if the bindingConfig contains a valid binding type and returns an
	 * appropriate instance.
	 * 
	 * @param item
	 * @param bindingConfig
	 * 
	 * @throws BindingConfigParseException
	 *             if bindingConfig is no valid binding type
	 */
	protected VDRBindingConfig parseBindingConfig(Item item,
			String bindingConfigs, VDRBindingConfig parent)
			throws BindingConfigParseException {

		String bindingConfig = StringUtils.substringBefore(bindingConfigs, ",");
		String bindingConfigTail = StringUtils.substringAfter(bindingConfigs,
				",");

		String[] configParts = bindingConfig.split(":");
		if (configParts.length != 2) {
			throw new BindingConfigParseException(
					"VDR binding configuration must consist of two parts [config="
							+ configParts + "]");
		}

		String vdrId = StringUtils.trim(configParts[0]);
		String command = StringUtils.trim(configParts[1]);

		if (command != null) {
			if (VDRCommandType.validateBinding(command, item.getClass())) {
				VDRBindingConfig vdrBindingConfig = new VDRBindingConfig(vdrId,
						command, item, parent);
				if (StringUtils.isNotBlank(bindingConfigTail)) {
					parseBindingConfig(item, bindingConfigTail,
							vdrBindingConfig);
				}
				return vdrBindingConfig;
			} else {
				String validItemType = VDRCommandType.getValidItemTypes(command);
				if (StringUtils.isEmpty(validItemType)) {
					throw new BindingConfigParseException("'" + bindingConfig
							+ "' is no valid binding type");					
				} else {
					throw new BindingConfigParseException("'" + bindingConfig
							+ "' is not bound to a valid item type. Valid item type(s): " + validItemType) ;
				}
			}
		} else {
			throw new BindingConfigParseException("'" + bindingConfig
					+ "' is no valid binding type");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.openhab.binding.vdr.VDRBindingProvider#getVDRId(java.lang.String)
	 */
	public List<String> getVDRId(String itemName) {
		VDRBindingConfig config = (VDRBindingConfig) bindingConfigs
				.get(itemName);
		List<String> ret = null;
		if (config != null) {
			ret = new ArrayList<String>();
			ret.add(config.getVDRId());
			while (config.getNext() != null) {
				config = config.getNext();
				ret.add(config.getVDRId());
			}
		}
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.openhab.binding.vdr.VDRBindingProvider#getVDRCommand(java.lang.String
	 * )
	 */
	public List<String> getVDRCommand(String itemName) {
		VDRBindingConfig config = (VDRBindingConfig) bindingConfigs
				.get(itemName);
		List<String> ret = null;
		if (config != null) {
			ret = new ArrayList<String>();
			ret.add(config.getCommand());
			while (config.getNext() != null) {
				config = config.getNext();
				ret.add(config.getCommand());
			}
		}
		return ret;
	}

	static class VDRBindingConfig implements BindingConfig {

		final private String command;
		final private String vDRId;
		final private Item item;
		private VDRBindingConfig next = null;

		public VDRBindingConfig(String pVDRId, String pCommand, Item pItem,
				VDRBindingConfig pParent) {
			this.vDRId = pVDRId;
			this.command = pCommand;
			this.item = pItem;
			if (pParent != null) {
				pParent.next = this;
			}
		}

		public String getVDRId() {
			return vDRId;
		}

		public String getCommand() {
			return command;
		}

		public VDRBindingConfig getNext() {
			return next;
		}

	}

}