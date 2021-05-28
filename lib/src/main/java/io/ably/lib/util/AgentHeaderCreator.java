package io.ably.lib.util;

import android.os.Build;
import io.ably.lib.BuildConfig;
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
    private static final String ANDROID_LIBRARY_NAME = "android";

    public static String create(Map<String, String> additionalAgents) {
        StringBuilder agentStringBuilder = new StringBuilder();
        agentStringBuilder.append(Defaults.ABLY_AGENT_VERSION);
        if (!additionalAgents.isEmpty()) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAdditionalAgentEntries(additionalAgents));
        }
        if (BuildConfig.LIBRARY_NAME.equals(ANDROID_LIBRARY_NAME)) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAndroidAgent());
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

    private static String getAndroidAgent() {
        return "android" + AGENT_DIVIDER + Build.VERSION.SDK_INT;
    }
}
