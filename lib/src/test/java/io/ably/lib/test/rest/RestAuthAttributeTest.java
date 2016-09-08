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
}
