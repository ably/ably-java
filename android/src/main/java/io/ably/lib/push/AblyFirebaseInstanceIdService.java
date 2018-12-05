package io.ably.lib.push;

import android.content.Context;
import android.util.Log;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessagingService;
import io.ably.lib.types.RegistrationToken;

public class AblyFirebaseInstanceIdService extends FirebaseInstanceIdService {

	@Override
	public void onTokenRefresh() {
		// Get updated InstanceID token.
		String token = FirebaseInstanceId.getInstance().getToken();
		onNewRegistrationToken(this, token);
	}

	/**
	 * Update Ably with the Registration Token
	 * @param context
	 * @param token
	 */
	public static void onNewRegistrationToken(Context context, String token) {
		Log.i(TAG, "Firebase Refreshed token: " + token);
		ActivationContext.getActivationContext(context.getApplicationContext()).onNewRegistrationToken(RegistrationToken.Type.FCM, token);
	}

	private static final String TAG = AblyFirebaseInstanceIdService.class.getName();
}
