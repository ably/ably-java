package io.ably.lib.test.loader;

import java.io.IOException;

public interface ResourceLoader {
    byte[] read(String resourceName) throws IOException;
}
