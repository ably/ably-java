package io.ably.lib.realtime;

import com.google.gson.JsonObject;
import io.ably.lib.types.Message;

/**
 * A filter for inspecting the extras of a message to check if they match.
 * Spec: RTL22c
 */
final class MessageExtrasFilter implements FilteredListeners.IMessageFilter {
    public final io.ably.lib.types.MessageFilter filter;

    public MessageExtrasFilter(io.ably.lib.types.MessageFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean onMessage(Message message) {
        return clientIdMatchesFilter(message) &&
            nameMatchesFilter(message) &&
            referenceMatchesFilter(message);
    }

    private boolean nameMatchesFilter(Message message) {
        return filter.name == null || message.name.equals(filter.name);
    }

    private boolean clientIdMatchesFilter(Message message) {
        return filter.clientId == null || message.clientId.equals(filter.clientId);
    }

    private boolean referenceMatchesFilter(Message message) {
        // No reference-based filters, so we can skip.
        if (filter.isRef == null && filter.refType == null && filter.refTimeSerial == null) {
            return true;
        }

        MessageRef messageRef = getMessageRef(message);

        return isRefMatchesFilter(messageRef) &&
            refTypeMatchesFilter(messageRef) &&
            refTimeserialMatchesFilter(messageRef);
    }

    private MessageRef getMessageRef(Message message) {

        // No extras
        if (message.extras == null) {
            return null;
        }

        JsonObject messageExtras = message.extras.asJsonObject();
        if (!messageExtras.has("ref")) {
            return null;
        }

        try {
            JsonObject messageRef = message.extras.asJsonObject().get("ref").getAsJsonObject();
            return new MessageRef(
                messageRef.get("type").getAsJsonPrimitive().getAsString(),
                messageRef.get("timeserial").getAsJsonPrimitive().getAsString()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRefMatchesFilter(MessageRef messageRef) {
        return filter.isRef == null ||
            (Boolean.TRUE.equals(filter.isRef) && messageRef != null) ||
            (Boolean.FALSE.equals(filter.isRef) && messageRef == null);
    }

    private boolean refTypeMatchesFilter(MessageRef messageRef) {
        return filter.refType == null || (messageRef != null && filter.refType.equals(messageRef.type));
    }

    private boolean refTimeserialMatchesFilter(MessageRef messageRef) {
        return filter.refTimeSerial == null || (messageRef != null && filter.refTimeSerial.equals(messageRef.timeserial));
    }

    private static final class MessageRef {
        public final String type;

        public final String timeserial;


        private MessageRef(String type, String timeserial) {
            this.type = type;
            this.timeserial = timeserial;
        }
    }
}
