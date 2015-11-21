package icn.core;

import org.projectfloodlight.openflow.protocol.match.Match;

public class ContentFlow {
	
	private int flowId;
	private Match flowMatch;
	
	public ContentFlow(int flowId) {
		super();
		this.flowId = flowId;
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
