package io.ably.lib.`object`

import io.ably.lib.`object`.path.DefaultLiveMapPathObject
import io.ably.lib.`object`.path.types.LiveMapPathObject

/**
 * The RTO23 entry point: returns the PathObject rooted at the channel's root
 * InternalLiveMap with an empty path. In typed SDKs the static type is
 * LiveMapPathObject (RTO23f / RTTS6d). Exposing this on the channel facade is
 * the bridge's responsibility.
 *
 * Spec: RTO23
 */
internal suspend fun ObjectsBridge.getRootPathObject(): LiveMapPathObject {
  throwIfInvalidAccessApiConfiguration() // RTO23a / RTO25
  ensureAttachedAndSynced() // RTO23b, RTO23c, RTO23e
  return DefaultLiveMapPathObject(this, emptyList()) // RTO23d, RTO23f
}
