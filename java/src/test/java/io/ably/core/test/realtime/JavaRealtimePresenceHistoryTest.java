package io.ably.core.test.realtime;

import io.ably.core.test.util.IntegrationTestConfigurationCreator;
import io.ably.core.test.util.JavaTestConfigurationCreator;
import org.junit.Ignore;

@Ignore("FIXME: fix ably exception")
public class JavaRealtimePresenceHistoryTest extends RealtimePresenceHistoryTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
