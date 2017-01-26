package io.ably.lib.http;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParseException;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawHttpListener;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ErrorResponse;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProxyOptions;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

/**
 * Http
 * Support class for HTTP REST operations supporting
 * host fallback in the case of host unavailability
 * and authentication.
 * Internal
 *
 */
public class Http {
	public static final String GET    = "GET";
	public static final String PUT    = "PUT";
	public static final String POST   = "POST";
	public static final String DELETE = "DELETE";

	/**
	 * Interface for an entity that performs type-specific processing on an http response
	 * @param <T>
	 */
	public interface ResponseHandler<T> {
		T handleResponse(Response response, ErrorInfo error) throws AblyException;
	}

	/**
	 * Interface for an entity that performs type-specific processing on an http response body
	 * @param <T>
	 */
	public interface BodyHandler<T> {
		T[] handleResponseBody(String contentType, byte[] body) throws AblyException;
	}

	/**
	 * Interface for an entity that supplies an http request body
	 * @param <T>
	 */
	public interface RequestBody {
		byte[] getEncoded();
		String getContentType();
	}

	/**
	 * Exception signifying that an http request failed with a WWW-Authenticate response
	 */
	public static class AuthRequiredException extends AblyException {
		private static final long serialVersionUID = 1L;
		public AuthRequiredException(Throwable throwable, ErrorInfo reason) {
			super(throwable, reason);
		}
		public boolean expired;
		public Map<HttpAuth.Type, String> authChallenge;
		public Map<HttpAuth.Type, String> proxyAuthChallenge;
	}

	/**
	 * A type encapsulating an http response
	 */
	public static class Response {
		public int statusCode;
		public String statusLine;
		public Map<String,List<String>> headers;
		public String contentType;
		public int contentLength;
		public byte[] body;

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

	/**
	 * A RequestBody wrapping a JSON-serialisable object
	 */
	public static class JsonRequestBody implements RequestBody {
		public JsonRequestBody(String jsonText) { this.jsonText = jsonText; }
		public JsonRequestBody(Object ob) { this(Serialisation.gson.toJson(ob)); }

		@Override
		public byte[] getEncoded() { return (bytes != null) ? bytes : (bytes = jsonText.getBytes(StandardCharsets.UTF_8)); }
		@Override
		public String getContentType() { return JSON; }

		private final String jsonText;
		private byte[] bytes;
	}

	/**
	 * A RequestBody wrapping a byte array
	 */
	public static class ByteArrayRequestBody implements RequestBody {
		public ByteArrayRequestBody(byte[] bytes, String contentType) { this.bytes = bytes; this.contentType = contentType; }

		@Override
		public byte[] getEncoded() { return bytes; }
		@Override
		public String getContentType() { return contentType; }

		private final byte[] bytes;
		private final String contentType;
	}

	public static class FormRequestBody implements RequestBody {
		public FormRequestBody(Param[] formData) { this.formData = formData; }

		@Override
		public byte[] getEncoded() {
			try {
				StringBuilder body = new StringBuilder();
				for (int i = 0; i < formData.length; i++) {
					if (i != 0)
						body.append('&');
					body.append(URLEncoder.encode(formData[i].key, "UTF-8"));
					body.append('=');
					body.append(URLEncoder.encode(formData[i].value, "UTF-8"));
				}
				return body.toString().getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				return new byte[]{};
			}
		}

		@Override
		public String getContentType() {
			return FORM_ENCODING;
		}

		private Param[] formData;
	}

	/*************************
	 *     Public API
	 *************************/

