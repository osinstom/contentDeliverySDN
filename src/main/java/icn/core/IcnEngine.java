package icn.core;

import icn.core.ContentDesc.Location;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.multipathrouting.types.MultiRoute;
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
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import com.fasterxml.jackson.dataformat.yaml.snakeyaml.nodes.NodeTuple;

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

		if (ipv4.getDestinationAddress().equals(IcnModule.VIP)) {
			if (payload.contains("HTTP") && payload.contains("GET")) { // HTTP
																		// GET =
																		// ContentRequest
				String contentId = Utils.getContentId(payload);
				String contentSourceUrl = getContentSource(contentId);
				String srcIp = ipv4.getSourceAddress().toString();

				if (contentSourceUrl != null) {

					int flowId = getFlowId();
					contentSourceUrl = contentSourceUrl.replace("$flowId$",
							Integer.toString(flowId));
					MonitoringSystem.flows.add(new ContentFlow(flowId));
					prepareRoute(
							srcIp,
							contentSourceUrl.substring(0,
									contentSourceUrl.indexOf(":")),
							TransportPort.of(flowId));

					OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, srcIp,
							contentSourceUrl);
				} else
					OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
							OFUtils.HTTP_NOTFOUND);

			} else if (tcp.getFlags() == 2 // If TCP SYN to Virtual IP is
											// received
											// on port 80
					&& ipv4.getDestinationAddress().equals(IcnModule.VIP)
					&& tcp.getDestinationPort().equals(TransportPort.of(80))) {
				OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
			}
		} else {
			IcnModule.logger.info("Route to dest request: "
					+ tcp.getSourcePort() + " " + tcp.getDestinationPort());
			if (MonitoringSystem.getInstance().getFlowIds()
					.contains(tcp.getDestinationPort().getPort())) {

				IcnModule.logger.info("Route to dest request: "
						+ tcp.getSourcePort() + " " + tcp.getDestinationPort());

				OFUtils.setNatFlow(sw, msg, ipv4.getSourceAddress(),
						ipv4.getDestinationAddress(), tcp.getSourcePort(),
						tcp.getDestinationPort());

			} else {
				OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
						OFUtils.HTTP_BADREQUEST);
			}

		}

	}

	private Integer getFlowId() {

		int flowId = 0;
		do {

			Random rn = new Random();
			int range = 65535 - 49152 + 1;
			flowId = rn.nextInt(range) + 49152;
			IcnModule.logger.info("FlowId: " + flowId);
		} while (flowId == 0
				&& MonitoringSystem.getInstance().getFlowIds().contains(flowId));
		return flowId;
	}

	private void updateContentStats(String contentId, IPv4Address srcIp) {

		if (!contentRequestsStats.containsKey(contentId)) {
			HashMap<IPv4Address, Integer> inner = new HashMap<IPv4Address, Integer>();
			inner.put(srcIp, 1);
			contentRequestsStats.put(contentId, inner);
		} else if (contentRequestsStats.containsKey(contentId)
				&& !contentRequestsStats.get(contentId).containsKey(srcIp)) {
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

		ContentDesc contentDesc = Utils.getContentDesc(contentId);
		IcnModule.logger.info(contentDesc.toString());
		Location bestSource = null;
		if (contentDesc != null)
			bestSource = calculateBestSource(contentDesc.getLocations());
		else
			return null;

		return bestSource.getIpAddr() + ":$flowId$/"
				+ bestSource.getLocalPath();

	}

	private Location calculateBestSource(List<Location> locations) {

		if (locations.size() == 1)
			return locations.get(0);
		else {

			// calculating best source from all locations

		}

		return null;

	}

	public void prepareRoute(String srcIp, String dstIp, TransportPort srcPort) {

		IDevice srcDevice = null;
		IDevice dstDevice = null;

		for (IDevice device : deviceService.getAllDevices()) {
			IcnModule.logger
					.info("Device MAC: " + device.getMACAddressString());
			IcnModule.logger.info("Device: \n" + device.toString());
			if (device.getIPv4Addresses().length != 0
					&& device.getIPv4Addresses()[0] != null) {
				if (device.getIPv4Addresses()[0].equals(IPv4Address.of(srcIp)))
					srcDevice = device;
				else if (device.getIPv4Addresses()[0].equals(IPv4Address
						.of(dstIp)))
					dstDevice = device;
			}
		}
		IcnModule.logger.info("SRC DEVICE: " + srcDevice.toString());
		IcnModule.logger.info("DST DEVICE: " + dstDevice.toString());

		prepareRoute(srcDevice, dstDevice, srcPort);

	}

	private void prepareRouteX(DatapathId srcSwId, DatapathId dstSwId) {

		MultiRoute multiRoute = mpathRoutingService.getMultiRoute(srcSwId,
				dstSwId);
		for (Route r : multiRoute.getRoutes())
			IcnModule.logger.info("Route: " + r.toString());

		// multiRoute.getRoute()
	}

	private void prepareRoute(IDevice srcDevice, IDevice dstDevice,
			TransportPort srcPort) {

		DatapathId srcSwId = srcDevice.getAttachmentPoints()[0].getSwitchDPID();
		DatapathId dstSwId = dstDevice.getAttachmentPoints()[0].getSwitchDPID();

		// Route route = mpathRoutingService.getMultiRoute(srcSwId, dstSwId)
		// .getRoute();

		Route route = routingService.getRoute(srcSwId,
				srcDevice.getAttachmentPoints()[0].getPort(), dstSwId,
				dstDevice.getAttachmentPoints()[0].getPort(), null);

//		IcnModule.logger.info("Routes:\n"
//				+ mpathRoutingService.getMultiRoute(srcSwId, dstSwId)
//						.getRoutes());

		if (route == null) {
			route = new Route(
					srcDevice.getAttachmentPoints()[0].getSwitchDPID(),
					dstDevice.getAttachmentPoints()[0].getSwitchDPID());
		}

		//route = addClientAPs(route, srcDevice, dstDevice);

		for (ContentFlow flow : MonitoringSystem.flows) {
			if (flow.getFlowId() == srcPort.getPort()) {
				IcnModule.logger.info("Adding route " + route);
				ArrayList<NodePortTuple> path = new ArrayList<NodePortTuple>();
				path.addAll(route.getPath());
				flow.setRoute(path);
			}
		}

		Match.Builder forwardMatch = OFFactories
				.getFactory(OFVersion.OF_13)
				.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IN_PORT,
						srcDevice.getAttachmentPoints()[0].getPort())
				.setExact(MatchField.IPV4_SRC, srcDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.TCP_SRC, srcPort)
				.setExact(MatchField.TCP_DST, TransportPort.of(80));

		Match.Builder reverseMatch = OFFactories
				.getFactory(OFVersion.OF_13)
				.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IN_PORT,
						dstDevice.getAttachmentPoints()[0].getPort())
				.setExact(MatchField.IPV4_SRC, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, srcDevice.getIPv4Addresses()[0])
				.setExact(MatchField.TCP_DST, srcPort)
				.setExact(MatchField.TCP_SRC, TransportPort.of(80));

		U64 cookie = AppCookie.makeCookie(2, 0);

		IcnModule.logger.info("Pushing route: " + route.toString());
		
		pushRoute(route, forwardMatch.build(), cookie, OFFlowModCommand.ADD,
				srcSwId);

		// packets return via the same route
		Collections.reverse(route.getPath());
		
		IcnModule.logger.info("Pushing route: " + route.toString());
		pushRoute(route, reverseMatch.build(), cookie, OFFlowModCommand.ADD,
				srcSwId);

	}

}
