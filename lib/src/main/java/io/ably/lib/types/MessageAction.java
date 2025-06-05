package io.ably.lib.types;

public enum MessageAction {
    MESSAGE_CREATE, // 0
    MESSAGE_UPDATE, // 1
    MESSAGE_DELETE, // 2
    META, // 3
    MESSAGE_SUMMARY; // 4

    static MessageAction tryFindByOrdinal(int ordinal) {
        return values().length <= ordinal ? null: values()[ordinal];
    }
}
