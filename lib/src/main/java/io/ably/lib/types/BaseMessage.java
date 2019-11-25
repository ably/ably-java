package io.ably.lib.types;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto.ChannelCipher;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

public class BaseMessage implements Cloneable {
	/**
	 * A unique id for this message
	 */
	public String id;

	/**
	 * The timestamp for this message
	 */
	public long timestamp;

	/**
	 * The id of the publisher of this message
	 */
	public String clientId;

	/**
	 * The connection id of the publisher of this message
	 */
	public String connectionId;

	/**
	 * Any transformation applied to the data for this message
	 */
	public String encoding;

	/**
	 * The message payload.
	 */
	public Object data;

	/**
	 * Generate a String summary of this BaseMessage
	 * @return string
	 */
	public void getDetails(StringBuilder builder) {
		if(clientId != null)
			builder.append(" clientId=").append(clientId);
		if(connectionId != null)
			builder.append(" connectionId=").append(connectionId);
		if(data != null)
			builder.append(" data=").append(data);
		if(encoding != null)
			builder.append(" encoding=").append(encoding);
		if(id != null)
			builder.append(" id=").append(id);
	}

	public void decode(ChannelOptions opts) throws MessageDecodeException {

		this.decode(opts, new DecodingContext(new HashMap<String, PluggableCodec>()));
	}

	public void decode(ChannelOptions opts,  DecodingContext context) throws MessageDecodeException {

		Object lastPayload = data;

		if(encoding != null) {
			String[] xforms = encoding.split("\\/");
			int lastProcessedEncodingIndex = 0, encodingsToProcess  = xforms.length;
			try {
				while((lastProcessedEncodingIndex  = encodingsToProcess ) > 0) {
					Matcher match = xformPattern.matcher(xforms[--encodingsToProcess ]);
					if(!match.matches()) break;
					switch(match.group(1)) {
						case "base64":
							try {
								data = Base64Coder.decode((String) data);
							} catch (IllegalArgumentException e) {
								throw MessageDecodeException.fromDescription("Invalid base64 data received");
							}
							if(lastProcessedEncodingIndex == xforms.length) {
								lastPayload = data;
							}
							continue;

						case "utf-8":
							try { data = new String((byte[])data, "UTF-8"); } catch(UnsupportedEncodingException|ClassCastException e) {}
							continue;

						case "json":
							try {
								String jsonText = ((String)data).trim();
								data = Serialisation.gsonParser.parse(jsonText);
							} catch(JsonParseException e) {
								throw MessageDecodeException.fromDescription("Invalid JSON data received");
							}
							continue;

						case "cipher":
							if(opts != null && opts.encrypted) {
								try {
									data = opts.getCipher().decrypt((byte[]) data);
								} catch(AblyException e) {
									throw MessageDecodeException.fromDescription(e.errorInfo.message);
								}
								continue;
							}
							else {
								throw MessageDecodeException.fromDescription("Encrypted message received but encryption is not set up");
							}
						case "vcdiff":
							if(context.codecs.containsKey("vcdiff"))
							{
								VCDiffPluggableCodec vcdiffCodec = (VCDiffPluggableCodec) context.codecs.get("vcdiff");
								if(vcdiffCodec == null)
									throw MessageDecodeException.fromDescription("vcdiff codec is not of type VCDiffPluggableCodec");

								try {
									data = vcdiffCodec.decode((byte[]) data, context.get_lastMessage());
									lastPayload = data;
								}
								catch (AblyException ex) {
									throw MessageDecodeException.fromThrowableAndErrorInfo(ex, new ErrorInfo("Decoding failed for user provided codec " + match.group(1), ex.errorInfo.statusCode, ex.errorInfo.code));
								}

								continue;
							}
					}
					break;
				}
			} finally {
				encoding = (lastProcessedEncodingIndex  <= 0) ? null : join(xforms, '/', 0, lastProcessedEncodingIndex );
			}
		}

		if(lastPayload instanceof String)
			context.set_lastMessage((String)lastPayload);
		else if (lastPayload instanceof byte[])
			context.set_lastMessage((byte[])lastPayload);
		else
			throw MessageDecodeException.fromDescription("Message data neither String nor byte[]. Unsupported message data type.");
	}

