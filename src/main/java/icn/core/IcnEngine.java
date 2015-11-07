package icn.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
			String contentSourceUrl = getContentSource(contentId);
			String srcIp = ipv4.getSourceAddress().toString();

			if (contentSourceUrl != null) {
				updateContentStats(contentId, IPv4Address.of(srcIp));
				OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, srcIp,
						contentSourceUrl);
			} else
				OFUtils.returnHttp404(sw, msg, ipv4, eth, tcp, srcIp);

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

		// HERE ICN ENGINE LOGIC
		if (contentId.equals("abc123"))
			return "10.0.0.2:80/Files/abc123.txt";
		else if (contentId.equals("index"))
			return "10.0.0.2/Files/index.html";

		return null;
	}

	public void prepareRoute(String srcIp, String dstIp, TransportPort srcPort,
			TransportPort dstPort) {

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

		prepareRoute(srcDevice, dstDevice);

	}

	private void prepareRouteX(DatapathId srcSwId, DatapathId dstSwId) {

		MultiRoute multiRoute = mpathRoutingService.getMultiRoute(srcSwId,
				dstSwId);
		for (Route r : multiRoute.getRoutes())
			IcnModule.logger.info("Route: " + r.toString());

		// multiRoute.getRoute()
	}

	private void prepareRoute(IDevice srcDevice, IDevice dstDevice) {

		DatapathId srcSwId = srcDevice.getAttachmentPoints()[0].getSwitchDPID();
		DatapathId dstSwId = dstDevice.getAttachmentPoints()[0].getSwitchDPID();

		Route route = mpathRoutingService.getMultiRoute(srcSwId, dstSwId)
				.getRoute();

		if (route == null) {
			route = new Route(
					srcDevice.getAttachmentPoints()[0].getSwitchDPID(),
					dstDevice.getAttachmentPoints()[0].getSwitchDPID());
		}

		route = addClientAPs(route, srcDevice, dstDevice);

		Match.Builder forwardMatch = OFFactories
				.getFactory(OFVersion.OF_13)
				.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
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

		IcnModule.logger.info("Pushing route: " + route.toString());

		pushRoute(route, forwardMatch.build(), cookie, OFFlowModCommand.ADD);

		// Temporarily, packets return via the same route
		Collections.reverse(route.getPath());
		IcnModule.logger.info("Pushing route: " + route.toString());
		pushRoute(route, reverseMatch.build(), cookie, OFFlowModCommand.ADD);

	}

	private Route addClientAPs(Route route, IDevice srcDevice, IDevice dstDevice) {

		List<NodePortTuple> path = route.getPath();
		if (path == null) {
			path = new ArrayList<NodePortTuple>(2);
		}

		path.add(
				0,
				new NodePortTuple(srcDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), srcDevice.getAttachmentPoints()[0]
						.getPort()));
		path.add(
				path.size(),
				new NodePortTuple(dstDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), dstDevice.getAttachmentPoints()[0]
						.getPort()));

		route.setPath(path);

		return route;
	}

	public void flood(IOFSwitch sw, Ethernet eth, OFMessage msg) {
		OFPacketIn pi = (OFPacketIn) msg;
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		// Set Action to flood
		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		Set<OFPort> broadcastPorts = this.topologyService
				.getSwitchBroadcastPorts(sw.getId());

		if (broadcastPorts == null) {
			IcnModule.logger
					.debug("BroadcastPorts returned null. Assuming single switch w/no links.");
			/* Must be a single-switch w/no links */
			broadcastPorts = Collections.singleton(OFPort.FLOOD);
		}

		for (OFPort p : broadcastPorts) {
			if (p.equals(inPort))
				continue;
			actions.add(sw.getOFFactory().actions()
					.output(p, Integer.MAX_VALUE));
		}
		pob.setActions(actions);
		// log.info("actions {}",actions);
		// set buffer-id, in-port and packet-data based on packet-in
		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort(inPort);
		pob.setData(pi.getData());

		IcnModule.logger.info(
				"Writing flood PacketOut switch={} packet-in={} packet-out={}",
				new Object[] { sw, pi, pob.build() });

		sw.write(pob.build());

		return;

	}

	public void forward(IOFSwitch sw, Ethernet eth, OFMessage msg,
			FloodlightContext cntx) {
		// TODO Implementation of basic forwarding without constraints
		// TODO Maybe application aware routing based on transport ports
		OFPacketIn pi = (OFPacketIn) msg;
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		IDevice dstDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);
		DatapathId source = sw.getId();

		if (dstDevice != null) {
			IDevice srcDevice = IDeviceService.fcStore.get(cntx,
					IDeviceService.CONTEXT_SRC_DEVICE);

			if (srcDevice == null) {
				IcnModule.logger
						.error("No device entry found for source device. Is the device manager running? If so, report bug.");
				return;
			}

			/*
			 * Validate that the source and destination are not on the same
			 * switch port
			 */
			boolean on_same_if = false;
			for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
				if (sw.getId().equals(dstDap.getSwitchDPID())
						&& inPort.equals(dstDap.getPort())) {
					on_same_if = true;
				}
				break;
			}

			if (on_same_if) {
				IcnModule.logger
						.info("Both source and destination are on the same switch/port {}/{}. Action = NOP",
								sw.toString(), inPort);
				return;
			}

			SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
			SwitchPort dstDap = null;

			/*
			 * Search for the true attachment point. The true AP is not an
			 * endpoint of a link. It is a switch port w/o an associated link.
			 * Note this does not necessarily hold true for devices that 'live'
			 * between OpenFlow islands.
			 * 
			 * TODO Account for the case where a device is actually attached
			 * between islands (possibly on a non-OF switch in between two
			 * OpenFlow switches).
			 */
			for (SwitchPort ap : dstDaps) {
				if (topologyService.isEdge(ap.getSwitchDPID(), ap.getPort())) {
					dstDap = ap;
					break;
				}
			}

			Route route = mpathRoutingService.getRoute(source, inPort,
					dstDap.getSwitchDPID(), dstDap.getPort()); // cookie = 0,
																// i.e., default
																// route

			Match m = createMatchFromPacket(sw, inPort, cntx);
			U64 cookie = AppCookie.makeCookie(2, 0);

			if (route != null) {
				IcnModule.logger.debug("pushRoute inPort={} route={} "
						+ "destination={}:{}", new Object[] { inPort, route,
						dstDap.getSwitchDPID(), dstDap.getPort() });

				IcnModule.logger.debug(
						"Cretaing flow rules on the route, match rule: {}", m);
				pushRoute(route, m, cookie, OFFlowModCommand.ADD);

			} else {
				/* Route traverses no links --> src/dst devices on same switch */
				IcnModule.logger
						.debug("Could not compute route. Devices should be on same switch src={} and dst={}",
								srcDevice, dstDevice);
				Route r = new Route(
						srcDevice.getAttachmentPoints()[0].getSwitchDPID(),
						dstDevice.getAttachmentPoints()[0].getSwitchDPID());
				List<NodePortTuple> path = new ArrayList<NodePortTuple>(2);
				path.add(new NodePortTuple(srcDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), srcDevice.getAttachmentPoints()[0]
						.getPort()));
				path.add(new NodePortTuple(dstDevice.getAttachmentPoints()[0]
						.getSwitchDPID(), dstDevice.getAttachmentPoints()[0]
						.getPort()));
				r.setPath(path);
				pushRoute(r, m, cookie, OFFlowModCommand.ADD);
			}
		}

	}

	private Match createMatchFromPacket(IOFSwitch sw, OFPort inPort,
			FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}

}
