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
package org.openhab.binding.dmx.internal.cmd;

import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.dmx.DmxService;
import org.openhab.binding.dmx.internal.config.DmxItem;
import org.openhab.model.item.binding.BindingConfigParseException;

/**
 * DMX Fade Command. Fades a DMX channel from its' current value to a new value
 * in a given time frame and holds the result for a set duration.
 * 
 * Fade commands can be chained together to create different scenes. E.g fade
 * from white to blue to green to ..
 * 
 * {@literal
 * 
 * <dmx-command>|<fade-time>:<channel-values>:<hold-time>|<fade-time>:<channel-values>:<hold-time>|..
 * 
 * <dmx-command>	:	FADE
 * <fade-time>		:	time to use to fade from the current DMX value to the target DMX value
 * <channel-values>	: 	a csv list of target DMX values (0-255) for each of the main channels defined in the channel config.  
 * 						These values are repeated automatically on all the mirror channels.
 * <hold-time>		:	time to hold the target values in milliseconds.  A value of -1 will keep the value active indefinite.
 * 
 * }
 * 
 * @author Davy Vanherbergen
 * @since 1.2.0
 */
public class DmxFadeCommand implements DmxCommand {

	protected DmxItem item;

	private List<Fade> fades = new ArrayList<Fade>();

	/**
	 * Create new fade command from a configuration string.
	 * 
	 * @param item
	 *            to which the command applies.
	 * @param cmd
	 *            configuration string.
	 * @throws BindingConfigParseException
	 *             if parsing the configuration fails.
	 */
	public DmxFadeCommand(DmxItem item, String cmd)
			throws BindingConfigParseException {

		this.item = item;
		String[] values = cmd.split("\\|");
		for (String fadeCmd : values) {
			Fade fade = new Fade(fadeCmd);
			fades.add(fade);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(DmxService service) {
		int i = 0;
		for (Fade f : fades) {
			// first fade should reset any running fades
			int j = 0;
			for (int channel : item.getChannels()) {
				service.fadeChannel(channel, f.getTime(), f.targetValues[j++],
						f.getHoldTime(), i == 0);
				if (j == f.targetValues.length) {
					j = 0;
				}
			}
			i++;
		}
	}

	/**
	 * Utility class for storing fade values.
	 */
	protected class Fade {

		private int fadeTime;

		private int holdTime;

		private int[] targetValues;

		/**
		 * Create new fade from configuration string.
		 * 
		 * The configuration string should be in the format
		 * <fadeTime>:<value>-<value>-<value>:<holdTime>
		 * 
		 * The number of values should be lower or equal than the number of
		 * channels on the item. If lower, the values are repeated to fill all
		 * the item channels.
		 * 
		 * @param cmd
		 *            configuration string.
		 * @throws BindingConfigParseException
		 *             if parsing fails.
		 */
		public Fade(String cmd) throws BindingConfigParseException {

			String[] values = cmd.split(":");
			if (values.length != 3) {
				throw new BindingConfigParseException(
						"Fade configs should consist out of 3 sections!");
			}
			fadeTime = Integer.parseInt(values[0]);
			holdTime = Integer.parseInt(values[2]);
			if (holdTime < -1) {
				holdTime = -1;
			}
			String[] target = values[1].split(",");
			targetValues = new int[target.length];
			int i = 0;
			for (String t : target) {
				targetValues[i++] = Integer.parseInt(t);
			}
		}

		/**
		 * @return time to fade to the target value in ms.
		 */
		public int getTime() {
			return fadeTime;
		}

		/**
		 * @return time to hold the target value in ms.
		 */
		public int getHoldTime() {
			return holdTime;
		}

	}

}
