package io.ably.lib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.ably.lib.realtime.Connection;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ErrorResponse;
import io.ably.lib.types.Param;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

/**
 * Http
 * Support class for HTTP REST operations supporting
 * host fallback in the case of host unavalilability
 * and authentication.
 * Internal
 *
 */
public class Http {

	public interface ResponseHandler<T> {
		T handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException;
	}

	public interface BodyHandler<T> {
		T[] handleResponseBody(String contentType, byte[] body) throws AblyException;
	}

	public interface RequestBody {
		byte[] getEncoded();
		String getContentType();
	}

	private static class Response {
		int statusCode;
		String statusLine;
		Map<String,List<String>> headers;
		String contentType;
		int contentLength;
		byte[] body;

		/**
		 * Returns the value of the named header field.
		 * <p>
		 * If called on a connection that sets the same header multiple times
		 * with possibly different values, only the last value is returned.
		 *
		 *
		 * @param   name   the name of a header field.
		 * @return  the value of the named header field, or {@code null}
		 *          if there is no such field in the header.
		 */
		public String getHeaderField(String name) {
			List<String> fields = getHeaderFields(name);

			if (fields == null || fields.isEmpty()) {
				return null;
			}

			return fields.get(fields.size() - 1);
		}

		/**
		 * Returns the value of the named header field.
		 * <p>
		 * If called on a connection that sets the same header multiple times
		 * with possibly different values, only the last value is returned.
		 *
		 *
		 * @param   name   the name of a header field.
		 * @return  the value of the named header field, or {@code null}
		 *          if there is no such field in the header.
		 */
		public List<String> getHeaderFields(String name) {
			if(headers == null) {
				return null;
			}

			return headers.get(name.toLowerCase());
		}
	}

	public static class JSONRequestBody implements RequestBody {
		public JSONRequestBody(String jsonText) { this.jsonText = jsonText; }
		public JSONRequestBody(Object ob) { this(Serialisation.gson.toJson(ob)); }

		@Override
		public byte[] getEncoded() { return jsonText.getBytes(); }
		@Override
		public String getContentType() { return JSON; }

		private final String jsonText;
	}

	public static class ByteArrayRequestBody implements RequestBody {
		public ByteArrayRequestBody(byte[] bytes, String contentType) { this.bytes = bytes; this.contentType = contentType; }

		@Override
		public byte[] getEncoded() { return bytes; }
		@Override
		public String getContentType() { return contentType; }

		private final byte[] bytes;
		private final String contentType;
	}

	/*************************
	 *     Public API
	 *************************/

	public Http(ClientOptions options, Auth auth) {
		this.options = options;
		this.auth = auth;
		this.host = options.restHost;
		this.scheme = options.tls ? "https://" : "http://";
		this.port = Defaults.getPort(options);
	}

	/**
	 * Sets host for this HTTP client
	 *
	 * @param host URL string
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Gets host for this HTTP client
	 *
	 * @return
     */
	public String getHost() {
		return host;
	}

	/**
	 * Simple HTTP GET; no auth, headers, returning response body as string
	 * @param url
	 * @return
	 * @throws AblyException
	 */
	public String getUrlString(String url) throws AblyException {
		return new String(getUrl(url));
	}

