package io.ably.lib.rest;

import com.google.gson.JsonObject;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import io.ably.lib.types.Function;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Base64Coder;
import io.azam.ulidj.ULID;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class LocalDevice extends DeviceDetails {
    public String deviceSecret;
    public String deviceIdentityToken;

    private final AblyRest rest;

    private LocalDevice(AblyRest rest) {
        super();
        this.rest = rest;
        this.push = new DeviceDetails.Push();
    }

    public JsonObject toJsonObject() {
        JsonObject o = super.toJsonObject();
        if (deviceSecret != null) {
            o.addProperty("deviceSecret", deviceSecret);
        }

        return o;
    }

    protected static LocalDevice load(Context context, AblyRest rest) {
        LocalDevice device = new LocalDevice(rest);
        device.loadPersisted(context, rest);
        return device;
    }

    protected void loadPersisted(Context context, AblyRest rest) {
        this.platform = "android";
        this.clientId = rest.auth.clientId;
        this.formFactor = isTablet(context) ? "tablet" : "phone";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
        this.id = id;
        if (id == null) {
            this.resetId(context);
        }
        this.deviceIdentityToken = prefs.getString(SharedPrefKeys.DEVICE_TOKEN, null);

        RegistrationToken.Type type = RegistrationToken.Type.fromInt(
            prefs.getInt(SharedPrefKeys.TOKEN_TYPE, -1));

        RegistrationToken token = null;
        if (type != null) {
            token = new RegistrationToken(type, prefs.getString(SharedPrefKeys.TOKEN, null));
            if(token != null) {
                setRegistrationToken(token);
            }
        }
    }

    protected RegistrationToken getRegistrationToken() {
        if (push == null) {
            return null;
        }
        JsonObject recipient = push.recipient;
        if (recipient == null) {
            return null;
        }
        return new RegistrationToken(
            RegistrationToken.Type.fromCode(recipient.get("transportType").getAsString()),
            recipient.get("registrationToken").getAsString()
        );
    }

    private void setRegistrationToken(RegistrationToken token) {
        push.recipient = new JsonObject();
        push.recipient.addProperty("transportType", token.type.code);
        push.recipient.addProperty("registrationToken", token.token);
    }

    private void setRegistrationToken(RegistrationToken.Type type, String token) {
        setRegistrationToken(new RegistrationToken(type, token));
    }

    protected void setAndPersistRegistrationToken(Context context, RegistrationToken token) {
        setRegistrationToken(token);        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putInt(SharedPrefKeys.TOKEN_TYPE, token.type.toInt())
            .putString(SharedPrefKeys.TOKEN, token.token)
            .apply();
    }

    public void setDeviceToken(Context context, String token) {
        this.deviceIdentityToken = token;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(SharedPrefKeys.DEVICE_TOKEN, token).apply();
    }

    public boolean resetId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(SharedPrefKeys.DEVICE_ID, (id = ULID.random()));
        editor.putString(SharedPrefKeys.DEVICE_SECRET, (deviceSecret = generateSecret()));

        return editor.commit();
    }

    // Returns a function to be called if the editing succeeds, to refresh the object's fields.
    public Function<Context, Void> reset(SharedPreferences.Editor editor) {
        for (Field f : SharedPrefKeys.class.getDeclaredFields()) {
            try {
                editor.remove((String) f.get(null));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return new Function<Context, Void>() {
            @Override
            public Void call(Context context) {
                LocalDevice.this.loadPersisted(context, rest);
                return null;
            }
        };
    }

    private static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static class SharedPrefKeys {
        static final String DEVICE_ID = "ABLY_DEVICE_ID";
        static final String DEVICE_SECRET = "ABLY_DEVICE_SECRET";
        static final String DEVICE_TOKEN = "ABLY_DEVICE_IDENTITY_TOKEN";
        static final String TOKEN_TYPE = "ABLY_REGISTRATION_TOKEN_TYPE";
        static final String TOKEN = "ABLY_REGISTRATION_TOKEN";
    }

    private static String generateSecret() {
        byte[] entropy = new byte[64];
        (new SecureRandom()).nextBytes(entropy);
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {}
        byte[] encodedhash = digest.digest(entropy);
        return Base64Coder.encode(encodedhash).toString();
    }
}
