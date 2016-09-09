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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
			TokenDetails tokenDetails1 = ably.auth.authorise(tokenParams, authOptions);

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
			/* init ably client for token */
			final Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions optsForToken = testVars.createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* init custom TokenParams */
			Capability capability1 = new Capability();
			capability1.addResource("testchannel", "subscribe");
			final String capabilityStr = capability1.toString();
			final String testClientId1 = "firstClientId";
			TokenParams tokenParams1 = new TokenParams() {{
				clientId = testClientId1;
				capability = capabilityStr;
			}};

			/* init custom AuthOptions */
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ablyForToken.auth.requestToken(params, null);
					}
				};
				key = testVars.keys[1].keyStr;
			}};

			/* requestToken with custom options */
			TokenDetails tokenDetails1 = ably.auth.requestToken(tokenParams1, authOptions);
			assertNotNull("Expected token details", tokenDetails1);
			assertEquals("Unexpected clientId", tokenDetails1.clientId, testClientId1);
			assertEquals("Unexpected capability", tokenDetails1.capability, capabilityStr);

			/* init different custom TokenParams with capability value is null */
			Capability capability2 = new Capability();
			capability2.addResource("testchannel", "subscribe");
			final String capabilityStr2 = capability2.toString();
			TokenParams tokenParams2 = new TokenParams() {{
				clientId = null;
				capability = capabilityStr2;
			}};

			/* init custom AuthOptions */
			AuthOptions authOptions2 = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ablyForToken.auth.requestToken(params, null);
					}
				};
				key = testVars.keys[1].keyStr;
			}};

			/* requestToken with custom options */
			TokenDetails tokenDetails2 = ably.auth.requestToken(tokenParams2, authOptions2);
			assertNotNull("Expected token details", tokenDetails2);
			assertNotEquals("Unexpected token value", tokenDetails1.token, tokenDetails2.token);
			assertNull("Unexpected clientId", tokenDetails2.clientId);
			assertEquals("Unexpected capability", tokenDetails2.capability, capabilityStr2);
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
			/* test values */
			final String ablyDefaultClientId = ably.options.clientId;
			Setup.TestVars testVars = Setup.getTestVars();
			final Setup.Key defaultKey = testVars.keys[2];
			final String defaultCapability = defaultKey.capability;
			final String testClientId = "testClientId";
			final long testTtl = 2000L;

			/* init custom TokenParams */
			TokenParams tokenParams = new TokenParams() {{
				ttl = testTtl;
				clientId = testClientId;
				capability = defaultCapability;
			}};
			/* init custom AuthOptions */
			AuthOptions authOptions = new AuthOptions() {{
				key = defaultKey.keyStr;
			}};

			/* createTokenRequest with custom options */
			TokenRequest tokenRequest = ably.auth.createTokenRequest(tokenParams, authOptions);
			assertNotNull("Expected token request", tokenRequest);
			assertEquals("Unexpected ttl value", tokenRequest.ttl, testTtl);
			assertEquals("Unexpected clientId value", tokenRequest.clientId, testClientId);
			assertEquals("Unexpected capability value", tokenRequest.capability, defaultCapability);
			assertEquals("Unexpected keyName value", tokenRequest.keyName, defaultKey.keyName);

			/* init custom TokenParams */
			final long testTtl2 = 5000L;
			TokenParams tokenParams2 = new TokenParams() {{
				ttl = testTtl2;
				clientId = null;
				capability = null;
			}};

			/* createTokenRequest with custom AuthOptions */
			TokenRequest tokenRequest2 = ably.auth.createTokenRequest(tokenParams2, null);
			assertNotNull("Expected token request", tokenRequest2);
			assertEquals("Unexpected clientId value", tokenRequest2.clientId, ablyDefaultClientId);
			assertEquals("Unexpected ttl value", tokenRequest2.ttl, testTtl2);
			assertNull("Unexpected ttl value", tokenRequest2.capability);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_custom_options_create_token_request: Unexpected exception");
		}
	}

	/**
	 * Verify provided values of TokenParams or AuthOptions and
	 * supersede any previously client library configured TokenParams and AuthOptions,
	 * and used for every subsequent authorisation
	 * <p>
	 * Spec: RSA10j
	 * </p>
	 */
	@Test
	public void auth_custom_options_authorise() {
		try {
			/* init ably client for token */
			final Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions optsForToken = testVars.createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* init custom TokenParams */
			Capability capability1 = new Capability();
			capability1.addResource("testchannel", "subscribe");
			final String capabilityStr1 = capability1.toString();
			TokenParams tokenParams1 = new TokenParams() {{
				ttl = 2000L;
				clientId = null;
				capability = capabilityStr1;
			}};

			/* init custom AuthOptions */
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ablyForToken.auth.requestToken(params, null);
					}
				};
				key = testVars.keys[1].keyStr;
			}};

			/* authorise with custom options */
			TokenDetails tokenDetails1 = ably.auth.authorise(tokenParams1, authOptions);
			assertNotNull("Expected token details", tokenDetails1);
			assertNull("Unexpected clientId", tokenDetails1.clientId);
			assertEquals("Unexpected capability", tokenDetails1.capability, capabilityStr1);

			/* wait until token expires */
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException ie) {
			}

			/* init different custom TokenParams with capability value is null */
			Capability capability2 = new Capability();
			capability2.addResource("testchannel", "subscribe");
			final String capabilityStr2 = capability2.toString();
			final String testClientId2 = "testClientId";
			TokenParams tokenParams2 = new TokenParams() {{
				ttl = 2000L;
				clientId = testClientId2;
				capability = capabilityStr2;
			}};

			/* init custom AuthOptions */
			AuthOptions authOptions2 = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return ablyForToken.auth.requestToken(params, null);
					}
				};
				key = testVars.keys[1].keyStr;
			}};

			/* authorise with custom options */
			TokenDetails tokenDetails2 = ably.auth.authorise(tokenParams2, authOptions2);
			assertNotNull("Expected token details", tokenDetails2);
			assertNotEquals("Unexpected token value", tokenDetails1.token, tokenDetails2.token);
			assertEquals("Unexpected clientId", tokenDetails2.clientId, testClientId2);
			assertEquals("Unexpected capability", tokenDetails2.capability, capabilityStr2);

			/* wait until token expires */
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException ie) {
			}

			/* authorise with default values */
			TokenDetails tokenDetails3 = ably.auth.authorise(null, null);
			assertNotNull("Expected token details", tokenDetails3);
			assertNotEquals("Unexpected token value", tokenDetails2.token, tokenDetails3.token);
			assertEquals("Unexpected clientId", tokenDetails3.clientId, testClientId2);
			assertEquals("Unexpected capability", tokenDetails3.capability, capabilityStr2);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("auth_custom_options_request_token: Unexpected exception");
		}
	}
}
