package io.ably.lib.rest;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Annotation;
import io.ably.lib.types.AnnotationAction;
import io.ably.lib.types.AnnotationSerializer;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Log;

import java.util.Arrays;

/**
 * The RestAnnotation class provides methods to manage and interact with annotations
 * associated with messages in a specific channel.
 * <p>
 * Annotations can be retrieved, published, or deleted both synchronously and asynchronously.
 * This class is intended as part of a client library for managing annotations via REST architecture.
 * <p>
 * Note: This is an experimental API. While the underlying functionality is stable,
 * the public API may change in future releases.
 */
public class RestAnnotation {

    private static final String TAG = RestAnnotation.class.getName();

    private final String channelName;
    private final Http http;
    private final ClientOptions clientOptions;
    private final ChannelOptions channelOptions;

    public RestAnnotation(String channelName, Http http, ClientOptions clientOptions, ChannelOptions channelOptions) {
        this.channelName = channelName;
        this.http = http;
        this.clientOptions = clientOptions;
        this.channelOptions = channelOptions;
    }

    /**
     * Retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params an array of query parameters for filtering or modifying the request.
     * @return a {@link PaginatedResult} containing the matching annotations.
     * @throws AblyException if an error occurs during the retrieval process.
     */
    public PaginatedResult<Annotation> get(String messageSerial, Param[] params) throws AblyException {
        return getImpl(messageSerial, params).sync();
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param params an array of query parameters for filtering or modifying the request.
     * @param callback a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Param[] params, Callback<AsyncPaginatedResult<Annotation>> callback) {
        getImpl(messageSerial, params).async(callback);
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
        return get(messageSerial, null);
    }

    /**
     * Asynchronously retrieves a paginated list of annotations associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param callback a callback to handle the result asynchronously, providing an {@link AsyncPaginatedResult} containing the matching annotations.
     */
    public void getAsync(String messageSerial, Callback<AsyncPaginatedResult<Annotation>> callback) {
        getImpl(messageSerial, null).async(callback);
    }

    /**
     * Publishes an annotation associated with the specified message serial
     * to the REST channel.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be published.
     * @throws AblyException if an error occurs during the publishing process.
     */
    public void publish(String messageSerial, Annotation annotation) throws AblyException {
        publishImpl(messageSerial, annotation).sync();
    }

    /**
     * Asynchronously publishes an annotation associated with the specified message serial
     * to the REST channel.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be published.
     * @param callback a callback to handle the result asynchronously, providing a
     *                 completion indication or error information.
     */
    public void publishAsync(String messageSerial, Annotation annotation, Callback<Void> callback) {
        publishImpl(messageSerial, annotation).async(callback);
    }

    /**
     * Deletes an annotation associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be deleted.
     * @throws AblyException if an error occurs during the deletion process.
     */
    public void delete(String messageSerial, Annotation annotation) throws AblyException {
        annotation.action = AnnotationAction.ANNOTATION_DELETE;
        publish(messageSerial, annotation);
    }

    /**
     * Asynchronously deletes an annotation associated with the specified message serial.
     * <p>
     * Note: This is an experimental API. While the underlying functionality is stable,
     * the public API may change in future releases.
     *
     * @param messageSerial the unique serial identifier for the message being annotated.
     * @param annotation the annotation to be deleted.
     * @param callback a callback to handle the result asynchronously, providing a completion
     *                 indication or error information.
     */
    public void deleteAsync(String messageSerial, Annotation annotation, Callback<Void> callback) {
        annotation.action = AnnotationAction.ANNOTATION_DELETE;
        publishAsync(messageSerial, annotation, callback);
    }

    private String getBasePath(String messageSerial) {
        return "/channels/" + HttpUtils.encodeURIComponent(channelName) + "/messages/" + HttpUtils.encodeURIComponent(messageSerial) + "/annotations";
    }

    private Http.Request<Void> publishImpl(String messageSerial, Annotation annotation) {
        Log.v(TAG, "publishImpl(): annotation=" + annotation);
        return http.request((http, callback) -> {
            HttpCore.RequestBody requestBody = clientOptions.useBinaryProtocol ? AnnotationSerializer.asMsgpackRequest(annotation) : AnnotationSerializer.asJsonRequest(annotation);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null; // RSC7c
            http.post(getBasePath(messageSerial), HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, requestBody, null, true, callback);
        });
    }

    private BasePaginatedQuery.ResultRequest<Annotation> getImpl(String messageSerial, Param[] initialParams) {
        Log.v(TAG, "getImpl(): params=" + Arrays.toString(initialParams));
        HttpCore.BodyHandler<Annotation> bodyHandler = AnnotationSerializer.getAnnotationResponseHandler(channelOptions);
        final Param[] params = clientOptions.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams; // RSC7c
        return (new BasePaginatedQuery<>(http, getBasePath(messageSerial), HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, bodyHandler)).get();
    }
}
