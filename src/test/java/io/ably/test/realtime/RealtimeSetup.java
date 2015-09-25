package io.ably.test.realtime;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import io.ably.http.Http.JSONRequestBody;
import io.ably.http.Http.ResponseHandler;
import io.ably.http.HttpUtils;
import io.ably.rest.AblyRest;
import io.ably.test.common.Setup;
import io.ably.types.AblyException;
import io.ably.types.ClientOptions;
import io.ably.util.Serialisation;

public class RealtimeSetup {

	private static final String specFile = "src/test/resources/assets/testAppSpec.json";

	public static class TestVars extends Setup.AppSpec {
		public String restHost;
		public String realtimeHost;
		public int port;
		public int tlsPort;
		public boolean tls;

		public ClientOptions createOptions() {
			ClientOptions opts = new ClientOptions();
			fillInOptions(opts);
			return opts;
		}
		public ClientOptions createOptions(String key) throws AblyException {
			ClientOptions opts = new ClientOptions(key);
			fillInOptions(opts);
			return opts;
		}
		public void fillInOptions(ClientOptions opts) {
			opts.restHost = restHost;
			opts.realtimeHost = realtimeHost;
			opts.port = port;
			opts.tlsPort = tlsPort;
			opts.tls = tls;
		}
	}

	private static AblyRest ably;
	private static TestVars testVars;
	private static String environment;
	private static String host;
	private static String wsHost;
	private static int port;
	private static int tlsPort;

	static {
		host = System.getenv("ABLY_REST_HOST");
		if(host != null) {
			wsHost = System.getenv("ABLY_REALTIME_HOST");
			if(wsHost == null)
				wsHost = host;
		} else {
			environment = System.getenv("ABLY_ENV");
			if(environment == null)
				environment = "sandbox";

			host = environment + "-rest.ably.io";
			wsHost = environment + "-realtime.ably.io";
		}

		if(System.getenv("ABLY_PORT") != null) {
			port = Integer.valueOf(System.getenv("ABLY_PORT"));
			tlsPort = Integer.valueOf(System.getenv("ABLY_TLS_PORT"));
		} else if(host.contains("local")) {
			port = 8080;
			tlsPort = 8081;
		} else {
			/* default to connecting to sandbox or production through load balancer */
			port = 80;
			tlsPort = 443;
		}
	}

	public static synchronized TestVars getTestVars() {
		if(testVars == null) {
			if(ably == null) {
				try {
					ClientOptions opts = new ClientOptions();
					/* we need to provide an appId to keep the library happy,
					 * but we are only instancing the library to use the http
					 * convenience methods */
					opts.restHost = host;
					opts.port = port;
					opts.tlsPort = tlsPort;
					opts.tls = true;
					ably = new AblyRest(opts);
				} catch(AblyException e) {
					System.err.println("Unable to instance AblyRest: " + e);
					e.printStackTrace();
					System.exit(1);
				}
			}

			Setup.AppSpec appSpec = null;
			try {
				appSpec = (Setup.AppSpec)Helpers.loadJSON(specFile, Serialisation.jsonObjectMapper, new TypeReference<Setup.AppSpec>(){});
				appSpec.notes = "Test app; created by ably-java realtime tests; date = " + new Date().toString();
			} catch(IOException ioe) {
				System.err.println("Unable to read spec file: " + ioe);
				ioe.printStackTrace();
				System.exit(1);
			}
			try {
				testVars = (TestVars)ably.http.post("/apps", HttpUtils.defaultPostHeaders(false), null, new JSONRequestBody(appSpec, Serialisation.jsonObjectMapper), new ResponseHandler() {
					@Override
					public Object handleResponse(int statusCode, String contentType, String[] headers, byte[] body) throws AblyException {
						try {
							TestVars result = (TestVars)Serialisation.jsonObjectMapper.readValue(body, TestVars.class);
							result.restHost = host;
							result.realtimeHost = wsHost;
							result.port = port;
							result.tlsPort = tlsPort;
							result.tls = true;
							return result;
						} catch (IOException e) {
							throw new AblyException("Unexpected exception processing server response; err = " + e, 500, 50000);
						}
					}});
			} catch (AblyException ae) {
				System.err.println("Unable to create test app: " + ae);
				ae.printStackTrace();
				System.exit(1);
			} catch (JsonProcessingException jpe) {
				System.err.println("Unable to process app spec: " + jpe);
				jpe.printStackTrace();
				System.exit(1);
			}
		}
		return testVars;
	}

	public static synchronized void clearTestVars() {
		if(testVars != null) {
			try {
				ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
				opts.restHost = host;
				opts.port = port;
				opts.tlsPort = tlsPort;
				opts.tls = true;
				ably = new AblyRest(opts);
				ably.http.del("/apps/" + testVars.appId, HttpUtils.defaultGetHeaders(false), null, null);
			} catch (AblyException ae) {
				System.err.println("Unable to delete test app: " + ae);
				ae.printStackTrace();
				System.exit(1);
			}
			testVars = null;
		}
	}
}
