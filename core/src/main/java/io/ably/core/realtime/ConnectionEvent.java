package io.ably.core.realtime;

/**
 * Connection event
 */
public enum ConnectionEvent {
    initialized,
    connecting,
    connected,
    disconnected,
    suspended,
    closing,
    closed,
    failed,
    update
}
