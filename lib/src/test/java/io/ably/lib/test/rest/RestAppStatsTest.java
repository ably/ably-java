package io.ably.lib.test.rest;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import io.ably.lib.http.HttpUtils;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.util.StatsWriter;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.Stats;
import io.ably.lib.types.StatsReader;

import java.util.Date;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class RestAppStatsTest extends ParameterizedTest {

	private AblyRest ably;
	private static String[] intervalIds;

	@Before
	public void setUpBefore() throws Exception {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		ably = new AblyRest(opts);
	}

	@BeforeClass
	public static void populateStats() {
		try {
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr, Setup.TestParameters.TEXT);
			AblyRest ably = new AblyRest(opts);
			/* get time, preferring time from Ably */
			long currentTime = System.currentTimeMillis();
			try {
				currentTime = ably.time();
			} catch (AblyException e) {}
	
			/* round down to the start of the current minute */
			Date currentDate = new Date(currentTime);
			currentDate.setSeconds(0);
			currentTime = currentDate.getTime();
	
			/* Make it the same time last year, to avoid problems with the app
			 * already having stats from other tests in the same test run. */
			currentTime -= 365 * 24 * 3600 * 1000L;
	
			/* get time bounds for test */
			intervalIds = new String[3];
			for(int i = 0; i < 3; i++) {
				long intervalTime = currentTime + (i - 3) * 60 * 1000;
				intervalIds[i] = Stats.toIntervalId(intervalTime, Stats.Granularity.minute);
			}
	
			/* add stats for each of the minutes within the interval */
			Stats[] testStats = StatsReader.readJson(
					'['
					+   "{ \"intervalId\": \"" + intervalIds[0] + "\","
					+     "\"inbound\": {\"realtime\":{\"messages\":{\"count\":50,\"data\":5000}}}"
					+   "},"
					+   "{ \"intervalId\": \"" + intervalIds[1] + "\","
					+     "\"inbound\": {\"realtime\":{\"messages\":{\"count\":60,\"data\":6000}}}"
					+   "},"
					+   "{ \"intervalId\": \"" + intervalIds[2] + "\","
					+     "\"inbound\": {\"realtime\":{\"messages\":{\"count\":70,\"data\":7000}}}"
					+   '}'
					+ ']'
				);

			ably.http.post("/stats", HttpUtils.defaultAcceptHeaders(false), null, StatsWriter.asJsonRequest(testStats), null);
		} catch (AblyException e) {}
	}

	/**
	 * Check minute-level stats exist (forwards)
	 */
	@Test
	public void appstats_minute0() {
		/* get the stats for this channel */
		try {
			/* note that bounds are inclusive */
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[0])
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));

			stats = ably.stats(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", intervalIds[1]),
				new Param("end", intervalIds[1])
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));

			stats = ably.stats(new Param[] {
					new Param("direction", "forwards"),
					new Param("start", intervalIds[2]),
					new Param("end", intervalIds[2])
				});
				assertNotNull("Expected non-null stats", stats);
				assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
				assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_minute0: Unexpected exception");
			return;
		}
	}

	/**
	 * Check minute-level stats exist (backwards)
	 */
	@Test
	public void appstats_minute1() {
		/* get the stats for this channel */
		try {
			/* note that bounds are inclusive */
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[0])
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));

			stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", intervalIds[1]),
				new Param("end", intervalIds[1])
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));

			stats = ably.stats(new Param[] {
					new Param("direction", "backwards"),
					new Param("start", intervalIds[2]),
					new Param("end", intervalIds[2])
				});
				assertNotNull("Expected non-null stats", stats);
				assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
				assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_minute1: Unexpected exception");
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("unit", "hour")
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 180 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(180)));
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("unit", "day")
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 180 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(180)));
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("unit", "month")
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 record", stats.items().length, is(equalTo(1)));
			assertThat("Expected 180 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(180)));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_month0: Unexpected exception");
			return;
		}
	}

	/**
	 * Publish events and check limit query param (backwards)
	 */
	@Test
	public void appstats_limit0() {
		/* get the stats for this channel */
		try {
			PaginatedResult<Stats> stats = ably.stats(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));
			/* verify that there is no next page */
			assertFalse("Expected null next page", stats.hasNext());
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
			/* verify that there is no next page */
			assertFalse("Expected null next page", stats.hasNext());
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));
			/* get first page */
			stats = stats.first();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 70 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(70)));
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
				new Param("start", intervalIds[0]),
				new Param("end", intervalIds[2]),
				new Param("limit", String.valueOf(1))
			});
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));
			/* get next page */
			stats = stats.next();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 60 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(60)));
			/* get first page */
			stats = stats.first();
			assertNotNull("Expected non-null stats", stats);
			assertThat("Expected 1 records", stats.items().length, is(equalTo(1)));
			assertThat("Expected 50 messages", (int)stats.items()[0].inbound.all.all.count, is(equalTo(50)));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("appstats_pagination3: Unexpected exception");
			return;
		}
	}

}
