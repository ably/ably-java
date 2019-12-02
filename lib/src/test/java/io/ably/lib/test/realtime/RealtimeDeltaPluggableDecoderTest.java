package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.ablydeltacodec.AblyVcdiffCodec;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.*;

public class RealtimeDeltaPluggableDecoderTest extends ParameterizedTest {
	private static final String[] testData = new String[] {
		"{ foo: \"bar\", count: 1, status: \"active\" }",
		"{ foo: \"bar\", count: 2, status: \"active\" }",
		"{ foo: \"bar\", count: 2, status: \"inactive\" }",
		"{ foo: \"bar\", count: 3, status: \"inactive\" }",
		"{ foo: \"bar\", count: 3, status: \"active\" }"
	};

	@Rule
	public Timeout testTimeout = Timeout.seconds(300);

	@Test
	public void simple_delta_codec() {
		AblyRealtime ably = null;
		String testName = "simple_delta_codec";
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			MonitoredCodec monitoredCodec = new MonitoredCodec(new AblyVcdiffCodec());
			opts.Codecs.put(PluginType.vcdiffDecoder, monitoredCodec);
			ably = new AblyRealtime(opts);
			Channel channel = ably.channels.get("[?delta=vcdiff]" + testName);

			/* subscribe */
			MessageWaiter messageWaiter = new MessageWaiter(channel);

			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);

			for (int i = 0; i < testData.length; i++) {
				channel.publish(Integer.toString(i), testData[i]);
			}

			messageWaiter.waitFor(testData.length);
			assertEquals("Verify number of received messages", testData.length, messageWaiter.receivedMessages.size());
			for (int i = 0; i < messageWaiter.receivedMessages.size(); i++) {
				Message message = messageWaiter.receivedMessages.get(i);
				int messageIndex = Integer.parseInt(message.name);
				assertEquals("Verify message order", i, messageIndex);
				assertEquals("Verify message data", true, testData[messageIndex].equals(message.data));
			}
			assertEquals("Verify number of calls to the codec", testData.length - 1, monitoredCodec.numberOfCalls);
		} catch(Exception e) {
			fail(testName + ": Unexpected exception " + e.getMessage());
			e.printStackTrace();
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	private static class MonitoredCodec implements VCDiffPluggableCodec {
		private VCDiffPluggableCodec codec;
		private int numberOfCalls = 0;

		MonitoredCodec(VCDiffPluggableCodec codec) {
			this.codec = codec;
		}

		@Override
		public byte[] decode(byte[] delta, byte[] base) throws MessageDecodeException {
			this.numberOfCalls++;
			return this.codec.decode(delta, base);
		}
	}
}
