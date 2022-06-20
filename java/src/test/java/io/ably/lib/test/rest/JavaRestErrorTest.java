package io.ably.lib.test.rest;

import io.ably.lib.test.util.IntegrationTestConfigurationCreator;
import io.ably.lib.test.util.JavaTestConfigurationCreator;

public class JavaRestErrorTest extends RestErrorTest {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
