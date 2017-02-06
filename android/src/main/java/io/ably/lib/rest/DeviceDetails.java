package io.ably.lib.rest;

import com.google.gson.JsonObject;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import io.ably.lib.types.AblyException;
import io.azam.ulidj.ULID;
import android.util.Log;

class DeviceDetails extends DeviceDetailsBase {
    private final AblyRest rest;

    private DeviceDetails(AblyRest rest) {
        this.rest = rest;
    }

    protected static DeviceDetails loadFromDevice(Context context, AblyRest rest) {
        DeviceDetails details = new DeviceDetails(rest);
        details.platform = "android";
        details.clientId = rest.auth.clientId;
        details.formFactor = isTablet(context) ? "tablet" : "mobile";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
        if (id == null) {
            id = ULID.random();
            boolean ok = prefs.edit().putString(SharedPrefKeys.DEVICE_ID, id).commit();
            if (!ok) {
                id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
            }
        }
        details.id = id;

        details.updateToken = prefs.getString(SharedPrefKeys.UPDATE_TOKEN, null);

        return details;
    }

    protected void setRegistrationToken(io.ably.lib.rest.Push.RegistrationToken token) {
        if (token == null) {
            this.push = null;
            return;
        }
        this.push = new DeviceDetails.Push();
        this.push.transportType = token.type.code;
        this.push.metadata = new JsonObject();
        this.push.metadata.addProperty("registrationToken", token.token);
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
    }
}
