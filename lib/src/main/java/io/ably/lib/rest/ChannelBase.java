package io.ably.lib.rest;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.push.Push;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageSerializer;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.PresenceSerializer;
import io.ably.lib.util.Crypto;

/**
 * A class representing a Channel in the Ably REST API.
 * In the REST API, the library is essentially stateless;
 * a Channel object simply represents a channel for making
 * REST requests, and existence of a channel does not
 * signify that there is a realtime connection or attachment
 * to that channel.
 */
public class ChannelBase {

    /**
     * The Channel name
     */
    public final String name;

    /**
     * The presence instance for this channel.
     */
    public final Presence presence;

    /**
     * Publish a message on this channel using the REST API.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     * @param name the event name
     * @param data the message payload; see {@link io.ably.types.Data} for
     * details of supported data types.
     * @throws AblyException
     */
    public void publish(String name, Object data) throws AblyException {
        publishImpl(name, data).sync();
    }

    /**
     * Publish a message on this channel using the REST API.
     * Since the REST API is stateless, this request is made independently
     * of any other request on this or any other channel.
     *
     * @param name the event name
     * @param data the message payload; see {@link io.ably.types.Data} for
     * @param listener a listener to be notified of the outcome of this message.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void publishAsync(String name, Object data, CompletionListener listener) {
        publishImpl(name, data).async(new CompletionListener.ToCallback(listener));
    }

    private Http.Request<Void> publishImpl(String name, Object data) {
        return publishImpl(new Message[] {new Message(name, data)});
    }

    /**
     * Publish an array of messages on this channel. When there are
     * multiple messages to be sent, it is more efficient to use this
     * method to publish them in a single request, as compared with
     * publishing via multiple independent requests.
     * @param messages array of messages to publish.
     * @throws AblyException
     */
    public void publish(final Message[] messages) throws AblyException {
        publishImpl(messages).sync();
    }

    /**
     * Asynchronously publish an array of messages on this channel
     *
     * @param messages the message
     * @param listener a listener to be notified of the outcome of this message.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void publishAsync(final Message[] messages, final CompletionListener listener) {
        publishImpl(messages).async(new CompletionListener.ToCallback(listener));
    }

    private Http.Request<Void> publishImpl(final Message[] messages) {
        return ably.http.request(new Http.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, final Callback<Void> callback) throws AblyException {
                /* handle message ids */
                boolean hasClientSuppliedId = false;
                for(Message message : messages) {
                    /* RSL1k2 */
                    hasClientSuppliedId |= (message.id != null);
                    /* RTL6g3 */
                    ably.auth.checkClientId(message, true, false);
                    message.encode(options);
                }
                if(!hasClientSuppliedId && ably.options.idempotentRestPublishing) {
                    /* RSL1k1: populate the message id with a library-generated id */
                    String messageId = Crypto.getRandomId();
                    for (int i = 0; i < messages.length; i++) {
                        messages[i].id = messageId + ':' + i;
                    }
                }

                HttpCore.RequestBody requestBody = ably.options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(messages) : MessageSerializer.asJsonRequest(messages);
                final Param[] params = ably.options.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null; // RSC7c

