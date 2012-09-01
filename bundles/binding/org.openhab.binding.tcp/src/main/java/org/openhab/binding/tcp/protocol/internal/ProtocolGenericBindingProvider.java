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

package org.openhab.binding.tcp.protocol.internal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.openhab.binding.tcp.protocol.ProtocolBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.types.Command;
import org.openhab.core.types.TypeParser;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * The "standard" TCP and UDP network bindings follow the same configuration format:
 * 
 * tcp=">[ON:192.168.0.1:3000:some text], >[OFF:192.168.0.1:3000:some other command]"
 * tcp="<[*:192.168.0.1:3000:some text]" - for String Items
 * 
 * direction[openhab command:hostname:port number:protocol command]
 * 
 * For String Items, the "protocol command" is quite irrelevant as the Item will be updated with the incoming string
 * openhab commands can be repeated more than once for a given Item, e.g. receving ON command could trigger to pieces
 * of data to be sent to for example to different host:port combinations,...
 * 
 * @author kgoderis
 *
 */
abstract class ProtocolGenericBindingProvider extends AbstractGenericBindingProvider implements ProtocolBindingProvider {

	static final Logger logger = LoggerFactory
			.getLogger(ProtocolGenericBindingProvider.class);

	/** {@link Pattern} which matches a binding configuration part */
	private static final Pattern BASE_CONFIG_PATTERN = Pattern.compile("(<|>|\\*)\\[(.*):(.*):(.*):(.*)\\]");

	@Override
	public void validateItemType(Item item, String bindingConfig)
			throws BindingConfigParseException {                
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item,
			String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);

