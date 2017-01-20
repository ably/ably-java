package io.ably.lib.util;

import java.util.Properties;

public abstract class Platform {
	public enum PlatformEvent {
		NETWORK_UP,
		NETWORK_DOWN
	}

	public static interface PlatformEventListener {
		public void onPlatformEvent(PlatformEvent event, Object... args);
	}

	public static class PlatformEventEmitter extends EventEmitter<PlatformEvent, PlatformEventListener> {
		@Override
		protected void apply(PlatformEventListener listener, PlatformEvent event, Object... args) {
			listener.onPlatformEvent(event, args);
		}
	}

	/*****************
	 * public API
	 *****************/

	public static String getName() {
		return instance.name;
	}

	public static PlatformEventEmitter getEvents() {
		return instance.events;
	}

	public static String getStringProperty(String key) {
		return getProperties().getProperty(key);
	}

	public static String getStringProperty(String key, String defaultValue) {
		return getProperties().getProperty(key, defaultValue);
	}

	public static int getIntProperty(String key) {
		return Integer.parseInt(getProperties().getProperty(key));
	}

	public static int getIntProperty(String key, int defaultValue) {
		String strValue = getProperties().getProperty(key);
		return (strValue != null) ? Integer.parseInt(strValue) : defaultValue;
	}

	/*****************
	 * internal
	 *****************/

	private static Properties getProperties() {
		return instance.properties;
	}
	protected static Platform instance;
	protected String name;
	protected final Properties properties = new Properties();
	protected final PlatformEventEmitter events = new PlatformEventEmitter();

	static {
		try {
			try {
				instance = (Platform)Class.forName("io.ably.lib.util.AndroidPlatform").newInstance();
			} catch(ClassNotFoundException cnfe) {
				instance = (Platform)Class.forName("io.ably.lib.util.JavaPlatform").newInstance();
			}
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {}
	}

}
