package io.ably.lib.transport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultsTest {
    @Test
    public void versions() {
        assertEquals("1.2", Defaults.ABLY_VERSION);
    }
}
