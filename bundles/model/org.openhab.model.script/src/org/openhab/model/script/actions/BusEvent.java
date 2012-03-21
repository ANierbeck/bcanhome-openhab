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
package org.openhab.model.script.actions;

import java.util.Map;
import java.util.Map.Entry;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TypeParser;
import org.openhab.model.script.internal.ScriptActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * The static methods of this class are made available as functions in the scripts.
 * This gives direct write access to the openHAB event bus from within scripts.
 * Items should not be updated directly (setting the state property), but updates should
 * be sent to the bus, so that all interested bundles are notified.
 * 
 * @author Kai Kreuzer
 * @since 0.9.0
 *
 */
public class BusEvent {

	static private final Logger logger = LoggerFactory.getLogger(BusEvent.class);

	/**
	 * Sends a command for a specified item to the event bus.
	 * 
	 * @param item the item to send the command to
	 * @param command the command to send
	 */
	static public Object sendCommand(Item item, Command command) {
		EventPublisher publisher = (EventPublisher) ScriptActivator.eventPublisherTracker.getService();
		if(publisher!=null) {
			publisher.sendCommand(item.getName(), command);
		}
		return null;
	}

	/**
	 * Sends a command for a specified item to the event bus.
	 * 
	 * @param item the item to send the command to
	 * @param commandString the command to send
	 */
	static public Object sendCommand(Item item, String commandString) {
		return sendCommand(item.getName(), commandString);
	}

	/**
	 * Sends a number as a command for a specified item to the event bus.
	 * 
	 * @param item the item to send the command to
	 * @param number the number to send as a command
	 */
	static public Object sendCommand(Item item, Number number) {
		return sendCommand(item.getName(), number.toString());
	}

	/**
	 * Sends a command for a specified item to the event bus.
	 * 
	 * @param itemName the name of the item to send the command to
	 * @param commandString the command to send
	 */
	static public Object sendCommand(String itemName, String commandString) {
		ItemRegistry registry = (ItemRegistry) ScriptActivator.itemRegistryTracker.getService();
		EventPublisher publisher = (EventPublisher) ScriptActivator.eventPublisherTracker.getService();
		if(publisher!=null && registry!=null) {
			try {
				Item item = registry.getItem(itemName);
				Command command = TypeParser.parseCommand(item.getAcceptedCommandTypes(), commandString);
				publisher.sendCommand(itemName, command);
			} catch (ItemNotFoundException e) {
				logger.warn("Item '" + itemName + "' does not exist.");
			}
		}
		return null;
	}

	/**
	 * Posts a status update for a specified item to the event bus.
	 * t
	 * @param item the item to send the status update for
	 * @param state the new state of the item
	 */
	static public Object postUpdate(Item item, State state) {
		EventPublisher publisher = (EventPublisher) ScriptActivator.eventPublisherTracker.getService();
		if(publisher!=null) {
			publisher.postUpdate(item.getName(), state);
		}
		return null;
	}

	/**
	 * Posts a status update for a specified item to the event bus.
	 * 
	 * @param item the item to send the status update for
	 * @param state the new state of the item as a number
	 */
	static public Object postUpdate(Item item, Number state) {
		return postUpdate(item.getName(), state.toString());
	}

	/**
	 * Posts a status update for a specified item to the event bus.
	 * 
	 * @param item the item to send the status update for
	 * @param stateAsString the new state of the item
	 */
	static public Object postUpdate(Item item, String stateAsString) {
		return postUpdate(item.getName(), stateAsString);
	}

	/**
	 * Posts a status update for a specified item to the event bus.
	 * 
	 * @param itemName the name of the item to send the status update for
	 * @param stateAsString the new state of the item
	 */
	static public Object postUpdate(String itemName, String stateString) {
		ItemRegistry registry = (ItemRegistry) ScriptActivator.itemRegistryTracker.getService();
		EventPublisher publisher = (EventPublisher) ScriptActivator.eventPublisherTracker.getService();
		if(publisher!=null && registry!=null) {
			try {
				Item item = registry.getItem(itemName);
				State state = TypeParser.parseState(item.getAcceptedDataTypes(), stateString);
				publisher.postUpdate(itemName, state);
			} catch (ItemNotFoundException e) {
				logger.warn("Item '" + itemName + "' does not exist.");
			}
		}
		return null;
	}

	/**
	 * Stores the current states for a list of items in a map.
	 * A group item is not itself put into the map, but instead all its members.
	 * 
	 * @param items the items for which the state should be stored
	 * @return the map of items with their states
	 */
	static public Map<Item, State> storeStates(Item... items) {
		Map<Item, State> statesMap = Maps.newHashMap();
		for(Item item : items) {
			if (item instanceof GroupItem) {
				GroupItem groupItem = (GroupItem) item;
				for(Item member : groupItem.getAllMembers()) {
					statesMap.put(member, member.getState());
				}
			} else {
				statesMap.put(item, item.getState());
			}
		}
		return statesMap;
	}

	/**
	 * Restores item states from a map.
	 * If the saved state can be interpreted as a command, a command is sent for the item
	 * (and the physical device can send a status update if occurred). If it is no valid
	 * command, the item state is directly updated to the saved value.
	 * 
	 * @param statesMap a map with ({@link Item}, {@link State}) entries
	 * @return null
	 */
	static public Object restoreStates(Map<Item, State> statesMap) {
		for(Entry<Item, State> entry : statesMap.entrySet()) {
			if(entry.getValue() instanceof Command) {
				sendCommand(entry.getKey(), (Command) entry.getValue());
			} else {
				postUpdate(entry.getKey(), entry.getValue());
			}
		}
		return null;
	}
}
