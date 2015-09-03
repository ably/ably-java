package io.ably.types;

import org.json.JSONObject;

public class ConnectionDetails {
	public String clientId;
	public String connectionKey;
	public Long maxMessageSize;
	public Long maxInboundRate;
	public Long maxFrameSize;

	public static ConnectionDetails readJSON(JSONObject json) {
		ConnectionDetails result = new ConnectionDetails();
		if(json != null) {
			if(json.has("clientId"))
				result.clientId = json.optString("clientId");
			if(json.has("connectionKey"))
				result.connectionKey = json.optString("connectionKey");
			if(json.has("maxMessageSize"))
				result.maxMessageSize = Long.valueOf(json.optLong("maxMessageSize"));
			if(json.has("maxInboundRate"))
				result.maxInboundRate = Long.valueOf(json.optLong("maxInboundRate"));
			if(json.has("maxFrameSize"))
				result.maxFrameSize = Long.valueOf(json.optLong("maxFrameSize"));
		}
		return result;
	}
}
