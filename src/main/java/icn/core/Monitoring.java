package icn.core;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsRequest.Builder;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyService;

public class Monitoring implements IOFMessageListener{

	
	public static Map<String, ConcurrentLinkedQueue<ContentFlow>> flows = new ConcurrentHashMap<String, ConcurrentLinkedQueue<ContentFlow>>();
	
	public static Monitoring instance = null;

	private IOFSwitchService switchService;

	private ILinkDiscoveryService linkDiscoveryService;

	private ITopologyService topologyService;

	public static Monitoring getInstance() {
		if (instance == null)
			instance = new Monitoring();

		return instance;
	}

	public void setSwitchService(IOFSwitchService switchService) {
		this.switchService = switchService;
	}

	public Monitoring() {

		IcnModule.logger.info("REQUEST!!");
		Thread t = new Thread() {
			public void run() {
				requestStats();
			}
		};
		t.start();
	}

	public List<Integer> getFlowIds(String contentFlowId) {
		List<Integer> list = new ArrayList();
		if(flows.containsKey(contentFlowId))
			for (ContentFlow flow : flows.get(contentFlowId))
					list.add(flow.getFlowId());

		return list;
	}

	public void requestStats() {

		while (true) {
			IcnModule.logger.info("Requesting..");
			
			Map<DatapathId, Set<Link>> allLinks = topologyService.getAllLinks();
			
			for(DatapathId dpid : allLinks.keySet()) {
				
				for(Link link : allLinks.get(dpid)) {
					
					if(link.getMaxBandwidth() == 0) {
						OFPortDesc port = switchService.getSwitch(dpid).getPort(link.getSrcPort());
						if(port!=null)
							link.setMaxBandwidth(port.getCurrSpeed());
						IcnModule.logger.info("Current speed set to: " + link.getMaxBandwidth() + " for link " + link.toKeyString());
					}
					
					try {
						calculateCurrentBandwidth(link);
					} catch (Exception e) {
						
					}
					
				}
				
			}
			
			

//			for(DatapathId dpid : switchService.getAllSwitchDpids()) {
//				
//				
//				
//				
//				
//				Set<OFPort> portsWithLinks = topologyService.getPortsWithLinks(dpid);
//				
//				for(OFPort port : portsWithLinks) {
//					IOFSwitch sw = switchService.getSwitch(dpid);
//					
//					OFPortStatsRequest req = sw.getOFFactory()
//							.buildPortStatsRequest()
//							.setPortNo(port).build();
//					
//					ListenableFuture<?> future = sw.writeStatsRequest(req);
//					
//					try {
//						@SuppressWarnings("unchecked")
//						List<OFPortStatsReply> stats = (List<OFPortStatsReply>) future
//								.get(10, TimeUnit.SECONDS);
//						for(OFPortStatsReply reply : stats) {
//							for(OFPortStatsEntry entry : reply.getEntries()) {
//								
//							}
//							
//						}
//						//IcnModule.logger.info(sw.getId() + " " + stats.toString());
//					} catch (InterruptedException | ExecutionException
//							| TimeoutException e) {
//						e.printStackTrace();
//					}
//				}
//				
//			}

//			Collection<Set<Link>> values = topologyService.getAllLinks()
//					.values();
//
//			for (Set<Link> set : values) {
//				while (set.iterator().hasNext()) {
//					Link link = set.iterator().next();
//
//					IOFSwitch sw = switchService.getSwitch(link.getSrc());
//					OFPortStatsRequest req = sw.getOFFactory()
//							.buildPortStatsRequest()
//							.setPortNo(link.getSrcPort()).build();
//
//					ListenableFuture<?> future = sw.writeStatsRequest(req);
//
//					try {
//						@SuppressWarnings("unchecked")
//						List<OFStatsReply> stats = (List<OFStatsReply>) future
//								.get(10, TimeUnit.SECONDS);
//						IcnModule.logger.info(sw.getId() + " " + stats.toString());
//					} catch (InterruptedException | ExecutionException
//							| TimeoutException e) {
//						e.printStackTrace();
//					}
//				}
//
//			}

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private void calculateCurrentBandwidth(Link link) throws Exception {
		
		IOFSwitch sw = switchService.getSwitch(link.getSrc());
		
		OFPortStatsRequest req = sw.getOFFactory()
				.buildPortStatsRequest()
				.setPortNo(link.getSrcPort()).build();
		
		ListenableFuture<?> future = sw.writeStatsRequest(req);
		
		try {
			@SuppressWarnings("unchecked")
			List<OFPortStatsReply> stats = (List<OFPortStatsReply>) future
					.get(10, TimeUnit.SECONDS);
			
			for(OFPortStatsReply reply : stats) {
				for(OFPortStatsEntry entry : reply.getEntries()) {
					long currTimestamp = new Date().getTime();
					long bytes = entry.getTxBytes().getValue() + entry.getRxBytes().getValue(); 
					
					if(link.getPrevTimestamp()!=0 && link.getPrevBytes()!=0) {
						long equal = bytes - link.getPrevBytes();
						if(equal != 0) {
						IcnModule.logger.info("Link: " + link);
						IcnModule.logger.info("Odejmowanie bajtow: " + bytes + " - " + link.getPrevBytes() + " = " + equal);
						long timeEqual = currTimestamp - link.getPrevTimestamp();
						IcnModule.logger.info("ODejmowanie czasow: " + currTimestamp + " - " + link.getPrevTimestamp() + " = " + timeEqual);
						}
						double bandwidth = (((bytes - link.getPrevBytes())/(currTimestamp - link.getPrevTimestamp())) * 8 )/ 1000;
						if(bandwidth!=0) {
							IcnModule.logger.info("Bytes= " + bytes + " PrevBytes= " + link.getPrevBytes());
							IcnModule.logger.info("Timestamp=" + currTimestamp + " PrevTime=" + link.getPrevTimestamp());
							IcnModule.logger.info("Bandiwidth = " + bandwidth + " for link " + link);
						}
						//link.setCurrBandwidth(bandwidth);
						
					} 
					link.setPrevTimestamp(currTimestamp);
					link.setPrevBytes(bytes);
					
				}
			}
			
			
		} catch (Exception e) { throw e; }
		
		
	}

	public void setLinkDiscoveryService(
			ILinkDiscoveryService linkDiscoveryService) {
		this.linkDiscoveryService = linkDiscoveryService;

	}

	public void setTopologyService(ITopologyService topologyService) {
		this.topologyService = topologyService;

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		OFFlowRemoved flowRemoved = (OFFlowRemoved) msg;
		//IcnModule.logger.info("FLOW REMOVED " + flowRemoved.getMatch().get(MatchField.TCP_SRC).getPort());
		String contentFlowId = flowRemoved.getMatch().get(MatchField.IPV4_SRC).toString() + ":" + flowRemoved.getMatch().get(MatchField.IPV4_DST).toString();
		if(getFlowIds(contentFlowId).contains(flowRemoved.getMatch().get(MatchField.TCP_SRC).getPort()) || getFlowIds(contentFlowId).contains(flowRemoved.getMatch().get(MatchField.TCP_DST).getPort())) {
			ContentFlow toRemove = null;
			for(ContentFlow f : flows.get(contentFlowId))
				if(f.getFlowId() == flowRemoved.getMatch().get(MatchField.TCP_SRC).getPort() || f.getFlowId() == flowRemoved.getMatch().get(MatchField.TCP_DST).getPort())
					toRemove = f;
			
			flows.get(contentFlowId).remove(toRemove);
		}
		
		return Command.CONTINUE;
	}

}
