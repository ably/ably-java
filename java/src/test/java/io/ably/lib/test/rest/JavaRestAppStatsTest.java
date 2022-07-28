package io.ably.lib.test.rest;

import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.test.util.JavaTestConfigurationCreator;

@SuppressWarnings("deprecation")
public class JavaRestAppStatsTest extends RestAppStatsTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
