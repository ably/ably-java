package io.ably.types;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import com.fasterxml.jackson.core.type.TypeReference;

import io.ably.http.Http;
import io.ably.http.Http.BodyHandler;
import io.ably.http.Http.RequestBody;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer {

	public static Message[] readMsgpack(byte[] packed) throws AblyException {
		try {
			List<Message> messages = BaseMessage.objectMapper.readValue(packed, typeReference);
			return messages.toArray(new Message[messages.size()]);
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
			return BaseMessage.objectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static RequestBody asMsgpackRequest(Message message) throws AblyException {
		return asMsgpackRequest(new Message[] { message });
	}

	public static RequestBody asMsgpackRequest(Message[] messages) throws AblyException {
		try {
			return new Http.ByteArrayRequestBody(BaseMessage.objectMapper.writeValueAsBytes(messages));
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
	private static final TypeReference<List<Message>> typeReference = new TypeReference<List<Message>>(){};

}
