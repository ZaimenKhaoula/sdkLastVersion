package org.sdci.sdk.communication;

public interface IConfigurableService extends ICommunicationFeature {
    String KEY = "CONFIGURABLE";
    void XProcessConfiguration(String configuration);
}
