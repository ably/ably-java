package io.ably.lib.rest;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageAction;
import io.ably.lib.types.MessageDecodeException;
import io.ably.lib.types.MessageSerializer;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Serialisation;

public class MessageEditsMixin {

    private final String basePath;

    private final ClientOptions clientOptions;

    private final ChannelOptions channelOptions;

    private final Auth auth;

    public MessageEditsMixin(String basePath, ClientOptions clientOptions, ChannelOptions channelOptions, Auth auth) {
        this.basePath = basePath;
        this.clientOptions = clientOptions;
        this.channelOptions = channelOptions;
        this.auth = auth;
    }

    /**
     * Retrieves the latest version of a specific message by its serial identifier.
     * <p>
     * This method allows you to fetch the current state of a message, including any updates
     * or deletions that have been applied since its creation.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @return A {@link Message} object representing the latest version of the message.
     * @throws AblyException If the message cannot be retrieved or does not exist.
     */
    public Message getMessage(Http http, String serial) throws AblyException {
        return getMessageImpl(http, serial).sync();
    }

    /**
     * Asynchronously retrieves the latest version of a specific message by its serial identifier.
     *
     * @param serial The unique serial identifier of the message to retrieve.
     * @param callback A callback to handle the result asynchronously.
     * <p>
     * This callback is invoked on a background thread.
     */
    public void getMessageAsync(Http http, String serial, Callback<Message> callback) {
        getMessageImpl(http, serial).async(callback);
    }

