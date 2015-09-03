package io.ably.types;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONObject;

/**
 * A class encapsulating a Stats datapoint.
 * Ably usage information, across an account or an individual app,
 * is available as Stats records on a timeline with different granularities.
 * This class defines the Stats type and its subtypes, giving a structured
 * representation of service usage for a specific scope and time interval.
 * This class also contains utility methods to convert from the different
 * formats used for REST responses.
 */
public class Stats {

	/**
	 * A breakdown of summary stats data for different (tls vs non-tls)
	 * connection types.
	 */
	public static class ConnectionTypes {
		public ResourceCount all;
		public ResourceCount plain;
		public ResourceCount tls;
		public static ConnectionTypes fromJSON(JSONObject json) {
			ConnectionTypes result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
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
	public static class MessageCount {
		public double count;
		public double data;
		public static MessageCount fromJSON(JSONObject json) {
			MessageCount result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
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
	public static class MessageTypes {
		public MessageCount all;
		public MessageCount messages;
		public MessageCount presence;
		public static MessageTypes fromJSON(JSONObject json) {
			MessageTypes result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
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
	public static class MessageTraffic {
		public MessageTypes all;
		public MessageTypes realtime;
		public MessageTypes rest;
		public MessageTypes webhook;
		public static MessageTraffic fromJSON(JSONObject json) {
			MessageTraffic result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
					result = new MessageTraffic();
					result.all = MessageTypes.fromJSON(json.optJSONObject("all"));
					result.realtime = MessageTypes.fromJSON(json.optJSONObject("realtime"));
					result.rest = MessageTypes.fromJSON(json.optJSONObject("rest"));
					result.webhook = MessageTypes.fromJSON(json.optJSONObject("webhook"));
				}
			}
			return result;
		}
	}

	/**
	 * Aggregate data for numbers of requests in a specific scope.
	 */
	public static class RequestCount {
		public double succeeded;
		public double failed;
		public double refused;
		public static RequestCount fromJSON(JSONObject json) {
			RequestCount result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
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
	public static class ResourceCount {
		public double opened;
		public double peak;
		public double mean;
		public double min;
		public double refused;
		public static ResourceCount fromJSON(JSONObject json) {
			ResourceCount result = null;
			if(json != null) {
				if(json.keys().hasNext()) {
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
			result.intervalId = json.optString("intervalId");
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

	public static enum Granularity {
		MINUTE,
		HOUR,
		DAY,
		MONTH
	}

	private static String[] intervalFormatString = new String[] {
		"yyyy-MM-dd:hh:mm",
		"yyyy-MM-dd:hh",
		"yyyy-MM-dd",
		"yyyy-MM"
	};

	public static String toIntervalId(long timestamp, Granularity granularity) {
		String formatString = intervalFormatString[granularity.ordinal()];
		return new SimpleDateFormat(formatString).format(new Date(timestamp));
	}

	public static long fromIntervalId(String intervalId) {
		try {
			String formatString = intervalFormatString[0].substring(0, intervalId.length());
			return new SimpleDateFormat(formatString).parse(intervalId).getTime();
		} catch (ParseException e) { return 0; }
	}

	public String intervalId;
	public MessageTypes all;
	public MessageTraffic inbound;
	public MessageTraffic outbound;
	public MessageTypes persisted;
	public ConnectionTypes connections;
	public ResourceCount channels;
	public RequestCount apiRequests;
	public RequestCount tokenRequests;
}
