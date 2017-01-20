package io.ably.lib.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.util.Properties;

import io.ably.lib.util.Log;
import io.ably.lib.util.Platform.PlatformEvent;

public class AndroidPlatform extends Platform {
	public static final String ANDROID_SDK_VERSION = "android-sdk-version";

	public AndroidPlatform() {
		name = "android";
		properties.setProperty(ANDROID_SDK_VERSION, String.valueOf(Build.VERSION.SDK_INT));

		try {
			setContext((Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null, (Object[]) null));
		} catch(Throwable t) {
			Log.e(TAG, "Unable to access application context", t);
		}
	}

	public void setContext(Context ctx) {
		this.context = ctx;
		connectivityReceiver = new ConnectivityReceiver();
	}

	private class ConnectivityReceiver extends BroadcastReceiver {
		ConnectivityReceiver() {
			try {
				/* get current network state */
				ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				isConnected = getConnectionState(activeNetwork);

				/* register for change events */
				IntentFilter filter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
				context.registerReceiver(this, filter);
			} catch(Throwable t) {
				Log.e(TAG, "Unable to access application context", t);
			}
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			NetworkInfo networkInfo = intent.getParcelableExtra("networkInfo");
			boolean isConnectedNow = getConnectionState(networkInfo);
			if(isConnected != isConnectedNow) {
				isConnected = isConnectedNow;
				events.emit(isConnected ? PlatformEvent.NETWORK_UP : PlatformEvent.NETWORK_DOWN);
			}
		}

		private boolean getConnectionState(NetworkInfo activeNetwork) {
			return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
		}

		private boolean isConnected;
	}

	private Context context;
	private ConnectivityReceiver connectivityReceiver;
	private static final String TAG = AndroidPlatform.class.getName();
}
