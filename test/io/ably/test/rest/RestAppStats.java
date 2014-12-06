package io.ably.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ably.rest.AblyRest;
import io.ably.rest.Channel;
import io.ably.test.rest.RestSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.Options;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;
import io.ably.types.Stats;

import java.util.Date;

import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class RestAppStats {

	private static AblyRest ably;
	private static long timeOffset;
	private static long testStart, intervalStart, intervalEnd;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = RestSetup.getTestVars();
		Options opts = new Options(testVars.keys[0].keyStr);
		opts.host = testVars.host;
		opts.port = testVars.port;
		opts.tlsPort = testVars.tlsPort;
		opts.tls = testVars.tls;
		ably = new AblyRest(opts);
		long timeFromService = ably.time();
		timeOffset = timeFromService - System.currentTimeMillis();
	}

	/**
	 * Publish events and check minute-level stats exist (forwards)
	 */
	@Test
	public void appstats_minute0() {
		/* first, wait for the start of a minute,
		 * to prevent earlier tests polluting our results */
		long currentSystemTime = timeOffset + System.currentTimeMillis();
		Date nextSystemMinute = new Date(currentSystemTime + 60000L);
		nextSystemMinute.setSeconds(0);
		testStart = intervalStart = nextSystemMinute.getTime();
		try {
			Thread.sleep(intervalStart - currentSystemTime + 1000);
		} catch(InterruptedException ie) {}

		/* publish some messages */
		Channel stats0 = ably.channels.get("appstats_0");
		for(int i = 0; i < 50; i++)
		try {
			stats0.publish("stats" + i, new Integer(i));
		} catch(AblyException e) {
			e.printStackTrace();
			fail("appstats0: Unexpected exception");
			return;
		}
		/* wait for the stats to be persisted */
		intervalEnd = timeOffset + System.currentTimeMillis();
		try {
			Thread.sleep(8000);
		} catch(InterruptedException ie) {}
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(intervalStart)),
				new Param("end", String.valueOf(intervalEnd))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 record", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_minute0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check hour-level stats exist (forwards)
	 */
	@Test
	public void appstats_hour0() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(intervalStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "hour")
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 record", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_hour0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check day-level stats exist (forwards)
	 */
	@Test
	public void appstats_day0() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(intervalStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "day")
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 record", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_day0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check month-level stats exist (forwards)
	 */
	@Test
	public void appstats_month0() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(intervalStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "month")
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 record", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_month0: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events and check minute stats exist (backwards)
	 */
	@Test
	public void appstats_minute1() {
		/* first, wait for the start of a minute,
		 * to prevent earlier tests polluting our results */
		long currentSystemTime = timeOffset + System.currentTimeMillis();
		Date nextMinute = new Date(currentSystemTime + 60000L);
		nextMinute.setSeconds(0);
		intervalStart = nextMinute.getTime();
		try {
			Thread.sleep(intervalStart - currentSystemTime + 1000L);
		} catch(InterruptedException ie) {}
				
		/*publish some messages */
		Channel stats1 = ably.channels.get("appstats_1");
		for(int i = 0; i < 60; i++)
		try {
			stats1.publish("stats" + i,  new Integer(i));
		} catch(AblyException e) {
			e.printStackTrace();
			fail("appstats1: Unexpected exception");
			return;
		}
		/* wait for the stats to be persisted */
		intervalEnd = timeOffset + System.currentTimeMillis();
		try {
			Thread.sleep(8000);
		} catch(InterruptedException ie) {}
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", String.valueOf(intervalStart)),
				new Param("end", String.valueOf(intervalEnd))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 record", stats.asArray().length, 1);
			assertEquals("Expected 60 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)60);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_minute1: Unexpected exception");
			return;
		}
	}

	/**
	 * Check hour-level stats exist (backwards)
	 */
	@Test
	public void appstats_hour1() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "hour")
			});
			assertNotNull("Expected non-null stats", stats);
			assertTrue("Expect 1 or two records", stats.asArray().length == 1 || stats.asArray().length == 2);
			if(stats.asArray().length == 1)
				assertEquals("Expected 110 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)110);
			else
				assertEquals("Expected 60 messages", (int)stats.asArray()[1].inbound.all.all.count, (int)60);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_hour1: Unexpected exception");
			return;
		}
	}

	/**
	 * Check day-level stats exist (backwards)
	 */
	@Test
	public void appstats_day1() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "day")
			});
			assertNotNull("Expected non-null stats", stats);
			assertTrue("Expect 1 or two records", stats.asArray().length == 1 || stats.asArray().length == 2);
			if(stats.asArray().length == 1)
				assertEquals("Expected 110 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)110);
			else
				assertEquals("Expected 60 messages", (int)stats.asArray()[1].inbound.all.all.count, (int)60);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_day1: Unexpected exception");
			return;
		}
	}

	/**
	 * Check month-level stats exist (backwards)
	 */
	@Test
	public void appstats_month1() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("unit", "month")
			});
			assertNotNull("Expected non-null stats", stats);
			assertTrue("Expect 1 or two records", stats.asArray().length == 1 || stats.asArray().length == 2);
			if(stats.asArray().length == 1)
				assertEquals("Expected 110 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)110);
			else
				assertEquals("Expected 60 messages", (int)stats.asArray()[1].inbound.all.all.count, (int)60);
				
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_month1: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events and check limit query param (backwards)
	 */
	@Test
	public void appstats_limit0() {
		/* first, wait for the start of a minute,
		 * to ensure we get records in distinct minutes */
		long currentSystemTime = timeOffset + new Date().getTime();
		Date nextMinute = new Date(currentSystemTime + 60000L);
		nextMinute.setSeconds(0);
		intervalStart = nextMinute.getTime();
		try {
			Thread.sleep(intervalStart - currentSystemTime + 1000L);
		} catch(InterruptedException ie) {}
				
		/*publish some messages */
		Channel stats2 = ably.channels.get("appstats_2");
		for(int i = 0; i < 70; i++)
		try {
			stats2.publish("stats" + i,  new Integer(i));
		} catch(AblyException e) {
			e.printStackTrace();
			fail("appstats1: Unexpected exception");
			return;
		}
		/* wait for the stats to be persisted */
		intervalEnd = timeOffset + System.currentTimeMillis();
		try {
			Thread.sleep(8000);
		} catch(InterruptedException ie) {}
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 70 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)70);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_limit0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check limit query param (forwards)
	 */
	@Test
	public void appstats_limit1() {
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_limit1: Unexpected exception");
			return;
		}
	}

	/**
	 * Check query pagination (backwards)
	 */
	@Test
	public void appstats_pagination0() {
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 70 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)70);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 60 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)60);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
			/* verify that there is no next page */
			assertNull("Expected null next page", stats.getNext());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_pagination0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check query pagination (forwards)
	 */
	@Test
	public void appstats_pagination1() {
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 60 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)60);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 70 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)70);
			/* verify that there is no next page */
			assertNull("Expected null next page", stats.getNext());
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_pagination1: Unexpected exception");
			return;
		}
	}

	/**
	 * Check query pagination rel="first" (backwards)
	 */
	@Test
	public void appstats_pagination2() {
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 70 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)70);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 60 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)60);
			/* get first page */
			stats = ably.stats(stats.getFirst());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 70 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)70);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_pagination2: Unexpected exception");
			return;
		}
	}

	/**
	 * Check query pagination rel="first" (forwards)
	 */
	@Test
	public void appstats_pagination3() {
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(testStart)),
				new Param("end", String.valueOf(intervalEnd)),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
			/* get next page */
			stats = ably.stats(stats.getNext());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 60 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)60);
			/* get first page */
			stats = ably.stats(stats.getFirst());
			assertNotNull("Expected non-null stats", stats);
			assertEquals("Expected 1 records", stats.asArray().length, 1);
			assertEquals("Expected 50 messages", (int)stats.asArray()[0].inbound.all.all.count, (int)50);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_pagination3: Unexpected exception");
			return;
		}
	}

}
