package io.ably.lib.test.util;

import io.ably.lib.test.loader.AndroidArgumentLoader;
import io.ably.lib.test.loader.AndroidResourceLoader;
import io.ably.lib.test.loader.ArgumentLoader;
import io.ably.lib.test.loader.ResourceLoader;

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
