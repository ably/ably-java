package io.ably.lib.util;

import java.util.Map;
import java.util.regex.Pattern;

public class AblyAgentValidator {
    /**
     * Agent name validation regex.
     * Allow only lowercase letters, digits and characters from the set [ !#$%&'*+-.^_`|~ ].
     */
    private static final String AGENT_NAME_REGEX = "^[a-z0-9!#$%&'*+\\-.^_`|~]+$";
    private static final Pattern agentNamePattern = Pattern.compile(AGENT_NAME_REGEX);

    /**
     * Agent version validation regex.
     * Suggested Semantic Versioning regex from the official site.
     * https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
     */
    private static final String SEM_VER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
    private static final Pattern agentVersionPattern = Pattern.compile(SEM_VER_REGEX);

    /**
     * Checks if provided Ably agent values are valid.
     *
     * @return true if both agentName and agentVersion (if it's present) are valid values, false otherwise.
     */
    public static boolean isValid(String agentName, String agentVersion) {
        return agentNamePattern.matcher(agentName).matches()
            && (agentVersion == null || agentVersionPattern.matcher(agentVersion).matches());
    }

    /**
     * Checks if all provided Ably agent values are valid.
     *
     * @return true if all agents are valid, false otherwise.
     */
    public static boolean areAllValid(Map<String, String> agents) {
        for (String agentName : agents.keySet()) {
            if (!isValid(agentName, agents.get(agentName))) {
                return false;
            }
        }
        return true;
    }
}
