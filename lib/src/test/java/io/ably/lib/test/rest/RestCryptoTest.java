package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.KeyGenerator;

import org.junit.BeforeClass;
import org.junit.Test;

public class RestCryptoTest {

	private static final String TAG = RestCryptoTest.class.getName();
	private static AblyRest ably_text;
	private static AblyRest ably_binary;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = Setup.getTestVars();

		ClientOptions opts_text = new ClientOptions(testVars.keys[0].keyStr);
		opts_text.restHost = testVars.restHost;
		opts_text.port = testVars.port;
		opts_text.tlsPort = testVars.tlsPort;
		opts_text.tls = testVars.tls;
		opts_text.useBinaryProtocol = false;
		ably_text = new AblyRest(opts_text);

		ClientOptions opts_binary = new ClientOptions(testVars.keys[0].keyStr);
		opts_binary.restHost = testVars.restHost;
		opts_binary.port = testVars.port;
		opts_binary.tlsPort = testVars.tlsPort;
		opts_binary.tls = testVars.tls;
		ably_binary = new AblyRest(opts_binary);
	}

	/**
	 * Publish events with data of various datatypes using text protocol
	 */
	@Test
	public void crypto_publish_text() {
		/* first, publish some messages */
		Channel publish0;
		try {
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			publish0 = ably_text.channels.get("persisted:crypto_publish_text", channelOpts);

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
	 * Publish events with data of various datatypes using binary protocol
	 */
	@Test
	public void crypto_publish_binary() {
		/* first, publish some messages */
		Channel publish1;
		try {
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			publish1 = ably_binary.channels.get("persisted:crypto_publish_binary", channelOpts);

			publish1.publish("publish0", "This is a string message payload");
			publish1.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_binary: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = publish1.history(null);
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
			fail("channelpublish_binary: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events with data of various datatypes using text protocol with a 256-bit key
	 */
	@Test
	public void crypto_publish_text_256() {
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
			publish0 = ably_text.channels.get("persisted:crypto_publish_text_256", channelOpts);

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
	 * Publish events with data of various datatypes using binary protocol
	 */
	@Test
	public void crypto_publish_binary_256() {
		/* first, publish some messages */
		Channel publish1;
		try {
			/* create a key */
			KeyGenerator keygen = KeyGenerator.getInstance("AES");
	        keygen.init(256);
	        byte[] key = keygen.generateKey().getEncoded();
			final CipherParams params = Crypto.getDefaultParams(key);

			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
			publish1 = ably_binary.channels.get("persisted:crypto_publish_binary_256", channelOpts);

			publish1.publish("publish0", "This is a string message payload");
			publish1.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_binary: Unexpected exception");
			return;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception generating key");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = publish1.history(null);
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
			fail("channelpublish_binary: Unexpected exception");
			return;
		}
	}

	/**
	 * Connect twice to the service, using the default (binary) protocol
	 * and the text protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void crypto_publish_text_binary() {
		/* first, publish some messages */
		Channel tx_publish;
		ChannelOptions channelOpts;
		try {
			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			channelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			tx_publish = ably_text.channels.get("persisted:crypto_publish_text_binary", channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			Channel rx_publish = ably_binary.channels.get("persisted:crypto_publish_text_binary", channelOpts);
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
	 * Connect twice to the service, using the default (binary) protocol
	 * and the text protocol. Publish an encrypted message on that channel using
	 * the default cipher params and verify correct receipt.
	 */
	@Test
	public void crypto_publish_binary_text() {
		/* first, publish some messages */
		Channel tx_publish;
		ChannelOptions channelOpts;
		try {
			/* create a key */
			final CipherParams params = Crypto.getDefaultParams();

			/* create a channel */
			channelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
			tx_publish = ably_binary.channels.get("persisted:crypto_publish_binary_text", channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			Channel rx_publish = ably_text.channels.get("persisted:crypto_publish_binary_text", channelOpts);
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
		try {
			/* create a channel */
			ChannelOptions tx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
			tx_publish = ably_binary.channels.get("persisted:crypto_publish_key_mismatch", tx_channelOpts);

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
			Channel rx_publish = ably_text.channels.get("persisted:crypto_publish_key_mismatch", rx_channelOpts);
			rx_publish.history(null);
			fail("crypto_publish_key_mismatch: Expected exception");
		} catch (AblyException e) {
			assertEquals("Expect decrypt padding failure", e.getCause().getClass(), javax.crypto.BadPaddingException.class);
		}
	}

	/**
	 * Connect twice to the service, one with and one without encryption.
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void crypto_send_unencrypted() {
		/* first, publish some messages */
		try {
			/* create a channel */
			Channel tx_publish = ably_binary.channels.get("persisted:crypto_send_unencrypted");

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			Channel rx_publish = ably_text.channels.get("persisted:crypto_send_unencrypted", channelOpts);
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
	 * Publish an unencrypted message and verify that the receiving connection
	 * does not attempt to decrypt it.
	 */
	@Test
	public void crypto_send_encrypted_unhandled() {
		/* first, publish some messages */
		try {
			/* create a channel */
			ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
			Channel tx_publish = ably_binary.channels.get("persisted:crypto_send_encrypted_unhandled", channelOpts);

			tx_publish.publish("publish0", "This is a string message payload");
			tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			Channel rx_publish = ably_text.channels.get("persisted:crypto_send_encrypted_unhandled");
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
			fail("channelpublish_text: Unexpected exception");
			return;
		}
	}

}
