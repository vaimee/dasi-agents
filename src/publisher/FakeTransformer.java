package publisher;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

public class FakeTransformer extends Producer {
	private final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
	private int timeStep = 0;
	private final Calendar currentTime = Calendar.getInstance(UTC_TZ);
	private final DateFormat xsdDatetimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
	private final int PERIOD_SEC = 60*60; // in seconds
	private final int TIME_INTERVAL_SEC = 5; // in seconds
	private final double AVERAGE_VALUE = 75.0; // in Celsius degrees
	private final double VARIATION_AMPLITUDE = 50.0; // in Celsius degrees
	
	public FakeTransformer(JSAP appProfile, String updateID)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(appProfile, updateID);
		xsdDatetimeFormat.setTimeZone(UTC_TZ);
	}

	private void stepForward() throws InterruptedException {
		Thread.sleep(TIME_INTERVAL_SEC*1000); // Sleep 5 seconds
		timeStep = (timeStep + 1) % (PERIOD_SEC / TIME_INTERVAL_SEC);
		currentTime.add(Calendar.SECOND, TIME_INTERVAL_SEC);
	}
	
	private String getTemperature() {
		double temperature = AVERAGE_VALUE + VARIATION_AMPLITUDE*Math.sin((2*Math.PI/(PERIOD_SEC / TIME_INTERVAL_SEC))*timeStep);
		return Double.toString(temperature);
	}

	private String getTime() {
		return xsdDatetimeFormat.format(currentTime.getTime());
	}
	
	public void produceNewObservations() throws InterruptedException {
		stepForward();
		
		String graph = "https://vaimee.com/monas/company123/observations/0000.2.1/data_without_sysattrs";
		String observation = "https://vaimee.com/monas/company123/observations/0000.2.1";
		String transformer = "https://vaimee.com/monas/company123/transformers/0500.0.1";
		String sensor = "https://vaimee.com/monas/company123/sensors/0000.2.1";
		String time = getTime();
		String temperature = getTemperature();
		
		System.out.println(time + "\t" + temperature);
		
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
		} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException
				| SEPABindingsException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, IOException, InterruptedException {

		JSAP appProfile = new JSAP("resources/ObservationHistory.jsap");

		FakeTransformer app = new FakeTransformer(appProfile, "NEW_OBSERVATION_ENTITY");

		while(true) {
			try {
				app.produceNewObservations();
			} catch(InterruptedException e) {
				e.printStackTrace();
				break;
			}
		}
		
		app.close();

	}

}
