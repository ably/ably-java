package io.ably.lib.util;

import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;

public class Listeners {

    public static <T> Callback<T> fromCompletionListener(CompletionListener listener) {
        return new CompletionListenerWrapper<T>(listener);
    }

    private static class CompletionListenerWrapper<T> implements Callback<T> {
        private final CompletionListener listener;

        private CompletionListenerWrapper(CompletionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(T result) {
            if (listener != null) {
                listener.onSuccess();
            }
        }

        @Override
        public void onError(ErrorInfo reason) {
            if (listener != null) {
                listener.onError(reason);
            }
        }
    }
}
