package io.ably.lib.realtime;

import io.ably.lib.rest.RestAnnotations;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Annotation;
import io.ably.lib.types.AnnotationAction;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.MessageDecodeException;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;
import io.ably.lib.util.Multicaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RealtimeAnnotation provides subscription capabilities for annotations received on a channel.
 * It allows adding or removing listeners to handle annotation events and facilitates broadcasting
 * those events to the appropriate listeners.
 * <p>
 * Note: This is an experimental API. While the underlying functionality is stable,
 * the public API may change in future releases.
 */
public class RealtimeAnnotations {

    private static final String TAG = RealtimeAnnotations.class.getName();

    private final ChannelBase channel;
    private final RestAnnotations restAnnotations;
    private final AnnotationMulticaster listeners = new AnnotationMulticaster();
    private final Map<String, AnnotationMulticaster> typeListeners = new HashMap<>();

    public RealtimeAnnotations(ChannelBase channel, RestAnnotations restAnnotations) {
        this.channel = channel;
        this.restAnnotations = restAnnotations;
    }

    /**
     * Publishes an annotation to the specified channel with the given message serial.
     * Validates and encodes the annotation before sending it as a protocol message.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message to be annotated
     * @param annotation    the annotation object associated with the message
     * @param listener      the completion listener to handle success or failure during the publish process
     * @throws AblyException if an error occurs during validation, encoding, or sending the annotation
     */
    public void publish(String messageSerial, Annotation annotation, CompletionListener listener) throws AblyException {
        Log.v(TAG, String.format("publish(MsgSerial, Annotation); channel = %s", channel.name));

        // (RSAN1, RSAN1a3)
        if (annotation.type == null) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Annotation type must be specified", 400, 40000));
        }

        // (RSAN1, RSAN1c1)
        annotation.messageSerial = messageSerial;
        // (RSAN1, RSAN1c2)
        if (annotation.action == null) {
            annotation.action = AnnotationAction.ANNOTATION_CREATE;
        }

        try {
            // (RSAN1, RSAN1c3)
            annotation.encode(channel.options);
        } catch (MessageDecodeException e) {
            throw AblyException.fromThrowable(e);
        }

        Log.v(TAG, String.format("RealtimeAnnotations.publish(): channelName = %s, sending annotation with messageSerial = %s, type = %s",
            channel.name, messageSerial, annotation.type));

        ProtocolMessage protocolMessage = new ProtocolMessage();
        protocolMessage.action = ProtocolMessage.Action.annotation;
        protocolMessage.channel = channel.name;
        protocolMessage.annotations = new Annotation[]{annotation};

