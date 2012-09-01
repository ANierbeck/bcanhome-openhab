package org.openhab.binding.sonos.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.openhab.binding.sonos.SonosCommandType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.action.ActionArgumentValue;
import org.teleal.cling.model.action.ActionException;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Action;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.meta.StateVariable;
import org.teleal.cling.model.meta.StateVariableTypeDetails;
import org.teleal.cling.model.state.StateVariableValue;
import org.teleal.cling.model.types.Datatype;
import org.teleal.cling.model.types.InvalidValueException;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.model.types.UnsignedIntegerFourBytes;
import org.teleal.cling.transport.impl.apache.StreamClientConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamClientImpl;
import org.teleal.cling.transport.impl.apache.StreamServerConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamServerImpl;
import org.teleal.cling.transport.spi.NetworkAddressFactory;
import org.teleal.cling.transport.spi.StreamClient;
import org.teleal.cling.transport.spi.StreamServer;
import org.xml.sax.SAXException;
import org.openhab.io.net.http.HttpUtil;

/**
 * Internal data structure which carries the connection details of one Sonos player
 * (there could be several)
 * 
 */
class SonosZonePlayer {
	
	private static Logger logger = LoggerFactory.getLogger(SonosBinding.class);
	
	protected final int interval = 600;
	private boolean isConfigured = false;
	
	/** the default socket timeout when requesting an url */
	private static final int SO_TIMEOUT = 5000;

	private RemoteDevice device;
	private UDN udn;
	private DateTime lastOPMLQuery;
	
	
	static protected UpnpService upnpService;
	protected SonosBinding sonosBinding;

	private Map<String, StateVariableValue> stateMap = Collections.synchronizedMap(new HashMap<String,StateVariableValue>());
	
	/**
	 * @return the stateMap
	 */
	public Map<String, StateVariableValue> getStateMap() {
		return stateMap;
	}

	public boolean isConfigured() {
		return isConfigured;
	}

	SonosZonePlayer(SonosBinding binding) {
		
		if( binding != null) {
			sonosBinding = binding;
		}	
	}
	
	
	private void enableGENASubscriptions(){
		
		if(device!=null && isConfigured()) {
		
		// Create a GENA subscription of each service for this device, if supported by the device        
		List<SonosCommandType> subscriptionCommands = SonosCommandType.getSubscriptions();
		List<String> addedSubscriptions = new ArrayList<String>();

		for(SonosCommandType c : subscriptionCommands){
			Service service = device.findService(new UDAServiceId(c.getService()));			
			if(service != null && !addedSubscriptions.contains(c.getService())) {
				SonosPlayerSubscriptionCallback callback = new SonosPlayerSubscriptionCallback(service,interval);
				//logger.debug("Added a GENA Subscription for service {} on device {}",service,device);
				addedSubscriptions.add(c.getService());
				upnpService.getControlPoint().execute(callback);
			}
		}
		}
	}

    
    protected boolean isUpdatedValue(String valueName,StateVariableValue newValue) {
    	if(newValue != null && valueName != null) {
    		StateVariableValue oldValue = stateMap.get(valueName);
    		
    		if(newValue.getValue()== null) {
    			// we will *not* store an empty value, thank you.
    			return false;
    		} else {
    			if(oldValue == null) {
    				// there was nothing stored before
    				return true;
    			} else {
    				if (oldValue.getValue() == null) {
    					// something was defined, but no value present
    					return true;
    				} else {
        				if(newValue.getValue().equals(oldValue.getValue())) {
        					return false;
        				} else {
        					return true;
        				}
    				}
    			}
    		}
    	}	
    	
    	return false;
    }


    
    protected void processStateVariableValue(String valueName,StateVariableValue newValue) {
    	if(newValue!=null && isUpdatedValue(valueName,newValue)) {
    		Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
    		mapToProcess.put(valueName,newValue);
    		stateMap.putAll(mapToProcess);
    		sonosBinding.processVariableMap(device,mapToProcess);
    	}
    }
	
	/**
	 * @return the device
	 */
	public RemoteDevice getDevice() {
		return device;
	}

	/**
	 * @param device the device to set
	 */
	public void setDevice(RemoteDevice device) {
		this.device = device;
	}
    
    public class SonosPlayerSubscriptionCallback extends SubscriptionCallback {
    	    	

    	public SonosPlayerSubscriptionCallback(Service service) {
    		super(service);
    		// TODO Auto-generated constructor stub
    	}

    	public SonosPlayerSubscriptionCallback(Service service,
    			int requestedDurationSeconds) {
    		super(service, requestedDurationSeconds);
    	}

      	@Override
    	public void established(GENASubscription sub) {
    		//logger.debug("Established: " + sub.getSubscriptionId());
    	}

