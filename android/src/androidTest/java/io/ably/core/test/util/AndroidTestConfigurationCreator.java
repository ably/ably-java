package io.ably.core.test.util;

import io.ably.core.test.loader.AndroidArgumentLoader;
import io.ably.core.test.loader.AndroidResourceLoader;
import io.ably.core.test.loader.ArgumentLoader;
import io.ably.core.test.loader.ResourceLoader;

public class AndroidTestConfigurationCreator implements IntegrationTestConfigurationCreator {
    @Override
    public AblyInstanceCreator createAblyInstanceCreator() {
        return new AndroidAblyInstanceCreator();
    }

    @Override
    public ArgumentLoader createArgumentLoader() {
        return new AndroidArgumentLoader();
    }

    @Override
    public ResourceLoader createResourceLoader() {
        return new AndroidResourceLoader();
    }
}
