package io.ably.core.test.realtime;

import io.ably.core.test.util.IntegrationTestConfigurationCreator;
import io.ably.core.test.util.JavaTestConfigurationCreator;

public class JavaRealtimeAuthTest extends RealtimeAuthTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
