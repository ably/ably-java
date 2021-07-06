package io.ably.lib.push;

import android.content.Context;
import android.test.AndroidTestCase;
import io.ably.lib.types.RegistrationToken;
import junit.extensions.TestSetup;
import junit.framework.TestSuite;
import org.junit.BeforeClass;

import java.lang.reflect.Field;
import java.util.HashMap;

public class LocalDeviceStorageTest extends AndroidTestCase {
    private Context context;
    private ActivationContext activationContext;


    private HashMap<String, Object> hashMap = new HashMap<>();

    private Storage inMemoryStorage = new Storage() {
        @Override
        public void putString(String key, String value) {
            hashMap.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            hashMap.put(key, value);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = hashMap.get(key);
            return value != null ? (String) value : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = hashMap.get(key);
            return value != null ? (int) value : defaultValue;
        }

        @Override
        public void reset(Field[] fields) {
            hashMap = new HashMap<>();
        }
    };

    @BeforeClass
    public void setUp() {
        context = getContext();
        activationContext = new ActivationContext(context.getApplicationContext());
    }

    public static junit.framework.Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new TestSetup(new TestSuite(LocalDeviceStorageTest.class)) {});
        return suite;
    }

    public void test_shared_preferences_storage_used_by_default() {
        LocalDevice localDevice = new LocalDevice(activationContext, null);
        /* initialize properties in storage */
        localDevice.create();

        /* verify custom storage is not used */
        assertTrue(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
    }

    public void test_shared_preferences_storage_works_correctly() {
        LocalDevice localDevice = new LocalDevice(activationContext, null);

        RegistrationToken registrationToken= new RegistrationToken(RegistrationToken.Type.FCM, "ABLY");
        /* initialize properties in storage */
        localDevice.create();
        localDevice.setAndPersistRegistrationToken(registrationToken);

        /* verify custom storage is not used */
        assertTrue(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
        assertTrue(localDevice.isCreated());
        assertEquals("FCM", localDevice.getRegistrationToken().type.name());
        assertEquals("ABLY", localDevice.getRegistrationToken().token);

        /* reset all properties */
        localDevice.reset();

        /* properties were cleared */
        assertNull(localDevice.id);
        assertNull(localDevice.deviceSecret);
        assertNull(localDevice.getRegistrationToken());
    }

    public void test_custom_storage_used_if_provided() {
        LocalDevice localDevice = new LocalDevice(activationContext, inMemoryStorage);
        /* initialize properties in storage */
        localDevice.create();

        /* verify in memory storage is used */
        assertFalse(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);

        String deviceId = localDevice.id;
        String deviceSecret = localDevice.deviceSecret;

        /* values are the same */
        assertEquals(deviceId, hashMap.get("ABLY_DEVICE_ID"));
        assertEquals(deviceSecret, hashMap.get("ABLY_DEVICE_SECRET"));
    }

    public void test_custom_storage_works_correctly() {
        LocalDevice localDevice = new LocalDevice(activationContext, inMemoryStorage);

        RegistrationToken registrationToken= new RegistrationToken(RegistrationToken.Type.FCM, "ABLY");
        /* initialize properties in storage */
        localDevice.create();
        localDevice.setAndPersistRegistrationToken(registrationToken);

        /* verify custom storage is used */
        assertFalse(hashMap.isEmpty());

        /* load properties */
        assertNotNull(localDevice.id);
        assertNotNull(localDevice.deviceSecret);
        assertTrue(localDevice.isCreated());
        assertEquals("FCM", localDevice.getRegistrationToken().type.name());
        assertEquals("ABLY", localDevice.getRegistrationToken().token);

        /* reset all properties */
        localDevice.reset();
        /* verify custom storage was cleared out */
        assertTrue(hashMap.isEmpty());

        /* properties were cleared */
        assertNull(localDevice.id);
        assertNull(localDevice.deviceSecret);
        assertNull(localDevice.getRegistrationToken());
    }
}
