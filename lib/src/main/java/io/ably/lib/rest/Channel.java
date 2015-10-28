package io.ably.lib.rest;

import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageSerializer;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.PresenceSerializer;

/**
 * A class representing a Channel in the Ably REST API.
 * In the REST API, the library is essentially stateless;
 * a Channel object simply represents a channel for making
 * REST requests, and existence of a channel does not
 * signify that there is a realtime connection or attachment
 * to that channel.
 */
public class Channel {

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
		Message message = new Message(name, data);
		message.encode(options);
		RequestBody requestBody = ably.options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(message) : MessageSerializer.asJSONRequest(message);
		ably.http.post(basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), null, requestBody, null);
	}

	/**
	 * Publish an array of messages on this channel. When there are
	 * multiple messages to be sent, it is more efficient to use this
	 * method to publish them in a single request, as compared with
	 * publishing via multiple independent requests.
	 * @param messages: array of messages to publish.
	 * @throws AblyException
	 */
	public void publish(Message[] messages) throws AblyException {
		for(Message message : messages)
			message.encode(options);
		RequestBody requestBody = ably.options.useBinaryProtocol ? MessageSerializer.asMsgpackRequest(messages) : MessageSerializer.asJSONRequest(messages);
		ably.http.post(basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), null, requestBody, null);
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
		BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
		return new PaginatedQuery<Message>(ably.http, basePath + "/messages", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
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
			BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
			return new PaginatedQuery<PresenceMessage>(ably.http, basePath + "/presence", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
		}

		/**
		 * Obtain presence history for this channel using the REST API.
		 * The history provided relqtes to all clients of this application,
		 * not just this instance.
		 * @param params: the request params. See the Ably REST API
		 * documentation for more details.
		 * @return: an array of PresenceMessages for this Channel.
		 * @throws AblyException
		 */
		public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
			BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(options);
			return new PaginatedQuery<PresenceMessage>(ably.http, basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
		}
	}

	/******************
	 * internal
	 * @throws AblyException 
	 ******************/
	
	Channel(AblyRest ably, String name, ChannelOptions options) throws AblyException {
		this.ably = ably;
		this.name = name;
		this.options = options;
		this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
		this.presence = new Presence();
	}

	private final AblyRest ably;
	private final String basePath;
	private ChannelOptions options;

}
