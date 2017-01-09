package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;

import io.ably.lib.http.Http;
import io.ably.lib.http.Http.JSONRequestBody;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;

public class RestRequestTest extends ParameterizedTest {

	private AblyRest ably;
	private String channelName;
	private String channelAltName;
	private String channelNamePrefix;
	private String channelPath;
	private String channelsPath;
	private String channelMessagesPath;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
		channelNamePrefix = "persisted:rest_request_";
		channelName = "persisted:rest_request_test_" + testParams.name;
		channelAltName = "persisted:rest_request_alt_" + testParams.name;
		channelsPath = "/channels";
		channelPath = channelsPath + "/" + channelName;
		channelMessagesPath = channelPath + "/messages";

		/* publish events */
		Channel channel = ably.channels.get(channelName);
		for(int i = 0; i < 4; i++) {
			channel.publish("Test event", "Test data " + i);
		}
		Channel altChannel = ably.channels.get(channelAltName);
		for(int i = 0; i < 4; i++) {
			altChannel.publish("Test event", "Test alt data " + i);
		}

		/* wait to persist */
		try { Thread.sleep(1000L); } catch(InterruptedException ie) {}
	}

	/**
	 * Get channel details using the request() API
	 */
	@Test
	public void request_simple() {
		try {
			JsonElement channelDetails = ably.request(Http.GET, channelPath, null, null, null);
			/* check it looks like a ChannelDetails */
			assertNotNull("Verify a result is returned", channelDetails);
			assertTrue("Verify an object is returned", channelDetails.isJsonObject());
			assertTrue("Verify id member is present", channelDetails.getAsJsonObject().has("id"));
			assertEquals("Verify id member is channelName", channelName, channelDetails.getAsJsonObject().get("id").getAsString());
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the requestAsync() API
	 */
	@Test
	public void request_simple_async() {
		ably.requestAsync(Http.GET, channelPath, null, null, null, new Callback<JsonElement>() {
			@Override
			public void onSuccess(JsonElement channelDetails) {
				/* check it looks like a ChannelDetails */
				assertNotNull("Verify a result is returned", channelDetails);
				assertTrue("Verify an object is returned", channelDetails.isJsonObject());
				assertTrue("Verify id member is present", channelDetails.getAsJsonObject().has("id"));
				assertEquals("Verify id member is channelName", channelName, channelDetails.getAsJsonObject().get("id").getAsString());
			}
			@Override
			public void onError(ErrorInfo reason) {
				fail("request_simple_async: Unexpected exception");
			}
		});
	}

	/**
	 * Get channel details using the paginatedRequest() API
	 */
	@Test
	public void request_paginated() {
		try {
			Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
			PaginatedResult<JsonElement> channels = ably.paginatedRequest(Http.GET, channelsPath, params, null, null);
			/* check it looks like an array of ChannelDetails */
			assertNotNull("Verify a result is returned", channels);
			JsonElement[] items = channels.items();
			assertTrue("Verify at least two channels are returned", items.length >= 2);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequestAsync() API
	 */
	@Test
	public void request_paginated_async() {
		Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
		ably.paginatedRequestAsync(Http.GET, channelsPath, params, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
			@Override
			public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
				/* check it looks like an array of ChannelDetails */
				assertNotNull("Verify a result is returned", result);
				JsonElement[] items = result.items();
				assertTrue("Verify at least two channels are returned", items.length >= 2);
				for(int i = 0; i < items.length; i++) {
					assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
				}
			}
			@Override
			public void onError(ErrorInfo reason) {
				fail("request_paginated_async: Unexpected exception");
			}
		});
	}

	/**
	 * Get channel details using the paginatedRequest() API with a specified limit,
	 * checking pagination links
	 */
	@Test
	public void request_paginated_limit() {
		try {
			Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
			PaginatedResult<JsonElement> channels = ably.paginatedRequest(Http.GET, channelsPath, params, null, null);
			/* check it looks like an array of ChannelDetails */
			assertNotNull("Verify a result is returned", channels);
			JsonElement[] items = channels.items();
			assertTrue("Verify one channel is returned", items.length == 1);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}
			/* get next page */
			channels = channels.next();
			assertNotNull("Verify a result is returned", channels);
			items = channels.items();
			assertTrue("Verify one channels is returned", items.length == 1);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequestAsync() API with a specified limit,
	 * checking pagination links
	 */
	@Test
	public void request_paginated_async_limit() {
		Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
		ably.paginatedRequestAsync(Http.GET, channelsPath, params, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
			@Override
			public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
				/* check it looks like an array of ChannelDetails */
				assertNotNull("Verify a result is returned", result);
				JsonElement[] items = result.items();
				assertTrue("Verify one channel is returned", items.length == 1);
				for(int i = 0; i < items.length; i++) {
					assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
				}
				result.next(new Callback<AsyncPaginatedResult<JsonElement>>() {
					@Override
					public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
						/* check it looks like an array of ChannelDetails */
						assertNotNull("Verify a result is returned", result);
						JsonElement[] items = result.items();
						assertTrue("Verify one channel is returned", items.length == 1);
						for(int i = 0; i < items.length; i++) {
							assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
						}
					}
					@Override
					public void onError(ErrorInfo reason) {
						fail("request_paginated_async: Unexpected exception");
					}					
				});
			}
			@Override
			public void onError(ErrorInfo reason) {
				fail("request_paginated_async: Unexpected exception");
			}
		});
	}

	/**
	 * Publish a message using the request() API
	 */
	@Test
	public void request_post() {
		final String messageData = "Test data (request_post)";
		try {
			/* publish a message */
			Message message = new Message("Test event", messageData);
			JSONRequestBody requestBody = new JSONRequestBody(message);
			ably.request(Http.POST, channelMessagesPath, null, requestBody, null);

			/* wait to persist */
			try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

			/* get the history */
			Param[] params = new Param[] { new Param("limit", "1") };
			PaginatedResult<Message> resultPage = ably.channels.get(channelName).history(params);

			/* check it looks like a result page */
			assertNotNull("Verify a result is returned", resultPage);
			assertTrue("Verify an single message is returned", resultPage.items().length == 1);
			assertEquals("Verify returned message was the one posted", messageData, resultPage.items()[0].data);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_post: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish a message using the requestAsync() API
	 */
	@Test
	public void request_post_async() {
		final String messageData = "Test data (request_post_async)";
		/* publish a message */
		Message message = new Message("Test event", messageData);
		JSONRequestBody requestBody = new JSONRequestBody(message);
		ably.requestAsync(Http.POST, channelMessagesPath, null, requestBody, null, new Callback<JsonElement>() {
			@Override
			public void onSuccess(JsonElement result) {
				/* wait to persist */
				try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

				/* get the history */
				Param[] params = new Param[] { new Param("limit", "1") };
				PaginatedResult<Message> resultPage;
				try {
					resultPage = ably.channels.get(channelName).history(params);

					/* check it looks like a result page */
					assertNotNull("Verify a result is returned", resultPage);
					assertTrue("Verify an single message is returned", resultPage.items().length == 1);
					assertEquals("Verify returned message was the one posted", messageData, resultPage.items()[0].data);
				} catch (AblyException e) {
					e.printStackTrace();
					fail("request_post_async: Unexpected exception");
				}
			}
			@Override
			public void onError(ErrorInfo reason) {
				fail("request_post_async: Unexpected exception");
			}				
		});
	}

}
