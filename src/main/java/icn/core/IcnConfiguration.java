package icn.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.projectfloodlight.openflow.types.DatapathId;

public class IcnConfiguration {

	public static IcnConfiguration instance = null;

	private Properties props = new Properties();
	
	private HashMap<String, String> dpidToSwitch = new HashMap<String, String>();

	public static IcnConfiguration getInstance() {

		if (instance == null)
			instance = new IcnConfiguration();

		return instance;

	}

	public IcnConfiguration() {

		try {
			props.load(this.getClass()
					.getResourceAsStream("/icnApp.properties"));
		} catch (IOException e) {
			props.setProperty("virtualIP", "10.0.99.99"); // default
			e.printStackTrace();
		}
		
		fillDpidToSwitchMap();

	}

	private void fillDpidToSwitchMap() {
		
		dpidToSwitch.put(getS1(), "s1");
		dpidToSwitch.put(getS2(), "s2");
		dpidToSwitch.put(getS3(), "s3");
		dpidToSwitch.put(getS4(), "s4");
		dpidToSwitch.put(getS5(), "s5");
		dpidToSwitch.put(getS6(), "s6");
		dpidToSwitch.put(getS7(), "s7");
		
	}
	
	public String getSwitchFromDpid(String dpid) {
		return dpidToSwitch.get(dpid);
	}

	public String getVirtualIP() {
		return props.getProperty("virtualIP");
	}

	public String getS1() {
		return props.getProperty("s1");
	}

	public String getS2() {
		return props.getProperty("s2");
	}

	public String getS3() {
		return props.getProperty("s3");
	}

	public String getS4() {
		return props.getProperty("s4");
	}

	public String getS5() {
		return props.getProperty("s5");
	}

	public String getS6() {
		return props.getProperty("s6");
	}

	public String getS7() {
		return props.getProperty("s7");
	}
	
	public Integer getMaxShortestRoutes() {
		return Integer.parseInt(props.getProperty("maxRoutes"));
	}
	
	public Integer getRouteLengthDelta() {
		return Integer.parseInt(props.getProperty("lengthDelta"));
	}
	
	public Integer getPathLengthAspirationLevel() {
		return Integer.parseInt(props.getProperty("pathLengthAL"));
	}
	
	public Integer getPathLenghtReservationLevel() {
		return Integer.parseInt(props.getProperty("pathLengthRL"));
	}
	
	public Integer getBandwidthAspirationLevel() {
		return Integer.parseInt(props.getProperty("bandwidthAL"));
	}
	
	public Integer getBandwidthReservationLevel() {
		return Integer.parseInt(props.getProperty("bandwidthRL"));
	}
	
	public String getMacForIp(String ip) {
		return props.getProperty(ip);
	}
	

}
