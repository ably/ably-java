package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;

import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RestPresenceTest {

	private static final String[] clientIds = new String[] {
		"client_string_0",
		"client_string_1",
		"client_string_2",
		"client_string_3"
	};

	private static AblyRest ably_text;
	private static AblyRest ably_binary;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = Setup.getTestVars();

		ClientOptions opts_text = new ClientOptions(testVars.keys[0].keyStr);
		opts_text.restHost = testVars.restHost;
		opts_text.environment = testVars.environment;
		opts_text.port = testVars.port;
		opts_text.tlsPort = testVars.tlsPort;
		opts_text.tls = testVars.tls;
		opts_text.useBinaryProtocol = false;
		ably_text = new AblyRest(opts_text);

		ClientOptions opts_binary = new ClientOptions(testVars.keys[0].keyStr);
		opts_binary.restHost = testVars.restHost;
		opts_binary.environment = testVars.environment;
		opts_binary.port = testVars.port;
		opts_binary.tlsPort = testVars.tlsPort;
		opts_binary.tls = testVars.tls;
		ably_binary = new AblyRest(opts_binary);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Get member data of various datatypes using text protocol
	 */
	@Test
	public void rest_getpresence_text() {
		/* get channel */
		Channel channel = ably_text.channels.get("restpresence_notpersisted");
		try {
			PresenceMessage[] members = channel.presence.get(null).items();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.length, 1);

			/* verify presence contents */
			assertEquals("Expect data to be expected String", members[0].data, "This is a string data payload");
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_getpresence_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Get member data of various datatypes using binary protocol
	 */
	@Test
	public void rest_getpresence_binary() {
		/* get channel */
		Channel channel = ably_binary.channels.get("restpresence_notpersisted");
		try {
			PresenceMessage[] members = channel.presence.get(null).items();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.length, 1);

			/* verify presence contents */
			assertEquals("Expect client_string to be expected String", members[0].data, "This is a string data payload");
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_getpresence_binary: Unexpected exception");
			return;
		}
	}

	/**
	 * Get presence history data of various datatypes using text protocol
	 */
	@Test
	public void rest_presencehistory_simple_text() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 4 messages", members.items().length, 4);

			/* verify presence contents */
			HashMap<String, Object> memberData = new HashMap<String, Object>();
			for(PresenceMessage member : members.items())
				memberData.put(member.clientId, member.data);
			assertEquals("Expect client_string_0 to be expected String", memberData.get("client_string_0"), "This is a string data payload");
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_simple_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Get presence history data of various datatypes using binary protocol
	 */
	@Test
	public void rest_presencehistory_simple_binary() {
		/* get channel */
		Channel channel = ably_binary.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 4 messages", members.items().length, 4);

			/* verify presence contents */
			HashMap<String, Object> memberData = new HashMap<String, Object>();
			for(PresenceMessage member : members.items())
				memberData.put(member.clientId, member.data);
			assertEquals("Expect client_string_0 to be expected String", memberData.get("client_string_0"), "This is a string data payload");
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_simple_text: Unexpected exception");
			return;
		}
	}

	/**
	 * Get presence history data in the forward direction using text protocol and check order
	 */
	@Test
	public void rest_presencehistory_order_text_f() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 4 messages", members.items().length, 4);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_order_text_f: Unexpected exception");
			return;
		}
	}

	/**
	 * Get presence history data in the backwards direction using text protocol and check order
	 */
	@Test
	public void rest_presencehistory_order_text_b() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 4 messages", members.items().length, 4);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_order_text_b: Unexpected exception");
			return;
		}
	}

	/**
	 * Get limited presence history data in the forward direction using text protocol and check order
	 */
	@Test
	public void rest_presencehistory_limit_text_f() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards"), new Param("limit", "2") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 2 messages", members.items().length, 2);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_limit_text_f: Unexpected exception");
			return;
		}
	}

	/**
	 * Get limited presence history data in the backwards direction using text protocol and check order
	 */
	@Test
	public void rest_presencehistory_limit_text_b() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards"), new Param("limit", "2") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 2 messages", members.items().length, 2);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_limit_text_b: Unexpected exception");
			return;
		}
	}

	/**
	 * Get paginated presence history data in the forward direction using text protocol
	 */
	@Test
	public void rest_presencehistory_paginate_text_f() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "forwards"), new Param("limit", "1") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[1 + i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[2 + i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[3 + i], member.clientId);
			}

			/* verify there are no further results */
			if(members.hasNext()) {
				members = members.next();
				if(members != null)
					assertEquals("Expected no further members", members.items().length, 0);
			}

		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_paginate_text_f: Unexpected exception");
			return;
		}
	}

	/**
	 * Get paginated presence history data in the backwards direction using text protocol
	 */
	@Test
	public void rest_presencehistory_paginate_text_b() {
		/* get channel */
		Channel channel = ably_text.channels.get("persisted:restpresence_persisted");
		try {
			/* get the history for this channel */
			PaginatedResult<PresenceMessage> members = channel.presence.history(new Param[]{ new Param("direction", "backwards"), new Param("limit", "1") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[3 - i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 messages", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[2 - i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[1 - i], member.clientId);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 1 message", members.items().length, 1);

			/* verify message order */
			for(int i = 0; i < members.items().length; i++) {
				PresenceMessage member = members.items()[i];
				assertEquals("Verify expected member (" + i + ')', clientIds[0 - i], member.clientId);
			}

			/* verify there are no further results */
			if(members.hasNext()) {
				members = members.next();
				if(members != null)
					assertEquals("Expected no further members", members.items().length, 0);
			}

		} catch(AblyException e) {
			e.printStackTrace();
			fail("rest_presencehistory_paginate_text_f: Unexpected exception");
			return;
		}
	}

}
