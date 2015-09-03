package io.ably.test.common;

public class Setup {

	public static class Key {
		public String id;
		public String value;
		public String keyName;
		public String keySecret;
		public String keyStr;
		public String capability;
		public boolean privileged;
		public int status;
		public long created;
		public long modified;
		public long expires;
	}

	public static class Namespace {
		public String id;
		public boolean persisted;
		public boolean hasStats;
		public int status;
		public long created;
		public long modified;
	}

	public static class Connection {
		public String id;
	}

	public static class Channel {
		public String name;
	}

	public static class AppSpec {
		public String id;
		public String appId;
		public String accountId;
		public Key[] keys;
		public Namespace[] namespaces;
		public Connection[] connections;
		public Channel[] channels;
		public Integer status;
		public boolean tlsOnly;
		public long created;
		public long modified;
		public String labels;
		public String notes;
	}
}
