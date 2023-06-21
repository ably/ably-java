package io.ably.lib.util;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.sun.tools.javac.util.Pair;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.Arrays;

public class ReconnectionStrategyTest {

    @Test
    public void calculateRetryTimeoutUsingIncrementalBackoffAndJitter() {

        int[] retryAttempts = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
        int initialTimeoutValue = 15; // timeout value in seconds

        int[] retryTimeouts = Arrays.stream(retryAttempts).map(attempt -> ReconnectionStrategy.getRetryTime(initialTimeoutValue, attempt)).toArray();

        assertTimeoutBetween(retryTimeouts[0], 12d, 15d);
        assertTimeoutBetween(retryTimeouts[1], 16d, 20d);
        assertTimeoutBetween(retryTimeouts[2], 20d, 25d);

        for (int i = 3; i < retryTimeouts.length; i++)
        {
            assertTimeoutBetween(retryTimeouts[i], 24d, 30d);
        }

        for (int retryAttempt : retryAttempts) {

            int retryTimeout = ReconnectionStrategy.getRetryTime(initialTimeoutValue, retryAttempt);
            Pair<Double, Double> pair = calculateRetryBounds(retryAttempt, initialTimeoutValue);

            assertTimeoutBetween(retryTimeout, pair.fst, pair.snd);
        }
    }

    public void assertTimeoutBetween(int timeout, Double min, Double max) {
        assertThat(String.format("timeout %d should be between %f and %f", timeout, min, max ), (double) timeout, between(min, max));
    }

    public static Matcher<Double> between(Double min, Double max) {
        return allOf(greaterThanOrEqualTo(min), lessThanOrEqualTo(max));
    }

    public static Pair<Double, Double> calculateRetryBounds(int retryAttempt, int initialTimeout)
    {
        double upperBound = Math.min((retryAttempt + 2) / 3d, 2d) * initialTimeout;
        double lowerBound = 0.8 * upperBound;
        return new Pair<>(lowerBound, upperBound);
    }
}
