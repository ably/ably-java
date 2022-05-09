package io.ably.lib.test.util;

import io.ably.lib.test.loader.ArgumentLoader;
import io.ably.lib.test.loader.ResourceLoader;

public interface IntegrationTestConfigurationCreator {
    AblyInstanceCreator createAblyInstanceCreator();

    ArgumentLoader createArgumentLoader();

    ResourceLoader createResourceLoader();
}
