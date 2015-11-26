package icn.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.perfmon.PktInProcessingTime;
import net.floodlightcontroller.util.FlowModUtils;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

public class OFUtils {

	public static final int HTTP_NOTFOUND = 404;
	public static final int HTTP_BADREQUEST = 400;

	public static final short ACK_FLAG = (short) 0x010;

	public static final short FIN_ACK_FLAG = (short) 0x011;

	public static final short FIN_PSH_ACK_FLAG = (short) 0x019;

	public static final short PSH_ACK_FLAG = (short) 0x018;

	public static final short RST_FLAG = (short) 0x004;

	public static final short SYN_FLAG = (short) 0x002;

	public static final short SYN_ACK_FLAG = (short) 0x012;

	private static byte[] ackOptionsHeader = new byte[] { (byte) 0x01,
			(byte) 0x01, (byte) 0x08, (byte) 0x0a,
	// (byte) 0x00, (byte) 0x64, (byte) 0xf2, (byte) 0xae,
	// (byte) 0x00, (byte) 0x5a, (byte) 0x2c, (byte) 0x6e
	};

	public static void pushARP(IOFSwitch sw, Ethernet eth, OFMessage msg,
			MacAddress mac) {

		OFPacketIn pi = (OFPacketIn) msg;

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		IcnModule.logger.info("SRCMAC=" + eth.getSourceMACAddress());

		Ethernet l2 = new Ethernet();
		l2.setSourceMACAddress(mac);
		l2.setDestinationMACAddress(eth.getSourceMACAddress());
		l2.setEtherType(EthType.ARP);

		ARP l2_5 = new ARP();
		l2_5.setSenderHardwareAddress(mac);
		l2_5.setTargetHardwareAddress(((ARP) eth.getPayload())
				.getSenderHardwareAddress());
		l2_5.setSenderProtocolAddress(((ARP) eth.getPayload())
				.getTargetProtocolAddress());
		l2_5.setTargetProtocolAddress(((ARP) eth.getPayload())
				.getSenderProtocolAddress());
		l2_5.setProtocolType(((ARP) eth.getPayload()).getProtocolType());
		l2_5.setHardwareAddressLength(((ARP) eth.getPayload())
				.getHardwareAddressLength());
		l2_5.setProtocolAddressLength(((ARP) eth.getPayload())
				.getProtocolAddressLength());
		l2_5.setHardwareType(((ARP) eth.getPayload()).getHardwareType());
		l2_5.setOpCode(ArpOpcode.REPLY);

		l2.setPayload(l2_5);
		IcnModule.logger.info("" + l2_5.getTargetHardwareAddress());
		byte[] serializedData = l2.serialize();

		OFPacketOut po = sw
				.getOFFactory()
				.buildPacketOut()
				/* mySwitch is some IOFSwitch object */
				.setData(serializedData)
				.setActions(
						Collections.singletonList((OFAction) sw.getOFFactory()
								.actions().output(inPort, 0xffFFffFF)))
				.setInPort(OFPort.CONTROLLER).build();

		if (!Utils.arpTable.containsKey(((ARP) eth.getPayload())
				.getSenderProtocolAddress().toString()))
			Utils.arpTable.setProperty(((ARP) eth.getPayload())
					.getSenderProtocolAddress().toString(), ((ARP) eth
					.getPayload()).getSenderHardwareAddress().toString());

		sw.write(po);

		IcnModule.logger.info("ARP Response packet sent");

	}

	public static void insertHTTPDpiFlow(IOFSwitch sw) {

		OFFactory ofFactory = sw.getOFFactory();

		Builder match = ofFactory.buildMatch();

		// for(OFPortDesc port : sw.getPorts())
		// match.setExact(MatchField.IN_PORT, port.getPortNo());

		OFAction action = ofFactory.actions().output(OFPort.CONTROLLER,
				0xffFFffFF);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);

