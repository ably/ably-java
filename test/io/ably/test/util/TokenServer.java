/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package io.ably.test.util;

import io.ably.rest.AblyRest;
import io.ably.rest.Auth.TokenDetails;
import io.ably.rest.Auth.TokenParams;
import io.ably.types.AblyException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Basic, yet fully functional and spec compliant, HTTP/1.1 file server.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP file server.
 *
 *
 */
public class TokenServer {

	public TokenServer(AblyRest ably, int port) {
		this.ably = ably;
		this.port = port;
	}

	public void start() throws IOException {
		listenerThread = new RequestListenerThread(port);
		listenerThread.setDaemon(false);
		listenerThread.start();
	}

	public void stop() {
		if(listenerThread != null) {
			listenerThread.destroy();
			listenerThread = null;
		}
	}

	private class TokenRequestHandler implements HttpRequestHandler {

		public TokenRequestHandler() {
			super();
		}

		@Override
		public void handle(
				final HttpRequest request,
				final HttpResponse response,
				final HttpContext context) throws HttpException, IOException {

			String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
			if (!method.equals("GET")) {
				throw new MethodNotSupportedException(method + " method not supported");
			}

			URI targetUri = null;
			try {
				targetUri = new URI(request.getRequestLine().getUri());
			} catch (URISyntaxException e) {}

			String target = URLDecoder.decode(request.getRequestLine().getUri(), "UTF-8");
			List<NameValuePair> params = URLEncodedUtils.parse(targetUri, "UTF-8");

			if(target.startsWith("/get-token")) {
				TokenParams tokenParams = params2TokenParams(params);
				try {
					TokenDetails token = ably.auth.requestToken(null, tokenParams);
					response.setStatusCode(HttpStatus.SC_OK);
					response.setEntity(json2Entity(token.asJSON()));
				} catch (AblyException e) {
					e.printStackTrace();
					int statusCode = e.errorInfo.statusCode;
					response.setStatusCode(statusCode);
					response.setEntity(error2Entity(statusCode, null));
				} catch (JSONException e) {
					e.printStackTrace();
					response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
					response.setEntity(error2Entity(HttpStatus.SC_BAD_REQUEST, null));
				}
			}
			else if(target.startsWith("/get-token-request")) {
				TokenParams tokenParams = params2TokenParams(params);
				try {
					TokenParams tokenRequest = ably.auth.createTokenRequest(null, tokenParams);
					response.setStatusCode(HttpStatus.SC_OK);
					response.setEntity(json2Entity(tokenRequest.asJSON()));
				} catch (AblyException e) {
					e.printStackTrace();
					int statusCode = e.errorInfo.statusCode;
					response.setStatusCode(statusCode);
					response.setEntity(error2Entity(statusCode, null));
				}
			}
			else if(target.startsWith("/echo-params")) {
				response.setStatusCode(HttpStatus.SC_NOT_FOUND);
				response.setEntity(error2Entity(HttpStatus.SC_NOT_FOUND, params2Json(params)));
			}
			else if(target.startsWith("/echo-headers")) {
				response.setStatusCode(HttpStatus.SC_NOT_FOUND);
				response.setEntity(error2Entity(HttpStatus.SC_NOT_FOUND, headers2Json(request.getAllHeaders())));
			}
			else if(target.startsWith("/404")) {
				response.setStatusCode(HttpStatus.SC_NOT_FOUND);
				response.setEntity(error2Entity(HttpStatus.SC_NOT_FOUND, null));
			}
		}
	}

	private class RequestListenerThread extends Thread {

		private final ServerSocket serversocket;
		private final HttpParams params;
		private final HttpService httpService;

		public RequestListenerThread(int port) throws IOException {
			this.serversocket = new ServerSocket(port);
			this.params = new SyncBasicHttpParams();
			this.params
			.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
			.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
			.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
			.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
			.setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

			// Set up the HTTP protocol processor
			HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
					new ResponseDate(),
					new ResponseServer(),
					new ResponseContent(),
					new ResponseConnControl()
			});