    private Http.Request<Message> getMessageImpl(Http http, String serial) {
        return http.request(new Http.Execute<Message>() {
            @Override
            public void execute(HttpScheduler http, final Callback<Message> callback) throws AblyException {
                if (serial == null || serial.isEmpty()) {
                    throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
                }
                HttpCore.BodyHandler<Message> bodyHandler = new HttpCore.BodyHandler<Message>() {
                    @Override
                    public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
                        try {
                            Message message = null;
                            if ("application/json".equals(contentType)) {
                                message = Serialisation.gson.fromJson(new String(body), Message.class);
                            } else if ("application/x-msgpack".equals(contentType)) {
                                Message[] messages = MessageSerializer.readMsgpack(body);
                                if (messages.length > 0) {
                                    message = messages[0];
                                }
                            }
                            if (message != null) {
                                try {
                                    message.decode(channelOptions);
                                } catch (MessageDecodeException e) {
                                    throw AblyException.fromThrowable(e);
                                }
                                return new Message[] { message };
                            }
                            return new Message[0];
                        } catch (Exception e) {
                            throw AblyException.fromThrowable(e);
                        }
                    }
                };
                final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null;
                http.get(basePath + "/messages/" + HttpUtils.encodeURIComponent(serial),
                    HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol),
                    params,
                    (response, error) -> {
                        if (error != null) throw AblyException.fromErrorInfo(error);
                        Message[] messages = bodyHandler.handleResponseBody(response.contentType, response.body);
                        if (messages != null && messages.length > 0) return messages[0];
                        throw AblyException.fromErrorInfo(new ErrorInfo("Message not found", 404, 40400));
                    },
                    true,
                    callback);
            }
        });
    }

    /**
     * Updates an existing message by its serial identifier using patch semantics.
     * <p>
     * Non-null fields in the provided message (name, data, extras) will replace the corresponding
     * fields in the existing message, while null fields will be left unchanged.
     *
     * @param serial The unique serial identifier of the message to update.
     * @param message A {@link Message} object containing the fields to update.
     *                Only non-null fields will be applied to the existing message.
     * @throws AblyException If the update operation fails.
     */
    public void updateMessage(Http http, String serial, Message message) throws AblyException {
        updateMessageImpl(http, serial, message).sync();
    }

    /**
     * Asynchronously updates an existing message by its serial identifier.
     *
     * @param serial The unique serial identifier of the message to update.
     * @param message A {@link Message} object containing the fields to update.
     * @param listener A listener to be notified of the outcome of this operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void updateMessageAsync(Http http, String serial, Message message, CompletionListener listener) {
        updateMessageImpl(http, serial, message).async(new CompletionListener.ToCallback(listener));
    }

    private Http.Request<Void> updateMessageImpl(Http http, String serial, final Message message) {
        return http.request((http1, callback) -> {
            if (serial == null || serial.isEmpty()) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
            }
            /* Set action to MESSAGE_UPDATE */
            message.action = MessageAction.MESSAGE_UPDATE;
            /* RTL6g3 */
            auth.checkClientId(message, true, false);
            message.encode(channelOptions);

            HttpCore.RequestBody requestBody = clientOptions.useBinaryProtocol ?
                MessageSerializer.asMsgpackRequest(message) :
                MessageSerializer.asJsonRequest(message);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null;

            http1.patch(basePath + "/messages/" + HttpUtils.encodeURIComponent(serial),
                HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol),
                params,
                requestBody,
                null,
                true,
                callback);
        });
    }

    /**
     * Marks a message as deleted by its serial identifier.
     * <p>
     * This operation does not remove the message from history; it marks it as deleted
     * while preserving the full message history. The deleted message can still be
     * retrieved and will have its action set to MESSAGE_DELETE.
     *
     * @param serial The unique serial identifier of the message to delete.
     * @param message A {@link Message} object that may contain operation metadata such as
     *                clientId, description, or metadata in the version field.
     * @throws AblyException If the delete operation fails.
     */
    public void deleteMessage(Http http, String serial, Message message) throws AblyException {
        deleteMessageImpl(http, serial, message).sync();
    }

    /**
     * Asynchronously marks a message as deleted by its serial identifier.
     *
     * @param serial The unique serial identifier of the message to delete.
     * @param message A {@link Message} object for operation metadata.
     * @param listener A listener to be notified of the outcome of this operation.
     * <p>
     * This listener is invoked on a background thread.
     */
    public void deleteMessageAsync(Http http, String serial, Message message, CompletionListener listener) {
        deleteMessageImpl(http, serial, message).async(new CompletionListener.ToCallback(listener));
    }

    private Http.Request<Void> deleteMessageImpl(Http http, String serial, final Message message) {
        return http.request((http1, callback) -> {
            if (serial == null || serial.isEmpty()) {
                throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
            }
            /* Set action to MESSAGE_DELETE */
            message.action = MessageAction.MESSAGE_DELETE;
            /* RTL6g3 */
            auth.checkClientId(message, true, false);
            message.encode(channelOptions);

            HttpCore.RequestBody requestBody = clientOptions.useBinaryProtocol ?
                MessageSerializer.asMsgpackRequest(message) :
                MessageSerializer.asJsonRequest(message);
            final Param[] params = clientOptions.addRequestIds ? Param.array(Crypto.generateRandomRequestId()) : null;

            http1.post(basePath + "/messages/" + HttpUtils.encodeURIComponent(serial),
                HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol),
                params,
                requestBody,
                null,
                true,
                callback);
        });
    }

    /**
     * Retrieves all historical versions of a specific message.
     * <p>
     * This method returns a paginated result containing all versions of the message,
     * ordered chronologically. Each version includes metadata about when and by whom
     * the message was modified.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination (e.g., limit, start, end).
     * @return A {@link PaginatedResult} containing an array of {@link Message} objects
     *         representing all versions of the message.
     * @throws AblyException If the versions cannot be retrieved.
     */
    public PaginatedResult<Message> getMessageVersions(Http http, String serial, Param[] params) throws AblyException {
        return getMessageVersionsImpl(http, serial, params).sync();
    }

    /**
     * Asynchronously retrieves all historical versions of a specific message.
     *
     * @param serial The unique serial identifier of the message.
     * @param params Query parameters for filtering or pagination.
     * @param callback A callback to handle the result asynchronously.
     */
    public void getMessageVersionsAsync(Http http, String serial, Param[] params, Callback<AsyncPaginatedResult<Message>> callback) throws AblyException {
        getMessageVersionsImpl(http, serial, params).async(callback);
    }

    private BasePaginatedQuery.ResultRequest<Message> getMessageVersionsImpl(Http http, String serial, Param[] initialParams) throws AblyException {
        if (serial == null || serial.isEmpty()) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Message serial cannot be empty", 400, 40003));
        }
        HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(channelOptions);
        final Param[] params = clientOptions.addRequestIds ? Param.set(initialParams, Crypto.generateRandomRequestId()) : initialParams;
        return (new BasePaginatedQuery<>(http, basePath + "/messages/" + HttpUtils.encodeURIComponent(serial) + "/versions",
            HttpUtils.defaultAcceptHeaders(clientOptions.useBinaryProtocol), params, bodyHandler)).get();
    }

}
