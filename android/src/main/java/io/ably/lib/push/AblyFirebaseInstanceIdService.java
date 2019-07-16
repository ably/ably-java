package io.ably.lib.push;

import android.content.Context;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Log;

public class AblyFirebaseInstanceIdService {

	/**
	 * Update Ably with the Registration Token
	 * @param context
	 * @param token
	 */
	public static void onNewRegistrationToken(Context context, String token) {
		if(token != null && token.length() > 10) {
			Log.i(TAG, "Firebase token registered: " + token.substring(0,10));
		}
		ActivationContext.getActivationContext(context.getApplicationContext()).onNewRegistrationToken(RegistrationToken.Type.FCM, token);
	}

	private static final String TAG = AblyFirebaseInstanceIdService.class.getName();
}
