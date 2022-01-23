package main.java.it.vaimee.dasi.publisher;

public class ObservationUpdate {
	private String observationID;
	private String timestamp;
	private String temperature;
	
	public ObservationUpdate(String observationID, String timestamp, String temperature) {
		super();
		this.observationID = observationID;
		this.timestamp = timestamp;
		this.temperature = temperature;
	}
	
	public String getObservationID() {
		return observationID;
	}
	
	public void setObservationID(String observationID) {
		this.observationID = observationID;
	}
	
	public String getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getTemperature() {
		return temperature;
	}
	
	public void setTemperature(String temperature) {
		this.temperature = temperature;
	}
	
}
