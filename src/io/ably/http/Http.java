package io.ably.http;

import io.ably.http.TokenAuth.TokenCredentials;
import io.ably.realtime.AblyRealtime;
import io.ably.realtime.Connection;
import io.ably.realtime.Connection.ConnectionState;
import io.ably.rest.AblyRest;
import io.ably.rest.Auth;
import io.ably.rest.Auth.AuthMethod;
import io.ably.transport.Defaults;
import io.ably.types.AblyException;
import io.ably.types.Options;
import io.ably.types.Param;
import io.ably.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * Http
 * Support class for HTTP REST operations supporting
 * host fallback in the case of host unavalilability
 * and authentication.
 * Internal
 *
 */
public class Http {

	public interface ResponseHandler {
		public Object handleResponse(int statusCode, String contentType, String[] linkHeaders, byte[] body) throws AblyException;
	}

	public interface BodyHandler<T> {
		public T[] handleResponseBody(String contentType, byte[] body) throws AblyException;
	}

	public interface RequestBody {
		public HttpEntity getEntity() throws AblyException;
	}

	public static class JSONRequestBody implements RequestBody {
		public JSONRequestBody(String jsonText) { this.jsonText = jsonText; }
		@Override
		public HttpEntity getEntity() throws AblyException {
			AbstractHttpEntity entity = new ByteArrayEntity(jsonText.getBytes());
			entity.setContentEncoding("utf-8");
			entity.setContentType("application/json");
			return entity;
		}
		private String jsonText;
	}

	public static class ByteArrayRequestBody implements RequestBody {
		public ByteArrayRequestBody(byte[] bytes) { this.bytes = bytes; }
		@Override
		public HttpEntity getEntity() throws AblyException {
			AbstractHttpEntity entity = new ByteArrayEntity(bytes);
			entity.setContentEncoding("utf-8");
			entity.setContentType("application/json");
			return entity;
		}
		private byte[] bytes;
	}

	/*************************
	 *     Public API
	 *************************/

	public void setAuth(Auth auth) {
		String prefScheme;
		if(auth.getAuthMethod() == AuthMethod.basic) {
			credentials = new UsernamePasswordCredentials(auth.getBasicCredentials());
			authHeader = BasicScheme.authenticate(credentials,"US-ASCII",false);
			prefScheme = "basic";
		} else {
			prefScheme = TokenAuth.SCHEME_NAME;
			httpClient.getAuthSchemes().register(TokenAuth.SCHEME_NAME, new TokenAuth(ably.http, auth));
			String token = auth.getTokenCredentials();
			if(token != null) {
				credentials = new TokenAuth.TokenCredentials(token);
				authHeader = TokenAuth.authenticate(credentials,"US-ASCII",false);
			}
		}
		httpClient.getParams().setParameter("http.auth.target-scheme-pref", Arrays.asList(new String[] { prefScheme }));
		if(credentials != null)
			httpClient.getCredentialsProvider().setCredentials(authScope, credentials);
	}

	public TokenCredentials setToken(String token) {
		if(token != null) {
			credentials = null;
			authHeader = null;
		} else {
			credentials = new TokenAuth.TokenCredentials(token);
			authHeader = TokenAuth.authenticate(credentials,"US-ASCII",false);
			httpClient.getCredentialsProvider().setCredentials(authScope, credentials);
		}
		return (TokenCredentials)credentials;
	}

