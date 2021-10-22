package io.ably.lib.types;

import java.util.Locale;

public class RegistrationToken {
    public Type type;
    public String token;

    /**
     * Deprecated: use RegistrationToken(String token) instead
     */
    @Deprecated
    public RegistrationToken(Type type, String token) {
        this.type = type;
        this.token = token;
    }

    public RegistrationToken(String token) {
        this.type = Type.FCM;
        this.token = token;
    }

    public enum Type {
        @Deprecated GCM,
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
