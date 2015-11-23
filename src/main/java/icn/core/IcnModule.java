package icn.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
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
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;
import net.floodlightcontroller.multipathrouting.IMultiPathRoutingService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.topology.ITopologyService;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IcnModule implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	public static Logger logger;

	public IOFSwitchService switchService = null;
	public IRoutingService routingService = null;
	public ITopologyService topologyService = null;
	public static IDeviceService deviceService = null;
	public IMultiPathRoutingService mpathRoutingService = null;

	protected final static IPv4Address VIP = IPv4Address.of("10.0.99.99");
	protected final static MacAddress VMAC = MacAddress.of("99:99:99:99:99:99");

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
		dependencies.add(IRoutingService.class);
		dependencies.add(ITopologyService.class);
		dependencies.add(IDeviceService.class);
		dependencies.add(IMultiPathRoutingService.class);
		return dependencies;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		mpathRoutingService = context
				.getServiceImpl(IMultiPathRoutingService.class);
		logger = LoggerFactory.getLogger(IcnModule.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	    floodlightProvider.addOFMessageListener(OFType.STATS_REPLY, new
		 StatsListener());
		
		switchService.addOFSwitchListener(new SwitchListener(switchService));

		IcnEngine.getInstance().setTopologyService(this.topologyService);
		IcnEngine.getInstance().setRoutingService(this.routingService);
		IcnEngine.getInstance().setDeviceService(this.deviceService);
		IcnEngine.getInstance().setSwitchService(this.switchService);
		IcnEngine.getInstance().setMpathRoutingService(mpathRoutingService);

	}

	@Override
	public String getName() {
		return IcnModule.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return true;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return true;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		

		if (eth.getEtherType().equals(EthType.ARP)) {
			ARP arp = (ARP) eth.getPayload();
			if (arp.getTargetProtocolAddress().equals(VIP))
				OFUtils.pushARP(sw, eth, msg, IcnModule.VMAC);
			else 
				IcnEngine.getInstance().flood(sw, eth, msg);
		}

		if (eth.getEtherType().equals(EthType.IPv4)) {
			IPv4 ipv4 = (IPv4) eth.getPayload();
			IcnModule.logger.info("Packet type: " + eth.getEtherType());
			IcnModule.logger.info("SRC Device: "
					+ IDeviceService.fcStore.get(cntx,
							IDeviceService.CONTEXT_SRC_DEVICE));
			IcnModule.logger.info("DST Device: "
					+ IDeviceService.fcStore.get(cntx,
							IDeviceService.CONTEXT_DST_DEVICE));

			if (ipv4.getProtocol().equals(IpProtocol.TCP)) {

				TCP tcp = (TCP) ipv4.getPayload();
				IcnEngine.getInstance().handleTcp(sw, msg, eth, ipv4, tcp);
			}
		}

		return Command.CONTINUE;
	}


}
