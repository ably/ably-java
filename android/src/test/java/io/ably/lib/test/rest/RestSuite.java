package io.ably.lib.test.rest;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.ably.lib.test.common.Setup;

@RunWith(Suite.class)
@SuiteClasses({
	RestAppStatsTest.class,
	RestInitTest.class,
	RestTimeTest.class,
	RestAuthTest.class,
	RestTokenTest.class,
	RestCapabilityTest.class,
	RestChannelHistoryTest.class,
	RestChannelPublishTest.class,
	RestCryptoTest.class,
	RestPresenceTest.class,
	RestProxyTest.class,
	HttpTest.class
})
public class RestSuite {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}
}
