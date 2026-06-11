package io.ably.lib.`object`

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Object type discriminator used in objectId generation. Spec: RTO14 */
internal enum class WireObjectType(val value: String) {
  Map("map"),
  Counter("counter"),
}

/**
 * ObjectId generation for client-created objects. Copied from the legacy
 * `io.ably.lib.objects.ObjectId` so this package has no dependency on it -
 * the format `type:base64url(sha256(initialValue:nonce))@msTimestamp` is a
 * wire contract.
 *
 * Spec: RTO14, RTO6b1
 */
internal object ObjectsIdentifier {
  internal fun fromInitialValue(
    objectType: WireObjectType,
    initialValue: String,
    nonce: String,
    msTimestamp: Long,
  ): String {
    val valueForHash = "$initialValue:$nonce".toByteArray(StandardCharsets.UTF_8)
    // RTO14b - hash the initial value and nonce to create a unique identifier
    val hashBytes = MessageDigest.getInstance("SHA-256").digest(valueForHash)
    val urlSafeHash = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
    return "${objectType.value}:$urlSafeHash@$msTimestamp"
  }
}

/**
 * Generates a random nonce string for object creation (16 alphanumeric chars).
 * Copied from the legacy `generateNonce`. Spec: RTLMV4g, RTLCV4d
 */
internal fun generateObjectNonce(): String {
  val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
  return (1..16).map { chars.random() }.joinToString("")
}
