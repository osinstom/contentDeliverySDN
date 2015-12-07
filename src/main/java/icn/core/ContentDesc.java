package icn.core;

import java.util.List;

public class ContentDesc {
	
	private String contentId;
	private List<Location> locations;
	private Long bandwidth;
	
	public ContentDesc(String contentId, List<Location> locations,
			String bandwidth) {
		super();
		this.contentId = contentId;
		this.locations = locations;
		this.bandwidth = Long.parseLong(bandwidth);
	}

	@Override
	public String toString() {
		return "ContentDesc [contentId=" + contentId + ", locations="
				+ locations + ", bandwidth=" + bandwidth + "]";
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

	public Long getBandwidth() {
		return bandwidth;
	}

	public void setBandwidth(Long bandwidth) {
		this.bandwidth = bandwidth;
	}

	public static class Location {
		
		public final String ipAddr;
		public final String localPath;
		private boolean isLoaded;
		
		public boolean isLoaded() {
			return isLoaded;
		}

		public void setLoaded(boolean isLoaded) {
			this.isLoaded = isLoaded;
		}

		public Location(String ipAddr, String localPath, boolean isLoaded) {
			super();
			this.ipAddr = ipAddr;
			this.localPath = localPath;
			this.isLoaded = isLoaded;
		}

		public String getIpAddr() {
			return ipAddr;
		}

		public String getLocalPath() {
			return localPath;
		}
		
		
	}
	
	

}
