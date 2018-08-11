package io.ably.lib.http;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Log;

/**
 * HttpScheduler schedules HttpCore operations to an Executor, exposing a generic async API.
 *
 * Internal; use Http instead.
 *
 * @param <Executor> The Executor that will run blocking operations.
 */
public class HttpScheduler<Executor extends java.util.concurrent.Executor> {

    /**
     * Async HTTP GET for Ably host, with fallbacks
     * @param path
     * @param headers
     * @param params
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> get(String path, Param[] headers, Param[] params, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, HttpConstants.Methods.GET, headers, params, null, responseHandler, requireAblyAuth, callback);
    }

    /**
     * Async HTTP PUT for Ably host, with fallbacks
     * @param path
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> put(String path, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, HttpConstants.Methods.PUT, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
    }

    /**
     * Async HTTP PATCH for Ably host, with fallbacks
     * @param path
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> patch(String path, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, HttpConstants.Methods.PATCH, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
    }

    /**
     * Async HTTP POST for Ably host, with fallbacks
     * @param path
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> post(String path, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, HttpConstants.Methods.POST, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
    }

    /**
     * Async HTTP DEL for Ably host, with fallbacks
     * @param path
     * @param headers
     * @param params
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> del(String path, Param[] headers, Param[] params, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, HttpConstants.Methods.DELETE, headers, params, null, responseHandler, requireAblyAuth, callback);
    }

    /**
     * Async HTTP request for Ably host, with fallbacks
     * @param path
     * @param method
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     */
    public <T> Future<T> exec(String path, String method, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth, Callback<T> callback) {
        return ablyHttpExecuteWithFallback(path, method, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
    }

    /**************************
     *     Internal API
     **************************/

    /**
     * An AsyncRequest type representing a request to a specific URL
     * @param <T>
     */
    private class UrlRequest<T> extends AsyncRequest<T> implements Runnable {
        private UrlRequest(
                URL url,
                final String method,
                final Param[] headers,
                final Param[] params,
                final HttpCore.RequestBody requestBody,
                final boolean withCredentials,
                final HttpCore.ResponseHandler<T> responseHandler,
                final Callback<T> callback) {
            super(method, headers, params, requestBody, withCredentials, responseHandler, callback);
            this.url = url;
        }
        @Override
        public void run() {
            try {
                T result = httpExecuteWithRetry(url);
                setResult(result);
            } catch(AblyException e) {
                setError(e.errorInfo);
            } finally {
                disposeConnection();
            }
        }
        private final URL url;
    }

    /**
     * An AsyncRequest type representing a request to an Ably endpoint specified by host and path,
     * supporting reauthentication on receipt of WWW-Authenticate
     * @param <T>
     */
    private class AblyRequestWithRetry<T> extends AsyncRequest<T> implements Runnable {
        private AblyRequestWithRetry(
                String host,
                String path,
                final String method,
                final Param[] headers,
                final Param[] params,
                final HttpCore.RequestBody requestBody,
                final HttpCore.ResponseHandler<T> responseHandler,
                final boolean requireAblyAuth,
                final Callback<T> callback) {
            super(method, headers, params, requestBody, true, responseHandler, callback);
            this.host = host;
            this.path = path;
            this.requireAblyAuth = requireAblyAuth;
        }
        @Override
        public void run() {
            try {
                result = httpExecuteWithRetry(host, path, requireAblyAuth);
                setResult(result);
            } catch(AblyException e) {
                setError(e.errorInfo);
            } finally {
                disposeConnection();
            }
        }
        private final String host;
        private final String path;
        private final Boolean requireAblyAuth;
    }

    /**
     * An AsyncRequest type representing a request to an Ably endpoint specified by path,
     * supporting host fallback and reauthentication on receipt of WWW-Authenticate
     * @param <T>
     */
    private class AblyRequestWithFallback<T> extends AsyncRequest<T> implements Runnable {
        private AblyRequestWithFallback(
                String path,
                final String method,
                final Param[] headers,
                final Param[] params,
                final HttpCore.RequestBody requestBody,
                final HttpCore.ResponseHandler<T> responseHandler,
                final boolean requireAblyAuth,
                final Callback<T> callback) {
            super(method, headers, params, requestBody, true, responseHandler, callback);
            this.path = path;
            this.requireAblyAuth = requireAblyAuth;
        }
        @Override
        public void run() {
            String candidateHost = httpCore.getHost();
            int retryCountRemaining = httpCore.hosts.getFallback(candidateHost) != null ? httpCore.options.httpMaxRetryCount : 0;

            while(!isCancelled) {
                try {
                    result = httpExecuteWithRetry(candidateHost, path, requireAblyAuth);
                    setResult(result);
                    break;
                } catch (AblyException.HostFailedException e) {
                    if(--retryCountRemaining < 0) {
                        setError(e.errorInfo);
                        break;
                    }
                    Log.d(TAG, "Connection failed to host `" + candidateHost + "`. Searching for new host...");
                    candidateHost = httpCore.hosts.getFallback(candidateHost);
                    if (candidateHost == null) {
                        setError(e.errorInfo);
                        break;
                    }
                    Log.d(TAG, "Switched to `" + candidateHost + "`.");
                } catch(AblyException e) {
                    setError(e.errorInfo);
                    break;
                } finally {
                    disposeConnection();
                }
            }
        }
        private final String path;
        private final boolean requireAblyAuth;
    }

    /**
     * A class encapsulating a scheduled or in-process async HTTP request
     * @param <T>
     */
    private abstract class AsyncRequest<T> implements Future<T> {
        private AsyncRequest(
                final String method,
                final Param[] headers,
                final Param[] params,
                final HttpCore.RequestBody requestBody,
                final boolean withCredentials,
                final HttpCore.ResponseHandler<T> responseHandler,
                final Callback<T> callback) {
            this.method = method;
            this.headers = headers;
            this.params = params;
            this.requestBody = requestBody;
            this.responseHandler = responseHandler;
            this.callback = callback;
        }

        /**************************
         *    Future<T> methods
         **************************/
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            isCancelled = true;
            return disposeConnection();
        }
        @Override
        public boolean isCancelled() {
            return isCancelled;
        }
        @Override
        public boolean isDone() {
            return isDone;
        }
        @Override
        public T get() throws InterruptedException, ExecutionException {
            synchronized(this) {
                while(!isDone) {
                    wait();
                }
                if(err != null) {
                    throw new ExecutionException(AblyException.fromErrorInfo(err));
                }
            }
            return result;
        }
        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            long remaining = unit.toMillis(timeout), deadline = System.currentTimeMillis() + remaining;
            synchronized(this) {
                while(remaining > 0) {
                    wait(remaining);
                    if(isDone) { break; }
                    remaining = deadline - System.currentTimeMillis();
                }
                if(!isDone) {
                    throw new TimeoutException();
                }
                if(err != null) {
                    throw new ExecutionException(AblyException.fromErrorInfo(err));
                }
            }
            return result;
        }

