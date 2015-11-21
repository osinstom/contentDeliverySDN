package icn.core;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class MonitoringSystem {
	
	public static List<ContentFlow> flows = new ArrayList<ContentFlow>();
	
	public static MonitoringSystem instance = null;
	
	public static MonitoringSystem getInstance() {
		if(instance == null)
			instance = new MonitoringSystem();
		
		return instance;
	}

	public List<Integer> getFlowIds() {
		List<Integer> list = new ArrayList();
		for(ContentFlow flow : flows)
			list.add(flow.getFlowId());
		
		return list;
	}
}
