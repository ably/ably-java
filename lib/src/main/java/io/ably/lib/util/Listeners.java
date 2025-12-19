package io.ably.lib.util;

import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.PublishResult;
import io.ably.lib.types.UpdateDeleteResult;

public class Listeners {

    public static <T> Callback<T> fromCompletionListener(CompletionListener listener) {
        return new CompletionListenerWrapper<T>(listener);
    }

    public static Callback<PublishResult> toPublishResultListener(Callback<UpdateDeleteResult> listener) {
        return new UpdateResultToPublishAdapter(listener);
    }

    public static <T> CompletionListener unwrap(Callback<T> listener) {
        if (listener instanceof CompletionListenerWrapper) {
            return ((CompletionListenerWrapper<T>)listener).listener;
        } else {
            return null;
        }
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

    private static class UpdateResultToPublishAdapter implements Callback<PublishResult> {
        private final Callback<UpdateDeleteResult> listener;

        private UpdateResultToPublishAdapter(Callback<UpdateDeleteResult> listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(PublishResult result) {
            if (listener != null) {
                String serial = result != null && result.serials != null && result.serials.length > 0
                    ? result.serials[0] : null;
                listener.onSuccess(new UpdateDeleteResult(serial));
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
