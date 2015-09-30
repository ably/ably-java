package io.ably.lib.test.android.realtime;

import android.support.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.ably.lib.test.common.Setup;
import io.ably.lib.test.realtime.*;

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
