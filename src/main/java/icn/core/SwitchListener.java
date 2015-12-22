package icn.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv6Address;

public class SwitchListener implements IOFSwitchListener {

	private IOFSwitchService switchService;
	private List<DatapathId> activeCS;

	public SwitchListener(IOFSwitchService switchService) {
		this.switchService = switchService;
		activeCS = new ArrayList<DatapathId>();
		addContentServers();
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		IcnModule.logger.info("Added switch: " + switchId);
		OFUtils.insertHTTPDpiFlow(switchService.getSwitch(switchId));
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
	}

	@Override
	public void switchActivated(DatapathId switchId) {
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
	}

	@Override
	public void switchChanged(DatapathId switchId) {
	}
	
	public void addContentServers() {
		List<ContentServer> contentServers = Utils.getContentServersInfo();

		for (ContentServer cs : contentServers) {
				IcnModule.logger.info("Adding: " + cs.getIpAddr());
				Long deviceKey = IcnModule.deviceService.getDeviceKeyCounter()
						.getAndIncrement();
				Entity entity = new Entity(cs.getMacAddress(), null,
						cs.getIpAddr(), IPv6Address.NONE, cs.getDpId(),
						cs.getSwitchPort(), new Date());
				IcnModule.deviceService.getDeviceMap().put(
						deviceKey,
						new Device((DeviceManagerImpl) IcnModule.deviceService,
								deviceKey, entity,
								new DefaultEntityClassifier()
										.classifyEntity(entity)));
				activeCS.add(cs.getDpId());
				for (IDevice device : IcnModule.deviceService.getAllDevices())
					IcnModule.logger.info("Device: " + device.toString());
		}
	}
	
	public void discoverContentServer(DatapathId switchId) {
		List<ContentServer> contentServers = Utils.getContentServersInfo();

		for (ContentServer cs : contentServers) {

			if (cs.getDpId().equals(switchId)) {
				IcnModule.logger.info("Adding: " + cs.getIpAddr());
				Long deviceKey = IcnModule.deviceService.getDeviceKeyCounter()
						.getAndIncrement();
				Entity entity = new Entity(cs.getMacAddress(), null,
						cs.getIpAddr(), IPv6Address.NONE, cs.getDpId(),
						cs.getSwitchPort(), new Date());
				IcnModule.deviceService.getDeviceMap().put(
						deviceKey,
						new Device((DeviceManagerImpl) IcnModule.deviceService,
								deviceKey, entity,
								new DefaultEntityClassifier()
										.classifyEntity(entity)));
				activeCS.add(cs.getDpId());
				for (IDevice device : IcnModule.deviceService.getAllDevices())
					IcnModule.logger.info("Device: " + device.toString());
			}
		}
	}

}