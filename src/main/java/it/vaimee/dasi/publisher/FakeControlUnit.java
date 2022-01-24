package it.vaimee.dasi.publisher;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

public class FakeControlUnit {
	private final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
	private int timeStep = 0;
	private final Calendar currentTime = Calendar.getInstance(UTC_TZ);
	private final DateFormat xsdDatetimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private final int PERIOD_SEC = 60 * 60; // in seconds
	private final int TIME_INTERVAL_SEC = 5; // in seconds
	private final double AVERAGE_VALUE = 75.0; // in Celsius degrees
	private final double VARIATION_AMPLITUDE = 50.0; // in Celsius degrees

	private String COMPANY_ID = "vaimeesrl";
	private String SENSOR_A_ID = "0000.2.1"; // ProbeA for transformer "13101974.0.0"
	private String SENSOR_B_ID = "0000.2.2"; // ProbeB for transformer "13101974.0.0"
	private String SENSOR_C_ID = "0000.2.3"; // ProbeC for transformer "13101974.0.0"
	private String SENSOR_D_ID = "0000.2.4"; // ProbeD for transformer "13101974.0.0"

	private String NGSI_LD_ENDPOINT = "https://ngsi-ld.dasibreaker.vaimee.it";
	
	private final String updateQueryTemplate = "{\n"
			+ "    \"id\": \"monas:${company}/observations/${observation}\",\n"
			+ "    \"type\": \"monas:Observation\",\n"
			+ "    \"resultTime\": {\n"
			+ "        \"type\": \"Property\",\n"
			+ "        \"value\": \"${timestamp}\"\n"
			+ "    },\n"
			+ "    \"hasSimpleResult\": {\n"
			+ "        \"type\": \"Property\",\n"
			+ "        \"value\": \"${temperature}\"\n"
			+ "    }\n"
			+ "}";

	public FakeControlUnit() {
		this.xsdDatetimeFormat.setTimeZone(UTC_TZ);
	}

	private void stepForward() throws InterruptedException {
		Thread.sleep(TIME_INTERVAL_SEC * 1000); // Sleep 5 seconds
		this.timeStep = (this.timeStep + 1) % (PERIOD_SEC / TIME_INTERVAL_SEC);
		this.currentTime.add(Calendar.SECOND, TIME_INTERVAL_SEC);
	}

	private String getTime() {
		return this.xsdDatetimeFormat.format(this.currentTime.getTime());
	}

	private String computeTemperature(double curTimeStep) {
		double temp = AVERAGE_VALUE
				+ VARIATION_AMPLITUDE * Math.sin((2 * Math.PI / (PERIOD_SEC / TIME_INTERVAL_SEC)) * curTimeStep);
		return Double.toString(temp);
	}
	
	private ObservationUpdate[] getObservationUpdates() {
		String time = this.getTime();
		double timeDisplacement = (PERIOD_SEC / TIME_INTERVAL_SEC) / 4;
		
		String tempA = this.computeTemperature(this.timeStep + 0*timeDisplacement);
		String tempB = this.computeTemperature(this.timeStep + 1*timeDisplacement);
		String tempC = this.computeTemperature(this.timeStep + 2*timeDisplacement);
		String tempD = this.computeTemperature(this.timeStep + 3*timeDisplacement);

		ObservationUpdate[] observations = new ObservationUpdate[4];
		observations[0] = new ObservationUpdate(SENSOR_A_ID, time, tempA);
		observations[1] = new ObservationUpdate(SENSOR_B_ID, time, tempB);
		observations[2] = new ObservationUpdate(SENSOR_C_ID, time, tempC);
		observations[3] = new ObservationUpdate(SENSOR_D_ID, time, tempD);
		
		System.out.println("New observations: @" + time + " [" + tempA + ", " + tempB + ", " + tempC + ", " + tempD +"]");
		return observations;
	}

	private String prepareUpdateJSONObject(ObservationUpdate observation) {
		return this.updateQueryTemplate
				.replace("${company}", COMPANY_ID)
				.replace("${observation}", observation.getObservationID())
				.replace("${timestamp}", observation.getTimestamp())
				.replace("${temperature}", observation.getTemperature());
	}

