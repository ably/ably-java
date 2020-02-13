package io.ably.lib.rest;

import java.util.HashMap;
import java.util.Map;

import io.ably.annotation.Experimental;
import io.ably.lib.http.AsyncHttpScheduler;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.SyncHttpScheduler;
import io.ably.lib.http.AsyncHttpPaginatedQuery;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.http.HttpPaginatedQuery;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.platform.Platform;
import io.ably.lib.push.Push;
import io.ably.lib.types.*;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

/**
 * AblyBase
 * The top-level class to be instanced for the Ably REST library.
 *
 */
public abstract class AblyBase {

	public final ClientOptions options;
	public final Http http;
	public final HttpCore httpCore;

	public final Auth auth;
	public final Channels channels;
	public final Platform platform;
	public final Push push;

	/**
	 * Instance the Ably library using a key only.
	 * This is simply a convenience constructor for the
	 * simplest case of instancing the library with a key
	 * for basic authentication and no other options.
	 * @param key; String key (obtained from application dashboard)
	 * @throws AblyException
	 */
	public AblyBase(String key) throws AblyException {
		this(new ClientOptions(key));
	}

	/**
	 * Instance the Ably library with the given options.
	 * @param options: see {@link io.ably.lib.types.ClientOptions} for options
	 * @throws AblyException
	 */
	public AblyBase(ClientOptions options) throws AblyException {
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

		auth = new Auth(this, options);
		httpCore = new HttpCore(options, auth);
		http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));
		
		channels = new InternalChannels();

		platform = new Platform();
		push = new Push(this);
	}

	/**
	 * A collection of Channels associated with an Ably instance.
	 */
	public interface Channels {
		Channel get(String channelName);
		Channel get(String channelName, ChannelOptions channelOptions) throws AblyException;
		void release(String channelName);
	}

	private class InternalChannels implements Channels {
		private final Map<String, Channel> map = new HashMap<>();
		
		@Override
		public Channel get(String channelName) {
			try {
				return get(channelName, null);
			} catch (AblyException e) { return null; }
		}

		@Override
		public Channel get(String channelName, ChannelOptions channelOptions) throws AblyException {
			Channel channel = map.get(channelName);
			if (channel != null) {
				if (channelOptions != null)
					channel.options = channelOptions;
				return channel;
			}

			channel = new Channel(AblyBase.this, channelName, channelOptions);
			map.put(channelName, channel);
			return channel;
		}

		@Override
		public void release(String channelName) {
			map.remove(channelName);
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
		return timeImpl().sync().longValue();
	}

	/**
	 * Asynchronously obtain the time from the Ably service.
	 * This may be required on clients that do not have access
	 * to a sufficiently well maintained time source, to provide
	 * timestamps for use in token requests
	 * @param callback
	 */
	public void timeAsync(Callback<Long> callback) {
		timeImpl().async(callback);
	}

	private Http.Request<Long> timeImpl() {
		return http.request(new Http.Execute<Long>() {
			@Override
			public void execute(HttpScheduler http, Callback<Long> callback) throws AblyException {
				http.get("/time", HttpUtils.defaultAcceptHeaders(false), null, new HttpCore.ResponseHandler<Long>() {
					@Override
					public Long handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
						if(error != null) {
							throw AblyException.fromErrorInfo(error);
						}
						return Serialisation.gson.fromJson(new String(response.body), Long[].class)[0];
					}
				}, false, callback);
			}
		});
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
		(new AsyncPaginatedQuery<Stats>(http, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler)).get(callback);
	}

	/**
	 * Make a generic HTTP request against an endpoint representing a collection
	 * of some type; this is to provide a forward compatibility path for new APIs.
	 * @param method: the HTTP method to use (see constants in io.ably.lib.httpCore.HttpCore)
	 * @param path: the path component of the resource URI
	 * @param params (optional; may be null): any parameters to send with the request; see API-specific documentation
	 * @param body (optional; may be null): an instance of RequestBody; either a JSONRequestBody or ByteArrayRequestBody
	 * @param headers (optional; may be null): any additional headers to send; see API-specific documentation
	 * @return a page of results, each represented as a JsonElement
	 * @throws AblyException if it was not possible to complete the request, or an error response was received
	 */
	public HttpPaginatedResponse request(String method, String path, Param[] params, HttpCore.RequestBody body, Param[] headers) throws AblyException {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		return new HttpPaginatedQuery(http, method, path, headers, params, body).exec();
	}

	/**
	 * Make an async generic HTTP request against an endpoint representing a collection
	 * of some type; this is to provide a forward compatibility path for new APIs.
	 * @param method: the HTTP method to use (see constants in io.ably.lib.httpCore.HttpCore)
	 * @param path: the path component of the resource URI
	 * @param params (optional; may be null): any parameters to send with the request; see API-specific documentation
	 * @param body (optional; may be null): an instance of RequestBody; either a JSONRequestBody or ByteArrayRequestBody
	 * @param headers (optional; may be null): any additional headers to send; see API-specific documentation
	 * @param callback: called with the asynchronous result
	 */
	public void requestAsync(String method, String path, Param[] params, HttpCore.RequestBody body, Param[] headers, final AsyncHttpPaginatedResponse.Callback callback)  {
		headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
		(new AsyncHttpPaginatedQuery(http, method, path, headers, params, body)).exec(callback);
	}

	/**
	 * Publish a messages on one or more channels. When there are
	 * messages to be sent on multiple channels simultaneously,
	 * it is more efficient to use this method to publish them in
	 * a single request, as compared with publishing via multiple
	 * independent requests.
	 * @throws AblyException
	 */
	@Experimental
	public PublishResponse[] publishBatch(Message.Batch[] pubSpecs, ChannelOptions channelOptions) throws AblyException {
		return publishBatchImpl(pubSpecs, channelOptions).sync();
	}

	@Experimental
	public void publishBatchAsync(Message.Batch[] pubSpecs, ChannelOptions channelOptions, final Callback<PublishResponse[]> callback) throws AblyException {
		publishBatchImpl(pubSpecs, channelOptions).async(callback);
	}

	private Http.Request<PublishResponse[]> publishBatchImpl(final Message.Batch[] pubSpecs, ChannelOptions channelOptions) throws AblyException {
		boolean hasClientSuppliedId = false;
		for(Message.Batch spec : pubSpecs) {
			for(Message message : spec.messages) {
				/* handle message ids */
				/* RSL1k2 */
				hasClientSuppliedId |= (message.id != null);
				/* RTL6g3 */
				auth.checkClientId(message, true, false);
				message.encode(channelOptions);
			}
			if(!hasClientSuppliedId && options.idempotentRestPublishing) {
				/* RSL1k1: populate the message id with a library-generated id */
				String messageId = Crypto.getRandomMessageId();
				for (int i = 0; i < spec.messages.length; i++) {
					spec.messages[i].id = messageId + ':' + i;
				}
			}
		}
		return http.request(new Http.Execute<PublishResponse[]>() {
			@Override
			public void execute(HttpScheduler http, final Callback<PublishResponse[]> callback) throws AblyException {
				HttpCore.RequestBody requestBody = options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(pubSpecs) : MessageSerializer.asJSONRequest(pubSpecs);
				http.post("/messages", HttpUtils.defaultAcceptHeaders(options.useBinaryProtocol), null, requestBody, new HttpCore.ResponseHandler<PublishResponse[]>() {
					@Override
					public PublishResponse[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
						if(error != null && error.code != 40020) {
							throw AblyException.fromErrorInfo(error);
						}
						return PublishResponse.getBulkPublishResponseHandler(response.statusCode).handleResponseBody(response.contentType, response.body);
					}
				}, true, callback);
			}
		});
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

	/**
	 * clientId set by late initialisation
	 */
	protected void onClientIdSet(String clientId) {
		/* Default is to do nothing. Overridden by subclass. */
	}

}
