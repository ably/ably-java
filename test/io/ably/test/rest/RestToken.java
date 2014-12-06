package io.ably.test.rest;

import static org.junit.Assert.*;
import io.ably.rest.AblyRest;
import io.ably.rest.Auth.AuthOptions;
import io.ably.rest.Auth.TokenDetails;
import io.ably.rest.Auth.TokenParams;
import io.ably.test.rest.RestSetup.Key;
import io.ably.test.rest.RestSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.Capability;
import io.ably.types.Options;

import org.junit.BeforeClass;
import org.junit.Test;

public class RestToken {

	private static String permitAll;
	private static AblyRest ably;
	private static long timeOffset;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Capability capability = new Capability();
		capability.addResource("*", "*");
		permitAll = capability.toString();
		TestVars testVars = RestSetup.getTestVars();
		Options opts = testVars.createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
		long timeFromService = ably.time();
		timeOffset = timeFromService - System.currentTimeMillis();
	}

	/**
	 * Base requestToken case with null params
	 */
	@Test
	public void authrequesttoken0() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertNotNull("Expected token id", tokenDetails.id);
			assertTrue("Unexpected issuedAt time", (tokenDetails.issuedAt >= (requestTime - 2)) && (tokenDetails.issuedAt <= (requestTime + 2)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issuedAt + 60*60);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authrequesttoken0: Unexpected exception");
		}
	}

	/**
	 * Base requestToken case with non-null but empty params
	 */
	@Test
	public void authrequesttoken1() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			TokenDetails tokenDetails = ably.auth.requestToken(null, new TokenParams());
			assertNotNull("Expected token id", tokenDetails.id);
			assertTrue("Unexpected issuedAt time", (tokenDetails.issuedAt >= (requestTime - 1)) && (tokenDetails.issuedAt <= (requestTime + 1)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issuedAt + 60*60);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authrequesttoken1: Unexpected exception");
		}
	}

	/**
	 * requestToken with explicit timestamp
	 */
	@Test
	public void authtime0() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			TokenParams tokenParams = new TokenParams();
			tokenParams.timestamp = requestTime;
			TokenDetails tokenDetails = ably.auth.requestToken(null, tokenParams);
			assertNotNull("Expected token id", tokenDetails.id);
			assertTrue("Unexpected issuedAt time", (tokenDetails.issuedAt >= (requestTime - 1)) && (tokenDetails.issuedAt <= (requestTime + 1)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issuedAt + 60*60);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authtime0: Unexpected exception");
		}
	}

	/**
	 * requestToken with explicit, invalid timestamp
	 */
	@Test
	public void authtime1() {
		long requestTime = (timeOffset + System.currentTimeMillis())/1000;
		TokenParams tokenParams = new TokenParams();
		tokenParams.timestamp = requestTime - 30 * 60;
		try {
			ably.auth.requestToken(null, tokenParams);
			fail("Expected token request rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40101);
		}
	}

	/**
	 * requestToken with system timestamp
	 */
	@Test
	public void authtime2() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			AuthOptions authOptions = new AuthOptions();
			authOptions.queryTime = true;
			TokenDetails tokenDetails = ably.auth.requestToken(authOptions, null);
			assertNotNull("Expected token id", tokenDetails.id);
			assertTrue("Unexpected issuedAt time", (tokenDetails.issuedAt >= (requestTime - 1)) && (tokenDetails.issuedAt <= (requestTime + 1)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issuedAt + 60*60);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authtime2: Unexpected exception");
		}
	}

	/**
	 * requestToken with duplicate nonce
	 */
	@Test
	public void authnonce0() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			TokenParams tokenParams = new TokenParams();
			tokenParams.timestamp = requestTime;
			tokenParams.nonce = "1234567890123456";
			TokenDetails tokenDetails = ably.auth.requestToken(null, tokenParams);
			assertNotNull("Expected token id", tokenDetails.id);
			try {
				ably.auth.requestToken(null, tokenParams);
			} catch(AblyException e) {
				assertEquals("Unexpected error code", e.errorInfo.code, 40101);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authtime0: Unexpected exception");
		}
	}

	/**
	 * Base requestToken case with non-null but empty params
	 */
	@Test
	public void authclientid0() {
		try {
			long requestTime = (timeOffset + System.currentTimeMillis())/1000;
			TokenParams tokenParams = new TokenParams();
			tokenParams.client_id = "test client id";
			TokenDetails tokenDetails = ably.auth.requestToken(null, tokenParams);
			assertNotNull("Expected token id", tokenDetails.id);
			assertTrue("Unexpected issuedAt time", (tokenDetails.issuedAt >= (requestTime - 2)) && (tokenDetails.issuedAt <= (requestTime + 2)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issuedAt + 60*60);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
			assertEquals("Unexpected clientId", tokenDetails.clientId, tokenParams.client_id);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authclientid0: Unexpected exception");
		}
	}

	/**
	 * Token generation with capability that subsets key capability
	 */
	@Test
	public void authcapability0() {
		try {
			TokenParams tokenParams = new TokenParams();
			Capability capability = new Capability();
			capability.addResource("onlythischannel", "subscribe");
			String capabilityText = tokenParams.capability = capability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(null, tokenParams);
			assertNotNull("Expected token id", tokenDetails.id);
			assertEquals("Unexpected capability", tokenDetails.capability, capabilityText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability0: Unexpected exception");
		}
	}

	/**
	 * Token generation with specified key
	 */
	@Test
	public void authkey0() {
		try {
			Key key = RestSetup.getTestVars().keys[1];
			AuthOptions authOptions = new AuthOptions();
			authOptions.keyId = key.keyId;
			authOptions.keyValue = key.keyValue;
			TokenDetails tokenDetails = ably.auth.requestToken(authOptions, null);
			assertNotNull("Expected token id", tokenDetails.id);
			assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authkey0: Unexpected exception");
		}
	}

	/**
	 * requestToken with invalid mac
	 */
	@Test
	public void authmac0() {
		TokenParams tokenParams = new TokenParams();
		tokenParams.mac = "thisisnotavalidmac";
		try {
			ably.auth.requestToken(null, tokenParams);
			fail("Expected token request rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40101);
		}
	}

	/**
	 * Token generation with specified ttl
	 */
	@Test
	public void authttl0() {
		try {
			TokenParams tokenParams = new TokenParams();
			tokenParams.ttl = 100;
			TokenDetails tokenDetails = ably.auth.requestToken(null, tokenParams);
			assertNotNull("Expected token id", tokenDetails.id);
			assertEquals("Unexpected expires", tokenDetails.expires, tokenDetails.issuedAt + 100);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authttl0: Unexpected exception");
		}
	}

	/**
	 * Token generation with excessive ttl
	 */
	@Test
	public void authttl1() {
		TokenParams tokenParams = new TokenParams();
		tokenParams.ttl = 365*24*60*60;
		try {
			ably.auth.requestToken(null, tokenParams);
			fail("Expected token request rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40003);
		}
	}

	/**
	 * Token generation with invalid ttl
	 */
	@Test
	public void authttl2() {
		TokenParams tokenParams = new TokenParams();
		tokenParams.ttl = -1;
		try {
			ably.auth.requestToken(null, tokenParams);
			fail("Expected token request rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40003);
		}
	}

}
