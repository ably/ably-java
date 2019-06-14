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

	public abstract static class Map<T, U> implements Callback<T> {
		private final Callback<U> callback;

		public abstract U map(T result);

		public Map(Callback<U> callback) {
			this.callback = callback;
		}

		@Override
		public void onSuccess(T result) {
			callback.onSuccess(map(result));
		}

		@Override
		public void onError(ErrorInfo reason) {
			callback.onError(reason);
		}
	}
}
