package io.ably.lib.realtime;

import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Callback;

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
	public static class Multicaster extends io.ably.lib.util.Multicaster<CompletionListener> implements CompletionListener {
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

	public static class ToCallback implements Callback<Void> {
		private CompletionListener listener;
		public ToCallback(CompletionListener listener) {
			this.listener = listener;
		}

		@Override
		public void onSuccess(Void v) {
			listener.onSuccess();
		}

		@Override
		public void onError(ErrorInfo reason) {
			listener.onError(reason);
		}
	}

	public static class FromCallback implements CompletionListener {
		private final Callback<Void> callback;

		public FromCallback(Callback<Void> callback) {
			this.callback = callback;
		}

		@Override
		public void onSuccess() {
			callback.onSuccess(null);
		}

		@Override
		public void onError(ErrorInfo reason) {
			callback.onError(reason);
		}
	}
}
