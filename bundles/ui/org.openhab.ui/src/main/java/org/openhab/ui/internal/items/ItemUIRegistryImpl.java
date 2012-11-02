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
package org.openhab.ui.internal.items;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.ItemRegistryChangeListener;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationHelper;
import org.openhab.core.transform.TransformationService;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.UnDefType;
import org.openhab.model.sitemap.Group;
import org.openhab.model.sitemap.LinkableWidget;
import org.openhab.model.sitemap.Sitemap;
import org.openhab.model.sitemap.SitemapFactory;
import org.openhab.model.sitemap.Widget;
import org.openhab.ui.internal.UIActivator;
import org.openhab.ui.items.ItemUIProvider;
import org.openhab.ui.items.ItemUIRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a simple way to ask different item providers by a 
 * single method call, i.e. the consumer does not need to iterate over all
 * registered providers as this is done inside this class.
 * 
 * @author Kai Kreuzer
 * @since 0.2.0
 *
 */
public class ItemUIRegistryImpl implements ItemUIRegistry {
	
	private static final String ICON_NONE = "none";

	private final static Logger logger = LoggerFactory.getLogger(ItemUIRegistryImpl.class);
	
	/* the file extension of the images */
	protected static final String IMAGE_EXT = ".png";

	/* the image location inside the installation folder */
	protected static final String IMAGE_LOCATION = "./webapps/images/";

	/* RegEx to extract and parse a function String <code>'\[(.*?)\((.*)\):(.*)\]'</code> */
	protected static final Pattern EXTRACT_TRANSFORMFUNCTION_PATTERN = Pattern.compile("\\[(.*?)\\((.*)\\):(.*)\\]");
	
	/* RegEx to identify format patterns */
	protected static final String IDENTIFY_FORMAT_PATTERN_PATTERN = "%(\\d\\$)?(<)?(\\.\\d)?[a-zA-Z]{1,2}";

	protected Set<ItemUIProvider> itemUIProviders = new HashSet<ItemUIProvider>();

	protected ItemRegistry itemRegistry;

	public ItemUIRegistryImpl() {}

