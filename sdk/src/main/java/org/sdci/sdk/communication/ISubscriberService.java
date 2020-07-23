package org.sdci.sdk.communication;

public interface ISubscriberService extends ICommunicationFeature {
    String KEY = "SUBSCRIBER";
    void XProcessMessage (String message);
}
