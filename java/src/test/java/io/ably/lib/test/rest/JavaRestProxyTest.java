package io.ably.lib.test.rest;

import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.test.util.JavaTestConfigurationCreator;

public class JavaRestProxyTest extends RestProxyTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
