package org.sdci.sdk.models;

public class Subscribe extends MessageContent {

	private String topic;

	public Subscribe(String topic) {
		this.topic = topic;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

}
