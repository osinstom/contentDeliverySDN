package net.floodlightcontroller.multipathrouting;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.multipathrouting.types.LinkWithCost;
import net.floodlightcontroller.multipathrouting.types.MultiRoute;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public interface IMultiPathRoutingService extends IFloodlightService  {
    public void modifyLinkCost(DatapathId srcDpid,DatapathId dstDpid,short cost);
    public Route getRoute(DatapathId srcDpid,OFPort srcPort,DatapathId dstDpid,OFPort dstPort);
	public MultiRoute getMultiRoute(DatapathId srcDpid, DatapathId dstDpid);
	public void modifyLinkCost(DatapathId dpid, OFPort port, int cost);
	List<Route> getAllRoutes(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid, OFPort dstPort, int minBand);
	
}
