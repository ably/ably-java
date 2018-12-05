package io.ably.lib.types;

public class RegistrationToken {
	public Type type;
	public String token;

	public RegistrationToken(Type type, String token) {
		this.type = type;
		this.token = token;
	}

	public enum Type {
		GCM("gcm"),
		FCM("fcm"),
		UNKNOWN("unknown");

		public String code;
		Type(String code) {
			this.code = code;
		}

		public int toInt() {
			Type[] values = Type.values();
			for (int i = 0; i < values.length; i++) {
				if (this == values[i]) {
					return i;
				}
			}
			return -1;
		}

		public static Type fromInt(int i) {
			Type[] values = Type.values();
			if (i < 0 || i >= values.length) {
				return null;
			}
			return values[i];
		}

		public static Type fromCode(String code) {
			Type[] values = Type.values();
			for (Type t : values) {
				if (t.code.equals(code)) {
					return t;
				}
			}
			return null;
		}
	}
}