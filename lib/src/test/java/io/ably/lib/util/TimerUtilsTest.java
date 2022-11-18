package io.ably.lib.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimerUtilsTest {

    @Test
    public void timer_retry_time_is_incremental() {
        for (int i = 1; i <= 5; i++) {
            int defaultTimerMs = 150;
            int timerMs = TimerUtil.getRetryTime(defaultTimerMs, i);
            long higherRange = defaultTimerMs + Math.min(i, 3) * 50L;
            double lowerRange = 0.3 * defaultTimerMs + Math.min(i, 3) * 50L;
            System.out.println("--------------------------------------------------");
            System.out.println("Timer value: " + timerMs + "ms for i: " + i);
            System.out.println("Expected timer lower range: " + lowerRange + "ms");
            System.out.println("Expected timer higher range: " + higherRange + "ms");
            System.out.println("--------------------------------------------------");
            assertTrue("retry time higher range for count " + i + " is not in valid: " + timerMs + " expected: " + higherRange,
                timerMs < higherRange);
            assertTrue("retry time lower range for count " + i + " is not in valid: " + timerMs + " expected: " + lowerRange,
                timerMs > lowerRange);
        }
    }
}
