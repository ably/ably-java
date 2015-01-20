package io.ably.test.rest;

import io.ably.http.Http.JSONRequestBody;
import io.ably.http.Http.ResponseHandler;
import io.ably.http.HttpUtils;
import io.ably.rest.AblyRest;
import io.ably.types.AblyException;
import io.ably.types.Options;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RestSetup {

	private static final String defaultSpecFile = "test/io/ably/test/assets/testAppSpec.json";

	public static class Key {
		public String keyId;
		public String keyValue;
		public String keyStr;
		public String capability;
	}

	public static class TestVars {
		public String appId;
		public Key[] keys;
		public String host;
		public int port;
		public int tlsPort;
		public boolean tls;

		public Options createOptions() {
			Options opts = new Options();
			fillInOptions(opts);
			return opts;
		}
		public Options createOptions(String key) throws AblyException {
			Options opts = new Options(key);
			fillInOptions(opts);
			return opts;
		}
		public void fillInOptions(Options opts) {
			opts.host = host;
			opts.port = port;
			opts.tlsPort = tlsPort;
			opts.tls = tls;
		}
	}

	private static AblyRest ably;
	private static Map<String, TestVars> testEnvironments = new HashMap<String, TestVars>();
	private static Boolean tls_env;
	private static String host;
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
			/* default to connecting to sandbox through load balancer */
			port = 80;
			tlsPort = 443;
		} else {
			/* use the given host, assuming no load balancer */
			port = 8080;
			tlsPort = 8081;
		}
	}

	public static TestVars getTestVars() {
		return getTestVars(defaultSpecFile);
	}

	public static synchronized TestVars getTestVars(String specFile) {
		TestVars testVars = testEnvironments.get(specFile);
		if(testVars == null) {
			if(ably == null) {
				try {
					Options opts = new Options();
					/* we need to provide an appId to keep the library happy,
					 * but we are only instancing the library to use the http
					 * convenience methods */
					opts.host = host;
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
							appSpec.put("notes", "Test app; created by ably-java rest tests; date = " + new Date().toString());
							TestVars result = new TestVars();
							result.host = host;
							result.port = port;
							result.tlsPort = tlsPort;
							result.tls = tls;
							String appId = result.appId = appSpec.optString("appId");
							JSONArray keys = appSpec.optJSONArray("keys");
							int keyCount = keys.length();
							result.keys = new Key[keyCount];
							for(int i = 0; i < keyCount; i++) {
								JSONObject jsonKey = keys.optJSONObject(i);
								Key key = result.keys[i] = new Key();
								key.keyId = appId + '.' + jsonKey.optString("id");
								key.keyValue = jsonKey.optString("value");
								key.keyStr = key.keyId + ':' + key.keyValue;
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
		testEnvironments.put(specFile, testVars);
		return testVars;
	}

	public static void clearTestVars() {
		clearTestVars(defaultSpecFile);
	}

	public static synchronized void clearTestVars(String specFile) {
		TestVars testVars = testEnvironments.get(specFile);
		if(testVars != null) {
			try {
				Options opts = new Options(testVars.keys[0].keyStr);
				opts.host = host;
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
			testEnvironments.remove(specFile);
		}
	}
}