                http.post(basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, requestBody, null, true, callback);
            }
        });
    }

    /**
     * Obtain recent history for this channel using the REST API.
     * The history provided relqtes to all clients of this application,
     * not just this instance.
     * @param params the request params. See the Ably REST API
     * documentation for more details.
     * @return an array of Messages for this Channel.
     * @throws AblyException
     */
    public PaginatedResult<Message> history(Param[] params) throws AblyException {
        return historyImpl(params).sync();
    }

    /**
     * Asynchronously obtain recent history for this channel using the REST API.
     * @param params the request params. See the Ably REST API
     * @param callback
     * @return
     */
    public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
        historyImpl(params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<Message> historyImpl(Param[] initialParams) {
        HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
        final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
        return (new BasePaginatedQuery<Message>(ably.http, basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
    }

    /**
     * Enables the retrieval of the current and historic presence set for a channel.
     */
    public class Presence {

        /**
         * Retrieves the current members present on the channel and the metadata for each member,
         * such as their {@link io.ably.lib.types.PresenceMessage.Action} and ID. Returns a {@link PaginatedResult} object,
         * containing an array of {@link PresenceMessage} objects.
         * <p>
         * Spec: RSPa
         * @param params the request params:
         * <p>
         * limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * <p>
         * clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
         * <p>
         * connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
         * @return A {@link PaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * @throws AblyException
         */
        public PaginatedResult<PresenceMessage> get(Param[] params) throws AblyException {
            return getImpl(params).sync();
        }

        /**
         * Asynchronously retrieves the current members present on the channel and the metadata for each member,
         * such as their {@link io.ably.lib.types.PresenceMessage.Action} and ID. Returns a {@link PaginatedResult} object,
         * containing an array of {@link PresenceMessage} objects.
         * <p>
         * Spec: RSPa
         * @param params the request params:
         * <p>
         * limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * <p>
         * clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
         * <p>
         * connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
         * @param callback A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * <p>
         * This callback is invoked on a background thread.
         */
        public void getAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            getImpl(params).async(callback);
        }

        private BasePaginatedQuery.ResultRequest<PresenceMessage> getImpl(Param[] initialParams) {
            HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
            final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
            return (new BasePaginatedQuery<PresenceMessage>(ably.http, basePath + "/presence", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
        }

        /**
         * Retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
         * If the channel is configured to persist messages,
         * then presence messages can be retrieved from history for up to 72 hours in the past.
         * If not, presence messages can only be retrieved from history for up to two minutes in the past.
         * <p>
         * Spec: RSP4a
         * @param params the request params:
         * <p>
         * start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * direction (RSP4b2) - The order for which messages are returned in.
         *               Valid values are backwards which orders messages from most recent to oldest,
         *               or forwards which orders messages from oldest to most recent.
         *               The default is backwards.
         * limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * @return A {@link PaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * @throws AblyException
         */
        public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
            return historyImpl(params).sync();
        }

        /**
         * Asynchronously retrieves a {@link PaginatedResult} object, containing an array of historical {@link PresenceMessage} objects for the channel.
         * If the channel is configured to persist messages,
         * then presence messages can be retrieved from history for up to 72 hours in the past.
         * If not, presence messages can only be retrieved from history for up to two minutes in the past.
         * <p>
         * Spec: RSP4a
         * @param params the request params:
         * <p>
         * start (RSP4b1) - The time from which messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * end (RSP4b1) - The time until messages are retrieved, specified as milliseconds since the Unix epoch.
         * <p>
         * direction (RSP4b2) - The order for which messages are returned in.
         *               Valid values are backwards which orders messages from most recent to oldest,
         *               or forwards which orders messages from oldest to most recent.
         *               The default is backwards.
         * limit (RSP4b3) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
         * @param callback  A Callback returning {@link AsyncPaginatedResult} object containing an array of {@link PresenceMessage} objects.
         * <p>
         * This callback is invoked on a background thread.
         * @throws AblyException
         */
        public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
            historyImpl(params).async(callback);
        }

        private BasePaginatedQuery.ResultRequest<PresenceMessage> historyImpl(Param[] initialParams) {
            HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
            final Param[] params = ably.options.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
            return (new BasePaginatedQuery<PresenceMessage>(ably.http, basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
        }

    }

    /******************
     * internal
     * @throws AblyException
     ******************/

    ChannelBase(AblyBase ably, String name, ChannelOptions options) throws AblyException {
        this.ably = ably;
        this.name = name;
        this.options = options;
        this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
        this.presence = new Presence();
    }

    private final AblyBase ably;
    private final String basePath;
    ChannelOptions options;

}
