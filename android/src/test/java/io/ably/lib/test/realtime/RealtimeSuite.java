package io.ably.lib.test.realtime;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.ably.lib.test.common.Setup;

@RunWith(Suite.class)
@SuiteClasses({
	EventEmitterTest.class,
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
}
