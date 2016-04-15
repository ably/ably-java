package io.ably.lib.types;

import io.ably.lib.types.ErrorInfo;

/**
 * An interface allowing a client to be notified of the outcome
 * of an asynchronous operation.
 */
public interface Callback<T> {
	/**
	 * Called when the associated operation completes successfully,
	 */
	public void onSuccess(T result);

	/**
	 * Called when the associated operation completes with an error.
	 * @param reason: information about the error.
	 */
	public void onError(ErrorInfo reason);
}
