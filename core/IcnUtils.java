package icn.core;

import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;

public class IcnUtils {

	public static void initIcnFlows(Set<IOFSwitch> switches) {
		
		IcnModule.logger.info("Init..... List size="  + switches.size());
		for(IOFSwitch sw : switches)
			OFUtils.insertHTTPDpiFlow(sw);
		
	}
	
	

}
