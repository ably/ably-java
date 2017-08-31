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
            final Object[] result = new Object[1];
            final ErrorInfo[] error = new ErrorInfo[1];
            execute.execute(anySyncHttp.syncHttp, new Callback<Result>() {
                @Override
                public void onSuccess(Result r) {
                    result[0] = r;
                }

                @Override
                public void onError(ErrorInfo e) {
                    error[0] = e;
                }
            });
            if (error[0] != null) {
                throw AblyException.fromErrorInfo(error[0]);
            }
            return (Result) result[0];
        }

        public void async(Callback<Result> callback) {
            try {
                execute.execute(anySyncHttp.asyncHttp, callback);
            } catch (AblyException e) {
                callback.onError(e.errorInfo);
            }
        }
    }

    public <Result> Request<Result> request(Execute<Result> execute) {
        return new Request(this, execute);
    }

    public interface Execute<Result> {
        public void execute(CallbackfulHttp http, Callback<Result> callback) throws AblyException;
    }
}
