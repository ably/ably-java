package io.ably.lib.push;

import android.content.Context;
import android.content.res.Configuration;
import com.google.gson.JsonObject;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.Param;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

public class LocalDevice extends DeviceDetails {
    public String deviceSecret;
    public String deviceIdentityToken;
    private final Storage storage;

    private final ActivationContext activationContext;

    public LocalDevice(ActivationContext activationContext, Storage storage) {
        super();
        Log.v(TAG, "LocalDevice(): initialising");
        this.platform = "android";
        this.formFactor = isTablet(activationContext.getContext()) ? "tablet" : "phone";
        this.activationContext = activationContext;
        this.push = new DeviceDetails.Push();
        this.storage = storage != null ? storage : new SharedPreferenceStorage(activationContext);
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
        String id = storage.get(SharedPrefKeys.DEVICE_ID, null);
        this.id = id;
        if(id != null) {
            Log.v(TAG, "loadPersisted(): existing deviceId found; id: " + id);
            deviceSecret = storage.get(SharedPrefKeys.DEVICE_SECRET, null);
        } else {
            Log.v(TAG, "loadPersisted(): existing deviceId not found.");
        }
        this.clientId = storage.get(SharedPrefKeys.CLIENT_ID, null);
        this.deviceIdentityToken = storage.get(SharedPrefKeys.DEVICE_TOKEN, null);

        final int ordinalTypeValue = storage.get(SharedPrefKeys.TOKEN_TYPE, -1);
        if (ordinalTypeValue != RegistrationToken.TOKEN_TYPE_ORDINAL_VALUE_FCM) {
            Log.e(TAG, "Cannot load persisted token because ordinal does not indicate FCM (it's " + ordinalTypeValue + ").");
            return;
        }

        RegistrationToken token = null;
        String tokenString = storage.get(SharedPrefKeys.TOKEN, null);
        Log.d(TAG, "loadPersisted(): FCM token string = " + tokenString);
        if(tokenString != null) {
            token = new RegistrationToken(tokenString);
            setRegistrationToken(token);
        }
    }

    RegistrationToken getRegistrationToken() {
        JsonObject recipient = push.recipient;
        if(recipient == null) {
            Log.v(TAG, "getRegistrationToken(): returning null because push.recipient is null");
            return null;
        }

        // GCM is no longer active, therefore we only support FCM.
        final String tokenType = recipient.get("transportType").getAsString();
        if (tokenType != RegistrationToken.TOKEN_TYPE_STRING_VALUE_FCM) {
            // Presumably GCM, which has been deactivated by Google.
            Log.e(TAG, "getRegistrationToken(): returning null because push.recipient.transportType is not FCM (it's \"" + tokenType + "\")");
            return null;
        }

        Log.v(TAG, "getRegistrationToken(): returning a new registration token because push.recipient is set and is FCM");
        return new RegistrationToken(recipient.get("registrationToken").getAsString());
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
        storage.put(SharedPrefKeys.TOKEN_TYPE, token.type.ordinal());
        storage.put(SharedPrefKeys.TOKEN, token.token);
    }

    void setClientId(String clientId) {
        Log.v(TAG, "setClientId(): clientId=" + clientId);
        this.clientId = clientId;
        storage.put(SharedPrefKeys.CLIENT_ID, clientId);
    }

    public void setDeviceIdentityToken(String token) {
        Log.v(TAG, "setDeviceIdentityToken(): token=" + token);
        this.deviceIdentityToken = token;
        storage.put(SharedPrefKeys.DEVICE_TOKEN, token);
    }

    boolean isCreated() {
        return id != null;
    }

    void create() {
        /* Spec: RSH8b */
        Log.v(TAG, "create()");
        storage.put(SharedPrefKeys.DEVICE_ID, (id = UUID.randomUUID().toString()));
        storage.put(SharedPrefKeys.CLIENT_ID, (clientId = activationContext.clientId));
        storage.put(SharedPrefKeys.DEVICE_SECRET, (deviceSecret = generateSecret()));
    }

    public void reset() {
        Log.v(TAG, "reset()");
        this.id = null;
        this.deviceSecret = null;
        this.deviceIdentityToken = null;
        this.clientId = null;
        this.clearRegistrationToken();

        storage.clear(SharedPrefKeys.class.getDeclaredFields());
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

        /**
         * Historically this value was required because we had two token types, GCM and FCM.
         * Google have now deactivated GCM, so the only currently supported type is FCM.
         * The value stored against this key is an integer ordinal, where:
         * - 0 represents GCM (not supported)
         * - 1 represents FCM (supported) - defined, for clarity, as {@link RegistrationToken#TOKEN_TYPE_ORDINAL_VALUE_FCM}
         */
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
