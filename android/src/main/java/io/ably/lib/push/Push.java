package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.platform.PlatformBase;
import io.ably.lib.platform.AndroidPlatform;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Log;

import java.util.Arrays;

public class Push extends PushBase {

    public Push(AblyBase<PushBase, PlatformBase, RestChannelBase> rest) {
        super(rest);
    }

    public void activate() throws AblyException {
        activate(false);
    }

    public void activate(boolean useCustomRegistrar) throws AblyException {
        Log.v(TAG, "activate(): useCustomRegistrar=" + useCustomRegistrar);
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        getStateMachine().handleEvent(ActivationStateMachine.CalledActivate.useCustomRegistrar(useCustomRegistrar, prefs));
    }

    public void deactivate() throws AblyException {
        deactivate(false);
    }

    public void deactivate(boolean useCustomRegistrar) throws AblyException {
        Log.v(TAG, "deactivate(): useCustomRegistrar=" + useCustomRegistrar);
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        getStateMachine().handleEvent(ActivationStateMachine.CalledDeactivate.useCustomRegistrar(useCustomRegistrar, prefs));
    }

    synchronized ActivationStateMachine getStateMachine() throws AblyException {
        return getActivationContext().getActivationStateMachine();
    }

    public void tryRequestRegistrationToken() {
        try {
            if (getLocalDevice().isRegistered()) {
                Log.v(TAG, "Local device is registered.");
                getStateMachine().getRegistrationToken();
            } else {
                Log.v(TAG, "Local device is not registered.");
            }
        } catch (AblyException e) {
            Log.e(TAG, "couldn't validate existing push recipient device details", e);
        }
    }

    Context getApplicationContext() throws AblyException {
        Context applicationContext = ((AndroidPlatform) rest.platform).getApplicationContext();
        if(applicationContext == null) {
            Log.e(TAG, "getApplicationContext(): Unable to get application context; not set");
            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get application context; not set", 40000, 400));
        }
        return applicationContext;
    }

    protected ActivationContext activationContext = null;

    public ActivationContext getActivationContext() throws AblyException {
        if (activationContext == null) {
            Log.v(TAG, "getActivationContext(): creating a new context and returning that");
            Context applicationContext = getApplicationContext();
            activationContext = ActivationContext.getActivationContext(applicationContext, rest);
        } else {
            Log.v(TAG, "getActivationContext(): returning existing content");
        }
        return activationContext;
    }

    public LocalDevice getLocalDevice() throws AblyException {
        return getActivationContext().getLocalDevice();
    }

    @Override
    Param[] pushRequestHeaders(boolean forLocalDevice) {
        Param[] headers = super.pushRequestHeaders(forLocalDevice);
        if(forLocalDevice) {
            try {
                final Param[] deviceIdentityHeaders = getLocalDevice().deviceIdentityHeaders();
                if(deviceIdentityHeaders != null) {
                    Log.v(TAG, "pushRequestHeaders(): deviceIdentityHeaders=" + Arrays.toString(deviceIdentityHeaders));
                    headers = HttpUtils.mergeHeaders(headers, deviceIdentityHeaders);
                } else {
                    Log.w(TAG, "pushRequestHeaders(): Local device returned null device identity headers!");
                }
            } catch (AblyException e) {
                Log.w(TAG, "pushRequestHeaders(): Failed to get device identity headers. forLocalDevice=" + forLocalDevice, e);
            }
        }
        return headers;
    }

    @Override
    Param[] pushRequestHeaders(String deviceId) {
        boolean forLocalDevice = false;
        try {
            forLocalDevice = deviceId != null && deviceId.equals(getLocalDevice().id);
        } catch (AblyException e) {
            Log.w(TAG, "pushRequestHeaders(): deviceId=" + deviceId, e);
        }
        return pushRequestHeaders(forLocalDevice);
    }

    private static final String TAG = Push.class.getName();
}
