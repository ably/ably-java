package io.ably.lib.types;

import androidx.annotation.NonNull;

public class RegistrationToken {
    /**
     * The token value.
     */
    public String token;

    /**
     * Initializes a newly created RegistrationToken object representing an FCM registration token.
     *
     * @param token The FCM token value.
     */
    public RegistrationToken(String token) {
        this.token = token;
    }

    /**
     * The Ably transportType used to categorise an FCM registration token.
     */
    public static final String TOKEN_TYPE_FCM = "fcm";

    @NonNull
    @Override
    public String toString() {
        return "RegistrationToken{" +
            "type=" + TOKEN_TYPE_FCM +
            ", token='" + token + '\'' +
            '}';
    }
}
