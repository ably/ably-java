package io.ably.lib.util;

import io.ably.lib.rest.DerivedClientOptions;
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
    public static final String AGENT_DIVIDER = "/";

    public static String create(Map<String, String> additionalAgents, PlatformAgentProvider platformAgentProvider, DerivedClientOptions derivedOptions) {
        StringBuilder agentStringBuilder = new StringBuilder();
        agentStringBuilder.append(Defaults.ABLY_AGENT_VERSION);
        if (additionalAgents != null && !additionalAgents.isEmpty()) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAdditionalAgentEntries(additionalAgents));
        }
        String platformAgent = platformAgentProvider.createPlatformAgent();
        if (platformAgent != null) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(platformAgent);
        }

        if (derivedOptions != null) {
            derivedOptions.getAgents().entrySet().forEach(entry -> {
                agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
                agentStringBuilder.append(entry.getKey());
                agentStringBuilder.append(AGENT_DIVIDER);
                agentStringBuilder.append(entry.getValue());
            });
        }

        return agentStringBuilder.toString();
    }

    public static String create(Map<String, String> additionalAgents, PlatformAgentProvider platformAgentProvider) {
        return create(additionalAgents, platformAgentProvider, null);
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
}
