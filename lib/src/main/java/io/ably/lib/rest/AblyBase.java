package io.ably.lib.rest;

import io.ably.annotation.Experimental;
import io.ably.lib.http.AsyncHttpPaginatedQuery;
import io.ably.lib.http.AsyncHttpScheduler;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpPaginatedQuery;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.SyncHttpScheduler;
import io.ably.lib.platform.Platform;
import io.ably.lib.push.Push;
import io.ably.lib.realtime.Connection;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncHttpPaginatedResponse;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.HttpPaginatedResponse;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageSerializer;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PublishResponse;
import io.ably.lib.types.ReadOnlyMap;
import io.ably.lib.types.Stats;
import io.ably.lib.types.StatsReader;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.HttpCode;
import io.ably.lib.util.InternalMap;
import io.ably.lib.util.Log;
import io.ably.lib.util.PlatformAgentProvider;
import io.ably.lib.util.Serialisation;

/**
 * A client that offers a simple stateless API to interact directly with Ably's REST API.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public abstract class AblyBase implements AutoCloseable {

    public final ClientOptions options;
    public final Http http;
    public final HttpCore httpCore;

    /**
     * An {@link Auth} object.
     * <p>
     * Spec: RSC5
     */
    public final Auth auth;
    /**
     * An {@link Channels} object.
     * <p>
     * Spec: RSN1
     */
    public final Channels channels;
    public final Platform platform;
    /**
     * An {@link Push} object.
     * <p>
     * Spec: RSH7
     */
    public final Push push;
    protected final PlatformAgentProvider platformAgentProvider;

    /**
     * Constructs a client object using an Ably API key or token string.
     * <p>
     * Spec: RSC1
     * @param key The Ably API key or token string used to validate the client.
     * @param platformAgentProvider provides platform agent for the agent header.
     * @throws AblyException
     */
    public AblyBase(String key, PlatformAgentProvider platformAgentProvider) throws AblyException {
        this(new ClientOptions(key), platformAgentProvider);
    }

    /**
     * Construct a client object using an Ably {@link ClientOptions} object.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object to configure the client connection to Ably.
     * @param platformAgentProvider provides platform agent for the agent header.
     * @throws AblyException
     */
    public AblyBase(ClientOptions options, PlatformAgentProvider platformAgentProvider) throws AblyException {
        /* normalise options */
        if(options == null) {
            String msg = "no options provided";
            Log.e(getClass().getName(), msg);
            throw AblyException.fromErrorInfo(new ErrorInfo(msg, HttpCode.BAD_REQUEST, 40000));
        }
        this.options = options;

        /* process options */
        Log.setLevel(options.logLevel);
        Log.setHandler(options.logHandler);
        Log.i(getClass().getName(), "started");

        this.platformAgentProvider = platformAgentProvider;
        auth = new Auth(this, options);
        httpCore = new HttpCore(options, auth, this.platformAgentProvider);
        http = new Http(new AsyncHttpScheduler(httpCore, options), new SyncHttpScheduler(httpCore));

        channels = new InternalChannels();

        platform = new Platform();
        push = new Push(this);
    }

    /**
     * Causes the connection to close, entering the [{@link io.ably.lib.realtime.ConnectionState#closing} state.
     * Once closed, the library does not attempt to re-establish the connection without an explicit call to
     * {@link Connection#connect()}.
     * <p>
     * Spec: RTN12
     * @throws Exception
     */
    @Override
    public void close() throws Exception {
        http.close();
    }

    /**
     * A collection of Channels associated with an Ably instance.
     */
    public interface Channels extends ReadOnlyMap<String, Channel> {
        Channel get(String channelName);
        Channel get(String channelName, ChannelOptions channelOptions) throws AblyException;
        void release(String channelName);
        int size();
        Iterable<Channel> values();
    }

    private class InternalChannels extends InternalMap<String, Channel> implements Channels {
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
     * Retrieves the time from the Ably service as milliseconds
     * since the Unix epoch. Clients that do not have access
     * to a sufficiently well maintained time source and wish
     * to issue Ably {@link Auth.TokenRequest} with
     * a more accurate timestamp should use the
     * {@link ClientOptions#queryTime} property instead of this method.
     * <p>
     * Spec: RSC16
     * @return The time as milliseconds since the Unix epoch.
     * @throws AblyException
     */
    public long time() throws AblyException {
        return timeImpl().sync().longValue();
    }

    /**
     * Asynchronously retrieves the time from the Ably service as milliseconds
     * since the Unix epoch. Clients that do not have access
     * to a sufficiently well maintained time source and wish
     * to issue Ably {@link Auth.TokenRequest} with
     * a more accurate timestamp should use the
     * {@link ClientOptions#queryTime} property instead of this method.
     * <p>
     * Spec: RSC16
     * @param callback Listener with the time as milliseconds since the Unix epoch.
     * <p>
     * This callback is invoked on a background thread
     */
    public void timeAsync(Callback<Long> callback) {
        timeImpl().async(callback);
    }

    private Http.Request<Long> timeImpl() {
        final Param[] params = this.options.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null; // RSC7c
        return http.request(new Http.Execute<Long>() {
            @Override
            public void execute(HttpScheduler http, Callback<Long> callback) throws AblyException {
                http.get("/time", HttpUtils.defaultAcceptHeaders(false), params, new HttpCore.ResponseHandler<Long>() {
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
     * Queries the REST /stats API and retrieves your application's usage statistics.
     * @param params query options:
     * <p>
     * start (RSC6b1) - The time from which stats are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RSC6b1) - The time until stats are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RSC6b2) - The order for which stats are returned in. Valid values are backwards which orders stats from most recent to oldest,
     * or forwards which orders stats from oldest to most recent. The default is backwards.
     * <p>
     * limit (RSC6b3) - An upper limit on the number of stats returned. The default is 100, and the maximum is 1000.
     * <p>
     * unit (RSC6b4) - minute, hour, day or month. Based on the unit selected, the given start or end times are rounded down to the start of the relevant interval depending on the unit granularity of the query.)
     * <p>
     * Spec: RSC6a
     * @return A {@link PaginatedResult} object containing an array of {@link Stats} objects.
     * @throws AblyException
     */
    public PaginatedResult<Stats> stats(Param[] params) throws AblyException {
        return new PaginatedQuery<Stats>(http, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler).get();
    }

    /**
     * Asynchronously queries the REST /stats API and retrieves your application's usage statistics.
     * @param params query options:
     * <p>
     * start (RSC6b1) - The time from which stats are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * end (RSC6b1) - The time until stats are retrieved, specified as milliseconds since the Unix epoch.
     * <p>
     * direction (RSC6b2) - The order for which stats are returned in. Valid values are backwards which orders stats from most recent to oldest,
     * or forwards which orders stats from oldest to most recent. The default is backwards.
     * <p>
     * limit (RSC6b3) - An upper limit on the number of stats returned. The default is 100, and the maximum is 1000.
     * <p>
     * unit (RSC6b4) - minute, hour, day or month. Based on the unit selected, the given start or end times are rounded down to the start of the relevant interval depending on the unit granularity of the query.)
     * <p>
     * Spec: RSC6a
     * @param callback Listener which returns a {@link AsyncPaginatedResult} object containing an array of {@link Stats} objects.
     * <p>
     * This callback is invoked on a background thread
     */
    public void statsAsync(Param[] params, Callback<AsyncPaginatedResult<Stats>> callback)  {
        (new AsyncPaginatedQuery<Stats>(http, "/stats", HttpUtils.defaultAcceptHeaders(false), params, StatsReader.statsResponseHandler)).get(callback);
    }

    /**
     * Makes a REST request to a provided path. This is provided as a convenience
     * for developers who wish to use REST API functionality that is either not
     * documented or is not yet included in the public API, without having to
     * directly handle features such as authentication, paging, fallback hosts,
     * MsgPack and JSON support.
     * <p>
     * Spec: RSC19
     * @param method The request method to use, such as GET, POST.
     * @param path The request path.
     * @param params The parameters to include in the URL query of the request.
     *               The parameters depend on the endpoint being queried.
     *               See the <a href="https://ably.com/docs/api/rest-api">REST API reference</a>
     *               for the available parameters of each endpoint.
     * @param body The RequestBody of the request.
     * @param headers Additional HTTP headers to include in the request.
     * @return An {@link HttpPaginatedResponse} object returned by the HTTP request, containing an empty or JSON-encodable object.
     * @throws AblyException if it was not possible to complete the request, or an error response was received
     */
    public HttpPaginatedResponse request(String method, String path, Param[] params, HttpCore.RequestBody body, Param[] headers) throws AblyException {
        headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
        return new HttpPaginatedQuery(http, method, path, headers, params, body).exec();
    }

    /**
     * Makes a async REST request to a provided path. This is provided as a convenience
     * for developers who wish to use REST API functionality that is either not
     * documented or is not yet included in the public API, without having to
     * directly handle features such as authentication, paging, fallback hosts,
     * MsgPack and JSON support.
     * <p>
     * Spec: RSC19
     * @param method The request method to use, such as GET, POST.
     * @param path The request path.
     * @param params The parameters to include in the URL query of the request.
     *               The parameters depend on the endpoint being queried.
     *               See the <a href="https://ably.com/docs/api/rest-api">REST API reference</a>
     *               for the available parameters of each endpoint.
     * @param body The RequestBody of the request.
     * @param headers Additional HTTP headers to include in the request.
     * @param callback called with the asynchronous result,
     *                 returns an {@link AsyncHttpPaginatedResponse} object returned by the HTTP request,
     *                 containing an empty or JSON-encodable object.
     * <p>
     * This callback is invoked on a background thread
     */
    public void requestAsync(String method, String path, Param[] params, HttpCore.RequestBody body, Param[] headers, final AsyncHttpPaginatedResponse.Callback callback)  {
        headers = HttpUtils.mergeHeaders(HttpUtils.defaultAcceptHeaders(false), headers);
        (new AsyncHttpPaginatedQuery(http, method, path, headers, params, body)).exec(callback);
    }

    /**
     * Publish an array of {@link Message.Batch} objects to one or more channels, up to a maximum of 100 channels.
     * Each {@link Message.Batch} object can contain a single message or an array of messages.
     * Returns an array of {@link PublishResponse} object.
     * <p>
     * Spec: BO2a
     * @param pubSpecs An array of {@link Message.Batch} objects.
     * @param channelOptions A {@link ClientOptions} object to configure the client connection to Ably.
     * @return A {@link PublishResponse} object.
     * @throws AblyException
     */
    @Experimental
    public PublishResponse[] publishBatch(Message.Batch[] pubSpecs, ChannelOptions channelOptions) throws AblyException {
        return publishBatchImpl(pubSpecs, channelOptions, null).sync();
    }

    /**
     * Publish an array of {@link Message.Batch} objects to one or more channels, up to a maximum of 100 channels.
     * Each {@link Message.Batch} object can contain a single message or an array of messages.
     * Returns an array of {@link PublishResponse} object.
     * <p>
     * Spec: BO2a
     * @param pubSpecs An array of {@link Message.Batch} objects.
     * @param channelOptions A {@link ClientOptions} object to configure the client connection to Ably.
     * @param params params to pass into the initial query
     * @return A {@link PublishResponse} object.
     * @throws AblyException
     */
    @Experimental
    public PublishResponse[] publishBatch(Message.Batch[] pubSpecs, ChannelOptions channelOptions, Param[] params) throws AblyException {
        return publishBatchImpl(pubSpecs, channelOptions, params).sync();
    }

    /**
     * Asynchronously publish an array of {@link Message.Batch} objects to one or more channels, up to a maximum of 100 channels.
     * Each {@link Message.Batch} object can contain a single message or an array of messages.
     * Returns an array of {@link PublishResponse} object.
     * <p>
     * Spec: BO2a
     * @param pubSpecs An array of {@link Message.Batch} objects.
     * @param channelOptions A {@link ClientOptions} object to configure the client connection to Ably.
     * @param callback callback A callback with {@link PublishResponse} object.
     * <p>
     * This callback is invoked on a background thread
     * @throws AblyException
     */
    @Experimental
    public void publishBatchAsync(Message.Batch[] pubSpecs, ChannelOptions channelOptions, final Callback<PublishResponse[]> callback) throws AblyException {
        publishBatchImpl(pubSpecs, channelOptions, null).async(callback);
    }

    /**
     * Asynchronously publish an array of {@link Message.Batch} objects to one or more channels, up to a maximum of 100 channels.
     * Each {@link Message.Batch} object can contain a single message or an array of messages.
     * Returns an array of {@link PublishResponse} object.
     * <p>
     * Spec: BO2a
     * @param pubSpecs An array of {@link Message.Batch} objects.
     * @param channelOptions A {@link ClientOptions} object to configure the client connection to Ably.
     * @param params params to pass into the initial query
     * @param callback A callback with {@link PublishResponse} object.
     * <p>
     * This callback is invoked on a background thread
     * @throws AblyException
     */
    @Experimental
    public void publishBatchAsync(Message.Batch[] pubSpecs, ChannelOptions channelOptions, Param[] params, final Callback<PublishResponse[]> callback) throws AblyException {
        publishBatchImpl(pubSpecs, channelOptions, params).async(callback);
    }

    private Http.Request<PublishResponse[]> publishBatchImpl(final Message.Batch[] pubSpecs, ChannelOptions channelOptions, final Param[] initialParams) throws AblyException {
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
                String messageId = Crypto.getRandomId();
                for (int i = 0; i < spec.messages.length; i++) {
                    spec.messages[i].id = messageId + ':' + i;
                }
            }
        }
        return http.request(new Http.Execute<PublishResponse[]>() {
            @Override
            public void execute(HttpScheduler http, final Callback<PublishResponse[]> callback) throws AblyException {
                HttpCore.RequestBody requestBody = options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(pubSpecs) : MessageSerializer.asJSONRequest(pubSpecs);
                final Param[] params = options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams ; // RSC7c
                http.post("/messages", HttpUtils.defaultAcceptHeaders(options.useBinaryProtocol), params, requestBody, new HttpCore.ResponseHandler<PublishResponse[]>() {
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
     * Override this method in AblyRealtime and pass updated token to ConnectionManager
     * @param token new token
     * @param authUpdateResult Callback result
     */
    protected void onAuthUpdatedAsync(String token, Auth.AuthUpdateResult authUpdateResult)  {
        //this must be overriden by subclass
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
