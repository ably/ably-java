package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.*;
import org.junit.Before;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;

public class RestChannelBulkPublishTest extends ParameterizedTest  {

	private AblyRest ably;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
	}

	/**
	 * Publish a single message on multiple channels
	 * 
	 * The payload constructed has the form
	 * [
	 *   {
	 *     channel: [ <channel 0>, <channel 1>, ... ],
	 *     message: [{ data: <message text> }]
	 *   }
	 * ]
	 * 
	 * It publishes the given message on all of the given channels.
	 */
	@Test
	public void bulk_publish_multiple_channels_simple() {
		/* first, publish some messages */
		int channelCount = 5;
		ArrayList<String> channels = new ArrayList<String>();
		for(int i = 0; i < channelCount; i++)
			channels.add("persisted:" + randomString());

		Message message = new Message(null, "bulk_publish_multiple_channels_simple");
		Message.Batch payload = new Message.Batch(channels, Collections.singleton(message));

		try {
			PublishResponse[] result = ably.publish(new Message.Batch[] { payload }, null);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("bulkpublish_multiple_channels_simple: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			for(String channel : channels) {
				PaginatedResult<Message> messages = ably.channels.get(channel).history(null);
				assertNotNull("Expected non-null messages", messages);
				assertEquals("Expected 1 message", messages.items().length, 1);
				/* verify message contents */
				assertEquals("Expect message data to be expected String", messages.items()[0].data, message.data);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("bulkpublish_multiple_channels_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish a multiple messages on multiple channels
	 * 
	 * The payload constructed has the form
	 * [
	 *   {
	 *     channel: [ <channel 0> ],
	 *     message: [
	 *       { data: <message text> },
	 *       { data: <message text> },
	 *       { data: <message text> },
	 *       ...
	 *     ]
	 *   },
	 *   {
	 *     channel: [ <channel 1> ],
	 *     message: [
	 *       { data: <message text> },
	 *       { data: <message text> },
	 *       { data: <message text> },
	 *       ...
	 *     ]
	 *   },
	 *   ...
	 * ]
	 * 
	 * It publishes the given messages on the associated channels.
	 */
	@Test
	public void bulk_publish_multiple_channels_multiple_messages() {
		/* first, publish some messages */
		int channelCount = 5;
		int messageCount = 6;
		String baseMessageText = "bulk_publish_multiple_channels_multiple_messages";
		ArrayList<Message.Batch> payload = new ArrayList<Message.Batch>();

		ArrayList<String> rndMessageTexts = new ArrayList<String>();
		for(int i = 0; i < messageCount; i++) {
			rndMessageTexts.add(randomString());
		}

		ArrayList<String> channels = new ArrayList<String>();
		for(int i = 0; i < channelCount; i++) {
			String channel = "persisted:" + randomString();
			channels.add(channel);
			ArrayList<Message> messages = new ArrayList<Message>();
			for(int j = 0; j < messageCount; j++)
				messages.add(new Message(null, baseMessageText + '-' + channel + '-' + rndMessageTexts.get(j)));
			payload.add(new Message.Batch(Collections.singleton(channel), messages));
		}

		try {
			ably.publish(payload.toArray(new Message.Batch[payload.size()]), null);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("bulk_publish_multiple_channels_multiple_messages: Unexpected exception");
			return;
		}

		/* get the history for this channel */
		try {
			for(String channel : channels) {
				PaginatedResult<Message> messages = ably.channels.get(channel).history(new Param[] {new Param("direction", "forwards")});
				assertNotNull("Expected non-null messages", messages);
				assertEquals("Expected correct number of messages", messages.items().length, messageCount);
				/* verify message contents */
				for(int i = 0; i < messageCount; i++)
					assertEquals("Expect message data to be expected String", messages.items()[i].data, baseMessageText + '-' + channel + '-' + rndMessageTexts.get(i));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("bulk_publish_multiple_channels_multiple_messages: Unexpected exception");
			return;
		}
	}

	private String randomString() { return String.valueOf(new Random().nextDouble()).substring(2); }
}
