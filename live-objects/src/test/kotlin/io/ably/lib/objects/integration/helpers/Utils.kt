package io.ably.lib.objects.integration.helpers

import io.ably.lib.objects.DefaultLiveObjects
import io.ably.lib.objects.LiveCounter
import io.ably.lib.objects.LiveMap
import io.ably.lib.objects.LiveObjects
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap

internal val LiveMap.ObjectId get() = (this as DefaultLiveMap).objectId

internal val LiveCounter.ObjectId get() = (this as DefaultLiveCounter).objectId

internal val LiveObjects.State get() = (this as DefaultLiveObjects).state
