package io.ably.lib.test.common;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import io.ably.lib.http.Http.JSONRequestBody;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.util.Serialisation;

public class Setup {

	public static Object loadJSON(String resourceName, Class<? extends Object> expectedType) throws IOException {
		try {
			byte[] jsonBytes = resourceLoader.read(resourceName);
			return gson.fromJson(new String(jsonBytes), expectedType);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static class Key {
		public String keyName;
		public String keySecret;
		public String keyStr;
		public String capability;
		public int status;
	}

	public static class Namespace {
		public String id;
		public boolean persisted;
		public int status;
	}

	public static class Connection {
		public String id;
	}

	public static class Channel {
		public String name;
		public PresenceMessage[] presence;
	}

	public static class AppSpec {
		public String id;
		public String appId;
		public String accountId;
		public Key[] keys;
		public Namespace[] namespaces;
		public Connection[] connections;
		public Channel[] channels;
		public boolean tlsOnly;
		public String notes;
	}

	public static class TestVars extends AppSpec {
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

	public static synchronized TestVars getTestVars() {
		return (refCount++ == 0) ? __getTestVars() : testVars;
	}

	private static TestVars __getTestVars() {
		if(testVars == null) {
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

			if(ably == null) {
				try {
					ClientOptions opts = new ClientOptions();
					/* we need to provide an appId to keep the library happy,
					 * but we are only instancing the library to use the http
					 * convenience methods */
					opts.key = "none:none";
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
				appSpec = (Setup.AppSpec)loadJSON(specFile, Setup.AppSpec.class);
				appSpec.notes = "Test app; created by ably-java realtime tests; date = " + new Date().toString();
			} catch(IOException ioe) {
				System.err.println("Unable to read spec file: " + ioe);
				ioe.printStackTrace();
				System.exit(1);
			}
			try {
				testVars = ably.http.post("/apps", null, null, new JSONRequestBody(appSpec), new ResponseHandler<TestVars>() {
					@Override
					public TestVars handleResponse(int statusCode, String contentType, Collection<String> headers, byte[] body) throws AblyException {
						TestVars result = (TestVars)Serialisation.gson.fromJson(new String(body), TestVars.class);
						result.restHost = host;
						result.realtimeHost = wsHost;
						result.port = port;
						result.tlsPort = tlsPort;
						result.tls = true;
						return result;
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
		if(--refCount == 0)
			__clearTestVars();
	}

	private static void __clearTestVars() {
		if(testVars != null) {
			try {
				ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
				opts.restHost = host;
				opts.port = port;
				opts.tlsPort = tlsPort;
				opts.tls = true;
				ably = new AblyRest(opts);
				ably.http.del("/apps/" + testVars.appId, HttpUtils.defaultAcceptHeaders(false), null, null);
			} catch (AblyException ae) {
				System.err.println("Unable to delete test app: " + ae);
				ae.printStackTrace();
				System.exit(1);
			}
			testVars = null;
		}
	}

	static {
		Class<? extends ResourceLoader> claz = null;
		try {
			try {
				claz = (Class<? extends ResourceLoader>) Class.forName("io.ably.lib.test.common.jre.JreResourceLoader");
			} catch(ClassNotFoundException cnfe) {
				try {
					claz = (Class<? extends ResourceLoader>) Class.forName("io.ably.lib.test.common.android.AssetResourceLoader");
				} catch(ClassNotFoundException cnfe2) {
					System.err.println("Unable to instance any known ResourceLoader class");
				}
			}
			resourceLoader = (ResourceLoader)claz.newInstance();
		} catch(Throwable t) {
			System.err.println("Unexpected exception instancing ResourceLoader class");
			t.printStackTrace();
		}
	}

	private static ResourceLoader resourceLoader;
	private static final String specFile = "local/testAppSpec.json";

	private static AblyRest ably;
	private static String environment;
	private static String host;
	private static String wsHost;
	private static int port;
	private static int tlsPort;

	private static TestVars testVars;
	private static int refCount;
	private static Gson gson = new Gson();
}
