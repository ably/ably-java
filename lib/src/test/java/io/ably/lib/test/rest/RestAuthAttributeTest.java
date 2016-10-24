package io.ably.lib.test.rest;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;

import static io.ably.lib.rest.Auth.AuthOptions;
import static io.ably.lib.rest.Auth.TokenRequest;
import static io.ably.lib.rest.Auth.TokenCallback;
import static io.ably.lib.rest.Auth.TokenDetails;
import static io.ably.lib.rest.Auth.TokenParams;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by VOstopolets on 9/3/16.
 */
public class RestAuthAttributeTest {

	private static AblyRest ably;

	private static void setup() throws Exception {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.clientId = "defaultClientId";
		ably = new AblyRest(opts);
	}

	/**
	 * Stores the AuthOptions and TokenParams arguments as defaults for subsequent authorisations
	 * <p>
	 * Spec: RSA10g
	 * </p>
	 */
	@Test
	public void auth_stores_options_params() {
		try {
			setup();
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
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_stores_options_params: Unexpected exception");
		}
	}

	/**
	 * Verify that {@link AuthOptions#force} attribute don't stored/used for subsequent authorisations
	 * <p>
	 * Spec: RSA10g
	 * </p>
	 */
	@Test
	public void auth_stores_options_exception_force() {
		try {
			setup();
			/* authorise with default values */
			TokenDetails tokenDetails1 = ably.auth.authorise(null, null);
			final String token1 = tokenDetails1.token;
			final String clientId1 = tokenDetails1.clientId;

			/* authorise with force attribute */
			TokenDetails tokenDetails2 = ably.auth.authorise(null,
					new AuthOptions() {{
						force = true;
						key = ably.options.key;
					}});
			final String token2 = tokenDetails2.token;
			final String clientId2 = tokenDetails2.clientId;

			/* Verify that, new token was issued */
			assertNotNull(tokenDetails1);
			assertNotNull(tokenDetails2);
			assertEquals(clientId1, clientId2);
			assertNotEquals(token1, token2);

			/* authorise with stored values */
			TokenDetails tokenDetails3 = ably.auth.authorise(null, null);
			final String token3 = tokenDetails3.token;
			final String clientId3 = tokenDetails3.clientId;

			/* Verify that, new token wasn't issued */
			assertNotNull(tokenDetails3);
			assertEquals(clientId2, clientId3);
			assertEquals(token2, token3);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_stores_options_exception_force: Unexpected exception");
		}
	}

	/**
	 * Verify that {@link AuthOptions#queryTime} attribute don't stored/used for subsequent authorisations
	 * <p>
	 * Spec: RSA10g
	 * </p>
	 */
	@Test
	public void auth_stores_options_exception_querytime() {
		try {
			setup();
			final long fakeServerTime = -1000;
			final String expectedClientId = "testClientId";
			Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.clientId = expectedClientId;
			AblyRest ablyForTime = new AblyRest(opts) {
				@Override
				public long time() throws AblyException {
					return fakeServerTime;
				}
			};
			final AuthOptions authOptions = new AuthOptions();
			authOptions.key = ablyForTime.options.key;
			authOptions.queryTime = true;
			TokenParams tokenParams = new TokenParams();

			/* create token request with custom AuthOptions that has attribute queryTime */
			TokenRequest tokenRequest = ablyForTime.auth.createTokenRequest(authOptions, tokenParams);

			/* verify that issued time of server equals fake expected value */
			assertEquals(expectedClientId, tokenRequest.clientId);
			assertEquals(fakeServerTime, tokenRequest.timestamp);

			/* authorise for store custom AuthOptions that has attribute queryTime */
			try {
				ablyForTime.auth.authorise(tokenParams, authOptions);
			} catch (Throwable e) {
			}

			/* create token request with stored AuthOptions */
			tokenRequest = ablyForTime.auth.createTokenRequest(null, tokenParams);

			/* Verify that,
			* 	 - timestamp not equals fake server time
			* 	 - timestamp equals local time */
			assertEquals(expectedClientId, tokenRequest.clientId);
			assertNotEquals(fakeServerTime, tokenRequest.timestamp);
			long localTime = System.currentTimeMillis();
			assertTrue((tokenRequest.timestamp >= (localTime - 500)) && (tokenRequest.timestamp <= (localTime + 500)));
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_stores_options_exception_querytime: Unexpected exception");
		}
	}

