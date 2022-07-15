package io.ably.core.test.util;

import io.ably.core.test.loader.ArgumentLoader;
import io.ably.core.test.loader.ResourceLoader;

public interface IntegrationTestConfigurationCreator {
    AblyInstanceCreator createAblyInstanceCreator();

    ArgumentLoader createArgumentLoader();

    ResourceLoader createResourceLoader();
}
