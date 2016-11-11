package io.ably.lib.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.ably.lib.util.Log;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.http.Http;
import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.JSONRequestBody;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.util.Serialisation;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer {

	/****************************************
	 *            Msgpack decode
	 ****************************************/
	
	static Message[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
		int count = unpacker.unpackArrayHeader();
		Message[] result = new Message[count];
		for(int i = 0; i < count; i++)
			result[i] = Message.fromMsgpack(unpacker);
		return result;
	}

	public static Message[] readMsgpack(byte[] packed) throws AblyException {
		try {
			MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(packed);
			return readMsgpackArray(unpacker);
		} catch(IOException ioe) {
			throw AblyException.fromThrowable(ioe);
		}
	}

	/****************************************
	 *            Msgpack encode
	 ****************************************/

	public static RequestBody asMsgpackRequest(Message message) throws AblyException {
		return asMsgpackRequest(new Message[] { message });
	}

	public static RequestBody asMsgpackRequest(Message[] messages) {
		return new Http.ByteArrayRequestBody(writeMsgpackArray(messages), "application/x-msgpack");
	}

	static byte[] writeMsgpackArray(Message[] messages) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MessagePacker packer = MessagePack.newDefaultPacker(out);
			writeMsgpackArray(messages, packer);
			packer.flush();
			return out.toByteArray();
		} catch(IOException e) { return null; }
	}

	static void writeMsgpackArray(Message[] messages, MessagePacker packer) {
		try {
			int count = messages.length;
			packer.packArrayHeader(count);
			for(Message message : messages)
				message.writeMsgpack(packer);
		} catch(IOException e) {}
	}

	/****************************************
	 *              JSON decode
	 ****************************************/
	
	private static Message[] readJSON(byte[] packed) throws IOException {
		return Serialisation.gson.fromJson(new String(packed), Message[].class);
	}

	/****************************************
	 *            JSON encode
	 ****************************************/
	
	public static RequestBody asJSONRequest(Message message) throws AblyException {
		return asJSONRequest(new Message[] { message });
	}

	public static RequestBody asJSONRequest(Message[] messages) {
		return new JSONRequestBody(Serialisation.gson.toJson(messages));
	}

	/****************************************
	 *              BodyHandler
	 ****************************************/
	
	public static BodyHandler<Message> getMessageResponseHandler(ChannelOptions opts) {
		return opts == null ? messageResponseHandler : new MessageBodyHandler(opts);
	}

	private static class MessageBodyHandler implements BodyHandler<Message> {

		public MessageBodyHandler(ChannelOptions opts) { this.opts = opts; }

		@Override
		public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			try {
				Message[] messages = null;
				if("application/json".equals(contentType))
					messages = readJSON(body);
				else if("application/x-msgpack".equals(contentType))
					messages = readMsgpack(body);
				if(messages != null) {
					for (Message message : messages) {
						try {
							message.decode(opts);
						} catch (MessageDecodeException e) {
							Log.e(TAG, e.errorInfo.message);
						}
					}
				}
				return messages;
			} catch(IOException e) {
				throw AblyException.fromThrowable(e);
			}
		}

		private ChannelOptions opts;
	}

	private static BodyHandler<Message> messageResponseHandler = new MessageBodyHandler(null);

	private static final String TAG = MessageSerializer.class.getName();
}
