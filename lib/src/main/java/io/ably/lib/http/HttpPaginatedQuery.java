package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.http.Http.Response;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.HttpPaginatedResponse;
import io.ably.lib.types.Param;
import io.ably.lib.util.Serialisation;

public class HttpPaginatedQuery implements ResponseHandler<HttpPaginatedResponse> {

	public HttpPaginatedQuery(Http http, String method, String path, Param[] headers, Param[] params,
			RequestBody requestBody) {
		this.http = http;
		this.method = method;
		this.path = path;
		this.requestHeaders = headers;
		this.requestParams = params;
		this.requestBody = requestBody;
		this.bodyHandler = jsonArrayResponseHandler;
	}

	/**
	 * Get the result of the first query
	 * @return An HttpPaginatedResponse giving the first page of results
	 * together with any available links to related results pages.
	 * @throws AblyException
	 */
	public HttpPaginatedResponse exec() throws AblyException {
		return http.exec(path, method, requestHeaders, requestParams, requestBody, this);
	}

	/**
	 * Get the result of the first query
	 * @return An HttpPaginatedResponse giving the first page of results
	 * together with any available links to related results pages.
	 * @throws AblyException
	 */
	public HttpPaginatedResponse exec(Param[] params) throws AblyException {
		return http.exec(path, method, requestHeaders, params, requestBody, this);
	}

	@Override
	public HttpPaginatedResponse handleResponse(Response response, ErrorInfo error) throws AblyException {
		return new HttpPaginatedResult(response, error);
	}

	public class HttpPaginatedResult extends HttpPaginatedResponse {
		private JsonElement[] contents;

		private HttpPaginatedResult(Response response, ErrorInfo error) throws AblyException {
			statusCode = response.statusCode;
			headers = HttpUtils.toParamArray(response.headers);
			if(error != null) {
				errorCode = error.code;
				errorMessage = error.message;
			} else {
				success = true;
				if(response.body != null) {
					contents = bodyHandler.handleResponseBody(response.contentType, response.body);
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
		public HttpPaginatedResponse first() throws AblyException { return execRel(relFirst); }

		@Override
		public HttpPaginatedResponse current() throws AblyException { return execRel(relCurrent); }

		@Override
		public HttpPaginatedResponse next() throws AblyException { return execRel(relNext); }

		private HttpPaginatedResponse execRel(String linkUrl) throws AblyException {
			if(linkUrl == null) return null;
			/* we're expecting the format to be ./path-component?name=value&name=value... */
			Matcher urlMatch = PaginatedQuery.urlPattern.matcher(linkUrl);
			if(urlMatch.matches()) {
				String[] paramSpecs = urlMatch.group(2).split("&");
				Param[] params = new Param[paramSpecs.length];
				try {
					for(int i = 0; i < paramSpecs.length; i++) {
						String[] split = paramSpecs[i].split("=");
						params[i] = new Param(split[0], URLDecoder.decode(split[1], "UTF-8"));
					}
				} catch(UnsupportedEncodingException uee) {}
				return exec(params);
			}
			throw AblyException.fromErrorInfo(new ErrorInfo("Unexpected link URL format", 500, 50000));
		}
	
		private String relFirst, relCurrent, relNext;

		@Override
		public boolean hasFirst() { return relFirst != null; }

		@Override
		public boolean hasCurrent() { return relCurrent != null; }

		@Override
		public boolean hasNext() { return relNext != null; }

		@Override
		public boolean isLast() {
			return relNext == null;
		}		
	}

	static final BodyHandler<JsonElement> jsonArrayResponseHandler = new BodyHandler<JsonElement>() {
		@Override
		public JsonElement[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			if(!"application/json".equals(contentType)) {
				throw AblyException.fromErrorInfo(new ErrorInfo("Unexpected content type: " + contentType, 500, 50000));
			}
			JsonElement jsonBody = Serialisation.gsonParser.parse(new String(body, Charset.forName("UTF-8")));
			if(!jsonBody.isJsonArray()) {
				return new JsonElement[] { jsonBody };
			}
			JsonArray jsonArray = jsonBody.getAsJsonArray();
			JsonElement[] items = new JsonElement[jsonArray.size()];
			for(int i = 0; i < items.length; i++) {
				items[i] = jsonArray.get(i);
			}
			return items;
		}
	};

	private final Http http;
	private final String method;
	private final String path;
	private final Param[] requestHeaders;
	private final Param[] requestParams;
	private final RequestBody requestBody;
	private final BodyHandler<JsonElement> bodyHandler;
}
