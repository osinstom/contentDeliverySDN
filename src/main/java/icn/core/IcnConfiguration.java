package icn.core;

import java.io.IOException;
import java.util.Properties;

public class IcnConfiguration {
	
	public static IcnConfiguration instance = null;
	
	private Properties props = new Properties();
	
	public static IcnConfiguration getInstance() {
		
		if(instance==null)
			instance = new IcnConfiguration();
		
		return instance;
		
	}
	
	public IcnConfiguration() {
	
		try {
			props.load(this.getClass().getResourceAsStream("/icnApp.properties"));
		} catch (IOException e) {
			props.setProperty("virtualIP", "10.0.99.99"); //default
			e.printStackTrace();
		}
	
	}
	
	public String getVirtualIP() {
		return props.getProperty("virtualIP");
		
	}
	
}
