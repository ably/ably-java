package io.ably.lib.objects.path

import io.ably.lib.objects.type.map.LiveMapValue
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo

/** Error helpers used by the path API. Codes align with the existing Objects error ranges. */

internal fun objectNotFound(path: String): AblyException =
    AblyException.fromErrorInfo(ErrorInfo("Object not found at path '$path'", 400, 92000))

internal fun typeMismatch(path: String, expected: String, actual: LiveMapValue): AblyException {
    val actualName = when {
        actual.isLiveMap()     -> "LiveMap"
        actual.isLiveCounter() -> "LiveCounter"
        actual.isString()      -> "String"
        actual.isNumber()      -> "Number"
        actual.isBoolean()     -> "Boolean"
        actual.isBinary()      -> "Binary"
        actual.isJsonArray()   -> "JsonArray"
        actual.isJsonObject()  -> "JsonObject"
        else                   -> "Unknown"
    }
    return AblyException.fromErrorInfo(
        ErrorInfo("Type mismatch at path '$path': expected $expected, got $actualName", 400, 92001)
    )
}

internal fun notImplementedAbly(label: String): AblyException =
    AblyException.fromErrorInfo(ErrorInfo("$label is not yet implemented", 501, 92999))
