package io.ably.lib.`object`.path

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.WireObjectData
import io.ably.lib.`object`.decodedBytes
import io.ably.lib.`object`.path.types.BinaryPathObject
import io.ably.lib.`object`.path.types.BooleanPathObject
import io.ably.lib.`object`.path.types.JsonArrayPathObject
import io.ably.lib.`object`.path.types.JsonObjectPathObject
import io.ably.lib.`object`.path.types.NumberPathObject
import io.ably.lib.`object`.path.types.StringPathObject

/**
 * Shared base for the six primitive PathObject subclasses (allowed by
 * RTTS6f). Each adds only a nullable `value()` narrowed to its primitive,
 * returning null when the path does not resolve or resolves to a different
 * type (RTPO3c1, RTTS6c).
 */
internal abstract class DefaultPrimitivePathObject(
  bridge: ObjectsBridge,
  pathSegments: List<String>,
) : DefaultBasePathObject(bridge, pathSegments) {

  /** The resolved primitive leaf data, or null (RTPO3c1). */
  protected fun resolvedLeaf(): WireObjectData? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO7a / RTO25
    return (resolve() as? ResolvedValue.Leaf)?.data
  }
}

/** Spec: RTTS6c */
internal class DefaultNumberPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), NumberPathObject {
  override fun value(): Number? = resolvedLeaf()?.number
}

/** Spec: RTTS6c */
internal class DefaultStringPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), StringPathObject {
  override fun value(): String? = resolvedLeaf()?.string
}

/** Spec: RTTS6c */
internal class DefaultBooleanPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), BooleanPathObject {
  override fun value(): Boolean? = resolvedLeaf()?.boolean
}

/** Spec: RTTS6c */
internal class DefaultBinaryPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), BinaryPathObject {
  override fun value(): ByteArray? = resolvedLeaf()?.decodedBytes()
}

/** Spec: RTTS6c */
internal class DefaultJsonObjectPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), JsonObjectPathObject {
  override fun value(): JsonObject? = resolvedLeaf()?.json?.takeIf { it.isJsonObject }?.asJsonObject
}

/** Spec: RTTS6c */
internal class DefaultJsonArrayPathObject(bridge: ObjectsBridge, pathSegments: List<String>) :
  DefaultPrimitivePathObject(bridge, pathSegments), JsonArrayPathObject {
  override fun value(): JsonArray? = resolvedLeaf()?.json?.takeIf { it.isJsonArray }?.asJsonArray
}
