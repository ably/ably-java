package io.ably.lib.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ParamTest {
	@Test
	public void testArray() {
		final Param[] p1 = Param.array(new Param("foo", "bar"));
		final Param[] p2 = Param.array("foo", "bar");

		assertEquals(p1.length, 1);
		assertParam("foo", "bar", p1[0]);

		assertEquals(p2.length, 1);
		assertParam("foo", "bar", p2[0]);

		assertArrayEquals(p1, p2);
	}

	@Test
	public void testPush() {
		final Param[] params = Param.array("foo", "bar");
		assertEquals(params.length, 1);
		assertParam("foo", "bar", params[0]);

		final Param[] params2 = Param.push(params, "blue", "star");
		assertEquals(params2.length, 2);
		assertParam("foo", "bar", params2[0]);
		assertParam("blue", "star", params2[1]);

		// validate that params2 was a clone, and that params remains the same as it
		// started
		assertEquals(params.length, 1);
		assertParam("foo", "bar", params[0]);
	}

	@Test
	public void testPushDoesNotReplaceValueForKey() {
		final Param[] p1 = Param.array("a", "1");
		final Param[] p2 = Param.push(p1, "c", "2");
		final Param[] p3 = Param.push(p2, "e", "3");
		final Param[] p4 = Param.push(p3, "c", "4");
		final Param[] p5 = Param.push(p4, "a", "5");

		assertEquals(p5.length, 5);
		assertParam("a", "1", p5[0]);
		assertParam("c", "2", p5[1]);
		assertParam("e", "3", p5[2]);
		assertParam("c", "4", p5[3]);
		assertParam("a", "5", p5[4]);
	}

	@Test
	public void testSetReplacesValueForKey() {
		final Param[] p1 = Param.array("a", "1");
		final Param[] p2 = Param.set(p1, new Param("c", "2"));
		final Param[] p3 = Param.set(p2, new Param("e", "3"));
		final Param[] p4 = Param.set(p3, new Param("c", "4"));
		final Param[] p5 = Param.set(p4, new Param("a", "5"));

		assertEquals(p5.length, 3);
		assertParam("e", "3", p5[0]);
		assertParam("c", "4", p5[1]);
		assertParam("a", "5", p5[2]);
	}

	private static void assertParam(final String expectedKey, final String expectedValue, final Param actual) {
		// test field values
		assertEquals(expectedKey, actual.key);
		assertEquals(expectedValue, actual.value);

		// test equality implementation
		assertEquals(new Param(expectedKey, expectedValue), actual);
	}
}
