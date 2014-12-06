package io.ably.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.ably.http.Http.BodyHandler;
import io.ably.http.Http.ResponseHandler;
import io.ably.types.AblyException;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;

/**
 * An object that encapsulates parameters of a REST query with a paginated response
 *
 * @param <T> the body response type.
 */
public class PaginatedQuery<T> implements ResponseHandler {

	/**
	 * Construct a PaginatedQuery
	 * 
	 * @param http. the http instance
	 * @param path. the path of the resource being queried
	 * @param headers. headers to pass into the first and all relative queries
	 * @param params. params to pass into the initial query
	 * @param bodyHandler. handler to parse response bodies for first and all relative queries
	 */
	public PaginatedQuery(Http http, String path, Param[] headers, Param[] params, BodyHandler<T> bodyHandler) {
		this.http = http;
		this.path = path;
		this.headers = headers;
		this.params = params;
		this.bodyHandler = bodyHandler;
	}

	/**
	 * Get the result of the first query
	 * @return A PaginatedResult<T> giving the first page of results
	 * together with any available links to related results pages.
	 * @throws AblyException
	 */
	@SuppressWarnings("unchecked")
	public PaginatedResult<T> get() throws AblyException {
		return (ResultPage)http.get(path, headers, params, this);
	}

	/**
	 * A private class encapsulating the result of a single page response
	 *
	 */
	private class ResultPage implements PaginatedResult<T> {
		private T[] contents;

		private ResultPage(T[] contents, String[] linkHeaders) throws AblyException {
			this.contents = contents;

			if(linkHeaders != null) {
				HashMap<String, String> links = parseLinks(linkHeaders);
				relFirst = links.get("first");
				relCurrent = links.get("current");
				relNext = links.get("next");
			}
		}

		@Override
		public T[] asArray() { return contents; }

		@Override
		public List<T> asList() { return Arrays.asList(contents); }

		@Override
		public Param[] getFirst() throws AblyException { return getRel(relFirst); }

		@Override
		public Param[] getCurrent() throws AblyException { return getRel(relCurrent); }

		@Override
		public Param[] getNext() throws AblyException { return getRel(relNext); }

		private Param[] getRel(String linkUrl) throws AblyException {
			if(linkUrl == null) return null;
			/* we're expecting the format to be ./path-component?name=value&name=value... */
			Matcher urlMatch = urlPattern.matcher(linkUrl);
			if(urlMatch.matches()) {
				String[] paramSpecs = urlMatch.group(2).split("&");
				Param[] params = new Param[paramSpecs.length];
				try {
					for(int i = 0; i < paramSpecs.length; i++) {
						String[] split = paramSpecs[i].split("=");
						params[i] = new Param(split[0], URLDecoder.decode(split[1], "UTF-8"));
					}
				} catch(UnsupportedEncodingException uee) {}
				return params;
			}
			throw new AblyException("Unexpected link URL format", 500, 50000);
		}
	
		private String relFirst, relCurrent, relNext;
	}

	@Override
	public Object handleResponse(int statusCode, String contentType, String[] linkHeaders, byte[] body) throws AblyException {
		T[] responseContents = bodyHandler.handleResponseBody(contentType, body);
		return new ResultPage(responseContents, linkHeaders);
	}

	/****************
	 * internal
	 ****************/

	private static Pattern linkPattern = Pattern.compile("\\s*<(.*)>;\\s*rel=\"(.*)\"");
	private static Pattern urlPattern = Pattern.compile("\\./(.*)\\?(.*)");

	private static HashMap<String, String> parseLinks(String[] linkHeaders) {
		HashMap<String, String> result = new HashMap<String, String>();
		for(String link : linkHeaders) {
			/* we're expecting the format to be <url>; rel="first current ..." */
			Matcher linkMatch = linkPattern.matcher(link);
			if(linkMatch.matches()) {
				String linkUrl = linkMatch.group(1);
				for (String linkRel : linkMatch.group(2).toLowerCase(Locale.ENGLISH).split("\\s"))
					result.put(linkRel, linkUrl);
			}
		}
		return result;
	}

	private Http http;
	private String path;
	private Param[] headers;
	private Param[] params;
	private BodyHandler<T> bodyHandler;
}
