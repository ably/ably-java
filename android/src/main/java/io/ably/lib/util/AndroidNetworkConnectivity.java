package io.ably.lib.util;

import io.ably.lib.types.ErrorInfo;

import java.util.HashSet;
import java.util.Set;

public class AndroidNetworkConnectivity extends NetworkConnectivity {
    public static AndroidNetworkConnectivity getInstance() { return instance; }
    private static final AndroidNetworkConnectivity instance = new AndroidNetworkConnectivity();
}
