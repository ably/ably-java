package io.ably.lib.util;

import io.ably.lib.transport.Defaults;

import java.util.Map;

public class AgentHeaderCreator {
    /**
     * Separates agent entries from each other.
     */
    private static final String AGENT_ENTRY_SEPARATOR = " ";

    /**
     * Separates agent name from agent version.
     */
    private static final String AGENT_DIVIDER = "/";

    /**
     * Optional platform agent, e.g. "android/24"
     */
    private static String platformAgent = null;

    public static String create(Map<String, String> additionalAgents) {
        StringBuilder agentStringBuilder = new StringBuilder();
        agentStringBuilder.append(Defaults.ABLY_AGENT_VERSION);
        if (!additionalAgents.isEmpty()) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAdditionalAgentEntries(additionalAgents));
        }
        if (platformAgent != null) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(platformAgent);
        }
        return agentStringBuilder.toString();
    }

    private static String getAdditionalAgentEntries(Map<String, String> additionalAgents) {
        StringBuilder additionalAgentsBuilder = new StringBuilder();
        for (String additionalAgentName : additionalAgents.keySet()) {
            String additionalAgentVersion = additionalAgents.get(additionalAgentName);
            additionalAgentsBuilder.append(additionalAgentName);
            if (additionalAgentVersion != null) {
                additionalAgentsBuilder.append(AGENT_DIVIDER);
                additionalAgentsBuilder.append(additionalAgentVersion);
            }
            additionalAgentsBuilder.append(AGENT_ENTRY_SEPARATOR);
        }
        return additionalAgentsBuilder.toString().trim();
    }

    public static void setAndroidPlatformAgent(int platformVersion) {
        platformAgent = "android" + AGENT_DIVIDER + platformVersion;
    }

    /**
     * Added to clear AgentHeaderCreator state for unit tests.
     */
    public static void clearPlatformAgent() {
        platformAgent = null;
    }
}
