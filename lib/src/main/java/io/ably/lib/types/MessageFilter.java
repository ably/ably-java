package io.ably.lib.types;

import java.util.Objects;

/**
 * Supplies filter options for subscriptions.
 * Spec: MFI1, MFI2
 */
public final class MessageFilter {
    /**
     * Whether the message should contain a `extras.ref` field.
     * Spec: MFI2a
     */
    public final Boolean isRef;

    /**
     * Value to check against `extras.ref.timeserial`.
     * Spec: MFI2b
     */
    public final String refTimeSerial;

    /**
     * Value to check against `extras.ref.type`.
     * Spec: MFI2c
     */
    public final String refType;

    /**
     * Value to check against the `name` of a message.
     * Spec: MFI2d
     */
    public final String name;

    /**
     * Value to check against the `cliendId` that published the message.
     * Spec: MFI2e
     */
    public final String clientId;

    public MessageFilter(Boolean isRef, String refTimeSerial, String refType, String name, String clientId) {
        this.isRef = isRef;
        this.refTimeSerial = refTimeSerial;
        this.refType = refType;
        this.name = name;
        this.clientId = clientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageFilter that = (MessageFilter) o;
        return isRef == that.isRef &&
            Objects.equals(refTimeSerial, that.refTimeSerial) &&
            Objects.equals(refType, that.refType) &&
            Objects.equals(name, that.name) &&
            Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isRef, refTimeSerial, refType, name, clientId);
    }
}
