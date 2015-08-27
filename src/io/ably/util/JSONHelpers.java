package io.ably.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONHelpers {

	@SuppressWarnings("unchecked")
	public static String[] getNames(JSONObject json) {
		Iterator<String> keys = json.keys();
		List<String> keyList = new ArrayList<String>();
		while(keys.hasNext()) keyList.add(keys.next());
		String[] result = new String[keyList.size()];
		result = keyList.toArray(result);
		return result;
	}

	public static JSONArray newJSONArray(Object[] members) {
		JSONArray result = new JSONArray();
		for(Object member : members) result.put(member);
		return result;
	}

	public static JSONArray remove(JSONArray arr, int idx) {
		int length = arr.length();
		JSONArray result = new JSONArray();
		if(idx > 0) {
			for(int i = 0; i < idx; i++) result.put(arr.opt(i));
		}
		if(++idx < length) {
			for(int i = idx; i < length; i++) result.put(arr.opt(i));
		}
		return result;
	}

	public static String getString(JSONObject json, String key) {
		return json.isNull(key) ? null : json.optString(key);
	}
}
