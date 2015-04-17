package io.ably.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import io.ably.rest.AblyRest;
import io.ably.rest.Channel;
import io.ably.test.rest.RestSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.Message;
import io.ably.types.ClientOptions;
import io.ably.types.PaginatedResult;

import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;

public class RestChannelPublish {

	private static AblyRest ably_text;
	private static AblyRest ably_binary;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = RestSetup.getTestVars();

		ClientOptions opts_text = new ClientOptions(testVars.keys[0].keyStr);
		opts_text.host = testVars.host;
		opts_text.port = testVars.port;
		opts_text.tlsPort = testVars.tlsPort;
		opts_text.tls = testVars.tls;
		opts_text.useBinaryProtocol = false;
		ably_text = new AblyRest(opts_text);

		ClientOptions opts_binary = new ClientOptions(testVars.keys[0].keyStr);
		opts_binary.host = testVars.host;
		opts_binary.port = testVars.port;
		opts_binary.tlsPort = testVars.tlsPort;
		opts_binary.tls = testVars.tls;
		ably_binary = new AblyRest(opts_binary);
}

	/**
	 * Publish events with data of various datatypes using text protocol
	 */
	@Test
	public void channelpublish_text() {
		/* first, publish some messages */
		Channel publish0 = ably_text.channels.get("persisted:publish0");
		try {
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
			assertEquals("Expected 2 messages", messages.asArray().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.asArray())
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
	public void channelpublish_binary() {
		/* first, publish some messages */
		Channel publish1 = ably_binary.channels.get("persisted:publish1");
		try {
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
			assertEquals("Expected 2 messages", messages.asArray().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.asArray())
				messageContents.put(message.name, message.data);
			assertEquals("Expect publish0 to be expected String", messageContents.get("publish0"), "This is a string message payload");
			assertEquals("Expect publish1 to be expected byte[]", new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_binary: Unexpected exception");
			return;
		}
	}
}
