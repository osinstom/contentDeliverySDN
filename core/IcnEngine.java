package icn.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

public class IcnEngine extends IcnForwarding {

	private HashMap<String, HashMap<IPv4Address, Integer>> contentRequestsStats = null;

	public static IcnEngine instance = null;

	private final Match.Builder icnFlowMatchBuilder = OFFactories
			.getFactory(OFVersion.OF_13).buildMatch()
			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IP_PROTO, IpProtocol.TCP);

	public static IcnEngine getInstance() {
		if (instance == null)
			instance = new IcnEngine();

		return instance;
	}

	public IcnEngine() {
		contentRequestsStats = new HashMap<String, HashMap<IPv4Address, Integer>>();
	}

	public void handleTcp(IOFSwitch sw, OFMessage msg, Ethernet eth, IPv4 ipv4,
			TCP tcp) {
		String payload = new String(((Data) tcp.getPayload()).serialize());
		IcnModule.logger.info(payload);
		if (payload.contains("HTTP") && payload.contains("GET")) { // HTTP GET =
																	// ContentRequest
			String contentId = Utils.getContentId(payload);
			String contentSource = getContentSource(contentId);
			String srcIp = ipv4.getSourceAddress().toString();

			if (contentSource != null)
				updateContentStats(contentId, IPv4Address.of(srcIp));

			OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, srcIp,
					contentSource);

		} else if (tcp.getFlags() == 2 // If TCP SYN to Virtual IP is received
										// on port 80
				&& ipv4.getDestinationAddress().equals(IcnModule.VIP)
				&& tcp.getDestinationPort().equals(TransportPort.of(80))) {
			OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
		}

	}

	private void updateContentStats(String contentId, IPv4Address srcIp) {

		if (!contentRequestsStats.containsKey(contentId)) {
			HashMap<IPv4Address, Integer> inner = new HashMap<IPv4Address, Integer>();
			inner.put(srcIp, 1);
			contentRequestsStats.put(contentId, inner);
		} else if (contentRequestsStats.containsKey(contentId)
				&& !contentRequestsStats.get(contentId).containsKey(
						srcIp)) {
			contentRequestsStats.get(contentId).put(srcIp, 1);
		} else {
			contentRequestsStats.get(contentId).put(srcIp,
					contentRequestsStats.get(contentId).get(srcIp) + 1);
		}

		IcnModule.logger.info(contentRequestsStats.toString());
		IcnModule.logger.info("CONTENT POPULARITY STATS: \nContent ["
				+ contentId + "] was requested "
				+ contentRequestsStats.get(contentId).get(srcIp)
				+ " times from " + srcIp);

	}

	private String getContentSource(String contentId) {

		// HERE ICN ENGINE LOGIC
		if (contentId.equals("abc123"))
			return "10.0.0.2/Files/abc123.txt";
		else if(contentId.equals("index"))
			return "10.0.0.2/Files/index.html";
		
		return null;
	}

	public void prepareRoute(String srcIp, String dstIp, TransportPort srcPort,
			TransportPort dstPort) {

		IDevice srcDevice = null;
		IDevice dstDevice = null;

		for (IDevice device : deviceService.getAllDevices()) {
			if (device.getIPv4Addresses()[0].equals(IPv4Address.of(srcIp)))
				srcDevice = device;
			else if (device.getIPv4Addresses()[0].equals(IPv4Address.of(dstIp)))
				dstDevice = device;
		}

		DatapathId srcSwId = srcDevice.getAttachmentPoints()[0].getSwitchDPID();
		DatapathId dstSwId = dstDevice.getAttachmentPoints()[0].getSwitchDPID();
		if (!srcSwId.equals(dstSwId)) {
			prepareRoute(srcSwId, dstSwId);
		} else {
			prepareRoute(srcDevice, dstDevice);
		}
	}

	private void prepareRoute(DatapathId srcSwId, DatapathId dstSwId) {

		Route route = routingService.getRoute(srcSwId, dstSwId, null);
		
		
	}

	private void prepareRoute(IDevice srcDevice, IDevice dstDevice) {

		Route forwardRoute = new Route(
				srcDevice.getAttachmentPoints()[0].getSwitchDPID(),
				dstDevice.getAttachmentPoints()[0].getSwitchDPID());
		List<NodePortTuple> path = new ArrayList<NodePortTuple>(2);
		path.add(new NodePortTuple(srcDevice.getAttachmentPoints()[0]
				.getSwitchDPID(), srcDevice.getAttachmentPoints()[0].getPort()));
		path.add(new NodePortTuple(dstDevice.getAttachmentPoints()[0]
				.getSwitchDPID(), dstDevice.getAttachmentPoints()[0].getPort()));
		forwardRoute.setPath(path);

		Route reverseRoute = new Route(
				dstDevice.getAttachmentPoints()[0].getSwitchDPID(),
				srcDevice.getAttachmentPoints()[0].getSwitchDPID());
		List<NodePortTuple> reversePath = new ArrayList<NodePortTuple>(2);
		reversePath
				.add(new NodePortTuple(dstDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), dstDevice.getAttachmentPoints()[0]
						.getPort()));
		reversePath
				.add(new NodePortTuple(srcDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), srcDevice.getAttachmentPoints()[0]
						.getPort()));
		reverseRoute.setPath(reversePath);

		IcnModule.logger.info("DPID"
				+ srcDevice.getAttachmentPoints()[0].getSwitchDPID());
		IOFSwitch sw = switchService.getActiveSwitch(srcDevice
				.getAttachmentPoints()[0].getSwitchDPID());

		Match.Builder forwardMatch = icnFlowMatchBuilder
				.setExact(MatchField.IN_PORT,
						srcDevice.getAttachmentPoints()[0].getPort())
				.setExact(MatchField.IPV4_SRC, srcDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.TCP_DST, TransportPort.of(80));

		Match.Builder reverseMatch = OFFactories
				.getFactory(OFVersion.OF_13)
				.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IN_PORT,
						dstDevice.getAttachmentPoints()[0].getPort())
				.setExact(MatchField.IPV4_SRC, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, srcDevice.getIPv4Addresses()[0]);

		U64 cookie = AppCookie.makeCookie(2, 0);

		pushRoute(forwardRoute, forwardMatch.build(), sw.getId(), cookie,
				OFFlowModCommand.ADD);
		pushRoute(reverseRoute, reverseMatch.build(), sw.getId(), cookie,
				OFFlowModCommand.ADD);

	}

}
