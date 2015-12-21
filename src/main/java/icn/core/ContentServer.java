package icn.core;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

public class ContentServer {
	
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
	
	

}
