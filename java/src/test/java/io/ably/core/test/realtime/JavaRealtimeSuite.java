package io.ably.core.test.realtime;

import io.ably.core.test.common.Setup;
import io.ably.core.test.loader.ArgumentLoader;
import io.ably.core.test.loader.JavaArgumentLoader;
import io.ably.core.test.loader.JavaResourceLoader;
import io.ably.core.test.loader.ResourceLoader;
import io.ably.core.test.util.AblyInstanceCreator;
import io.ably.core.test.util.JavaAblyInstanceCreator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    JavaConnectionManagerTest.class,
    JavaRealtimeHttpHeaderTest.class,
    JavaRealtimeAuthTest.class,
    JavaRealtimeJWTTest.class,
    JavaRealtimeReauthTest.class,
    JavaRealtimeInitTest.class,
    JavaRealtimeConnectTest.class,
    JavaRealtimeConnectFailTest.class,
    JavaRealtimeChannelTest.class,
    JavaRealtimeDeltaDecoderTest.class,
    JavaRealtimePresenceTest.class,
    JavaRealtimeMessageTest.class,
    JavaRealtimeResumeTest.class,
    JavaRealtimeRecoverTest.class,
    JavaRealtimeCryptoTest.class,
    JavaRealtimeChannelHistoryTest.class,
    JavaRealtimePresenceHistoryTest.class
})
public class JavaRealtimeSuite {
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
        Result result = JUnitCore.runClasses(JavaRealtimeSuite.class);
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }
    }
}
