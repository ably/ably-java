package com.ably.pubsub

data class WrapperSdkProxyOptions(val agents: Map<String, String>)

interface SdkWrapperCompatible<T> {

  /**
   * Creates a proxy client to be used to supply analytics information for Ably-authored SDKs.
   * The proxy client shares the state of the `RealtimeClient` or `RestClient` instance on which this method is called.
   * This method should only be called by Ably-authored SDKs.
   */
  fun createWrapperSdkProxy(options: WrapperSdkProxyOptions): T
}

fun RealtimeClient.createWrapperSdkProxy(options: WrapperSdkProxyOptions): RealtimeClient =
  (this as SdkWrapperCompatible<*>).createWrapperSdkProxy(options) as RealtimeClient

fun RestClient.createWrapperSdkProxy(options: WrapperSdkProxyOptions): RestClient =
  (this as SdkWrapperCompatible<*>).createWrapperSdkProxy(options) as RestClient

