package io.ably.lib.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.ably.lib.util.Log;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.JSONRequestBody;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.util.Serialisation;

/**
 * PresenceSerializer: internal
 * Utility class to convert response bodies in different formats to PresenceMessage
 * and PresenceMessage arrays.
 */
public class PresenceSerializer {

	/****************************************
	 *            Msgpack decode
	 ****************************************/
	
	static PresenceMessage[] readMsgpackArray(MessageUnpacker unpacker) throws IOException {
		int count = unpacker.unpackArrayHeader();
		PresenceMessage[] result = new PresenceMessage[count];
		for(int i = 0; i < count; i++)
			result[i] = PresenceMessage.fromMsgpack(unpacker);
		return result;
	}

	public static PresenceMessage[] readMsgpack(byte[] packed) throws AblyException {
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

	static byte[] writeMsgpackArray(PresenceMessage[] messages) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MessagePacker packer = MessagePack.newDefaultPacker(out);
			writeMsgpackArray(messages, packer);
			packer.flush();
			return out.toByteArray();
		} catch(IOException e) { return null; }
	}

	static void writeMsgpackArray(PresenceMessage[] messages, MessagePacker packer) {
		try {
			int count = messages.length;
			packer.packArrayHeader(count);
			for(PresenceMessage message : messages)
				message.writeMsgpack(packer);
		} catch(IOException e) {}
	}

	/****************************************
	 *              JSON decode
	 ****************************************/
	
	private static PresenceMessage[] readJSON(byte[] packed) throws IOException {
		return Serialisation.gson.fromJson(new String(packed), PresenceMessage[].class);
	}

	/****************************************
	 *            JSON encode
	 ****************************************/
	
	public static RequestBody asJSONRequest(PresenceMessage message) throws AblyException {
		return asJSONRequest(new PresenceMessage[] { message });
	}

	public static RequestBody asJSONRequest(PresenceMessage[] messages) {
		return new JSONRequestBody(Serialisation.gson.toJson(messages));
	}

	/****************************************
	 *              BodyHandler
	 ****************************************/
	
	public static BodyHandler<PresenceMessage> getPresenceResponseHandler(ChannelOptions opts) {
		return opts == null ? presenceResponseHandler : new PresenceBodyHandler(opts);
	}

	private static class PresenceBodyHandler implements BodyHandler<PresenceMessage> {

		public PresenceBodyHandler(ChannelOptions opts) { this.opts = opts; }

		@Override
		public PresenceMessage[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			try {
				PresenceMessage[] messages = null;
				if("application/json".equals(contentType))
					messages = readJSON(body);
				else if("application/x-msgpack".equals(contentType))
					messages = readMsgpack(body);
				if(messages != null) {
					for (PresenceMessage message : messages) {
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

	private static BodyHandler<PresenceMessage> presenceResponseHandler = new PresenceBodyHandler(null);

	private static final String TAG = PresenceSerializer.class.getName();
}
