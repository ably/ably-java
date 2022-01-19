package io.ably.lib.types;

import java.util.Locale;

public class RegistrationToken {
    /**
     * The type of registration token represented by the value in {@link #token}.
     * @deprecated As of version 1.2.11. Only FCM is active now as GCM has been deactivated by Google.
     */
    @Deprecated
    public Type type;

    /**
     * The token value.
     */
    public String token;

    /**
     * @deprecated As of version 1.2.11.
     * Use {@link #RegistrationToken(String)} instead, to create an FCM token.
     * Only FCM is active now as GCM has been deactivated by Google.
     */
    @Deprecated
    public RegistrationToken(Type type, String token) {
        this.type = type;
        this.token = token;
    }

    /**
     * Initializes a newly created RegistrationToken object representing an FCM registration token.
     * @param token The FCM token value.
     */
    public RegistrationToken(String token) {
        this.type = Type.FCM;
        this.token = token;
    }

    /**
     * The Ably transportType used to categorise an FCM registration token.
     */
    public static final String TOKEN_TYPE_STRING_VALUE_FCM = "fcm";

    /**
     * The value persisted to shared preferences on Android to categorise an FCM registration token.
     */
    public static final int TOKEN_TYPE_ORDINAL_VALUE_FCM = 1;

    /**
     * @deprecated As of version 1.2.11. GCM has been deactivated by Google, use FCM instead.
     */
    @Deprecated
    public enum Type {
        GCM,
        FCM;

        public static Type fromOrdinal(int i) {
            try {
                return Type.values()[i];
            } catch(Throwable t) {
                return null;
            }
        }

        public static Type fromName(String name) {
            try {
                return Type.valueOf(name.toUpperCase(Locale.ROOT));
            } catch(Throwable t) {
                return null;
            }
        }

        public String toName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String toString() {
        return "RegistrationToken{" +
            "type=" + type +
            ", token='" + token + '\'' +
            '}';
    }
}
