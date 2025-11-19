package io.ably.lib.types;

import com.google.gson.JsonObject;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * MessageOperationSerializer: internal
 * Utility class to serialize message update/delete requests in different formats.
 */
public class MessageOperationSerializer {

    /**
     * Creates a JSON request body for a message update/delete operation.
     *
     * @param message The message containing the update/delete data
     * @param operation The MessageOperation metadata
     * @param channelOptions Channel options for encoding
     * @return HttpCore.RequestBody for the request
     * @throws AblyException If encoding fails
     */
    public static HttpCore.RequestBody asJsonRequest(Message message, MessageOperation operation, ChannelOptions channelOptions) throws AblyException {
        UpdateDeleteRequest request = new UpdateDeleteRequest(message, operation, channelOptions);
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(request.asJsonObject()));
    }

    /**
     * Creates a MessagePack request body for a message update/delete operation.
     *
     * @param message The message containing the update/delete data
     * @param operation The MessageOperation metadata
     * @param channelOptions Channel options for encoding
     * @return HttpCore.RequestBody for the request
     * @throws AblyException If encoding fails
     */
    public static HttpCore.RequestBody asMsgPackRequest(Message message, MessageOperation operation, ChannelOptions channelOptions) throws AblyException {
        UpdateDeleteRequest request = new UpdateDeleteRequest(message, operation, channelOptions);
        byte[] packed = writeMsgpack(request);
        return new HttpUtils.ByteArrayRequestBody(packed, "application/x-msgpack");
    }

    /**
     * Serializes an UpdateDeleteRequest to MessagePack format.
     *
     * @param request The request to serialize
     * @return byte array containing the MessagePack data
     */
    private static byte[] writeMsgpack(UpdateDeleteRequest request) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
            request.writeMsgpack(packer);
            packer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write msgpack", e);
            return null;
        }
    }

    /**
     * Represents a request to update or delete a message.
     * Contains the message data and operation metadata.
     */
    static class UpdateDeleteRequest {
        private static final String NAME = "name";
        private static final String DATA = "data";
        private static final String ENCODING = "encoding";
        private static final String EXTRAS = "extras";
        private static final String OPERATION = "operation";

        public final String name;
        public final Object data;
        public final String encoding;
        public final MessageExtras extras;
        public final MessageOperation operation;

        /**
         * Constructs an UpdateDeleteRequest from a Message and operation metadata.
         *
         * @param message The message containing the update/delete data
         * @param operation The MessageOperation metadata
         * @param channelOptions Channel options for encoding the message data
         * @throws AblyException If encoding fails
         */
        UpdateDeleteRequest(Message message, MessageOperation operation, ChannelOptions channelOptions) throws AblyException {
            this.operation = operation;
            this.name = message.name;
            this.extras = message.extras;

            BaseMessage.EncodedMessageData encodedMessageData = message.encodeData(channelOptions);
            this.data = encodedMessageData.data;
            this.encoding = encodedMessageData.encoding;
        }

        /**
         * Writes this UpdateDeleteRequest to MessagePack format.
         *
         * @param packer The MessagePacker to write to
         * @throws IOException If writing fails
         */
        void writeMsgpack(MessagePacker packer) throws IOException {
            int fieldCount = 0;
            if (name != null) ++fieldCount;
            if (data != null) ++fieldCount;
            if (encoding != null) ++fieldCount;
            if (extras != null) ++fieldCount;
            if (operation != null) ++fieldCount;

            packer.packMapHeader(fieldCount);

            if (name != null) {
                packer.packString(NAME);
                packer.packString(name);
            }
            if (data != null) {
                packer.packString(DATA);
                if (data instanceof byte[]) {
                    byte[] byteData = (byte[])data;
                    packer.packBinaryHeader(byteData.length);
                    packer.writePayload(byteData);
                } else {
                    packer.packString(data.toString());
                }
            }
            if (encoding != null) {
                packer.packString(ENCODING);
                packer.packString(encoding);
            }
            if (extras != null) {
                packer.packString(EXTRAS);
                extras.write(packer);
            }
            if (operation != null) {
                packer.packString(OPERATION);
                operation.writeMsgpack(packer);
            }
        }

        /**
         * Base for gson serialisers.
         */
        JsonObject asJsonObject() {
            JsonObject json = new JsonObject();
            Object data = this.data;
            String encoding = this.encoding;
            if (data != null) {
                if (data instanceof byte[]) {
                    byte[] dataBytes = (byte[])data;
                    json.addProperty(DATA, new String(Base64Coder.encode(dataBytes)));
                    encoding = (encoding == null) ? "base64" : encoding + "/base64";
                } else {
                    json.addProperty(DATA, data.toString());
                }
                if (encoding != null) json.addProperty(ENCODING, encoding);
            }
            if (this.name != null) json.addProperty(NAME, this.name);
            if (this.extras != null) json.add(EXTRAS, this.extras.asJsonObject());
            if (this.operation != null) json.add(OPERATION, this.operation.asJsonObject());
            return json;
        }
    }

    private static final String TAG = MessageOperationSerializer.class.getName();
}
