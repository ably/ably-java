package io.ably.lib.objects.unit.type.livemap

import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectMap
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.unit.*
import org.junit.Test
import kotlin.test.assertEquals

class DefaultLiveMapTest {
  @Test
  fun `(RTLM6, RTLM6a) DefaultLiveMap should override serials with state serials from sync`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    // Set initial data
    liveMap.siteTimeserials["site1"] = "serial1"
    liveMap.siteTimeserials["site2"] = "serial2"

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
      map = ObjectMap(
        semantics = MapSemantics.LWW,
      )
    )
    liveMap.applyObjectSync(objectState)
    assertEquals(mapOf("site3" to "serial3", "site4" to "serial4"), liveMap.siteTimeserials) // RTLM6a
  }
}
