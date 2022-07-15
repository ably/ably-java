package io.ably.core.test.realtime;

import io.ably.core.test.util.IntegrationTestConfigurationCreator;
import io.ably.core.test.util.JavaTestConfigurationCreator;

public class JavaRealtimeRecoverTest extends RealtimeRecoverTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
