package io.ably.lib.util;

import android.os.Build;
import io.ably.lib.transport.Defaults;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AgentHeaderCreatorTest {
    private final static String PREDEFINED_AGENTS = Defaults.ABLY_AGENT_VERSION + " android/" + Build.VERSION.SDK_INT;

    @Test
    public void should_create_default_header_if_there_are_no_additional_agents() {
        // given
        Map<String, String> agents = new HashMap<>();

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents);

        // then
        assertMatchingAgentHeaders(PREDEFINED_AGENTS, agentHeaderValue);
    }

    @Test
    public void should_create_header_with_appended_agents_if_they_are_provided() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("library", "1.0.1");
        agents.put("other", "0.8.2");

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents);

        // then
        assertMatchingAgentHeaders("library/1.0.1 other/0.8.2 " + PREDEFINED_AGENTS, agentHeaderValue);
    }

    @Test
    public void should_create_header_with_appended_agents_without_versions() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("library", "1.0.1");
        agents.put("no-version", null);

        // when
        String agentHeaderValue = AgentHeaderCreator.create(agents);

        // then
        assertMatchingAgentHeaders("library/1.0.1 no-version " + PREDEFINED_AGENTS, agentHeaderValue);
    }

    private void assertMatchingAgentHeaders(String expectedAgentHeader, String actualAgentHeader) {
        assertPredefinedAgentsAreAtTheEnd(actualAgentHeader);
        assertAllExpectedAgentsArePresentInActualAgents(expectedAgentHeader, actualAgentHeader);
    }

    private void assertPredefinedAgentsAreAtTheEnd(String actualAgentHeader) {
        assertTrue(
            actualAgentHeader + " does not end with the library predefined agents",
            actualAgentHeader.endsWith(PREDEFINED_AGENTS)
        );
    }

    private void assertAllExpectedAgentsArePresentInActualAgents(String expectedAgentHeader, String actualAgentHeader) {
        List<String> actualAgents = Arrays.asList(actualAgentHeader.split(" "));
        String[] expectedAgents = expectedAgentHeader.split(" ");
        for (String expectedAgent : expectedAgents) {
            assertTrue(
                actualAgentHeader + " does not include " + expectedAgent,
                actualAgents.contains(expectedAgent)
            );
        }
    }
}
