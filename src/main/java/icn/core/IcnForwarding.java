package icn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.multipathrouting.IMultiPathRoutingService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MatchUtils;

public class IcnForwarding {

	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 3; // in seconds
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 2; // infinite
	public static int FLOWMOD_DEFAULT_PRIORITY = Integer.MAX_VALUE; // 0 is the default
													// table-miss flow in
													// OF1.3+, so we need to use
													// 1

	protected IOFSwitchService switchService;
	protected IDeviceService deviceService;
	protected IRoutingService routingService;
	protected ITopologyService topologyService;
	protected IMultiPathRoutingService mpathRoutingService;
	protected ILinkDiscoveryService linkDiscoveryService;

	public void setLinkDiscoveryService(
			ILinkDiscoveryService linkDiscoveryService) {
		this.linkDiscoveryService = linkDiscoveryService;
	}

	public void setSwitchService(IOFSwitchService switchService) {
		this.switchService = switchService;
	}

	public void setRoutingService(IRoutingService routingService) {
		this.routingService = routingService;
	}

	public void setTopologyService(ITopologyService topologyService) {
		this.topologyService = topologyService;
	}

	public void setDeviceService(IDeviceService deviceService) {
		this.deviceService = deviceService;
	}

	protected boolean pushRoute(List<NodePortTuple> switchPortList,
			Match match, U64 cookie, OFFlowModCommand flowModCommand,
			DatapathId without) {

		boolean packetOutSent = false;

		for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			DatapathId switchDPID = switchPortList.get(indx).getNodeId();
			IOFSwitch sw = switchService.getSwitch(switchDPID);
			if (without != null && switchDPID.equals(without)) {

			} else {
				if (sw == null) {
					if (IcnModule.logger.isWarnEnabled()) {
						IcnModule.logger.warn(
								"Unable to push route, switch at DPID {} "
										+ "not available", switchDPID);
					}
					return packetOutSent;
				}

				// need to build flow mod based on what type it is. Cannot set
				// command later
				OFFlowMod.Builder fmb;
				switch (flowModCommand) {
				case ADD:
					fmb = sw.getOFFactory().buildFlowAdd();
					break;
				case DELETE:
					fmb = sw.getOFFactory().buildFlowDelete();
					break;
				case DELETE_STRICT:
					fmb = sw.getOFFactory().buildFlowDeleteStrict();
					break;
				case MODIFY:
					fmb = sw.getOFFactory().buildFlowModify();
					break;
				default:
					IcnModule.logger
							.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");
				case MODIFY_STRICT:
					fmb = sw.getOFFactory().buildFlowModifyStrict();
					break;
				}

				OFActionOutput.Builder aob = sw.getOFFactory().actions()
						.buildOutput();
				List<OFAction> actions = new ArrayList<OFAction>();
				Match.Builder mb = MatchUtils.convertToVersion(match, sw
						.getOFFactory().getVersion());

				// set input and output ports on the switch
				OFPort outPort = switchPortList.get(indx).getPortId();
				OFPort inPort = switchPortList.get(indx - 1).getPortId();
				mb.setExact(MatchField.IN_PORT, inPort);
				aob.setPort(outPort);
				aob.setMaxLen(Integer.MAX_VALUE);
				actions.add(aob.build());

				// if (FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG ||
				// requestFlowRemovedNotification) {
				// Set<OFFlowModFlags> flags = new HashSet<>();
				// flags.add(OFFlowModFlags.SEND_FLOW_REM);
				// fmb.setFlags(flags);
				// }
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setMatch(mb.build()).setActions(actions)
						.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
						.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
						.setFlags(flags).setBufferId(OFBufferId.NO_BUFFER)
						.setCookie(cookie).setOutPort(outPort)
						.setPriority(FLOWMOD_DEFAULT_PRIORITY);

				try {

					IcnModule.logger.trace(
							"Pushing Route flowmod routeIndx={} "
									+ "sw={} inPort={} outPort={}",
							new Object[] { indx, sw,
									fmb.getMatch().get(MatchField.IN_PORT),
									outPort });

					sw.write(fmb.build());

				} catch (Exception e) {
					IcnModule.logger.error("Failure writing flow mod", e);
				}
			}

		}

