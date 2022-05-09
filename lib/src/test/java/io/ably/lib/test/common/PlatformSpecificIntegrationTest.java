package io.ably.lib.test.common;

import io.ably.lib.platform.PlatformBase;
import io.ably.lib.push.PushBase;
import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.RealtimeChannelBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.test.loader.ArgumentLoader;
import io.ably.lib.test.loader.ResourceLoader;
import io.ably.lib.test.util.AblyInstanceCreator;
import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.IOException;

/**
 * Base class for all integration tests that should be run on different platforms (e.g. Java, Android).
 * It makes sure that the environment is set up properly for a given platform and provides utility methods
 * for creating platform specific Ably Rest and Realtime instances.
 */
public abstract class PlatformSpecificIntegrationTest {
    private final ResourceLoader resourceLoader;
    private final ArgumentLoader argumentLoader;
    private final AblyInstanceCreator ablyInstanceCreator;
    private static boolean isSetUp = false;
    protected static Setup.TestVars testVars;

    protected PlatformSpecificIntegrationTest() {
        IntegrationTestConfigurationCreator testConfigurationCreator = createTestConfigurationCreator();
        ablyInstanceCreator = testConfigurationCreator.createAblyInstanceCreator();
        resourceLoader = testConfigurationCreator.createResourceLoader();
        argumentLoader = testConfigurationCreator.createArgumentLoader();
    }

    protected abstract IntegrationTestConfigurationCreator createTestConfigurationCreator();

    @Before
    public synchronized void setUpBeforeClass() {
        // JUnit 4 does not allow to annotate a non-static method with @BeforeClass, so as a
        // workaround we use the @Before method but run it only once. Without it, we wouldn't
        // be able to access the non-static dependency creation methods.
        if (!isSetUp) {
            testVars = Setup.getTestVars(ablyInstanceCreator, argumentLoader, resourceLoader);
            isSetUp = true;
        }
    }

    @AfterClass
    public static void tearDownAfterClass() {
        if (isSetUp) {
            Setup.clearTestVars();
            isSetUp = false;
        }
    }

    protected AblyRealtimeBase<PushBase, PlatformBase, RealtimeChannelBase> createAblyRealtime(String key) throws AblyException {
        return ablyInstanceCreator.createAblyRealtime(key);
    }

    protected AblyRealtimeBase<PushBase, PlatformBase, RealtimeChannelBase> createAblyRealtime(ClientOptions options) throws AblyException {
        return ablyInstanceCreator.createAblyRealtime(options);
    }

    protected AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(String key) throws AblyException {
        return ablyInstanceCreator.createAblyRest(key);
    }

    protected AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(ClientOptions options) throws AblyException {
        return ablyInstanceCreator.createAblyRest(options);
    }

    protected AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(ClientOptions options, long mockedTime) throws AblyException {
        return ablyInstanceCreator.createAblyRest(options, mockedTime);
    }

    protected Object loadJson(String resourceName, Class<? extends Object> expectedType) throws IOException {
        return Setup.loadJson(resourceName, expectedType, resourceLoader);
    }
}
