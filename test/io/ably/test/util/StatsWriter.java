package io.ably.test.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ably.http.Http;
import io.ably.http.Http.RequestBody;
import io.ably.types.AblyException;
import io.ably.types.Stats;
import io.ably.types.Stats.ConnectionTypes;
import io.ably.types.Stats.MessageCount;
import io.ably.types.Stats.MessageTraffic;
import io.ably.types.Stats.MessageTypes;
import io.ably.types.Stats.RequestCount;
import io.ably.types.Stats.ResourceCount;

public class StatsWriter {
	public static RequestBody asJSONRequest(Stats[] stats) throws AblyException {
		return new Http.JSONRequestBody(writeJSON(stats).toString());
	}

	private static JSONArray writeJSON(Stats[] stats) throws AblyException {
		JSONArray json;
		try {
			json = new JSONArray();
			for(int i = 0; i < stats.length; i++)
				json.put(i, toJSON(stats[i]));

			return json;
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	private static JSONObject toJSON(Stats ob) {
		JSONObject json = new JSONObject();
		if(ob.intervalId != null) json.put("intervalId", ob.intervalId);
		if(ob.all != null) json.put("all", toJSON(ob.all));
		if(ob.inbound != null) json.put("inbound", toJSON(ob.inbound));
		if(ob.outbound != null) json.put("outbound", toJSON(ob.outbound));
		if(ob.persisted != null) json.put("persisted", toJSON(ob.persisted));
		if(ob.connections != null) json.put("connections", toJSON(ob.connections));
		if(ob.channels != null) json.put("channels", toJSON(ob.channels));
		if(ob.apiRequests != null) json.put("apiRequests", toJSON(ob.apiRequests));
		if(ob.tokenRequests != null) json.put("tokenRequests", toJSON(ob.tokenRequests));
		return json;
	}

	private static JSONObject toJSON(ConnectionTypes ob) {
		JSONObject json = new JSONObject();
		if(ob.all != null) json.put("all", toJSON(ob.all));
		if(ob.plain != null) json.put("plain", toJSON(ob.plain));
		if(ob.tls != null) json.put("tls", toJSON(ob.tls));
		return json;
	}

	private static JSONObject toJSON(MessageTypes ob) {
		JSONObject json = new JSONObject();
		if(ob.all != null) json.put("all", toJSON(ob.all));
		if(ob.messages != null) json.put("messages", toJSON(ob.messages));
		if(ob.presence != null) json.put("presence", toJSON(ob.presence));
		return json;
	}

	private static JSONObject toJSON(MessageTraffic ob) {
		JSONObject json = new JSONObject();
		if(ob.all != null) json.put("all", toJSON(ob.all));
		if(ob.realtime != null) json.put("realtime", toJSON(ob.realtime));
		if(ob.rest != null) json.put("rest", toJSON(ob.rest));
		if(ob.push != null) json.put("push", toJSON(ob.push));
		if(ob.httpStream != null) json.put("httpStream", toJSON(ob.httpStream));
		return json;
	}

	private static JSONObject toJSON(MessageCount ob) {
		JSONObject json = new JSONObject();
		if(ob.count != 0) json.put("count", ob.count);
		if(ob.data != 0) json.put("data", ob.data);
		return json;
	}

	private static JSONObject toJSON(ResourceCount ob) {
		JSONObject json = new JSONObject();
		if(ob.opened != 0) json.put("opened", ob.opened);
		if(ob.peak != 0) json.put("peak", ob.peak);
		if(ob.mean != 0) json.put("mean", ob.mean);
		if(ob.min != 0) json.put("min", ob.min);
		if(ob.refused != 0) json.put("refused", ob.refused);
		return json;
	}

	private static JSONObject toJSON(RequestCount ob) {
		JSONObject json = new JSONObject();
		if(ob.succeeded != 0) json.put("succeeded", ob.succeeded);
		if(ob.failed != 0) json.put("failed", ob.failed);
		if(ob.refused != 0) json.put("refused", ob.refused);
		return json;
	}
}
