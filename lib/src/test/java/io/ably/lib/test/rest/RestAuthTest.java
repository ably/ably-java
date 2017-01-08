package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.rest.Auth.TokenCallback;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.TokenServer;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;

public class RestAuthTest extends ParameterizedTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	/**
	 * Init token server
	 */
	@BeforeClass
	public static void auth_start_tokenserver() {
		try {
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			AblyRest ably = new AblyRest(opts);
			tokenServer = new TokenServer(ably, 8982);
			tokenServer.start();

			nanoHTTPD = new SessionHandlerNanoHTTPD(27335);
			nanoHTTPD.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

			while (!nanoHTTPD.wasStarted()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail("auth_start_tokenserver: Unexpected exception starting server");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_start_tokenserver: Unexpected exception starting server");
		}
	}

	/**
	 * Kill token server
	 */
	@AfterClass
	public static void auth_stop_tokenserver() {
		if(tokenServer != null)
			tokenServer.stop();
		if (nanoHTTPD != null)
			nanoHTTPD.stop();
	}

	/**
	 * Init library with a key only
	 */
	@Test
	public void authinit0() {
		try {
			AblyRest ably = new AblyRest(testVars.keys[0].keyStr);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.basic);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with useTokenAuth set
	 */
	@Test
	public void authinit0_useTokenAuth() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.useTokenAuth = true;
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit0point5: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with a token only
	 */
	@Test
	public void authinit1() {
		try {
			ClientOptions opts = new ClientOptions();
			opts.token = "this_is_not_really_a_token";
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit1: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with a token callback
	 */
	private boolean authinit2_cbCalled;
	@Test
	public void authinit2() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authCallback = new TokenCallback() {
				@Override
				public String getTokenRequest(TokenParams params) throws AblyException {
					authinit2_cbCalled = true;
					return "this_is_not_really_a_token_request";
				}};
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				ably.stats(null);
			} catch(Throwable t) {}
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
			assertTrue("Token callback not called", authinit2_cbCalled);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit2: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with a key and clientId; expect token auth to be chosen
	 */
	@Test
	public void authinit3() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.clientId = "testClientId";
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit3: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with a token
	 */
	@Test
	public void authinit4() {
		try {
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			optsForToken.restHost = testVars.restHost;
			optsForToken.environment = testVars.environment;
			optsForToken.port = testVars.port;
			optsForToken.tlsPort = testVars.tlsPort;
			optsForToken.tls = testVars.tls;
			AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);
			assertNotNull("Expected token value", tokenDetails.token);
			ClientOptions opts = new ClientOptions();
			opts.token = tokenDetails.token;
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tls = testVars.tls;
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit3: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authURL called and handled when returning token request
	 */
	@Test
	public void auth_authURL_tokenrequest() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authUrl = "http://localhost:8982/get-token-request";
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				TokenDetails tokenDetails = ably.auth.requestToken(null, null);
				assertNotNull("Expected token value", tokenDetails.token);
			} catch (AblyException e) {
				e.printStackTrace();
				fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authURL called and handled when returning token
	 */
	@Test
	public void auth_authURL_token() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authUrl = "http://localhost:8982/get-token";
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				TokenDetails tokenDetails = ably.auth.requestToken(null, null);
				assertNotNull("Expected token value", tokenDetails.token);
			} catch (AblyException e) {
				e.printStackTrace();
				fail("auth_authURL_token: Unexpected exception requesting token");
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_token: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authURL called and handled when returning error
	 */
	@Test
	public void auth_authURL_err() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authUrl = "http://localhost:8982/404";
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				ably.auth.requestToken(null, null);
				fail("auth_authURL_err: Unexpected success requesting token");
			} catch (AblyException e) {
				assertEquals("Expected forwarded error code", e.errorInfo.code, 40170);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_token: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authURL is passed specified params
	 */
	@Test
	public void auth_authURL_params() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authUrl = "http://localhost:8982/echo-params";
			opts.authParams = new Param[]{new Param("test-param", "test-value")};
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				ably.auth.requestToken(null, null);
				fail("auth_authURL_params: Unexpected success requesting token");
			} catch (AblyException e) {
				assertEquals("Expected forwarded error code", e.errorInfo.code, 40170);
				assertTrue("Expected echoed header", e.errorInfo.message.indexOf("test-param=test-value") != -1);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_params: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authURL is passed specified headers
	 */
	@Test
	public void auth_authURL_headers() {
		try {
			ClientOptions opts = createOptions();
			opts.restHost = testVars.restHost;
			opts.environment = testVars.environment;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			opts.authUrl = "http://localhost:8982/echo-headers";
			opts.authHeaders = new Param[]{new Param("test-header", "test-value")};
			AblyRest ably = new AblyRest(opts);
			/* make a call to trigger token request */
			try {
				ably.auth.requestToken(null, null);
				fail("auth_authURL_headers: Unexpected success requesting token");
			} catch (AblyException e) {
				assertEquals("Expected forwarded error code", e.errorInfo.code, 40170);
				assertTrue("Expected echoed header", e.errorInfo.message.indexOf("test-header=test-value") != -1);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_headers: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authCallback called and handled when returning {@code TokenRequest}
	 */
	@Test
	public void auth_authcallback_tokenrequest() {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.createTokenRequest(params, null);
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = createOptions();
			opts.authCallback = authCallback;
			AblyRest ably = new AblyRest(opts);

			/* make a call to trigger token request */
			try {
				TokenDetails tokenDetails = ably.auth.requestToken(null, null);
				assertNotNull("Expected token value", tokenDetails.token);
			} catch (AblyException e) {
				e.printStackTrace();
				fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authCallback called and handled when returning {@code TokenDetails}
	 */
	@Test
	public void auth_authcallback_tokendetails() {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(params, null);
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = createOptions();
			opts.authCallback = authCallback;
			AblyRest ably = new AblyRest(opts);

			/* make a call to trigger token request */
			try {
				TokenDetails tokenDetails = ably.auth.requestToken(null, null);
				assertNotNull("Expected token value", tokenDetails.token);
			} catch (AblyException e) {
				e.printStackTrace();
				fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authCallback called and handled when returning token string
	 */
	@Test
	public void auth_authcallback_tokenstring() throws AblyException {
			/* implement callback, using Ably instance with key */
		TokenCallback authCallback = new TokenCallback() {
			private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
			@Override
			public Object getTokenRequest(TokenParams params) throws AblyException {
				return ably.auth.requestToken(params, null).token;
			}
		};

			/* create Ably instance without key */
		ClientOptions opts = createOptions();
		opts.authCallback = authCallback;
		AblyRest ably = new AblyRest(opts);

			/* make a call to trigger token request */
		try {
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertNotNull("Expected token value", tokenDetails.token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
		}
	}

	/**
	 * Verify authCallback called when token expires
	 */
	@Test
	public void auth_authcallback_token_expire() {
		try {
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 5000L; }}, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* implement callback, using Ably instance with key */
			final class TokenGenerator implements TokenCallback {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					++cbCount;
					return ablyForToken.auth.requestToken(params, null);
				}
				public int getCbCount() { return cbCount; }
				private int cbCount = 0;
			};

			TokenGenerator authCallback = new TokenGenerator();

			/* create Ably instance without key */
			ClientOptions opts = createOptions();
			opts.token = tokenDetails.token;
			opts.authCallback = authCallback;
			AblyRest ably = new AblyRest(opts);

			/* wait until token expires */
			try {
				Thread.sleep(6000L);
			} catch(InterruptedException ie) {}

			/* make a request that relies on the token */
			try {
				ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
			} catch (AblyException e) {
				e.printStackTrace();
				fail("auth_authURL_tokenrequest: Unexpected exception requesting token");
			}

			/* verify that the auth callback was called */
			assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_tokenrequest: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify authCallback called and handled when returning error
	 */
	@Test
	public void auth_authcallback_err() {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					throw AblyException.fromErrorInfo(new ErrorInfo("test exception", 404, 0));
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = createOptions();
			opts.authCallback = authCallback;
			AblyRest ably = new AblyRest(opts);

			/* make a call to trigger token request */
			try {
				ably.auth.requestToken(null, null);
				fail("auth_authURL_err: Unexpected success requesting token");
			} catch (AblyException e) {
				assertEquals("Expected forwarded error code", e.errorInfo.code, 40170);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_token: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify token details has null client id after authenticating with null client id,
	 * the message gets published, and published message also does not contain a client id.<br>
	 * <br>
	 * Spec: RSA8f1
	 */
	@Test
	public void auth_clientid_null_success() {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(params, null);
				}
			};

			/* create Ably instance without clientId */
			ClientOptions options = createOptions();
			options.clientId = null;
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);

			/* Fetch token */
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be null", null, tokenDetails.clientId);

			/* Publish message */
			String messageName = "clientless";
			String messageData = String.valueOf(System.currentTimeMillis());

			Channel channel = ably.channels.get("test");
			channel.publish(messageName, messageData);

			/* Fetch published message */
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message publishedMessage = null;
			Message message;

			for(int i = 0; i < messages.length; i++) {
				message = messages[i];

				if(messageName.equals(message.name) &&
					messageData.equals(message.data)) {
					publishedMessage = message;
					break;
				}
			}

			assertNotNull("Recently published message expected to be accessible", publishedMessage);
			assertEquals("Message#clientId is expected to be null", null, publishedMessage.clientId);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_null_success: Unexpected exception");
		}
	}

	/**
	 * Verify message gets rejected when there is a client id mismatch
	 * between token details and message<br>
	 * <br>
	 * Spec: RSA8f2
	 */
	@Test
	public void auth_clientid_null_mismatch() throws AblyException {
		AblyRest ably = null;

		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(params, null);
				}
			};

			/* create Ably instance */
			ClientOptions options = createOptions();
			options.authCallback = authCallback;
			ably = new AblyRest(options);

			/* Create token with null clientId */
			TokenParams tokenParams = new TokenParams() {{ clientId = null; }};
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
			assertEquals("Auth#clientId is expected to be null", null, tokenDetails.clientId);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_null_mismatch: Unexpected exception");
		}

		/* Publish a message with mismatching client id */
		Message message = new Message(
				"I", /* name */
				"will", /* data */
				"fail" /* mismatching client id */
		);
		Channel channel = ably.channels.get("test");

		thrown.expect(AblyException.class);
		thrown.expectMessage("Malformed message; mismatched clientId");
		channel.publish(new Message[]{ message });
	}

	/**
	 * Verify message with wildcard `*` client id gets published,
	 * and contains null client id.<br>
	 * <br>
	 * Spec: RSA8f3
	 */
	@Test
	public void auth_clientid_null_wildcard () {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(params, null);
				}
			};

			/* create Ably instance with wildcard clientId */
			ClientOptions options = createOptions();
			options.clientId = "*";
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);

			/* Fetch token */
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);

			/* Publish message */
			String messageName = "wildcard";
			String messageData = String.valueOf(System.currentTimeMillis());

			Channel channel = ably.channels.get("test");
			channel.publish(messageName, messageData);

			/* Fetch published message */
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message publishedMessage = null;
			Message message;

			for(int i = 0; i < messages.length; i++) {
				message = messages[i];

				if(messageName.equals(message.name) &&
						messageData.equals(message.data)) {
					publishedMessage = message;
					break;
				}
			}

			assertNotNull("Recently published message expected to be accessible", publishedMessage);
			assertEquals("Message#clientId is expected to be null", null, publishedMessage.clientId);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_null_wildcard: Unexpected exception");
		}
	}

	/**
	 * Verify message with explicit client id successfully gets published,
	 * when authenticated with wildcard '*' client id<br>
	 * <br>
	 * Spec: RSA8f4
	 */
	@Test
	public void auth_clientid_explicit_wildcard () {
		try {
			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(params, null);
				}
			};

			/* create Ably instance with wildcard clientId */
			ClientOptions options = createOptions();
			options.clientId = "*";
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);

			/* Fetch token */
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);

			/* Publish a message */
			Message messagePublishee = new Message(
					"wildcard",	/* name */
					String.valueOf(System.currentTimeMillis()), /* data */
					"brian that is called brian" /* clientId */
			);

			Channel channel = ably.channels.get("test");
			channel.publish(new Message[] { messagePublishee });

			/* Fetch published message */
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message messagePublished = null;
			Message message;

			for(int i = 0; i < messages.length; i++) {
				message = messages[i];

				if(messagePublishee.name.equals(message.name) &&
						messagePublishee.data.equals(message.data)) {
					messagePublished = message;
					break;
				}
			}

			assertNotNull("Recently published message expected to be accessible", messagePublished);
			assertEquals("Message#clientId is expected to be same with explicitly defined clientId", messagePublishee.clientId, messagePublished.clientId);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_explicit_wildcard: Unexpected exception");
		}
	}

	/**
	 * Verify token auth used when useTokenAuth=true
	 */
	@Test
	public void auth_useTokenAuth() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.useTokenAuth = true;
			AblyRest ably = new AblyRest(opts);
			/* verify that we don't have a token yet. */
			assertTrue("Not expecting a token yet", ably.auth.getTokenAuth() == null || ably.auth.getTokenAuth().getTokenDetails() == null);
			/* make a request that relies on the token */
			try {
				ably.stats(new Param[] { new Param("by", "hour"), new Param("limit", "1") });
			} catch (AblyException e) {
				e.printStackTrace();
				fail("Unexpected exception requesting token");
			}
			/* verify that we have a token. */
			assertTrue("Expected to use token auth", ably.auth.getTokenAuth().getTokenDetails() != null);
			System.out.println("Token is " + ably.auth.getTokenAuth().getTokenDetails().token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception instantiating library");
		}
	}

	/**
	 * Test behaviour of queryTime parameter in ClientOpts. Time is requested from the Ably server only once,
	 * cached value should be used afterwards
	 */
	@Test
	public void auth_testQueryTime() {
		try {
			Auth.clearCachedServerTime();
			nanoHTTPD.clearRequestHistory();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.tls = false;
			opts.restHost = "localhost";
			opts.port = nanoHTTPD.getListeningPort();
			opts.queryTime = true;

			AblyRest ably1 = new AblyRest(opts);
			Auth.TokenRequest tr1 = ably1.auth.createTokenRequest(null, null);

			AblyRest ably2 = new AblyRest(opts);
			Auth.TokenRequest tr2 = ably2.auth.createTokenRequest(null, null);

			List<String> requestHistory = nanoHTTPD.getRequestHistory();
			/* search for all /time request in the list */
			int timeRequestCount = 0;
			for (String request: requestHistory)
				if (request.contains("/time"))
					timeRequestCount++;

			assertEquals("Verify number of time requests", timeRequestCount, 1);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception");
		}
	}

	private static TokenServer tokenServer;
	private static SessionHandlerNanoHTTPD nanoHTTPD;

	private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
		private final ArrayList<String> requestHistory = new ArrayList<>();

		public SessionHandlerNanoHTTPD(int port) {
			super(port);
		}

		@Override
		public Response serve(IHTTPSession session) {
			synchronized (requestHistory) {
				requestHistory.add(session.getUri());
			}
			/* the only request supported here is /time */
			return newFixedLengthResponse(String.format(Locale.US, "[%d]", System.currentTimeMillis()));
		}

		public void clearRequestHistory() { requestHistory.clear(); }

		public List<String> getRequestHistory() { return requestHistory; }
	}

}
