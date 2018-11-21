package io.ably.lib.platform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import io.ably.lib.transport.NetworkConnectivity;
import io.ably.lib.types.ErrorInfo;

public class AndroidNetworkConnectivity extends NetworkConnectivity {

	AndroidNetworkConnectivity() {}

	void activate(Context context) {
		networkStateReceiver = new NetworkStateReceiver();
		context.registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	void deactivate(Context context) {
		context.unregisterReceiver(networkStateReceiver);
		networkStateReceiver = null;
	}

	private class NetworkStateReceiver extends BroadcastReceiver {
		public NetworkStateReceiver() {
		}

		public void onReceive(Context context, Intent intent) {
			if(intent == null || intent.getExtras() == null) {
				return;
			}

			ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo ni = manager.getActiveNetworkInfo();

			if(ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
				notifyNetworkAvailable();
			} else if(intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,Boolean.FALSE)) {
				notifyNetworkUnavailable(new ErrorInfo("No network connection available", 503, 80003));
			}
		}
	}

	private NetworkStateReceiver networkStateReceiver;
}