	public void encode(ChannelOptions opts) throws AblyException {
		if(data != null) {
			if(data instanceof JsonElement) {
				data = Serialisation.gson.toJson((JsonElement)data);
				encoding = ((encoding == null) ? "" : encoding + "/") + "json";
			}
			if(data instanceof String) {
				if (opts != null && opts.encrypted) {
					try { data = ((String)data).getBytes("UTF-8"); } catch(UnsupportedEncodingException e) {}
					encoding = ((encoding == null) ? "" : encoding + "/") + "utf-8";
				}
			} else if(!(data instanceof byte[])) {
				Log.d(TAG, "Message data must be either `byte[]`, `String` or `JSONElement`; implicit coercion of other types to String is deprecated");
				throw AblyException.fromErrorInfo(new ErrorInfo("Invalid message data or encoding", 400, 40013));
			}
		}
		if (opts != null && opts.encrypted) {
			ChannelCipher cipher = opts.getCipher();
			data = cipher.encrypt((byte[]) data);
			encoding = ((encoding == null) ? "" : encoding + "/") + "cipher+" + cipher.getAlgorithm();
		}
	}

	/* trivial utilities for processing encoding string */
	private static Pattern xformPattern = Pattern.compile("([\\-\\w]+)(\\+([\\-\\w]+))?");
	private String join(String[] elements, char separator, int start, int end) {
		StringBuilder result = new StringBuilder(elements[start++]);
		for(int i = start; i < end; i++)
			result.append(separator).append(elements[i]);
		return result.toString();
	}

	/* Gson Serializer */
	public static class Serializer {
		public JsonElement serialize(BaseMessage message, Type typeOfMessage, JsonSerializationContext ctx) {
			JsonObject json = new JsonObject();
			Object data = message.data;
			String encoding = message.encoding;
			if(data != null) {
				if(data instanceof byte[]) {
					byte[] dataBytes = (byte[])data;
					json.addProperty("data", new String(Base64Coder.encode(dataBytes)));
					encoding = (encoding == null) ? "base64" : encoding + "/base64";
				} else {
					json.addProperty("data", data.toString());
				}
				if(encoding != null) json.addProperty("encoding", encoding);
			}
			if(message.id != null) json.addProperty("id", message.id);
			if(message.clientId != null) json.addProperty("clientId", message.clientId);
			if(message.connectionId != null) json.addProperty("connectionId", message.connectionId);
			return json;
		}
	}

	/* Msgpack processing */
	boolean readField(MessageUnpacker unpacker, String fieldName, MessageFormat fieldType) throws IOException {
		boolean result = true;
		switch (fieldName) {
			case "timestamp":
				timestamp = unpacker.unpackLong(); break;
			case "id":
				id = unpacker.unpackString(); break;
			case "clientId":
				clientId = unpacker.unpackString(); break;
			case "connectionId":
				connectionId = unpacker.unpackString(); break;
			case "encoding":
				encoding = unpacker.unpackString(); break;
			case "data":
				if(fieldType.getValueType().isBinaryType()) {
					byte[] byteData = new byte[unpacker.unpackBinaryHeader()];
					unpacker.readPayload(byteData);
					data = byteData;
				} else {
					data = unpacker.unpackString();
				}
				break;
			default:
				result = false;
				break;
		}
		return result;
	}

	protected int countFields() {
		int fieldCount = 0;
		if(timestamp > 0) ++fieldCount;
		if(id != null) ++fieldCount;
		if(clientId != null) ++fieldCount;
		if(connectionId != null) ++fieldCount;
		if(encoding != null) ++fieldCount;
		if(data != null) ++fieldCount;
		return fieldCount;
	}

	void writeFields(MessagePacker packer) throws IOException {
		if(timestamp > 0) {
			packer.packString("timestamp");
			packer.packLong(timestamp);
		}
		if(id != null) {
			packer.packString("id");
			packer.packString(id);
		}
		if(clientId != null) {
			packer.packString("clientId");
			packer.packString(clientId);
		}
		if(connectionId != null) {
			packer.packString("connectionId");
			packer.packString(connectionId);
		}
		if(encoding != null) {
			packer.packString("encoding");
			packer.packString(encoding);
		}
		if(data != null) {
			packer.packString("data");
			if(data instanceof byte[]) {
				byte[] byteData = (byte[])data;
				packer.packBinaryHeader(byteData.length);
				packer.writePayload(byteData);
			} else {
				packer.packString(data.toString());
			}
		}
	}

	private static final String TAG = BaseMessage.class.getName();
}
