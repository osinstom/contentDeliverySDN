package icn.core;

import java.util.Date;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;

public class ContentServer implements IDevice {
	
	private String name;
	private MacAddress macAddress;
	private IPv4Address ipAddr;
	private DatapathId dpId;
	private OFPort switchPort;

	public ContentServer(String name, MacAddress macAddress,
			IPv4Address ipAddr, DatapathId dpId, OFPort switchPort) {
		super();
		this.name = name;
		this.macAddress = macAddress;
		this.ipAddr = ipAddr;
		this.dpId = dpId;
		this.switchPort = switchPort;
	}
	
	public String getName() {
		return name;
	}

	public MacAddress getMacAddress() {
		return macAddress;
	}

	public IPv4Address getIpAddr() {
		return ipAddr;
	}

	public DatapathId getDpId() {
		return dpId;
	}

	public OFPort getSwitchPort() {
		return switchPort;
	}

	@Override
	public Long getDeviceKey() {
		return null;
	}

	@Override
	public MacAddress getMACAddress() {
		return macAddress;
	}

	@Override
	public String getMACAddressString() {
		return macAddress.toString();
	}

	@Override
	public VlanVid[] getVlanId() {
		return null;
	}

	@Override
	public IPv4Address[] getIPv4Addresses() {
		IPv4Address[] ips = { ipAddr };
		return ips;
	}

	@Override
	public IPv6Address[] getIPv6Addresses() {
		return null;
	}

	@Override
	public SwitchPort[] getAttachmentPoints() {
		SwitchPort[] aps = { new SwitchPort(dpId, switchPort) };
		return aps;
	}

	@Override
	public SwitchPort[] getOldAP() {
		return null;
	}

	@Override
	public SwitchPort[] getAttachmentPoints(boolean includeError) {
		return null;
	}

	@Override
	public VlanVid[] getSwitchPortVlanIds(SwitchPort swp) {
		return null;
	}

	@Override
	public Date getLastSeen() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IEntityClass getEntityClass() {
		// TODO Auto-generated method stub
		return null;
	}
	
	

}
