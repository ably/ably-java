package io.ably.lib.test.rest;

import static org.junit.Assert.*;

import io.ably.lib.test.common.Setup.Key;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.*;
import io.ably.lib.rest.Auth.*;
import io.ably.lib.test.common.ParameterizedTest;

public class RestJWTTest extends ParameterizedTest {

	private AblyRest restJWTRequester;
	private ClientOptions jwtRequesterOptions;
	private Key key = testVars.keys[0];
	Param[] validKeys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", key.keySecret) };
	Param[] invalidKeys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", "invalidinvalid") };
	Param[] tokenEmbedded = new Param[]{ new Param("jwtType", "embedded") };
	Param[] tokenEmbeddedAndEncrypted = new Param[]{ new Param("jwtType", "embedded"), new Param("encrypted", 1) };
	Param[] jwtReturnType = new Param[]{ new Param("returnType", "jwt") };

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
			assertEquals("Unexpected statusCode from exception", 401, e.errorInfo.statusCode);
			assertEquals("Error message not matching the expected one", "Error verifying JWT; err = invalid signature", e.errorInfo.message);
		}
	}

	/**
	 * Request of a JWT token that embeds and Ably token (RSC1 RSC1a RSC1c RSA3d)
	 */
	@Test
	public void auth_jwt_request_embedded_token() {
		try {
			ClientOptions options = buildClientOptions(mergeParams(validKeys, tokenEmbedded));
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
			ClientOptions options = buildClientOptions(mergeParams(validKeys, tokenEmbeddedAndEncrypted));
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
			options.authParams = mergeParams(validKeys, jwtReturnType);
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
			restJWTRequester = new AblyRest(createOptions(testVars.keys[0].keyStr));
			TokenCallback authCallback = new TokenCallback() {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					return restJWTRequester.auth.requestToken(params, null);
				}
			};
			ClientOptions optionsWithCallback = createOptions();
			optionsWithCallback.authCallback = authCallback;
			AblyRest client = new AblyRest(optionsWithCallback);
			PaginatedResult<Stats> stats = client.stats(null);
			assertNotNull("Stats should not be null", stats);
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
			jwtRequesterOptions = createOptions(testVars.keys[0].keyStr);
			jwtRequesterOptions.authUrl = echoServer;
			jwtRequesterOptions.authParams = params;
			restJWTRequester = new AblyRest(jwtRequesterOptions);
			TokenDetails tokenDetails = restJWTRequester.auth.requestToken(null, null);
			options.token = tokenDetails.token;
			return options;
		} catch (AblyException e) {
			fail("Failure in fetching a JWT token" + e);
			return null;
		}
	}

}
