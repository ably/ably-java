package io.ably.lib.rest;

import java.util.HashMap;

import io.ably.lib.http.AsyncHttp;
import io.ably.lib.http.AsyncHttpPaginatedQuery;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.Response;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.http.HttpPaginatedQuery;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncHttpPaginatedResponse;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.HttpPaginatedResponse;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.Stats;
import io.ably.lib.types.StatsReader;
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
		private static final long serialVersionUID = 1L;

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
			public Long handleResponse(Response response, ErrorInfo error) throws AblyException {
				if(error != null) {
					throw AblyException.fromErrorInfo(error);
				}
				return (Long)Serialisation.gson.fromJson(new String(response.body), Long[].class)[0];
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
			public Long handleResponse(Response response, ErrorInfo error) throws AblyException {
				if(error != null) {
					throw AblyException.fromErrorInfo(error);
				}
				return Serialisation.gson.fromJson(new String(response.body), Long[].class)[0];
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
	 * Make a generic HTTP request against an endpoint representing a collection
	 * of some type; this is to provide a forward compatibility path for new APIs.
	 * @param method: the HTTP method to use (see constants in io.ably.lib.http.Http)
	 * @param path: the path component of the resource URI
	 * @param params (optional; may be null): any parameters to send with the request; see API-specific documentation
	 * @param body (optional; may be null): an instance of RequestBody; either a JSONRequestBody or ByteArrayRequestBody
	 * @param headers (optional; may be null): any additional headers to send; see API-specific documentation
	 * @return a page of results, each represented as a JsonElement
	 * @throws AblyException if it was not possible to complete the request, or an error response was received
	 */
	public HttpPaginatedResponse request(String method, String path, Param[] params, RequestBody body, Param[] headers) throws AblyException {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		return new HttpPaginatedQuery(http, method, path, headers, params, body).exec();
	}

	/**
	 * Make an async generic HTTP request against an endpoint representing a collection
	 * of some type; this is to provide a forward compatibility path for new APIs.
	 * @param method: the HTTP method to use (see constants in io.ably.lib.http.Http)
	 * @param path: the path component of the resource URI
	 * @param params (optional; may be null): any parameters to send with the request; see API-specific documentation
	 * @param body (optional; may be null): an instance of RequestBody; either a JSONRequestBody or ByteArrayRequestBody
	 * @param headers (optional; may be null): any additional headers to send; see API-specific documentation
	 * @param callback: called with the asynchronous result
	 */
	public void requestAsync(String method, String path, Param[] params, RequestBody body, Param[] headers, final AsyncHttpPaginatedResponse.Callback callback)  {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		(new AsyncHttpPaginatedQuery(asyncHttp, method, path, headers, params, body)).exec(callback);
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
