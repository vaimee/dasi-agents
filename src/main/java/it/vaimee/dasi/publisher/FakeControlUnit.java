package publisher;

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
import java.util.TimeZone;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;
import it.unibo.arces.wot.sepa.pattern.JSAP;
import it.unibo.arces.wot.sepa.pattern.Producer;

public class FakeControlUnit extends Producer {
	private final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
	private int timeStep = 0;
	private final Calendar currentTime = Calendar.getInstance(UTC_TZ);
	private final DateFormat xsdDatetimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private final int PERIOD_SEC = 60 * 60; // in seconds
	private final int TIME_INTERVAL_SEC = 5; // in seconds
	private final double AVERAGE_VALUE = 75.0; // in Celsius degrees
	private final double VARIATION_AMPLITUDE = 50.0; // in Celsius degrees

	private final String DOMAIN = "http://localhost:3000";
	private final String APP_NAME = "monas";
	private final String COMPANY_ID = "companyX";
	private final String SENSOR_ID = "0000.2.1";
	private final String TRANSFORMER_ID = "13101974.0.0";

	private final String NGSI_LD_ENDPOINT = "http://localhost:9090";

	public FakeControlUnit(JSAP appProfile, String updateID)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(appProfile, updateID);
		xsdDatetimeFormat.setTimeZone(UTC_TZ);
	}

	private void stepForward() throws InterruptedException {
		Thread.sleep(TIME_INTERVAL_SEC * 1000); // Sleep 5 seconds
		timeStep = (timeStep + 1) % (PERIOD_SEC / TIME_INTERVAL_SEC);
		currentTime.add(Calendar.SECOND, TIME_INTERVAL_SEC);
	}

	private String getTemperature() {
		double temperature = AVERAGE_VALUE
				+ VARIATION_AMPLITUDE * Math.sin((2 * Math.PI / (PERIOD_SEC / TIME_INTERVAL_SEC)) * timeStep);
		return Double.toString(temperature);
	}

	private String getTime() {
		return xsdDatetimeFormat.format(currentTime.getTime());
	}

	private void sendUpdateQueryToSEPA(String graph, String observation, String transformer, String sensor, String time,
			String temperature) {
		try {
			this.setUpdateBindingValue("graph", new RDFTermURI(graph));
			this.setUpdateBindingValue("observation", new RDFTermURI(observation));
			this.setUpdateBindingValue("transformer", new RDFTermURI(transformer));
			this.setUpdateBindingValue("sensor", new RDFTermURI(sensor));
			this.setUpdateBindingValue("time", new RDFTermLiteral(time));
			this.setUpdateBindingValue("temperature", new RDFTermLiteral(temperature));
		} catch (SEPABindingsException e) {
			e.printStackTrace();
		}

		try {
			this.update();
		} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException | SEPABindingsException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Currently implementing the "OVERWRITE MULTIPLE ATTRIBUTES OF A DATA ENTITY"
	 * strategy
	 * https://ngsi-ld-tutorials.readthedocs.io/en/latest/ngsi-ld-operations.html#
	 * overwrite-multiple-attributes-of-a-data-entity
	 * 
	 * TODO: a control unit should update the temperature of every sensor (A,B,C,D)
	 * at the same time to save bandwidth! The
	 * "BATCH UPDATE ATTRIBUTES OF MULTIPLE DATA ENTITIES" strategy should be
	 * implemented here.
	 * https://ngsi-ld-tutorials.readthedocs.io/en/latest/ngsi-ld-operations.html#
	 * batch-update-attributes-of-multiple-data-entities
	 */
	private void sendUpdateToNGSILD(String observation, String time, String temperature) {
		String updateBody = new StringBuilder("{\"resultTime\": {\"type\": \"Property\", \"value\": \"")
				.append(time).append("\"}, \"hasSimpleResult\": {\"type\": \"Property\",	\"value\": \"")
				.append(temperature).append("\"}}").toString();

		String requestURI = NGSI_LD_ENDPOINT + "/ngsi-ld/v1/entities/" + observation + "/attrs";
		HttpRequest request = null;
		try {
			request = HttpRequest.newBuilder().uri(new URI(requestURI)).version(HttpClient.Version.HTTP_1_1)
					.timeout(Duration.of(10, ChronoUnit.SECONDS))
					.method("PATCH", HttpRequest.BodyPublishers.ofString(updateBody))
					.header("Content-Type", "application/json")
					.header("Link",	"<http://iosonopersia.altervista.org/context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\", <https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\"")
					.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.err.println("ERROR: request URI's syntax is invalid. Aborting...");
			System.exit(1);
		}

		HttpResponse<String> response = null;
		try {
			response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.err.println("ERROR: failed to obtain a response from the server. Aborting...");
			System.exit(2);
		}
		;

		if (!request.uri().toString().equals(requestURI) || !response.uri().toString().equals(requestURI)) {
			System.err.println("ERROR: request URI was changed, maybe because of a redirect. Aborting...");
			System.exit(3);
		}

		switch (response.statusCode()) {
		case 204: {
			// All the Attributes were updated successfully.
			// Body: empty
		} break;
		case 207: {
			// Only the Attributes included in the response payload were successfully updated.
			// Body: UpdateResult
			System.err.println("ERROR: only some attributes were successfully updated. Aborting...");
			System.exit(4);
		} break;
		case 400: {
			/*
			 * It is used to indicate that the request or its content is incorrect, see clause 6.3.2.
			 * In the returned ProblemDetails structure, the "detail" attribute should
			 * convey more information about the error.
			 */
			// Body: ProblemDetails
			
			// TODO: parse the response payload to extract the "detail" attribute and use it as error message
			System.err.println("ERROR: request was incorrect. Aborting...");
			System.exit(5);
		} break;
		case 404: {
			// It is used when a client provided an	entity identifier not known to the system, see clause 6.3.2.
			// Body: ProblemDetails
			System.err.println("ERROR: entity identifier is not known to the server. Aborting...");
			System.exit(6);
		} break;
		default: {
			System.err.println("ERROR: response status code is not compliant with the NGSI-LD spec. Aborting...");
			System.exit(7);
		}
		}
	}

	public void produceNewObservations() throws InterruptedException {
		stepForward();

		String graph = DOMAIN + "/" + APP_NAME + "/" + COMPANY_ID + "/observations/" + SENSOR_ID + "/entity/data_without_sysattrs";
		String observation = DOMAIN + "/" + APP_NAME + "/" + COMPANY_ID + "/observations/" + SENSOR_ID;
		String transformer = DOMAIN + "/" + APP_NAME + "/" + COMPANY_ID + "/transformers/" + TRANSFORMER_ID;
		String sensor = DOMAIN + "/" + APP_NAME + "/" + COMPANY_ID + "/sensors/" + SENSOR_ID;
		String time = getTime();
		String temperature = getTemperature();

		System.out.println(time + "\t" + temperature);

		// sendUpdateQueryToSEPA(graph, observation, transformer, sensor, time, temperature);
		sendUpdateToNGSILD(observation, time, temperature);
	}

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, IOException, InterruptedException {

		JSAP appProfile = new JSAP("resources/ObservationHistory.jsap");

		FakeControlUnit app = new FakeControlUnit(appProfile, "NEW_OBSERVATION_ENTITY");

		while (true) {
			try {
				app.produceNewObservations();
			} catch (InterruptedException e) {
				e.printStackTrace();
				break;
			}
		}

		app.close();

	}

}
