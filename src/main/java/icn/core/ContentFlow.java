package icn.core;

import java.util.List;

import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.match.Match;

import com.fasterxml.jackson.dataformat.yaml.snakeyaml.nodes.NodeTuple;

public class ContentFlow {
	
	
	private int flowId;
	private Match flowMatch;
	private List<NodePortTuple> route;

	public ContentFlow(int flowId) {
		super();
		
		this.flowId = flowId;
	}

	public List<NodePortTuple> getRoute() {
		return route;
	}

	public void setRoute(List<NodePortTuple> route) {
		this.route = route;
	}
	
	public int getFlowId() {
		return flowId;
	}
	public void setFlowId(int flowId) {
		this.flowId = flowId;
	}
	public Match getFlowMatch() {
		return flowMatch;
	}
	public void setFlowMatch(Match flowMatch) {
		this.flowMatch = flowMatch;
	}
	

}
