package net.floodlightcontroller.multipathrouting.types;

import icn.core.IcnModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.NodePortTuple;

public class MultiRoute {
    protected int routeCount;
    protected int routeSize;
    protected ArrayList<Route> routes;

    public MultiRoute() {
        routeCount = 0;
        routeSize = 0;
        routes = new ArrayList<Route>();
    }

	public ArrayList<Route> getRoutes() {
		
		List<Route> tmp = new ArrayList<Route>();
		for(Route route : routes) {
			tmp.add(IcnModule.statisticsService.getRouteWithCost(route));
		}
		
		routes.clear();
		routes.addAll(tmp);
		
		return routes;
	}

    public Route getRoute() {
//        routeCount = (routeCount+1)%routeSize;
//        return routes.get(routeCount);
    	Random rand = new Random();
    	return routes.get(rand.nextInt(routeSize));
    }
    
    public Route getRoute(OFPort srcPort, OFPort dstPort) {
    	
    	
    	List<NodePortTuple> nptList = null;
        NodePortTuple npt;
        Route r = getRoute();
        
        if (r != null) {
            nptList= new ArrayList<NodePortTuple>(r.getPath());
        }
        
        DatapathId srcId = nptList.get(0).getNodeId();
        DatapathId dstId = nptList.get(nptList.size()-1).getNodeId();
        
        npt = new NodePortTuple(srcId, srcPort);
        nptList.add(0, npt); // add src port to the front
        npt = new NodePortTuple(dstId, dstPort);
        nptList.add(npt); // add dst port to the end
    	
        RouteId id = new RouteId(srcId, dstId);
        r = new Route(id, nptList);
        return r;
        
    }

    public int getRouteCount() {
        return routeCount;
    }

    public int getRouteSize() {
        return routeSize;
    }

    public void addRoute(Route route) {
        routeSize++;
        routes.add(route);
    }
}
