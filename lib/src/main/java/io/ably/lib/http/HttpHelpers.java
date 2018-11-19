package io.ably.lib.http;

import java.io.IOException;
import java.net.URL;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Log;

import static io.ably.lib.http.HttpUtils.buildURL;

public class HttpHelpers {
	/**
	 * Make a synchronous HTTP request to an Ably endpoint, using the Ably auth credentials and fallback hosts if necessary
	 * @param httpCore
	 * @param path
	 * @param method
	 * @param headers
	 * @param params
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public static <T> T ablyHttpExecute(HttpCore httpCore, String path, String method, Param[] headers, Param[] params, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler, boolean requireAblyAuth) throws AblyException {
		String candidateHost = httpCore.hosts.getPreferredHost();
		int retryCountRemaining = (httpCore.hosts.fallbackHostsRemaining(candidateHost) > 0) ? httpCore.options.httpMaxRetryCount : 0;
		URL url;

		while(true) {
			url = buildURL(httpCore.scheme, candidateHost, httpCore.port, path, params);
			try {
				T result = httpCore.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, requireAblyAuth);
				httpCore.hosts.setPreferredHost(candidateHost, true);
				return result;
			} catch (AblyException.HostFailedException e) {
				if(--retryCountRemaining < 0)
					throw e; /* reached httpMaxRetryCount */
				Log.d(TAG, "Connection failed to host `" + candidateHost + "`. Searching for new host...");
				candidateHost = httpCore.hosts.getFallback(candidateHost);
				if (candidateHost == null)
					throw e; /* run out of fallback hosts */
				Log.d(TAG, "Switched to `" + candidateHost + "`.");
			}
		}
	}

	private static final String TAG = HttpHelpers.class.getName();

	/**
	 * Simple HTTP GET; no auth, headers, returning response body as string
	 * @param httpCore
	 * @param url
	 * @return
	 * @throws AblyException
	 */
	public static String getUrlString(HttpCore httpCore, String url) throws AblyException {
		return new String(getUrl(httpCore, url));
	}

	/**
	 * Simple HTTP GET; no auth, headers, returning response body as byte[]
	 * @param httpCore
	 * @param url
	 * @return
	 * @throws AblyException
	 */
	public static byte[] getUrl(HttpCore httpCore, String url) throws AblyException {
		try {
			return httpExecute(httpCore, new URL(url), HttpConstants.Methods.GET, null, null, new HttpCore.ResponseHandler<byte[]>() {
				@Override
				public byte[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
					if(error != null) {
						throw AblyException.fromErrorInfo(error);
					}
					return response.body;
				}});
		} catch(IOException ioe) {
			throw AblyException.fromThrowable(ioe);
		}
	}

	/**
	 * HTTP GET for non-Ably host
	 * @param uri
	 * @param headers
	 * @param params
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public static <T> T getUri(HttpCore httpCore, String uri, Param[] headers, Param[] params, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
		return httpExecute(httpCore, buildURL(uri, params), HttpConstants.Methods.GET, headers, null, responseHandler);
	}

	/**
	 * HTTP POST with data in form encoding for non-Ably host
	 * @param uri
	 * @param headers
	 * @param queryParams
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public static <T> T postUri(HttpCore httpCore, String uri, Param[] headers, Param[] queryParams, Param[] bodyParams, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
		return httpExecute(httpCore, buildURL(uri, queryParams), HttpConstants.Methods.POST, headers, new HttpUtils.FormRequestBody(bodyParams), responseHandler);
	}

	/**
	 * Make a synchronous HTTP request to non-Ably endpoint, specified by URL and using the configured proxy, if any
	 * @param httpCore
	 * @param url
	 * @param method
	 * @param headers
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public static <T> T httpExecute(HttpCore httpCore, URL url, String method, Param[] headers, HttpCore.RequestBody requestBody, HttpCore.ResponseHandler<T> responseHandler) throws AblyException {
		return httpCore.httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, false);
	}

	public static <T> T postSync(final Http http, final String path, final Param[] headers, final Param[] params, final HttpCore.RequestBody requestBody, final HttpCore.ResponseHandler<T> responseHandler, final boolean requireAblyAuth) throws AblyException {
		return http.request(new Http.Execute<T>() {
			@Override
			public void execute(HttpScheduler http, Callback<T> callback) throws AblyException {
				http.post(path, headers, params, requestBody, responseHandler, requireAblyAuth, callback);
			}
		}).sync();
	}
}