    	@Override
    	protected void failed(GENASubscription subscription,
    			UpnpResponse responseStatus,
    			Exception exception,
    			String defaultMsg) {
    		logger.error(defaultMsg);
    	}

    	public void eventReceived(GENASubscription sub) {

    		// get the device linked to this service linked to this subscription
    		
    		//logger.debug("Received GENA Event on {}",sub.getService());

    		Map<String, StateVariableValue> values = sub.getCurrentValues();        
    		Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
    		Map<String, StateVariableValue> parsedValues = null;
    		
    		// now, lets deal with the specials - some UPNP responses require some XML parsing
    		// or we need to update our internal data structure
    		// or are things we want to store for further reference
    		    		
    		for(String stateVariable : values.keySet()){

    			if(stateVariable.equals("LastChange") && service.getServiceType().getType().equals("AVTransport")){
    				try {
    					parsedValues = SonosXMLParser.getAVTransportFromXML(values.get(stateVariable).toString());
    					//logger.debug("parsed map {}",parsedValues.toString());
    					for(String someValue : parsedValues.keySet()) {
    						if(isUpdatedValue(someValue,parsedValues.get(someValue))){
    							//logger.debug("New value found {} on {}",someValue,sub.getService().getDevice());
    							//logger.debug("update {} {}",parsedValues.get(someValue).getValue().toString(),stateMap.get(someValue).getValue().toString());
    							mapToProcess.put(someValue,parsedValues.get(someValue));
    						}
    					}
    				} catch (SAXException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}

    			} else

    				if(stateVariable.equals("LastChange") && service.getServiceType().getType().equals("RenderingControl")){
    					try {
    						parsedValues = SonosXMLParser.getRenderingControlFromXML(values.get(stateVariable).toString());
        					for(String someValue : parsedValues.keySet()) {
        						if(isUpdatedValue(someValue,parsedValues.get(someValue))){
        							mapToProcess.put(someValue,parsedValues.get(someValue));
        						}
        					}
    					} catch (SAXException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
    				} else if(isUpdatedValue(stateVariable,values.get(stateVariable))){
    					mapToProcess.put(stateVariable, values.get(stateVariable));
    				}

    		}    		

    		if(isConfigured) {
    			stateMap.putAll(mapToProcess);
    			//logger.debug("to process {}",mapToProcess.toString());
    			//logger.debug("statemap {}",stateMap.toString());
    			sonosBinding.processVariableMap(device,mapToProcess);
    		}
    	}

    	public void eventsMissed(GENASubscription sub, int numberOfMissedEvents) {
    		logger.warn("Missed events: " + numberOfMissedEvents);
    	}

    	@Override
    	protected void ended(GENASubscription subscription,
    			CancelReason reason, UpnpResponse responseStatus) {
    		// TODO Auto-generated method stub

    	}
    }
	
	public void setService(UpnpService service) {
		if(upnpService == null) {
			upnpService = service; 
			//= new UpnpServiceImpl(new SonosUpnpServiceConfiguration());
			//logger.debug("Creating a new UPNP Service handler on {}",device.getDisplayString());
		}
		if(upnpService !=null) {
			isConfigured = true;
			//logger.debug("{} is fully configured",device.getDisplayString());
			enableGENASubscriptions();
		}

	}

	/**
	 * @return the udn
	 */
	public UDN getUdn() {
		return udn;
	}

	/**
	 * @param udn the udn to set
	 */
	public void setUdn(UDN udn) {
		this.udn = udn;
	}

	@Override
	public String toString() {
		return "Sonos [udn=" + udn + ", device=" + device +"]";
	}
	
	public boolean play() {
		if(isConfigured()) {
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("Play");
		ActionInvocation invocation = new ActionInvocation(action);

		invocation.setInput("Speed", "1");
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
	}
	
	public boolean playRadio(String station){
		
		if(isConfigured()) {
		
			List<SonosEntry> stations = getFavoriteRadios();
			
			SonosEntry theEntry = null;
			
			// search for the appropriate radio based on its name (title)
			for(SonosEntry someStation : stations){
				if(someStation.getTitle().equals(station)){
					theEntry = someStation;
					break;
				}
			}
			
			// set the URI of the group coordinator
			if(theEntry != null) {
			
				SonosZonePlayer coordinator = sonosBinding.getCoordinatorForZonePlayer(this);
				coordinator.setCurrentURI(theEntry);
				coordinator.play();

				return true;
			}
			else {
				return false;
			}	
		} else {
			return false;
		}
		
	}
	
	public boolean stop() {
		if(isConfigured()) {
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("Stop");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
	}
	
	public boolean pause() {
		if(isConfigured()) {
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("Pause");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
	}
	
	public boolean next() {
		if(isConfigured()) {
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("Next");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
	}
	
	public boolean previous() {
		if(isConfigured()) {
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("Previous");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
	}
	
	public String getZoneName() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("ZoneName");
			if(value != null) {
			return value.getValue().toString();
			}
		}
			return null;
		
	}
	
	public String getZoneGroupID() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("LocalGroupUUID");
			if(value != null) {
			return value.getValue().toString();
			}
		}
			return null;
		
	}
	
	public boolean isGroupCoordinator() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("GroupCoordinatorIsLocal");
			if(value != null) {
				return (Boolean) value.getValue();
			}
		}

