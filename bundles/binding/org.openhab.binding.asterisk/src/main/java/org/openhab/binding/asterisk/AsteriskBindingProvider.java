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
package org.openhab.binding.asterisk;

import org.openhab.binding.asterisk.internal.AsteriskBindingTypes;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;


/**
 * This interface is implemented by classes that can map openHAB items to
 * Asterisk binding types.
 * 
 * Implementing classes should register themselves as a service in order to be 
 * taken into account.
 * 
 * @author Thomas.Eichstaedt-Engelen
 * @since 0.9.0
 */
public interface AsteriskBindingProvider extends BindingProvider {
	
	/**
	 * Returns the Type of the Item identified by {@code itemName}
	 * 
	 * @param itemName the name of the item to find the type for
	 * @return the type of the Item identified by {@code itemName}
	 */
	Class<? extends Item> getItemType(String itemName);
	
	/** 
	 * Returns the binding type for an item name
	 * 
	 * @param itemName the name of the item
	 * @return the items binding type
	 */
	AsteriskBindingTypes getType(String itemName);

	/**
	 * Provides an array of all item names of this provider for a given binding type
	 * @param bindingType the binding type of the items
	 * @return an array of all item names of this provider for the given binding type
	 */
	String[] getItemNamesByType(AsteriskBindingTypes bindingType);
	
}
