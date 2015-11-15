package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.hamcrest.core.IsEqual.equalTo;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.AsyncWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;

import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RestChannelPublishTest {

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

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if(ably_text != null)
			ably_text.dispose();

		if(ably_binary != null)
			ably_binary.dispose();
	}

	/**
	 * Publish events with data of various datatypes using text protocol
	 */
	@Test
	public void channelpublish_text() {
		/* first, publish some messages */
		String channelName = "persisted:channelpublish_text";
		Channel pubChannel = ably_text.channels.get(channelName);
		try {
			pubChannel.publish("pub_text", "This is a string message payload");
			pubChannel.publish("pub_binary", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_text: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = pubChannel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect pub_text to be expected String", messageContents.get("pub_text"), "This is a string message payload");
			assertEquals("Expect pub_binary to be expected byte[]", new String((byte[])messageContents.get("pub_binary")), "This is a byte[] message payload");
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
		String channelName = "persisted:channelpublish_binary";
		Channel pubChannel = ably_binary.channels.get(channelName);
		try {
			pubChannel.publish("pub_text", "This is a string message payload");
			pubChannel.publish("pub_binary", "This is a byte[] message payload".getBytes());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("channelpublish_binary: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			PaginatedResult<Message> messages = pubChannel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Object> messageContents = new HashMap<String, Object>();
			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message.data);
			assertEquals("Expect pub_text to be expected String", messageContents.get("pub_text"), "This is a string message payload");
			assertEquals("Expect pub_binary to be expected byte[]", new String((byte[])messageContents.get("pub_binary")), "This is a byte[] message payload");
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelpublish_binary: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events using the async publish API
	 */
	@Test
	public void channelpublish_async() {
		/* first, publish some messages */
		String channelName = "persisted:channelpublish_async";
		String textPayload = "This is a string message payload";
		byte[] binaryPayload = "This is a byte[] message payload".getBytes();
		Channel pubChannel = ably_binary.channels.get(channelName);
		CompletionSet pubComplete = new CompletionSet();

		pubChannel.publishAsync(new Message[] {new Message("pub_text", textPayload)}, pubComplete.add());
		pubChannel.publishAsync(new Message[] {new Message("pub_binary", binaryPayload)}, pubComplete.add());

		ErrorInfo[] pubErrors = pubComplete.waitFor();
		if(pubErrors != null && pubErrors.length > 0) {
			fail("channelpublish_async: Unexpected errors from publish");
			return;
		}

		/* get the history for this channel */
		AsyncWaiter<AsyncPaginatedResult<Message>> callback = new AsyncWaiter<AsyncPaginatedResult<Message>>();
		pubChannel.historyAsync(null, callback);
		callback.waitFor();

		if(callback.error != null) {
			fail("channelpublish_async: Unexpected errors from history: " + callback.error);
			return;
		}

		AsyncPaginatedResult<Message> messages = callback.result;
		assertNotNull("Expected non-null messages", messages);
		assertEquals("Expected 2 messages", messages.items().length, 2);
		HashMap<String, Object> messageContents = new HashMap<String, Object>();
		/* verify message contents */
		for(Message message : messages.items())
			messageContents.put(message.name, message.data);
		assertEquals("Expect pub_text to be expected String", messageContents.get("pub_text"), textPayload);
		assertThat("Expect pub_binary to be expected byte[]", (byte[])messageContents.get("pub_binary"), equalTo(binaryPayload));
	}
}
