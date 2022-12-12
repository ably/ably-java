package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static io.ably.lib.util.HttpCodes.UNAUTHORIZED;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup.Key;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.Stats;

public class RestJWTTest extends ParameterizedTest {

    private Key key = testVars.keys[0];
    Param[] environment = new Param[]{ new Param("environment", testVars.environment) };
    Param[] validKeys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", key.keySecret) };
    Param[] invalidKeys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", "invalidinvalid") };
    Param[] tokenEmbedded = new Param[]{ new Param("jwtType", "embedded") };
    Param[] tokenEmbeddedAndEncrypted = new Param[]{ new Param("jwtType", "embedded"), new Param("encrypted", 1) };
    Param[] jwtReturnType = new Param[]{ new Param("returnType", "jwt") };
    private static final String echoServer = "https://echo.ably.io/createJWT";

    /**
     * Base request of a JWT token (RSA8g RSA8c)
     */
    @Test
    public void auth_jwt_request() {
        try {
            ClientOptions options = buildClientOptions(validKeys);
            AblyRest client = new AblyRest(options);
            PaginatedResult<Stats> stats = client.stats(null);
            assertNotNull("Stats should not be null", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_jwt_request: Unexpected exception");
        }
    }

    /**
     * Base request of a JWT token with wrong credentials (RSA8g RSA8c)
     */
    @Test
    public void auth_jwt_request_wrong_keys() {
        try {
            ClientOptions options = buildClientOptions(invalidKeys);
            AblyRest client = new AblyRest(options);
            PaginatedResult<Stats> stats = client.stats(null);
        } catch (AblyException e) {
            assertEquals("Unexpected code from exception", 40144, e.errorInfo.code);
            assertEquals("Unexpected statusCode from exception", UNAUTHORIZED.code, e.errorInfo.statusCode);
            assertTrue("Error message not matching the expected one", e.errorInfo.message.contains("signature verification failed"));
        }
    }

    /**
     * Request of a JWT token that embeds and Ably token (RSC1 RSC1a RSC1c RSA3d)
     */
    @Test
    public void auth_jwt_request_embedded_token() {
        try {
            ClientOptions options = buildClientOptions(mergeParams(new Param[][]{environment, validKeys, tokenEmbedded}));
            AblyRest client = new AblyRest(options);
            PaginatedResult<Stats> stats = client.stats(null);
            assertNotNull("Stats should not be null", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_jwt_request_embedded_token: Unexpected exception");
        }
    }

    /**
     * Request of a JWT token that embeds and Ably token and is encrypted (RSC1 RSC1a RSC1c RSA3d)
     */
    @Test
    public void auth_jwt_request_embedded_token_encrypted() {
        try {
            ClientOptions options = buildClientOptions(mergeParams(new Param[][]{environment, validKeys, tokenEmbeddedAndEncrypted}));
            AblyRest client = new AblyRest(options);
            PaginatedResult<Stats> stats = client.stats(null);
            assertNotNull("Stats should not be null", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_jwt_request_embedded_token_encrypted: Unexpected exception");
        }
    }

    /**
     * Request of a JWT token that is returned with application/jwt content type (RSA4f, RSA8c)
     */
    @Test
    public void auth_jwt_request_returntype() {
        try {
            ClientOptions options = createOptions();
            options.authUrl = echoServer;
            options.authParams = mergeParams(new Param[][]{environment, validKeys, jwtReturnType});
            AblyRest client = new AblyRest(options);
            PaginatedResult<Stats> stats = client.stats(null);
            assertNotNull("Stats should not be null", stats);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_jwt_request_returntype: Unexpected exception");
        }
    }

    /**
     * Request of a JWT token via authCallback (RSA8g)
     */
    @Test
    public void auth_jwt_request_authcallback() {
        try {
            final AblyRest restJWTRequester = new AblyRest(createOptions(testVars.keys[0].keyStr));
            final boolean[] callbackCalled = new boolean[] { false };
            Auth.TokenCallback authCallback = new Auth.TokenCallback() {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    callbackCalled[0] = true;
                    return restJWTRequester.auth.requestToken(params, null);
                }
            };
            ClientOptions optionsWithCallback = createOptions();
            optionsWithCallback.authCallback = authCallback;
            AblyRest client = new AblyRest(optionsWithCallback);
            PaginatedResult<Stats> stats = client.stats(null);
            assertNotNull("Stats should not be null", stats);
            assertTrue("Callback was not called", callbackCalled[0]);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_jwt_request_authcallback: Unexpected exception");
        }
    }

    /**
     * Helper to fetch a token with params via authUrl
     */
    private ClientOptions buildClientOptions(Param[] params) {
        try {
            ClientOptions options = createOptions();
            final String[] resultToken = new String[1];
            AblyRest rest = new AblyRest(createOptions(testVars.keys[0].keyStr));
            HttpHelpers.getUri(rest.httpCore, echoServer, null, params, new HttpCore.ResponseHandler() {
                @Override
                public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                    try {
                        resultToken[0] = new String(response.body, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        fail("Error in fetching a JWT token " + e);
                    }
                    return null;
                }
            });
            options.token = resultToken[0];
            return options;
        } catch (AblyException e) {
            fail("Failure in fetching a JWT token" + e);
            return null;
        }
    }

}
