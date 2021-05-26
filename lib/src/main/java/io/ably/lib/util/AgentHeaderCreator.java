package io.ably.lib.util;

import android.os.Build;
import io.ably.lib.BuildConfig;
import io.ably.lib.transport.Defaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        if (!additionalAgents.isEmpty()) {
            agentStringBuilder.append(getAdditionalAgentEntries(additionalAgents));
        }
        agentStringBuilder.append(Defaults.ABLY_AGENT_VERSION);
        if (BuildConfig.LIBRARY_NAME.equals(ANDROID_LIBRARY_NAME)) {
            agentStringBuilder.append(AGENT_ENTRY_SEPARATOR);
            agentStringBuilder.append(getAndroidAgent());
        }
        return agentStringBuilder.toString();
    }

    private static String getAdditionalAgentEntries(Map<String, String> additionalAgents) {
        StringBuilder additionalAgentsBuilder = new StringBuilder();
        for (String additionalAgentName : getSortedAgentNames(additionalAgents)) {
            String additionalAgentVersion = additionalAgents.get(additionalAgentName);
            additionalAgentsBuilder.append(additionalAgentName);
            if (additionalAgentVersion != null) {
                additionalAgentsBuilder.append(AGENT_DIVIDER);
                additionalAgentsBuilder.append(additionalAgentVersion);
            }
            additionalAgentsBuilder.append(AGENT_ENTRY_SEPARATOR);
        }
        return additionalAgentsBuilder.toString();
    }


    private static List<String> getSortedAgentNames(Map<String, String> agents) {
        List<String> agentNames = new ArrayList<>(agents.keySet());
        Collections.sort(agentNames);
        return agentNames;
    }

    private static String getAndroidAgent() {
        return "android" + AGENT_DIVIDER + Build.VERSION.SDK_INT;
    }
}