	/*
	 * "BATCH UPDATE ATTRIBUTES OF MULTIPLE DATA ENTITIES"
	 * https://ngsi-ld-tutorials.readthedocs.io/en/latest/ngsi-ld-operations.html#
	 * batch-update-attributes-of-multiple-data-entities
	 */
	private void sendUpdateToNGSILD(ObservationUpdate[] observations) {
		String updateBody = "[\n";
		for (int i = 0; i < observations.length; ++i) {
			ObservationUpdate curObs = observations[i];
			updateBody += this.prepareUpdateJSONObject(curObs);
			if (i < observations.length - 1) {
				// It's not the last JSON object in the list!
				updateBody += ",";
			}
			updateBody += "\n";
		}
		updateBody += "]\n";
		
		String requestURI = NGSI_LD_ENDPOINT + "/ngsi-ld/v1/entityOperations/update?options=update";
		HttpRequest request = null;
		try {
			request = HttpRequest.newBuilder().uri(new URI(requestURI)).version(HttpClient.Version.HTTP_1_1)
					.timeout(Duration.of(5, ChronoUnit.SECONDS))
					.method("POST", HttpRequest.BodyPublishers.ofString(updateBody))
					.header("Content-Type", "application/json")
					.header("Link",	"<http://iosonopersia.altervista.org/context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\","
							+ " <https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\"")
					.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.err.println("ERROR: request URI's syntax is invalid. Request failed!");
			return;
		}

		HttpResponse<String> response = null;
		try {
			response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.err.println("ERROR: failed to obtain a response from the server. Request failed!");
			return;
		}
		;

		if (!request.uri().toString().equals(requestURI) || !response.uri().toString().equals(requestURI)) {
			System.err.println("ERROR: request URI was changed, maybe because of a redirect. Request failed!");
			return;
		}

		switch (response.statusCode()) {
		case 204: {
			// All the Attributes were updated successfully.
			// Body: empty
		} break;
		case 207: {
			// Only the Attributes included in the response payload were successfully updated.
			// Body: UpdateResult
			System.err.println("ERROR: only some attributes were successfully updated.");
			System.err.println("ERROR: response from SCORPIO is [STATUS " + response.statusCode() + "] " + response.body());
		} break;
		default: {
			System.err.println("ERROR: response from SCORPIO is [STATUS " + response.statusCode() + "] " + response.body());
		}
		}
	}

	public void produceNewObservations() throws InterruptedException {
		this.stepForward();		

		ObservationUpdate[] newObservations = this.getObservationUpdates();
		this.sendUpdateToNGSILD(newObservations);
	}

	public void setup() {
		Map<String, String> env = System.getenv();
		// Company ID
		if(env.get("COMPANY_ID") != null) {
    		this.COMPANY_ID = env.get("COMPANY_ID");
    	}
		
		// Sensor IDs
		if(env.get("SENSOR_A_ID") != null) {
    		this.SENSOR_A_ID = env.get("SENSOR_A_ID");
    	}
		if(env.get("SENSOR_B_ID") != null) {
    		this.SENSOR_B_ID = env.get("SENSOR_B_ID");
    	}
		if(env.get("SENSOR_C_ID") != null) {
    		this.SENSOR_C_ID = env.get("SENSOR_C_ID");
    	}
		if(env.get("SENSOR_D_ID") != null) {
    		this.SENSOR_D_ID = env.get("SENSOR_D_ID");
    	}

		// NGSI-LD endpoint
		if(env.get("NGSI_LD_ENDPOINT") != null) {
    		this.NGSI_LD_ENDPOINT = env.get("NGSI_LD_ENDPOINT");
    	}
	}
	
	public static void main(String[] args) {
		FakeControlUnit fakeProducer = new FakeControlUnit();
		fakeProducer.setup();
		while (true) {
			try {
				fakeProducer.produceNewObservations();
			} catch(InterruptedException e) {
				System.err.println("[[FakeControlUnit]] was interrupted! Aborting...");
				System.exit(1);
			}
		}
	}

}
