package io.ably.lib.objects

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal val gson: Gson = createGsonSerializer()

private fun createGsonSerializer(): Gson {
  return GsonBuilder().create() // Do not call serializeNulls() to omit null values
}
