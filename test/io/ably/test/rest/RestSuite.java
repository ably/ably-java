package io.ably.test.rest;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	RestAppStats.class,
	RestInit.class,
	RestTime.class,
	RestAuth.class,
	RestToken.class,
	RestCapability.class,
	RestChannelHistory.class,
	RestChannelPublish.class,
	RestCrypto.class,
	RestPresence.class
})
public class RestSuite {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		RestSetup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		RestSetup.clearTestVars();
	}

	public static void main(String[] args) {
	    Result result = JUnitCore.runClasses(RestSuite.class);
	    for(Failure failure : result.getFailures()) {
	      System.out.println(failure.toString());
	    }
	}
}