	@SuppressWarnings("deprecation")
	public Http(AblyRest ably, Options options) {
		this.ably = ably;
		this.scheme = options.tls ? "https" : "http";
		this.port = Defaults.getPort(options);
		BasicHttpParams params = new BasicHttpParams();
		SchemeRegistry schemeRegistry = new SchemeRegistry();  
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

		HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);  
		HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);  

		ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
		httpClient = new DefaultHttpClient(cm, params);
	}

	private String getPrefHost() {
		if(ably instanceof AblyRealtime) {
			Connection connection = ((AblyRealtime)ably).connection;
			if(connection.state == ConnectionState.connected)
				return connection.connectionManager.getHost();
		}
		return Defaults.getHost(ably.options);
	}

	public String getUrlString(String url) throws AblyException {
		return new String(getUrl(url));
	}

	public byte[] getUrl(String url) throws AblyException {
		HttpGet httpGet = new HttpGet(url);
		try {
			return EntityUtils.toByteArray(httpClient.execute(httpGet).getEntity());
		} catch(IOException ioe) {
			throw new AblyException(ioe);
		}
	}

	public Object get(String path, Param[] headers, Param[] params, ResponseHandler handler) throws AblyException {
		HttpGet httpGet = new HttpGet(HttpUtils.encodeParams(path, params));
		if(headers != null)
			for(Param header : headers)
				httpGet.addHeader(new BasicHeader(header.key, header.value));
		if(authHeader != null)
			httpGet.addHeader(authHeader);
		try {
			return getForHost(getPrefHost(), httpGet, handler);
		} catch(HostFailedException bhe) {
			/* one of the exceptions occurred that signifies a problem reaching the host */
			String[] fallbackHosts = Defaults.getFallbackHosts(ably.options);
			if(fallbackHosts != null) {
				for(String host : fallbackHosts) {
					try {
						return getForHost(host, httpGet, handler);
					} catch(HostFailedException bhe2) {}
				}
			}
			throw new AblyException("Connection failed; no host available", 404, 80000);
		}
	}

	private Object getForHost(String host, HttpGet httpGet, ResponseHandler handler) throws HostFailedException, AblyException {
		try {
			return handleResponse(httpClient.execute(getHttpHost(host), httpGet, localContext), handler);
		} catch(Throwable t) {
			throw HostFailedException.checkFor(t);
		}
	}

	public Object getUri(String uri, Param[] headers, Param[] params, ResponseHandler handler) throws AblyException {
		try {
			URI parsedUri = new URI(uri);
			HttpGet httpGet = new HttpGet(HttpUtils.encodeParams(parsedUri.getPath(), params));
			if(headers != null)
				for(Param header : headers)
					httpGet.addHeader(new BasicHeader(header.key, header.value));
			return handleResponse(httpClient.execute(new HttpHost(parsedUri.getHost(), parsedUri.getPort(), parsedUri.getScheme()), httpGet, localContext), handler);
		} catch(Throwable t) {
			throw AblyException.fromThrowable(t);
		}
	}

	public Object post(String path, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler handler) throws AblyException {
		HttpPost httpPost = new HttpPost(HttpUtils.encodeParams(path, params));
		if(headers != null)
			for(Param header : headers)
				httpPost.addHeader(new BasicHeader(header.key, header.value));
		if(authHeader != null)
			httpPost.addHeader(authHeader);
		httpPost.setEntity(requestBody.getEntity());
		try {
			return postForHost(getPrefHost(), httpPost, handler);
		} catch(HostFailedException bhe) {
			/* one of the exceptions occurred that signifies a problem reaching the host */
			String[] fallbackHosts = Defaults.getFallbackHosts(ably.options);
			if(fallbackHosts != null) {
				for(String host : fallbackHosts) {
					try {
						return postForHost(host, httpPost, handler);
					} catch(HostFailedException bhe2) {}
				}
			}
			throw new AblyException("Connection failed; no host available", 404, 80000);
		}
	}

	private Object postForHost(String host, HttpPost httpPost, ResponseHandler handler) throws HostFailedException, AblyException {
		try {
			return handleResponse(httpClient.execute(getHttpHost(host), httpPost, localContext), handler);
		} catch(Throwable t) {
			throw HostFailedException.checkFor(t);
		}
	}

	public Object del(String path, Param[] headers, Param[] params, ResponseHandler handler) throws AblyException {
		HttpDelete httpDel = new HttpDelete(HttpUtils.encodeParams(path, params));
		if(headers != null)
			for(Param header : headers)
				httpDel.addHeader(new BasicHeader(header.key, header.value));
		if(authHeader != null)
			httpDel.addHeader(authHeader);
		try {
			return delForHost(getPrefHost(), httpDel, handler);
		} catch(HostFailedException bhe) {
			/* one of the exceptions occurred that signifies a problem reaching the host */
			String[] fallbackHosts = Defaults.getFallbackHosts(ably.options);
			if(fallbackHosts != null) {
				for(String host : fallbackHosts) {
					try {
						return delForHost(host, httpDel, handler);
					} catch(HostFailedException bhe2) {}
				}
			}
			throw new AblyException("Connection failed; no host available", 404, 80000);
		}
	}

	private Object delForHost(String host, HttpDelete httpDel, ResponseHandler handler) throws HostFailedException, AblyException {
		try {
			return handleResponse(httpClient.execute(getHttpHost(host), httpDel, localContext), handler);
		} catch(Throwable t) {
			throw HostFailedException.checkFor(t);
		}
	}

	/**************************
	 *     Internal API
	 **************************/

	private synchronized HttpHost getHttpHost(String host) {
		HttpHost httpHost = httpHosts.get(host);
		if(httpHost == null) {
			httpHost = new HttpHost(host, port, scheme);
			httpHosts.put(host,  httpHost);
		}
		return httpHost;
	}

	private Object handleResponse(HttpResponse response, ResponseHandler handler) throws AblyException {
		try {
			StatusLine statusLine = response.getStatusLine();
			int statusCode = statusLine.getStatusCode();
			byte[] responseBody = null;
			HttpEntity entity = response.getEntity();
			if(entity != null)
				responseBody = EntityUtils.toByteArray(entity);

			if(statusCode < 200 || statusCode >= 300) {
				if(responseBody != null && responseBody.length > 0) {
					Log.e(TAG, "Error response from server: statusCode = " + statusCode + "; responseBody = " + new String(responseBody));
					throw AblyException.fromJSON(responseBody);
				} else {
					Log.e(TAG, "Error response from server: statusCode = " + statusCode + "; statusLine = " + statusLine);
					throw AblyException.fromResponseStatus(statusLine, statusCode);
				}
			}

			if(handler == null)
				return null;

			String[] linkHeaders = null;
			Header[] headers = response.getHeaders("Link");
			if(headers != null && headers.length > 0) {
				linkHeaders = new String[headers.length];
				for(int i = 0; i <headers.length; i++)
					linkHeaders[i] = headers[i].getValue();
			}
			return handler.handleResponse(statusCode, response.getFirstHeader("Content-Type").getValue(), linkHeaders, responseBody);
		} catch (Throwable t) {
			throw AblyException.fromThrowable(t);
		}
	}

	synchronized void dispose() {
		if(!isDisposed) {
			httpClient.getConnectionManager().shutdown();
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

	/*************************
	 *     Private state
	 *************************/

	public static int CONNECTION_TIMEOUT = 2*60*1000;  
	public static int SOCKET_TIMEOUT = 2*60*1000;
	private static final AuthScope authScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT);
	private static final String TAG = Http.class.getName();

	private AblyRest ably;
	private String scheme;
	private int port;
	Credentials credentials;
	private Map<String, HttpHost> httpHosts = new HashMap<String, HttpHost>();
	private HttpContext localContext = new BasicHttpContext();
	private Header authHeader;

	AbstractHttpClient httpClient;
	private boolean isDisposed;

}
