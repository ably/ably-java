package io.ably.lib.test.realtime;

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
	ConnectionParamTest.class,
	ConnectionManagerTest.class,
	HostsTest.class,
	EventEmitterTest.class,
	RealtimeHttpHeaderTest.class,
	RealtimeReauthTest.class,
	RealtimeInitTest.class,
	RealtimeConnectTest.class,
	RealtimeConnectFailTest.class,
	RealtimeChannelTest.class,
	RealtimePresenceTest.class,
	RealtimeMessageTest.class,
	RealtimeResumeTest.class,
	RealtimeRecoverTest.class,
	RealtimeCryptoTest.class,
	RealtimeCryptoMessageTest.class,
	RealtimeChannelHistoryTest.class,
	RealtimePresenceHistoryTest.class
})
public class RealtimeSuite {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	public static void main(String[] args) {
	    Result result = JUnitCore.runClasses(RealtimeSuite.class);
	    for(Failure failure : result.getFailures()) {
	      System.out.println(failure.toString());
	    }
	}
}
