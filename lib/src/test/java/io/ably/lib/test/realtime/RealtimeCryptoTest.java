package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class RealtimeCryptoTest extends ParameterizedTest {

	@Rule
	public Timeout testTimeout = Timeout.seconds(30);

	/**
	 * Connect to the service
	 * and publish an encrypted message on that channel using
	 * the default cipher params
	 */
	@Test
	public void single_send() {
		String channelName = "single_send_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel channel = ably.channels.get(channelName, channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Channel is not attached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
				messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Unexpected message received",
				messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null) {
				ably.close();
			}
		}
	}

	/**
	 * Connect to the service
	 * and publish an encrypted message on that channel using
	 * a 256-bit key
	 */
	@Test
	public void single_send_256() {
		String channelName = "single_send_256_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a key */
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
			keygen.init(256);
			byte[] key = keygen.generateKey().getEncoded();
			final CipherParams params = Crypto.getDefaultParams(key);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
			final Channel channel = ably.channels.get(channelName, channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals(
			    "Channel is not attached",
				channel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Unexpected message received",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception generating key");
		} finally {
			if(ably != null) {
				ably.close();
			}
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, subscribe to an event, and publish multiple
	 * messages on that channel
	 */
	private void _multiple_send(String channelName, int messageCount, long delay) {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* generate and remember message texts */
			String[] messageTexts = new String[messageCount];
			for(int i = 0; i < messageCount; i++) {
				messageTexts[i] = "Test message (_multiple_send) " + i;
			}
			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel channel = ably.channels.get(channelName, channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals(
			    "Channel is not attached",
				channel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channel.publish("test_event", messageTexts[i], msgComplete.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete.waitFor();
			assertTrue("Errors when sending messages", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals(
			    "Unexpected number of messages received",
			    messageWaiter.receivedMessages.size(), messageCount
			);

			/* check the correct plaintext recovered from the message */
			for(int i = 0; i < messageCount; i++) {
				assertTrue(
				    "Unexpected message received",
					messageTexts[i].equals(
					    messageWaiter.receivedMessages.get(i).data
					)
				);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null) {
				ably.close();
			}
		}
	}

	@Test
	public void multiple_send_2_200() {
		int messageCount = 2;
		long delay = 200L;
		_multiple_send("multiple_send_binary_2_200_" + testParams.name, messageCount, delay);
	}

	@Test
	public void multiple_send_20_100() {
		int messageCount = 20;
		long delay = 100L;
		_multiple_send("multiple_send_binary_20_100_" + testParams.name, messageCount, delay);
	}

	/**
	 * Connect twice to the service, using the default (binary) protocol
	 * and the text protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void single_send_binary_text() {
		String channelName = "single_send_binary_text_" + testParams.name;
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(senderOpts);
			ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
			receiverOpts.useBinaryProtocol = !testParams.useBinaryProtocol;
			receiver = new AblyRealtime(receiverOpts);

			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			final ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel senderChannel = sender.channels.get(channelName, senderChannelOpts);
			final ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel receiverChannel = receiver.channels.get(channelName, receiverChannelOpts);

			/* attach */
			senderChannel.attach();
			receiverChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
			assertEquals(
				"Sender channel is not attached",
				senderChannel.state, ChannelState.attached
			);
			assertEquals(
				"Receiver channel is not attached",
				receiverChannel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_binary_text)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			senderChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
				"Unexpected number of received messages",
				messageWaiter.receivedMessages.size(), 1
			);
			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Unexpected message received",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}

	/**
	 * Connect twice to the service, using different cipher keys.
	 * Publish an encrypted message on that channel using
	 * the default cipher params and verify that the decrypt failure
	 * is noticed as bad recovered plaintext.
	 */
	@Test
	public void single_send_key_mismatch() {
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(senderOpts);
			ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
			receiver = new AblyRealtime(receiverOpts);

			/* create a channel */
			final ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel senderChannel = sender.channels.get("single_send_binary_text", senderChannelOpts);
			final ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel receiverChannel = receiver.channels.get("single_send_binary_text", receiverChannelOpts);

			/* attach */
			senderChannel.attach();
			receiverChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
			assertEquals(
			    "Sender channel is not attached",
			    senderChannel.state, ChannelState.attached
			);
			assertEquals(
			    "Receiver channel is not attached",
			    receiverChannel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_key_mismatch)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			senderChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertFalse(
			    "Unexpected message received",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}


	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void single_send_unencrypted() {
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(senderOpts);
			ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
			receiver = new AblyRealtime(receiverOpts);

			/* create a channel */
			final Channel senderChannel = sender.channels.get("single_send_unencrypted");
			ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel receiverChannel = receiver.channels.get("single_send_unencrypted", receiverChannelOpts);

			/* attach */
			senderChannel.attach();
			receiverChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
			assertEquals(
			    "Sender channel is not attached",
			    senderChannel.state, ChannelState.attached
			);
			assertEquals(
			    "Receiver channel is not attached",
			    receiverChannel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_unencrypted)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			senderChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct text recovered from the message */
			assertTrue(
			    "Received message is not correct",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}

	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void single_send_encrypted_unhandled() {
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(senderOpts);
			ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
			receiver = new AblyRealtime(receiverOpts);

			/* create a channel */
			ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel senderChannel = sender.channels.get("single_send_encrypted_unhandled", senderChannelOpts);
			final Channel receiverChannel = receiver.channels.get("single_send_encrypted_unhandled");

			/* attach */
			senderChannel.attach();
			receiverChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
			assertEquals(
			    "Sender channel is not attached",
			    senderChannel.state, ChannelState.attached
			);
			assertEquals(
			    "Receiver channel is not attached",
			    receiverChannel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_encrypted_unhandled)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			senderChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of messages received",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the the message payload is indicated as encrypted */
//			assertTrue("Verify correct message text received", messageWaiter.receivedMessages.get(0).data instanceof CipherData);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}

	/**
	 * Check Channel.setOptions updates CipherParams correctly:
	 * - publish a message using a key, verifying correct receipt;
	 * - publish with an updated key on the tx connection and verify that it is not decrypted by the rx connection;
	 * - publish with an updated key on the rx connection and verify connect receipt
	 */
	@Test
	public void set_cipher_params() {
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions senderOpts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(senderOpts);
			ClientOptions receiverOpts = createOptions(testVars.keys[0].keyStr);
			receiverOpts.useBinaryProtocol = !testParams.useBinaryProtocol;
			receiver = new AblyRealtime(receiverOpts);

			/* create a key */
			final CipherParams params1 = Crypto.getDefaultParams();

			/* create a channel */
			ChannelOptions senderChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params1; }};
			final Channel senderChannel = sender.channels.get("set_cipher_params", senderChannelOpts);
			ChannelOptions receiverChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params1; }};
			final Channel receiverChannel = receiver.channels.get("set_cipher_params", receiverChannelOpts);

			/* attach */
			senderChannel.attach();
			receiverChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);

			assertEquals(
			    "Sender channel is not attached",
			    senderChannel.state, ChannelState.attached
			);
			assertEquals(
			    "Receiver channel is not attached",
			    receiverChannel.state, ChannelState.attached
			);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish to the channel */
			String messageText = "Test message (set_cipher_params)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			senderChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Received message is not correct",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

			/* create a second key and set sender channel opts */
			final CipherParams params2 = Crypto.getDefaultParams();
			senderChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params2; }};
			senderChannel.setOptions(senderChannelOpts);

			/* publish to the channel, wait, check message bad */
			messageWaiter.reset();
			senderChannel.publish("test_event", messageText, msgComplete);
			messageWaiter.waitFor(1);
			assertFalse(
			    "Received message is not correct",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

			/* See issue https://github.com/ably/ably-java/issues/202
			 * This final part of the test fails intermittently. For now just try
			 * it multiple times. */
			for (int count = 4;; --count) {
				assertTrue("Verify correct plaintext received", count != 0);

				/* set rx channel opts */
				receiverChannel.setOptions(senderChannelOpts);

				/* publish to the channel, wait, check message bad */
				messageWaiter.reset();
				senderChannel.publish("test_event", messageText, msgComplete);
				messageWaiter.waitFor(1);
				if (messageText.equals(messageWaiter.receivedMessages.get(0).data)) {
					break;
				}
			}

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}

	/**
	 * Test channel options creation from the cipher key.
	 *
	 * This test should be removed when we get rid of the methods
	 * ChannelOptions.fromCipherKey(...) which are deprecated and have
	 * been replaced with ChannelOptions.withCipherKey(...).
	 * @see <a href="https://docs.ably.io/client-lib-development-guide/features/#TB3>TB3</a>
	 */
	@Test
	@Deprecated
	public void channel_options_from_cipher_key() {
		String channelName = "cipher_params_test_" + testParams.name;
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(opts);
			receiver = new AblyRealtime(opts);

			/* 128-bit key */
			byte[] key = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
			/* Same key but encoded with Base64 */
			String base64key = "AQIDBAUGBwgJCgsMDQ4PEA==";

			/* create a sending channel using byte[] array */
			final Channel channelSend = sender.channels.get(channelName, ChannelOptions.fromCipherKey(key));
			/* create a receiving channel using (the same) key encoded with base64 */
			final Channel channelReceive = receiver.channels.get(channelName, ChannelOptions.fromCipherKey(base64key));

			/* attach */
			channelSend.attach();
			channelReceive.attach();

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelReceive);

			/* publish to the channel */
			String messageText = "Test message";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channelSend.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
			    messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Received message is not correct",
			    messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}

	/**
	 * Test channel options creation with the cipher key.
	 * @see <a href="https://docs.ably.io/client-lib-development-guide/features/#TB3>TB3</a>
	 */
	@Test
	public void channel_options_with_cipher_key() {
		String channelName = "cipher_params_test_" + testParams.name;
		AblyRealtime sender = null;
		AblyRealtime receiver = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			sender = new AblyRealtime(opts);
			receiver = new AblyRealtime(opts);

			/* 128-bit key */
			byte[] key = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
			/* Same key but encoded with Base64 */
			String base64key = "AQIDBAUGBwgJCgsMDQ4PEA==";

			/* create a sending channel using byte[] array */
			final Channel channelSend = sender.channels.get(channelName, ChannelOptions.withCipherKey(key));
			/* create a receiving channel using (the same) key encoded with base64 */
			final Channel channelReceive = receiver.channels.get(channelName, ChannelOptions.withCipherKey(base64key));

			/* attach */
			channelSend.attach();
			channelReceive.attach();

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelReceive);

			/* publish to the channel */
			String messageText = "Test message";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channelSend.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Success callback was not called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals(
			    "Unexpected number of received messages",
				messageWaiter.receivedMessages.size(), 1
			);

			/* check the correct plaintext recovered from the message */
			assertTrue(
			    "Received message is not correct",
				messageText.equals(messageWaiter.receivedMessages.get(0).data)
			);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}


	/**
	 * Test Crypto.getDefaultParams.
	 * @see <a href="https://docs.ably.io/client-lib-development-guide/features/#RSE1">RSE1</a>
	 * @throws AblyException If something goes wrong.
	 */
	@Test
	public void cipher_params() throws AblyException {
		/* 128-bit key */
		/* {0xFF, 0xFE, 0xFD, 0xFC, 0xFB, 0xFA, 0xF9, 0xF8, 0xF7, 0xF6, 0xF5, 0xF4, 0xF3, 0xF2, 0xF1, 0xF0}; */
		byte[] key = {-1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -15, -16};
		/* Same key but encoded with Base64 */
		String base64key = "//79/Pv6+fj39vX08/Lx8A==";
		/* Same key but encoded in URL style (RFC 4648 s.5) */
		String base64key2 = "__79_Pv6-fj39vX08_Lx8A==";

		/* IV */
		byte[] iv = {16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

		final CipherParams params1 = Crypto.getDefaultParams(key); params1.ivSpec = new IvParameterSpec(iv);
		final CipherParams params2 = Crypto.getDefaultParams(base64key); params2.ivSpec = new IvParameterSpec(iv);
		final CipherParams params3 = Crypto.getDefaultParams(params1); params3.ivSpec = new IvParameterSpec(iv);
		final CipherParams params4 = Crypto.getDefaultParams(base64key2); params4.ivSpec = new IvParameterSpec(iv);

		assertTrue(
		    "Key length is incorrect",
		    params1.keyLength == key.length*8 &&
		    params2.keyLength == key.length*8 &&
		    params3.keyLength == key.length*8 &&
		    params4.keyLength == key.length*8
		);

		byte[] plaintext = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		Crypto.ChannelCipher channelCipher1 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params1; }});
		Crypto.ChannelCipher channelCipher2 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params2; }});
		Crypto.ChannelCipher channelCipher3 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params3; }});
		Crypto.ChannelCipher channelCipher4 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params4; }});

		byte[] ciphertext1 = channelCipher1.encrypt(plaintext);
		byte[] ciphertext2 = channelCipher2.encrypt(plaintext);
		byte[] ciphertext3 = channelCipher3.encrypt(plaintext);
		byte[] ciphertext4 = channelCipher4.encrypt(plaintext);

		assertTrue(
		    "All ciphertexts should be the same.",
		    Arrays.equals(ciphertext1, ciphertext2) &&
		    Arrays.equals(ciphertext1, ciphertext3) &&
		    Arrays.equals(ciphertext1, ciphertext4)
		);
	}

	/**
	 * Test Crypto.generateRandomKey.
	 * @see <a href="https://docs.ably.io/client-lib-development-guide/features/#RSE2">RSE2</a>
	 */
	@Test
	public void generate_random_key() {
		final int numberOfRandomKeys = 50;
		final int randomKeyBits = 256;
		byte[][] randomKeys = new byte[numberOfRandomKeys][];

		for (int i=0; i<numberOfRandomKeys; i++) {
			randomKeys[i] = Crypto.generateRandomKey(randomKeyBits);
			assertEquals(
			    "Random key doesn't have the correct length",
			    randomKeys[i].length, (randomKeyBits + 7) / 8
			);
			for (int j=0; j<i; j++) {
				assertFalse(
				    "Found duplicates among randomly generated keys",
					Arrays.equals(randomKeys[i], randomKeys[j])
				);
			}
		}
	}
}