			return false;
		
	}
	
	public SonosZonePlayer getCoordinator(){
		return sonosBinding.getCoordinatorForZonePlayer(this);
	}
	
	public boolean addMember(SonosZonePlayer newMember) {
		if(newMember != null && isConfigured()) {
			
			SonosEntry entry = new SonosEntry("", "", "", "", "", "", "", "x-rincon:"+udn.getIdentifierString());
			return newMember.setCurrentURI(entry);		

		} else {
			return false;
		}
	}
	
	public boolean removeMember(SonosZonePlayer oldMember){
		if(oldMember != null && isConfigured()) {
			
			oldMember.becomeStandAlonePlayer();
			SonosEntry entry = new SonosEntry("", "", "", "", "", "", "", "x-rincon-queue:"+oldMember.getUdn().getIdentifierString()+"#0");
			return oldMember.setCurrentURI(entry);		
			
		} else {
			return false;
		}
	}
	
	public boolean becomeStandAlonePlayer() {
		
		if(isConfigured()) {
		
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("BecomeCoordinatorOfStandaloneGroup");
		ActionInvocation invocation = new ActionInvocation(action);

		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
		
	}
	
	public boolean setMute(String string) {
		if(string != null && isConfigured()) {
			
			Service service = device.findService(new UDAServiceId("RenderingControl"));
			Action action = service.getAction("SetMute");
			ActionInvocation invocation = new ActionInvocation(action);
			
			try {
				invocation.setInput("Channel", "Master");
				
				if(string.equals("ON") || string.equals("OPEN") || string.equals("UP") ) {
					invocation.setInput("DesiredMute", "On");	        		
				} else 
				
				if(string.equals("OFF") || string.equals("CLOSED") || string.equals("DOWN") ) {
					invocation.setInput("DesiredMute", "Off");	        		
				} else {
					return false;
				}
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }
			executeActionInvocation(invocation);
				
			return true;			
			
		} else {
			return false;
		}
	}
	
	public String getMute(){
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("MuteMaster");
			if(value != null) {
				return value.getValue().toString();
			}
		}

			return null;

	}

	public boolean setVolume(String value) {
		if(value != null && isConfigured()) {
			
			Service service = device.findService(new UDAServiceId("RenderingControl"));
			Action action = service.getAction("SetVolume");
			ActionInvocation invocation = new ActionInvocation(action);
			
			try {
				invocation.setInput("Channel", "Master");
				invocation.setInput("DesiredVolume",value);
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }
			
			executeActionInvocation(invocation);
			
			return true;			
			
		} else {
			return false;
		}
	}
	