			// Set up request handlers
			HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
			reqistry.register("*", new TokenRequestHandler());

			// Set up the HTTP service
			this.httpService = new HttpService(
					httpproc,
					new DefaultConnectionReuseStrategy(),
					new DefaultHttpResponseFactory(),
					reqistry,
					this.params);
		}

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				try {
					// Set up HTTP connection
					Socket socket = this.serversocket.accept();
					DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
					conn.bind(socket, this.params);

					// Start worker thread
					Thread t = new WorkerThread(this.httpService, conn);
					t.setDaemon(true);
					t.start();
				} catch (InterruptedIOException ex) {
					break;
				} catch (IOException e) {
					System.err.println("I/O error initialising connection thread: "
							+ e.getMessage());
					break;
				}
			}
		}
	}

	static class WorkerThread extends Thread {

		private final HttpService httpservice;
		private final HttpServerConnection conn;

		public WorkerThread(
				final HttpService httpservice,
				final HttpServerConnection conn) {
			super();
			this.httpservice = httpservice;
			this.conn = conn;
		}

		@Override
		public void run() {
			HttpContext context = new BasicHttpContext(null);
			try {
				while (!Thread.interrupted() && this.conn.isOpen()) {
					this.httpservice.handleRequest(this.conn, context);
				}
			} catch (ConnectionClosedException ex) {
				/* we don't need to report this
				System.err.println("Client closed connection"); */
			} catch (IOException ex) {
				/* each thread will end up here with a read timeout
				 System.err.println("I/O error: " + ex.getMessage()); */
			} catch (HttpException ex) {
				System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
			} finally {
				try {
					this.conn.shutdown();
				} catch (IOException ignore) {}
			}
		}
	}

	private static HashMap<String, String> params2HashMap(List<NameValuePair> params) {
		HashMap<String, String> map = new HashMap<String, String>();
		for(NameValuePair nameValuePair : params) {
			map.put(nameValuePair.getName(), nameValuePair.getValue());
		}
		return map;
	}

	private static JSONObject params2Json(List<NameValuePair> params) {
		JSONObject json = new JSONObject();
		for(NameValuePair nameValuePair : params) {
			try {
				json.put(nameValuePair.getName(), nameValuePair.getValue());
			} catch (JSONException e) {}
		}
		return json;
	}

	private static JSONObject headers2Json(Header[] headers) {
		JSONObject json = new JSONObject();
		for(Header header : headers) {
			try {
				json.put(header.getName(), header.getValue());
			} catch (JSONException e) {}
		}
		return json;
	}

	private static TokenParams params2TokenParams(List<NameValuePair> params) {
		HashMap<String, String> map = params2HashMap(params);
		TokenParams tokenParams = new TokenParams();
		if(map.containsKey("id"))
			tokenParams.id = map.get("id");
		if(map.containsKey("client_id"))
			tokenParams.client_id = map.get("client_id");
		if(map.containsKey("timestamp"))
			tokenParams.timestamp = Long.valueOf(map.get("timestamp"));
		if(map.containsKey("ttl"))
			tokenParams.ttl = Long.valueOf(map.get("ttl"));
		if(map.containsKey("capability"))
			tokenParams.capability = map.get("capability");
		return tokenParams;
	}

	private static HttpEntity json2Entity(JSONObject json) {
		return new StringEntity(json.toString(), ContentType.create("application/json", "UTF-8"));
	}

	private static HttpEntity error2Entity(int statusCode, JSONObject err) {
		try {
			if(err == null) err = new JSONObject();
			err.put("statusCode", statusCode);

			JSONObject json = new JSONObject();
			json.put("error", err);

			return new StringEntity(json.toString(), ContentType.create("application/json", "UTF-8"));
		}
		catch (JSONException e) {}
		return null;
	}

	private final AblyRest ably;
	private final int port;
	private RequestListenerThread listenerThread;
}