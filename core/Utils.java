package icn.core;

import net.floodlightcontroller.forwarding.Forwarding;

public class Utils {

	public static String getContentId(String payload) {
	
		String contentId = payload.substring(payload.indexOf("/"), payload.indexOf("HTTP")).replaceAll(" ", "");
		if(contentId.contains("/"))
			contentId = contentId.replaceAll("/", "");
		
		IcnModule.logger.info("ContenID: " + contentId);
		
		return contentId;
	}

}