	public String getVolume() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("VolumeMaster");
			if(value != null) {
			return value.getValue().toString();
			}
		}

			return null;
		
	}
	
	public boolean updateTime() {

		if(isConfigured()) {
		
		Service service = device.findService(new UDAServiceId("AlarmClock"));
		Action action = service.getAction("GetTimeNow");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		return true;
		} else {
			return false;
		}
		
	}
	
	public String getTime() {
		if(isConfigured()) {
			updateTime();
			if(stateMap != null) {
				StateVariableValue value = stateMap.get("CurrentLocalTime");
				if(value != null) {
				return value.getValue().toString();
				}

			}
		}
			return null;
		

	}
	
	protected void executeActionInvocation(ActionInvocation invocation) {
		if(invocation != null) {
			//new SonosActionCallback(invocation, upnpService.getControlPoint()).run();
			new ActionCallback.Default(invocation, upnpService.getControlPoint()).run();
			
			ActionException anException = invocation.getFailure();
			if(anException!= null && anException.getMessage()!=null) {
				logger.warn(anException.getMessage());
			}

			Map<String, ActionArgumentValue> result =  invocation.getOutputMap();
			Map<String, StateVariableValue> mapToProcess = new HashMap<String, StateVariableValue>();
			if(result != null) {

				// only process the variables that have changed value
				for(String variable : result.keySet()) {
					ActionArgumentValue newArgument = result.get(variable);
					
					StateVariable newVariable = new StateVariable(variable,new StateVariableTypeDetails(newArgument.getDatatype()));
					StateVariableValue newValue = new StateVariableValue(newVariable, newArgument.getValue());
					
					//StateVariableValue oldValue = stateMap.get(variable);

					if(isUpdatedValue(variable,newValue)) {
						mapToProcess.put(variable, newValue);
					}
				}
				
				stateMap.putAll(mapToProcess);
				sonosBinding.processVariableMap(device,mapToProcess);
			}
		}
	}

	public boolean updateRunningAlarmProperties() {

		if(stateMap != null && isConfigured()) {

			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("GetRunningAlarmProperties");
			ActionInvocation invocation = new ActionInvocation(action);

			executeActionInvocation(invocation);

			// for this property we would like to "compile" a more friendly variable.
			// this newly created "variable" is also store in the stateMap
			StateVariableValue alarmID  = stateMap.get("AlarmID");
			StateVariableValue groupID  = stateMap.get("GroupID");
			StateVariableValue loggedStartTime  = stateMap.get("LoggedStartTime");
			String newStringValue = null;
			if(alarmID != null && loggedStartTime != null) {
				newStringValue = alarmID.getValue() + " - " + loggedStartTime.getValue();
			} else {
				newStringValue = "No running alarm";
			}

			StateVariable newVariable = new StateVariable("RunningAlarmProperties",new StateVariableTypeDetails(Datatype.Builtin.STRING.getDatatype()));
			StateVariableValue newValue = new StateVariableValue(newVariable, newStringValue);

			processStateVariableValue(newVariable.getName(),newValue);

			return true;
		} else {
			return false;
		}
	}
	
	public String getRunningAlarmProperties() {
		if(isConfigured()) {
			updateRunningAlarmProperties();
			if(stateMap != null) {
				StateVariableValue value = stateMap.get("RunningAlarmProperties");
				if(value != null) {
					return value.getValue().toString();
				}
			}
		}

			return null;

		}
	
	public boolean updateZoneInfo() {
		if(stateMap != null && isConfigured()) {
			Service service = device.findService(new UDAServiceId("DeviceProperties"));
			Action action = service.getAction("GetZoneInfo");
			ActionInvocation invocation = new ActionInvocation(action);

			executeActionInvocation(invocation);

			return true;
		} else {
			return false;
		}
	}

	public String getMACAddress() {
		if(isConfigured()) {
			updateZoneInfo();
			if(stateMap != null) {
				StateVariableValue value = stateMap.get("MACAddress");
				if(value != null) {
				return value.getValue().toString();
				}
			}
		}
				return null;
			
	}


	public boolean setLed(String string) {
    	
		if(string != null && isConfigured()) {
			
			Service service = device.findService(new UDAServiceId("DeviceProperties"));
			Action action = service.getAction("SetLEDState");
			ActionInvocation invocation = new ActionInvocation(action);
			
			try {
				if(string.equals("ON") || string.equals("OPEN") || string.equals("UP") ) {
					invocation.setInput("DesiredLEDState", "On");	        		
				} else
				
				if(string.equals("OFF") || string.equals("CLOSED") || string.equals("DOWN") ) {
					invocation.setInput("DesiredLEDState", "Off");	        		
				} else {
					return false;
				}
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }
			executeActionInvocation(invocation);
				
			return true;			
			
		} else {
			return false;
		}
	}

	public boolean updateLed() {
		
		if(isConfigured()) {
			
		Service service = device.findService(new UDAServiceId("DeviceProperties"));
		Action action = service.getAction("GetLEDState");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
			
		return true;	
		}
		else {
			return false;
		}
	}
	
	public boolean getLed() {
	
		if(isConfigured()) {
	
		updateLed();
		if(stateMap != null) {
			StateVariableValue variable = stateMap.get("CurrentLEDState");
			if(variable != null) {
				return variable.getValue().equals("On") ? true : false;
			}
		} 
		}	

		return false;
	}
	
	public boolean updatePosition() {
		
		if(isConfigured()) {
			
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("GetPositionInfo");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
			
		return true;	
		}
		else {
			return false;
		}
	}
	
	public String getPosition() {
		if(stateMap != null && isConfigured()) {

			updatePosition();
			if(stateMap != null) {
				StateVariableValue variable = stateMap.get("RelTime");
				if(variable != null) {
				return variable.getValue().toString();
				}
			}	
		}
		return null;
	}
	
	public boolean setPosition(String relTime) {
		return seek("REL_TIME",relTime);
	}
	
	public boolean setPositionTrack(long tracknr) {
		return seek("TRACK_NR",Long.toString(tracknr));
	}
	
	protected boolean seek(String unit, String target) {
		if(isConfigured() && unit != null && target != null) {
			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("Seek");
			ActionInvocation invocation = new ActionInvocation(action);
			
			try {
				invocation.setInput("InstanceID","0");
				invocation.setInput("Unit", unit);
				invocation.setInput("Target", target);
			} catch (InvalidValueException ex) {
				logger.error("Action Invalid Value Exception {}",ex.getMessage());
			} catch (NumberFormatException ex) {
				logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
			}

			executeActionInvocation(invocation);

			return true;
		}
		
		return false;
	}
	

	public Boolean isLineInConnected() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue statusLineIn = stateMap.get("LineInConnected");
			if(statusLineIn != null) {
				return (Boolean) statusLineIn.getValue();
			}
		}
		return null;

	}

		public Boolean isAlarmRunning() {
			if(stateMap != null && isConfigured()) {
				StateVariableValue status = stateMap.get("AlarmRunning");
				if(status!=null) {
					return status.getValue().equals("1") ? true : false;
				}
			}
			return null;

		}
	
	public String getTransportState() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue status = stateMap.get("TransportState");
			if(status != null) {
				return status.getValue().toString();
			}
		}

		return null;

	}
	
	public boolean addURIToQueue(String URI, String meta,int desiredFirstTrack, boolean enqueueAsNext) {
		
		if(isConfigured() && URI != null && meta != null) {

			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("AddURIToQueue");
			ActionInvocation invocation = new ActionInvocation(action);

			try {
				invocation.setInput("InstanceID","0");
				invocation.setInput("EnqueuedURI",URI);
				invocation.setInput("EnqueuedURIMetaData",meta);
				invocation.setInput("DesiredFirstTrackNumberEnqueued",new UnsignedIntegerFourBytes(desiredFirstTrack));
				invocation.setInput("EnqueueAsNext",enqueueAsNext);
				
			} catch (InvalidValueException ex) {
				logger.error("Action Invalid Value Exception {}",ex.getMessage());
			} catch (NumberFormatException ex) {
				logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
			}

			executeActionInvocation(invocation);

			return true;
		}
		
		return false;
	}
	
	public String getCurrentURI(){
		updateMediaInfo();
		if(stateMap != null && isConfigured()) {
			StateVariableValue status = stateMap.get("CurrentURI");
			if(status != null) {
				return status.getValue().toString();
			}
		}
		return null;
				
	}
	
	public long getCurrenTrackNr() {
		
		if(stateMap != null && isConfigured()) {

			updatePosition();
			if(stateMap != null) {
				StateVariableValue variable = stateMap.get("Track");
				if(variable != null) {
				return ((UnsignedIntegerFourBytes)variable.getValue()).getValue();
				}
			}	
		}
		return (long) -1;
	}
		
	
	public boolean updateMediaInfo(){
		
		if(isConfigured()) {
		
		Service service = device.findService(new UDAServiceId("AVTransport"));
		Action action = service.getAction("GetMediaInfo");
		ActionInvocation invocation = new ActionInvocation(action);
		
		try {
			invocation.setInput("InstanceID","0");
		} catch (InvalidValueException ex) {
			logger.error("Action Invalid Value Exception {}",ex.getMessage());
		} catch (NumberFormatException ex) {
			logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
		}

		executeActionInvocation(invocation);

		return true;
		}
		
		return false;
		
	}
	
	public boolean setCurrentURI(String URI, String URIMetaData ) {
		if(URI != null && URIMetaData != null && isConfigured()) {

			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("SetAVTransportURI");
			ActionInvocation invocation = new ActionInvocation(action);

			try {
				invocation.setInput("InstanceID","0");
				invocation.setInput("CurrentURI",URI);
				invocation.setInput("CurrentURIMetaData", URIMetaData);
			} catch (InvalidValueException ex) {
				logger.error("Action Invalid Value Exception {}",ex.getMessage());
			} catch (NumberFormatException ex) {
				logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
			}

			executeActionInvocation(invocation);
			
			return true;			

		} else {
			return false;
		}
	}
	
	public boolean setCurrentURI(SonosEntry newEntry) {
		return setCurrentURI(newEntry.getRes(),SonosXMLParser.compileMetadataString(newEntry));
	}

	public boolean updateCurrentURIFormatted() {

		if(stateMap != null && isConfigured()) {
			
			String currentURI = null;
			SonosMetaData currentTrack = null;
			
			if(!isGroupCoordinator()) {
				currentURI = getCoordinator().getCurrentURI();
				currentTrack = getCoordinator().getCurrentURIMetadata();
			} else {
				currentURI = getCurrentURI();
				currentTrack = getCurrentURIMetadata();
			}

			if(currentURI != null) {

				String resultString = null;

				if(currentURI.contains("x-sonosapi-stream")) {
					//TODO: Get partner ID for openhab.org

					String stationID = StringUtils.substringBetween(currentURI, ":s", "?sid");

					StateVariable newVariable = new StateVariable("StationID",new StateVariableTypeDetails(Datatype.Builtin.STRING.getDatatype()));
					StateVariableValue newValue = new StateVariableValue(newVariable, stationID);

					if(this.isUpdatedValue("StationID", newValue) || lastOPMLQuery ==null || lastOPMLQuery.plusMinutes(1).isBeforeNow()) {

						processStateVariableValue(newVariable.getName(),newValue);	

						String url = "http://opml.radiotime.com/Describe.ashx?c=nowplaying&id=" + stationID + "&partnerId=IAeIhU42&serial=" + getMACAddress();

						String response = HttpUtil.executeUrl("GET", url, SO_TIMEOUT);
						//logger.debug("RadioTime Response: {}",response);
						
						lastOPMLQuery = DateTime.now();

						List<String> fields = null;
						try {
							fields = SonosXMLParser.getRadioTimeFromXML(response);
						} catch (SAXException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						if(fields != null) {

						}

						resultString = new String();

						Iterator<String> listIterator = fields.listIterator();
						while(listIterator.hasNext()){
							String field = listIterator.next();
							resultString = resultString + field;
							if(listIterator.hasNext()) {
								resultString = resultString + " - ";
							}
						}
					} else {
						resultString = stateMap.get("CurrentURIFormatted").getValue().toString();
					}
					

				} else {
					if(currentTrack != null) {
					resultString = currentTrack.getAlbumArtist() + " - " + currentTrack.getAlbum() + " - " + currentTrack.getTitle();
					} else {
						resultString = "";
					}
					
				}

				StateVariable newVariable = new StateVariable("CurrentURIFormatted",new StateVariableTypeDetails(Datatype.Builtin.STRING.getDatatype()));
				StateVariableValue newValue = new StateVariableValue(newVariable, resultString);

				processStateVariableValue(newVariable.getName(),newValue);		

				return true;

				
			}
		}
		
		return false;
	}
	
	public String getCurrentURIFormatted(){
		updateCurrentURIFormatted();
		if(stateMap != null && isConfigured()) {
			StateVariableValue status = stateMap.get("CurrentURIFormatted");
			if(status != null) {
			return status.getValue().toString();
			}
		}

			return null;
			
	}
	
	public String getCurrentURIMetadataAsString() {
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("CurrentTrackMetaData");
			if(value != null) {
			return value.getValue().toString();
			}
		}
				return null;
			
	}
	

	public SonosMetaData getCurrentURIMetadata(){
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("CurrentURIMetaData");
			SonosMetaData currentTrack = null;
			if(value != null) {
			try {
				if(((String)value.getValue()).length()!=0) {
					currentTrack = SonosXMLParser.getMetaDataFromXML((String)value.getValue());
				}
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return currentTrack;
			} else {
				return null;
			}
		} else {
			return null;
		}		
	}
	
	public SonosMetaData getTrackMetadata(){
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("CurrentTrackMetaData");
			SonosMetaData currentTrack = null;
			if(value != null) {
			try {
				if(((String)value.getValue()).length()!=0) {
					currentTrack = SonosXMLParser.getMetaDataFromXML((String)value.getValue());
				}
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return currentTrack;
			} else {
				return null;
			}
		} else {
			return null;
		}		
	}
	
	public SonosMetaData getEnqueuedTransportURIMetaData(){
		if(stateMap != null && isConfigured()) {
			StateVariableValue value = stateMap.get("EnqueuedTransportURIMetaData");
			SonosMetaData currentTrack = null;
			if(value != null) {
			try {
				if(((String)value.getValue()).length()!=0) {
					currentTrack = SonosXMLParser.getMetaDataFromXML((String)value.getValue());
				}
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return currentTrack;
			} else {
				return null;
			}
		} else {
			return null;
		}		
	}
	
	
	public String getCurrentVolume(){
		if(stateMap != null && isConfigured()) {
			StateVariableValue status = stateMap.get("VolumeMaster");
			return status.getValue().toString();
		} else {
			return null;
		}		
	}
	
	protected List<SonosEntry> getEntries(String type, String filter){

		List<SonosEntry> resultList = null;

		
		if(isConfigured()) {
		
		long startAt = 0;

		Service service = device.findService(new UDAServiceId("ContentDirectory"));
		Action action = service.getAction("Browse");
		ActionInvocation invocation = new ActionInvocation(action);
		try {
			invocation.setInput("ObjectID",type);
			invocation.setInput("BrowseFlag","BrowseDirectChildren");
			invocation.setInput("Filter", filter);
			invocation.setInput("StartingIndex",new UnsignedIntegerFourBytes(startAt));
			invocation.setInput("RequestedCount",new UnsignedIntegerFourBytes( 200));
			invocation.setInput("SortCriteria","");
        } catch (InvalidValueException ex) {
            logger.error("Action Invalid Value Exception {}",ex.getMessage());
        } catch (NumberFormatException ex) {
            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
        }
		// Execute this action synchronously 
		new ActionCallback.Default(invocation, upnpService.getControlPoint()).run();

		Long totalMatches  = ((UnsignedIntegerFourBytes) invocation.getOutput("TotalMatches").getValue()).getValue();
		Long initialNumberReturned  = ((UnsignedIntegerFourBytes) invocation.getOutput("NumberReturned").getValue()).getValue();
		String initialResult = (String) invocation.getOutput("Result").getValue();
		
		//logger.debug("Browse Result = {}",initialResult);

		try {
			resultList = SonosXMLParser.getEntriesFromString(initialResult);
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		startAt = startAt + initialNumberReturned;

		while(startAt<totalMatches){
			invocation = new ActionInvocation(action);
			try {
				invocation.setInput("ObjectID",type);
				invocation.setInput("BrowseFlag","BrowseDirectChildren");
				invocation.setInput("Filter", filter);
				invocation.setInput("StartingIndex",new UnsignedIntegerFourBytes(startAt));
				invocation.setInput("RequestedCount",new UnsignedIntegerFourBytes( 200));
				invocation.setInput("SortCriteria","");
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }
			// Execute this action synchronously 
			new ActionCallback.Default(invocation, upnpService.getControlPoint()).run();
			String result = (String) invocation.getOutput("Result").getValue();
			int numberReturned  = (Integer) invocation.getOutput("NumberReturned").getValue();

			try {
				resultList.addAll(SonosXMLParser.getEntriesFromString(result));
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			startAt = startAt + numberReturned;
		}
		}

		return resultList;

		
	}
	
	public List<SonosEntry> getArtists( String filter){
		return getEntries("A:",filter);
	}

	public List<SonosEntry> getArtists(){
		return getEntries("A:","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosEntry> getAlbums(String filter){
		return getEntries("A:ALBUM",filter);
	}

	public List<SonosEntry> getAlbums(){
		return getEntries("A:ALBUM","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosEntry> getTracks( String filter){
		return getEntries("A:TRACKS",filter);
	}

	public List<SonosEntry> getTracks(){
		return getEntries("A:TRACKS","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosEntry> getQueue(String filter){
		return getEntries("Q:0",filter);
	}

	public List<SonosEntry> getQueue(){
		return getEntries("Q:0","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosEntry> getPlayLists(String filter){
		return getEntries("SQ:",filter);
	}
	
	public List<SonosEntry> getPlayLists(){
		return getEntries("SQ:","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosEntry> getFavoriteRadios(String filter){
		return getEntries("R:0/0",filter);
	}

	public List<SonosEntry> getFavoriteRadios(){
		return getEntries("R:0/0","dc:title,res,dc:creator,upnp:artist,upnp:album");
	}
	
	public List<SonosAlarm> getCurrentAlarmList(){
		
		
		List<SonosAlarm> sonosAlarms = null;
		
		if(isConfigured()) {
		
		Service service = device.findService(new UDAServiceId("AlarmClock"));
		Action action = service.getAction("ListAlarms");
		ActionInvocation invocation = new ActionInvocation(action);
		
		executeActionInvocation(invocation);
		
		try {
			sonosAlarms = SonosXMLParser.getAlarmsFromStringResult(invocation.getOutput("CurrentAlarmList").toString());
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
		
		return sonosAlarms;
	}
	
	public boolean updateAlarm(SonosAlarm alarm) {
		if(alarm != null && isConfigured()) {
			Service service = device.findService(new UDAServiceId("AlarmClock"));
			Action action = service.getAction("ListAlarms");
			ActionInvocation invocation = new ActionInvocation(action);


			DateTimeFormatter formatter = DateTimeFormat.forPattern("HH:mm:ss");

			PeriodFormatter pFormatter= new PeriodFormatterBuilder()
			.printZeroAlways()
			.appendHours()
			.appendSeparator(":")
			.appendMinutes()
			.appendSeparator(":")
			.appendSeconds()
			.toFormatter();


			try {
				invocation.setInput("ID",Integer.toString(alarm.getID()));
				invocation.setInput("StartLocalTime",formatter.print(alarm.getStartTime()));
				invocation.setInput("Duration",pFormatter.print(alarm.getDuration()));
				invocation.setInput("Recurrence",alarm.getRecurrence());
				invocation.setInput("RoomUUID",alarm.getRoomUUID());
				invocation.setInput("ProgramURI",alarm.getProgramURI());
				invocation.setInput("ProgramMetaData",alarm.getProgramMetaData());
				invocation.setInput("PlayMode",alarm.getPlayMode());
				invocation.setInput("Volume",Integer.toString(alarm.getVolume()));
				if(alarm.getIncludeLinkedZones()) {
					invocation.setInput("IncludeLinkedZones","1");
				} else {
					invocation.setInput("IncludeLinkedZones","0");
				}

				if(alarm.getEnabled()) {
					invocation.setInput("Enabled", "1");	        		
				} else {
					invocation.setInput("Enabled", "0");	        		
				}
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }

			executeActionInvocation(invocation);

			return true;
		}
		else {
			return false;
		}
		
	}
	
	public boolean setAlarm(String alarmSwitch) {
		if(alarmSwitch.equals("ON") || alarmSwitch.equals("OPEN") || alarmSwitch.equals("UP") ) {
			return setAlarm(true);	        		
		} else 
		
		if(alarmSwitch.equals("OFF") || alarmSwitch.equals("CLOSED") || alarmSwitch.equals("DOWN") ) {
			return setAlarm(false);        		
		} else {
			return false;
		}
	}
	
	public boolean setAlarm(boolean alarmSwitch) {
		
		List<SonosAlarm> sonosAlarms = getCurrentAlarmList();
		
		if(isConfigured()) {
		
		// find the nearest alarm - take the current time from the Sonos System, not the system where openhab is running

		String currentLocalTime = getTime();
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

		DateTime currentDateTime = fmt.parseDateTime(currentLocalTime);

		Duration shortestDuration = Period.days(10).toStandardDuration();
		SonosAlarm firstAlarm = null;

		for(SonosAlarm anAlarm : sonosAlarms) {
			Duration duration = new Duration(currentDateTime,anAlarm.getStartTime());
			if(anAlarm.getStartTime().isBefore(currentDateTime.plus(shortestDuration)) && anAlarm.getRoomUUID().equals(udn.getIdentifierString())) {
				shortestDuration = duration;
				firstAlarm = anAlarm;
			}
		}

		// Set the Alarm
		if(firstAlarm != null) {

			if(alarmSwitch) {
				firstAlarm.setEnabled(true);
			} else {
				firstAlarm.setEnabled(false);
			}
			
			return updateAlarm(firstAlarm);

		} else {
			return false;
		}
		} else {
			return false;
		}
			
		
	}
	
	public boolean snoozeAlarm(int minutes){
		if(isAlarmRunning() && isConfigured()) {

			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("SnoozeAlarm");
			ActionInvocation invocation = new ActionInvocation(action);

			Period snoozePeriod = Period.minutes(minutes);
			PeriodFormatter pFormatter= new PeriodFormatterBuilder()
			.printZeroAlways()
			.appendHours()
			.appendSeparator(":")
			.appendMinutes()
			.appendSeparator(":")
			.appendSeconds()
			.toFormatter();

			try {
				invocation.setInput("Duration",pFormatter.print(snoozePeriod));
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }

			executeActionInvocation(invocation);

			return true;

		} else {
			logger.warn("There is no alarm running on {} ",this);
			return false;
		}
	}
	
	public boolean publicAddress(){
		
		//check if sourcePlayer has a line-in connected
		if(isLineInConnected() && isConfigured()) {

			//first remove this player from its own group if any
			becomeStandAlonePlayer();
			
			List<SonosZoneGroup> currentSonosZoneGroups = new ArrayList<SonosZoneGroup>(sonosBinding.getSonosZoneGroups().size());
			for(SonosZoneGroup grp : sonosBinding.getSonosZoneGroups()){
				currentSonosZoneGroups.add((SonosZoneGroup) grp.clone());
			}

			//add all other players to this new group
			for(SonosZoneGroup group : currentSonosZoneGroups){
				for(String player : group.getMembers()){
					SonosZonePlayer somePlayer = sonosBinding.getPlayerForID(player);
					if(somePlayer != this){
						somePlayer.becomeStandAlonePlayer();
						somePlayer.stop();
						addMember(somePlayer);
					}
				}
			}
			

			//set the URI of the group to the line-in
			//TODO : check if this needs to be set on the group coordinator or can be done on any member
			SonosZonePlayer coordinator = getCoordinator();
        	SonosEntry entry = new SonosEntry("", "", "", "", "", "", "", "x-rincon-stream:"+udn.getIdentifierString());
        	coordinator.setCurrentURI(entry);
        	coordinator.play();

			return true;
				
		} else {
			logger.warn("Line-in of {} is not connected",this);
			return false;
		}
		
	}
	
	public boolean saveQueue(String name, String queueID) {
		
		if(name != null && queueID != null && isConfigured()) {
			
			Service service = device.findService(new UDAServiceId("AVTransport"));
			Action action = service.getAction("SaveQueue");
			ActionInvocation invocation = new ActionInvocation(action);
			
			try {
				invocation.setInput("Title", name);	        		
				invocation.setInput("ObjectID", queueID);	        		
				
	        } catch (InvalidValueException ex) {
	            logger.error("Action Invalid Value Exception {}",ex.getMessage());
	        } catch (NumberFormatException ex) {
	            logger.error("Action Invalid Value Format Exception {}",ex.getMessage());	
	        }
			executeActionInvocation(invocation);
				
			return true;			
			
		} else {
			return false;
		}
		
	}
	
}