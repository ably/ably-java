package io.ably.lib

import io.ably.lib.types.ClientOptions
import kotlin.test.Test
import kotlin.test.assertNotNull

class SampleTest {
    @Test
    fun `ClientOptions can be instantiated`() {
        val options = ClientOptions("test-key")
        assertNotNull(options)
    }
}
