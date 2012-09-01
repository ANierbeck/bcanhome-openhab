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
package org.openhab.binding.tcp.protocol;

import java.util.List;

import org.openhab.binding.tcp.ChannelBindingProvider;
import org.openhab.binding.tcp.protocol.internal.Direction;
import org.openhab.core.types.Command;

/**
 * Further specialisation of the ChannelBindingProvider interface that serves
 * as base for the "default" ASCII based TCP and UDP network bindings
 * 
 * In all of the defined methods below, "ProtocolCommand" is the 'command' to be
 * executed by the protocol, which is mapped on a openHAB Command through the  binding
 * configuration. See TCP and UDP implementations. In a lot of protocols the ProtocolCommand
 * will just be a (data)string that is sent "as is" to the remote-end
 * 
 * 
 * @author Karel Goderis
 *
 **/

public interface ProtocolBindingProvider extends ChannelBindingProvider {

	public Direction getDirection(String itemName, Command command);

	public String getProtocolCommand(String itemName, Command command);

	public List<String> getItemNames(String protocolCommand);

	public List<Command> getCommands(String itemName, String protocolCommand);

	public List<Command> getCommands(String itemName);

}
