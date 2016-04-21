package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RealtimeCryptoTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and publish an encrypted message on that channel using
	 * the default cipher params
	 */
	@Test
	public void single_send_binary() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel channel = ably.channels.get("subscribe_send_binary", channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the text protocol
	 * and publish an encrypted message on that channel using
	 * the default cipher params
	 */
	@Test
	public void single_send_text() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel channel = ably.channels.get("subscribe_send_text", channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and publish an encrypted message on that channel using
	 * a 256-bit key
	 */
	@Test
	public void single_send_binary_256() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a key */
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
	        keygen.init(256);
	        byte[] key = keygen.generateKey().getEncoded();
			final CipherParams params = Crypto.getDefaultParams(key);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
			final Channel channel = ably.channels.get("subscribe_send_binary", channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception generating key");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the text protocol
	 * and publish an encrypted message on that channel using
	 * a 256-bit key
	 */
	@Test
	public void single_send_text_256() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a key */
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
	        keygen.init(256);
	        byte[] key = keygen.generateKey().getEncoded();
			final CipherParams params = Crypto.getDefaultParams(key);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
			final Channel channel = ably.channels.get("subscribe_send_binary", channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			String messageText = "Test message (subscribe_send_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception generating key");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, subscribe to an event, and publish multiple
	 * messages on that channel
	 */
	private void _multiple_send(String channelName, boolean useBinaryProtocol, int messageCount, long delay) {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = useBinaryProtocol;
			ably = new AblyRealtime(opts);

			/* generate and remember message texts */
			String[] messageTexts = new String[messageCount];
			for(int i = 0; i < messageCount; i++)
				messageTexts[i] = "Test message (_multiple_send) " + i;

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel channel = ably.channels.get(channelName, channelOpts);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

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
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

			/* check the correct plaintext recovered from the message */
			for(int i = 0; i < messageCount; i++)
				assertTrue("Verify correct plaintext received", messageTexts[i].equals(messageWaiter.receivedMessages.get(i).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void multiple_send_binary_2_200() {
		int messageCount = 2;
		long delay = 200L;
		_multiple_send("multiple_send_binary_2_200", false, messageCount, delay);
	}

	@Test
	public void multiple_send_text_2_200() {
		int messageCount = 2;
		long delay = 200L;
		_multiple_send("multiple_send_text_2_200", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_20_100() {
		int messageCount = 20;
		long delay = 100L;
		_multiple_send("multiple_send_binary_20_100", false, messageCount, delay);
	}

	@Test
	public void multiple_send_text_20_100() {
		int messageCount = 20;
		long delay = 100L;
		_multiple_send("multiple_send_text_20_100", true, messageCount, delay);
	}

	/**
	 * Connect twice to the service, using the default (binary) protocol
	 * and the text protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void single_send_binary_text() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxOpts.useBinaryProtocol = false;
			rxAbly = new AblyRealtime(rxOpts);

			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			final ChannelOptions txChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel txChannel = txAbly.channels.get("single_send_binary_text", txChannelOpts);
			ChannelOptions rxChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel rxChannel = rxAbly.channels.get("single_send_binary_text", rxChannelOpts);

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_binary_text)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
		}
	}

	/**
	 * Connect twice to the service, using the text protocol and the
	 * default (binary) protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void single_send_text_binary() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txOpts.useBinaryProtocol = false;
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);

			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			final ChannelOptions txChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel txChannel = txAbly.channels.get("single_send_binary_text", txChannelOpts);
			ChannelOptions rxChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			final Channel rxChannel = rxAbly.channels.get("single_send_binary_text", rxChannelOpts);

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_text_binary)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
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
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);

			/* create a channel */
			final ChannelOptions txChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel txChannel = txAbly.channels.get("single_send_binary_text", txChannelOpts);
			ChannelOptions rxChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel rxChannel = rxAbly.channels.get("single_send_binary_text", rxChannelOpts);

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_key_mismatch)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertFalse("Verify correct plaintext not received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
		}
	}


	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void single_send_unencrypted() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);

			/* create a channel */
			final Channel txChannel = txAbly.channels.get("single_send_unencrypted");
			ChannelOptions rxChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel rxChannel = rxAbly.channels.get("single_send_unencrypted", rxChannelOpts);

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_unencrypted)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct text recovered from the message */
			assertTrue("Verify correct message text received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
		}
	}

	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void single_send_encrypted_unhandled() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);

			/* create a channel */
			ChannelOptions txChannelOpts = new ChannelOptions() {{ encrypted = true; }};
			final Channel txChannel = txAbly.channels.get("single_send_encrypted_unhandled", txChannelOpts);
			final Channel rxChannel = rxAbly.channels.get("single_send_encrypted_unhandled");

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (single_send_encrypted_unhandled)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the the message payload is indicated as encrypted */
//			assertTrue("Verify correct message text received", messageWaiter.receivedMessages.get(0).data instanceof CipherData);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
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
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxOpts.useBinaryProtocol = false;
			rxAbly = new AblyRealtime(rxOpts);

			/* create a key */
			final CipherParams params1 = Crypto.getDefaultParams();

			/* create a channel */
			ChannelOptions txChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params1; }};
			final Channel txChannel = txAbly.channels.get("set_cipher_params", txChannelOpts);
			ChannelOptions rxChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params1; }};
			final Channel rxChannel = rxAbly.channels.get("set_cipher_params", rxChannelOpts);

			/* attach */
			txChannel.attach();
			rxChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			String messageText = "Test message (set_cipher_params)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

			/* check the correct plaintext recovered from the message */
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

			/* create a second key and set tx channel opts */
			final CipherParams params2 = Crypto.getDefaultParams();
			txChannelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params2; }};
			txChannel.setOptions(txChannelOpts);

			/* publish to the channel, wait, check message bad */
			messageWaiter.reset();
			txChannel.publish("test_event", messageText, msgComplete);
			messageWaiter.waitFor(1);
			assertFalse("Verify correct plaintext not received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

			/* set rx channel opts */
			rxChannel.setOptions(txChannelOpts);

			/* publish to the channel, wait, check message bad */
			messageWaiter.reset();
			txChannel.publish("test_event", messageText, msgComplete);
			messageWaiter.waitFor(1);
			assertTrue("Verify correct plaintext received", messageText.equals(messageWaiter.receivedMessages.get(0).data));

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
		}
	}

}