	public Http(ClientOptions options, Auth auth) throws AblyException {
		this.options = options;
		this.auth = auth;
		this.scheme = options.tls ? "https://" : "http://";
		this.port = Defaults.getPort(options);
		this.hosts = new Hosts(options.restHost, Defaults.HOST_REST, options);

		this.proxyOptions = options.proxy;
		if(proxyOptions != null) {
			String proxyHost = proxyOptions.host;
			if(proxyHost == null) { throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy host", 40000, 400)); }
			int proxyPort = proxyOptions.port;
			if(proxyPort == 0) { throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy port", 40000, 400)); }
			this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
			String proxyUser = proxyOptions.username;
			if(proxyUser != null) {
				String proxyPassword = proxyOptions.password;
				if(proxyPassword == null) { throw AblyException.fromErrorInfo(new ErrorInfo("Unable to configure proxy without proxy password", 40000, 400)); }
				proxyAuth = new HttpAuth(proxyUser, proxyPassword, proxyOptions.prefAuthType);
			}
		}
	}

	/**
	 * Sets host for this HTTP client
	 *
	 * @param host URL string
	 */
	public void setHost(String host) {
		hosts.setHost(host);
	}

	/**
	 * Gets host for this HTTP client
	 *
	 * @return
     */
	public String getHost() {
		return hosts.getHost();
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
			return httpExecute(new URL(url), GET, null, null, new ResponseHandler<byte[]>() {
				@Override
				public byte[] handleResponse(Response response, ErrorInfo error) throws AblyException {
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
	public <T> T getUri(String uri, Param[] headers, Param[] params, ResponseHandler<T> responseHandler) throws AblyException {
		return httpExecute(buildURL(uri, params), GET, headers, null, responseHandler);
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
	 * HTTP PUT for Ably host, with fallbacks
	 * @param path
	 * @param headers
	 * @param params
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T put(String path, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		return ablyHttpExecute(path, PUT, headers, params, requestBody, responseHandler);
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

	/**
	 * HTTP request for Ably host, with fallbacks
	 * @param path
	 * @param method
	 * @param headers
	 * @param params
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T exec(String path, String method, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		return ablyHttpExecute(path, method, headers, params, requestBody, responseHandler);
	}

	/**************************
	 *     Internal API
	 **************************/

	/**
	 * Get the Authorization header, forcing the creation of a new token if requested
	 * @param renew
	 * @return
	 * @throws AblyException
	 */
	private String getAuthorizationHeader(boolean renew) throws AblyException {
		if(authHeader != null && !renew) {
			return authHeader;
		}
		if(auth.getAuthMethod() == AuthMethod.basic) {
			authHeader = "Basic " + Base64Coder.encodeString(auth.getBasicCredentials());
		} else {
			if (renew) {
				auth.renew();
			} else {
				auth.assertValidToken();
			}
			authHeader = "Bearer " + auth.getEncodedToken();
		}
		return authHeader;
	}

	void authorize(boolean renew) throws AblyException {
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

	/**
	 * Make a synchronous HTTP request to an Ably endpoint, using the Ably auth credentials and fallback hosts if necessary
	 * @param path
	 * @param method
	 * @param headers
	 * @param params
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T ablyHttpExecute(String path, String method, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		String candidateHost = getHost();
		int retryCountRemaining = hosts.getFallback(candidateHost) != null ? options.httpMaxRetryCount : 0;
		URL url;

		while(true) {
			url = buildURL(scheme, candidateHost, port, path, params);
			try {
				return httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, true);
			} catch (AblyException.HostFailedException e) {
				if(--retryCountRemaining < 0)
					throw e; /* reached httpMaxRetryCount */
				Log.d(TAG, "Connection failed to host `" + candidateHost + "`. Searching for new host...");
				candidateHost = hosts.getFallback(candidateHost);
				if (candidateHost == null)
					throw e; /* run out of fallback hosts */
				Log.d(TAG, "Switched to `" + candidateHost + "`.");
			}
		}
	}

	/**
	 * Make a synchronous HTTP request to non-Ably endpoint, specified by URL and using the configured proxy, if any
	 * @param url
	 * @param method
	 * @param headers
	 * @param requestBody
	 * @param withCredentials
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		return httpExecuteWithRetry(url, method, headers, requestBody, responseHandler, false);
	}

	/**
	 * Make a synchronous HTTP request specified by URL and proxy
	 * @param url
	 * @param proxy
	 * @param method
	 * @param headers
	 * @param requestBody
	 * @param withCredentials
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T httpExecute(URL url, Proxy proxy, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, ResponseHandler<T> responseHandler) throws AblyException {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection)url.openConnection(proxy);
			boolean withProxyCredentials = (proxy != Proxy.NO_PROXY) && (proxyAuth != null);
			return httpExecute(conn, method, headers, requestBody, withCredentials, withProxyCredentials, responseHandler);
		} catch(IOException ioe) {
			throw AblyException.fromThrowable(ioe);
		} finally {
			if(conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Make a synchronous HTTP request with a given HttpURLConnection
	 * @param conn
	 * @param method
	 * @param headers
	 * @param requestBody
	 * @param withCredentials
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	<T> T httpExecute(HttpURLConnection conn, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, boolean withProxyCredentials, ResponseHandler<T> responseHandler) throws AblyException {
		Response response;
		boolean credentialsIncluded = false;
		RawHttpListener rawHttpListener = null;
		String id = null;
		try {
			/* prepare connection */
			conn.setRequestMethod(method);
			conn.setConnectTimeout(options.httpOpenTimeout);
			conn.setReadTimeout(options.httpRequestTimeout);
			conn.setDoInput(true);

			if(withCredentials && authHeader != null) {
				conn.setRequestProperty(AUTHORIZATION, authHeader);
				credentialsIncluded = true;
			}
			if(withProxyCredentials && proxyAuth.hasChallenge()) {
				byte[] encodedRequestBody = (requestBody != null) ? requestBody.getEncoded() : null;
				String proxyAuthorizationHeader = proxyAuth.getAuthorizationHeader(method, conn.getURL().getPath(), encodedRequestBody);
				conn.setRequestProperty(PROXY_AUTHORIZATION, proxyAuthorizationHeader);
			}
			boolean acceptSet = false;
			if(headers != null) {
				for(Param header: headers) {
					conn.setRequestProperty(header.key, header.value);
					if(header.key.equals(ACCEPT)) { acceptSet = true; }
				}
			}
			if(!acceptSet) { conn.setRequestProperty(ACCEPT, JSON); }

			/* pass required headers */
			conn.setRequestProperty(Defaults.ABLY_VERSION_HEADER, Defaults.ABLY_VERSION);
			conn.setRequestProperty(Defaults.ABLY_LIB_HEADER, Defaults.ABLY_LIB_VERSION);

			/* prepare request body */
			byte[] body = null;
			if(requestBody != null) {
				body = prepareRequestBody(requestBody, conn);
				if (Log.level <= Log.VERBOSE)
					Log.v(TAG, System.lineSeparator() + new String(body));
			}

			/* log raw request details */
			Map<String, List<String>> requestProperties = conn.getRequestProperties();
			if (Log.level <= Log.VERBOSE) {
				Log.v(TAG, "HTTP request: " + conn.getURL() + " " + method);
				if (credentialsIncluded)
					Log.v(TAG, "  " + AUTHORIZATION + ": " + authHeader);
				for (Map.Entry<String, List<String>> entry : requestProperties.entrySet())
					for (String val : entry.getValue())
						Log.v(TAG, "  " + entry.getKey() + ": " + val);
			}

			if(options instanceof DebugOptions) {
				rawHttpListener = ((DebugOptions)options).httpListener;
				if(rawHttpListener != null) {
					id = String.valueOf(Math.random()).substring(2);
					rawHttpListener.onRawHttpRequest(id, conn, method, (credentialsIncluded ? authHeader : null), requestProperties, requestBody);
				}
			}

			/* send request body */
			if(requestBody != null) {
				writeRequestBody(body, conn);
			}
			response = readResponse(conn);
			if(rawHttpListener != null) {
				rawHttpListener.onRawHttpResponse(id, response);
			}
		} catch(IOException ioe) {
			if(rawHttpListener != null) {
				rawHttpListener.onRawHttpException(id, ioe);
			}
			throw AblyException.fromThrowable(ioe);
		}

		return handleResponse(conn, credentialsIncluded, response, responseHandler);
	}

	/**
	 * Make a synchronous HTTP request specified by URL and proxy, retrying if necessary on WWW-Authenticate
	 * @param url
	 * @param proxy
	 * @param method
	 * @param headers
	 * @param requestBody
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	public <T> T httpExecuteWithRetry(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler, boolean allowAblyAuth) throws AblyException {
		boolean authPending = true, renewPending = true, proxyAuthPending = true;
		while(true) {
			try {
				return httpExecute(url, getProxy(url), method, headers, requestBody, true, responseHandler);
			} catch(AuthRequiredException are) {
				if(are.authChallenge != null && allowAblyAuth) {
					if(authPending) {
						authorize(false);
						authPending = false;
						continue;
					}
					if(are.expired && renewPending) {
						authorize(true);
						renewPending = false;
						continue;
					}
				}
				if(are.proxyAuthChallenge != null && proxyAuthPending && proxyAuth != null) {
					proxyAuth.processAuthenticateHeaders(are.proxyAuthChallenge);
					proxyAuthPending = false;
					continue;
				}
				throw are;
			}
		}
	}

	/**
	 * Handle HTTP response
	 * @param conn
	 * @param credentialsIncluded
	 * @param response
	 * @param responseHandler
	 * @return
	 * @throws AblyException
	 */
	private <T> T handleResponse(HttpURLConnection conn, boolean credentialsIncluded, Response response, ResponseHandler<T> responseHandler) throws AblyException {
		if (response.statusCode == 0) {
			return null;
		}

		if (response.statusCode >=500 && response.statusCode <= 504) {
			ErrorInfo error = ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode);
			throw AblyException.fromErrorInfo(error);
		}

		if(response.statusCode < 200 || response.statusCode >= 300) {
			/* get any in-body error details */
			ErrorInfo error = null;
			if(response.body != null && response.body.length > 0) {
				String bodyText = new String(response.body);
				try {
					ErrorResponse errorResponse = ErrorResponse.fromJSON(bodyText);
					if(errorResponse != null) {
						error = errorResponse.error;
					}
				} catch(JsonParseException jse) {
					/* error pages aren't necessarily going to satisfy our Accept criteria ... */
					System.err.println("Error message in unexpected format: " + bodyText);
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
				boolean stale = (error != null && error.code == 40140);
				List<String> wwwAuthHeaders = response.getHeaderFields(WWW_AUTHENTICATE);
				if(wwwAuthHeaders != null && wwwAuthHeaders.size() > 0) {
					Map<HttpAuth.Type, String> headersByType = HttpAuth.sortAuthenticateHeaders(wwwAuthHeaders);
					String tokenHeader = headersByType.get(HttpAuth.Type.X_ABLY_TOKEN);
					if(tokenHeader != null) { stale |= (tokenHeader.indexOf("stale") > -1); }
					AuthRequiredException exception = new AuthRequiredException(null, error);
					exception.authChallenge = headersByType;
					if(stale) {
						exception.expired = true;
						throw exception;
					}
					if(!credentialsIncluded) {
						throw exception;
					}
				}
			}
			/* handle proxy-authenticate */
			if(response.statusCode == 407) {
				List<String> proxyAuthHeaders = response.getHeaderFields(PROXY_AUTHENTICATE);
				if(proxyAuthHeaders != null && proxyAuthHeaders.size() > 0) {
					AuthRequiredException exception = new AuthRequiredException(null, error);
					exception.proxyAuthChallenge = HttpAuth.sortAuthenticateHeaders(proxyAuthHeaders);
					throw exception;
				}
			}
			if(error == null) {
				Log.e(TAG, "Error response from server: statusCode = " + response.statusCode + "; statusLine = " + response.statusLine);
				error = ErrorInfo.fromResponseStatus(response.statusLine, response.statusCode);
			} else {
				Log.e(TAG, "Error response from server: " + error);
			}
			if(responseHandler != null) {
				return responseHandler.handleResponse(response, error);
			}
			throw AblyException.fromErrorInfo(error);
		}

		if(responseHandler != null) {
			return responseHandler.handleResponse(response, null);
		}

		return null;
	}

	/**
	 * Emit the request body for an HTTP request
	 * @param requestBody
	 * @param conn
	 * @return body
	 * @throws IOException
	 */
	private byte[] prepareRequestBody(RequestBody requestBody, HttpURLConnection conn) throws IOException {
		conn.setDoOutput(true);
		byte[] body = requestBody.getEncoded();
		int length = body.length;
		conn.setFixedLengthStreamingMode(length);
		conn.setRequestProperty(CONTENT_TYPE, requestBody.getContentType());
		conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(length));
		return body;
	}

	private void writeRequestBody(byte[] body, HttpURLConnection conn) throws IOException {
		OutputStream os = conn.getOutputStream();
		os.write(body);
	}

	/**
	 * Read the response for an HTTP request
	 * @param connection
	 * @return
	 * @throws IOException
	 */
	private Response readResponse(HttpURLConnection connection) throws IOException {
		Response response = new Response();
		response.statusCode = connection.getResponseCode();
		response.statusLine = connection.getResponseMessage();

		/* Store all header field names in lower-case to eliminate case insensitivity */
		Log.v(TAG, "HTTP response:");
		Map<String, List<String>> caseSensitiveHeaders = connection.getHeaderFields();
		response.headers = new HashMap<>(caseSensitiveHeaders.size(), 1f);

		for (Map.Entry<String, List<String>> entry : caseSensitiveHeaders.entrySet()) {
			if (entry.getKey() != null) {
				response.headers.put(entry.getKey().toLowerCase(), entry.getValue());
				if (Log.level <= Log.VERBOSE)
					for (String val : entry.getValue())
						Log.v(TAG, entry.getKey() + ": " + val);
			}
		}

		if(response.statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
			return response;
		}

		response.contentType = connection.getContentType();
		response.contentLength = connection.getContentLength();

		InputStream is = null;
		try {
			is = connection.getInputStream();
		} catch (Throwable e) {}
		if (is == null)
			is = connection.getErrorStream();

		try {
			response.body = readInputStream(is, response.contentLength);
			Log.v(TAG, System.lineSeparator() + new String(response.body));
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

	private static void appendParams(StringBuilder uri, Param[] params) {
		if(params != null && params.length > 0) {
			uri.append('?').append(params[0].key).append('=').append(params[0].value);
			for(int i = 1; i < params.length; i++) {
				uri.append('&').append(params[i].key).append('=').append(params[i].value);
			}
		}
	}

	static URL buildURL(String scheme, String host, int port, String path, Param[] params) {
		StringBuilder builder = new StringBuilder(scheme).append(host).append(':').append(port).append(path);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}

	static URL buildURL(String uri, Param[] params) {
		StringBuilder builder = new StringBuilder(uri);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}

	Proxy getProxy(URL url) {
		String host = url.getHost();
		return getProxy(host);
	}

	private Proxy getProxy(String host) {
		if(proxyOptions != null) {
			String[] nonProxyHosts = proxyOptions.nonProxyHosts;
			if(nonProxyHosts != null) {
				for(String nonProxyHostPattern : nonProxyHosts) {
					if(host.matches(nonProxyHostPattern)) {
						return null;
					}
				}
			}
		}
		return proxy;
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

	final String scheme;
	final int port;
	final ClientOptions options;
	final Hosts hosts;

	private final Auth auth;
	private String authHeader;
	private final ProxyOptions proxyOptions;
	private HttpAuth proxyAuth;
	private Proxy proxy = Proxy.NO_PROXY;
	private boolean isDisposed;

	private static final String TAG                 = Http.class.getName();
	private static final String ACCEPT              = "Accept";
	private static final String CONTENT_TYPE        = "Content-Type";
	private static final String CONTENT_LENGTH      = "Content-Length";
	private static final String JSON                = "application/json";
	private static final String FORM_ENCODING       = "application/x-www-form-urlencoded";
	private static final String WWW_AUTHENTICATE    = "WWW-Authenticate";
	private static final String PROXY_AUTHENTICATE  = "Proxy-Authenticate";
	private static final String AUTHORIZATION       = "Authorization";
	private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
	static final String         LINK                = "Link";
}
