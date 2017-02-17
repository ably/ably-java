package io.ably.lib.rest;

import com.google.gson.JsonObject;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.RegistrationToken;
import io.azam.ulidj.ULID;
import android.util.Log;

class LocalDevice extends DeviceDetails {
    private final AblyRest rest;

    private LocalDevice(AblyRest rest) {
        super();
        this.rest = rest;
    }

    protected static LocalDevice load(Context context, AblyRest rest) {
        LocalDevice device = new LocalDevice(rest);
        device.platform = "android";
        device.clientId = rest.auth.clientId;
        device.formFactor = isTablet(context) ? "tablet" : "mobile";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
        if (id == null) {
            id = ULID.random();
            boolean ok = prefs.edit().putString(SharedPrefKeys.DEVICE_ID, id).commit();
            if (!ok) {
                id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
            }
        }
        device.id = id;
        device.updateToken = prefs.getString(SharedPrefKeys.UPDATE_TOKEN, null);

        RegistrationToken.Type type = RegistrationToken.Type.fromInt(
            prefs.getInt(SharedPrefKeys.TOKEN_TYPE, -1));
        if (type != null) {
            String token = prefs.getString(SharedPrefKeys.TOKEN, null);
            if (token != null) {
                device.setRegistrationToken(type, token);
            }
        }

        return device;
    }

    protected RegistrationToken getRegistrationToken() {
        if (push == null) {
            return null;
        }
        JsonObject metadata = push.metadata;
        if (metadata == null) {
            return null;
        }
        return new RegistrationToken(
            RegistrationToken.Type.fromCode(push.transportType),
            metadata.get("registrationToken").getAsString()
        );
    }

    private void setRegistrationToken(RegistrationToken token) {
        setRegistrationToken(token.type, token.token);
    }

    private void setRegistrationToken(RegistrationToken.Type type, String token) {
        push = new DeviceDetails.Push();
        push.transportType = type.code;
        push.metadata = new JsonObject();
        push.metadata.addProperty("registrationToken", token);
    }

    protected void setAndPersistRegistrationToken(Context context, RegistrationToken token) {
        setRegistrationToken(token);        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putInt(SharedPrefKeys.TOKEN_TYPE, token.type.toInt())
            .putString(SharedPrefKeys.TOKEN, token.token)
            .apply();
    }

    protected void setAndPersistUpdateToken(Context context, String token) {
        this.updateToken = token;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(SharedPrefKeys.UPDATE_TOKEN, token).apply();
    }

    public void resetUpdateToken() throws AblyException {
        if (this.id == null || this.updateToken == null) {
            return;
        }
        // TODO
    }

    private static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static class SharedPrefKeys {
        private static final String DEVICE_ID = "ABLY_DEVICE_ID";
        private static final String UPDATE_TOKEN = "ABLY_DEVICE_UPDATE_TOKEN";
        private static final String TOKEN_TYPE = "ABLY_REGISTRATION_TOKEN_TYPE";
        private static final String TOKEN = "ABLY_REGISTRATION_TOKEN";
    }
}
