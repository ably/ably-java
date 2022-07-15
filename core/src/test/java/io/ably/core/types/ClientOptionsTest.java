package io.ably.core.types;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientOptionsTest {

    private final ClientOptions clientOptions = new ClientOptions();

    @Test
    public void should_support_idempotent_rest_publishing() {
        // Then
        assertTrue(clientOptions.idempotentRestPublishing);
    }
}
