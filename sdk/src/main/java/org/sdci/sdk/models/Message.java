package org.sdci.sdk.models;


public class Message {
	String source;
	String destination;
	MessageType type;
	String tags;
	String cos;
	

	MessageContent content;

	public Message(String source, String destination, MessageType type, MessageContent content) {
		this.source = source;
		this.destination = destination;
		this.type = type;
		this.content = content;
		this.tags = "";
		this.cos = "";
	}
	public String getCos() {
		return cos;
	}

	public void setCos(String cos) {
		this.cos = cos;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public MessageType getType() {
		return type;
	}

	public void setType(MessageType type) {
		this.type = type;
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags;
	}

	public void setContent(MessageContent content) {
		this.content = content;
	}

	public MessageContent getContent() {
		return content;
	}

}
