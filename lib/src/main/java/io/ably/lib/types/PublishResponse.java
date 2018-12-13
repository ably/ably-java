package io.ably.lib.types;

import com.google.gson.annotations.SerializedName;
import io.ably.lib.http.HttpCore;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/****************************************
 *            PublishResponse
 ****************************************/

public class PublishResponse {
	public ErrorInfo error;
	@SerializedName("channel")
	public String channelId;
	public String messageId;

	private static PublishResponse[] fromJSONArray(byte[] json) {
		return Serialisation.gson.fromJson(new String(json), PublishResponse[].class);
	}

	private static PublishResponse fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new PublishResponse()).readMsgpack(unpacker);
	}

	private static PublishResponse[] fromMsgpackArray(byte[] msgpack) throws IOException {
		MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(msgpack);
		return fromMsgpackArray(unpacker);
	}

	private static PublishResponse[] fromMsgpackArray(MessageUnpacker unpacker) throws IOException {
		int count = unpacker.unpackArrayHeader();
		PublishResponse[] result = new PublishResponse[count];
		for(int j = 0; j < count; j++) {
			result[j] = PublishResponse.fromMsgpack(unpacker);
		}
		return result;
	}

	private PublishResponse readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			switch(fieldName) {
				case "error":
					error = ErrorInfo.fromMsgpack(unpacker);
					break;
				case "channel":
				case "channelId":
					channelId = unpacker.unpackString();
					break;
				case "messageId":
					messageId = unpacker.unpackString();
					break;
				default:
					Log.v(TAG, "Unexpected field: " + fieldName);
					unpacker.skipValue();
			}
		}
		return this;
	}

	public static HttpCore.BodyHandler<PublishResponse> getBulkPublishResponseHandler(int statusCode) {
		return (statusCode < 300) ? bulkResponseBodyHandler : batchErrorBodyHandler;
	}

	private static class BatchErrorResponse {
		public ErrorInfo error;
		public PublishResponse[] batchResponse;

		static BatchErrorResponse readJSON(byte[] json) {
			return Serialisation.gson.fromJson(new String(json), BatchErrorResponse.class);
		}

		static BatchErrorResponse readMsgpack(byte[] msgpack) throws IOException {
			MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(msgpack);
			return (new BatchErrorResponse()).readMsgpack(unpacker);
		}

		BatchErrorResponse readMsgpack(MessageUnpacker unpacker) throws IOException {
			int fieldCount = unpacker.unpackMapHeader();
			for(int i = 0; i < fieldCount; i++) {
				String fieldName = unpacker.unpackString().intern();
				MessageFormat fieldFormat = unpacker.getNextFormat();
				if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

				switch(fieldName) {
					case "error":
						error = ErrorInfo.fromMsgpack(unpacker);
						break;
					case "batchResponse":
						batchResponse = PublishResponse.fromMsgpackArray(unpacker);
						break;
					default:
						Log.v(TAG, "Unexpected field: " + fieldName);
						unpacker.skipValue();
				}
			}
			return this;
		}
	}

	private static class BulkResponseBodyHandler implements HttpCore.BodyHandler<PublishResponse> {
		@Override
		public PublishResponse[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			try {
				if("application/json".equals(contentType)) {
					return PublishResponse.fromJSONArray(body);
				} else if("application/x-msgpack".equals(contentType)) {
					return PublishResponse.fromMsgpackArray(body);
				}
				return null;
			} catch(IOException e) {
				throw AblyException.fromThrowable(e);
			}
		}
	}

	private static class BatchErrorBodyHandler implements HttpCore.BodyHandler<PublishResponse> {
		@Override
		public PublishResponse[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			try {
				BatchErrorResponse response = null;
				if("application/json".equals(contentType)) {
					response = BatchErrorResponse.readJSON(body);
				} else if("application/x-msgpack".equals(contentType)) {
					response = BatchErrorResponse.readMsgpack(body);
				}
				if(response == null) {
					return null;
				}
				if(response.error != null && response.error.code != 40020) {
					throw AblyException.fromErrorInfo(response.error);
				}
				return response.batchResponse;
			} catch(IOException e) {
				throw AblyException.fromThrowable(e);
			}
		}
	}

	private static HttpCore.BodyHandler<PublishResponse> batchErrorBodyHandler = new BatchErrorBodyHandler();
	private static HttpCore.BodyHandler<PublishResponse> bulkResponseBodyHandler = new BulkResponseBodyHandler();

	private static final String TAG = MessageSerializer.class.getName();
}
