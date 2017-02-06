package io.ably.lib.gcm;

import android.util.Log;
import android.app.IntentService;
import android.content.Intent;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Push;

public abstract class AblyRegistrationIntentService extends IntentService {
    protected static final String TAG = "AblyRegistrationIntent";

    public AblyRegistrationIntentService() {
        super(TAG);
    }

    protected void onHandleIntent(Intent intent, String senderId, AblyRest rest) {
        try {
            InstanceID instanceID = InstanceID.getInstance(this);
            String token = instanceID.getToken(senderId,
                    GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            Log.i(TAG, "GCM Registration Token: " + token);
            rest.push.onNewRegistrationToken(this, Push.RegistrationTokenType.GCM, token);
        } catch (Exception e) {
            Log.d(TAG, "Failed to complete token refresh", e);
            // TODO: Retry with exponential backoff.
        }
    }
}
