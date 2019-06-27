package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import com.google.gson.JsonObject;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.azam.ulidj.ULID;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class LocalDevice extends DeviceDetails {
	public String deviceSecret;
	public String deviceIdentityToken;

	private final ActivationContext activationContext;

	public LocalDevice(ActivationContext activationContext) {
		super();
		Log.v(TAG, "LocalDevice(): initialising");
		this.activationContext = activationContext;
		this.push = new DeviceDetails.Push();
		try {
			loadPersisted();
		} catch(AblyException e) {
			Log.e(TAG, "unable to load local device state");
		}
	}

	public JsonObject toJsonObject() {
		JsonObject o = super.toJsonObject();
		if (deviceSecret != null) {
			o.addProperty("deviceSecret", deviceSecret);
		}

		return o;
	}

	private void loadPersisted() throws AblyException {
		this.platform = "android";
		this.clientId = activationContext.clientId;
		this.formFactor = isTablet(activationContext.getContext()) ? "tablet" : "phone";

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());

		String id = prefs.getString(SharedPrefKeys.DEVICE_ID, null);
		this.id = id;
		if(id != null) {
			Log.v(TAG, "loadPersisted(): existing deviceId found; id: " + id);
		} else {
			this.resetId();
			Log.v(TAG, "loadPersisted(): no existing deviceId found; generated id: " + this.id);
		}
		this.deviceIdentityToken = prefs.getString(SharedPrefKeys.DEVICE_TOKEN, null);

		RegistrationToken.Type type = RegistrationToken.Type.fromOrdinal(
			prefs.getInt(SharedPrefKeys.TOKEN_TYPE, -1));

		Log.d(TAG, "loadPersisted(): token type = " + type);
		if(type != null) {
			RegistrationToken token = null;
			String tokenString = prefs.getString(SharedPrefKeys.TOKEN, null);
			if(tokenString != null) {
				Log.d(TAG, "loadPersisted(): token string = " + tokenString);
				token = new RegistrationToken(type, tokenString);
				setRegistrationToken(token);
			}
		}
	}

	RegistrationToken getRegistrationToken() {
		JsonObject recipient = push.recipient;
		if(recipient == null) {
			return null;
		}
		return new RegistrationToken(
			RegistrationToken.Type.fromName(recipient.get("transportType").getAsString()),
			recipient.get("registrationToken").getAsString()
		);
	}

	private void setRegistrationToken(RegistrationToken token) {
		push.recipient = new JsonObject();
		push.recipient.addProperty("transportType", token.type.toName());
		push.recipient.addProperty("registrationToken", token.token);
	}

	void setAndPersistRegistrationToken(RegistrationToken token) {
		Log.v(TAG, "setAndPersistRegistrationToken(): token: " + token.token);
		setRegistrationToken(token);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
		prefs.edit()
			.putInt(SharedPrefKeys.TOKEN_TYPE, token.type.ordinal())
			.putString(SharedPrefKeys.TOKEN, token.token)
			.apply();
	}

	public void setDeviceIdentityToken(String token) {
		this.deviceIdentityToken = token;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
		prefs.edit().putString(SharedPrefKeys.DEVICE_TOKEN, token).apply();
	}

	public boolean resetId() {
		Log.v(TAG, "resetId()");
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
		SharedPreferences.Editor editor = prefs.edit();

		editor.putString(SharedPrefKeys.DEVICE_ID, (id = ULID.random()));
		editor.putString(SharedPrefKeys.DEVICE_SECRET, (deviceSecret = generateSecret()));

		return editor.commit();
	}

	public void reset() {
		Log.v(TAG, "reset()");
		SharedPreferences.Editor editor = activationContext.getPreferences().edit();
		for (Field f : SharedPrefKeys.class.getDeclaredFields()) {
			if(f.getName().startsWith("ABLY")) {
				try {
					editor.remove((String) f.get(null));
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
		editor.commit();
	}

	void setClientId(String clientId) {
		this.clientId = clientId;
	}

	boolean isRegistered() {
		return (deviceIdentityToken != null);
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

	private static final String TAG = LocalDevice.class.getName();
}
