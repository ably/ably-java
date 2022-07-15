package io.ably.core.test.rest;

import io.ably.core.test.util.IntegrationTestConfigurationCreator;
import io.ably.core.test.util.JavaTestConfigurationCreator;

public class JavaRestChannelBulkPublishTest extends RestChannelBulkPublishTest  {
    @Override
    protected IntegrationTestConfigurationCreator createTestConfigurationCreator() {
        return new JavaTestConfigurationCreator();
    }
}
