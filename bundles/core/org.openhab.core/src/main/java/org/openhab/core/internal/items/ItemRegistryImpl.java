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
package org.openhab.core.internal.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.ItemRegistryChangeListener;
import org.openhab.core.items.ItemsChangeListener;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main implementing class of the {@link ItemRegistry} interface.
 * It keeps track of all declared items of all item providers and keeps their
 * current state in memory. This is the central point where states are kept
 * and thus it is a core part for all stateful services.
 * 
 * @author Kai Kreuzer
 *
 */
public class ItemRegistryImpl implements ItemRegistry, ItemsChangeListener {
	
	private static final Logger logger = LoggerFactory.getLogger(ItemRegistryImpl.class);

	/** if an EventPublisher service is available, we provide it to all items, so that they can communicate over the bus */
	protected EventPublisher eventPublisher;
	
	/** this is our local map in which we store all our items */
	protected Map<ItemProvider, Collection<Item>> itemMap = Collections.synchronizedMap(new HashMap<ItemProvider, Collection<Item>>());
	
	/** to keep track of all item change listeners */
	protected Collection<ItemRegistryChangeListener> listeners = new HashSet<ItemRegistryChangeListener>();

	public void activate(ComponentContext componentContext) {
	}
	
	public void deactivate(ComponentContext componentContext) {
        synchronized (itemMap) {
			// first remove ourself as a listener from the item providers
			for(ItemProvider provider : itemMap.keySet()) {
				provider.removeItemChangeListener(this);
			}
			// then release all items
			itemMap.clear();
		}
    }

	/* (non-Javadoc)
	 * @see org.openhab.core.internal.items.ItemRegistry#getItem(java.lang.String)
	 */
    @Override
	public Item getItem(String name) throws ItemNotFoundException {
        synchronized (itemMap) {
			for(Collection<Item> items : itemMap.values()) {
				for(Item item : items) {
					if(item.getName().matches(name)) {
						return item;
					}
				}
			}
        }
		throw new ItemNotFoundException(name);
	}

	/* (non-Javadoc)
	 * @see org.openhab.core.internal.items.ItemRegistry#getItemByPattern(java.lang.String)
	 */
    @Override
	public Item getItemByPattern(String name) throws ItemNotFoundException, ItemNotUniqueException {
		Collection<Item> items = getItems(name);
		
		if(items.isEmpty()) {
			throw new ItemNotFoundException(name);
		}
		
		if(items.size()>1) {
			throw new ItemNotUniqueException(name, items);
		}
		
		return items.iterator().next();
		
	}
	
	/* (non-Javadoc)
	 * @see org.openhab.core.internal.items.ItemRegistry#getItems()
	 */
    @Override
	public Collection<Item> getItems() {
		Collection<Item> allItems = new ArrayList<Item>();
        synchronized (itemMap) {
			for(Collection<Item> items : itemMap.values()) {
				allItems.addAll(items);
			}
        }
		return allItems;
	}
	
	/* (non-Javadoc)
	 * @see org.openhab.core.internal.items.ItemRegistry#getItems(java.lang.String)
	 */
    @Override
	public Collection<Item> getItems(String pattern) {
		String regex = pattern.replace("?", ".?").replace("*", ".*?");
		Collection<Item> matchedItems = new ArrayList<Item>();
        synchronized (itemMap) {
			for(Collection<Item> items : itemMap.values()) {
				for(Item item : items) {
					if(item.getName().matches(regex)) {
						matchedItems.add(item);
					}
				}
			}
        }        
        return matchedItems;
	}

	public void addItemProvider(ItemProvider itemProvider) {
		// only add this provider if it does not already exist
		if(!itemMap.containsKey(itemProvider)) {
			Collection<Item> items = Collections.synchronizedCollection(itemProvider.getItems());
			itemProvider.addItemChangeListener(this);
            synchronized (itemMap) {
            	itemMap.put(itemProvider, items);
            }
			logger.debug("Item provider '{}' has been added.", itemProvider.getClass().getSimpleName());
			allItemsChanged(itemProvider, null);
		}
	}

    @Override
	public boolean isValidItemName(String name) {
		return name.matches("[a-zA-Z0-9_]*");
	}

