package io.ably.lib.types;

/**
 * Supplies filter options for subscriptions.
 * Spec: MFI1, MFI2
 */
public final class MessageFilter {
    /**
     * Whether the message should contain a `extras.ref` field.
     * Spec: MFI2a
     */
    public final boolean isRef;

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

    public MessageFilter(boolean isRef, String refTimeSerial, String refType, String name, String clientId) {
        this.isRef = isRef;
        this.refTimeSerial = refTimeSerial;
        this.refType = refType;
        this.name = name;
        this.clientId = clientId;
    }
}
