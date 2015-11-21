package icn.core;

import icn.core.ContentDesc.Location;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.forwarding.Forwarding;

public class Utils {

	public static Properties arpTable = new Properties();

	public static String getContentId(String payload) {

		String contentId = payload.substring(payload.indexOf("/"),
				payload.indexOf("HTTP")).replaceAll(" ", "");
		if (contentId.contains("/"))
			contentId = contentId.replaceAll("/", "");

		IcnModule.logger.info("ContenID: " + contentId);

		return contentId;
	}

	public static List<ContentServer> getContentServersInfo() {

		List<ContentServer> servers = new ArrayList<ContentServer>();

		try {
			File file = new File("src/main/resources/content_servers.xml");
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory
					.newDocumentBuilder();
			Document document = documentBuilder.parse(file);
			NodeList nodes = document.getElementsByTagName("server");

			for (int temp = 0; temp < nodes.getLength(); temp++) {

				Node node = nodes.item(temp);

				if (node.getNodeType() == Node.ELEMENT_NODE) {

					Element element = (Element) node;

					String name = element.getAttribute("name");

					MacAddress mac = MacAddress.of(element
							.getElementsByTagName("MacAddress").item(0)
							.getTextContent());
					IPv4Address ipAddr = IPv4Address.of(element
							.getElementsByTagName("IPv4Address").item(0)
							.getTextContent());
					DatapathId dpId = DatapathId.of(element
							.getElementsByTagName("SwitchDPID").item(0)
							.getTextContent());
					OFPort port = OFPort.of(Integer.parseInt(element
							.getElementsByTagName("SwitchPort").item(0)
							.getTextContent()));

					servers.add(new ContentServer(name, mac, ipAddr, dpId, port));

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return servers;

	}

	public static ContentDesc getContentDesc(String contentId) {

		String type = null;
		String description = null;
		List<Location> locations = new ArrayList<ContentDesc.Location>();

		try {
			File file = new File("src/main/resources/contents_db.xml");
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory
					.newDocumentBuilder();
			Document document = documentBuilder.parse(file);
			NodeList nodes = document.getElementsByTagName("content");

			for (int temp = 0; temp < nodes.getLength(); temp++) {

				Node node = nodes.item(temp);

				if (node.getNodeType() == Node.ELEMENT_NODE) {

					Element element = (Element) node;

					if (element.getAttribute("id").equals(contentId)) {

						type = element.getElementsByTagName("type").item(0)
								.getTextContent();
						description = element
								.getElementsByTagName("description").item(0)
								.getTextContent();

						NodeList locationNodes = element
								.getElementsByTagName("location");
						for (int tmp = 0; tmp < locationNodes.getLength(); tmp++) {

							Node locationNode = locationNodes.item(tmp);
							if (locationNode.getNodeType() == Node.ELEMENT_NODE) {
								Element elmnt = (Element) locationNode;
								String ipAddr = elmnt
										.getAttribute("address");
								String path = elmnt
										.getAttribute("localPath");
								locations.add(new ContentDesc.Location(ipAddr,
										path));
							}

						}
						
						return new ContentDesc(contentId, locations, type);
					}
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;

	}

	public static List<DatapathId> getCSDatapathIds() {
		List<DatapathId> csDpIds = new ArrayList<DatapathId>();
		for (ContentServer cs : Utils.getContentServersInfo())
			csDpIds.add(cs.getDpId());

		return csDpIds;
	}

	public static MacAddress findMacByIP(IPv4Address targetProtocolAddress) {

		for (ContentServer cs : getContentServersInfo())
			if (cs.getIpAddr().equals(targetProtocolAddress))
				return cs.getMacAddress();

		IcnModule.logger.info("ARP TABLE: " + arpTable.toString());
		if (arpTable.containsKey(targetProtocolAddress))
			return MacAddress.of(arpTable.getProperty(targetProtocolAddress
					.toString()));

		return null;

	}

}
