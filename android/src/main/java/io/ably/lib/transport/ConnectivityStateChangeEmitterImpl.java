package io.ably.lib.transport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;

/**
 * Created by gokhanbarisaker on 3/31/16.
 */
public class ConnectivityStateChangeEmitterImpl extends ConnectivityStateChangeEmitter {

	public ConnectivityStateChangeEmitterImpl() throws AblyException {
		try {
			Context context = getApplicationContextUsingReflection();
			IntentFilter filter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");;
			context.registerReceiver(new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					NetworkInfo networkInfo = intent.getParcelableExtra("networkInfo");
					emit(networkInfo.isConnected());
				}
			}, filter);
		} catch (Exception e) {
			AblyException ablyException = AblyException.fromErrorInfo(new ErrorInfo("Unable to access application context", 000));

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				ablyException.addSuppressed(e);
			}

			throw ablyException;
		}
	}

	@Override
	protected void apply(ConnectivityStateChangeListener listener, Boolean connected, Object... args) {
		listener.onConnectivityStateChanged(connected);
	}

	private Context getApplicationContextUsingReflection() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null, (Object[]) null);
	}
}
