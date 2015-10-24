package icn.core;

import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

public class IcnUtils {

	public static void initIcnFlows(Set<IOFSwitch> switches) {

		IcnModule.logger.info("Init..... List size=" + switches.size());
		for (IOFSwitch sw : switches)
			OFUtils.insertHTTPDpiFlow(sw);

	}

	public static void handleTcp(IOFSwitch sw, OFMessage msg, Ethernet eth,
			IPv4 ipv4, TCP tcp) {

		if (new String(((Data) tcp.getPayload()).serialize()).contains("HTTP")) {
			
			OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, "10.0.0.2");
		} else if (tcp.getFlags() == 2                                              // If TCP SYN to Virtual IP is received on port 80
				&& ipv4.getDestinationAddress().equals(IcnModule.VIP)
				&& tcp.getDestinationPort().equals(TransportPort.of(80))) {
			OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
		}

	}

}
