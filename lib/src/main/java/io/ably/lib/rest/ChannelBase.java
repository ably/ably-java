package io.ably.lib.rest;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.http.AsyncPaginatedQuery;
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
	 * @param name: the event name
	 * @param data: the message payload; see {@link io.ably.types.Data} for
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
	 * @param name: the event name
	 * @param data: the message payload; see {@link io.ably.types.Data} for
	 * @param listener
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
	 * @param messages: array of messages to publish.
	 * @throws AblyException
	 */
	public void publish(final Message[] messages) throws AblyException {
		publishImpl(messages).sync();
	}

	/**
	 * Asynchronously publish an array of messages on this channel
	 * @param messages
	 * @param listener
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
					String messageId = Crypto.getRandomMessageId();
					for (int i = 0; i < messages.length; i++) {
						messages[i].id = messageId + ':' + i;
					}
				}

				HttpCore.RequestBody requestBody = ably.options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(messages) : MessageSerializer.asJsonRequest(messages);

				http.post(basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), null, requestBody, null, true, callback);
			}
		});
	}

	/**
	 * Obtain recent history for this channel using the REST API.
	 * The history provided relqtes to all clients of this application,
	 * not just this instance.
	 * @param params: the request params. See the Ably REST API
	 * documentation for more details.
	 * @return: an array of Messages for this Channel.
	 * @throws AblyException
	 */
	public PaginatedResult<Message> history(Param[] params) throws AblyException {
		return historyImpl(params).sync();
	}

	/**
	 * Asynchronously obtain recent history for this channel using the REST API.
	 * @param params: the request params. See the Ably REST API
	 * @param callback
	 * @return
	 */
	public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
		historyImpl(params).async(callback);
	}

	private BasePaginatedQuery.ResultRequest<Message> historyImpl(Param[] params) {
		HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
		return (new BasePaginatedQuery<Message>(ably.http, basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
	}

	/**
	 * A class enabling access to Channel Presence information via the REST API.
	 * Since the library is stateless, REST clients are therefore never present
	 * themselves. This API enables the service to be queried to determine
	 * presence state for other clients on this channel.
	 */
	public class Presence {

		/**
		 * Get the presence state for this Channel.
		 * @return: the current present members.
		 * @throws AblyException 
		 */
		public PaginatedResult<PresenceMessage> get(Param[] params) throws AblyException {
			return getImpl(params).sync();
		}

		/**
		 * Asynchronously get the presence state for this Channel.
		 * @param callback: on success returns the currently present members.
		 */
		public void getAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
			getImpl(params).async(callback);
		}

		private BasePaginatedQuery.ResultRequest<PresenceMessage> getImpl(Param[] params) {
			HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
			return (new BasePaginatedQuery<PresenceMessage>(ably.http, basePath + "/presence", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler)).get();
		}

		/**
		 * Asynchronously obtain presence history for this channel using the REST API.
		 * The history provided relqtes to all clients of this application,
		 * not just this instance.
		 * @param params: the request params. See the Ably REST API
		 * documentation for more details.
		 */
		public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
			return historyImpl(params).sync();
		}

		/**
		 * Asynchronously obtain recent history for this channel using the REST API.
		 * @param params: the request params. See the Ably REST API
		 * @param callback
		 * @return
		 */
		public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<PresenceMessage>> callback) {
			historyImpl(params).async(callback);
		}

		private BasePaginatedQuery.ResultRequest<PresenceMessage> historyImpl(Param[] params) {
			HttpCore.BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
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