		return packetOutSent;
	}

	public void setMpathRoutingService(
			IMultiPathRoutingService mpathRoutingService) {
		this.mpathRoutingService = mpathRoutingService;
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
				pushRoute(route.getPath(), m, cookie, OFFlowModCommand.ADD,
						null);

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
				pushRoute(r.getPath(), m, cookie, OFFlowModCommand.ADD, null);
			}
		}

	}

	private Match createMatchFromPacket(IOFSwitch sw, OFPort inPort,
			FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
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

		sw.write(pob.build());

		return;

	}

	public static void setNatFlow(IOFSwitch sw, OFMessage msg,
			IPv4Address srcIp, IPv4Address dstIp, TransportPort sourcePort,
			TransportPort destinationPort) {

		OFPacketIn pi = (OFPacketIn) msg;

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		OFFactory ofFactory = sw.getOFFactory();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFOxms oxms = ofFactory.oxms();

		OFActionSetField setTcpDst = ofFactory
				.actions()
				.buildSetField()
				.setField(
						oxms.buildTcpDst().setValue(TransportPort.of(80))
								.build()).build();
		actions.add(setTcpDst);

		OFActionSetField setTcpSrc = ofFactory.actions().buildSetField()
				.setField(oxms.buildTcpSrc().setValue(destinationPort).build())
				.build();
		actions.add(setTcpSrc);

		OFPort output = null;
		OFPort revOutput = null;

		for (ContentFlow flow : Monitoring.flows.get(srcIp.toString() + ":" + dstIp.toString())) {
			if (flow.getFlowId() == destinationPort.getPort()) {
				IcnModule.logger.info(flow.getRoute().toString());
				output = flow.getRoute().get(1).getPortId();
				revOutput = flow.getRoute().get(0).getPortId();
			}
		}

		Set<OFFlowModFlags> flags = new HashSet<>();
		flags.add(OFFlowModFlags.SEND_FLOW_REM);

		actions.add(ofFactory.actions().output(output, 0xffFFffFF));

		OFFlowAdd natFlow = ofFactory
				.buildFlowAdd()
				.setActions(actions)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setFlags(flags)
				.setPriority(FLOWMOD_DEFAULT_PRIORITY)
				.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setMatch(
						ofFactory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
								.setExact(MatchField.IPV4_SRC, srcIp)
								.setExact(MatchField.IPV4_DST, dstIp)
								.setExact(MatchField.TCP_SRC, sourcePort)
								.setExact(MatchField.TCP_DST, destinationPort)
								.build()).build();

		ArrayList<OFMessage> messages = new ArrayList<OFMessage>();
		messages.add(natFlow);

		List<OFAction> revActions = new ArrayList<OFAction>();
		OFActionSetField setRevTcpDst = ofFactory.actions().buildSetField()
				.setField(oxms.buildTcpDst().setValue(sourcePort).build())
				.build();
		revActions.add(setRevTcpDst);

		OFActionSetField setRevTcpSrc = ofFactory.actions().buildSetField()
				.setField(oxms.buildTcpSrc().setValue(destinationPort).build())
				.build();
		revActions.add(setRevTcpSrc);

		revActions.add(ofFactory.actions().output(revOutput, 0xffFFffFF));

		OFFlowAdd revNatFlow = ofFactory
				.buildFlowAdd()
				.setActions(revActions)
				.setFlags(flags)
				.setPriority(FLOWMOD_DEFAULT_PRIORITY)
				.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(
						ofFactory
								.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
								.setExact(MatchField.IPV4_SRC, dstIp)
								.setExact(MatchField.IPV4_DST, srcIp)
								.setExact(MatchField.TCP_SRC,
										TransportPort.of(80))
								.setExact(MatchField.TCP_DST, destinationPort)
								.build()).build();
		messages.add(revNatFlow);

		OFPacketOut po = sw.getOFFactory().buildPacketOut()
				.setData(((OFPacketIn) msg).getData()).setActions(actions)
				.setInPort(inPort).build();

		messages.add(po);
		sw.write(messages);
	}

}
