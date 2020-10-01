package io.ably.lib.types;

public class RegistrationToken {
    public Type type;
    public String token;

    public RegistrationToken(Type type, String token) {
        this.type = type;
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
                return Type.valueOf(name.toUpperCase());
            } catch(Throwable t) {
                return null;
            }
        }

        public String toName() {
            return name().toLowerCase();
        }
    }
}
