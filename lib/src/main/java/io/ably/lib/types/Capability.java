package io.ably.lib.types;


import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ably.lib.util.JSONHelpers;

/**
 * A class representing an Ably Capability, providing
 * convenience methods to simplify creation of token requests
 */
public class Capability {

	/**
	 * Convenience method to canonicalise a JSON capability expression
	 * 
	 * @param capability: a capability string, which is the JSON text for the capability
	 * @return a capability string which has been canonicalised
	 * @throws AblyException if there is an error processing the given string
	 * (if for example it is not valid JSON)
	 */
	public static final String c14n(String capability) throws AblyException {
		try {
			return (capability == null || capability.isEmpty()) ? "" : (new Capability(new JSONObject(capability))).toString();
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	/**
	 * Construct a new empty Capability
	 */
	public Capability() {
		json = new JSONObject();
	}

	/**
	 * Private constructor; create a new Capability instance given a JSONObject
	 *
	 * @param json the JSONObject
	 */
	private Capability(JSONObject json) {
		this.json = json;
		dirty = true;
	}

	/**
	 * Add a resource to an existing Capability instance with the
	 * given set of operations. If the resource already exists,
	 * it is wholly replaced by the given set of operations.
	 *
	 * @param resource the resource string
	 * @param ops a String[] of the operations permitted for this resource;
	 * the array does not need to be sorted
	 */
	public void addResource(String resource, String[] ops) {
		try {
			JSONArray jsonOps = JSONHelpers.newJSONArray(ops);
			json.put(resource, jsonOps);
			dirty = true;
		} catch (JSONException e) {}
	}

	/**
	 * Add a resource to an existing Capability instance with the
	 * given single operation. If the resource already exists,
	 * it is wholly replaced by the given set of operations.
	 *
	 * @param resource the resource string
	 * @param op a single operation String to be permitted for this resource;
	 */
	public void addResource(String resource, String op) {
		addResource(resource, new String[]{op});
	}

	/**
	 * Add a resource to an existing Capability instance with an
	 * empty set of operations. If the resource already exists,
	 * the effect is to reset its set of operations to empty.
	 *
	 * @param resource the resource string
	 */
	public void addResource(String resource) {
		addResource(resource, new String[0]);
	}
	/**
	 * Remove a resource from an existing Capability instance
	 *
	 * @param resource the (possibly existing) resource
	 */
	public void removeResource(String resource) {
		json.remove(resource);
		/* removal doesn't break the sort order, so dirty can remain true */
	}

	/**
	 * Add an operation to an existing Capability instance for a
	 * given resource. If the resource does not already exist,
	 * it is created.
	 *
	 * @param resource the resource string
	 * @param op a single operation String to be added for this resource;
	 */
	public void addOperation(String resource, String op) {
		try {
			JSONArray jsonOps = json.optJSONArray(resource);
			if(jsonOps == null) {
				jsonOps = new JSONArray();
				json.put(resource, jsonOps);
			}
			int opCount = jsonOps.length();
			for(int i = 0; i < opCount; i++)
				if(jsonOps.optString(i).equals(op))
					return;

			jsonOps.put(op);
			dirty = true;
		} catch (JSONException e) {}
	}

	/**
	 * Remove an operation for a given resource.
	 * If the resource becomes empty as a result,
	 * it is removed.
	 *
	 * @param resource the resource string
	 * @param op a operation String to be removed for this resource;
	 */
	public void removeOperation(String resource, String op) {
		JSONArray jsonOps = json.optJSONArray(resource);
		if(jsonOps == null)
			return;

		int opCount = jsonOps.length();
		for(int i = 0; i < opCount; i++)
			if(jsonOps.optString(i).equals(op)) {
				if(opCount == 1) {
					json.remove(resource);
				} else {
					jsonOps = JSONHelpers.remove(jsonOps, i);
					try { json.put(resource, jsonOps); } catch (JSONException e) {}
				}
				return;
			}
		/* removal doesn't break sort order, so dirty can remain false */
	}
	
	/**
	 * Get the canonicalised String text for a Capability instance.
	 * The json object and its members are sorted if the object has been modified
	 * since the last time it was canonicalised.
	 *
	 * @return the canonicalised String text
	 */
	public String toString() {
		if(dirty) {
			String[] resources = JSONHelpers.getNames(json);
			Arrays.sort(resources);
			if(resources.length == 0)
				return "";
			JSONObject c14nJson = new JSONObject();
			try {
				for(String resource : resources) {
					JSONArray jsonOps = json.optJSONArray(resource);
					int count = jsonOps.length();
					String[] ops = new String[count];
					for(int i = 0; i < count; i++)
						ops[i] = jsonOps.optString(i);
					Arrays.sort(ops);
					c14nJson.put(resource, JSONHelpers.newJSONArray(ops));
				}
			} catch (JSONException e) {}
			json = c14nJson;
			dirty = false;
		}
		return json.toString();
	}

	private JSONObject json;
	private boolean dirty;
}
