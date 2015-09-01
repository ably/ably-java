package io.ably.realtime;

import io.ably.types.ErrorInfo;

/**
 * An interface allowing a client to be notified of the outcome
 * of an asynchronous operation.
 */
public interface CompletionListener {
	/**
	 * Called when the associated operation completes successfully,
	 */
	public void onSuccess();

	/**
	 * Called when the associated operation completes with an error.
	 * @param reason: information about the error.
	 */
	public void onError(ErrorInfo reason);

	/**
	 * A Multicaster instance is used in the Ably library to manage a list
	 * of client listeners against certain operations.
	 */
	public static class Multicaster extends io.ably.util.Multicaster<CompletionListener> implements CompletionListener {
		public Multicaster(CompletionListener... members) { super(members); }

		@Override
		public void onSuccess() {
			for(CompletionListener member : members)
				try {
					member.onSuccess();
				} catch(Throwable t) {}
		}

		@Override
		public void onError(ErrorInfo reason) {
			for(CompletionListener member : members)
				try {
					member.onError(reason);
				} catch(Throwable t) {}
		}
	}
}
