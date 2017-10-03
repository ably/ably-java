package io.ably.lib.fcm;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.RegistrationToken;

public class AblyFirebaseInstanceIdService extends FirebaseInstanceIdService {
    private static String TAG = "AblyFirebaseInstanceId";

    public void onTokenRefresh(AblyRest rest) {
        String token = FirebaseInstanceId.getInstance().getToken();
        Log.i(TAG, "Firebase Refreshed token: " + token);
        rest.push.onNewRegistrationToken(this, RegistrationToken.Type.FCM, token);
    }
}
