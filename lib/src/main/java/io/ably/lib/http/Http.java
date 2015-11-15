package io.ably.lib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.transport.Defaults;
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
		public T handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException;
	}

	public interface BodyHandler<T> {
		public T[] handleResponseBody(String contentType, byte[] body) throws AblyException;
	}

	public interface RequestBody {
		public byte[] getEncoded();
		public String getContentType();
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

	public Http(AblyRest ably, ClientOptions options) {
		this.ably = ably;
		this.scheme = options.tls ? "https://" : "http://";
		this.port = Defaults.getPort(options);
	}

	private String getPrefHost() {
		if(ably instanceof AblyRealtime) {
			Connection connection = ((AblyRealtime)ably).connection;
			if(connection.state == ConnectionState.connected)
				return connection.connectionManager.getHost();
		}
		return Defaults.getHost(ably.options);
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
			throw new AblyException(ioe);
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
		Auth auth = ably.auth;
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

	private static class HostFailedException extends AblyException {
		private static final long serialVersionUID = 1L;
		public HostFailedException(Throwable cause) {
			super(cause);
		}		
		private static AblyException checkFor(Throwable t) {
			if(t instanceof ConnectException || t instanceof UnknownHostException || t instanceof NoRouteToHostException)
				return new HostFailedException(t);
			return AblyException.fromThrowable(t);
		}
	}

	<T> T ablyHttpExecute(String path, String method, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
		try {
			URL url = buildURL(scheme, getPrefHost(), path, params);
			return httpExecute(url, method, headers, requestBody, true, responseHandler);
		} catch(HostFailedException bhe) {
			/* one of the exceptions occurred that signifies a problem reaching the host */
			String[] fallbackHosts = Defaults.getFallbackHosts(ably.options);
			if(fallbackHosts != null) {
				for(String host : fallbackHosts) {
					try {
						URL url = buildURL(scheme, host, path, params);
						return httpExecute(url, method, headers, requestBody, true, responseHandler);
					} catch(HostFailedException bhe2) {}
				}
			}
			throw new AblyException("Connection failed; no host available", 404, 80000);
		}
	}

	<T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, boolean withCredentials, ResponseHandler<T> responseHandler) throws AblyException {
		HttpURLConnection conn = null;
		InputStream is = null;
		int statusCode = 0;
		String statusLine = null;
		String contentType = null;
		byte[] responseBody = null;
		List<String> linkHeaders = null;
		boolean credentialsIncluded = false;
		try {

			/* prepare connection */
			conn = (HttpURLConnection)url.openConnection();
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
				conn.setDoOutput(true);
				byte[] body = requestBody.getEncoded();
				int length = body.length;
				conn.setFixedLengthStreamingMode(length);
				conn.setRequestProperty(CONTENT_TYPE, requestBody.getContentType());
				conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(length));
				OutputStream os = conn.getOutputStream();
				os.write(body);
			}

			/* get response */
			statusCode = conn.getResponseCode();
			statusLine = conn.getResponseMessage();
			if(statusCode != HttpURLConnection.HTTP_NO_CONTENT) {
				contentType = conn.getContentType();
				int contentLength = conn.getContentLength();
				int successStatusCode = (method == POST) ? HttpURLConnection.HTTP_CREATED : HttpURLConnection.HTTP_OK;
				is = (statusCode == successStatusCode) ? conn.getInputStream() : conn.getErrorStream();
				if(is != null) {
					int read;
					if(contentLength == -1) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						byte[] buf = new byte[4 * 1024];
						while((read = is.read(buf)) > -1) {
							baos.write(buf, 0, read);
						}
						responseBody = baos.toByteArray();
					} else {
						int idx = 0;
						responseBody = new byte[contentLength];
						while((read = is.read(responseBody,  idx, contentLength - idx)) > -1) {
							idx += read;
						}
					}
				}
			}
		} catch(IOException ioe) {
			throw HostFailedException.checkFor(ioe);
		} finally {
			try {
				if(is != null)
					is.close();
				if(conn != null)
					conn.disconnect();
			} catch(IOException ioe) {}
		}

		if(statusCode == 0) {
			return null;
		}

		if(statusCode < 200 || statusCode >= 300) {
			/* get any in-body error details */
			ErrorInfo error = null;
			if(responseBody != null && responseBody.length > 0) {
				ErrorResponse errorResponse = ErrorResponse.fromJSON(new String(responseBody));
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
						error = new ErrorInfo(errorMessageHeader, statusCode, Integer.parseInt(errorCodeHeader));
					} catch(NumberFormatException e) {}
				}
			}

			/* handle www-authenticate */
			if(statusCode == 401) {
				String wwwAuthHeader = conn.getHeaderField(WWW_AUTHENTICATE);
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
				throw AblyException.fromError(error);
			} else {
				Log.e(TAG, "Error response from server: statusCode = " + statusCode + "; statusLine = " + statusLine);
				throw AblyException.fromResponseStatus(statusLine, statusCode);
			}
		}

		if(responseHandler == null) {
			return null;
		}

		linkHeaders = conn.getHeaderFields().get(LINK);
		return responseHandler.handleResponse(statusCode, contentType, linkHeaders, responseBody);
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

	private final AblyRest ably;
	private final String scheme;
	private final int port;
	private String authHeader;
	private boolean isDisposed;

	private static final String TAG              = Http.class.getName();
	private static final String LINK             = "Link";
	private static final String ACCEPT           = "Accept";
	private static final String CONTENT_TYPE     = "Content-Type";
	private static final String CONTENT_LENGTH   = "Content-Length";
	private static final String JSON             = "application/json";
	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String AUTHORIZATION    = "Authorization";

	static final String GET                      = "GET";
	static final String POST                     = "POST";
	static final String DELETE                   = "DELETE";
}
