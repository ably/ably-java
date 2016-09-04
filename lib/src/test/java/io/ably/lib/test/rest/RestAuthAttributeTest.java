package io.ably.lib.test.rest;

import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;

import static io.ably.lib.rest.Auth.AuthOptions;
import static io.ably.lib.rest.Auth.TokenCallback;
import static io.ably.lib.rest.Auth.TokenDetails;
import static io.ably.lib.rest.Auth.TokenParams;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
            Capability capability1 = new Capability();
            capability1.addResource("testchannel", "subscribe");
            TokenParams tokenParams = new TokenParams() {{
                ttl = 1000L;
                clientId = "firstClientId";
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
                key = testVars.keys[1].keyStr;
                tokenDetails = new TokenDetails();
                authHeaders = new Param[]{new Param("X-Header-Param", "1")};
                authParams = new Param[]{new Param("X-Auth-Param", "1")};
            }};

            /* authorise with custom options */
            ably.auth.authorise(authOptions, tokenParams);

            /* get stored TokenParams */
            TokenParams storedTokenParams = ably.auth.getTokenParams();

            /* Verify that,
             * storedTokenParams isn't null,
             * and storedTokenParams equals custom tokenParams */
            assertNotNull(storedTokenParams);
            assertEquals(storedTokenParams.ttl, tokenParams.ttl);
            assertEquals(storedTokenParams.clientId, tokenParams.clientId);
            assertEquals(storedTokenParams.capability, tokenParams.capability);

            /* get stored AuthOptions */
            AuthOptions storedAuthOptions = ably.auth.getAuthOptions();

            /* Verify that,
             * storedAuthOptions isn't null,
             * and storedAuthOptions equals custom authOptions */
            assertNotNull(storedAuthOptions);
            assertEquals(storedAuthOptions.authCallback, authOptions.authCallback);
            assertEquals(storedAuthOptions.authUrl, authOptions.authUrl);
            assertEquals(storedAuthOptions.token, authOptions.token);
            assertEquals(storedAuthOptions.key, authOptions.key);
            assertEquals(storedAuthOptions.tokenDetails, authOptions.tokenDetails);
            assertArrayEquals(storedAuthOptions.authHeaders, authOptions.authHeaders);
            assertArrayEquals(storedAuthOptions.authParams, authOptions.authParams);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_stores_options_params: Unexpected exception");
        }
    }
}
