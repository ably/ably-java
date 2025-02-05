package io.ably.lib.rest;

import java.util.HashMap;
import java.util.Map;

public final class DerivedClientOptions {
    private final Map<String, String> agents;

    DerivedClientOptions(Map<String, String> agents) {
        this.agents = agents;
    }

    public static DerivedClientOptionsBuilder builder() {
        return new DerivedClientOptionsBuilder();
    }

    public Map<String, String> getAgents() {
        return this.agents;
    }

    public static class DerivedClientOptionsBuilder {
        private final Map<String, String> agents = new HashMap<>();

        public DerivedClientOptionsBuilder addAgent(String agent, String version) {
            this.agents.put(agent, version);
            return this;
        }

        public DerivedClientOptions build() {
            return new DerivedClientOptions(this.agents);
        }
    }
}
