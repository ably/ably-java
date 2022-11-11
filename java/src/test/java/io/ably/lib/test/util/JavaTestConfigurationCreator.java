package io.ably.lib.test.util;

import io.ably.lib.test.loader.ArgumentLoader;
import io.ably.lib.test.loader.JavaArgumentLoader;
import io.ably.lib.test.loader.JavaResourceLoader;
import io.ably.lib.test.loader.ResourceLoader;

public class JavaTestConfigurationCreator implements IntegrationTestConfigurationCreator {
    @Override
    public AblyInstanceCreator createAblyInstanceCreator() {
        return new JavaAblyInstanceCreator();
    }

    @Override
    public ArgumentLoader createArgumentLoader() {
        return new JavaArgumentLoader();
    }

    @Override
    public ResourceLoader createResourceLoader() {
        return new JavaResourceLoader();
    }
}
