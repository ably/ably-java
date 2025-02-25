package com.ably.annotations

/**
 * API marked with this annotation is internal, and it is not intended to be used outside Ably.
 * It could be modified or removed without any notice. Using it outside Ably could cause undefined behaviour and/or
 * any unexpected effects.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message = "This API is internal in Ably and should not be used. It could be removed or changed without notice."
)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.TYPEALIAS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.PROPERTY_SETTER,
)
public annotation class InternalAPI
