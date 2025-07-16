package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.unit.getDefaultLiveCounterWithMockedDeps
import org.junit.Test
import kotlin.test.assertEquals

class DefaultLiveCounterTest {
  @Test
  fun `(RTLC6, RTLC6a) DefaultLiveCounter should override serials with state serials from sync`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    // Set initial data
    liveCounter.siteTimeserials["site1"] = "serial1"
    liveCounter.siteTimeserials["site2"] = "serial2"

    val objectState = ObjectState(
      objectId = "counter:testCounter@1",
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )
    liveCounter.applyObjectSync(objectState)
    assertEquals(mapOf("site3" to "serial3", "site4" to "serial4"), liveCounter.siteTimeserials) // RTLC6a
  }
}