	/**
	 * Simple HTTP GET; no auth, headers, returning response body as byte[]
	 * @param url
	 * @return
	 * @throws AblyException
	 */
	public byte[] getUrl(String url) throws AblyException {
		try {
			return httpExecute(new URL(url), GET, null, null, false, new ResponseHandler<byte[]>() {
				@Override
				public byte[] handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
					return body;
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
	public <T> T getUri(String uri, Param[] headers, Param[] params, ResponseHandler<T> responseHandler) throws AblyException {
		return httpExecute(buildURL(uri, params), GET, headers, null, false, responseHandler);
	}

	/**
	 * HTTP GET for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T get(String path, Param[] headers, Param[] params, ResponseHandler<T> responseHandler) throws AblyException {
		return ablyHttpExecute(path, GET, headers, params, null, responseHandler);
	}

	/**
	 * HTTP POST for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T post(String path, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		return ablyHttpExecute(path, POST, headers, params, requestBody, responseHandler);
	}

	/**
	 * HTTP DEL for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T del(String path, Param[] headers, Param[] params, ResponseHandler<T> responseHandler) throws AblyException {
		return ablyHttpExecute(path, DELETE, headers, params, null, responseHandler);
	}

	/**************************
	 *     Internal API
	 **************************/

	private String getAuthorizationHeader(boolean renew) throws AblyException {
		if(authHeader != null && !renew) {
			return authHeader;
		}
		if(auth.getAuthMethod() == AuthMethod.basic) {
			authHeader = "Basic " + Base64Coder.encodeString(auth.getBasicCredentials());
		} else {
			auth.authorise(null, null, renew);
			authHeader = "Bearer " + auth.getTokenAuth().getEncodedToken();
		}
		return authHeader;
	}

	private void authorise(boolean renew) throws AblyException {
		getAuthorizationHeader(renew);
	}

	synchronized void dispose() {
		if(!isDisposed) {
			isDisposed = true;
		}
	}

	public void finalize() {
		dispose();
	}

	<T> T ablyHttpExecute(String path, String method, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		int retryCountRemaining = Hosts.isRestFallbackSupported(this.host)?options.httpMaxRetryCount:0;
		String hostCurrent = this.host;
		URL url;

		do {
			url = buildURL(scheme, hostCurrent, path, params);

			try {
				return httpExecute(url, method, headers, requestBody, true, responseHandler);
			} catch (AblyException.HostFailedException e) {
				retryCountRemaining--;

				if (retryCountRemaining >= 0) {
					Log.d(TAG, "Connection failed to host `" + hostCurrent + "`. Searching for new host...");
					hostCurrent = Hosts.getFallback(hostCurrent);
					Log.d(TAG, "Switched to `" + hostCurrent + "`.");
				}
			}
		} while (retryCountRemaining >= 0);

		throw AblyException.fromErrorInfo(new ErrorInfo("Connection failed; no host available", 404, 80000));
	}

	<T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, ResponseHandler<T> responseHandler) throws AblyException {
		Response response;
		HttpURLConnection conn = null;
		boolean credentialsIncluded = false;
		try {
			/* prepare connection */
			conn = (HttpURLConnection)url.openConnection();
			conn.setConnectTimeout(options.httpOpenTimeout);
			conn.setReadTimeout(options.httpRequestTimeout);
			conn.setDoInput(true);
			if(method != null) {
				conn.setRequestMethod(method);
			}
			if(withCredentials && authHeader != null) {
				credentialsIncluded = true;
				conn.setRequestProperty(AUTHORIZATION, authHeader);
			}
			boolean acceptSet = false;
			if(headers != null) {
				for(Param header: headers) {
					conn.setRequestProperty(header.key, header.value);
					if(header.key.equals(ACCEPT)) acceptSet = true;
				}
			}
			if(!acceptSet) conn.setRequestProperty(ACCEPT, JSON);

			/* send request body */
			if(requestBody != null) {
				writeRequestBody(requestBody, conn);
			}

			response = readResponse(conn);
		} catch(IOException ioe) {
			throw AblyException.fromThrowable(ioe);
		} finally {
			if(conn != null)
				conn.disconnect();
		}

		if (response.statusCode == 0) {
			return null;
		}

		if (response.statusCode >=500 && response.statusCode <= 504) {
			throw AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode));
		}

		if(response.statusCode < 200 || response.statusCode >= 300) {
			/* get any in-body error details */
			ErrorInfo error = null;
			if(response.body != null && response.body.length > 0) {
				ErrorResponse errorResponse = ErrorResponse.fromJSON(new String(response.body));
				if(errorResponse != null) {
					error = errorResponse.error;
				}
			}

			/* handle error details in header instead of body */
			if(error == null) {
				String errorCodeHeader = conn.getHeaderField("X-Ably-ErrorCode");
				String errorMessageHeader = conn.getHeaderField("X-Ably-ErrorMessage");
				if(errorCodeHeader != null) {
					try {
						error = new ErrorInfo(errorMessageHeader, response.statusCode, Integer.parseInt(errorCodeHeader));
					} catch(NumberFormatException e) {}
				}
			}

			/* handle www-authenticate */
			if(response.statusCode == 401) {
				String wwwAuthHeader = response.getHeaderField(WWW_AUTHENTICATE);
				if(wwwAuthHeader != null) {
					boolean stale = (wwwAuthHeader.indexOf("stale") > -1) || (error != null && error.code == 40140);
					if(withCredentials && (stale || !credentialsIncluded)) {
						/* retry the request with credentials, renewed if necessary */
						authorise(stale);
						return httpExecute(url, method, headers, requestBody, withCredentials, responseHandler);
					}
				}
			}
			if(error != null) {
				Log.e(TAG, "Error response from server: " + error);
				throw AblyException.fromErrorInfo(error);
			} else {
				Log.e(TAG, "Error response from server: statusCode = " + response.statusCode + "; statusLine = " + response.statusLine);
				throw AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode));
			}
		}