	public void removeItemProvider(ItemProvider itemProvider) {
		if(itemMap.containsKey(itemProvider)) {
			allItemsChanged(itemProvider, null);
            synchronized (itemMap) {
				for(Item item : itemMap.get(itemProvider)) {
					if(item instanceof GenericItem) {
						((GenericItem) item).dispose();
					}
				}
				itemMap.remove(itemProvider);
            }
			itemProvider.removeItemChangeListener(this);
			logger.debug("Item provider '{}' has been removed.", itemProvider.getClass().getSimpleName());
		}
	}
	
	public void setEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
        for(Item item : getItems()) {
            ((GenericItem)item).setEventPublisher(eventPublisher);
        }
	}
	
	public void unsetEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = null;
        for(Item item : getItems()) {
            ((GenericItem)item).setEventPublisher(null);
        }
	}

    @Override
	public void allItemsChanged(ItemProvider provider, Collection<String> oldItemNames) {
		// if the provider did not provide any old item names, we check if we
		// know them and pass them further on to our listeners
		if(oldItemNames==null || oldItemNames.isEmpty()) {
			oldItemNames = new HashSet<String>();
            Collection<Item> oldItems;
            synchronized (itemMap) {
                oldItems = itemMap.get(provider);
            }
			if(oldItems!=null && oldItems.size() > 0) {
				for(Item oldItem : oldItems) {
					oldItemNames.add(oldItem.getName());
				}
			}
		}

		Collection<Item> items = Collections.synchronizedCollection(new ArrayList<Item>());
        synchronized (itemMap) {
        	itemMap.put(provider, items);
        }
		for(Item item : provider.getItems()) {
			if(initializeItem(item)) {
				items.add(item);
			}
		}

		for(ItemRegistryChangeListener listener : listeners) {
			listener.allItemsChanged(oldItemNames);
		}
	}

    @Override
	public void itemAdded(ItemProvider provider, Item item) {
        Collection<Item> items;
        synchronized (itemMap) {
            items = itemMap.get(provider);
        }
		if(items!=null) {
			if(initializeItem(item)) {
				items.add(item);
			} else {
				return;
			}
		}
		for(ItemRegistryChangeListener listener : listeners) {
			listener.itemAdded(item);
		}
	}

    @Override
	public void itemRemoved(ItemProvider provider, Item item) {
        Collection<Item> items;
        synchronized (itemMap) {
            items = itemMap.get(provider);
        }
		if(items!=null) {
			items.remove(item);
		}
		for(ItemRegistryChangeListener listener : listeners) {
			listener.itemRemoved(item);
		}
	}

    @Override
	public void addItemRegistryChangeListener(ItemRegistryChangeListener listener) {
		listeners.add(listener);
	}

    @Override
	public void removeItemRegistryChangeListener(ItemRegistryChangeListener listener) {
		listeners.remove(listener);
	}

	/**
	 * an item should be initialized, which means that the event publisher is
	 * injected and its implementation is notified that it has just been created,
	 * so it can perform any task it needs to do after its creation.
	 * 
	 * @param item the item to initialize
	 * @return false, if the item has no valid name
	 */
	private boolean initializeItem(Item item) {
		if(isValidItemName(item.getName())) {
			if(item instanceof GenericItem) {
				GenericItem genericItem = (GenericItem) item;
				genericItem.setEventPublisher(eventPublisher);
				genericItem.initialize();
			}
			
			if(item instanceof GroupItem) {
				// fill group with its members 
				for(Item i : getItems()) {
					if(i.getGroupNames().contains(item.getName())) {
						((GroupItem)item).addMember(i);
					}
				}
			}
			// add the item to all relevant groups
			for(String groupName : item.getGroupNames()) {
				try {
					Item groupItem = getItem(groupName);
					if(groupItem instanceof GroupItem) {
						((GroupItem)groupItem).addMember(item);
					}
				} catch (ItemNotFoundException e) {
					// the group might not yet be registered, let's ignore this
				}
			}
			return true;
		} else {
			logger.warn("Ignoring item '{}' as it does not comply with" +
					" the naming convention.", item.getName());
			return false;
		}
	}
}
