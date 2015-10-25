package icn.core;

import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

public class IcnEngine {

	public static void handleTcp(IOFSwitch sw, OFMessage msg, Ethernet eth,
			IPv4 ipv4, TCP tcp) {
		String payload = new String(((Data) tcp.getPayload()).serialize());
		IcnModule.logger.info(payload);
		if (payload.contains("HTTP") && payload.contains("GET")) { // HTTP GET =
																	// ContentRequest

			String source = getContentSource(Utils.getContentId(payload));
			OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, source);

		} else if (tcp.getFlags() == 2 // If TCP SYN to Virtual IP is received
										// on port 80
				&& ipv4.getDestinationAddress().equals(IcnModule.VIP)
				&& tcp.getDestinationPort().equals(TransportPort.of(80))) {
			OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
		}

	}

	private static String getContentSource(String contentId) {
		
		// HERE ICN ENGINE LOGIC
		if (contentId.equals("abc123"))
			return "10.0.0.2";

		return null;
	}


}
