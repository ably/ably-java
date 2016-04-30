package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

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

public class RestCapabilityTest {

	private static AblyRest ably;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
	}

	/**
	 * Blanket intersection with specified key
	 */
	@Test
	public void authcapability0() {
		try {
			Key key = Setup.getTestVars().keys[1];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenDetails tokenDetails = ably.auth.requestToken(null, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability0: Unexpected exception");
		}
	}

	/**
	 * Equal intersection with specified key
	 */
	@Test
	public void authcapability1() {
		try {
			Key key = Setup.getTestVars().keys[1];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			tokenParams.capability = key.capability;
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, key.capability);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability1: Unexpected exception");
		}
	}

	/**
	 * Empty ops intersection
	 */
	@Test
	public void authcapability2() {
		Key key = Setup.getTestVars().keys[1];
		AuthOptions authOptions = new AuthOptions();
		authOptions.key = key.keyStr;
		TokenParams tokenParams = new TokenParams();
		Capability capability = new Capability();
		capability.addResource("testchannel", "subscribe");
		tokenParams.capability = capability.toString();
		try {
			ably.auth.requestToken(tokenParams, authOptions);
			fail("Invalid capability, expected rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40160);
		}
	}

	/**
	 * Empty paths intersection
	 */
	@Test
	public void authcapability3() {
		Key key = Setup.getTestVars().keys[1];
		AuthOptions authOptions = new AuthOptions();
		authOptions.key = key.keyStr;
		TokenParams tokenParams = new TokenParams();
		Capability capability = new Capability();
		capability.addResource("testchannelx", "publish");
		tokenParams.capability = capability.toString();
		try {
			ably.auth.requestToken(tokenParams, authOptions);
			fail("Invalid capability, expected rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40160);
		}
	}

	/**
	 * Non-empty ops intersection 
	 */
	@Test
	public void authcapability4() {
		try {
			Key key = Setup.getTestVars().keys[4];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("channel2", new String[]{"presence", "subscribe"});
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			Capability expectedCapability = new Capability();
			expectedCapability.addResource("channel2", "subscribe");
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability4: Unexpected exception");
		}
	}

	/**
	 * Non-empty paths intersection 
	 */
	@Test
	public void authcapability5() {
		try {
			Key key = Setup.getTestVars().keys[4];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("channel2", new String[]{"presence", "subscribe"});
			requestedCapability.addResource("channelx", new String[]{"presence", "subscribe"});
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			Capability expectedCapability = new Capability();
			expectedCapability.addResource("channel2", "subscribe");
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability5: Unexpected exception");
		}
	}

	/**
	 * Wildcard ops intersection 
	 */
	@Test
	public void authcapability6() {
		try {
			Key key = Setup.getTestVars().keys[4];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("channel2", "*");
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			Capability expectedCapability = new Capability();
			expectedCapability.addResource("channel2", new String[]{"publish", "subscribe"});
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability6: Unexpected exception");
		}
	}
	@Test
	public void authcapability7() {
		try {
			Key key = Setup.getTestVars().keys[4];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("channel6", new String[]{"publish", "subscribe"});
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			Capability expectedCapability = new Capability();
			expectedCapability.addResource("channel6", new String[]{"publish", "subscribe"});
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, expectedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability7: Unexpected exception");
		}
	}

	/**
	 * Wildcard resources intersection 
	 */
	@Test
	public void authcapability8() {
		try {
			Key key = Setup.getTestVars().keys[2];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("cansubscribe", "subscribe");
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability8: Unexpected exception");
		}
	}
	@Test
	public void authcapability9() {
		try {
			Key key = Setup.getTestVars().keys[2];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("canpublish:check", "publish");
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability9: Unexpected exception");
		}
	}
	@Test
	public void authcapability10() {
		try {
			Key key = Setup.getTestVars().keys[2];
			AuthOptions authOptions = new AuthOptions();
			authOptions.key = key.keyStr;
			TokenParams tokenParams = new TokenParams();
			Capability requestedCapability = new Capability();
			requestedCapability.addResource("cansubscribe:*", "subscribe");
			tokenParams.capability = requestedCapability.toString();
			TokenDetails tokenDetails = ably.auth.requestToken(tokenParams, authOptions);
			assertNotNull("Expected token value", tokenDetails.token);
			assertEquals("Unexpected capability", tokenDetails.capability, requestedCapability.toString());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("authcapability10: Unexpected exception");
		}
	}

	/**
	 * Invalid capabilities
	 */
	@Test
	public void authinvalid0() {
		TokenParams tokenParams = new TokenParams();
		Capability invalidCapability = new Capability();
		invalidCapability.addResource("channel0", "publish_");
		tokenParams.capability = invalidCapability.toString();
		try {
			ably.auth.requestToken(tokenParams, null);
			fail("Invalid capability, expected rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40000);
		}
	}
	@Test
	public void authinvalid1() {
		TokenParams tokenParams = new TokenParams();
		Capability invalidCapability = new Capability();
		invalidCapability.addResource("channel0", new String[]{"*", "publish"});
		tokenParams.capability = invalidCapability.toString();
		try {
			ably.auth.requestToken(tokenParams, null);
			fail("Invalid capability, expected rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40000);
		}
	}
	@Test
	public void authinvalid2() {
		TokenParams tokenParams = new TokenParams();
		Capability invalidCapability = new Capability();
		invalidCapability.addResource("channel0", new String[0]);
		tokenParams.capability = invalidCapability.toString();
		try {
			ably.auth.requestToken(tokenParams, null);
			fail("Invalid capability, expected rejection");
		} catch(AblyException e) {
			assertEquals("Unexpected error code", e.errorInfo.code, 40000);
		}
	}
}
