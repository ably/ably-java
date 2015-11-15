package io.ably.lib.http;

import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Log;

public class AsyncHttp extends ThreadPoolExecutor {

	/**
	 * Async HTTP GET for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param responseHandler
	 * @param callback
	 */
	public <T> void get(String path, Param[] headers, Param[] params, ResponseHandler<T> responseHandler, Callback<T> callback) {
		ablyHttpExecute(path, Http.GET, headers, params, null, responseHandler, callback);
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
	public <T> void post(String path, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler, Callback<T> callback) {
		ablyHttpExecute(path, Http.POST, headers, params, requestBody, responseHandler, callback);
	}

	/**
	 * Async HTTP DEL for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param responseHandler
	 * @param callback
	 */
	public <T> void del(String path, Param[] headers, Param[] params, ResponseHandler<T> responseHandler, Callback<T> callback) {
		ablyHttpExecute(path, Http.DELETE, headers, params, null, responseHandler, callback);
	}

	/**************************
	 *     Internal API
	 **************************/

	public AsyncHttp(Http http) {
		super(DEFAULT_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		this.http = http;
	}

	public void setThreadPoolSize(int size) {
		this.setCorePoolSize(size);
	}

	public <T> void httpExecute(
			final URL url,
			final String method,
			final Param[] headers,
			final RequestBody requestBody,
			final boolean withCredentials,
			final ResponseHandler<T> responseHandler,
			final Callback<T> callback) {

		submit(new Runnable() {
			@Override
			public void run() {
				T result = null;
				try {
					result = http.httpExecute(url, method, headers, requestBody, withCredentials, responseHandler);
				} catch(AblyException e) {
					callback.onError(e.errorInfo);
					return;
				}
				callback.onSuccess(result);
			}
		});
	}

	public <T> void ablyHttpExecute(
			final String path,
			final String method,
			final Param[] headers,
			final Param[] params,
			final RequestBody requestBody,
			final ResponseHandler<T> responseHandler,
			final Callback<T> callback) {

		submit(new Runnable() {
			@Override
			public void run() {
				T result = null;
				ErrorInfo error = null;
				try {
					result = http.ablyHttpExecute(path, method, headers, params, requestBody, responseHandler);
				} catch(AblyException e) {
					error = e.errorInfo;
				}
				try {
					if(error != null)
						callback.onError(error);
					else
						callback.onSuccess(result);
				} catch(Throwable t) {
					Log.e(TAG, "Exception invoking callback", t);
				}
			}
		});
	}

	public void dispose() {
		shutdown();
		try {
			awaitTermination(SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			this.shutdownNow();
		}
	}

	private final Http http;

	private static final int DEFAULT_POOL_SIZE = 8;
	private static final int MAX_POOL_SIZE = 64;
	private static final long KEEP_ALIVE_TIME = 20000L;
	private static final long SHUTDOWN_TIME = 5000L;
	private static final String TAG = AsyncHttp.class.getName();
}
