package icn.core;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IcnModule implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;

	public IOFSwitchService switchService = null;

	protected final static IPv4Address VIP = IPv4Address.of("10.0.99.99");
	protected final static MacAddress VMAC = MacAddress.of("00:00:00:00:00:10");

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {

		Collection<Class<? extends IFloodlightService>> dependencies = new ArrayList<Class<? extends IFloodlightService>>();
		dependencies.add(IFloodlightProviderService.class);
		dependencies.add(IOFSwitchService.class);
		return dependencies;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(IcnModule.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		switchService.addOFSwitchListener(new SwitchListener(switchService));

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return IcnModule.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (eth.getEtherType().equals(EthType.ARP)) {
			ARP arp = (ARP) eth.getPayload();

			if (arp.getTargetProtocolAddress().equals(VIP))
				OFUtils.pushARP(sw, eth, msg);

			// logger.info("" + arp.getSenderHardwareAddress());
		}

		if (eth.getEtherType().equals(EthType.IPv4)) {
			IPv4 ipv4 = (IPv4) eth.getPayload();
			logger.info("SRC IP=" + ipv4.getSourceAddress());
			logger.info("IP DST: " + ipv4.getDestinationAddress() + " ? " + VIP);

			if (ipv4.getProtocol().equals(IpProtocol.TCP)) {
				/* We got a TCP packet; get the payload from IPv4 */

				TCP tcp = (TCP) ipv4.getPayload();
				
				if(new String(((Data)tcp.getPayload()).serialize()).contains("HTTP") ) {
					OFUtils.redirectHttpRequest(sw, msg, ipv4, eth, tcp, "10.0.0.2");
				}

				short SYN = 2;
				if(tcp.getFlags()==SYN && ipv4.getDestinationAddress().equals(VIP) && tcp.getDestinationPort().equals(TransportPort.of(80))) {
					logger.info("Handling TCP SYN");
					OFUtils.sendSynAck(sw, msg, ipv4, eth, tcp);
				}
				
				
				
			} else if (ipv4.getProtocol().equals(IpProtocol.UDP)) {
				/* We got a UDP packet; get the payload from IPv4 */
				UDP udp = (UDP) ipv4.getPayload();
				logger.info("Transport protocol: " + ipv4.getProtocol());
				logger.info("SRC PORT=" + udp.getSourcePort());
				logger.info("DST PORT=" + udp.getDestinationPort());
				/* Various getters and setters are exposed in UDP */

				/* Your logic here! */
			}
		}

		return Command.CONTINUE;
	}

	public static class SwitchListener implements IOFSwitchListener {

		private IOFSwitchService switchService;

		public SwitchListener(IOFSwitchService switchService) {
			this.switchService = switchService;
		}

		@Override
		public void switchAdded(DatapathId switchId) {
			//OFUtils.insertHTTPDpiFlow(switchService.getSwitch(switchId));
		}

		@Override
		public void switchRemoved(DatapathId switchId) {
			// TODO Auto-generated method stub

		}

		@Override
		public void switchActivated(DatapathId switchId) {
			// TODO Auto-generated method stub
			//OFUtils.insertHTTPDpiFlow(switchService.getActiveSwitch(switchId));
		}

		@Override
		public void switchPortChanged(DatapathId switchId, OFPortDesc port,
				PortChangeType type) {
			// TODO Auto-generated method stub

		}

		@Override
		public void switchChanged(DatapathId switchId) {
			// TODO Auto-generated method stub

		}

	}

}
