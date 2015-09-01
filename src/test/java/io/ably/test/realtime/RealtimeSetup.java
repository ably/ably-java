package io.ably.test.realtime;

import io.ably.http.Http.JSONRequestBody;
import io.ably.http.Http.ResponseHandler;
import io.ably.http.HttpUtils;
import io.ably.rest.AblyRest;
import io.ably.types.AblyException;
import io.ably.types.ClientOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RealtimeSetup {

	private static final String specFile = "src/test/resources/assets/testAppSpec.json";

	public static class Key {
		public String keyName;
		public String keySecret;
		public String keyStr;
		public String capability;
	}
	public static class TestVars {
		public String appId;
		public Key[] keys;
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
	private static Boolean tls_env;
	private static String host;
	private static String wsHost;
	private static int port;
	private static int tlsPort;
	private static boolean tls;

	static {
		tls_env = new Boolean(System.getenv("ABLY_TLS"));
		host = System.getenv("ABLY_HOST");
		if(host == null)
			host = "sandbox-rest.ably.io";

		tls = (tls_env == null) ? true : tls_env.booleanValue();
		if(host.endsWith("rest.ably.io")) {
			/* default to connecting to staging or production through load balancer */
			port = 80;
			tlsPort = 443;
		} else {
			/* use the given host, assuming no load balancer */
			port = 8080;
			tlsPort = 8081;
		}
		wsHost = System.getenv("ABLY_WS_HOST");
		if(wsHost == null)
			wsHost = "sandbox-realtime.ably.io";
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
					opts.tls = tls;
					ably = new AblyRest(opts);
				} catch(AblyException e) {
					System.err.println("Unable to instance AblyRest: " + e);
					e.printStackTrace();
					System.exit(1);
				}
			}
			String appSpecText = null;
			try {
				FileInputStream fis = new FileInputStream(specFile);
				try {
					byte[] jsonBytes = new byte[fis.available()];
					fis.read(jsonBytes);
					appSpecText = new String(jsonBytes);
				} finally {
					fis.close();
				}
			} catch(IOException ioe) {
				System.err.println("Unable to read spec file: " + ioe);
				ioe.printStackTrace();
				System.exit(1);
			}
			try {
				testVars = (TestVars)ably.http.post("/apps", HttpUtils.defaultPostHeaders(false), null, new JSONRequestBody(appSpecText), new ResponseHandler() {
					@Override
					public Object handleResponse(int statusCode, String contentType, String[] headers, byte[] body) throws AblyException {
						JSONObject appSpec;
						try {
							appSpec = new JSONObject(new String(body));
							appSpec.put("notes", "Test app; created by ably-java realtime tests; date = " + new Date().toString());
							TestVars result = new TestVars();
							result.restHost = host;
							result.realtimeHost = wsHost;
							result.port = port;
							result.tlsPort = tlsPort;
							result.tls = tls;
							result.appId = appSpec.optString("appId");
							JSONArray keys = appSpec.optJSONArray("keys");
							int keyCount = keys.length();
							result.keys = new Key[keyCount];
							for(int i = 0; i < keyCount; i++) {
								JSONObject jsonKey = keys.optJSONObject(i);
								Key key = result.keys[i] = new Key();
								key.keyName = jsonKey.optString("keyName");
								key.keySecret = jsonKey.optString("keySecret");
								key.keyStr = jsonKey.optString("keyStr");
								key.capability = jsonKey.optString("capability");
							}
							return result;
						} catch (JSONException e) {
							throw new AblyException("Unexpected exception processing server response; err = " + e, 500, 50000);
						}
					}});
			} catch (AblyException ae) {
				System.err.println("Unable to create test app: " + ae);
				ae.printStackTrace();
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
				opts.tls = tls;
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