        channel.sendProtocolMessage(protocolMessage, listener);
    }

    /**
     * Publishes an annotation to the specified channel with the given message serial.
     * Validates and encodes the annotation before sending it as a protocol message.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message to be annotated
     * @param annotation the annotation object associated with the message
     * @throws AblyException if an error occurs during validation, encoding, or sending the annotation
     */
    public void publish(String messageSerial, Annotation annotation) throws AblyException {
        publish(messageSerial, annotation, null);
    }

    /**
     * Deletes an annotation associated with the specified message serial.
     * Sets the annotation action to `ANNOTATION_DELETE` and publishes the
     * update to the channel with the given completion listener.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated
     * @param annotation the annotation object to be deleted
     * @param listener the completion listener to handle success or failure during the deletion process
     * @throws AblyException if an error occurs during the deletion or publishing process
     */
    public void delete(String messageSerial, Annotation annotation, CompletionListener listener) throws AblyException {
        Log.v(TAG, String.format("delete(MsgSerial, Annotation); channel = %s", channel.name));
        annotation.action = AnnotationAction.ANNOTATION_DELETE;
        publish(messageSerial, annotation, listener);
    }

    public void delete(String messageSerial, Annotation annotation) throws AblyException {
        delete(messageSerial, annotation, null);
    }

    /**
     * Retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params        an array of query parameters for filtering or modifying the request.
     * @return a {@link PaginatedResult} containing the matching annotations.
     * @throws AblyException if an error occurs during the retrieval process.
     */
    public PaginatedResult<Annotation> get(String messageSerial, Param[] params) throws AblyException {
        return restAnnotations.get(messageSerial, params);
    }

    /**
     * Retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated
     * @return a PaginatedResult containing the matching annotations
     * @throws AblyException if an error occurs during the retrieval process
     */
    public PaginatedResult<Annotation> get(String messageSerial) throws AblyException {
        return restAnnotations.get(messageSerial, null);
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params        an array of query parameters for filtering or modifying the request.
     * @param callback      a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Param[] params, Callback<AsyncPaginatedResult<Annotation>> callback) {
        restAnnotations.getAsync(messageSerial, params, callback);
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param callback      a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Callback<AsyncPaginatedResult<Annotation>> callback) {
        restAnnotations.getAsync(messageSerial, null, callback);
    }

    /**
     * Subscribes the given {@link AnnotationListener} to the channel, allowing it to receive annotations.
     * If the channel's attach on subscribe option is enabled, the channel is attached automatically.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param listener the listener to be subscribed to the channel
     * @throws AblyException if an error occurs during channel attachment
     */
    public synchronized void subscribe(AnnotationListener listener) throws AblyException {
        Log.v(TAG, String.format("subscribe(); annotations in channel = %s", channel.name));
        listeners.add(listener);
        if (channel.attachOnSubscribeEnabled()) {
            channel.attach();
        }
    }

    /**
     * Unsubscribes the specified {@link AnnotationListener} from the channel, stopping it
     * from receiving further annotations. Any corresponding type-specific listeners
     * associated with the listener are also removed.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param listener the {@link AnnotationListener} to be unsubscribed
     */
    public synchronized void unsubscribe(AnnotationListener listener) {
        Log.v(TAG, String.format("unsubscribe(); annotations in channel = %s", channel.name));
        listeners.remove(listener);
        for (AnnotationMulticaster multicaster : typeListeners.values()) {
            multicaster.remove(listener);
        }
    }

    /**
     * Subscribes the given {@link AnnotationListener} to the channel for a specific annotation type,
     * allowing it to receive annotations of the specified type. If the channel's attach on subscribe
     * option is enabled, the channel is attached automatically.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param type     the specific annotation type to subscribe to; if null, subscribes to all types
     * @param listener the {@link AnnotationListener} to be subscribed
     */
    public synchronized void subscribe(String type, AnnotationListener listener) throws AblyException {
        Log.v(TAG, String.format("subscribe(); annotations in channel = %s; single type = %s", channel.name, type));
        subscribeImpl(type, listener);
        if (channel.attachOnSubscribeEnabled()) {
            channel.attach();
        }
    }

    /**
     * Unsubscribes the specified {@link AnnotationListener} from receiving annotations
     * of a particular type within the channel. If there are no remaining listeners
     * for the specified type, the type-specific listener collection is also removed.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param type     the specific annotation type to unsubscribe from; if null, unsubscribes
     *                 from all annotations associated with the listener
     * @param listener the {@link AnnotationListener} to be unsubscribed
     */
    public synchronized void unsubscribe(String type, AnnotationListener listener) {
        Log.v(TAG, String.format("unsubscribe(); annotations in channel = %s; single type = %s", channel.name, type));
        unsubscribeImpl(type, listener);
    }

    /**
     * Internal method. Handles incoming annotation messages from the protocol layer.
     *
     * @param protocolMessage the protocol message containing annotation data
     */
    public void onAnnotation(ProtocolMessage protocolMessage) {
        List<Annotation> annotations = new ArrayList<>();
        for (int i = 0; i < protocolMessage.annotations.length; i++) {
            Annotation annotation = protocolMessage.annotations[i];
            try {
                if (annotation.data != null) annotation.decode(channel.options);
            } catch (MessageDecodeException e) {
                Log.e(TAG, String.format(Locale.ROOT, "%s on channel %s", e.errorInfo.message, channel.name));
            }
            /* populate fields derived from protocol message */
            if (annotation.connectionId == null) annotation.connectionId = protocolMessage.connectionId;
            if (annotation.timestamp == 0) annotation.timestamp = protocolMessage.timestamp;
            if (annotation.id == null) annotation.id = protocolMessage.id + ':' + i;
            annotations.add(annotation);
        }
        broadcastAnnotation(annotations);
    }

    private void broadcastAnnotation(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            listeners.onAnnotation(annotation);

            String type = annotation.type != null ? annotation.type : "";
            AnnotationMulticaster eventListener = typeListeners.get(type);
            if (eventListener != null) eventListener.onAnnotation(annotation);
        }
    }

    private void subscribeImpl(String type, AnnotationListener listener) {
        String annotationType = type != null ? type : "";
        AnnotationMulticaster typeSpecificListeners = typeListeners.get(annotationType);
        if (typeSpecificListeners == null) {
            typeSpecificListeners = new AnnotationMulticaster();
            typeListeners.put(annotationType, typeSpecificListeners);
        }
        typeSpecificListeners.add(listener);
    }

    private void unsubscribeImpl(String type, AnnotationListener listener) {
        AnnotationMulticaster listeners = typeListeners.get(type);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                typeListeners.remove(type);
            }
        }
    }

    public interface AnnotationListener {
        void onAnnotation(Annotation annotation);
    }

    private static class AnnotationMulticaster extends Multicaster<AnnotationListener> implements AnnotationListener {
        @Override
        public void onAnnotation(Annotation annotation) {
            for (final AnnotationListener member : getMembers()) {
                try {
                    member.onAnnotation(annotation);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
