/**
 * Write-side value types for LiveObjects mutations.
 * {@link io.ably.lib.object.value.LiveMapValue} is the union of values
 * assignable to a {@code LiveMap} key;
 * {@link io.ably.lib.object.value.LiveMap} and
 * {@link io.ably.lib.object.value.LiveCounter} are immutable initial-value
 * holders describing new objects to be created by a mutation; they expose only
 * the static {@code create} factories (RTLMV3 / RTLCV3), which delegate to the
 * LiveObjects implementation extending these abstract classes. Their internal
 * state ({@code entries} / {@code count}) is held by the implementation and
 * has no public accessor.
 *
 * <p>Spec: RTLM20 / RTPO15a2 / RTINS12a2 (value union); RTLMV3 / RTLCV3
 * (new-object value types)
 */
package io.ably.lib.object.value;
