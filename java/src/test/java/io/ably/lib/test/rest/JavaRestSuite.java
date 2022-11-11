package io.ably.lib.test.rest;

import io.ably.lib.test.common.Setup;
import io.ably.lib.test.loader.ArgumentLoader;
import io.ably.lib.test.loader.JavaArgumentLoader;
import io.ably.lib.test.loader.JavaResourceLoader;
import io.ably.lib.test.loader.ResourceLoader;
import io.ably.lib.test.util.AblyInstanceCreator;
import io.ably.lib.test.util.JavaAblyInstanceCreator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    JavaHttpTest.class,
    JavaHttpHeaderTest.class,
    JavaRestRequestTest.class,
    JavaRestAppStatsTest.class,
    JavaRestInitTest.class,
    JavaRestTimeTest.class,
    JavaRestAuthTest.class,
    JavaRestAuthAttributeTest.class,
    JavaRestTokenTest.class,
    JavaRestJWTTest.class,
    JavaRestCapabilityTest.class,
    JavaRestChannelTest.class,
    JavaRestChannelHistoryTest.class,
    JavaRestChannelPublishTest.class,
    JavaRestChannelBulkPublishTest.class,
    JavaRestCryptoTest.class,
    JavaRestPresenceTest.class,
    JavaRestProxyTest.class,
    JavaRestErrorTest.class,
    JavaRestPushTest.class,
    JavaRestClientTest.class
})
public class JavaRestSuite {
    private static final AblyInstanceCreator ablyInstanceCreator = new JavaAblyInstanceCreator();
    private static final ArgumentLoader argumentLoader = new JavaArgumentLoader();
    private static final ResourceLoader resourceLoader = new JavaResourceLoader();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Setup.getTestVars(ablyInstanceCreator, argumentLoader, resourceLoader);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        Setup.clearTestVars();
    }

    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(JavaRestSuite.class);
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }
    }
}
