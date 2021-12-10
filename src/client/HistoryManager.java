package client;

import java.io.IOException;
import java.util.List;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.sparql.ARBindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;
import it.unibo.arces.wot.sepa.pattern.Aggregator;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class HistoryManager extends Aggregator {

	public HistoryManager(JSAP appProfile, String subscribeID, String updateID)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(appProfile, subscribeID, updateID);
	}

	@Override
	public void onFirstResults(BindingsResults results) {
		this.saveHistory(results);
	}

	@Override
	public void onAddedResults(BindingsResults results) {
		this.saveHistory(results);
	}

	private void saveHistory(BindingsResults results) {
		List<Bindings> data = results.getBindings();
		for (Bindings binding : data) {
			String observation = binding.getValue("observation");
			String transformer = binding.getValue("transformer");
			String sensor = binding.getValue("sensor");
			String time = binding.getValue("time");
			String temperature = binding.getValue("temperature");

			String historyGraph = transformer + "/history.ttl";
			
			try {
				this.setUpdateBindingValue("graph", new RDFTermURI(historyGraph));
				this.setUpdateBindingValue("observation", new RDFTermURI(observation));
				this.setUpdateBindingValue("transformer", new RDFTermURI(transformer));
				this.setUpdateBindingValue("sensor", new RDFTermURI(sensor));
				this.setUpdateBindingValue("time", new RDFTermLiteral(time));
				this.setUpdateBindingValue("temperature", new RDFTermLiteral(temperature));
			} catch (SEPABindingsException e) {
				e.printStackTrace();
				continue;
			}
			
			try {
				this.update();
			} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException
					| SEPABindingsException e) {
				e.printStackTrace();
				continue;
			}
		}
	}

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, IOException {

		JSAP appProfile = new JSAP("resources/ObservationHistory.jsap");

		HistoryManager app = new HistoryManager(appProfile, "GET_OBSERVATIONS", "SAVE_OBSERVATION_IN_GRAPH");
		app.subscribe(5000L, 3L);

		synchronized (app) {
			try {
				app.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		app.close();
	}

	@Override
	public void onResults(ARBindingsResults results) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRemovedResults(BindingsResults results) {
//		List<Bindings> data = results.getBindings();
//		for (Bindings binding : data) {
//
//			String graph = binding.getValue("g");
//			if (!this.graphExists(graph)) {
//				String meta_graph = "meta:" + graph;
//				Producer addContains;
//				String updateID = "DELETE_META";
//				try {
//					addContains = new Producer(appProfile, updateID);
//					addContains.setUpdateBindingValue("graph", new RDFTermURI(meta_graph));
//					addContains.update();
//					addContains.close();
//				} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
//						| IOException e) {
//					System.err.println("Something went wrong during execution of " + updateID);
//				}
//			}
//			if (!this.isRootContainer(graph, ROOT)) {
//				try {
//					String fathergraph = this.getParentContainer(graph);
//					Producer addContains;
//					String updateID = "DELETE_CONTAINS";
//					try {
//						addContains = new Producer(appProfile, updateID);
//						addContains.setUpdateBindingValue("fathergraph", new RDFTermURI(fathergraph));
//						addContains.setUpdateBindingValue("graph", new RDFTermURI(graph));
//						addContains.update();
//						addContains.close();
//					} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
//							| IOException e) {
//						System.err.println("Something went wrong during execution of " + updateID);
//					}
//					
//				} catch (URISyntaxException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//		}
	}

	@Override
	public void onError(ErrorResponse errorResponse) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSubscribe(String spuid, String alias) {
		// TODO Auto-generated method stub
		System.out.println("SUBSCRIBED SUCCESSFULLY");

	}

	@Override
	public void onUnsubscribe(String spuid) {
		// TODO Auto-generated method stub
		System.out.println("UNSUBSCRIBED SUCCESSFULLY");
	}

}
