package io.ably.lib.rest;

import java.util.Collection;
import java.util.HashMap;

import com.google.gson.JsonElement;

import io.ably.lib.http.AsyncHttp;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.types.*;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

/**
 * AblyRest
 * The top-level class to be instanced for the Ably REST library.
 *
 */
public class AblyRest {
	
	public final ClientOptions options;
	final String clientId;
	public final Http http;
	public final AsyncHttp asyncHttp;

	public final Auth auth;
	public final Channels channels;

	/**
	 * Instance the Ably library using a key only.
	 * This is simply a convenience constructor for the
	 * simplest case of instancing the library with a key
	 * for basic authentication and no other options.
	 * @param key; String key (obtained from application dashboard)
	 * @throws AblyException
	 */
	public AblyRest(String key) throws AblyException {
		this(new ClientOptions(key));
	}

	/**
	 * Instance the Ably library with the given options.
	 * @param options: see {@link io.ably.lib.types.ClientOptions} for options
	 * @throws AblyException
	 */
	public AblyRest(ClientOptions options) throws AblyException {
		/* normalise options */
		if(options == null) {
			String msg = "no options provided";
			Log.e(getClass().getName(), msg);
			throw AblyException.fromErrorInfo(new ErrorInfo(msg, 400, 40000));
		}
		this.options = options;

		/* process options */
		Log.setLevel(options.logLevel);
		Log.setHandler(options.logHandler);
		Log.i(getClass().getName(), "started");
		this.clientId = options.clientId;

		auth = new Auth(this, options);
		http = new Http(options, auth);
		asyncHttp = new AsyncHttp(http);
		channels = new Channels();
	}

	/**
	 * A collection of Channels associated with an Ably instance.
	 *
	 */
	public class Channels extends HashMap<String, Channel> {
		public Channel get(String channelName) {
			try {
				return get(channelName, null);
			} catch (AblyException e) { return null; }
		}
		public Channel get(String channelName, ChannelOptions channelOptions) throws AblyException {
			Channel channel = super.get(channelName);
			if (channel != null) {
				if (channelOptions != null)
					channel.options = channelOptions;
				return channel;
			}

			channel = new Channel(AblyRest.this, channelName, channelOptions);
			super.put(channelName, channel);
			return channel;
		}

		public void release(String channelName) {
			super.remove(channelName);
		}
	}

	/**
	 * Obtain the time from the Ably service.
	 * This may be required on clients that do not have access
	 * to a sufficiently well maintained time source, to provide 
	 * timestamps for use in token requests
	 * @return time in millis since the epoch
	 * @throws AblyException
	 */
	public long time() throws AblyException {
		return http.get("/time", HttpUtils.defaultAcceptHeaders(false), null, new ResponseHandler<Long>() {
			@Override
			public Long handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
				return (Long)Serialisation.gson.fromJson(new String(body), Long[].class)[0];
			}}).longValue();
	}

	/**
	 * Asynchronously obtain the time from the Ably service.
	 * This may be required on clients that do not have access
	 * to a sufficiently well maintained time source, to provide 
	 * timestamps for use in token requests
	 * @param callback
	 */
	public void timeAsync(Callback<Long> callback) {
		asyncHttp.get("/time", HttpUtils.defaultAcceptHeaders(false), null, new ResponseHandler<Long>() {
			@Override
			public Long handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
				return Serialisation.gson.fromJson(new String(body), Long[].class)[0];
			}
		}, callback);
	}

	/**
	 * Request usage statistics for this application. Returned stats
	 * are application-wide and not just relating to this instance.
	 * @param params query options: see Ably REST API documentation
	 * for available options
	 * @return a PaginatedResult of Stats records for the requested params
	 * @throws AblyException
	 */
	public PaginatedResult<Stats> stats(Param[] params) throws AblyException {
		return new PaginatedQuery<Stats>(http, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler).get();
	}

	/**
	 * Asynchronously obtain usage statistics for this application using the REST API.
	 * @param params: the request params. See the Ably REST API
	 * @param callback
	 * @return
	 */
	public void statsAsync(Param[] params, Callback<AsyncPaginatedResult<Stats>> callback)  {
		(new AsyncPaginatedQuery<Stats>(asyncHttp, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler)).get(callback);
	}

	/**
	 * Make a generic HTTP request for a collection of the given type
	 * @param params query options: see Ably REST API documentation
	 * for available options
	 * @return a PaginatedResult of Stats records for the requested params
	 * @throws AblyException
	 */
	public PaginatedResult<JsonElement> paginatedRequest(String method, String path, Param[] params, RequestBody body, Param[] headers) throws AblyException {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		return new PaginatedQuery<JsonElement>(http, path, headers, params, HttpUtils.jsonArrayResponseHandler).exec(method);
	}

	/**
	 * Asynchronously obtain usage statistics for this application using the REST API.
	 * @param params: the request params. See the Ably REST API
	 * @param callback
	 * @return
	 */
	public void paginatedRequestAsync(String method, String path, Param[] params, RequestBody body, Param[] headers, Callback<AsyncPaginatedResult<JsonElement>> callback)  {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		(new AsyncPaginatedQuery<JsonElement>(asyncHttp, path, headers, params, HttpUtils.jsonArrayResponseHandler)).exec(method, callback);
	}

	/**
	 * Make a generic HTTP request for a collection of JsonElements
	 * @param params query options: see Ably REST API documentation
	 * for available options
	 * @return a PaginatedResult of JsonElement records for the requested params
	 * @throws AblyException
	 */
	public JsonElement request(String method, String path, Param[] params, RequestBody body, Param[] headers) throws AblyException {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		return http.exec(path, method, headers, params, body, HttpUtils.jsonResponseHandler);
	}

	/**
	 * Asynchronously make a generic HTTP request for a JsonElement
	 * @param params: the request params. See the Ably REST API
	 * @param callback
	 */
	public void requestAsync(String method, String path, Param[] params, RequestBody requestBody, Param[] headers, Callback<JsonElement> callback)  {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		asyncHttp.exec(path, method, headers, params, requestBody, HttpUtils.jsonResponseHandler, callback);
	}

	/**
	 * Authentication token has changed. waitForResult is true if there is a need to
	 * wait for server response to auth request
	 */

	/**
	 * Override this method in AblyRealtime and pass updated token to ConnectionManager
	 * @param token new token
	 * @param waitForResponse wait for server response before returning from method
	 * @throws AblyException
	 */
	protected void onAuthUpdated(String token, boolean waitForResponse) throws AblyException {
		/* Default is to do nothing. Overridden by subclass. */
	}

	/**
	 * Authentication error occurred
	 */
	protected void onAuthError(ErrorInfo errorInfo) {
		/* Default is to do nothing. Overridden by subclass. */
	}
}
