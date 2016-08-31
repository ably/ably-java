package io.ably.lib.test.realtime;

import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by VOstopolets on 8/26/16.
 */
public class RealtimeReauthTest {

	/**
	 * RTC8 (0.8 spec)
	 *  If authorise is called with AuthOptions#force set to true
	 *  the client will obtain a new token, disconnect the current transport
	 *  and resume the connection
	 *
	 * use authorise({force: true}) to reauth with a token with a different set of capabilities
	 */
	@Test
	public void reauth_tokenDetails() {
		String wrongChannel = "wrongchannel";
		String rightChannel = "rightchannel";
		String testClientId = "testClientId";

		try {
			/* init ably for token */
			final Setup.TestVars optsTestVars = Setup.getTestVars();
			ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[0].keyStr);
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
			final Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions();
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

			/* reauthorise */
			Auth.AuthOptions authOptions = new Auth.AuthOptions();
			authOptions.key = optsTestVars.keys[0].keyStr;
			authOptions.tokenDetails = secondToken;
			authOptions.force = true;
			Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorise(authOptions, null);
			assertNotNull("Expected token value", reauthTokenDetails.token);
			System.out.println("done reauthorise");

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
