package icn.core;

import java.util.List;

public class ContentDesc {
	
	private String contentId;
	private List<Location> locations;
	private String type;
	
	public ContentDesc(String contentId, List<Location> locations,
			String type) {
		super();
		this.contentId = contentId;
		this.locations = locations;
		this.type = type;
	}

	@Override
	public String toString() {
		return "ContentDesc [contentId=" + contentId + ", locations="
				+ locations + ", type=" + type + "]";
	}

	public List<Location> getLocations() {
		return locations;
	}

	public void setLocations(List<Location> locations) {
		this.locations = locations;
	}

	public String getContentId() {
		return contentId;
	}

	public void setContentId(String contentId) {
		this.contentId = contentId;
	}

	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public static class Location {
		
		public final String ipAddr;
		public final String localPath;
		
		public Location(String ipAddr, String localPath) {
			super();
			this.ipAddr = ipAddr;
			this.localPath = localPath;
		}

		public String getIpAddr() {
			return ipAddr;
		}

		public String getLocalPath() {
			return localPath;
		}
		
		
	}
	
	

}
