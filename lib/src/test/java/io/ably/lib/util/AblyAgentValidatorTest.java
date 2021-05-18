package io.ably.lib.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AblyAgentValidatorTest {
    @Test
    public void should_return_false_if_agent_name_is_invalid() {
        // given
        String agentName = "invalid/name";
        String agentVersion = "1.0.1";

        // when
        boolean isAgentValid = AblyAgentValidator.isValid(agentName, agentVersion);

        // then
        assertFalse(isAgentValid);
    }

    @Test
    public void should_return_false_if_agent_version_is_invalid() {
        // given
        String agentName = "valid-name";
        String agentVersion = "1v.23.ax";

        // when
        boolean isAgentValid = AblyAgentValidator.isValid(agentName, agentVersion);

        // then
        assertFalse(isAgentValid);
    }

    @Test
    public void should_return_false_if_both_agent_name_and_version_are_invalid() {
        // given
        String agentName = "invalid/name";
        String agentVersion = "1v.23.ax";

        // when
        boolean isAgentValid = AblyAgentValidator.isValid(agentName, agentVersion);

        // then
        assertFalse(isAgentValid);
    }

    @Test
    public void should_return_true_if_agent_name_and_version_are_valid() {
        // given
        String agentName = "valid-name";
        String agentVersion = "1.2.3-alpha.14";

        // when
        boolean isAgentValid = AblyAgentValidator.isValid(agentName, agentVersion);

        // then
        assertTrue(isAgentValid);
    }

    @Test
    public void should_return_true_if_agent_name_is_valid_and_version_is_null() {
        // given
        String agentName = "valid-name";
        String agentVersion = null;

        // when
        boolean isAgentValid = AblyAgentValidator.isValid(agentName, agentVersion);

        // then
        assertTrue(isAgentValid);
    }

    @Test
    public void should_return_true_if_all_agents_are_valid() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("valid-name", "1.0.1");
        agents.put("another-valid-name", null);

        // when
        boolean areAgentsValid = AblyAgentValidator.areAllValid(agents);

        // then
        assertTrue(areAgentsValid);
    }

    @Test
    public void should_return_false_if_any_agent_is_invalid() {
        // given
        Map<String, String> agents = new HashMap<>();
        agents.put("valid-name", "1.0.1");
        agents.put("invalid/name", "1.0.1");
        agents.put("another-valid-name", null);

        // when
        boolean areAgentsValid = AblyAgentValidator.areAllValid(agents);

        // then
        assertFalse(areAgentsValid);
    }
}
