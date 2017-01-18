package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.http.Http;
import io.ably.lib.http.Http.JsonRequestBody;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers.RawHttpRequest;
import io.ably.lib.test.common.Helpers.RawHttpTracker;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncHttpPaginatedResponse;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.HttpPaginatedResponse;
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
		channelNamePrefix = "persisted:rest_request_" + testParams.name;
		channelName = channelNamePrefix + "_channel";
		channelAltName = channelNamePrefix + "_alt_channel";
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
	 * Spec: RSC19a, RSC19d, HP1, HP3, HP4, HP5, HP8
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
			HttpPaginatedResponse channelResponse = ably.request(Http.GET, channelPath, testParams, null, testHeaders);

			/* check HttpPagninatedResponse details are present */
			assertEquals("Verify statusCode is present", channelResponse.statusCode, 200);
			assertTrue("Verify success is indicated", channelResponse.success);
			assertNull("Verify no error is indicated", channelResponse.errorMessage);
			Map<String, String> headers = HttpUtils.indexParams(channelResponse.headers);
			assertEquals("Verify Content-Type header is present", headers.get("content-type"), "application/json");

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
	 * Spec: RSC19a, RSC19d, HP1, HP3, HP4, HP5, HP8
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

			ably.requestAsync(Http.GET, channelPath, null, null, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse channelResponse) {

					/* check HttpPaginatedResponse details are present */
					waiter.assertEquals(channelResponse.statusCode, 200);
					waiter.assertTrue(channelResponse.success);
					waiter.assertNull(channelResponse.errorMessage);
					Map<String, String> headers = HttpUtils.indexParams(channelResponse.headers);
					waiter.assertEquals(headers.get("content-type"), "application/json");

					/* check it looks like a ChannelDetails */
					/* Verify a result is returned */
					waiter.assertNotNull(channelResponse);
					JsonElement[] items = channelResponse.items();
					waiter.assertEquals(items.length, 1);
					JsonElement channelDetails = items[0];
					waiter.assertTrue(channelDetails.isJsonObject());
					waiter.assertTrue(channelDetails.getAsJsonObject().has("id"));
					waiter.assertEquals(channelName, channelDetails.getAsJsonObject().get("id").getAsString());

					/* check request has expected attributes */
					RawHttpRequest req = httpListener.values().iterator().next();
					/* Spec: RSC19b */
					waiter.assertNotNull(httpListener.getRequestHeader(req.id, "Authorization"));
					/* Spec: RSC19c */
					waiter.assertTrue(httpListener.getRequestHeader(req.id, "Accept").contains("application/json"));
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
	 * Spec: HP2
	 */
	@Test
	public void request_paginated() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
			HttpPaginatedResponse channelsResponse = ably.request(Http.GET, channelsPath, params, null, null);

			/* check HttpPagninatedResponse details are present */
			assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
			assertTrue("Verify success is indicated", channelsResponse.success);
			assertNull("Verify no error is indicated", channelsResponse.errorMessage);
			Map<String, String> headers = HttpUtils.indexParams(channelsResponse.headers);
			assertEquals("Verify Content-Type header is present", headers.get("content-type"), "application/json");

			/* check it looks like an array of ChannelDetails */
			assertNotNull("Verify a result is returned", channelsResponse);
			JsonElement[] items = channelsResponse.items();
			assertTrue("Verify at least two channels are returned", items.length >= 2);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}

			/* check that there is either no next link, or no results from it */
			if(channelsResponse.hasNext()) {
				channelsResponse = channelsResponse.next();
				items = channelsResponse.items();
				assertEquals("Verify no further channels are returned", items.length, 0);
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequestAsync() API
	 * Spec: HP2
	 */
	@Test
	public void request_paginated_async() {
		final Waiter waiter = new Waiter();
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix) };
			ably.requestAsync(Http.GET, channelsPath, params, null, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse channelResponse) {

					/* check HttpPaginatedResponse details are present */
					waiter.assertEquals(channelResponse.statusCode, 200);
					waiter.assertTrue(channelResponse.success);
					waiter.assertNull(channelResponse.errorMessage);
					Map<String, String> headers = HttpUtils.indexParams(channelResponse.headers);
					waiter.assertEquals(headers.get("content-type"), "application/json");

					/* check it looks like an array of ChannelDetails */
					waiter.assertNotNull(channelResponse);
					JsonElement[] items = channelResponse.items();
					waiter.assertTrue(items.length >= 2);
					for(int i = 0; i < items.length; i++) {
						waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
					}
					/* check that there is either no next link, or no results from it */
					if(!channelResponse.hasNext()) {
						waiter.resume();
						return;
					}
					channelResponse.next(new AsyncHttpPaginatedResponse.Callback() {
						@Override
						public void onResponse(AsyncHttpPaginatedResponse channelResponse) {
							JsonElement[] items = channelResponse.items();
							assertEquals("Verify no further channels are returned", items.length, 0);
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
			fail("request_paginated_async: Unexpected exception");
			return;
		}
	}

	/**
	 * Get channel details using the paginatedRequest() API with a specified limit,
	 * checking pagination links
	 * Spec: HP2
	 */
	@Test
	public void request_paginated_limit() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
			HttpPaginatedResponse channelsResponse = ably.request(Http.GET, channelsPath, params, null, null);

			/* check HttpPagninatedResponse details are present */
			assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
			assertTrue("Verify success is indicated", channelsResponse.success);
			assertNull("Verify no error is indicated", channelsResponse.errorMessage);
			Map<String, String> headers = HttpUtils.indexParams(channelsResponse.headers);
			assertEquals("Verify Content-Type header is present", headers.get("content-type"), "application/json");

			/* check it looks like an array of ChannelDetails */
			assertNotNull("Verify a result is returned", channelsResponse);
			JsonElement[] items = channelsResponse.items();
			assertTrue("Verify one channel is returned", items.length == 1);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}

			/* get next page */
			channelsResponse = channelsResponse.next();
			assertNotNull("Verify a result is returned", channelsResponse);
			items = channelsResponse.items();
			assertTrue("Verify one channel is returned", items.length == 1);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}

			/* get first page */
			HttpPaginatedResponse firstResponse = channelsResponse.first();
			assertNotNull("Verify a result is returned", firstResponse);
			items = channelsResponse.items();
			assertTrue("Verify one channel is returned", items.length == 1);
			for(int i = 0; i < items.length; i++) {
				assertTrue("Verify id member is a matching channelName", items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
			}

			/* check that there is either no next link, or no results from it */
			if(channelsResponse.hasNext()) {
				channelsResponse = channelsResponse.next();
				items = channelsResponse.items();
				assertEquals("Verify no further channels are returned", items.length, 0);
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
	 * Spec: HP2
	 */
	@Test
	public void request_paginated_async_limit() {
		final Waiter waiter = new Waiter();
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			Param[] params = new Param[] { new Param("prefix", channelNamePrefix), new Param("limit", "1") };
			ably.requestAsync(Http.GET, channelsPath, params, null, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse channelsResponse) {

					/* check HttpPagninatedResponse details are present */
					assertEquals("Verify statusCode is present", channelsResponse.statusCode, 200);
					assertTrue("Verify success is indicated", channelsResponse.success);
					assertNull("Verify no error is indicated", channelsResponse.errorMessage);
					Map<String, String> headers = HttpUtils.indexParams(channelsResponse.headers);
					assertEquals("Verify Content-Type header is present", headers.get("content-type"), "application/json");

					/* check it looks like an array of ChannelDetails */
					waiter.assertNotNull(channelsResponse);
					JsonElement[] items = channelsResponse.items();
					waiter.assertTrue(items.length == 1);
					for(int i = 0; i < items.length; i++) {
						waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
					}
					channelsResponse.next(new AsyncHttpPaginatedResponse.Callback() {
						@Override
						public void onResponse(final AsyncHttpPaginatedResponse channelsResponse) {
							/* check it looks like an array of ChannelDetails */
							waiter.assertNotNull(channelsResponse);
							JsonElement[] items = channelsResponse.items();
							waiter.assertTrue(items.length == 1);
							for(int i = 0; i < items.length; i++) {
								waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
							}

							/* check that there is a first link */
							channelsResponse.first(new AsyncHttpPaginatedResponse.Callback() {
								@Override
								public void onResponse(AsyncHttpPaginatedResponse firstResponse) {
									waiter.assertNotNull(firstResponse);
									JsonElement[] items = firstResponse.items();
									waiter.assertTrue(items.length == 1);
									for(int i = 0; i < items.length; i++) {
										waiter.assertTrue(items[i].getAsJsonObject().get("id").getAsString().startsWith(channelNamePrefix));
									}

									/* check that there is either no next link, or no results from it */
									if(!channelsResponse.hasNext()) {
										waiter.resume();
										return;
									}
									channelsResponse.next(new AsyncHttpPaginatedResponse.Callback() {
										@Override
										public void onResponse(AsyncHttpPaginatedResponse result) {
											JsonElement[] items = result.items();
											waiter.assertEquals(items.length, 0);
											waiter.resume();
										}
										@Override
										public void onError(ErrorInfo reason) {
											waiter.fail("request_paginated_async_limit: Unexpected exception");
											waiter.resume();
										}
									});
								}
								@Override
								public void onError(ErrorInfo reason) {
									waiter.fail("request_paginated_async_limit: Unexpected exception");
									waiter.resume();
								}});
						}
						@Override
						public void onError(ErrorInfo reason) {
							waiter.fail("request_paginated_async_limit: Unexpected exception");
							waiter.resume();
						}
					});
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_paginated_async_limit: Unexpected exception");
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
	 * Spec: RSC19a, RSC19b
	 * 
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
			HttpPaginatedResponse publishResponse = ably.request(Http.POST, channelMessagesPath, null, requestBody, null);
			RawHttpRequest req = httpListener.getLastRequest();

			/* check HttpPagninatedResponse details are present */
			assertEquals("Verify statusCode is present", publishResponse.statusCode, 201);
			assertTrue("Verify success is indicated", publishResponse.success);
			assertNull("Verify no error is indicated", publishResponse.errorMessage);

			/* wait to persist */
			try { Thread.sleep(1000L); } catch(InterruptedException ie) {}

			/* get the history */
			Param[] params = new Param[] { new Param("limit", "1") };
			PaginatedResult<Message> resultPage = ably.channels.get(channelName).history(params);

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
	 * Spec: RSC19a, RSC19b
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
			ably.requestAsync(Http.POST, channelMessagesPath, null, requestBody, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse publishResponse) {

					/* check HttpPaginatedResponse details are present */
					assertEquals("Verify statusCode is present", publishResponse.statusCode, 201);
					assertTrue("Verify success is indicated", publishResponse.success);
					assertNull("Verify no error is indicated", publishResponse.errorMessage);

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
	 * Verify 400 error responses are indicated with an HttpPaginatedResponse
	 * Spec: RSC19e, HP4, HP5, HP6, HP7
	 */
	@Test
	public void request_404() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);
			HttpPaginatedResponse errorResponse = ably.request(Http.GET, "/non-existent-path", null, null, null);

			/* check HttpPaginatedResponse details are present */
			assertEquals("Verify statusCode is present", errorResponse.statusCode, 404);
			assertFalse("Verify non-success is indicated", errorResponse.success);
			assertNotNull("Verify error is indicated", errorResponse.errorMessage);
			Map<String, String> headers = HttpUtils.indexParams(errorResponse.headers);
			assertEquals("Verify Content-Type header is present", headers.get("content-type"), "application/json");
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_404: Unexpected exception");
		}
	}

	/**
	 * Verify 400 error responses are indicated with an response callback
	 * Spec: RSC19e, HP4, HP5, HP6, HP7
	 */
	@Test
	public void request_404_async() {
		try {
			final Waiter waiter = new Waiter();
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			AblyRest ably = new AblyRest(opts);

			ably.requestAsync(Http.GET, "/non-existent-path", null, null, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse response) {

					/* check HttpPaginatedResponse details are present */
					waiter.assertEquals(response.statusCode, 404);
					waiter.assertFalse(response.success);
					waiter.assertNotNull(response.errorMessage);
					waiter.assertTrue(response.errorCode != 0);
					Map<String, String> headers = HttpUtils.indexParams(response.headers);
					waiter.assertEquals(headers.get("content-type"), "application/json");
					waiter.resume();
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.fail("request_404_async: Expected a response callback");
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


	/**
	 * Verify 500 error responses are indicated with an exception
	 * Spec: RSC19e
	 */
	@Test
	public void request_500() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			opts.environment = "non.existent.env";
			AblyRest ably = new AblyRest(opts);

			ably.request(Http.GET, "/", null, null, null);
			fail("request_500: Expected an exception");
		} catch(AblyException e) {
			assertEquals("Verify expected status code in error response", e.errorInfo.statusCode, 500);
			return;
		}
	}

	/**
	 * Verify 500 error responses are indicated with an error callback
	 * Spec: RSC19e
	 */
	@Test
	public void request_500_async() {
		try {
			final Waiter waiter = new Waiter();
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			opts.environment = "non.existent.env";
			AblyRest ably = new AblyRest(opts);

			ably.requestAsync(Http.GET, "/", null, null, null, new AsyncHttpPaginatedResponse.Callback() {
				@Override
				public void onResponse(AsyncHttpPaginatedResponse response) {
					waiter.fail("request_500_async: Expected an error");
					waiter.resume();
				}
				@Override
				public void onError(ErrorInfo reason) {
					waiter.assertEquals(reason.statusCode, 500);
					waiter.resume();
				}
			});

			try {
				waiter.await(15000);
			} catch (TimeoutException e) {
				fail("request_500_async: Operation timed out");
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("request_500_async: Unexpected exception");
			return;
		}
	}

}
