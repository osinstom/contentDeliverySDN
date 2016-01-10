package net.floodlightcontroller.multipathrouting;

import icn.core.IcnConfiguration;
import icn.core.IcnModule;
import icn.core.NoNetworkResourcesException;
import icn.core.Utils;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.multipathrouting.types.FlowId;
import net.floodlightcontroller.multipathrouting.types.LinkWithCost;
import net.floodlightcontroller.multipathrouting.types.MultiRoute;
import net.floodlightcontroller.multipathrouting.types.NodeCost;
import net.floodlightcontroller.restserver.IRestApiService;

import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class MultiPathRouting implements IFloodlightModule, ITopologyListener,
		IMultiPathRoutingService {
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topologyService;
	protected IRestApiService restApi;

	protected final int ROUTE_LIMITATION = 10;
	protected HashMap<DatapathId, HashSet<LinkWithCost>> dpidLinks;
	protected int pathCount = 0;

	protected class FlowCacheLoader extends CacheLoader<FlowId, Route> {
		MultiPathRouting mpr;

		FlowCacheLoader(MultiPathRouting mpr) {
			this.mpr = mpr;
		}

		@Override
		public Route load(FlowId fid) {
			return mpr.buildFlowRoute(fid);
		}
	}

	private final FlowCacheLoader flowCacheLoader = new FlowCacheLoader(this);
	protected LoadingCache<FlowId, Route> flowcache;

	protected class PathCacheLoader extends CacheLoader<RouteId, MultiRoute> {
		MultiPathRouting mpr;

		PathCacheLoader(MultiPathRouting mpr) {
			this.mpr = mpr;
		}

		@Override
		public MultiRoute load(RouteId rid) {
			return mpr.buildMultiRoute(rid);
		}
	}

	private final PathCacheLoader pathCacheLoader = new PathCacheLoader(this);
	protected LoadingCache<RouteId, MultiRoute> pathcache;

	protected class AllPathCacheLoader extends
			CacheLoader<RouteId, List<Route>> {
		MultiPathRouting mpr;

		AllPathCacheLoader(MultiPathRouting mpr) {
			this.mpr = mpr;
		}

		@Override
		public List<Route> load(RouteId rid) {
			return mpr.findAllPaths(rid);
		}
	}

	private final AllPathCacheLoader allPathCacheLoader = new AllPathCacheLoader(
			this);
	protected LoadingCache<RouteId, List<Route>> allPathCache;

	protected static Stack<NodePortTuple> route = new Stack<NodePortTuple>();
	protected static ArrayList<Stack<NodePortTuple>> routesList = new ArrayList<Stack<NodePortTuple>>();
	protected static Stack<DatapathId> nvisited = new Stack<DatapathId>();

	//
	//
	// ITopologyListener
	//
	//
	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		for (LDUpdate update : linkUpdates) {

			if (update.getOperation().equals(
					ILinkDiscovery.UpdateOperation.LINK_REMOVED)
					|| update.getOperation().equals(
							ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
				LinkWithCost srcLink = new LinkWithCost(update.getSrc(),
						update.getSrcPort(), update.getDst(),
						update.getDstPort(), 1);
				LinkWithCost dstLink = srcLink.getInverse();

				if (update.getOperation().equals(
						ILinkDiscovery.UpdateOperation.LINK_REMOVED)) {
					removeLink(srcLink);
					removeLink(dstLink);
					clearRoutingCache();
				} else if (update.getOperation().equals(
						ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
					addLink(srcLink);
					addLink(dstLink);
				}
			}
		}

	}

	public List<Route> findAllPaths(RouteId rid) {

		DatapathId srcDpid = rid.getSrc();
		DatapathId dstDpid = rid.getDst();

		ArrayList<Route> routes = new ArrayList<Route>();

		route.clear();
		routesList.clear();
		nvisited.clear();

		this.searchDfs(srcDpid, dstDpid, nvisited);
		for (Stack<NodePortTuple> r : routesList) {
			ArrayList<NodePortTuple> ports = new ArrayList<NodePortTuple>();
			for (NodePortTuple np : r) {
				ports.add(np);
			}
			Route path = new Route(srcDpid, dstDpid);
			path.setPath(ports);
			routes.add(path);
		}

		return routes;

	}

	private void searchDfs(DatapathId current, DatapathId target,
			Stack<DatapathId> visited) {

		// To avoid loops
		if (visited.contains(current) && !current.equals(target)) {
			// curren node has been already visited
			return;
		}

		if (current.equals(target)) {
			// Target has been reached -> add the route to the routes list
			Stack<NodePortTuple> temp = new Stack<NodePortTuple>();
			for (NodePortTuple np : route) {
				temp.add(np);
			}
			routesList.add(temp);
			return;
		}

		nvisited.push(current);

		HashMap<DatapathId, HashSet<LinkWithCost>> allLinks = dpidLinks;
		for (LinkWithCost l : allLinks.get(current)) {
			// There are two links (bidirectional link)
			if (l.getDstDpid().equals(current)) {
				// we only need one of the two links to get to the next node
				continue;
			}

			// go to the next node into DFS subtree
			route.push(new NodePortTuple(l.getSrcDpid(), l.getSrcPort()));
			route.push(new NodePortTuple(l.getDstDpid(), l.getDstPort()));
			searchDfs(l.getDstDpid(), target, nvisited);
			// search in subtree is completed -> return to upper level
			route.pop();
			route.pop();
		}

		nvisited.pop();
	}

	public void clearRoutingCache() {
		flowcache.invalidateAll();
		pathcache.invalidateAll();
		allPathCache.invalidateAll();
	}

	public void removeLink(LinkWithCost link) {
		DatapathId dpid = link.getSrcDpid();

		if (null == dpidLinks.get(dpid)) {
			return;
		}

		dpidLinks.get(dpid).remove(link);
		if (0 == dpidLinks.get(dpid).size())
			dpidLinks.remove(dpid);
	}

	public void addLink(LinkWithCost link) {
		DatapathId dpid = link.getSrcDpid();

		if (null == dpidLinks.get(dpid)) {
			HashSet<LinkWithCost> links = new HashSet<LinkWithCost>();
			links.add(link);
			dpidLinks.put(dpid, links);
		} else {
			dpidLinks.get(dpid).add(link);
		}
	}

	public Route buildFlowRoute(FlowId fid) {
		DatapathId srcDpid = fid.getSrc();
		DatapathId dstDpid = fid.getDst();
		OFPort srcPort = fid.getSrcPort();
		OFPort dstPort = fid.getDstPort();

		List<NodePortTuple> nptList;
		NodePortTuple npt;
		MultiRoute routes = null;
		Route result = null;

		try {
			routes = pathcache.get(new RouteId(srcDpid, dstDpid));
		} catch (Exception e) {
			logger.error("error {}", e.toString());
		}

		if (0 == routes.getRouteSize()) {
			result = null;
		} else {
			result = routes.getRoute();
		}

		if (result != null) {
			nptList = new ArrayList<NodePortTuple>(result.getPath());
		} else {
			nptList = new ArrayList<NodePortTuple>();
		}

		npt = new NodePortTuple(srcDpid, srcPort);
		nptList.add(0, npt);
		npt = new NodePortTuple(dstDpid, dstPort);
		nptList.add(npt);

		result = new Route(new RouteId(srcDpid, dstDpid), nptList);
		return result;
	}

	public MultiRoute buildMultiRoute(RouteId rid) {
		return computeMultiPath(rid);
	}

	public MultiRoute computeMultiPath(RouteId rid) {
		DatapathId srcDpid = rid.getSrc();
		DatapathId dstDpid = rid.getDst();
		MultiRoute routes = new MultiRoute();

		if (srcDpid == dstDpid) {
			return routes;
		}

		if (null == dpidLinks.get(srcDpid) || null == dpidLinks.get(dstDpid)) {
			return routes;
		}

		HashMap<DatapathId, HashSet<LinkWithCost>> previous = new HashMap<DatapathId, HashSet<LinkWithCost>>();
		HashMap<DatapathId, HashSet<LinkWithCost>> links = dpidLinks;

		for (DatapathId dpid : links.keySet()) {
			IcnModule.logger.info("DPID: " + dpid);
			IcnModule.logger.info("Links: " + links.get(dpid));
		}
		HashMap<DatapathId, Integer> costs = new HashMap<DatapathId, Integer>();

		for (DatapathId dpid : links.keySet()) {
			costs.put(dpid, Integer.MAX_VALUE);
			previous.put(dpid, new HashSet<LinkWithCost>());
		}

		PriorityQueue<NodeCost> nodeq = new PriorityQueue<NodeCost>();
		HashSet<DatapathId> seen = new HashSet<DatapathId>();
		nodeq.add(new NodeCost(srcDpid, 0));
		NodeCost node;

		while (null != nodeq.peek()) {
			node = nodeq.poll();
			if (node.getDpid() == dstDpid) {
				break;
			}

			IcnModule.logger.info("Node: " + node.getDpid());
			int cost = node.getCost();
			seen.add(node.getDpid());

			for (LinkWithCost link : links.get(node.getDpid())) {
				DatapathId dst = link.getDstDpid();
				int totalCost = link.getCost() + cost;
				IcnModule.logger.info("DPID=" + link.getDstDpid());
				IcnModule.logger.info("Link: " + link.getInverse());
				if (true == seen.contains(dst)) {
					IcnModule.logger.info("SEEN");
					continue;
				}

				if (totalCost < costs.get(dst)) {
					costs.put(dst, totalCost);
					previous.get(dst).clear();
					previous.get(dst).add(link.getInverse());
					IcnModule.logger.info("Adding: ");
					IcnModule.logger.info("Shortest");
					NodeCost ndTemp = new NodeCost(dst, totalCost);
					nodeq.remove(ndTemp);
					nodeq.add(ndTemp);
				} else if (totalCost == costs.get(dst)) {
					// multiple path
					IcnModule.logger.info("multiple paths");
					previous.get(dst).add(link.getInverse());

					IcnModule.logger.info("Adding: ");
				}
				// else if (totalCost > costs.get(dst)) {
				// // all path
				// IcnModule.logger.info("all paths");
				// previous.get(dst).add(link.getInverse());
				// IcnModule.logger.info("Adding: ");
				// NodeCost ndTemp = new NodeCost(dst,totalCost);
				// nodeq.remove(ndTemp);
				// nodeq.add(ndTemp);
				// }

			}

		}

		LinkedList<NodePortTuple> switchPorts = new LinkedList<NodePortTuple>();
		pathCount = 0;
		generateMultiPath(routes, srcDpid, dstDpid, dstDpid, previous,
				switchPorts);

		return routes;
	}

	public void generateMultiPath(MultiRoute routes, DatapathId srcDpid,
			DatapathId dstDpid, DatapathId current,
			HashMap<DatapathId, HashSet<LinkWithCost>> previous,
			LinkedList<NodePortTuple> switchPorts) {
		if (pathCount >= ROUTE_LIMITATION) {
			IcnModule.logger.info("Wracam if 1");
			return;
		}

		if (current == srcDpid) {
			pathCount++;
			Route result = new Route(new RouteId(srcDpid, dstDpid),
					new LinkedList<NodePortTuple>(switchPorts));
			routes.addRoute(result);
			IcnModule.logger.info("Wracam if 2");
			return;
		}
		IcnModule.logger.info("Choc raz");
		HashSet<LinkWithCost> links = previous.get(current);
		IcnModule.logger.info(links.toString());
		for (LinkWithCost link : links) {
			NodePortTuple npt = new NodePortTuple(link.getDstDpid(),
					link.getDstPort());
			NodePortTuple npt2 = new NodePortTuple(link.getSrcDpid(),
					link.getSrcPort());
			switchPorts.addFirst(npt2);
			switchPorts.addFirst(npt);
			generateMultiPath(routes, srcDpid, dstDpid, link.getDstDpid(),
					previous, switchPorts);
			switchPorts.removeFirst();
			switchPorts.removeFirst();

		}
		return;
	}

	private void updateLinkCost(DatapathId srcDpid, DatapathId dstDpid, int cost) {
		if (null != dpidLinks.get(srcDpid)) {
			for (LinkWithCost link : dpidLinks.get(srcDpid)) {
				if (link.getSrcDpid() == srcDpid
						&& link.getDstDpid() == dstDpid) {
					link.setCost(cost);
					return;
				}
			}
		}
	}

	//
	// IMultiPathRoutingService implement
	//
	//
	@Override
	public Route getRoute(DatapathId srcDpid, OFPort srcPort,
			DatapathId dstDpid, OFPort dstPort) {
		// Return null the route source and desitnation are the
		// same switchports.
		if (srcDpid == dstDpid && srcPort == dstPort) {
			return null;
		}

		FlowId id = new FlowId(srcDpid, srcPort, dstDpid, dstPort);
		Route result = null;

		try {
			result = flowcache.get(id);
		} catch (Exception e) {
			logger.error("error {}", e.toString());
		}

		if (result == null && srcDpid != dstDpid) {
			return null;
		}

		return result;
	}

	@Override
	public List<Route> getAllRoutes(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid, OFPort dstPort,
			int minBand, Integer routesCount, Integer routeLengthDelta){

		if (srcDpid == dstDpid) {
			return null;
		}
		RouteId rId = new RouteId(srcDpid, dstDpid);
		List<Route> result = null;
		try {
			result = allPathCache.get(rId);
		} catch (Exception e) {
			logger.error("error {}", e.toString());
		}
		
		for (Route route : result) {
			List<NodePortTuple> nptList = null;
			if (route != null) {
				NodePortTuple npt;

				if (route != null) {
					nptList = new ArrayList<NodePortTuple>(route.getPath());
				}

				DatapathId srcId = nptList.get(0).getNodeId();
				DatapathId dstId = nptList.get(nptList.size() - 1).getNodeId();

				npt = new NodePortTuple(srcId,
						srcPort);
				if (!nptList.get(0).equals(npt))
					nptList.add(0, npt); // add src port to the front
				npt = new NodePortTuple(dstId,
						dstPort);
				if (!nptList.get(nptList.size() - 1).equals(npt))
					nptList.add(npt); // add dst port to the end
			} else {
				// when srcSwid==dstSwId

			}
			route.setPath(nptList);
		}
		IcnModule.logger.info("before getshortest");
		result = getShortest(result, routesCount, routeLengthDelta);
		IcnModule.logger.info("after getshortest");
		
		ArrayList<Route> tmp = new ArrayList<Route>();
//		Route lowestCostRoute = null;
		//IcnModule.logger.info("Routes count: " + result.size());
		for (Route route : result) {
			IcnModule.logger.info(route.toString());
			Route r = IcnModule.statisticsService.getRouteWithCost(route);
			
			if (r.getBottleneckBandwidth() >= minBand)
				tmp.add(r);

//			if (lowestCostRoute == null)
//				lowestCostRoute = r;
//			if (r.getTotalCost() <= lowestCostRoute.getTotalCost()) {
//				if (r.getPath().size() < lowestCostRoute.getPath().size())
//					lowestCostRoute = r;
//			}
		}


		return tmp;
	}

	private List<Route> getShortest(List<Route> result, Integer routesCount,
			Integer routeLengthDelta) {
		
		Collections.sort(result, new PathComparator());
		List<Route> tmp = new ArrayList<Route>();
//		for(Route r: result)
//			IcnModule.logger.info(r.toString());
		List<Route> anothers = new ArrayList<Route>();
		
		int shortest = result.get(0).getPath().size();
		
		for(int i=0; i < result.size(); i++) {
			if(result.get(i).getPath().size() <= shortest) {
				tmp.add(result.get(i));
			} else if(result.get(i).getPath().size()/2 == (shortest/2) + IcnConfiguration.getInstance().getRouteLengthDelta()) {
				IcnModule.logger.info("adding route: " + result.get(i));
				anothers.add(result.get(i));
			}
			if(tmp.size()==IcnConfiguration.getInstance().getMaxShortestRoutes())
				break;
		}
		
		Random rand = new Random();
		while(tmp.size()!=IcnConfiguration.getInstance().getMaxShortestRoutes()) {
			if(anothers.isEmpty())
				break;
			
			Route tmpRoute  = anothers.get(rand.nextInt(anothers.size()));
			tmp.add(tmpRoute);
			anothers.remove(tmpRoute);
			
		}
		
		return tmp;
	}
	
	private class PathComparator implements Comparator<Route> {
		@Override
		public int compare(Route o1, Route o2) {
			return Integer.compare(o1.getPath().size(),o2.getPath().size());
		}
		
	}

	@Override
	public MultiRoute getMultiRoute(DatapathId srcDpid, DatapathId dstDpid) {

		if (srcDpid == dstDpid) {
			return null;
		}

		RouteId rId = new RouteId(srcDpid, dstDpid);
		MultiRoute result = null;

		try {
			result = pathcache.get(rId);
		} catch (Exception e) {
			logger.error("error {}", e.toString());
		}

		return result;
	}

	@Override
	public void modifyLinkCost(DatapathId srcDpid, DatapathId dstDpid,
			short cost) {
		updateLinkCost(srcDpid, dstDpid, cost);
		updateLinkCost(dstDpid, srcDpid, cost);

		clearRoutingCache();

	}

	@Override
	public void modifyLinkCost(DatapathId dpid, OFPort port, int cost) {

		LinkWithCost linkWithCosts = getLinkWithCosts(dpid, port);
		if (linkWithCosts == null)
			return;
		// IcnModule.logger.info("Cost for link [" +
		// linkWithCosts.getSrcDpid().toString() + ":" +
		// linkWithCosts.getDstDpid().toString() + "] = " + cost);
		updateLinkCost(linkWithCosts.getSrcDpid(), linkWithCosts.getDstDpid(),
				cost);
		updateLinkCost(linkWithCosts.getDstDpid(), linkWithCosts.getSrcDpid(),
				cost);

		clearRoutingCache();
	}

	//
	//
	// IFloodlightModule
	//
	//
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IMultiPathRoutingService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IMultiPathRoutingService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(ITopologyService.class);
		// l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		logger = LoggerFactory.getLogger(MultiPathRouting.class);
		dpidLinks = new HashMap<DatapathId, HashSet<LinkWithCost>>();

		flowcache = CacheBuilder.newBuilder().concurrencyLevel(4)
				.maximumSize(1000L).build(new CacheLoader<FlowId, Route>() {
					public Route load(FlowId fid) {
						return flowCacheLoader.load(fid);
					}
				});
		pathcache = CacheBuilder.newBuilder().concurrencyLevel(4)
				.maximumSize(1000L)
				.build(new CacheLoader<RouteId, MultiRoute>() {
					public MultiRoute load(RouteId rid) {
						return pathCacheLoader.load(rid);
					}
				});

		allPathCache = CacheBuilder.newBuilder().concurrencyLevel(4)
				.maximumSize(1000L)
				.build(new CacheLoader<RouteId, List<Route>>() {
					public List<Route> load(RouteId rid) {
						return allPathCacheLoader.load(rid);
					}
				});
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		topologyService.addListener(this);
		// restApi.addRestletRoutable(new MultiPathRoutingWebRoutable());
	}

	public LinkWithCost getLinkWithCosts(DatapathId dpid, OFPort port) {

		for (DatapathId d : dpidLinks.keySet()) {
			for (LinkWithCost link : dpidLinks.get(d)) {
				if (link.getSrcDpid().equals(dpid)
						&& link.getSrcPort().equals(port)) {
					return link;
				}
			}
		}

		return null;

	}
}