		OFFlowAdd httpGetFlow = ofFactory
				.buildFlowAdd()
				.setActions(actions)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(
						match
						// .setExact(MatchField.IN_PORT, OFPort.of(1))
						// .setExact(MatchField.IN_PORT, OFPort.ANY)
						.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IPV4_DST, IcnModule.VIP)
								.build())
				.setPriority(FlowModUtils.PRIORITY_VERY_HIGH).build();

		sw.write(httpGetFlow);

		IcnModule.logger.info("HTTP FLOW MOD added to switch " + sw.getId());
	}

	public static void sendSynAck(IOFSwitch sw, OFMessage msg, IPv4 ipv4,
			Ethernet eth, TCP tcp) {

		OFPacketIn pi = (OFPacketIn) msg;
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		byte[] tcpSynAck = generateTCPResponse(eth, ipv4, tcp, SYN_ACK_FLAG,
				null);
		sendPacketOut(sw, inPort, tcpSynAck);

	}

	public static byte[] generateTCPResponse(Ethernet eth, IPv4 ipv4, TCP tcp,
			short flag, Data l7) {

		Ethernet l2 = (Ethernet) eth.clone();
		l2.setDestinationMACAddress(eth.getSourceMACAddress());
		l2.setSourceMACAddress(eth.getDestinationMACAddress());

		IPv4 l3 = new IPv4();
		l3.setDestinationAddress(ipv4.getSourceAddress());
		l3.setSourceAddress(ipv4.getDestinationAddress());
		l3.setDiffServ(ipv4.getDiffServ());
		l3.setChecksum(ipv4.getChecksum());
		l3.setFlags(ipv4.getFlags());
		l3.setTtl((byte) 32);
		l3.setIdentification(ipv4.getIdentification());
		l3.resetChecksum();

		TCP l4 = (TCP) tcp.clone();

		l4.setDestinationPort(tcp.getSourcePort());
		l4.setSourcePort(tcp.getDestinationPort());
		l4.setWindowSize(tcp.getWindowSize());

		if (flag == ACK_FLAG) {
			byte[] payloadData = ((Data) tcp.getPayload()).getData();
			l4.setOptions(getAckOptions(tcp.getOptions()));
			l4.setAcknowledge(tcp.getSequence() + payloadData.length);
			l4.setSequence(tcp.getAcknowledge());
			l4.setFlags(ACK_FLAG);
		} else if (flag == PSH_ACK_FLAG) {
			byte[] payloadData = ((Data) tcp.getPayload()).getData();
			l4.setOptions(getAckOptions(tcp.getOptions()));
			l4.setAcknowledge(tcp.getSequence() + payloadData.length);
			l4.setSequence(tcp.getAcknowledge());
			l4.setFlags(PSH_ACK_FLAG);
		} else if (flag == SYN_ACK_FLAG) {
			l4.setAcknowledge(tcp.getSequence() + 1);
			l4.setOptions(getSYNACKOptions(tcp.getOptions()));
			l4.setFlags(SYN_ACK_FLAG);
		}

		l4.resetChecksum();
		l4.setPayload(null);
		l4.setPayload(l7);
		l3.setPayload(l4);
		l2.setPayload(l3);

		return l2.serialize();
	}

	public static void sendPacketOut(IOFSwitch sw, OFPort outputPort,
			byte[] data) {

		OFPacketOut po = sw
				.getOFFactory()
				.buildPacketOut()
				.setData(data)
				.setActions(
						Collections.singletonList((OFAction) sw.getOFFactory()
								.actions().output(outputPort, 0xffFFffFF)))
				.setInPort(OFPort.CONTROLLER).build();

		sw.write(po);
	}

	public static void redirectHttpRequest(IOFSwitch sw, OFMessage msg,
			IPv4 ipv4, Ethernet eth, TCP tcp, String srcIp, String dstUrl) {

		OFPacketIn pi = (OFPacketIn) msg;

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/1.1 302 Found\r\n");
		builder.append("Location: http://" + dstUrl + "\r\n");
		builder.append("Connection: Keep-Alive\r\n");

		builder.append("\r\n");
		String httpHeader = builder.toString();
		Data l7 = new Data();
		l7.setData(httpHeader.getBytes());

		byte[] tcpAck = generateTCPResponse(eth, ipv4, tcp, ACK_FLAG, null);
		sendPacketOut(sw, inPort, tcpAck);

		byte[] httpRedirect = generateTCPResponse(eth, ipv4, tcp, PSH_ACK_FLAG,
				l7);
		sendPacketOut(sw, inPort, httpRedirect);

	}

	private static byte[] getAckOptions(byte[] options) {

		ByteBuffer bb = ByteBuffer.wrap(options);
		int tsVal = -1;
		int tsecr = -1;

		while (bb.hasRemaining()) {
			byte kind = bb.get();

			if (kind != 1) {
				byte length = bb.get();

				if (kind != 8) {
					for (int i = 0; i < length - 2; i++) {
						bb.get();
					}
				} else {
					tsVal = bb.getInt();
					tsecr = bb.getInt();
				}
			}
		}

		byte[] newOptions = new byte[12];

		ByteBuffer newbb = ByteBuffer.wrap(newOptions);

		for (int i = 0; i < ackOptionsHeader.length; i++) {
			newbb.put(ackOptionsHeader[i]);
		}
		int temp = tsVal;
		tsVal = tsecr + 1;
		tsecr = temp;
		newbb.putInt(tsVal);
		newbb.putInt(tsecr);

		return newOptions;
	}

	private static byte[] getSYNACKOptions(byte[] options) {
		ByteBuffer bb = ByteBuffer.wrap(options);
		byte[] synDataOptions = new byte[options.length];
		ByteBuffer synbb = ByteBuffer.wrap(synDataOptions);

		while (bb.hasRemaining()) {
			byte kind = bb.get();
			synbb.put(kind);

			if (kind != 1) {
				byte length = bb.get();
				synbb.put(length);

				if (kind != 8) {
					for (int i = 0; i < length - 2; i++) {
						synbb.put(bb.get());
					}
				} else {
					int tsVal = bb.getInt();
					int tsecr = bb.getInt();

					synbb.putInt(Math.abs((int) System.currentTimeMillis()));
					synbb.putInt(tsVal);
				}
			}
		}
		return synDataOptions;
	}

	public static void installRule(IOFSwitch sw, Match match,
			OFPort outputPort, List<OFAction> actions) {

		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setMatch(match)
				.setOutPort(outputPort).setActions(actions).build();

		sw.write(flowAdd);

		IcnModule.logger.info("RULE INSTALLED: " + flowAdd.toString());

	}

	public static void returnHttpResponse(IOFSwitch sw, OFMessage msg,
			IPv4 ipv4, Ethernet eth, TCP tcp, int responseCode) {
		// TODO 400 response for bad ip address(!=10.0.99.99)
		OFPacketIn pi = (OFPacketIn) msg;

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		StringBuilder builder = new StringBuilder();
		if (responseCode == HTTP_NOTFOUND) {
			builder.append("HTTP/1.1 404 Not Found\r\n");
			builder.append("Connection: close\r\n");

			byte[] tcpAck = generateTCPResponse(eth, ipv4, tcp, ACK_FLAG, null);
			sendPacketOut(sw, inPort, tcpAck);
		} else if (responseCode == HTTP_BADREQUEST) {
			builder.append("HTTP/1.1 400 Bad Request\r\n");
			builder.append("Connection: close\r\n");
		}
		builder.append("\r\n");

		String httpHeader = builder.toString();
		Data l7 = new Data();
		l7.setData(httpHeader.getBytes());

		byte[] http = generateTCPResponse(eth, ipv4, tcp, PSH_ACK_FLAG, l7);
		sendPacketOut(sw, inPort, http);

	}

	public static void insertARPFlow(IOFSwitch sw) {

		OFFactory ofFactory = sw.getOFFactory();
		List<OFAction> actions = new ArrayList<OFAction>();
		// for(OFPortDesc portDesc : sw.getPorts())
		// {
		// OFAction action = ofFactory.actions().output(portDesc.getPortNo(),
		// 0xffFFffFF);
		// actions.add(action);
		// }

		OFAction action = ofFactory.actions().output(OFPort.FLOOD, 0xffFFffFF);
		actions.add(action);

		OFFlowAdd arpFlow = ofFactory
				.buildFlowAdd()
				.setActions(actions)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(
						ofFactory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.ARP)
								.setExact(MatchField.ARP_OP, ArpOpcode.REQUEST)
								.build())
				.setPriority(FlowModUtils.PRIORITY_VERY_HIGH).build();

		sw.write(arpFlow);
		IcnModule.logger.info("ARP FLOW INSTALLED");
	}

	public static void setNatFlow(IOFSwitch sw, OFMessage msg, IPv4Address srcIp,
			IPv4Address dstIp, TransportPort sourcePort,
			TransportPort destinationPort) {
		
		OFPacketIn pi = (OFPacketIn) msg;

		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		OFFactory ofFactory = sw.getOFFactory();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFOxms oxms = ofFactory.oxms();

		OFActionSetField setTcpDst = ofFactory
				.actions()
				.buildSetField()
				.setField(
						oxms.buildTcpDst().setValue(TransportPort.of(80))
								.build()).build();
		actions.add(setTcpDst);

		OFActionSetField setTcpSrc = ofFactory
				.actions()
				.buildSetField()
				.setField(
						oxms.buildTcpSrc().setValue(destinationPort)
								.build()).build();
		actions.add(setTcpSrc);
		
		OFPort output = null ;
		OFPort revOutput = null;
		
		for(ContentFlow flow : MonitoringSystem.flows) {
			if(flow.getFlowId() == destinationPort.getPort()) {
				IcnModule.logger.info(flow.getRoute().toString());
				output = flow.getRoute().get(1).getPortId();
				revOutput = flow.getRoute().get(0).getPortId();
			}
		}
		
		actions.add(ofFactory.actions().output(output, 0xffFFffFF));

		OFFlowAdd natFlow = ofFactory
				.buildFlowAdd()
				.setActions(actions)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(
						ofFactory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
								.setExact(MatchField.IPV4_SRC, srcIp)
								.setExact(MatchField.IPV4_DST, dstIp)
								.setExact(MatchField.TCP_SRC, sourcePort)
								.setExact(MatchField.TCP_DST, destinationPort)
								.build())
				.setPriority(1).build();

		
		ArrayList<OFMessage> messages = new ArrayList<OFMessage>();
		messages.add(natFlow);
		
		List<OFAction> revActions = new ArrayList<OFAction>();
		OFActionSetField setRevTcpDst = ofFactory
				.actions()
				.buildSetField()
				.setField(
						oxms.buildTcpDst().setValue(sourcePort)
								.build()).build();
		revActions.add(setRevTcpDst);

		OFActionSetField setRevTcpSrc = ofFactory
				.actions()
				.buildSetField()
				.setField(
						oxms.buildTcpSrc().setValue(destinationPort)
								.build()).build();
		revActions.add(setRevTcpSrc);
		
		revActions.add(ofFactory.actions().output(revOutput, 0xffFFffFF));
		
		OFFlowAdd revNatFlow = ofFactory
				.buildFlowAdd()
				.setActions(revActions)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(
						ofFactory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
								.setExact(MatchField.IPV4_SRC, dstIp)
								.setExact(MatchField.IPV4_DST, srcIp)
								.setExact(MatchField.TCP_SRC, TransportPort.of(80))
								.setExact(MatchField.TCP_DST, destinationPort)
								.build())
				.setPriority(1).build();
		messages.add(revNatFlow);
		
		OFPacketOut po = sw
				.getOFFactory()
				.buildPacketOut()
				.setData(((OFPacketIn)msg).getData())
				.setActions(actions)
				.setInPort(inPort).build();

		messages.add(po);
		sw.write(messages);
	}

}
