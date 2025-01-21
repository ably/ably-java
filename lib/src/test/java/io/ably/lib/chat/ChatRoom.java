package io.ably.lib.chat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.HttpPaginatedResponse;
import io.ably.lib.types.Param;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class ChatRoom {
    private final AblyRest ablyRest;
    private final String roomId;

    protected ChatRoom(String roomId, AblyRest ablyRest) {
        this.roomId = roomId;
        this.ablyRest = ablyRest;
    }

    public JsonElement sendMessage(SendMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages", "POST", new Gson().toJsonTree(params))
            .orElseThrow(() -> new Exception("Failed to send message"));
    }

    public JsonElement updateMessage(String serial, UpdateMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages/" + serial, "PUT", new Gson().toJsonTree(params))
            .orElseThrow(() -> new Exception("Failed to update message"));
    }

    public JsonElement deleteMessage(String serial, DeleteMessageParams params) throws Exception {
        return makeAuthorizedRequest("/chat/v2/rooms/" + roomId + "/messages/" + serial + "/delete", "POST", new Gson().toJsonTree(params))
            .orElseThrow(() -> new Exception("Failed to delete message"));
    }

    public static class SendMessageParams {
        public String text;
        public JsonObject metadata;
        public Map<String, String> headers;
    }

    public static class UpdateMessageParams {
        public SendMessageParams message;
        public String description;
        public Map<String, String> metadata;
    }

    public static class DeleteMessageParams {
        public String description;
        public Map<String, String> metadata;
    }

    protected Optional<JsonElement> makeAuthorizedRequest(String url, String method, JsonElement body) throws AblyException {
        HttpCore.RequestBody httpRequestBody = HttpUtils.requestBodyFromGson(body, ablyRest.options.useBinaryProtocol);
        HttpPaginatedResponse response = ablyRest.request(method, url, new Param[] { new Param("v", 3) }, httpRequestBody, null);
        return Arrays.stream(response.items()).findFirst();
    }
}
