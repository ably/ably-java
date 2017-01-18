package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.JsonElement;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.Response;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncHttpPaginatedResponse;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;

public class AsyncHttpPaginatedQuery implements ResponseHandler<AsyncHttpPaginatedResponse> {

	public AsyncHttpPaginatedQuery(AsyncHttp http, String method, String path, Param[] headers, Param[] params,
			RequestBody requestBody) {
		this.http = http;
		this.method = method;
		this.path = path;
		this.headers = headers;
		this.params = params;
		this.requestBody = requestBody;
		this.bodyHandler = HttpPaginatedQuery.jsonArrayResponseHandler;
	}

	public void exec(final AsyncHttpPaginatedResponse.Callback callback) {
		exec(params, callback);
	}

	public void exec(Param[] params, final AsyncHttpPaginatedResponse.Callback callback) {
		http.exec(path, method, headers, params, requestBody, this, wrap(callback));
	}

	/**
	 * A private class encapsulating the result of a single page response
	 *
	 */
	public class AsyncHttpPaginatedResult extends AsyncHttpPaginatedResponse {
		private JsonElement[] contents;

		private AsyncHttpPaginatedResult(Response response, ErrorInfo error) {
			statusCode = response.statusCode;
			headers = HttpUtils.toParamArray(response.headers);
			if(error != null) {
				errorCode = error.code;
				errorMessage = error.message;
			} else {
				success = true;
				if(response.body != null) {
					try {
						contents = bodyHandler.handleResponseBody(response.contentType, response.body);
					} catch (AblyException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			List<String> linkHeaders = response.getHeaderFields(Http.LINK);
			if(linkHeaders != null) {
				HashMap<String, String> links = PaginatedQuery.parseLinks(linkHeaders);
				relFirst = links.get("first");
				relCurrent = links.get("current");
				relNext = links.get("next");
			}
		}

		@Override
		public JsonElement[] items() { return contents; }

		@Override
		public void first(AsyncHttpPaginatedResponse.Callback callback) {
			execRel(relFirst, callback);
		}

		@Override
		public void current(AsyncHttpPaginatedResponse.Callback callback) {
			execRel(relCurrent, callback);
		}

		@Override
		public void next(AsyncHttpPaginatedResponse.Callback callback) {
			execRel(relNext, callback);
		}

		private void execRel(String linkUrl, AsyncHttpPaginatedResponse.Callback callback) {
			if(linkUrl == null) {
				callback.onResponse(null);
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
			exec(params, callback);
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
	public AsyncHttpPaginatedResponse handleResponse(Response response, ErrorInfo error) {
		return new AsyncHttpPaginatedResult(response, error);
	}

	private static Callback<AsyncHttpPaginatedResponse> wrap(final AsyncHttpPaginatedResponse.Callback callback) {
		return new Callback<AsyncHttpPaginatedResponse>() {
			@Override
			public void onSuccess(AsyncHttpPaginatedResponse result) {
				callback.onResponse(result);
			}
			@Override
			public void onError(ErrorInfo reason) {
				callback.onError(reason);
			}
		};
	}

	private final AsyncHttp http;
	private final String method;
	private final String path;
	private final Param[] headers;
	private final Param[] params;
	private final RequestBody requestBody;
	private final BodyHandler<JsonElement> bodyHandler;
}
