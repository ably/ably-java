package io.ably.types;

import org.json.JSONObject;
import org.msgpack.annotation.Message;

/**
 * A class encapsulating a Stats datapoint.
 * Ably usage information, across an account or an individual app,
 * is available as Stats records on a timeline with different granularities.
 * This class defines the Stats type and its subtypes, giving a structured
 * representation of service usage for a specific scope and time interval.
 * This class also contains utility methods to convert from the different
 * formats used for REST responses.
 */
@Message
public class Stats {

	/**
	 * A breakdown of summary stats data for different (tls vs non-tls)
	 * connection types.
	 */
	@Message
	public static class ConnectionTypes {
		public ResourceCount all;
		public ResourceCount plain;
		public ResourceCount tls;
		public static ConnectionTypes fromJSON(JSONObject json) {
			ConnectionTypes result = null;
			if(json != null) {
				if(json.has("all")) {
					result = new ConnectionTypes();
					result.all = ResourceCount.fromJSON(json.optJSONObject("all"));
					result.plain = ResourceCount.fromJSON(json.optJSONObject("plain"));
					result.tls = ResourceCount.fromJSON(json.optJSONObject("tls"));
				}
			}
			return result;
		}
	}

	/**
	 * A datapoint for message volume (number of messages plus aggregate data size)
	 */
	@Message
	public static class MessageCount {
		public double count;
		public double data;
		public static MessageCount fromJSON(JSONObject json) {
			MessageCount result = null;
			if(json != null) {
				if(json.has("count")) {
					result = new MessageCount();
					result.count = json.optDouble("count");
					result.data = json.optDouble("data");
				}
			}
			return result;
		}
	}

	/**
	 * A breakdown of summary stats data for different (message vs presence)
	 * message types.
	 */
	@Message
	public static class MessageTypes {
		public MessageCount all;
		public MessageCount messages;
		public MessageCount presence;
		public static MessageTypes fromJSON(JSONObject json) {
			MessageTypes result = null;
			if(json != null) {
				if(json.has("all")) {
					result = new MessageTypes();
					result.all = MessageCount.fromJSON(json.optJSONObject("all"));
					result.messages = MessageCount.fromJSON(json.optJSONObject("messages"));
					result.presence = MessageCount.fromJSON(json.optJSONObject("presence"));
				}
			}
			return result;
		}
	}

	/**
	 * A breakdown of summary stats data for traffic over various transport types.
	 */
	@Message
	public static class MessageTraffic {
		public MessageTypes all;
		public MessageTypes realtime;
		public MessageTypes rest;
		public MessageTypes push;
		public MessageTypes httpStream;
		public static MessageTraffic fromJSON(JSONObject json) {
			MessageTraffic result = null;
			if(json != null) {
				if(json.has("all")) {
					result = new MessageTraffic();
					result.all = MessageTypes.fromJSON(json.optJSONObject("all"));
					result.realtime = MessageTypes.fromJSON(json.optJSONObject("realtime"));
					result.rest = MessageTypes.fromJSON(json.optJSONObject("rest"));
					result.push = MessageTypes.fromJSON(json.optJSONObject("push"));
					result.httpStream = MessageTypes.fromJSON(json.optJSONObject("httpStream"));
				}
			}
			return result;
		}
	}

	/**
	 * Aggregate data for numbers of requests in a specific scope.
	 */
	@Message
	public static class RequestCount {
		public double succeeded;
		public double failed;
		public double refused;
		public static RequestCount fromJSON(JSONObject json) {
			RequestCount result = null;
			if(json != null) {
				if(json.has("succeeded") || json.has("failed") || json.has("refused")) {
					result = new RequestCount();
					result.succeeded = json.optDouble("succeeded");
					result.failed = json.optDouble("failed");
					result.refused = json.optDouble("refused");
				}
			}
			return result;
		}
	}

	/**
	 * Aggregate data for usage of a resource in a specific scope.
	 */
	@Message
	public static class ResourceCount {
		public double opened;
		public double peak;
		public double mean;
		public double min;
		public double refused;
		public static ResourceCount fromJSON(JSONObject json) {
			ResourceCount result = null;
			if(json != null) {
				if(json.has("opened") || json.has("refused") || json.has("peak")) {
					result = new ResourceCount();
					result.opened = json.optDouble("opened");
					result.peak = json.optDouble("peak");
					result.mean = json.optDouble("mean");
					result.min = json.optDouble("min");
					result.refused = json.optDouble("refused");
				}
			}
			return result;
		}
	}

	/**
	 * Utility method for obtaining a Stats record from a JSON-encoded response body.
	 * @param json
	 * @return
	 */
	public static Stats fromJSON(JSONObject json) {
		Stats result = new Stats();
		if(json != null) {
			result.all = MessageTypes.fromJSON(json.optJSONObject("all"));
			result.inbound = MessageTraffic.fromJSON(json.optJSONObject("inbound"));
			result.outbound = MessageTraffic.fromJSON(json.optJSONObject("outbound"));
			result.persisted = MessageTypes.fromJSON(json.optJSONObject("persisted"));
			result.connections = ConnectionTypes.fromJSON(json.optJSONObject("connections"));
			result.channels = ResourceCount.fromJSON(json.optJSONObject("channels"));
			result.apiRequests = RequestCount.fromJSON(json.optJSONObject("apiRequests"));
			result.tokenRequests = RequestCount.fromJSON(json.optJSONObject("tokenRequests"));
		}
		return result;
	}

	public MessageTypes all;
	public MessageTraffic inbound;
	public MessageTraffic outbound;
	public MessageTypes persisted;
	public ConnectionTypes connections;
	public ResourceCount channels;
	public RequestCount apiRequests;
	public RequestCount tokenRequests;
}
