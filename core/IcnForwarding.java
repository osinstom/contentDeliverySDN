package icn.core;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.multipathrouting.IMultiPathRoutingService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MatchUtils;

public class IcnForwarding {
	
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	public static int FLOWMOD_DEFAULT_PRIORITY = 1; // 0 is the default
													// table-miss flow in
													// OF1.3+, so we need to use
													// 1
	protected IOFSwitchService switchService;
	protected IDeviceService deviceService;
	protected IRoutingService routingService;
	protected ITopologyService topologyService;
	protected IMultiPathRoutingService mpathRoutingService;
	
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
	
	protected boolean pushRoute(Route route, Match match, DatapathId id,
			U64 cookie, OFFlowModCommand flowModCommand) {

		boolean packetOutSent = false;

		List<NodePortTuple> switchPortList = route.getPath();

		for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			DatapathId switchDPID = switchPortList.get(indx).getNodeId();
			IOFSwitch sw = switchService.getSwitch(switchDPID);

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

			fmb.setMatch(mb.build()).setActions(actions)
//					.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
//					.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
					.setBufferId(OFBufferId.NO_BUFFER).setCookie(cookie)
					.setOutPort(outPort).setPriority(FLOWMOD_DEFAULT_PRIORITY);

			try {

				IcnModule.logger.trace("Pushing Route flowmod routeIndx={} "
						+ "sw={} inPort={} outPort={}", new Object[] { indx,
						sw, fmb.getMatch().get(MatchField.IN_PORT), outPort });

				sw.write(fmb.build());

			} catch (Exception e) {
				IcnModule.logger.error("Failure writing flow mod", e);
			}
		}

		return packetOutSent;

	}

	public void setMpathRoutingService(IMultiPathRoutingService mpathRoutingService) {
		this.mpathRoutingService = mpathRoutingService;
	}

}
