package io.ably.lib.test.rest;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers.AsyncWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;

public class RestChannelPublishTest extends ParameterizedTest {

	private AblyRest ably;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
	}

	/**
	 * Publish events with data of various datatypes using text protocol
	 */
	@Test
	public void channelpublish() {
		/* first, publish some messages */
		String channelName = "persisted:channelpublish_" + testParams.name;
		Channel pubChannel = ably.channels.get(channelName);
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
	 * Publish events using the async publish API
	 */
	@Test
	public void channelpublish_async() {
		/* first, publish some messages */
		String channelName = "persisted:channelpublish_async_" + testParams.name;
		String textPayload = "This is a string message payload";
		byte[] binaryPayload = "This is a byte[] message payload".getBytes();
		Channel pubChannel = ably.channels.get(channelName);
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
