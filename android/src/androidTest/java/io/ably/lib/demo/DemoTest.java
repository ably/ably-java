package io.ably.lib.demo;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Created by gokhanbarisaker on 4/13/16.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DemoTest {
	@Test
	public void doFoo() {
		assertTrue("This assertion simulates passing test case", true);
	}

	@Test
	public void doBar() {
		assertTrue("This assertion simulates failing test case", false);
	}
}
