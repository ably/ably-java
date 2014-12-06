package io.ably.test.realtime;

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
	RealtimeInit.class,
	RealtimeConnect.class,
	RealtimeConnectFail.class,
	RealtimeChannel.class,
	RealtimePresence.class,
	RealtimeMessage.class,
	RealtimeResume.class,
	RealtimeRecover.class,
	RealtimeCrypto.class,
	RealtimeCryptoMessage.class,
	RealtimeChannelHistory.class,
	RealtimePresenceHistory.class
})
public class RealtimeSuite {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		RealtimeSetup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		RealtimeSetup.clearTestVars();
	}

	public static void main(String[] args) {
	    Result result = JUnitCore.runClasses(RealtimeSuite.class);
	    for(Failure failure : result.getFailures()) {
	      System.out.println(failure.toString());
	    }
	}
}