		if (bindingConfig != null) {
			parseAndAddBindingConfig(item, bindingConfig);
		} else {
			logger.warn(getBindingType()+" bindingConfig is NULL (item=" + item
					+ ") -> processing bindingConfig aborted!");
		}
	}

	private void parseAndAddBindingConfig(Item item,
			String bindingConfigs) throws BindingConfigParseException {

		String bindingConfig = StringUtils.substringBefore(bindingConfigs, ",");
		String bindingConfigTail = StringUtils.substringAfter(bindingConfigs,
				",");

		ProtocolBindingConfig newConfig = new ProtocolBindingConfig();
		parseBindingConfig(newConfig,item,bindingConfig);
		addBindingConfig(item, newConfig);              

		while (StringUtils.isNotBlank(bindingConfigTail)) {
			bindingConfig = StringUtils.substringBefore(bindingConfigTail, ",");
			bindingConfig = StringUtils.strip(bindingConfig);
			bindingConfigTail = StringUtils.substringAfter(bindingConfig,
					",");
			parseBindingConfig(newConfig,item, bindingConfig);
			addBindingConfig(item, newConfig);      
		}

	}

	/**
	 * Parses the configuration string and update the provided config
	 * 
	 * @param config
	 * @param item
	 * @param bindingConfig
	 * @throws BindingConfigParseException
	 */
	private void parseBindingConfig(ProtocolBindingConfig config,Item item,
			String bindingConfig) throws BindingConfigParseException {

		if(bindingConfig != null){

			Matcher matcher = BASE_CONFIG_PATTERN.matcher(bindingConfig);

			if (!matcher.matches()) {
				throw new BindingConfigParseException(getBindingType()+
						" binding configuration must consist of five parts [config="
								+ matcher + "]");
			} else {

				String direction = matcher.group(1);
				Direction directionType = Direction.BIDIRECTIONAL;
				String commandAsString = matcher.group(2);
				String host = matcher.group(3);
				String port = matcher.group(4);
				String tcpCommand = matcher.group(5);

				if(direction.equals(">")){
					directionType = Direction.OUT;
				}else if (direction.equals("<")){
					directionType = Direction.IN;
				}else if (direction.equals("*")){
					directionType = Direction.BIDIRECTIONAL;
				}

				ProtocolBindingConfigElement newElement = new ProtocolBindingConfigElement(host,Integer.parseInt(port),directionType,tcpCommand);

				Command command = createCommandFromString(item, commandAsString);
				config.put(command, newElement);

			}
		}
		else
			return;

	}

	/**
	 * Creates a {@link Command} out of the given <code>commandAsString</code>
	 * incorporating the {@link TypeParser}.
	 *  
	 * @param item
	 * @param commandAsString
	 * 
	 * @return an appropriate Command (see {@link TypeParser} for more 
	 * information
	 * 
	 * @throws BindingConfigParseException if the {@link TypeParser} couldn't
	 * create a command appropriately
	 * 
	 * @see {@link TypeParser}
	 */
	private Command createCommandFromString(Item item, String commandAsString) throws BindingConfigParseException {

		Command command = TypeParser.parseCommand(
				item.getAcceptedCommandTypes(), commandAsString);

		if (command == null) {
			throw new BindingConfigParseException("couldn't create Command from '" + commandAsString + "' ");
		} 

		return command;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getHost(String itemName, Command command) {
		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(command) != null ? config.get(command).getHost() : null;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getPort(String itemName, Command command) {
		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(command) != null ? config.get(command).getPort() : null;
	}


	/**
	 * {@inheritDoc}
	 */
	public Direction getDirection(String itemName, Command command) {
		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(command) != null ? config.get(command).getDirection() : null;
	}


	/**
	 * {@inheritDoc}
	 */
	public String getProtocolCommand(String itemName, Command command) {
		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(command) != null ? config.get(command).getNetworkCommand() : null;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<Command> getCommands(String itemName){
		List<Command> commands = new ArrayList<Command>();
		ProtocolBindingConfig aConfig = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		for(Command aCommand : aConfig.keySet()) {
			commands.add(aCommand);
		}

		return commands;
	}

	/**
	 * This is an internal data structure to map commands to 
	 * {@link ProtocolBindingConfigElement }. There will be map like 
	 * <code>ON->ProtocolBindingConfigElement</code>
	 */
	static class ProtocolBindingConfig extends HashMap<Command, ProtocolBindingConfigElement> implements BindingConfig {

		private static final long serialVersionUID = 6363085986521089771L;

	}


	static class ProtocolBindingConfigElement implements BindingConfig {

		final private String host;
		final private int port;
		final private Direction direction;
		final private String networkCommand;


		public ProtocolBindingConfigElement(String host, int port, Direction direction, String networkCommand) {
			this.host = host;
			this.port = port;
			this.direction = direction;
			this.networkCommand = networkCommand;
		}

		@Override
		public String toString() {
			return "ProtocolBindingConfigElement [Direction=" + direction
					+ ", host=" + host + ", port=" + port
					+ ", cmd=" + networkCommand + "]";
		}


		/**
		 * @return the networkCommand
		 */
		 public String getNetworkCommand() {
			return networkCommand;
		}

		/**
		 * @return the direction
		 */
		 public Direction getDirection() {
			 return direction;
		 }

		 /**
		  * @return the host
		  */
		 public String getHost() {
			 return host;
		 }

		 /**
		  * @return the port
		  */
		 public int getPort() {
			 return port;
		 }
	}

	@Override
	public InetSocketAddress getInetSocketAddress(String itemName, Command command) {

		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		ProtocolBindingConfigElement element = config.get(command);
		InetSocketAddress socketAddress = new InetSocketAddress(element.getHost(),element.getPort());

		return socketAddress;
	}

	@Override
	public List<InetSocketAddress> getInetSocketAddresses(String itemName){
		//logger.debug("getInetSocketAddresses for "+itemName);
		List<InetSocketAddress> theList = new ArrayList<InetSocketAddress>();
		ProtocolBindingConfig config = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		if(config != null ){
			for(Command command : config.keySet()) {
				//logger.debug("found command "+command.toString()+" for this item, will try to create InetAddress "+config.get(command).getHost()+":"+config.get(command).getPort());
				InetSocketAddress anAddress = null;
				try {
					anAddress = new InetSocketAddress(InetAddress.getByName(config.get(command).getHost()),config.get(command).getPort());
				} catch (UnknownHostException e) {
					logger.warn("Could not resolve the hostname {} for item {}",config.get(command).getHost(),itemName);
				}
				theList.add(anAddress);
			}
		}
		return theList;
	}
	
	public Collection<String> getItemNames(String host, int port) {

		List<String> items = new ArrayList<String>();
		
		for (String itemName : bindingConfigs.keySet()) {
			ProtocolBindingConfig aConfig = (ProtocolBindingConfig) bindingConfigs.get(itemName);
			for(Command aCommand : aConfig.keySet()) {
				ProtocolBindingConfigElement anElement = (ProtocolBindingConfigElement) aConfig.get(aCommand);				
				if(anElement.getHost().equals(host) && anElement.getPort()==port) {
					if(!items.contains(itemName)) {
						items.add(itemName);
					}
				}			
			}
		}
		return items; 
		
	}

	@Override
	public List<String> getItemNames(String protocolCommand) {
		List<String> itemNames = new ArrayList<String>();
		for (String itemName : bindingConfigs.keySet()) {
			ProtocolBindingConfig aConfig = (ProtocolBindingConfig) bindingConfigs.get(itemName);
			for(Command aCommand : aConfig.keySet()) {
				ProtocolBindingConfigElement anElement = (ProtocolBindingConfigElement) aConfig.get(aCommand);
				if(anElement.networkCommand.equals(protocolCommand)) {
					itemNames.add(itemName);
				}
			}

		}
		return itemNames;               
	}

	public List<Command> getCommands(String itemName, String protocolCommand){
		List<Command> commands = new ArrayList<Command>();
		ProtocolBindingConfig aConfig = (ProtocolBindingConfig) bindingConfigs.get(itemName);
		for(Command aCommand : aConfig.keySet()) {
			ProtocolBindingConfigElement anElement = (ProtocolBindingConfigElement) aConfig.get(aCommand);
			if(anElement.networkCommand.equals(protocolCommand)) {
				commands.add(aCommand);
			}
		}               
		return commands;
	}
}