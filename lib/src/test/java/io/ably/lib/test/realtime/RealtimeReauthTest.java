package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

/**
 * Created by VOstopolets on 8/26/16.
 */
public class RealtimeReauthTest extends ParameterizedTest {

	/**
	 * RTC8 (0.8 spec with 0.9 removal of AuthOptions.force)
	 *  If authorize is called
	 *  the client will obtain a new token, disconnect the current transport
	 *  and resume the connection
	 */
	@Test
	public void reauth_tokenDetails() {
		String wrongChannel = "wrongchannel";
		String rightChannel = "rightchannel";
		String testClientId = "testClientId";

		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);
			System.out.println("done init ably for token");

			/* get first token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			Capability capability = new Capability();
			capability.addResource(wrongChannel, "*");
			tokenParams.capability = capability.toString();
			tokenParams.clientId = testClientId;
			System.out.println("done get first token");

			Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", firstToken.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = testClientId;
			opts.tokenDetails = firstToken;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);
			System.out.println("connected");

			/* create a channel and check can't attach */
			Channel channel = ablyRealtime.channels.get(rightChannel);
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.attach(waiter);
			ErrorInfo error = waiter.waitFor();
			assertNotNull("Expected error", error);
			assertEquals("Verify error code 40160 (channel is denied access)", error.code, 40160);
			System.out.println("can't attach");

			/* get second token */
			tokenParams = new Auth.TokenParams();
			capability = new Capability();
			capability.addResource(wrongChannel, "*");
			capability.addResource(rightChannel, "*");
			tokenParams.capability = capability.toString();
			tokenParams.clientId = testClientId;
			System.out.println("got second token");

			Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", secondToken.token);

			/* reauthorize */
			Auth.AuthOptions authOptions = new Auth.AuthOptions();
			authOptions.key = testVars.keys[0].keyStr;
			authOptions.tokenDetails = secondToken;
			Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorize(null, authOptions);
			assertNotNull("Expected token value", reauthTokenDetails.token);
			System.out.println("done reauthorize");
			/* Delay 2s to allow connection to go disconnected (and probably
			 * then onto connecting and connected). This is a workaround for
			 * https://github.com/ably/ably-java/issues/180 */
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
			}

			/* re-attach to the channel */
			waiter = new Helpers.CompletionWaiter();
			System.out.println("attaching");
			channel.attach(waiter);

			/* verify onSuccess callback gets called */
			waiter.waitFor();
			System.out.println("waited for attach");
			assertThat(waiter.success, is(true));
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}
}
