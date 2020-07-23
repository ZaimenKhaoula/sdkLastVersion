package org.sdci.sdk.communication;

import org.sdci.sdk.models.Request;
import org.sdci.sdk.models.Response;

public interface IServerService extends ICommunicationFeature {
    String KEY = "SERVER";
    Response XProcessRequest (String sender, Request request);
}
