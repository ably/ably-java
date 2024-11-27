package io.ably.lib.types;

public enum MessageAction {
    MESSAGE_UNSET, // 0
    MESSAGE_CREATE, // 1
    MESSAGE_UPDATE, // 2
    MESSAGE_DELETE, // 3
    ANNOTATION_CREATE, // 4
    ANNOTATION_DELETE, // 5
    META_OCCUPANCY; // 6

    static MessageAction tryFindByOrdinal(int ordinal) {
        return values().length <= ordinal ? null: values()[ordinal];
    }
}
