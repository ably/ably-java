package io.ably.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import io.ably.http.Http;
import io.ably.rest.AblyRest;
import io.ably.rest.Auth.AuthMethod;
import io.ably.rest.Auth.AuthOptions;
import io.ably.rest.Auth.TokenCallback;
import io.ably.rest.Auth.TokenDetails;
import io.ably.rest.Auth.TokenParams;
import io.ably.rest.Channel;
import io.ably.test.rest.RestSetup.TestVars;
import io.ably.test.util.TokenServer;
import io.ably.types.AblyException;
import io.ably.types.ClientOptions;
import io.ably.types.Message;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;

import org.json.JSONObject;
import org.junit.Test;

public class RestAuth {

	/**
	 * Init library with a key only
	 */
	@Test
	public void authinit0() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			AblyRest ably = new AblyRest(testVars.keys[0].keyStr);
			assertEquals("Unexpected Auth method mismatch", ably.auth.getAuthMethod(), AuthMethod.basic);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authinit0: Unexpected exception instantiating library");
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions optsForToken = new ClientOptions(testVars.keys[0].keyStr);
			optsForToken.restHost = testVars.host;
			optsForToken.port = testVars.port;
			optsForToken.tlsPort = testVars.tlsPort;
			optsForToken.tls = testVars.tls;
			AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);
			assertNotNull("Expected token value", tokenDetails.token);
			ClientOptions opts = new ClientOptions();
			opts.token = tokenDetails.token;
			opts.restHost = testVars.host;
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
	 * Init token server
	 */
	@Test
	public void auth_start_tokenserver() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			AblyRest ably = new AblyRest(opts);
			tokenServer = new TokenServer(ably, 8982);
			tokenServer.start();
		} catch (IOException e) {
			e.printStackTrace();
			fail("auth_start_tokenserver: Unexpected exception starting server");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_start_tokenserver: Unexpected exception starting server");
		}
	}

	/**
	 * Verify authURL called and handled when returning token request
	 */
	@Test
	public void auth_authURL_tokenrequest() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
				JSONObject jsonParams = e.errorInfo.getRawJSON();
				assertTrue("Expected JSON error info", jsonParams != null);
				assertEquals("Expected echoed param", "test-value", jsonParams.optString("test-param"));
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.host;
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
				JSONObject jsonParams = e.errorInfo.getRawJSON();
				assertTrue("Expected JSON error info", jsonParams != null);
				assertEquals("Expected echoed header", "test-value", jsonParams.optString("test-header"));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_authURL_headers: Unexpected exception instantiating library");
		}
	}

	/**
	 * Kill token server
	 */
	@Test
	public void auth_stop_tokenserver() {
		if(tokenServer != null)
			tokenServer.stop();
	}

	/**
	 * Verify authCallback called and handled when returning token request
	 */
	@Test
	public void auth_authcallback_tokenrequest() {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.createTokenRequest(null, params);
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = testVars.createOptions();
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
	 * Verify authCallback called and handled when returning token
	 */
	@Test
	public void auth_authcallback_token() {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(null, params);
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = testVars.createOptions();
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
	 * Verify authCallback called when token expires
	 */
	@Test
	public void auth_authcallback_token_expire() {
		try {
			final TestVars testVars = RestSetup.getTestVars();
			ClientOptions optsForToken = testVars.createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, new TokenParams() {{ ttl = 5000L; }});
			assertNotNull("Expected token value", tokenDetails.token);

			/* implement callback, using Ably instance with key */
			final class TokenGenerator implements TokenCallback {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					++cbCount;
					return ablyForToken.auth.requestToken(null, params);
				}
				public int getCbCount() { return cbCount; }
				private int cbCount = 0;
			};

			TokenGenerator authCallback = new TokenGenerator();

			/* create Ably instance without key */
			ClientOptions opts = testVars.createOptions();
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
			final TestVars testVars = RestSetup.getTestVars();

			/* implement callback, using Ably instance with key */
			TokenCallback authCallback = new TokenCallback() {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					throw new AblyException("test exception", 404, 0);
				}
			};

			/* create Ably instance without key */
			ClientOptions opts = testVars.createOptions();
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
	 * Verify client does not have a clientId data within token details and published messages, 
	 * when a token initialized with null client id
	 */
	@Test
	public void auth_clientid_null_success() {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			// implement callback, using Ably instance with key
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(null, params);
				}
			};
			
			// create Ably instance without clientId
			ClientOptions options = testVars.createOptions();
			options.clientId = null;
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);
			
			// Fetch token
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be null", null, tokenDetails.clientId);
			
			// Publish message
			String messageName = "clientless";
			String messageData = String.valueOf(System.currentTimeMillis());
			
			Channel channel = ably.channels.get("test");
			channel.publish(messageName, messageData);
			
			// Fetch published message
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message publishedMessage = null;
			Message message;
			
			for (int i = 0; i < messages.length; i++) {
				message = messages[i];
				
				if (messageName.equals(message.name) &&
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
	 * Verify publishing message fails, when there is a client id mismatch
	 */
	@Test
	public void auth_clientid_null_mismatch() {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			// implement callback, using Ably instance with key
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(null, params);
				}
			};
			
			// create Ably instance without clientId
			ClientOptions options = testVars.createOptions();
			options.clientId = null;
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);
			
			// Fetch token
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be null", null, tokenDetails.clientId);
			
			// Publish message
			Message message = new Message("I", "will", "fail");
			Channel channel = ably.channels.get("test");
			
			try {
				channel.publish(new Message[]{ message });
				fail ("auth_clientid_null_mismatch: AblyException for mismatched clientId is expected here");
			} catch (AblyException e) {
				assertEquals("AblyException for mismatched clientId is expected here", "Malformed message; mismatched clientId", e.getMessage());
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_null_mismatch: Unexpected exception");
		}
	}
	
	/**
	 * Verify published message has null client id,
	 * when authenticated with wildcard '*' client id
	 */
	@Test
	public void auth_clientid_null_wildcard () {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			// implement callback, using Ably instance with key
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(null, params);
				}
			};
			
			// create Ably instance without clientId
			ClientOptions options = testVars.createOptions();
			options.clientId = "*";
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);
			
			// Fetch token
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);
			
			// Publish message
			String messageName = "wildcard";
			String messageData = String.valueOf(System.currentTimeMillis());
			
			Channel channel = ably.channels.get("test");
			channel.publish(messageName, messageData);
			
			// Fetch published message
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message publishedMessage = null;
			Message message;
			
			for (int i = 0; i < messages.length; i++) {
				message = messages[i];
				
				if (messageName.equals(message.name) &&
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
	 * when authenticated with wildcard '*' client id
	 */
	@Test
	public void auth_clientid_explicit_wildcard () {
		try {
			final TestVars testVars = RestSetup.getTestVars();

			// implement callback, using Ably instance with key
			TokenCallback authCallback = new TokenCallback() {
				private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return ably.auth.requestToken(null, params);
				}
			};
			
			// create Ably instance without clientId
			ClientOptions options = testVars.createOptions();
			options.clientId = "*";
			options.authCallback = authCallback;
			AblyRest ably = new AblyRest(options);
			
			// Fetch token
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertEquals("Auth#clientId is expected to be wildcard '*'", "*", tokenDetails.clientId);
			
			// Publish message
			Message messagePublishee = new Message(
					"wildcard",	// name
					"brian that is called brian",	// clientId
					String.valueOf(System.currentTimeMillis())	// data
					);
			
			Channel channel = ably.channels.get("test");
			channel.publish(new Message[] { messagePublishee });
			
			// Fetch published message
			PaginatedResult<Message> result = channel.history(null);
			Message[] messages = result.items();
			Message messagePublished = null;
			Message message;
			
			for (int i = 0; i < messages.length; i++) {
				message = messages[i];
				
				if (messagePublishee.name.equals(message.name) &&
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
	

	private TokenServer tokenServer;
}