	/**
	 * Verify that {@link TokenParams#timestamp} attribute don't stored/used for subsequent authorisations
	 * <p>
	 * Spec: RSA10g
	 * </p>
	 */
	@Test
	public void auth_stores_options_exception_timestamp() {
		final String expectedClientId = "clientIdForToken";
		final long expectedTimestamp = 11111;
		try {
			setup();
			/* init ably for token */
			final Setup.TestVars testVars = Setup.getTestVars();
			final ClientOptions optsForToken = testVars.createOptions(testVars.keys[0].keyStr);
			optsForToken.clientId = expectedClientId;
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* create custom token callback for capturing timestamp values */
			final List<Long> timestampCapturedList = new ArrayList<>();
			TokenCallback tokenCallback = new TokenCallback() {
				private List<Long> timestampCapturedList;

				public TokenCallback setTimestampCapturedList(List<Long> timestampCapturedList) {
					this.timestampCapturedList = timestampCapturedList;
					return this;
				}

				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					this.timestampCapturedList.add(params.timestamp);
					return ablyForToken.auth.requestToken(null, null);
				}
			}.setTimestampCapturedList(timestampCapturedList);

			/* authorise with custom timestamp */
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = ably.options.key;
			authOptions.authCallback = tokenCallback;
			TokenParams tokenParams = new TokenParams();
			tokenParams.timestamp = expectedTimestamp;
			TokenDetails tokenDetails1 = ably.auth.authorise(tokenParams, authOptions);
			final String token1 = tokenDetails1.token;
			final String clientId1 = tokenDetails1.clientId;

			/* force authorise with stored TokenParams values */
			authOptions.force = true;
			TokenDetails tokenDetails2 = ably.auth.authorise(null, authOptions);
			final String token2 = tokenDetails2.token;
			final String clientId2 = tokenDetails2.clientId;

			/* Verify that,
			* 	 - new token was issued
			* 	 - authorise called twice
			* 	 - first timestamp value equals expected timestamp
			* 	 - second timestamp value is not expected
			* tokenDetails1 and tokenDetails2 aren't null,
			* the values of each attribute are equals */
			assertNotNull(tokenDetails1);
			assertNotNull(tokenDetails2);
			assertEquals(expectedClientId, clientId1);
			assertEquals(clientId1, clientId2);
			assertNotEquals(token1, token2);
			assertThat(timestampCapturedList.size(), is(equalTo(2)));
			assertEquals((long) timestampCapturedList.get(0), expectedTimestamp);
			assertNotEquals(timestampCapturedList.get(0), timestampCapturedList.get(1));
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_stores_options_exception_timestamp: Unexpected exception");
		}
	}

	/**
	 * Verify if {@link AuthOptions#force} is true
	 * will to issue a new token even if an existing token exists.
	 * <p>
	 * Spec: RSA10d
	 * </p>
	 */
	@Test
	public void auth_authorise_force() {
		try {
			setup();
			/* authorise with default options */
			TokenDetails tokenDetails1 = ably.auth.authorise(null, null);

			/* init custom AuthOptions with force value is true */
			final String custom_test_value = "test_forced_token";
			AuthOptions authOptions = new AuthOptions() {{
				authCallback = new TokenCallback() {
					@Override
					public Object getTokenRequest(TokenParams params) throws AblyException {
						return custom_test_value;
					}
				};
				force = true;
			}};

			/* authorise with custom AuthOptions */
			TokenDetails tokenDetails2 = ably.auth.authorise(null, authOptions);

			/* Verify that,
			 * tokenDetails1 and tokenDetails2 aren't null,
			 * tokens are different,
			 * token from tokenDetails2 equals custom_test_value */
			assertNotNull(tokenDetails1);
			assertNotNull(tokenDetails2);
			assertNotEquals(tokenDetails1.token, tokenDetails2.token);
			assertEquals(tokenDetails2.token, custom_test_value);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_custom_options_authorise: Unexpected exception");
		}
	}
}
