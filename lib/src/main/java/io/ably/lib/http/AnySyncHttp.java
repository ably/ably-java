package io.ably.lib.http;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;

/**
 * Created by tcard on 28/04/2017.
 */

public class AnySyncHttp {
    private final AsyncHttp asyncHttp;
    private final SyncHttp syncHttp;

    public AnySyncHttp(AsyncHttp asyncHttp, SyncHttp syncHttp) {
        this.asyncHttp = asyncHttp;
        this.syncHttp = syncHttp;
    }

    public static class Request<Result> {
        private final AnySyncHttp anySyncHttp;
        private final Execute<Result> execute;

        Request(AnySyncHttp anySyncHttp, Execute<Result> execute) {
            this.anySyncHttp = anySyncHttp;
            this.execute = execute;
        }
        
        public Result sync() throws AblyException {
            final SyncExecuteResult<Result> result = new SyncExecuteResult<>();
            execute.execute(anySyncHttp.syncHttp, new Callback<Result>() {
                @Override
                public void onSuccess(Result r) {
                    result.ok = r;
                }

                @Override
                public void onError(ErrorInfo e) {
                    result.error = e;
                }
            });
            if (result.error != null) {
                throw AblyException.fromErrorInfo(result.error);
            }
            return result.ok;
        }

        public void async(Callback<Result> callback) {
            try {
                execute.execute(anySyncHttp.asyncHttp, callback);
            } catch (AblyException e) {
                callback.onError(e.errorInfo);
            }
        }

        private static class SyncExecuteResult<Result> {
            public Result ok = null;
            public ErrorInfo error = null;
        }
    }

    public <Result> Request<Result> request(Execute<Result> execute) {
        return new Request(this, execute);
    }

    public interface Execute<Result> {
        public void execute(CallbackfulHttp http, Callback<Result> callback) throws AblyException;
    }
}
