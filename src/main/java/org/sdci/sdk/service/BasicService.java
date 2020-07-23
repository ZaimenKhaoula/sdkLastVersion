package org.sdci.sdk.service;

import org.sdci.sdk.communication.*;
import org.sdci.sdk.models.Message;
import org.sdci.sdk.models.MessageType;
import org.sdci.sdk.models.Metric;
import org.sdci.sdk.models.Request;
import org.sdci.sdk.models.Response;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public abstract class BasicService {
	Map<String, ICommunicationFeature> CommunicationFeatures = new HashMap<String, ICommunicationFeature>();
	ArrayList<Metric> metrics =new ArrayList<Metric>();
	
	// to identify the microservice we use
	String microServiceUniqueIdentifier;
	String microServiceUniqueIdentifierToSendPing;
	String microServiceUniqueIdentifierToRecievePing;
	// variables used to contact NCEM
	ZContext contextUsedToContactNCEM;
	ZMQ.Socket reqSocketUsedToContactNCEM;
	ZContext monitoringContext;
	ZMQ.Socket dealerSocketUsedByMS;
	String routerSocketUsedbyNCEM;

	// variables used by the client and the server
	ZContext contextClient, contextServer;
	ZMQ.Socket dealerSocketUsedByClient, dealerSocketUsedByServer;
	String routerSocketUsedByRouterURL;

	// variables used by the publisher and the subscriber
	ZContext contextPublisher, contextSubscriber;
	ZMQ.Socket subSocketUsedForReceivingMessages, pubSocketUsedForSendingMessages;
	String subSocketUsedByRouterURL, pubSocketUsedByRouterURL;

	// variables used if the microService is configurable
	ZContext contextConfiguration;
	String confMessage;
	String confPubSocketUsedByRouter;
	
	
	// variables used by the MicroService to measure RTT (latency) 
	ZContext contextToRespondToPing, contextToSendPing;
	ZMQ.Socket dealerSocketUsedByPingSender, dealerSocketUsedByPingReciever;
	
	final GsonBuilder builder = new GsonBuilder();
	final Gson gson = builder.setPrettyPrinting().create();

	public void XAddCommunicationFeature(ICommunicationFeature feature) {
		if (feature instanceof IClientService) {
			CommunicationFeatures.put(IClientService.KEY, feature);
			return;
		}
		if (feature instanceof IServerService) {
			CommunicationFeatures.put(IServerService.KEY, feature);
			return;
		}
		if (feature instanceof IPublisherService) {
			CommunicationFeatures.put(IPublisherService.KEY, feature);
			return;
		}
		if (feature instanceof ISubscriberService) {
			CommunicationFeatures.put(ISubscriberService.KEY, feature);
			return;
		}
		if (feature instanceof IConfigurableService) {
			CommunicationFeatures.put(IConfigurableService.KEY, feature);
			return;
		}
	}

	public void XInitialize() throws IOException, InterruptedException {
		// TO-DO

		// 1. retrieve the connection info of the NCEM
		// we suppose that the folder in which the file called "NCEM.properties" is
		// located, is known by every microService
		// for instance, the file is located in the project folder
		// we read the url of the NCEM which will give the Uservice the "local" router
		// url
		Properties ncem = new Properties();
		InputStream inStream = new FileInputStream("NCEM.properties");
		ncem.load(inStream);

		// 2. contact the NCEM to get connection info of the router
		// send the NCEM a get request in order to get the "local" router port number
		// for this we create a context and a REQ socket
		contextUsedToContactNCEM = new ZContext();
		reqSocketUsedToContactNCEM = contextUsedToContactNCEM.createSocket(SocketType.REQ);
		System.out.println("connecting to .. NCEM ..  " + ncem.getProperty("address.rep"));
		reqSocketUsedToContactNCEM.connect(ncem.getProperty("address.rep"));
		// reqSocketUsedToContactNCEM.setReceiveTimeOut(1000);
		String replyFromTheNCEM = null;
		// we keep sending request until we get a response from the NCEM
		while (replyFromTheNCEM == null) {
			reqSocketUsedToContactNCEM.sendMore(System.getenv("CLIENT_ID"));
			String id = System.getenv("ID");
			reqSocketUsedToContactNCEM.send(id, 0);
			replyFromTheNCEM = reqSocketUsedToContactNCEM.recvStr(0);
			if (replyFromTheNCEM != null && replyFromTheNCEM.contains("tcp://null")) {
				System.out.println("Missing NCEM info ! .. retry ..");
				Thread.sleep(500);
				replyFromTheNCEM = null;
			}
		}

		// replyFromTheNCEM contains the addresses which sockets used by the router are
		// binded to
		// this includes ROUTER socket address, SUB Socket address, PUB socket address,
		// PUB socket used for sending configuration messages
		String[] parts = replyFromTheNCEM.split("-");
		this.microServiceUniqueIdentifier = parts[0];
		microServiceUniqueIdentifierToSendPing=parts[0]+"pingSender";
		microServiceUniqueIdentifierToRecievePing=parts[0]+"pingReciever";
		microServiceUniqueIdentifier = this.microServiceUniqueIdentifier;
		
		
		try {
		monitoringContext = new ZContext();
		routerSocketUsedbyNCEM = parts[5];
		System.out.println("connected to NcemRouter .. " + routerSocketUsedbyNCEM);
		dealerSocketUsedByMS = monitoringContext.createSocket(SocketType.DEALER);
		dealerSocketUsedByMS.connect(routerSocketUsedbyNCEM);
		dealerSocketUsedByMS.setIdentity(microServiceUniqueIdentifier.getBytes());
		dealerSocketUsedByMS.setSendBufferSize(1024 * 1024);
		ZMQ.Socket socketUsedForLaunchingThreadMonitoring;
		socketUsedForLaunchingThreadMonitoring = ZThread.fork(monitoringContext, new ZThread.IAttachedRunnable() {
			@Override
			public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {
				String source,monitoringmessage;
		
				while (!Thread.currentThread().isInterrupted()) {
					source = dealerSocketUsedByMS.recvStr(0);
					monitoringmessage = dealerSocketUsedByMS.recvStr(0);
			
					if (monitoringmessage != null) {
						monitoringMsgProcessing( monitoringmessage,source);	
					}
				}
			}
		});}catch(Exception e ) {}

		
		
	/*** ping to other microservices**/	
		contextToSendPing = new ZContext();
		routerSocketUsedByRouterURL = parts[1];
		dealerSocketUsedByPingSender = contextToSendPing.createSocket(SocketType.DEALER);
		System.out.println("connected to Router .. " + routerSocketUsedByRouterURL);
		dealerSocketUsedByPingSender.connect(routerSocketUsedByRouterURL);
		dealerSocketUsedByPingSender.setIdentity(microServiceUniqueIdentifierToSendPing.getBytes());
		dealerSocketUsedByPingSender.setSendBufferSize(1024 * 1024);
		dealerSocketUsedByPingSender.setReceiveTimeOut(10000);
	
		/*** recieve  microservices's ping**/	
		
		
		 try {
		
		contextToRespondToPing = new ZContext();
		routerSocketUsedByRouterURL = parts[1];
		System.out.println("connected to Router .. " + routerSocketUsedByRouterURL);
		dealerSocketUsedByPingReciever = contextToRespondToPing.createSocket(SocketType.DEALER);
		dealerSocketUsedByPingReciever.connect(routerSocketUsedByRouterURL);
		dealerSocketUsedByPingReciever.setIdentity(microServiceUniqueIdentifierToRecievePing.getBytes());
		dealerSocketUsedByPingReciever.setSendBufferSize(1024 * 1024);
		ZMQ.Socket socketUsedForLaunchingThreadP;
		socketUsedForLaunchingThreadP = ZThread.fork(contextToRespondToPing, new ZThread.IAttachedRunnable() {
			@Override
			public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {
				String destination, source, message;
				Request request;
				Response response;
				while (!Thread.currentThread().isInterrupted()) {
					message = dealerSocketUsedByPingReciever.recvStr(0);
					if (message != null) {
						JsonObject message_json = gson.fromJson(message, JsonObject.class);
						source = message_json.get("source").getAsString();
						request = gson.fromJson(message_json.get("content").getAsJsonObject().toString(),
								Request.class);
						if(request.getBody().compareTo("ping")==0) {
							response=new Response("", "ping");
							Message responseMessage = new Message(microServiceUniqueIdentifierToRecievePing, source, MessageType.RESPONSE,
									response);
							dealerSocketUsedByPingReciever.send(gson.toJson(responseMessage));
						
						}

					}
				}
			}
		});}catch(Exception e) {}

		if (CommunicationFeatures.containsKey(IClientService.KEY)) {
			contextClient = new ZContext();
			routerSocketUsedByRouterURL = parts[1];
			dealerSocketUsedByClient = contextClient.createSocket(SocketType.DEALER);
			System.out.println("connected to Router .. " + routerSocketUsedByRouterURL);
			dealerSocketUsedByClient.connect(routerSocketUsedByRouterURL);
			dealerSocketUsedByClient.setIdentity(microServiceUniqueIdentifier.getBytes());
			dealerSocketUsedByClient.setSendBufferSize(1024 * 1024);
			dealerSocketUsedByClient.setReceiveTimeOut(10000);
		}
		if (CommunicationFeatures.containsKey(IServerService.KEY)) {
			contextServer = new ZContext();
			routerSocketUsedByRouterURL = parts[1];
			System.out.println("connected to Router .. " + routerSocketUsedByRouterURL);
			dealerSocketUsedByServer = contextServer.createSocket(SocketType.DEALER);
			dealerSocketUsedByServer.connect(routerSocketUsedByRouterURL);
			dealerSocketUsedByServer.setIdentity(microServiceUniqueIdentifier.getBytes());
			dealerSocketUsedByServer.setSendBufferSize(1024 * 1024);
			ZMQ.Socket socketUsedForLaunchingThread;
			socketUsedForLaunchingThread = ZThread.fork(contextServer, new ZThread.IAttachedRunnable() {
				@Override
				public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {
					String destination, source, message;
					Request request;
					Response response;
					while (!Thread.currentThread().isInterrupted()) {
						message = dealerSocketUsedByServer.recvStr(0);
						if (message != null) {
							JsonObject message_json = gson.fromJson(message, JsonObject.class);
							source = message_json.get("source").getAsString();
							request = gson.fromJson(message_json.get("content").getAsJsonObject().toString(),
									Request.class);
							if(request.getBody().compareTo("ping")==0) {
								response=new Response("", "ping");
								Message responseMessage = new Message(microServiceUniqueIdentifier, source, MessageType.RESPONSE,
										response);
								dealerSocketUsedByServer.send(gson.toJson(responseMessage));
							
							}
							
							response = ((IServerService) CommunicationFeatures.get(IServerService.KEY))
									.XProcessRequest(source, request);
							XSendResponse(source, response);
						}
					}
				}
			});
	}
		// 3. Open the necessary sockets
		
		if (CommunicationFeatures.containsKey(IPublisherService.KEY)) {
			contextPublisher = new ZContext();
			subSocketUsedByRouterURL = parts[3];
			pubSocketUsedForSendingMessages = contextPublisher.createSocket(SocketType.PUB);
			pubSocketUsedForSendingMessages.connect(subSocketUsedByRouterURL);
			System.out.println("Connected to Router .. " + subSocketUsedByRouterURL);
		}
		if (CommunicationFeatures.containsKey(ISubscriberService.KEY)) {
			contextSubscriber = new ZContext();
			pubSocketUsedByRouterURL = parts[4];
			subSocketUsedForReceivingMessages = contextSubscriber.createSocket(SocketType.SUB);
			subSocketUsedForReceivingMessages.connect(pubSocketUsedByRouterURL);
			System.out.println("Connected to Router .. " + pubSocketUsedByRouterURL);
			ZMQ.Socket socketUsedForLaunchingThreadS;
			socketUsedForLaunchingThreadS = ZThread.fork(contextSubscriber, new ZThread.IAttachedRunnable() {
				@Override
				public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {
					String message, topic;
					while (!Thread.currentThread().isInterrupted()) {
						topic = subSocketUsedForReceivingMessages.recvStr(0);
						message = subSocketUsedForReceivingMessages.recvStr(0);
						((ISubscriberService) CommunicationFeatures.get(ISubscriberService.KEY))
								.XProcessMessage(message);
					}
				}
			});
		}
		if (CommunicationFeatures.containsKey(IConfigurableService.KEY)) {
			contextConfiguration = new ZContext();
			ZMQ.Socket subSocketUsedForReceivingConfigurationMessages = contextConfiguration
					.createSocket(SocketType.SUB);
			confPubSocketUsedByRouter = parts[2];
			System.out.println("[config] connecting to Router .. " + confPubSocketUsedByRouter);
			subSocketUsedForReceivingConfigurationMessages.connect(confPubSocketUsedByRouter);
			subSocketUsedForReceivingConfigurationMessages.subscribe("conf" + microServiceUniqueIdentifier);
			ZMQ.Socket socketUsedForLaunchingThreadC;
			String finalMicroServiceUniqueIdentifier = microServiceUniqueIdentifier;
			socketUsedForLaunchingThreadC = ZThread.fork(contextConfiguration, new ZThread.IAttachedRunnable() {
				@Override
				public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {
					String topic;
					while (!Thread.currentThread().isInterrupted()) {
						topic = subSocketUsedForReceivingConfigurationMessages.recvStr(0);
						confMessage = subSocketUsedForReceivingConfigurationMessages.recvStr(0);
						System.out.println("[Info] [" + finalMicroServiceUniqueIdentifier + " ] receiving config msg : "
								+ confMessage);
						((IConfigurableService) CommunicationFeatures.get(IConfigurableService.KEY))
								.XProcessConfiguration(confMessage);
					}
				}
			});
		}
	}

	public void XTerminate() {
		// TO-DO
		// Graceful termination of the service:
		// Inform the NCEM of termination ?
		String replyFromTheNCEM = null;
		while (replyFromTheNCEM == null) {
			reqSocketUsedToContactNCEM.sendMore(microServiceUniqueIdentifier);
			reqSocketUsedToContactNCEM.send("DELETE", 0);
			replyFromTheNCEM = reqSocketUsedToContactNCEM.recvStr(0);
		}

		// close opened connections
		if (!contextUsedToContactNCEM.isClosed())
			contextUsedToContactNCEM.close();
		if (!contextConfiguration.isClosed())
			contextConfiguration.close();
		if (!contextClient.isClosed())
			contextClient.close();
		if (!contextServer.isClosed())
			contextServer.close();
		if (!contextPublisher.isClosed())
			contextPublisher.close();
		if (!contextSubscriber.isClosed())
			contextSubscriber.close();

		// Clean local data ?

	}

	public Response XSendRequest(String destination, Request request) {
		// Do not allow the use of this method is the user didn't specify the use of
		// IClientService interface
		assert (CommunicationFeatures.containsKey(IClientService.KEY));
		String response_json = null;
		Message requestMessage = new Message(this.microServiceUniqueIdentifier, destination, MessageType.REQUEST,
				request);
		// TO-DO:
		// 1. Use the requestSocket to send the request to the given destination
		// we keep sending the request until we get a response from the server
		// usually recv() method will wait until it gets a message, but we set the
		// receive timeout of this socket to 1s, so it will wait only 1s.
		// 2. Wait for the response and send it back

		// dealerSocketUsedByClient.sendMore(destination);
		dealerSocketUsedByClient.send(gson.toJson(requestMessage));
		// sourceOfResponse = dealerSocketUsedByClient.recvStr();
		response_json = dealerSocketUsedByClient.recvStr();

		if (response_json != null) {
			JsonObject responseMessage = gson.fromJson(response_json, JsonObject.class);
			String response = responseMessage.get("content").getAsJsonObject().toString();
			return gson.fromJson(response, Response.class);
		}

		else
			return null;
	}

	public void XSendResponse(String destination, Response response) {
		// Do not allow the use of this method is the user didn't specify the use of
		// IClientService interface
		assert (CommunicationFeatures.containsKey(IServerService.KEY));

		// TO-DO:
		// 1. Use the responseSocket to send the request to the given destination
		Message responseMessage = new Message(this.microServiceUniqueIdentifier, destination, MessageType.RESPONSE,
				response);
		// dealerSocketUsedByServer.sendMore(destination);
		dealerSocketUsedByServer.send(gson.toJson(responseMessage));
	}

	public void XSubscribeToTopic(String topic) {
		// Do not allow the use of this method is the user didn't specify the use of
		// ISubscriberService interface
		assert (CommunicationFeatures.containsKey(ISubscriberService.KEY));

		// TO-DO:
		// 1. Use the subSocket to subscribe to the given topic
		subSocketUsedForReceivingMessages.subscribe(topic);

	}

	public void XPublishMessage(String topic, String message) {
		// Do not allow the use of this method is the user didn't specify the use of
		// IPublisherService interface
		assert (CommunicationFeatures.containsKey(IPublisherService.KEY));

		// TO-DO:
		// 1. Use the pubSocket to publish the message in the given topic
		pubSocketUsedForSendingMessages.sendMore(topic);
		pubSocketUsedForSendingMessages.send(message);
	}
	
	
	private void monitoringMsgProcessing(String monitoringmessage, String source)
    {
		    long startTime;
		    long endTime; 
    	    String [] montoringMessageParts= monitoringmessage.split("-");
    		switch (montoringMessageParts[2]) {
    		case "EnableMetric":
    		 for(Metric m : metrics) {
    			 if(m.getMetricName().compareTo(montoringMessageParts[0])==0)
    				 m.setEnabled(true);
    			 dealerSocketUsedByMS.sendMore(source);
    			 dealerSocketUsedByMS.send("ok");
    		 }
    		break;
    		case "DesableMetric" :
    			 for(Metric m : metrics) {
        			 if(m.getMetricName().compareTo(montoringMessageParts[0])==0)
        				 m.setEnabled(false);
        			 dealerSocketUsedByMS.sendMore(source);
        			 dealerSocketUsedByMS.send("ok");
        		 }
        	break;
        	
    		case "Reset":
    			 for(Metric m : metrics) {
        			 if(m.getMetricName().compareTo(montoringMessageParts[0])==0)
        				 m.setValue(0);
        			 dealerSocketUsedByMS.sendMore(source);
        			 dealerSocketUsedByMS.send("ok");
        		 }
        		
           break;
           
    		case "GetValue":
    			if(montoringMessageParts[1].compareTo("counetr")==0)
    			{ for(Metric m : metrics) {
        			 if(m.getMetricName().compareTo(montoringMessageParts[0])==0)
        				
        				 dealerSocketUsedByMS.sendMore(source);
        			 dealerSocketUsedByMS.send(Float.toString(m.getValue())); 
        				 
        		 }}
    			else {
    				//metric type is rtt
    				
    				String response_json = null;
    				Request request= new Request("", "",  "ping");
    				Message requestMessage = new Message(microServiceUniqueIdentifierToSendPing, montoringMessageParts[3]+"pingReciever", MessageType.REQUEST,
    						request);
    				startTime = System.currentTimeMillis();
    				while(response_json==null) {
    				dealerSocketUsedByPingSender.send(gson.toJson(requestMessage));
    				// sourceOfResponse = dealerSocketUsedByClient.recvStr();
    				response_json = dealerSocketUsedByPingSender.recvStr();
    				}
    				endTime = System.currentTimeMillis();
    				 dealerSocketUsedByMS.sendMore(source);
        			 dealerSocketUsedByMS.send(Float.toString(endTime-startTime)); 
    				
    			}
            break;
    		
    		default:
    		
    		}
    	}
	
	
	
	
	
}

