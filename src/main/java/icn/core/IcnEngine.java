package icn.core;

import icn.core.ContentDesc.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

public class IcnEngine extends IcnForwarding {

	public static IcnEngine instance = null;

	public static IcnEngine getInstance() {
		if (instance == null)
			instance = new IcnEngine();

		return instance;
	}

	public IcnEngine() {

	}

	public void handleTcp(IOFSwitch sw, OFMessage msg, Ethernet eth, IPv4 ipv4,
			TCP tcp) {

		String payload = new String(((Data) tcp.getPayload()).serialize());
		String srcIp = ipv4.getSourceAddress().toString(); // ContentRequest
		if (ipv4.getDestinationAddress().equals(IcnModule.VIP)) {
			if (payload.contains("HTTP") && payload.contains("GET")) { // HTTP
				IcnModule.logger.info(payload); // GET =
				String contentFlowId = srcIp;
				int flowId = getFlowId(contentFlowId);

				String contentSourceUrl = getContentSource(
						Utils.getContentId(payload), srcIp, flowId);

				if (contentSourceUrl != null) {

					contentSourceUrl = contentSourceUrl.replace("$flowId$",
							Integer.toString(flowId));

					OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, srcIp,
							contentSourceUrl);
				} else
					OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
							OFUtils.HTTP_NOTFOUND);

			} else if (payload.contains("HTTP") && payload.contains("PUT")) {
				IcnModule.logger.info(payload);
			} else if (tcp.getFlags() == 2 // If TCP SYN to Virtual IP is
											// received
											// on port 80
					&& ipv4.getDestinationAddress().equals(IcnModule.VIP)
					&& tcp.getDestinationPort().equals(TransportPort.of(80))) {
				OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
			} else if (tcp.getFlags() == OFUtils.FIN_ACK_FLAG) {
				OFPacketIn pi = (OFPacketIn) msg;
				OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
						.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

				byte[] resp = OFUtils.generateTCPResponse(eth, ipv4, tcp,
						OFUtils.ACK_FLAG, null);
				OFUtils.sendPacketOut(sw, inPort, resp);
			}
		} else {
			IcnModule.logger.info("From sw: " + sw.getId()
					+ " Route to dest request: " + tcp.getSourcePort() + " "
					+ tcp.getDestinationPort() + " "
					+ ipv4.getDestinationAddress());
			String contentFlowId = ipv4.getSourceAddress().toString();
			IcnModule.logger.info(Monitoring.getInstance()
					.getFlowIds(contentFlowId).toString());

			if (Monitoring.getInstance().getFlowIds(contentFlowId)
					.contains(tcp.getDestinationPort().getPort())) {

				setNatFlow(sw, msg, ipv4.getSourceAddress(),
						ipv4.getDestinationAddress(), tcp.getSourcePort(),
						tcp.getDestinationPort());

			} else {
				// OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
				// OFUtils.HTTP_BADREQUEST);
			}

		}

	}

	private Integer getFlowId(String contentFlowId) {

		int flowId = 0;
		do {
			Random rn = new Random();
			int range = 65535 - 49152 + 1;
			flowId = rn.nextInt(range) + 49152;

		} while (flowId == 0
				|| Monitoring.getInstance().getFlowIds(contentFlowId)
						.contains(flowId));
		return flowId;
	}

	private String getContentSource(String contentId, String srcIp, int flowId) {

		ContentDesc contentDesc = Utils.getContentDesc(contentId);
		IcnModule.logger.info(contentDesc.toString());
		Location bestSource = calculateBestSource(contentDesc.getLocations(),
				srcIp, contentDesc.getBandwidth(), flowId);

		return bestSource.getIpAddr() + ":$flowId$/"
				+ bestSource.getLocalPath();

	}

	private Location calculateBestSource(List<Location> locations,
			String srcIp, int minBandwidth, int flowId) {

		IDevice srcDev = Utils.getDevice(srcIp);
		IDevice dstDev = null;
		KeyValuePair<Location, Route> bestSource = new KeyValuePair<ContentDesc.Location, Route>(
				null, null);

		List<Location> potentials = new ArrayList<ContentDesc.Location>();
		for (Location location : locations) {
			if (location.isLoaded() == false) {
				potentials.add(location);
			}
		}
		Map<Location, List<Route>> locAndRoutes = new HashMap<ContentDesc.Location, List<Route>>();
		List<Route> routes = new ArrayList<Route>();
		for (Location potential : potentials) {

			dstDev = Utils.getDevice(potential.getIpAddr());

			if (dstDev == null)
				IcnModule.logger.info("DST NULL");
			else if (srcDev == null)
				IcnModule.logger.info("SRC NULL");
			IcnModule.logger.info(srcDev.toString());
			IcnModule.logger.info(dstDev.toString());
//			ArrayList<Route> rs = IcnModule.mpathRoutingService.getMultiRoute(
//					srcDev.getAttachmentPoints()[0].getSwitchDPID(),
//					dstDev.getAttachmentPoints()[0].getSwitchDPID()).getRoutes(
//					minBandwidth);
			List<Route> rs = IcnModule.mpathRoutingService.getAllRoutes(
					srcDev.getAttachmentPoints()[0].getSwitchDPID(), srcDev.getAttachmentPoints()[0].getPort(),
					dstDev.getAttachmentPoints()[0].getSwitchDPID(), dstDev.getAttachmentPoints()[0].getPort(),
					minBandwidth);

//			IcnModule.logger.info("All paths: ");
//			for (Route r : routez) {
//				IcnModule.logger.info(r.toString());
//			}

			locAndRoutes.put(potential, rs);

		}

		double selectionCost = Double.MAX_VALUE;

		IcnModule.logger.info("All possibilities: \n");
		for (Entry<Location, List<Route>> entry : locAndRoutes.entrySet()) {
			IcnModule.logger.info("To location: " + entry.getKey());
			for (Route r : entry.getValue()) {
				double tmpCost = calculateSelectionCost(r.getPath().size(),
						r.getTotalCost());
				if (tmpCost < selectionCost) {
					bestSource.setKey(entry.getKey());
					bestSource.setValue(r);
					selectionCost = tmpCost;
				}
//				IcnModule.logger.info(r.toString());
//				IcnModule.logger.info("Selection factor: " + tmpCost);
			}
		}

		IcnModule.logger.info("Best source=" + bestSource);

		if (bestSource.getKey() != null && bestSource.getValue() != null) {

			if (Monitoring.flows.containsKey(srcIp))
				Monitoring.flows.get(srcIp).add(new ContentFlow(flowId));
			else {
				ConcurrentLinkedQueue<ContentFlow> cFlows = new ConcurrentLinkedQueue<ContentFlow>();
				cFlows.add(new ContentFlow(flowId));
				Monitoring.flows.put(srcIp, cFlows);
			}
			prepareRoute(bestSource.getValue(), srcDev,
					Utils.getDevice(bestSource.getKey().getIpAddr()),
					TransportPort.of(flowId));
		}

		return bestSource.getKey();

	}

	private Double calculateSelectionCost(int hops, int routeCost) {
		double hopsWeight = 0.6;
		double routeCostWeight = 0.3;
		return hopsWeight * hops + routeCostWeight * routeCost;
	}

	private void prepareRoute(Route route, IDevice srcDevice,
			IDevice dstDevice, TransportPort srcTcpPort) {

		DatapathId srcSwId = srcDevice.getAttachmentPoints()[0].getSwitchDPID();
		DatapathId dstSwId = dstDevice.getAttachmentPoints()[0].getSwitchDPID();
		// Route route = null;
		// if (!srcSwId.equals(dstSwId)) {
		// route = mpathRoutingService.getMultiRoute(srcSwId, dstSwId)
		// .getRoute(srcDevice.getAttachmentPoints()[0].getPort(),
		// dstDevice.getAttachmentPoints()[0].getPort());
		// } else {
		// route = routingService.getRoute(srcSwId,
		// srcDevice.getAttachmentPoints()[0].getPort(), dstSwId,
		// dstDevice.getAttachmentPoints()[0].getPort(), null);
		// }
		

		ContentFlow flow = null;
		for (ContentFlow f : Monitoring.flows
				.get(srcDevice.getIPv4Addresses()[0].toString())) {
			if (f.getFlowId() == srcTcpPort.getPort()) {
				flow = f;
			}
		}
		IcnModule.logger.info("Route for flow: " + route.getPath());
		flow.setRoute(route.getPath());

		Match.Builder forwardMatch = OFFactories.getFactory(OFVersion.OF_13)
				.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IPV4_SRC, srcDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.TCP_SRC, srcTcpPort)
				.setExact(MatchField.TCP_DST, TransportPort.of(80));

		Match.Builder reverseMatch = OFFactories.getFactory(OFVersion.OF_13)
				.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IPV4_SRC, dstDevice.getIPv4Addresses()[0])
				.setExact(MatchField.IPV4_DST, srcDevice.getIPv4Addresses()[0])
				.setExact(MatchField.TCP_DST, srcTcpPort)
				.setExact(MatchField.TCP_SRC, TransportPort.of(80));

		flow.setFlowMatch(forwardMatch.build());

		U64 cookie = AppCookie.makeCookie(2, 0);

		IcnModule.logger.info("Pushing route: " + route.getPath().toString());

		pushRoute(route.getPath(), forwardMatch.build(), cookie,
				OFFlowModCommand.ADD, srcSwId);

		List<NodePortTuple> revRoute = Utils.reverse(route.getPath());

		IcnModule.logger.info("Pushing route: " + revRoute.toString());
		pushRoute(revRoute, reverseMatch.build(), cookie, OFFlowModCommand.ADD,
				srcSwId);

	}

	public class KeyValuePair<K, V> implements Map.Entry<K, V> {
		private K key;
		private V value;

		@Override
		public String toString() {
			return "KeyValuePair [key=" + key + ", value=" + value + "]";
		}

		public KeyValuePair(K key, V value) {
			this.key = key;
			this.value = value;
		}

		public K getKey() {
			return this.key;
		}

		public V getValue() {
			return this.value;
		}

		public K setKey(K key) {
			return this.key = key;
		}

		public V setValue(V value) {
			return this.value = value;
		}
	}

}
