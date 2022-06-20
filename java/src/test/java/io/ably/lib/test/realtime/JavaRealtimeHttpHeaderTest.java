package io.ably.lib.test.realtime;

import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.test.util.JavaTestConfigurationCreator;

public class JavaRealtimeHttpHeaderTest extends RealtimeHttpHeaderTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}

