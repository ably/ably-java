package io.ably.lib.test.rest;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.ably.lib.test.common.Setup;

@RunWith(Suite.class)
@SuiteClasses({
	HttpTest.class,
	HttpHeaderTest.class,
	RestAppStatsTest.class,
	RestInitTest.class,
	RestTimeTest.class,
	RestAuthTest.class,
	RestAuthAttributeTest.class,
	RestTokenTest.class,
	RestCapabilityTest.class,
	RestChannelHistoryTest.class,
	RestChannelPublishTest.class,
	RestCryptoTest.class,
	RestPresenceTest.class,
	RestProxyTest.class
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

	public static void main(String[] args) {
	    Result result = JUnitCore.runClasses(RestSuite.class);
	    for(Failure failure : result.getFailures()) {
	      System.out.println(failure.toString());
	    }
	}
}
