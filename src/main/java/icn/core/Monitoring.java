package icn.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.util.concurrent.ListenableFuture;

public class Monitoring implements IOFMessageListener {

	public static Map<String, ConcurrentLinkedQueue<ContentFlow>> flows = new ConcurrentHashMap<String, ConcurrentLinkedQueue<ContentFlow>>();

	public static Monitoring instance = null;

	private IOFSwitchService switchService;
	private ITopologyService topologyService;
	private IStatisticsService statisticsService;

	public static final int minFlowId = 49152;
	public static final int maxFlowId = 65535;
	
	List<Double> measures = new ArrayList<Double>();

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
				// requestStats();
				//getStats();
			}
		};
		t.start();

	}

	public void getStats() {

		while (true) {
			Map<NodePortTuple, SwitchPortBandwidth> stats = statisticsService
					.getBandwidthConsumption();

			for (NodePortTuple npt : stats.keySet()) {
				if (npt.getNodeId().equals(
						DatapathId.of("00:00:00:00:00:00:00:01"))
						&& npt.getPortId().equals(OFPort.of(2))) {

					IcnModule.logger.info("Bandwidth for link 01-02: \n"
							+ "RX="
							+ stats.get(npt).getBitsPerSecondRx().getValue()
							+ ", TX="
							+ stats.get(npt).getBitsPerSecondTx().getValue());
					long bandwidth = stats.get(npt).getBitsPerSecondRx()
							.getValue()
							+ stats.get(npt).getBitsPerSecondTx().getValue();
					double dblBand = (double) (bandwidth / 1000);
					IcnModule.logger.info("Bandiwdth for link 1-2 and 2-1: "
							+ dblBand);

//					IcnModule.logger
//					.info("Before setting link cost= "
//							+ IcnModule.mpathRoutingService
//									.getLinkWithCosts(npt.getNodeId(),
//											npt.getPortId()).getCost());
//					LinkWithCost link = IcnModule.mpathRoutingService
//							.getLinkWithCosts(npt.getNodeId(), npt.getPortId());
//					IcnModule.mpathRoutingService.modifyLinkCost(
//							link.getSrcDpid(), link.getDstDpid(),
//							(short)(dblBand) );
//					IcnModule.logger
//							.info("After setting link cost= "
//									+ IcnModule.mpathRoutingService
//											.getLinkWithCosts(npt.getNodeId(),
//													npt.getPortId()).getCost());
					if (bandwidth != 0) {

						// counter++;
						// average = (average + dblBand)/counter;
						measures.add(dblBand);
						double sum = 0;
						for (double dbl : measures) {
							sum = sum + dbl;
						}
						double average = sum / measures.size();
						IcnModule.logger.info("Average bandwidth=" + average
								+ " kbit/s");
					}

				} else if (npt.getNodeId().equals(
						DatapathId.of("00:00:00:00:00:00:00:02"))
						&& npt.getPortId().equals(OFPort.of(2))) {
					IcnModule.logger.info("Bandwidth for link 02-01: \n"
							+ "RX=" + stats.get(npt).getBitsPerSecondRx()
							+ ", TX=" + stats.get(npt).getBitsPerSecondTx());
				}
			}

			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
			}
		}

	}

	public List<Integer> getFlowIds(String contentFlowId) {
		List<Integer> list = new ArrayList();
		if (flows.containsKey(contentFlowId))
			for (ContentFlow flow : flows.get(contentFlowId))
				list.add(flow.getFlowId());

		return list;
	}

	public void requestStats() {

		while (true) {
			IcnModule.logger.info("Requesting..");

			Map<DatapathId, Set<Link>> allLinks = topologyService.getAllLinks();

			for (DatapathId dpid : allLinks.keySet()) {

				for (Link link : allLinks.get(dpid)) {

					if (link.getMaxBandwidth() == 0) {
						OFPortDesc port = switchService.getSwitch(dpid)
								.getPort(link.getSrcPort());
						if (port != null)
							link.setMaxBandwidth(port.getCurrSpeed());
						IcnModule.logger.info("Current speed set to: "
								+ link.getMaxBandwidth() + " for link "
								+ link.toKeyString());
					}

					try {
						calculateCurrentBandwidth(link);
					} catch (Exception e) {

					}

				}

			}

			// for(DatapathId dpid : switchService.getAllSwitchDpids()) {
			//
			//
			//
			//
			//
			// Set<OFPort> portsWithLinks =
			// topologyService.getPortsWithLinks(dpid);
			//
			// for(OFPort port : portsWithLinks) {
			// IOFSwitch sw = switchService.getSwitch(dpid);
			//
			// OFPortStatsRequest req = sw.getOFFactory()
			// .buildPortStatsRequest()
			// .setPortNo(port).build();
			//
			// ListenableFuture<?> future = sw.writeStatsRequest(req);
			//
			// try {
			// @SuppressWarnings("unchecked")
			// List<OFPortStatsReply> stats = (List<OFPortStatsReply>) future
			// .get(10, TimeUnit.SECONDS);
			// for(OFPortStatsReply reply : stats) {
			// for(OFPortStatsEntry entry : reply.getEntries()) {
			//
			// }
			//
			// }
			// //IcnModule.logger.info(sw.getId() + " " + stats.toString());
			// } catch (InterruptedException | ExecutionException
			// | TimeoutException e) {
			// e.printStackTrace();
			// }
			// }
			//
			// }

			// Collection<Set<Link>> values = topologyService.getAllLinks()
			// .values();
			//
			// for (Set<Link> set : values) {
			// while (set.iterator().hasNext()) {
			// Link link = set.iterator().next();
			//
			// IOFSwitch sw = switchService.getSwitch(link.getSrc());
			// OFPortStatsRequest req = sw.getOFFactory()
			// .buildPortStatsRequest()
			// .setPortNo(link.getSrcPort()).build();
			//
			// ListenableFuture<?> future = sw.writeStatsRequest(req);
			//
			// try {
			// @SuppressWarnings("unchecked")
			// List<OFStatsReply> stats = (List<OFStatsReply>) future
			// .get(10, TimeUnit.SECONDS);
			// IcnModule.logger.info(sw.getId() + " " + stats.toString());
			// } catch (InterruptedException | ExecutionException
			// | TimeoutException e) {
			// e.printStackTrace();
			// }
			// }
			//
			// }

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private void calculateCurrentBandwidth(Link link) throws Exception {

		IOFSwitch sw = switchService.getSwitch(link.getSrc());

		OFPortStatsRequest req = sw.getOFFactory().buildPortStatsRequest()
				.setPortNo(link.getSrcPort()).build();

		ListenableFuture<?> future = sw.writeStatsRequest(req);

		try {
			@SuppressWarnings("unchecked")
			List<OFPortStatsReply> stats = (List<OFPortStatsReply>) future.get(
					10, TimeUnit.SECONDS);

			for (OFPortStatsReply reply : stats) {
				for (OFPortStatsEntry entry : reply.getEntries()) {
					long currTimestamp = new Date().getTime();
					long bytes = entry.getTxBytes().getValue()
							+ entry.getRxBytes().getValue();

					if (link.getPrevTimestamp() != 0
							&& link.getPrevBytes() != 0) {
						long equal = bytes - link.getPrevBytes();
						if (equal != 0) {
							IcnModule.logger.info("Link: " + link);
							IcnModule.logger.info("Odejmowanie bajtow: "
									+ bytes + " - " + link.getPrevBytes()
									+ " = " + equal);
							long timeEqual = currTimestamp
									- link.getPrevTimestamp();
							IcnModule.logger.info("ODejmowanie czasow: "
									+ currTimestamp + " - "
									+ link.getPrevTimestamp() + " = "
									+ timeEqual);
						}
						double bandwidth = (((bytes - link.getPrevBytes()) / (currTimestamp - link
								.getPrevTimestamp())) * 8) / 1000;
						if (bandwidth != 0) {
							IcnModule.logger.info("Bytes= " + bytes
									+ " PrevBytes= " + link.getPrevBytes());
							IcnModule.logger.info("Timestamp=" + currTimestamp
									+ " PrevTime=" + link.getPrevTimestamp());
							IcnModule.logger.info("Bandiwidth = " + bandwidth
									+ " for link " + link);
						}
						// link.setCurrBandwidth(bandwidth);

					}
					link.setPrevTimestamp(currTimestamp);
					link.setPrevBytes(bytes);

				}
			}

		} catch (Exception e) {
			throw e;
		}

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
		// IcnModule.logger.info("FLOW REMOVED " +
		// flowRemoved.getMatch().get(MatchField.TCP_SRC).getPort());
		String contentFlowId = flowRemoved.getMatch().get(MatchField.IPV4_SRC)
				.toString();
		
		if (getFlowIds(contentFlowId).contains(
				flowRemoved.getMatch().get(MatchField.TCP_SRC).getPort())
				|| getFlowIds(contentFlowId).contains(
						flowRemoved.getMatch().get(MatchField.TCP_DST)
								.getPort())) {
			ContentFlow toRemove = null;
			for (ContentFlow f : flows.get(contentFlowId))
				if (f.getFlowId() == flowRemoved.getMatch()
						.get(MatchField.TCP_SRC).getPort()
						|| f.getFlowId() == flowRemoved.getMatch()
								.get(MatchField.TCP_DST).getPort())
					toRemove = f;

			flows.get(contentFlowId).remove(toRemove);
		}

		return Command.CONTINUE;
	}

	public void setStatisticsService(IStatisticsService statisticsService) {
		this.statisticsService = statisticsService;

	}

}
