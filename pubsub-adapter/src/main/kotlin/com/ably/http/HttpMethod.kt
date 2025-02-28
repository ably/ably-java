package com.ably.http

public enum class HttpMethod(private val method: String) {
  Get("GET"),
  Post("POST"),
  Put("PUT"),
  Delete("DELETE"),
  Patch("PATCH"),
  ;

  override fun toString(): String = method
}
