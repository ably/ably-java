package io.ably.lib.`object`.instance

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.WireObjectData
import io.ably.lib.`object`.decodedBytes
import io.ably.lib.`object`.instance.types.BinaryInstance
import io.ably.lib.`object`.instance.types.BooleanInstance
import io.ably.lib.`object`.instance.types.JsonArrayInstance
import io.ably.lib.`object`.instance.types.JsonObjectInstance
import io.ably.lib.`object`.instance.types.NumberInstance
import io.ably.lib.`object`.instance.types.StringInstance
import io.ably.lib.`object`.typeMismatchError

/**
 * Shared base for the six primitive Instance subclasses (allowed by RTTS10d).
 * Each adds only a non-null `value()` narrowed to its primitive (RTTS10c).
 *
 * On a mismatched as* wrapper the non-nullable read throws ErrorInfo 400/92007
 * (RTTS9d2-style; see the design note in DEFAULT_INTERFACE_IMPLEMENTATION.md -
 * a Kotlin override of a `@NotNull` method cannot return null per RTTS9d1, and
 * the typed contract makes the misuse statically visible, mirroring RTTS6e).
 */
internal abstract class DefaultPrimitiveInstance(
  bridge: ObjectsBridge,
  value: ResolvedValue,
) : DefaultBaseInstance(bridge, value) {

  /** The wrapped primitive leaf data; throws 92007 on mismatched wrappers. */
  protected fun leafOrThrow(expected: String): WireObjectData {
    bridge.throwIfInvalidAccessApiConfiguration() // RTO25
    return (value as? ResolvedValue.Leaf)?.data
      ?: throw typeMismatchError("Instance does not wrap a $expected value")
  }
}

/** Spec: RTTS10c */
internal class DefaultNumberInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), NumberInstance {
  override fun value(): Number = leafOrThrow("Number").number
    ?: throw typeMismatchError("Instance does not wrap a Number value")
}

/** Spec: RTTS10c */
internal class DefaultStringInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), StringInstance {
  override fun value(): String = leafOrThrow("String").string
    ?: throw typeMismatchError("Instance does not wrap a String value")
}

/** Spec: RTTS10c */
internal class DefaultBooleanInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), BooleanInstance {
  override fun value(): Boolean = leafOrThrow("Boolean").boolean
    ?: throw typeMismatchError("Instance does not wrap a Boolean value")
}

/** Spec: RTTS10c */
internal class DefaultBinaryInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), BinaryInstance {
  override fun value(): ByteArray = leafOrThrow("Binary").decodedBytes()
    ?: throw typeMismatchError("Instance does not wrap a Binary value")
}

/** Spec: RTTS10c */
internal class DefaultJsonObjectInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), JsonObjectInstance {
  override fun value(): JsonObject = leafOrThrow("JsonObject").json?.takeIf { it.isJsonObject }?.asJsonObject
    ?: throw typeMismatchError("Instance does not wrap a JsonObject value")
}

/** Spec: RTTS10c */
internal class DefaultJsonArrayInstance(bridge: ObjectsBridge, value: ResolvedValue) :
  DefaultPrimitiveInstance(bridge, value), JsonArrayInstance {
  override fun value(): JsonArray = leafOrThrow("JsonArray").json?.takeIf { it.isJsonArray }?.asJsonArray
    ?: throw typeMismatchError("Instance does not wrap a JsonArray value")
}
