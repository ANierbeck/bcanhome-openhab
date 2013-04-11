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
package org.openhab.binding.homematic.internal.xmlrpc.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openhab.binding.homematic.internal.xmlrpc.AbstractXmlRpcObject;

/**
 * A DeviceDescription object describes any available device - physical and
 * logical (channel). Not every device specifies each attribute, so depending on
 * the device there can be null values.
 * 
 * @author Mathias Ewald
 * 
 */
public class DeviceDescription extends AbstractXmlRpcObject {

    public enum Direction {
        NONE, SENDER, RECEIVER
    }

    public enum Flag {
        VISIBLE, INTERNAL, DONTDELETE
    }

    private String type;
    private String address;
    private String[] paramsets;
    private Integer version;
    private Set<Flag> flags;
    private String[] children;
    private String parent;
    private String parentType;
    private Integer index;
    private Boolean aesActive;
    private String firmware;
    private String availableFirmware;
    private String linkSourceRoles;
    private String linkTargetRoles;
    private Direction direction;
    private String group;
    private String team;
    private String teamTag;
    private String[] teamChannels;
    private String interfaceName;
    private Boolean roaming;

    public DeviceDescription(Map<String, Object> values) {
        super(values);

        type = values.get("TYPE").toString();

        address = values.get("ADDRESS").toString();

        children = null;
        Object childrenObj = values.get("CHILDREN");
        if (childrenObj != null) {
            Object[] childrenArray = (Object[]) childrenObj;
            children = Arrays.copyOf(childrenArray, childrenArray.length, String[].class);
        }

        parent = null;
        Object parentObj = values.get("PARENT");
        if (parentObj != null) {
            parent = parentObj.toString();
        }

        parentType = null;
        Object parentTypeObj = values.get("PARENT_TYPE");
        if (parentTypeObj != null) {
            parentType = parentTypeObj.toString();
        }

        index = null;
        Object indexObj = values.get("INDEX");
        if (indexObj != null) {
            index = Integer.parseInt(indexObj.toString());
        }

        aesActive = null;
        Object aesActiveObj = values.get("AES_ACTIVE");
        if (aesActiveObj != null) {
            aesActive = Boolean.parseBoolean(aesActiveObj.toString());
        }

        Object[] paramsetsArray = (Object[]) values.get("PARAMSETS");
        paramsets = Arrays.copyOf(paramsetsArray, paramsetsArray.length, String[].class);

        firmware = null;
        Object firmwareObj = values.get("FIRMWARE");
        if (firmwareObj != null) {
            firmware = firmwareObj.toString();
        }

        version = Integer.parseInt(values.get("VERSION").toString());

        availableFirmware = null;
        Object availFwObj = values.get("AVAILABLE_FIRMWARE");
        if (availFwObj != null) {
            availableFirmware = availFwObj.toString();
        }

        Integer flagsVal = Integer.parseInt(values.get("FLAGS").toString());
        flags = new HashSet<DeviceDescription.Flag>();
        if ((flagsVal & 1) == 1) {
            flags.add(DeviceDescription.Flag.VISIBLE);
        }
        if ((flagsVal & 2) == 2) {
            flags.add(DeviceDescription.Flag.INTERNAL);
        }
        if ((flagsVal & 4) == 4) {
            flags.add(DeviceDescription.Flag.DONTDELETE);
        }

        linkSourceRoles = null;
        Object linkSRObj = values.get("LINK_SOURCE_ROLES");
        if (linkSRObj != null) {
            linkSourceRoles = linkSRObj.toString();
        }

        linkTargetRoles = null;
        Object linkTRObj = values.get("LINK_TARGET_ROLES");
        if (linkTRObj != null) {
            linkTargetRoles = linkTRObj.toString();
        }

        direction = DeviceDescription.Direction.NONE;
        Object dirObj = values.get("DIRECTION");
        if (dirObj != null) {
            Integer directionVal = Integer.parseInt(dirObj.toString());
            if (directionVal == 2) {
                direction = DeviceDescription.Direction.SENDER;
            }
            if (directionVal == 3) {
                direction = DeviceDescription.Direction.RECEIVER;
            }
        }

        group = null;
        Object grpObj = values.get("GROUP");
        if (grpObj != null) {
            group = grpObj.toString();
        }

        team = null;
        Object teamObj = values.get("TEAM");
        if (teamObj != null) {
            team = teamObj.toString();
        }

        teamTag = null;
        Object teamTagObj = values.get("TEAM_TAG");
        if (teamTagObj != null) {
            teamTag = teamTagObj.toString();
        }

        teamChannels = null;
        Object teamChObj = values.get("TEAM_CHANNELS");
        if (teamChObj != null) {
            Object[] teamChannelsArray = (Object[]) values.get("TEAM_CHANNELS");
            teamChannels = Arrays.copyOf(teamChannelsArray, teamChannelsArray.length, String[].class);
        }

        interfaceName = null;
        Object ifaceNameObj = values.get("INTERFACE");
        if (ifaceNameObj != null) {
            interfaceName = ifaceNameObj.toString();
        }

        roaming = null;
        Object roamObj = values.get("ROAMING");
        if (roamObj != null) {
            roaming = Boolean.parseBoolean(roamObj.toString());
        }
    }

    public String getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public String[] getParamsets() {
        return paramsets;
    }

    public Integer getVersion() {
        return version;
    }

    public Set<Flag> getFlags() {
        return flags;
    }

    public String[] getChildren() {
        return children;
    }

    public String getParent() {
        return parent;
    }

    public String getParentType() {
        return parentType;
    }

    public Integer getIndex() {
        return index;
    }

    public Boolean getAesActive() {
        return aesActive;
    }

    public String getFirmware() {
        return firmware;
    }

    public String getAvailableFirmware() {
        return availableFirmware;
    }

    public String getLinkSourceRoles() {
        return linkSourceRoles;
    }

    public String getLinkTargetRoles() {
        return linkTargetRoles;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getGroup() {
        return group;
    }

    public String getTeam() {
        return team;
    }

    public String getTeamTag() {
        return teamTag;
    }

    public String[] getTeamChannels() {
        return teamChannels;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public Boolean getRoaming() {
        return roaming;
    }

    @Override
    public String toString() {
        return "DeviceDescription [address=" + address + ", aesActive=" + aesActive + ", availableFirmware=" + availableFirmware
                + ", children=" + Arrays.toString(children) + ", direction=" + direction + ", firmware=" + firmware + ", flags=" + flags
                + ", group=" + group + ", index=" + index + ", interfaceName=" + interfaceName + ", linkSourceRoles=" + linkSourceRoles
                + ", linkTargetRoles=" + linkTargetRoles + ", paramsets=" + Arrays.toString(paramsets) + ", parent=" + parent
                + ", parentType=" + parentType + ", roaming=" + roaming + ", team=" + team + ", teamChannels="
                + Arrays.toString(teamChannels) + ", teamTag=" + teamTag + ", type=" + type + ", version=" + version + "]";
    }

}
