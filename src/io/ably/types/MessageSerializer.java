package io.ably.types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.ably.http.Http;
import io.ably.http.Http.BodyHandler;
import io.ably.http.Http.RequestBody;

import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;
import org.msgpack.template.ListTemplate;
import org.msgpack.template.Template;
import org.msgpack.unpacker.Unpacker;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer {

	static Message[] readMsgpack(Unpacker unpacker) throws IOException {
		List<Message> msgList = unpacker.read(listTmpl);
		return msgList.toArray(new Message[msgList.size()]);
	}

	public static Message[] readMsgpack(byte[] packed) throws AblyException {
		try {
			Unpacker unpacker = msgpack.createUnpacker(new ByteArrayInputStream(packed));
			return readMsgpack(unpacker);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static Message[] readJSON(JSONArray json) {
		int count = json.length();
		Message[] result = new Message[count];
			for(int i = 0; i < count; i++)
				result[i] = Message.fromJSON(json.optJSONObject(i));
			return result;
	}

	public static Message[] readJSON(String jsonText) throws AblyException {
		try {
			return readJSON(new JSONArray(jsonText));
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static Message[] readJSON(byte[] jsonBytes) throws AblyException {
		return readJSON(new String(jsonBytes));
	}

	public static JSONArray writeJSON(Message[] messages) throws AblyException {
		JSONArray json;
		try {
			json = new JSONArray();
			for(int i = 0; i < messages.length; i++)
				json.put(i, messages[i].toJSON());

			return json;
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static RequestBody asJSONRequest(Message message) throws AblyException {
		return new Http.JSONRequestBody(message.toJSON().toString());
	}

	public static RequestBody asJSONRequest(Message[] messages) throws AblyException {
		return new Http.JSONRequestBody(writeJSON(messages).toString());
	}

	public static byte[] asMsgpack(Message message) throws AblyException {
		try {
			return msgpack.write(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static RequestBody asMsgpackRequest(Message message) throws AblyException {
		return asMsgpackRequest(new Message[] { message });
	}

	public static RequestBody asMsgpackRequest(Message[] messages) throws AblyException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Packer packer = msgpack.createPacker(out);
			listTmpl.write(packer, Arrays.asList(messages));
			return new Http.ByteArrayRequestBody(out.toByteArray());
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static BodyHandler<Message> getMessageResponseHandler(ChannelOptions opts) {
		return opts == null ? messageResponseHandler : new MessageBodyHandler(opts);
	}

	private static class MessageBodyHandler implements BodyHandler<Message> {

		public MessageBodyHandler(ChannelOptions opts) { this.opts = opts; }

		@Override
		public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			Message[] messages = null;
			if("application/json".equals(contentType))
				messages = readJSON(body);
			else if("application/x-msgpack".equals(contentType))
				messages = readMsgpack(body);
			if(messages != null)
				for(Message message : messages)
					message.decode(opts);
			return messages;
		}

		private ChannelOptions opts;
	}

	private static BodyHandler<Message> messageResponseHandler = new MessageBodyHandler(null);
	private static final MessagePack msgpack = new MessagePack();
	private static final Template<List<Message>> listTmpl = new ListTemplate<Message>(msgpack.lookup(Message.class));

}
