package io.ably.lib.test.realtime;

import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.test.util.JavaTestConfigurationCreator;
import org.junit.Ignore;

@Ignore("FIXME: fix ably exception")
public class JavaRealtimePresenceHistoryTest extends RealtimePresenceHistoryTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
