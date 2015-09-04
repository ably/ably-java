package io.ably.test.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.ably.types.PresenceMessage;

public class Setup {

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class Key {
		public String keyName;
		public String keySecret;
		public String keyStr;
		public String capability;
		public int status;
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class Namespace {
		public String id;
		public boolean persisted;
		public int status;
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class Connection {
		public String id;
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class Channel {
		public String name;
		public PresenceMember[] presence;
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class PresenceMember extends PresenceMessage {
		public PresenceMember() { action = Action.ENTER; }
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class AppSpec {
		public String id;
		public String appId;
		public String accountId;
		public Key[] keys;
		public Namespace[] namespaces;
		public Connection[] connections;
		public Channel[] channels;
		public boolean tlsOnly;
		public String notes;
	}
}
