package io.ably.lib.test.mock

import io.ably.lib.types.ProtocolMessage
import java.net.URLDecoder

internal fun parseQueryString(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrEmpty()) return emptyMap()
    return rawQuery.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx < 0) null
        else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }.toMap()
}

interface PendingConnection {
    val host: String
    val port: Int
    val tls: Boolean
    val queryParams: Map<String, String>
    fun respondWithSuccess()
    fun respondWithSuccess(message: ProtocolMessage)
    fun respondWithRefused()
    fun respondWithTimeout()
    fun respondWithDnsError()
}