	public void setItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = itemRegistry;
	}

	public void unsetItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = null;
	}

	public void addItemUIProvider(ItemUIProvider itemUIProvider) {
		itemUIProviders.add(itemUIProvider);
	}

	public void removeItemUIProvider(ItemUIProvider itemUIProvider) {
		itemUIProviders.remove(itemUIProvider);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getIcon(String itemName) {		
		for(ItemUIProvider provider : itemUIProviders) {
			String currentIcon = provider.getIcon(itemName);
			if(currentIcon!=null) {
				return currentIcon;
			}
		}
		// do some reasonable default, if no provider had an answer
		// try to get the item type from the item name
		Class<? extends Item> itemType = getItemType(itemName);
		if(itemType==null) return null;
		
		// we handle items here that have no specific widget,
		// e.g. the default widget of a rollerblind is "Switch".
		// We want to provide a dedicated default icon for it
		// like "rollerblind".
		if (itemType.equals(NumberItem.class) ||
				itemType.equals(ContactItem.class) ||
				itemType.equals(RollershutterItem.class)) {
			return itemType.getSimpleName().replace("Item", "").toLowerCase();
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getLabel(String itemName) {
		for(ItemUIProvider provider : itemUIProviders) {
			String currentLabel = provider.getLabel(itemName);
			if(currentLabel!=null) {
				return currentLabel;
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public Widget getWidget(String itemName) {
		for(ItemUIProvider provider : itemUIProviders) {
			Widget currentWidget = provider.getWidget(itemName);
			if(currentWidget!=null) {
				return currentWidget;
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public Widget getDefaultWidget(Class<? extends Item> itemType, String itemName) {
		for(ItemUIProvider provider : itemUIProviders) {
			Widget widget = provider.getDefaultWidget(itemType, itemName);
			if(widget!=null) {
				return widget;
			}
		}

		// do some reasonable default, if no provider had an answer
		// if the itemType is not defined, try to get it from the item name
		if(itemType==null) {
			itemType = getItemType(itemName);
		}
		if(itemType==null) return null;
		
		if (itemType.equals(SwitchItem.class)) {
			return SitemapFactory.eINSTANCE.createSwitch();
		}
		if (itemType.equals(GroupItem.class)) {
			return SitemapFactory.eINSTANCE.createGroup();
		}
		if (NumberItem.class.isAssignableFrom(itemType)) {
			return SitemapFactory.eINSTANCE.createText();
		}
		if (itemType.equals(ContactItem.class)) {
			return SitemapFactory.eINSTANCE.createText();
		}
		if (itemType.equals(RollershutterItem.class)) {
			return SitemapFactory.eINSTANCE.createSwitch();
		}
		if (itemType.equals(StringItem.class)) {
			return SitemapFactory.eINSTANCE.createText();
		}
		if (itemType.equals(DimmerItem.class)) {
			return SitemapFactory.eINSTANCE.createSlider();
		}

		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getLabel(Widget w) {
		String label = getLabelFromWidget(w);
		
		// now insert the value, if the state is a string or decimal value and there is some formatting pattern defined in the label 
		// (i.e. it contains at least a %)
		String itemName = w.getItem();
		if(itemName!=null && label.contains("[")) {
			
			int indexOpenBracket = label.indexOf("[");
			int indexCloseBracket = label.indexOf("]");
			
			State state = null;
			String formatPattern = label.substring(indexOpenBracket + 1, indexCloseBracket);
			try {
				Item item = getItem(itemName);
				// TODO: TEE: we should find a more generic solution here! When
				// using indexes in formatString this 'contains' will fail again
				// and will cause an 'java.util.IllegalFormatConversionException:
				// d != java.lang.String' later on when trying to format a String
				// as %d (number).
				if (label.contains("%d")) {
					// a number is requested
					state = item.getState();
					if(!(state instanceof DecimalType)) {
						state = item.getStateAs(DecimalType.class);
					}
				} else {
					state = item.getState();
				}
			} catch (ItemNotFoundException e) {
				logger.error("Cannot retrieve item for widget {}", w.eClass().getInstanceTypeName());
			}

			if (state==null || state instanceof UnDefType) {
				formatPattern = formatUndefined(formatPattern);
			} else if (state instanceof Type) {
				formatPattern = ((Type) state).format(formatPattern);
			}

			label = label.substring(0, indexOpenBracket + 1) + formatPattern + label.substring(indexCloseBracket);
		}
		
		label = transform(label);
		
		return label;
	}

	private String getLabelFromWidget(Widget w) {
		String label = null;
		if (w.getLabel() != null) {
			// if there is a label defined for the widget, use this
			label = w.getLabel();
		} else {
			String itemName = w.getItem();
			if (itemName != null) {
				// check if any item ui provider provides a label for this item 
				label = getLabel(itemName);
				// if there is no item ui provider saying anything, simply use the name as a label
				if (label == null) label = itemName;
			}
		}
		// use an empty string, if no label could be found
		return label != null ? label : "";
	}

	/**
	 * Takes the given <code>formatPattern</code> and replaces it with a analog
	 * String-based pattern to replace all value Occurrences with a dash ("-")
	 * 
	 * @param formatPattern the original pattern which will be replaces by a
	 * String pattern.
	 * @return a formatted String with dashes ("-") as value replacement
	 */
	protected String formatUndefined(String formatPattern) {
		String undefinedFormatPattern = 
			formatPattern.replaceAll(IDENTIFY_FORMAT_PATTERN_PATTERN, "%1\\$s");
		String formattedValue = String.format(undefinedFormatPattern, "-");
		return formattedValue;
	}
	
	/*
	 * check if there is a status value being displayed on the right side of the
	 * label (the right side is signified by being enclosed in square brackets [].
	 * If so, check if the value starts with the call to a transformation service 
	 * (e.g. "[MAP(en.map):%s]") and execute the transformation in this case.
	 */
	private String transform(String label) {
		if(label.contains("[") && label.endsWith("]")) {
			Matcher matcher = EXTRACT_TRANSFORMFUNCTION_PATTERN.matcher(label);
			if(matcher.find()) {
				String type = matcher.group(1);
				String pattern = matcher.group(2);
				String value = matcher.group(3);
				TransformationService transformation = 
					TransformationHelper.getTransformationService(UIActivator.getContext(), type);
				if(transformation!=null) {
					try {
						label = label.substring(0, label.indexOf("[")+1) + transformation.transform(pattern, value) + "]";
					} catch (TransformationException e) {
						logger.error("transformation throws exception [transformation="
								+ transformation + ", value=" + value + "]", e);
						label = label.substring(0, label.indexOf("[")+1) + value + "]";
					}
				} else {
					logger.warn("couldn't transform value in label because transformationService of type '{}' is unavailable", type);
					label = label.substring(0, label.indexOf("[")+1) + value + "]";
				}
			}
		}
		return label;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getIcon(Widget w) {
		String widgetTypeName = w.eClass().getInstanceTypeName().substring(w.eClass().getInstanceTypeName().lastIndexOf(".")+1);
		
		// the default is the widget type name, e.g. "switch"
		String icon = widgetTypeName.toLowerCase();
		
		// if an icon is defined for the widget, use it
		if(w.getIcon()!=null) {
			icon = w.getIcon();
		} else {
			// otherwise check if any item ui provider provides an icon for this item			
			String itemName = w.getItem();
			if(itemName!=null) {
				String result = getIcon(itemName);
				if(result!=null) icon = result;
			}
		}
		
		// now add the state, if the string does not already contain a state information
		if(!icon.contains("-")) {
			Object state = getState(w);
			if(!state.equals(UnDefType.UNDEF)) {
				if(state instanceof PercentType) {
					// we do a special treatment for percent types; we try to find the icon of the biggest value
					// that is still smaller or equal to the current state. 
					// Example: if there are icons *-0.png, *-50.png and *-100.png, we choose *-0.png, if the state
					// is 40, and *-50.png, if the state is 70.
					int iconState = ((PercentType) state).toBigDecimal().intValue();
					String testIcon;
					do {
						testIcon = icon + "-" + String.valueOf(iconState--);
					} while(!iconExists(testIcon) && iconState>=0);
					icon = testIcon;
				} else {
					// for all other types, just add the string representation of the state
					icon += "-" + state.toString().toLowerCase();
				}
			}
		}
		
		// if the icon contains a status part, but does not exist, return the icon without status
		if(iconExists(icon) || !icon.contains("-")) {
			return icon;
		} else {
			icon = icon.substring(0, icon.indexOf("-"));
			if(iconExists(icon)) {
				return icon;
			} else {
				// see http://code.google.com/p/openhab/issues/detail?id=63
				return ICON_NONE;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public State getState(Widget w) {
		String itemName = w.getItem();
		if(itemName!=null) {
			try {
				Item item = getItem(itemName);
				return item.getState();
			} catch (ItemNotFoundException e) {
				logger.error("Cannot retrieve item '{}' for widget {}", new String[] { itemName, w.eClass().getInstanceTypeName() });
			}
		}
		return UnDefType.UNDEF;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public Widget getWidget(Sitemap sitemap, String id) {
		if(id.length()>0) {
			// see if the id is an itemName and try to get the a widget for it
			Widget w = getWidget(id);
			if(w==null) { 
				// try to get the default widget instead
				w = getDefaultWidget(null, id);
			}
			if(w!=null) {
				w.setItem(id);
				return w;
			} else {
				try {
					w = sitemap.getChildren().get(Integer.valueOf(id.substring(0, 2)));
					for(int i = 2; i < id.length(); i+=2) {
						w = ((LinkableWidget)w).getChildren().get(Integer.valueOf(id.substring(i, i+2)));
					}
					return w;
				} catch(NumberFormatException e) {
					// no valid number, so the requested page id does not exist
				}
			}
		}
		logger.warn("Cannot find page for id '{}'.", id);
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public EList<Widget> getChildren(LinkableWidget w) {
		if(w instanceof Group && ((LinkableWidget)w).getChildren().isEmpty()) {
			return getDynamicGroupChildren((Group) w);
		} else {
			return ((LinkableWidget)w).getChildren();
		}
	}
	
	/** 
	 * This method creates a list of children for a group dynamically.
	 * If there are no explicit children defined in a sitemap, the children
	 * can thus be created on the fly by iterating over the members of the group item.
	 * 
	 * @param group The group widget to get children for
	 * @return a list of default widgets provided for the member items
	 */
	private EList<Widget> getDynamicGroupChildren(Group group) {
		EList<Widget> children = new BasicEList<Widget>();
		String itemName = group.getItem();
		try {
			Item item = getItem(itemName);
			if(item instanceof GroupItem) {
				GroupItem groupItem = (GroupItem) item;
				for(Item member : groupItem.getMembers()) {
					Widget widget = getDefaultWidget(member.getClass(), member.getName());
					if(widget!=null) {
						widget.setItem(member.getName());
						children.add(widget);
					}					
				}
			} else {
				logger.warn("Item '{}' is not a group.", item.getName());
			}
		} catch (ItemNotFoundException e) {
			logger.warn("Group '{}' could not be found.", group.getLabel(), e);
		}
		return children;
		
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean iconExists(String icon) {
		String iconLocation = IMAGE_LOCATION + icon + IMAGE_EXT;
		File file = new File(iconLocation);
		if(file.exists()) {
			return true;
		} else {
			return false;
		}
	}

	private Class<? extends Item> getItemType(String itemName) {
		try {
			Item item = itemRegistry.getItem(itemName);
			return item.getClass();
		} catch (ItemNotFoundException e) {
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Item getItem(String name) throws ItemNotFoundException {
		if(itemRegistry!=null) {
			return itemRegistry.getItem(name);
		} else {
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Item getItemByPattern(String name) throws ItemNotFoundException, ItemNotUniqueException {
		if(itemRegistry!=null) {
			return itemRegistry.getItemByPattern(name);
		} else {
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Collection<Item> getItems() {
		if(itemRegistry!=null) {
			return itemRegistry.getItems();
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Collection<Item> getItems(String pattern) {
		if(itemRegistry!=null) {
			return itemRegistry.getItems(pattern);
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isValidItemName(String itemName) {
		if(itemRegistry!=null) {
			return itemRegistry.isValidItemName(itemName);
		} else {
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void addItemRegistryChangeListener(
			ItemRegistryChangeListener listener) {
		if(itemRegistry!=null) {
			itemRegistry.addItemRegistryChangeListener(listener);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeItemRegistryChangeListener(
			ItemRegistryChangeListener listener) {
			if(itemRegistry!=null) {
				itemRegistry.removeItemRegistryChangeListener(listener);
			}
	}

	/**
	 * {@inheritDoc}
	 */
	public String getWidgetId(Widget w) {
		String id = "";
		while(w.eContainer() instanceof Widget) {
			Widget parent = (Widget) w.eContainer();
			String index = String.valueOf(((LinkableWidget)parent).getChildren().indexOf(w));
			if(index.length()==1) index = "0" + index; // make it two digits
			id =  index + id;
			w = parent;
		}
		if(w.eContainer() instanceof Sitemap) {
			Sitemap sitemap = (Sitemap) w.eContainer();
			String index = String.valueOf(sitemap.getChildren().indexOf(w));
			if(index.length()==1) index = "0" + index; // make it two digits
			id =  index + id;
		}
		
		// if the widget is dynamically created and not available in the sitemap,
		// use the item name as the id
		if(w.eContainer()==null) {
			String itemName = w.getItem();
			id = itemName;
		}
		return id;
	}

}
