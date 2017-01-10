package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.http.Http;
import io.ably.lib.http.Http.JsonRequestBody;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers.RawHttpRequest;
import io.ably.lib.test.common.Helpers.RawHttpTracker;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import net.jodah.concurrentunit.Waiter;

/* Spec: RSC19 */
public class RestRequestTest extends ParameterizedTest {

	private AblyRest setupAbly;
	private String channelName;
	private String channelAltName;
	private String channelNamePrefix;
	private String channelPath;
	private String channelsPath;
	private String channelMessagesPath;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		setupAbly = new AblyRest(opts);
		channelNamePrefix = "persisted:rest_request_";
		channelName = "persisted:rest_request_test_" + testParams.name;
		channelAltName = "persisted:rest_request_alt_" + testParams.name;
		channelsPath = "/channels";
		channelPath = channelsPath + "/" + channelName;
		channelMessagesPath = channelPath + "/messages";

		/* publish events */
		Channel channel = setupAbly.channels.get(channelName);
		for(int i = 0; i < 4; i++) {
			channel.publish("Test event", "Test data " + i);
		}
		Channel altChannel = setupAbly.channels.get(channelAltName);
		for(int i = 0; i < 4; i++) {
			altChannel.publish("Test event", "Test alt data " + i);
		}

		/* wait to persist */
		try { Thread.sleep(1000L); } catch(InterruptedException ie) {}
	}

	/**
	 * Get channel details using the request() API
	 * Spec: RSC19a, RSC19d
	 */
	@Test
	public void request_simple() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			RawHttpTracker httpListener = new RawHttpTracker();
			opts.httpListener = httpListener;
			AblyRest ably = new AblyRest(opts);

			Param[] testParams = new Param[] { new Param("testParam", "testValue") };
			Param[] testHeaders = new Param[] { new Param("x-test-header", "testValue") };
			PaginatedResult<JsonElement> channelResponse = ably.request(Http.GET, channelPath, testParams, null, testHeaders);

			/* check it looks like a ChannelDetails */
			assertNotNull("Verify a result is returned", channelResponse);
			JsonElement[] items = channelResponse.items();
			assertEquals("Verify a single items is returned", items.length, 1);
			JsonElement channelDetails = items[0];
			assertTrue("Verify an object is returned", channelDetails.isJsonObject());
			assertTrue("Verify id member is present", channelDetails.getAsJsonObject().has("id"));
			assertEquals("Verify id member is channelName", channelName, channelDetails.getAsJsonObject().get("id").getAsString());

			/* check request has expected attributes; use last request in case of challenges preceding sending auth header */
			RawHttpRequest req = httpListener.getLastRequest();
			/* Spec: RSC19b */
			assertNotNull("Verify Authorization header present", httpListener.getRequestHeader(req.id, "Authorization"));
			/* Spec: RSC19c */
			assertTrue("Verify Accept header present", httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
			assertTrue("Verify Content-Type header present", httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the requestAsync() API
	 * Spec: RSC19a, RSC19d
	 */
	@Test
	public void request_simple_async() {
		final Waiter waiter = new Waiter();
		DebugOptions opts;
		try {
			opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			final RawHttpTracker httpListener = new RawHttpTracker();
			opts.httpListener = httpListener;
			AblyRest ably = new AblyRest(opts);

			ably.requestAsync(Http.GET, channelPath, null, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
				@Override
				public void onSuccess(AsyncPaginatedResult<JsonElement> result) {

					/* check it looks like a ChannelDetails */
					/* Verify a result is returned */
					waiter.assertNotNull(result);
					JsonElement[] items = result.items();
					/* Verify a single items is returned */
					waiter.assertEquals(items.length, 1);
					JsonElement channelDetails = items[0];
					/* Verify an object is returned */
					waiter.assertTrue(channelDetails.isJsonObject());
					/* Verify id member is present */
					waiter.assertTrue(channelDetails.getAsJsonObject().has("id"));
					/* Verify id member is channelName */
					waiter.assertEquals(channelName, channelDetails.getAsJsonObject().get("id").getAsString());

					/* check request has expected attributes */
					RawHttpRequest req = httpListener.values().iterator().next();
					/* Spec: RSC19b */
					/* Verify Authorization header present */
					waiter.assertNotNull(httpListener.getRequestHeader(req.id, "Authorization"));
					/* Spec: RSC19c */
					/* Verify Accept header present */
					waiter.assertTrue(httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
					/* Verify Content-Type header present */
					waiter.assertTrue(httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
					waiter.resume();
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_simple_async: Unexpected exception");
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_simple_async: Operation timed out");
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("request_simple_async: Unexpected exception");
		}
	}

	/**
	 * Get channel details using the paginatedRequest() API
	 */
	@Test
	public void request_paginated() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
			PaginatedResult<JsonElement> channels = ably.request(Http.GET, channelsPath, params, null, null);
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
		final Waiter waiter = new Waiter();
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
			ably.requestAsync(Http.GET, channelsPath, params, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
				@Override
				public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
					/* check it looks like an array of ChannelDetails */
					waiter.assertNotNull(result);
					JsonElement[] items = result.items();
					waiter.assertTrue(items.length >= 2);
					for(int i = 0; i < items.length; i++) {
						waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
					}
					waiter.resume();
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_paginated_async: Unexpected exception");
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_paginated_async: Operation timed out");
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_paginated_async: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequest() API with a specified limit,
	 * checking pagination links
	 */
	@Test
	public void request_paginated_limit() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
			PaginatedResult<JsonElement> channels = ably.request(Http.GET, channelsPath, params, null, null);
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
			fail("request_paginated_limit: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequestAsync() API with a specified limit,
	 * checking pagination links
	 */
	@Test
	public void request_paginated_async_limit() {
		final Waiter waiter = new Waiter();
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
			ably.requestAsync(Http.GET, channelsPath, params, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
				@Override
				public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
					/* check it looks like an array of ChannelDetails */
					waiter.assertNotNull(result);
					JsonElement[] items = result.items();
					waiter.assertTrue(items.length == 1);
					for(int i = 0; i < items.length; i++) {
						waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
					}
					result.next(new Callback<AsyncPaginatedResult<JsonElement>>() {
						@Override
						public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
							/* check it looks like an array of ChannelDetails */
							waiter.assertNotNull(result);
							JsonElement[] items = result.items();
							waiter.assertTrue(items.length == 1);
							for(int i = 0; i < items.length; i++) {
								waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
							}
							waiter.resume();
						}
						@Override
						public void onError(ErrorInfo reason) {
							waiter.fail("request_paginated_async: Unexpected exception");
							waiter.resume();
						}
					});
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_paginated_async: Unexpected exception");
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_paginated_async: Operation timed out");
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_paginated_async_limit: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish a message using the request() API
	 */
	@Test
	public void request_post() {
		final String messageData = "Test data (request_post)";
		DebugOptions opts;
		try {
			opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			final RawHttpTracker httpListener = new RawHttpTracker();
			opts.httpListener = httpListener;
			AblyRest ably = new AblyRest(opts);

			/* publish a message */
			Message message = new Message("Test event", messageData);
			JsonRequestBody requestBody = new JsonRequestBody(message);
			ably.request(Http.POST, channelMessagesPath, null, requestBody, null);
			RawHttpRequest req = httpListener.getLastRequest();

			/* wait to persist */
			try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

			/* get the history */
			Param[] params = new Param[] { new Param("limit", "1") };
			PaginatedResult<Message> resultPage = setupAbly.channels.get(channelName).history(params);

			/* check it looks like a result page */
			assertNotNull("Verify a result is returned", resultPage);
			assertTrue("Verify an single message is returned", resultPage.items().length == 1);
			assertEquals("Verify returned message was the one posted", messageData, resultPage.items()[0].data);

			/* check request has expected attributes */
			/* Spec: RSC19b */
			assertNotNull("Verify Authorization header present", httpListener.getRequestHeader(req.id, "authorization"));
			/* Spec: RSC19c */
			assertTrue("Verify Accept header present", httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
			assertTrue("Verify Content-Type header present", httpListener.getRequestHeader(req.id, "Content-Type").contains("application/json"));
			assertTrue("Verify Content-Type header present", httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
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
		final Waiter waiter = new Waiter();
		final String messageData = "Test data (request_post_async)";
		DebugOptions opts;
		try {
			opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			final RawHttpTracker httpListener = new RawHttpTracker();
			opts.httpListener = httpListener;
			AblyRest ably = new AblyRest(opts);

			/* publish a message */
			Message message = new Message("Test event", messageData);
			JsonRequestBody requestBody = new JsonRequestBody(message);
			ably.requestAsync(Http.POST, channelMessagesPath, null, requestBody, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
				@Override
				public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
					/* wait to persist */
					try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

					/* get the history */
					Param[] params = new Param[] { new Param("limit", "1") };
					PaginatedResult<Message> resultPage;
					try {
						resultPage = setupAbly.channels.get(channelName).history(params);

						/* check it looks like a result page */
						waiter.assertNotNull(resultPage);
						waiter.assertTrue(resultPage.items().length == 1);
						waiter.assertEquals(messageData, resultPage.items()[0].data);

						/* check request has expected attributes */
						RawHttpRequest req = httpListener.values().iterator().next();
						/* Spec: RSC19b */
						waiter.assertNotNull(httpListener.getRequestHeader(req.id, "Authorization"));
						/* Spec: RSC19c */
						waiter.assertTrue(httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
						waiter.assertTrue(httpListener.getRequestHeader(req.id, "Content-Type").contains("application/json"));
						waiter.assertTrue(httpListener.getResponseHeader(req.id, "Content-Type").contains("application/json"));
						waiter.resume();
					} catch (AblyException e) {
						e.printStackTrace();
						waiter.fail("request_post_async: Unexpected exception");
						waiter.resume();
					}
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_post_async: Unexpected exception");
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_paginated_async: Operation timed out");
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_post_async: Unexpected exception");
			return;
		}
	}

	/**
	 * Verify error responses are indicated with an exception
	 * Spec: RSC19e
	 */
	@Test
	public void request_404() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			ably.request(Http.GET, "/non-existent-path", null, null, null);
			fail("request_404: Expected an exception");
		} catch(AblyException e) {
			assertEquals("Verify expected status code in error response", e.errorInfo.statusCode, 404);
			return;
		}
	}

	/**
	 * Verify error responses are indicated with an error callback
	 * Spec: RSC19e
	 */
	@Test
	public void request_404_async() {
		try {
			final Waiter waiter = new Waiter();
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			ably.requestAsync(Http.GET, "/non-existent-path", null, null, null, new Callback<AsyncPaginatedResult<JsonElement>>() {
				@Override
				public void onSuccess(AsyncPaginatedResult<JsonElement> result) {
					waiter.fail("request_404_async: Expected an error");
					waiter.resume();
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.assertEquals(reason.statusCode, 404);
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_404_async: Operation timed out");
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_404_async: Unexpected exception");
			return;
		}
	}

}
