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
package org.openhab.binding.snmp.internal;

import java.io.IOException;
import java.util.Dictionary;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.snmp.SnmpBindingProvider;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
	

/**
 * The SNMP binding listens to SNMP Traps on the configured port and posts new
 * events of type ({@link StringType} to the event bus.
 * 
 * @author Thomas.Eichstaedt-Engelen
 * @since 0.9.0
 */
public class SnmpBinding extends AbstractBinding<SnmpBindingProvider> implements ManagedService, CommandResponder {

	private static final Logger logger = LoggerFactory.getLogger(SnmpBinding.class);

	protected static AbstractTransportMapping<UdpAddress> transport;
	
	/** The local port to bind on and listen to SNMP Traps */
	protected static int port;
	/** The SNMP community to filter SNMP Traps*/
	protected static String community;
	
	
	public void activate() {
	}
	
	public void deactivate() {
		stopListening();
	}
	
	
	/**
	 * Configures a {@link DefaultUdpTransportMapping} and starts listening
	 * on <code>SnmpBinding.port</code> for incoming SNMP Traps.
	 */
	private void listen() {
		UdpAddress address = new UdpAddress(SnmpBinding.port);
		try {
			transport = new DefaultUdpTransportMapping(address);
			MessageDispatcher mDispatcher = new MessageDispatcherImpl();
	
			// add message processing models
			mDispatcher.addMessageProcessingModel(new MPv1());
			mDispatcher.addMessageProcessingModel(new MPv2c());
	
			// add all security protocols
			SecurityProtocols.getInstance().addDefaultProtocols();
			SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());
	
			// Create Target
			CommunityTarget target = new CommunityTarget();
			target.setCommunity(new OctetString(SnmpBinding.community));
	
			Snmp snmp = new Snmp(mDispatcher, transport);
			snmp.addCommandResponder(this);
	
			transport.listen();
			logger.debug("SNMP binding is listening on " + address);
		} catch (IOException ioe) {
			logger.error("couldn't listen to " + address, ioe);
		}
	}
	
	/**
	 * Stops listing for incoming SNMP Traps
	 */
	private void stopListening() {
		if (SnmpBinding.transport != null) {
			try {
				SnmpBinding.transport.close();
			} catch (IOException ioe) {
				logger.error("couldn't close connection", ioe);
			}
			SnmpBinding.transport = null;
		}
	}
	
	/**
	 * Will be called whenever a {@link PDU} is received on the given port
	 * specified in the listen() method. It extracts a {@link Variable} according
	 * to the configured OID prefix and sends its value to the event bus. 
	 */
	public void processPdu(CommandResponderEvent event) {
		PDU pdu = event.getPDU();
		if (pdu != null) {
			logger.debug("Received PDU '{}'", event.getPDU());
			for (SnmpBindingProvider provider : providers) {
				for (String itemName : provider.getItemNames()) {
					OID oid = provider.getOID(itemName);				
					Variable variable = pdu.getVariable(oid);
					if (variable != null) {
						Class<? extends Item> itemType = provider.getItemType(itemName);
						
						State state = null;
						if (itemType.isAssignableFrom(StringItem.class)) {
							state = StringType.valueOf(variable.toString());
						} else if (itemType.isAssignableFrom(NumberItem.class)) {
							state = DecimalType.valueOf(variable.toString());
						}

						if (state != null) {
							eventPublisher.postUpdate(itemName, state);
						} else {
							logger.debug("'{}' couldn't be parsed to a State. Valid State-Types are String and Number", variable.toString());
						}
					} else {
						logger.trace("PDU doesn't contain a variable with OID ‘{}‘", oid.toString());
					}
				}
			}
			
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public void updated(Dictionary config) throws ConfigurationException {
		if (config != null) {
			stopListening();

			SnmpBinding.community = (String) config.get("community");
			if (StringUtils.isBlank(SnmpBinding.community)) {
				SnmpBinding.community = "public";
				logger.info("didn't find SNMP community configuration -> listen to SNMP community {}", SnmpBinding.community);
			}
			
			String portString = (String) config.get("port");
			if (StringUtils.isNotBlank(portString) && portString.matches("\\d*")) {
				SnmpBinding.port = Integer.valueOf(portString).intValue();
			}
			else {
				SnmpBinding.port = 162; // SNMP default port
				logger.info("didn't find SNMP port configuration or configuration is invalid -> listen to SNMP default port {}", SnmpBinding.port);
			}
			
			listen();
		}
	}
	
	
}
