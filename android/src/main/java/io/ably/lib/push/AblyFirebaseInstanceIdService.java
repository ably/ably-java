package io.ably.lib.push;

import android.content.Context;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
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
		ActivationContext.getActivationContext(context.getApplicationContext()).onNewRegistrationToken(RegistrationToken.Type.FCM, token);
	}

	private static final String TAG = AblyFirebaseInstanceIdService.class.getName();
}