        /**************************
         *        Private
         **************************/

        protected T httpExecuteWithRetry(URL url) throws AblyException {
            return httpCore.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, false);
        }
        protected T httpExecuteWithRetry(String host, String path, boolean requireAblyAuth) throws AblyException {
            URL url = HttpUtils.buildURL(httpCore.scheme, host, httpCore.port, path, params);
            return httpCore.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, requireAblyAuth);
        }
        protected void setResult(T result) {
            synchronized(this) {
                this.result = result;
                this.isDone = true;
                notifyAll();
            }
            if(callback != null) {
                callback.onSuccess(result);
            }
        }
        protected void setError(ErrorInfo err) {
            synchronized(this) {
                this.err = err;
                this.isDone = true;
                notifyAll();
            }
            if(callback != null) {
                callback.onError(err);
            }
        }
        protected synchronized boolean disposeConnection() {
            boolean hasConnection = conn != null;
            if(hasConnection) {
                conn.disconnect();
                conn = null;
            }
            return hasConnection;
        }

        protected HttpURLConnection conn;
        protected T result;
        protected ErrorInfo err;

        protected final String method;
        protected final Param[] headers;
        protected final Param[] params;
        protected final HttpCore.RequestBody requestBody;
        protected final HttpCore.ResponseHandler<T> responseHandler;
        protected final Callback<T> callback;
        protected boolean isCancelled = false;
        protected boolean isDone = false;
    }

    protected HttpScheduler(HttpCore httpCore, Executor executor) {
        this.httpCore = httpCore;
        this.executor = executor;
    }

    /**
     * Make an asynchronous HTTP request to a given URL
     * @param url
     * @param method
     * @param headers
     * @param requestBody
     * @param withCredentials
     * @param responseHandler
     * @param callback
     * @return
     */
    public <T> Future<T> httpExecute(
            final URL url,
            final String method,
            final Param[] headers,
            final HttpCore.RequestBody requestBody,
            final boolean withCredentials,
            final HttpCore.ResponseHandler<T> responseHandler,
            final Callback<T> callback) {

        UrlRequest<T> request = new UrlRequest<>(url, method, headers, null, requestBody, withCredentials, responseHandler, callback);
        executor.execute(request);
        return request;
    }

    /**
     * Make an asynchronous HTTP request to an Ably endpoint, using the Ably auth credentials and fallback hosts if necessary
     * @param path
     * @param method
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     * @return
     */
    public <T> Future<T> ablyHttpExecuteWithFallback(
            final String path,
            final String method,
            final Param[] headers,
            final Param[] params,
            final HttpCore.RequestBody requestBody,
            final HttpCore.ResponseHandler<T> responseHandler,
            final boolean requireAblyAuth,
            final Callback<T> callback) {

        AblyRequestWithFallback<T> request = new AblyRequestWithFallback<>(path, method, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
        executor.execute(request);
        return request;
    }

    /**
     * Make an asynchronous HTTP request to an Ably endpoint, using the Ably auth credentials and reauthentication if necessary
     * @param host
     * @param path
     * @param method
     * @param headers
     * @param params
     * @param requestBody
     * @param responseHandler
     * @param callback
     * @return
     */
    public <T> Future<T> ablyHttpExecuteWithRetry(
            final String host,
            final String path,
            final String method,
            final Param[] headers,
            final Param[] params,
            final HttpCore.RequestBody requestBody,
            final HttpCore.ResponseHandler<T> responseHandler,
            final boolean requireAblyAuth,
            final Callback<T> callback) {

        AblyRequestWithRetry<T> request = new AblyRequestWithRetry<>(host, path, method, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
        executor.execute(request);
        return request;
    }

    protected final Executor executor;
    private final HttpCore httpCore;

    protected static final String TAG = HttpScheduler.class.getName();

}
