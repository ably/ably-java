package io.ably.lib.test.other;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import io.ably.lib.GlobalConstants;
import io.ably.lib.http.HttpUtils;

public class HttpUtilsTest {

    private static String headerNoPlatform = null;
    private static String headerAndroidPlatform = null;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        headerNoPlatform = GlobalConstants.LIB_TYPE + "-" + GlobalConstants.LIB_VERSION;
        headerAndroidPlatform = GlobalConstants.LIB_TYPE + ".android-" + GlobalConstants.LIB_VERSION;
    }

    @Test
    public void testHeaderXAblyLyb() {
        assertTrue(HttpUtils.getHeaderXAblyLib(null).equals(headerNoPlatform));
        assertTrue(HttpUtils.getHeaderXAblyLib("android").equals(headerAndroidPlatform));
    }

}
