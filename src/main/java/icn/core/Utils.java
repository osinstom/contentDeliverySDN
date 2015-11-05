package icn.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

	public static String getContentId(String payload) {
	
		String contentId = payload.substring(payload.indexOf("/"), payload.indexOf("HTTP")).replaceAll(" ", "");
		if(contentId.contains("/"))
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
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		Document document = documentBuilder.parse(file);
		NodeList nodes = document.getElementsByTagName("server");
		
		for (int temp = 0; temp < nodes.getLength(); temp++) {

	        Node node = nodes.item(temp);

	        if (node.getNodeType() == Node.ELEMENT_NODE) {

	            Element element = (Element) node;

	            String name = element.getAttribute("name");
	            IcnModule.logger.info("MAC: " + element.getElementsByTagName("MacAddress").item(0).getTextContent());
	            MacAddress mac = MacAddress.of(element.getElementsByTagName("MacAddress").item(0).getTextContent());
	            IPv4Address ipAddr = IPv4Address.of(element.getElementsByTagName("IPv4Address").item(0).getTextContent());
	            DatapathId dpId = DatapathId.of(element.getElementsByTagName("SwitchDPID").item(0).getTextContent());
	            OFPort port = OFPort.of(Integer.parseInt(element.getElementsByTagName("SwitchPort").item(0).getTextContent()));
	            
	            servers.add(new ContentServer(name, mac, ipAddr, dpId, port));

	        }
	    }
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return servers;
		
	}

}
