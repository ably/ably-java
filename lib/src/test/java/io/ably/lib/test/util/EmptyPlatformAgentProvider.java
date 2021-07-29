package io.ably.lib.test.util;

import io.ably.lib.util.PlatformAgentProvider;

public class EmptyPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        return null;
    }
}