		if(responseHandler == null) {
			return null;
		}

		List<String> linkHeaders = response.getHeaderFields(LINK);
		return responseHandler.handleResponse(response.statusCode, response.contentType, linkHeaders, response.body);
	}

	private void writeRequestBody(RequestBody requestBody, HttpURLConnection conn) throws IOException {
		conn.setDoOutput(true);
		byte[] body = requestBody.getEncoded();
		int length = body.length;
		conn.setFixedLengthStreamingMode(length);
		conn.setRequestProperty(CONTENT_TYPE, requestBody.getContentType());
		conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(length));
		OutputStream os = conn.getOutputStream();
		os.write(body);
	}

	private Response readResponse(HttpURLConnection connection) throws IOException {
		Response response = new Response();
		response.statusCode = connection.getResponseCode();
		response.statusLine = connection.getResponseMessage();

		/* Store all header field names in lower-case to eliminate case insensitivity */
		Map<String, List<String>> caseSensitiveHeaders = connection.getHeaderFields();
		response.headers = new HashMap<>(caseSensitiveHeaders.size(), 1f);

		for (Map.Entry<String, List<String>> entry : caseSensitiveHeaders.entrySet()) {
			if (entry.getKey() != null) {
				response.headers.put(entry.getKey().toLowerCase(), entry.getValue());
			}
		}

		if(response.statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
			return response;
		}

		response.contentType = connection.getContentType();
		response.contentLength = connection.getContentLength();

		int successStatusCode = (POST.equals(connection.getRequestMethod())) ? HttpURLConnection.HTTP_CREATED : HttpURLConnection.HTTP_OK;
		InputStream is = (response.statusCode == successStatusCode) ? connection.getInputStream() : connection.getErrorStream();

		try {
			response.body = readInputStream(is, response.contentLength);
		} catch (NullPointerException e) {
			/* nothing to read */
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {}
			}
		}

		return response;
	}

	private byte[] readInputStream(InputStream inputStream, int bytes) throws IOException {
		/* If there is nothing to read */
		if (inputStream == null) {
			throw new NullPointerException("inputStream == null");
		}

		int bytesRead = 0;

		if (bytes == -1) {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[4 * 1024];
			while((bytesRead = inputStream.read(buffer)) > -1) {
				outputStream.write(buffer, 0, bytesRead);
			}

			return outputStream.toByteArray();
		}
		else {
			int idx = 0;
			byte[] output = new byte[bytes];
			while((bytesRead = inputStream.read(output,  idx, bytes - idx)) > -1) {
				idx += bytesRead;
			}

			return output;
		}
	}

	private void appendParams(StringBuilder uri, Param[] params) {
		if(params != null && params.length > 0) {
			uri.append('?').append(params[0].key).append('=').append(params[0].value);
			for(int i = 1; i < params.length; i++) {
				uri.append('&').append(params[i].key).append('=').append(params[i].value);
			}
		}
	}

	private URL buildURL(String scheme, String host, String path, Param[] params) {
		StringBuilder builder = new StringBuilder(scheme).append(host).append(':').append(port).append(path);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}

	private URL buildURL(String uri, Param[] params) {
		StringBuilder builder = new StringBuilder(uri);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}


	/*************************
	 *     Private state
	 *************************/

	static {
		/* if on Android, check version */
		Field androidVersionField = null;
		int androidVersion = 0;
		try {
	        androidVersionField = Class.forName("android.os.Build$VERSION").getField("SDK_INT");
	        androidVersion = androidVersionField.getInt(androidVersionField);
	    } catch (Exception e) {}
		if(androidVersionField != null && androidVersion < 8) {
			/* HTTP connection reuse which was buggy pre-froyo */
			System.setProperty("http.keepAlive", "false");
		}
	}

	private final ClientOptions options;
	private final Auth auth;
	private final String scheme;
	private String host;
	private final int port;
	private String authHeader;
	private boolean isDisposed;

	private static final String TAG               = Http.class.getName();
	private static final String LINK              = "Link";
	private static final String ACCEPT            = "Accept";
	private static final String CONTENT_TYPE      = "Content-Type";
	private static final String CONTENT_LENGTH    = "Content-Length";
	private static final String JSON              = "application/json";
	private static final String WWW_AUTHENTICATE  = "WWW-Authenticate";
	private static final String AUTHORIZATION     = "Authorization";

	static final String GET                      = "GET";
	static final String POST                     = "POST";
	static final String DELETE                   = "DELETE";
}
