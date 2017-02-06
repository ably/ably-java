package io.ably.lib.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.Param;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by tcard on 3/2/17.
 */
public class Push {
    public Push(AblyRest rest) {
        this.rest = rest;
    }

    public void publish(Param[] recipient, JsonObject payload) throws AblyException {
        rest.http.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null);
    }

    public void publishAsync(Param[] recipient, JsonObject payload, final CompletionListener listener) {
        rest.asyncHttp.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) { listener.onSuccess(); }
            @Override
            public void onError(ErrorInfo reason) { listener.onError(reason); }
        });
    }

    private Http.RequestBody publishBody(Param[] recipient, JsonObject payload) {
        JsonObject recipientJson = new JsonObject();
        for (Param param : recipient) {
            recipientJson.addProperty(param.key, param.value);
        }
        JsonObject bodyJson = new JsonObject();
        bodyJson.add("recipient", recipientJson);
        for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
            bodyJson.add(entry.getKey(), entry.getValue());
        }
        return rest.http.requestBodyFromGson(bodyJson);
    }

    public void activate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(SharedPrefKeys.ACTIVATED, false)) {
            // If already successfully activated, trigger the activation broadcast right away,
            // so that the caller of activate can act on it.
            new ActivateCallback(context, "PUSH_ACTIVATED").onSuccess(null);
            return;
        }
        if (prefs.getBoolean(SharedPrefKeys.ACTIVATING, false)) {
            // If there's an activation attempt underway, do nothing; the broadcast will be
            // triggered in the end.
            return;
        }
        prefs.edit().putBoolean(SharedPrefKeys.ACTIVATING, true).commit();
        RegistrationToken regToken = RegistrationToken.persisted(context);
        // regToken will be null if F/GCM didn't provide it yet; activateWithToken will then do
        // nothing. When onNewRegistrationToken is called by F/GCM with the token, it will call
        // activateWithToken with it.
        activateWithToken(context, regToken);
    }

    public void onNewRegistrationToken(Context context, RegistrationTokenType type, String token) {
        RegistrationToken persisted = RegistrationToken.persisted(context);
        if (persisted != null && persisted.type != type) {
            Log.e(TAG, "trying to register device with " + type + ", but it was already registered with " + persisted.type);
            return;
        }

        RegistrationToken regToken = RegistrationToken.persistAndReturn(context, type, token);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(SharedPrefKeys.ACTIVATING, false)) {
            // If not ACTIVATING, ie. if activate wasn't called, just let the token in storage and
            // be done here; when activate is called, it will pick up the token and call
            // activateWithToken.
            return;
        }

        activateWithToken(context, regToken);
    }

    private void activateWithToken(final Context context, RegistrationToken token) {
        // Preconditions: ACTIVATING = true; ACTIVATED = false
        // Postconditions: ACTIVATING = false; ACTIVATED = true iff succeeded (token renewed or new registration)
        if (token == null) {
            // If there's no token, onNewRegistrationToken will call this again with the token.
            return;
        }

        final DeviceDetails device = rest.device != null ? rest.device : DeviceDetails.loadFromDevice(context, rest);

        device.setRegistrationToken(token);

        boolean isNewRegistration = device.updateToken == null;
        if (isNewRegistration) {
            Http.RequestBody body = rest.http.requestBodyFromGson(device.toJsonObject());
            rest.asyncHttp.post("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, new Serialisation.HttpResponseHandler<JsonObject>(), new Callback<JsonObject>() {
                @Override
                public void onSuccess(JsonObject response) {
                    device.setAndPersistUpdateToken(context, response.getAsJsonPrimitive("updateToken").getAsString());
                    rest.device = device;
                    new ActivateCallback(context, "PUSH_ACTIVATED").onSuccess(null);
                }
                @Override
                public void onError(ErrorInfo reason) {
                    new ActivateCallback(context, "PUSH_ACTIVATED").onError(reason);
                }
            });
        } else {
            rest.device = device;
            Http.RequestBody body = rest.http.requestBodyFromGson(device.pushMetadataJsonObject());
            rest.asyncHttp.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null, new ActivateCallback(context, "PUSH_RENEWED_TOKEN"));
        }
    }

    private class ActivateCallback implements Callback<Void> {
        private Context context;
        private String action;

        ActivateCallback(Context context, String action) {
            this.context = context;
            this.action = action;
        }

        @Override
        public void onSuccess(Void v) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit()
                    .putBoolean(SharedPrefKeys.ACTIVATING, false)
                    .putBoolean(SharedPrefKeys.ACTIVATED, true)
                    .apply();
            Intent intent = new Intent();
            intent.setAction("io.ably.broadcast." + action);
            intent.putExtra("failed", false);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
        @Override
        public void onError(ErrorInfo reason) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit()
                    .putBoolean(SharedPrefKeys.ACTIVATING, false)
                    .putBoolean(SharedPrefKeys.ACTIVATED, false)
                    .apply();
            Intent intent = new Intent();
            intent.setAction("io.ably.broadcast." + action);
            intent.putExtra("failed", true);
            intent.putExtra("errorReason.message", reason.message);
            intent.putExtra("errorReason.code", reason.code);
            intent.putExtra("errorReason.statusCode", reason.statusCode);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public static class RegistrationToken {
        public RegistrationTokenType type;
        public String token;

        private RegistrationToken(RegistrationTokenType type, String token) {
            this.type = type;
            this.token = token;
        }

        public static RegistrationToken persisted(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            RegistrationTokenType type = RegistrationTokenType.fromInt(
                    prefs.getInt(SharedPrefKeys.TOKEN_TYPE, -1));
            if (type == null) {
                return null;
            }
            String token = prefs.getString(SharedPrefKeys.TOKEN, null);
            if (token == null) {
                return null;
            }
            return new RegistrationToken(type, token);
        }

        public static RegistrationToken persistAndReturn(Context context, RegistrationTokenType type, String token) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean ok = prefs.edit()
                    .putInt(SharedPrefKeys.TOKEN_TYPE, type.toInt())
                    .putString(SharedPrefKeys.TOKEN, token)
                    .commit();
            if (!ok) {
                return RegistrationToken.persisted(context);
            }
            return new RegistrationToken(type, token);
        }

        private class SharedPrefKeys {
            private static final String TOKEN_TYPE = "ABLY_REGISTRATION_TOKEN_TYPE";
            private static final String TOKEN = "ABLY_REGISTRATION_TOKEN";
        }
    }

    public enum RegistrationTokenType {
        GCM("gcm"),
        FCM("fcm");

        public String code;
        RegistrationTokenType(String code) {
            this.code = code;
        }

        public int toInt() {
            RegistrationTokenType[] values = RegistrationTokenType.values();
            for (int i = 0; i < values.length; i++) {
                if (this == values[i]) {
                    return i;
                }
            }
            return -1;
        }

        public static RegistrationTokenType fromInt(int i) {
            RegistrationTokenType[] values = RegistrationTokenType.values();
            if (i < 0 || i >= values.length) {
                return null;
            }
            return values[i];
        }
    }

    private final AblyRest rest;

    private static final String TAG = Push.class.getName();

    private static class SharedPrefKeys {
        private static final String ACTIVATING = "ABLY_PUSH_ACTIVATING";
        private static final String ACTIVATED = "ABLY_PUSH_ACTIVATED";
    }
}
