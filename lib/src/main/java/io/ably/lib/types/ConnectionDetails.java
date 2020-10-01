package io.ably.lib.types;

import java.io.IOException;

import io.ably.lib.transport.Defaults;
import io.ably.lib.util.Log;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

public class ConnectionDetails {
    public String clientId;
    public String connectionKey;
    public String serverId;
    public Long maxMessageSize;
    public Long maxInboundRate;
    public Long maxOutboundRate;
    public Long maxFrameSize;
    public Long maxIdleInterval;
    public Long connectionStateTtl;

    ConnectionDetails() {
        maxIdleInterval = Defaults.maxIdleInterval;
        connectionStateTtl = Defaults.connectionStateTtl;
    }

    ConnectionDetails readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "clientId":
                    clientId = unpacker.unpackString();
                    break;
                case "connectionKey":
                    connectionKey = unpacker.unpackString();
                    break;
                case "serverId":
                    serverId = unpacker.unpackString();
                    break;
                case "maxMessageSize":
                    maxMessageSize = unpacker.unpackLong();
                    break;
                case "maxInboundRate":
                    maxInboundRate = unpacker.unpackLong();
                    break;
                case "maxOutboundRate":
                    maxOutboundRate = unpacker.unpackLong();
                    break;
                case "maxFrameSize":
                    maxFrameSize = unpacker.unpackLong();
                    break;
                case "maxIdleInterval":
                    maxIdleInterval = unpacker.unpackLong();
                    break;
                case "connectionStateTtl":
                    connectionStateTtl = unpacker.unpackLong();
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return this;
    }

    static ConnectionDetails fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new ConnectionDetails()).readMsgpack(unpacker);
    }

    private static final String TAG = ConnectionDetails.class.getName();
}
