package io.ably.core.test.realtime;

import io.ably.core.test.util.IntegrationTestConfigurationCreator;
import io.ably.core.test.util.JavaTestConfigurationCreator;

public class JavaRealtimeChannelTest extends RealtimeChannelTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
