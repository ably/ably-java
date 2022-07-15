package io.ably.core.test.util;

import io.ably.core.util.PlatformAgentProvider;

public class EmptyPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        return null;
    }
}
