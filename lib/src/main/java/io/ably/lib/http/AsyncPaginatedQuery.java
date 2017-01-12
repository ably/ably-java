package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.Response;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;

/**
 * An object that encapsulates parameters of a REST query with a paginated response
 *
 * @param <T> the body response type.
 */
public class AsyncPaginatedQuery<T> implements ResponseHandler<AsyncPaginatedResult<T>> {

	/**
	 * Construct a PaginatedQuery
	 *
	 * @param http. the http instance
	 * @param path. the path of the resource being queried
	 * @param headers. headers to pass into the first and all relative queries
	 * @param params. params to pass into the initial query
	 * @param bodyHandler. handler to parse response bodies for first and all relative queries
	 */
	public AsyncPaginatedQuery(AsyncHttp http, String path, Param[] headers, Param[] params, BodyHandler<T> bodyHandler) {
		this(http, path, headers, params, null, bodyHandler);
	}

	/**
	 * Construct a PaginatedQuery
	 *
	 * @param http. the http instance
	 * @param path. the path of the resource being queried
	 * @param headers. headers to pass into the first and all relative queries
	 * @param params. params to pass into the initial query
	 * @param bodyHandler. handler to parse response bodies for first and all relative queries
	 */
	public AsyncPaginatedQuery(AsyncHttp http, String path, Param[] headers, Param[] params, RequestBody requestBody, BodyHandler<T> bodyHandler) {
		this.http = http;
		this.path = path;
		this.headers = headers;
		this.params = params;
		this.requestBody = requestBody;
		this.bodyHandler = bodyHandler;
	}

	/**
	 * Get the result of the first query
	 * @param callback. On success returns A PaginatedResult<T> giving the
	 * first page of results together with any available links to related results pages.
	 */
	public void get(Callback<AsyncPaginatedResult<T>> callback) {
		http.get(path, headers, params, this, callback);
	}

	/**
	 * Get the result of a query with the current params
	 * @param callback. On success returns A PaginatedResult<T> giving the
	 * first page of results together with any available links to related results pages.
	 */
	public void exec(String method, Callback<AsyncPaginatedResult<T>> callback) {
		exec(method, params, callback);
	}

	/**
	 * Get the result of a query with the given params
	 * @param callback. On success returns A PaginatedResult<T> giving the
	 * first page of results together with any available links to related results pages.
	 */
	public void exec(String method, Param[] params, Callback<AsyncPaginatedResult<T>> callback) {
		http.exec(path, method, headers, params, requestBody, this, callback);
	}

	/**
	 * A private class encapsulating the result of a single page response
	 *
	 */
	public class ResultPage implements AsyncPaginatedResult<T> {
		private T[] contents;

		private ResultPage(T[] contents, Collection<String> linkHeaders) {
			this.contents = contents;
			if(linkHeaders != null) {
				HashMap<String, String> links = PaginatedQuery.parseLinks(linkHeaders);
				relFirst = links.get("first");
				relCurrent = links.get("current");
				relNext = links.get("next");
			}
		}

		@Override
		public T[] items() { return contents; }

		@Override
		public void first(Callback<AsyncPaginatedResult<T>> callback) {
			getRel(relFirst, callback);
		}

		@Override
		public void current(Callback<AsyncPaginatedResult<T>> callback) {
			getRel(relCurrent, callback);
		}

		@Override
		public void next(Callback<AsyncPaginatedResult<T>> callback) {
			getRel(relNext, callback);
		}

		private void getRel(String linkUrl, Callback<AsyncPaginatedResult<T>> callback) {
			if(linkUrl == null) {
				callback.onSuccess(null);
				return;
			}

			/* we're expecting the format to be ./path-component?name=value&name=value... */
			Matcher urlMatch = PaginatedQuery.urlPattern.matcher(linkUrl);
			if(!urlMatch.matches()) {
				callback.onError(new ErrorInfo("Unexpected link URL format", 500, 50000));
				return;
			}

			String[] paramSpecs = urlMatch.group(2).split("&");
			Param[] params = new Param[paramSpecs.length];
			try {
				for(int i = 0; i < paramSpecs.length; i++) {
					String[] split = paramSpecs[i].split("=");
					params[i] = new Param(split[0], URLDecoder.decode(split[1], "UTF-8"));
				}
			} catch(UnsupportedEncodingException uee) {}
			http.get(path, headers, params, AsyncPaginatedQuery.this, callback);
		}
	
		@Override
		public boolean hasFirst() { return relFirst != null; }

		@Override
		public boolean hasCurrent() { return relCurrent != null; }

		@Override
		public boolean hasNext() { return relNext != null; }

		private String relFirst, relCurrent, relNext;
	}

	@Override
	public AsyncPaginatedResult<T> handleResponse(Response response, ErrorInfo error) throws AblyException {
		if(error != null) {
			throw AblyException.fromErrorInfo(error);
		}
		T[] responseContents = bodyHandler.handleResponseBody(response.contentType, response.body);
		List<String> linkHeaders = response.getHeaderFields(Http.LINK);
		return new ResultPage(responseContents, linkHeaders);
	}

	/****************
	 * internal
	 ****************/

	private final AsyncHttp http;
	private final String path;
	private final Param[] headers;
	private final Param[] params;
	private final RequestBody requestBody;
	private final BodyHandler<T> bodyHandler;
}
