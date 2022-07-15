package io.ably.core.test.util;

import io.ably.core.test.loader.ArgumentLoader;
import io.ably.core.test.loader.JavaArgumentLoader;
import io.ably.core.test.loader.JavaResourceLoader;
import io.ably.core.test.loader.ResourceLoader;

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
