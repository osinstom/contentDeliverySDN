package icn.core;

import icn.core.ContentDesc.Location;
import icn.core.Utils.DeviceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RoutingDecision;
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
import org.python.modules.sre.SRE_REPEAT;

public class IcnEngine extends IcnForwarding {

	public static IcnEngine instance = null;
	private int actual = 49152;

	public static IcnEngine getInstance() {
		if (instance == null)
			instance = new IcnEngine();

		return instance;
	}

	public void handleTcp(IOFSwitch sw, OFMessage msg, Ethernet eth, IPv4 ipv4,
			TCP tcp, FloodlightContext cntx) {

		String payload = new String(((Data) tcp.getPayload()).serialize());
		String srcIp = ipv4.getSourceAddress().toString(); // ContentRequest
		if (ipv4.getDestinationAddress().equals(IcnModule.VIP)) {
			if (payload.contains("HTTP") && payload.contains("GET")) { // HTTP
				IcnModule.logger.info(payload); // GET =
				String contentFlowId = srcIp;
				int flowId = getFlowId(contentFlowId);

				String contentSourceUrl;
				try {
					contentSourceUrl = getContentSource(
							Utils.getContentId(payload), srcIp, flowId);

					contentSourceUrl = contentSourceUrl.replace("$flowId$",
							Integer.toString(flowId));
					// contentSourceUrl = contentSourceUrl.replace(":$flowId$",
					// "");
					OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, srcIp,
							contentSourceUrl);
				} catch (ContentNotFoundException e) {
					OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
							OFUtils.HTTP_NOTFOUND);
				} catch (NoNetworkResourcesException e) {
					IcnModule.logger.info("503 SERVICE UNAVAILABLE!!!");
					OFUtils.returnHttpResponse(sw, msg, ipv4, eth, tcp,
							OFUtils.HTTP_SERVICE_UNAVAILABLE);
				}

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

			String contentFlowId = ipv4.getSourceAddress().toString();
			// IcnModule.logger.info(Monitoring.getInstance()
			// .getFlowIds(contentFlowId).toString());

			if (Monitoring.getInstance().getFlowIds(contentFlowId)
					.contains(tcp.getDestinationPort().getPort())) {
				IcnModule.logger.info("REDIRECTED !!!!!!!!!!!!!!!!! ");
				IcnModule.logger.info(tcp.getSourcePort() + " "
						+ tcp.getDestinationPort());
				setNatFlow(sw, msg, ipv4.getSourceAddress(),
						ipv4.getDestinationAddress(), tcp.getSourcePort(),
						tcp.getDestinationPort());
			}

			if (tcp.getDestinationPort().equals(TransportPort.of(80))) {
				IcnModule.logger.info("Forwarding DASH...");
				Forwarding forw = new Forwarding();
				OFPacketIn pi = (OFPacketIn) msg;
				forw.doForwardFlow(sw, pi, cntx, false);
			}

		}

	}

	private Integer getFlowId(String contentFlowId) {

		int start = 49152;
		int stop = 65535;

		if (actual >= stop)
			actual = start;

		for (int i = actual; i < stop; i++) {
			if (!Monitoring.getInstance().getFlowIds(contentFlowId).contains(i)) {
				actual = i;
				break;
			}
		}

		return actual;

		// int flowId = 0;
		// do {
		// Random rn = new Random();
		// int range = 65535 - 49152 + 1;
		// flowId = rn.nextInt(range) + 49152;
		//
		// } while (flowId == 0
		// || Monitoring.getInstance().getFlowIds(contentFlowId)
		// .contains(flowId));
		// return flowId;
	}

	private String getContentSource(String contentId, String srcIp, int flowId)
			throws ContentNotFoundException, NoNetworkResourcesException {

		ContentDesc contentDesc = Utils.getContentDesc(contentId);
		if (contentDesc == null)
			throw new ContentNotFoundException();

		Location bestSource = calculateBestSource(contentDesc.getLocations(),
				srcIp, contentDesc.getBandwidth(), flowId);

		return bestSource.getIpAddr() + ":$flowId$/"
				+ bestSource.getLocalPath();

	}

	private Location calculateBestSource(List<Location> locations,
			String srcIp, int minBandwidth, int flowId)
			throws NoNetworkResourcesException {

		IDevice srcDev = Utils.getDevice(srcIp, Utils.DeviceType.SRC);
		IDevice dstDev = null;
		List<KeyValuePair<Location, Route>> bestSources = new ArrayList<IcnEngine.KeyValuePair<Location, Route>>();

		List<Location> potentials = new ArrayList<ContentDesc.Location>();
		for (Location location : locations) {
			if (location.isLoaded() == false) {
				potentials.add(location);
			}
		}

		Map<Location, List<Route>> locAndRoutes = new HashMap<ContentDesc.Location, List<Route>>();
		for (Location potential : potentials) {

			dstDev = Utils.getDevice(potential.getIpAddr(),
					Utils.DeviceType.DST);

			IcnModule.logger.info("DST & SRC: " + dstDev + " " + srcDev);

			List<Route> rs = IcnModule.mpathRoutingService.getAllRoutes(
					srcDev.getAttachmentPoints()[0].getSwitchDPID(),
					srcDev.getAttachmentPoints()[0].getPort(),
					dstDev.getAttachmentPoints()[0].getSwitchDPID(),
					dstDev.getAttachmentPoints()[0].getPort(), minBandwidth,
					IcnConfiguration.getInstance().getMaxShortestRoutes(),
					IcnConfiguration.getInstance().getRouteLengthDelta());

			IcnModule.logger.info("here 1");
			if (rs.size() != 0)
				locAndRoutes.put(potential, rs);

		}

		if (locAndRoutes.size() == 0)
			throw new NoNetworkResourcesException();

		double selectionCost = 0;

		// IcnModule.logger.info("All possibilities: \n");
		for (Entry<Location, List<Route>> entry : locAndRoutes.entrySet()) {
			IcnModule.logger.info("To location: " + entry.getKey());
			for (Route r : entry.getValue()) {
				double tmpCost = calculateSelectionCost(r.getPath().size(),
						r.getBottleneckBandwidth());

				if (tmpCost > selectionCost) {
					bestSources.clear();
					bestSources
							.add(new KeyValuePair<ContentDesc.Location, Route>(
									entry.getKey(), r));
					selectionCost = tmpCost;
				} else if (tmpCost == selectionCost) {
					bestSources
							.add(new KeyValuePair<ContentDesc.Location, Route>(
									entry.getKey(), r));
				}
				IcnModule.logger.info("Route: Cost=" + tmpCost + ", via: "
						+ Utils.routeToString(r));

			}
		}
		IcnModule.logger.info("here 2");

		KeyValuePair<ContentDesc.Location, Route> bestSource = getBestSource(bestSources);

		IcnModule.logger.info("Best source=" + bestSource.getKey());

		if (bestSource.getKey() != null && bestSource.getValue() != null) {

			if (Monitoring.flows.containsKey(srcIp))
				Monitoring.flows.get(srcIp).add(new ContentFlow(flowId));
			else {
				ConcurrentLinkedQueue<ContentFlow> cFlows = new ConcurrentLinkedQueue<ContentFlow>();
				cFlows.add(new ContentFlow(flowId));
				Monitoring.flows.put(srcIp, cFlows);
			}
			prepareRoute(bestSource.getValue(), srcDev, Utils.getDevice(
					bestSource.getKey().getIpAddr(), DeviceType.DST),
					TransportPort.of(flowId));
			IcnModule.logger.info("here 3");
		}

		return bestSource.getKey();

	}

	private KeyValuePair<Location, Route> getBestSource(
			List<KeyValuePair<Location, Route>> bestSources) {

		Random rand = new Random();
		return bestSources.get(rand.nextInt(bestSources.size()));

	}

	private Double calculateSelectionCost(int hops, int bottleneckBandwidth) {

		hops = hops / 2;

		double x = IcnConfiguration.getInstance()
				.getPathLenghtReservationLevel()
				- IcnConfiguration.getInstance().getPathLengthAspirationLevel();
		double cost1 = (IcnConfiguration.getInstance()
				.getPathLenghtReservationLevel() - hops) / x;

		double y = IcnConfiguration.getInstance()
				.getBandwidthReservationLevel()
				- IcnConfiguration.getInstance().getBandwidthAspirationLevel();
		double cost2 = (IcnConfiguration.getInstance()
				.getBandwidthReservationLevel() - bottleneckBandwidth) / y;

		if (cost2 == 0)
			return cost1;

		return Math.min(cost1, cost2);
	}

	private void prepareRoute(Route route, IDevice srcDevice,
			IDevice dstDevice, TransportPort srcTcpPort) {

		DatapathId srcSwId = srcDevice.getAttachmentPoints()[0].getSwitchDPID();
		DatapathId dstSwId = dstDevice.getAttachmentPoints()[0].getSwitchDPID();

		ContentFlow flow = null;
		for (ContentFlow f : Monitoring.flows
				.get(srcDevice.getIPv4Addresses()[0].toString())) {
			if (f.getFlowId() == srcTcpPort.getPort()) {
				flow = f;
			}
		}

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

		IcnModule.logger.info("Pushing route: "
				+ Utils.routeToString(route.getPath()));

		pushRoute(route.getPath(), forwardMatch.build(),
				AppCookie.makeCookie(2, 0), OFFlowModCommand.ADD, srcSwId);

		List<NodePortTuple> revRoute = Utils.reverse(route.getPath());

		IcnModule.logger
				.info("Pushing route: " + Utils.routeToString(revRoute));
		pushRoute(revRoute, reverseMatch.build(), AppCookie.makeCookie(2, 0),
				OFFlowModCommand.ADD, srcSwId);

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
