package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.types.Param;
import io.azam.ulidj.ULID;

public class LocalDevice extends DeviceDetails {
    public String deviceSecret;
    public String deviceIdentityToken;

    private final ActivationContext activationContext;

    public LocalDevice(ActivationContext activationContext) {
        super();
        Log.v(TAG, "LocalDevice(): initialising");
        this.platform = "android";
        this.formFactor = isTablet(activationContext.getContext()) ? "tablet" : "phone";
        this.activationContext = activationContext;
        this.push = new DeviceDetails.Push();
        loadPersisted();
    }

    public JsonObject toJsonObject() {
        JsonObject o = super.toJsonObject();
        if (deviceSecret != null) {
            o.addProperty("deviceSecret", deviceSecret);
        }

        return o;
    }

    private void loadPersisted() {
        /* Spec: RSH8a */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());

        String id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
        this.id = id;
        if(id != null) {
            Log.v(TAG, "loadPersisted(): existing deviceId found; id: " + id);
            deviceSecret = prefs.getString(SharedPrefKeys.DEVICE_SECRET, null);
        } else {
            Log.v(TAG, "loadPersisted(): existing deviceId not found.");
        }
        this.clientId = prefs.getString(SharedPrefKeys.CLIENT_ID, null);
        this.deviceIdentityToken = prefs.getString(SharedPrefKeys.DEVICE_TOKEN, null);

        RegistrationToken.Type type = RegistrationToken.Type.fromOrdinal(
            prefs.getInt(SharedPrefKeys.TOKEN_TYPE, -1));

        Log.d(TAG, "loadPersisted(): token type = " + type);
        if(type != null) {
            RegistrationToken token = null;
            String tokenString = prefs.getString(SharedPrefKeys.TOKEN, null);
            Log.d(TAG, "loadPersisted(): token string = " + tokenString);
            if(tokenString != null) {
                token = new RegistrationToken(type, tokenString);
                setRegistrationToken(token);
            }
        }
    }

    RegistrationToken getRegistrationToken() {
        JsonObject recipient = push.recipient;
        if(recipient == null) {
            Log.v(TAG, "getRegistrationToken(): returning null because push.recipient is null");
            return null;
        }
        Log.v(TAG, "getRegistrationToken(): returning a new registration token because push.recipient is set");
        return new RegistrationToken(
            RegistrationToken.Type.fromName(recipient.get("transportType").getAsString()),
            recipient.get("registrationToken").getAsString()
        );
    }

    private void setRegistrationToken(RegistrationToken token) {
        Log.v(TAG, "setRegistrationToken(): token=" + token);
        push.recipient = new JsonObject();
        push.recipient.addProperty("transportType", token.type.toName());
        push.recipient.addProperty("registrationToken", token.token);
    }

    private void clearRegistrationToken() {
        Log.v(TAG, "clearRegistrationToken()");
        push.recipient = null;
    }

    void setAndPersistRegistrationToken(RegistrationToken token) {
        Log.v(TAG, "setAndPersistRegistrationToken(): token=" + token);
        setRegistrationToken(token);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        prefs.edit()
            .putInt(SharedPrefKeys.TOKEN_TYPE, token.type.ordinal())
            .putString(SharedPrefKeys.TOKEN, token.token)
            .apply();
    }

    void setClientId(String clientId) {
        Log.v(TAG, "setClientId(): clientId=" + clientId);
        this.clientId = clientId;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        prefs.edit().putString(SharedPrefKeys.CLIENT_ID, clientId).apply();
    }

    public void setDeviceIdentityToken(String token) {
        Log.v(TAG, "setDeviceIdentityToken(): token=" + token);
        this.deviceIdentityToken = token;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        prefs.edit().putString(SharedPrefKeys.DEVICE_TOKEN, token).apply();
    }

    boolean isCreated() {
        return id != null;
    }

    boolean create() {
        /* Spec: RSH8b */
        Log.v(TAG, "create()");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(SharedPrefKeys.DEVICE_ID, (id = ULID.random()));
        editor.putString(SharedPrefKeys.CLIENT_ID, (clientId = activationContext.clientId));
        editor.putString(SharedPrefKeys.DEVICE_SECRET, (deviceSecret = generateSecret()));

        return editor.commit();
    }

    public void reset() {
        Log.v(TAG, "reset()");
        this.id = null;
        this.deviceSecret = null;
        this.deviceIdentityToken = null;
        this.clientId = null;
        this.clearRegistrationToken();

        SharedPreferences.Editor editor = activationContext.getPreferences().edit();
        for (Field f : SharedPrefKeys.class.getDeclaredFields()) {
            try {
                editor.remove((String) f.get(null));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        editor.commit();
    }

    boolean isRegistered() {
        return (deviceIdentityToken != null);
    }

    Param[] deviceIdentityHeaders() {
        return deviceIdentityToken != null ? new Param[]{new Param(DEVICE_IDENTITY_HEADER, Base64Coder.encodeString(deviceIdentityToken))} : null;
    }

    private static final String DEVICE_IDENTITY_HEADER = "X-Ably-DeviceToken";

    private static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static class SharedPrefKeys {
        static final String DEVICE_ID = "ABLY_DEVICE_ID";
        static final String CLIENT_ID = "ABLY_CLIENT_ID";
        static final String DEVICE_SECRET = "ABLY_DEVICE_SECRET";
        static final String DEVICE_TOKEN = "ABLY_DEVICE_IDENTITY_TOKEN";
        static final String TOKEN_TYPE = "ABLY_REGISTRATION_TOKEN_TYPE";
        static final String TOKEN = "ABLY_REGISTRATION_TOKEN";
    }

    private static String generateSecret() {
        Log.v(TAG, "generateSecret()");
        byte[] entropy = new byte[64];
        (new SecureRandom()).nextBytes(entropy);
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {}
        byte[] encodedhash = digest.digest(entropy);
        return Base64Coder.encodeToString(encodedhash);
    }

    private static final String TAG = LocalDevice.class.getName();
}
