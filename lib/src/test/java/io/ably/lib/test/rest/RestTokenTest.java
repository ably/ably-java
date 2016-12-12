package io.ably.lib.test.rest;

import static org.junit.Assert.*;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth.AuthOptions;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.Key;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;

import org.junit.BeforeClass;
import org.junit.Test;

public class RestTokenTest {

	private static String permitAll;
	private static AblyRest ably;
	private static long timeOffset;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Capability capability = new Capability();
		capability.addResource("*", "*");
		permitAll = capability.toString();
		TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
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
			long requestTime = timeOffset + System.currentTimeMillis();
			TokenDetails tokenDetails = ably.auth.requestToken(null, null);
			assertNotNull("Expected token value", tokenDetails.token);
			assertTrue("Unexpected issued time", (tokenDetails.issued >= (requestTime - 2000)) && (tokenDetails.issued <= (requestTime + 2000)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issued + 60*60*1000);
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
			long requestTime = timeOffset + System.currentTimeMillis();
			TokenDetails tokenDetails = ably.auth.requestToken(new TokenParams(), null);
			assertNotNull("Expected token value", tokenDetails.token);
			assertTrue("Unexpected issued time", (tokenDetails.issued >= (requestTime - 1000)) && (tokenDetails.issued <= (requestTime + 1000)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issued + 60*60*1000);
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
			long requestTime = timeOffset + System.currentTimeMillis();
			TokenParams tokenParams = new TokenParams();
			tokenParams.timestamp = requestTime;
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);
			assertTrue("Unexpected issued time", (tokenDetails.issued >= (requestTime - 1000)) && (tokenDetails.issued <= (requestTime + 1000)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issued + 60*60*1000);
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
		long requestTime = timeOffset + System.currentTimeMillis();
		TokenParams tokenParams = new TokenParams();
		tokenParams.timestamp = requestTime - 30*60*1000;
		try {
			ably.auth.requestToken(tokenParams, null);
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
			long requestTime = timeOffset + System.currentTimeMillis();
			AuthOptions authOptions = new AuthOptions();
			/* Unset fields in authOptions no longer inherit from stored values,
			 * so we need to set up authOptions.key manually. */
			authOptions.key = ably.options.key;
			authOptions.queryTime = true;
			TokenDetails tokenDetails = ably.auth.requestToken(null, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertTrue("Unexpected issued time", (tokenDetails.issued >= (requestTime - 1000)) && (tokenDetails.issued <= (requestTime + 1000)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issued + 60*60*1000);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authtime2: Unexpected exception");
		}
	}

	/**
	 * Base requestToken case with non-null but empty params
	 */
	@Test
	public void authclientid0() {
		try {
			long requestTime = timeOffset + System.currentTimeMillis();
			TokenParams tokenParams = new TokenParams();
			tokenParams.clientId = "test client id";
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);
			assertTrue("Unexpected issued time", (tokenDetails.issued >= (requestTime - 2000)) && (tokenDetails.issued <= (requestTime + 2000)));
			assertEquals("Unexpected expires time", tokenDetails.expires, tokenDetails.issued + 60*60*1000);
			assertEquals("Unexpected capability", tokenDetails.capability, permitAll);
			assertEquals("Unexpected clientId", tokenDetails.clientId, tokenParams.clientId);
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
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);
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
			Key key = Setup.getTestVars().keys[1];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenDetails tokenDetails = ably.auth.requestToken(null, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authkey0: Unexpected exception");
		}
	}

	/**
	 * Token generation with specified ttl
	 */
	@Test
	public void authttl0() {
		try {
			TokenParams tokenParams = new TokenParams();
			tokenParams.ttl = 100*1000;
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected expires", tokenDetails.expires, tokenDetails.issued + 100*1000);
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
		tokenParams.ttl = 365*24*60*60*1000;
		try {
			ably.auth.requestToken(tokenParams, null);
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
			ably.auth.requestToken(tokenParams, null);
			fail("Expected token request rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40003);
		}
	}

}
