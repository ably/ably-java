package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.KeyGenerator;

import org.junit.Before;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

public class RestCryptoTest extends ParameterizedTest {

	private static final String TAG = RestCryptoTest.class.getName();
	private AblyRest ably;
	private AblyRest ably_alt;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
		ClientOptions opts_alt = createOptions(testVars.keys[0].keyStr);
		opts_alt.useBinaryProtocol = testParams.useBinaryProtocol;
		ably_alt = new AblyRest(opts_alt);
	}

	/**
	 * Publish events with data of various datatypes using text protocol
	 */
	@Test
	public void crypto_publish() {
		/* first, publish some messages */
		Channel publish0;
		try {
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			publish0 = ably.channels.get("persisted:crypto_publish_" + testParams.name, channelOpts);

			publish0.publish("publish0", "This is a string message payload");
			publish0.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = publish0.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect publish0 to be expected String", messageContents.get("publish0"), "This is a string message payload");
			assertEquals("Expect publish1 to be expected byte[]", new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events with data of various datatypes using text protocol with a 256-bit key
	 */
	@Test
	public void crypto_publish_256() {
		/* first, publish some messages */
		Channel publish0;
		try {
			/* create a key */
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
			keygen.init(256);
			byte[] key = keygen.generateKey().getEncoded();
			final CipherParams params = Crypto.getDefaultParams(key);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
			publish0 = ably.channels.get("persisted:crypto_publish_256_" + testParams.name, channelOpts);

			publish0.publish("publish0", "This is a string message payload");
			publish0.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception generating key");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = publish0.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect publish0 to be expected String", messageContents.get("publish0"), "This is a string message payload");
			assertEquals("Expect publish1 to be expected byte[]", new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Connect twice to the service, using the default (binary) protocol
	 * and the text protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void crypto_publish_alt() {
		/* first, publish some messages */
		Channel tx_publish;
		ChannelOptions channelOpts;
		String channelName = "persisted:crypto_publish_alt_" + testParams.name;
		try {
			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			channelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			tx_publish = ably.channels.get(channelName, channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			Channel rx_publish = ably_alt.channels.get(channelName, channelOpts);
			PaginatedResult<Message> messages = rx_publish.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect publish0 to be expected String", messageContents.get("publish0"), "This is a string message payload");
			assertEquals("Expect publish1 to be expected byte[]", new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Connect twice to the service, using different cipher keys.
	 * Publish an encrypted message on that channel using
	 * the default cipher params and verify that the decrypt failure
	 * is noticed as bad recovered plaintext.
	 */
	@Test
	public void crypto_publish_key_mismatch() {
		/* first, publish some messages */
		Channel tx_publish;
		String channelName = "persisted:crypto_publish_key_mismatch_" + testParams.name;
		try {
			/* create a channel */
			ChannelOptions tx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
			tx_publish = ably.channels.get(channelName, tx_channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			ChannelOptions rx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
			Channel rx_publish = ably.channels.get(channelName, rx_channelOpts);

			PaginatedResult<Message> messages = rx_publish.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "2") });
			for (Message failedMessage: messages.items())
				assertTrue("Check decrypt failure", failedMessage.encoding.contains("cipher"));
		} catch (AblyException e) {
			fail("Didn't expect exception");
		}
	}

	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void crypto_send_unencrypted() {
		String channelName = "persisted:crypto_send_unencrypted_" + testParams.name;
		/* first, publish some messages */
		try {
			/* create a channel */
			Channel tx_publish = ably.channels.get(channelName);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("crypto_send_unencrypted: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			Channel rx_publish = ably.channels.get(channelName, channelOpts);
			PaginatedResult<Message> messages = rx_publish.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect publish0 to be expected String", messageContents.get("publish0"), "This is a string message payload");
			assertEquals("Expect publish1 to be expected byte[]", new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an encrypted message and verify that the receiving connection
	 * is unable to decrypt it and leaves it as encoded cipher data
	 */
	@Test
	public void crypto_send_encrypted_unhandled() {
		String channelName = "persisted:crypto_send_encrypted_unhandled_" + testParams.name;
		/* first, publish some messages */
		try {
			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			Channel tx_publish = ably.channels.get(channelName, channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			Channel rx_publish = ably_alt.channels.get(channelName);
			PaginatedResult<Message> messages = rx_publish.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			assertTrue("Expect publish0 to be unprocessed CipherData", messageContents.get("publish0").encoding.contains("cipher"));
			assertTrue("Expect publish1 to be unprocessed CipherData", messageContents.get("publish1").encoding.contains("cipher"));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("crypto_send_encrypted_unhandled: Unexpected exception");
			return;
		}
	}
}
