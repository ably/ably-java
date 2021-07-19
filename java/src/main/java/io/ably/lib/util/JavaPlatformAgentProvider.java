package io.ably.lib.util;

public class JavaPlatformAgentProvider implements PlatformAgentProvider {
    @Override
    public String createPlatformAgent() {
        return "jre" + AgentHeaderCreator.AGENT_DIVIDER + System.getProperty("java.version");
    }
}
