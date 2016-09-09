package io.ably.lib.test.rest;

import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;

import static io.ably.lib.rest.Auth.AuthOptions;
import static io.ably.lib.rest.Auth.TokenCallback;
import static io.ably.lib.rest.Auth.TokenDetails;
import static io.ably.lib.rest.Auth.TokenParams;
import static io.ably.lib.rest.Auth.TokenRequest;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Created by VOstopolets on 9/3/16.
 */
public class RestAuthAttributeTest {

	private static AblyRest ably;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.clientId = "defaultClientId";
		ably = new AblyRest(opts);
	}

	/**
	 * Stores the AuthOptions and TokenParams arguments as defaults
	 * for subsequent authorisations with the exception of the attributes:
	 * {@link TokenParams#timestamp}, {@link AuthOptions#queryTime}, {@link AuthOptions#force}
	 * <p>
	 * Spec: RSA10g
	 * </p>
	 */
	@Test
	public void auth_stores_options_params() {
		try {
			/* init custom TokenParams */
			Capability capability = new Capability();
			capability.addResource("testchannel", "subscribe");
			final String capabilityStr = capability.toString();
			final String testClientId = "firstClientId";
			TokenParams tokenParams = new TokenParams() {{
				ttl = 4000L;
				clientId = testClientId;
				capability = capabilityStr;
			}};

			/* init custom AuthOptions */
			final Setup.TestVars testVars = Setup.getTestVars();
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));

					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ably.auth.requestToken(params, null);
					}
				};
				key = testVars.keys[1].keyStr;
			}};

			/* authorise with custom options */
			TokenDetails tokenDetails1 = ably.auth.authorise(authOptions, tokenParams);

			/* Verify that,
			 * tokenDetails1 isn't null,
			 * capability and clientId equals to the values of corresponding attributes in tokenParams */
			assertNotNull(tokenDetails1);
			assertEquals(tokenDetails1.clientId, testClientId);
			assertEquals(tokenDetails1.capability, capabilityStr);

			/* wait until token expires */
			try {
				Thread.sleep(5000L);
			} catch(InterruptedException ie) {}

			/* authorise with default options */
			TokenDetails tokenDetails2 = ably.auth.authorise(null, null);

			/* Verify that,
			 * tokenDetails2 isn't null,
			 * new token has to be issued,
			 * capability and clientId for different TokenDetails are the same */
			assertNotNull(tokenDetails2);
			assertNotEquals(tokenDetails1.token, tokenDetails2.token);
			assertEquals(tokenDetails1.capability, tokenDetails2.capability);
			assertEquals(tokenDetails1.clientId, tokenDetails2.clientId);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_stores_options_params: Unexpected exception");
		}
	}

	/**
	 * Verify provided values of TokenParams or AuthOptions,
	 * attributes are not merged with the configured client library defaults,
	 * but instead replace all corresponding values, even when null.
	 * <p>
	 * Spec: RSA8e
	 * </p>
	 */
	@Test
	public void auth_custom_options_request_token() {
		try {
			/* get key and capability */
			Setup.TestVars testVars = Setup.getTestVars();
			Setup.Key key = testVars.keys[2];
			String keyCapability = key.capability;
			String keyStr = key.keyStr;

			/* init custom TokenParams */
			TokenParams tokenParams = new TokenParams() {{
				ttl = 2000L;
				clientId = "testClientId";
				capability = keyCapability;
			}};

			/* requestToken with custom TokenParams */
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);

			/* Verify that,
			 * TokenDetails isn't null,
			 * TokenDetails attributes equals TokenParams attributes */
			assertNotNull(tokenDetails);
			assertEquals(tokenDetails.expires - tokenDetails.issued, tokenParams.ttl);
			assertEquals(tokenDetails.clientId, tokenParams.clientId);
			assertEquals(tokenDetails.capability, tokenParams.capability);

			/* init custom AuthOptions */
			String test_token = "test_token";
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return test_token;
					}
				};
				authUrl = "auth_url_1";
				token = "test_token_1";
				tokenDetails = new TokenDetails();
				authHeaders = new Param[]{new Param("X-Header-Param", "1")};
				authParams = new Param[]{new Param("X-Auth-Param", "1")};
				force = true;
				key = keyStr;
			}};

			/* requestToken with custom AuthOptions */
			tokenDetails = ably.auth.requestToken(null, authOptions);

			/* Verify that,
			 * TokenDetails not null,
			 * TokenDetails#token equals custom test token */
			assertNotNull(tokenDetails);
			assertEquals(tokenDetails.token, test_token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_custom_options_request_token: Unexpected exception");
		}
	}

	/**
	 * Verify provided values of TokenParams or AuthOptions,
	 * attributes are not merged with the configured client library defaults,
	 * but instead replace all corresponding values, even when null.
	 * <p>
	 * Spec: RSA9h
	 * </p>
	 */
	@Test
	public void auth_custom_options_create_token_request() {
		try {
			/* get key and capability */
			Setup.TestVars testVars = Setup.getTestVars();
			Setup.Key key = testVars.keys[2];
			String keyCapability = key.capability;
			String keyStr = key.keyStr;
			String keyName = key.keyName;

			/* init custom TokenParams */
			TokenParams tokenParams = new TokenParams() {{
				ttl = 2000L;
				clientId = "testClientId";
				capability = keyCapability;
			}};

			/* requestToken with custom tokenParams */
			TokenRequest tokenRequest = ably.auth.createTokenRequest(null, tokenParams);

			/* Verify that,
			 * TokenRequest isn't null,
			 * TokenRequest attributes equals TokenParams attributes */
			assertNotNull(tokenRequest);
			assertEquals(tokenRequest.ttl, tokenParams.ttl);
			assertEquals(tokenRequest.clientId, tokenParams.clientId);
			assertEquals(tokenRequest.capability, tokenParams.capability);

			/* init custom AuthOptions */
			AuthOptions authOptions = new AuthOptions() {{
				key = keyStr;
			}};

			/* requestToken with custom AuthOptions */
			tokenRequest = ably.auth.createTokenRequest(authOptions, null);

			/* Verify that,
			 * TokenRequest isn't null,
			 * TokenRequest#keyName equals custom keyName */
			assertNotNull(tokenRequest);
			assertEquals(tokenRequest.keyName, keyName);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_custom_options_create_token_request: Unexpected exception");
		}
	}

	/**
	 * Verify provided values of TokenParams or AuthOptions and
	 * supersede any previously client library configured TokenParams and AuthOptions.
	 * <p>
	 * Spec: RSA10j
	 * </p>
	 */
	@Test
	public void auth_custom_options_authorise() {
		try {
			/* init custom TokenParams */
			Capability capability1 = new Capability();
			capability1.addResource("testchannel", "subscribe");
			TokenParams tokenParams = new TokenParams() {{
				ttl = 5000L;
				clientId = "testClientId";
				capability = capability1.toString();
			}};

			/* init custom AuthOptions */
			Setup.TestVars testVars = Setup.getTestVars();
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					private AblyRest ably = new AblyRest(testVars.createOptions(testVars.keys[0].keyStr));

					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ably.auth.requestToken(params, null);
					}
				};
				authUrl = "auth_url_1";
				token = "test_token_1";
				key = testVars.keys[2].keyStr;
				tokenDetails = new TokenDetails();
				authHeaders = new Param[]{new Param("X-Header-Param", "1")};
				authParams = new Param[]{new Param("X-Auth-Param", "1")};
			}};

			/* authorise with custom options */
			TokenDetails tokenDetails1 = ably.auth.authorise(authOptions, tokenParams);

			/* authorise with default options */
			TokenDetails tokenDetails2 = ably.auth.authorise(null, null);

			/* Verify that,
			 * storedTokenParams isn't null,
			 * and storedTokenParams equals custom tokenParams */
			assertNotNull(tokenDetails1);
			assertNotNull(tokenDetails2);
			assertEquals(tokenDetails1.token, tokenDetails2.token);
			assertEquals(tokenDetails1.capability, tokenDetails2.capability);
			assertEquals(tokenDetails1.clientId, tokenDetails2.clientId);
			assertEquals(tokenDetails1.expires, tokenDetails2.expires);
			assertEquals(tokenDetails1.issued, tokenDetails2.issued);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_custom_options_authorise: Unexpected exception");
		}
	}
}
