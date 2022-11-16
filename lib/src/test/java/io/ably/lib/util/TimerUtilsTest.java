package io.ably.lib.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimerUtilsTest {

    @Test
    public void timer_retry_time_is_incremental() {
        for (int i = 1; i <= 6; i++) {
            int defaultTimerMs = 15000;
            int timerMs = TimerUtil.getRetryTime(defaultTimerMs, i);
            int higherRange = defaultTimerMs * i;
            int lowerRange = Double.valueOf(defaultTimerMs * (i * 0.2)).intValue();
            System.out.println("--------------------------------------------------");
            System.out.println("Timer value: " + timerMs + "ms for i: " + i);
            System.out.println("Expected timer lower range: " + lowerRange + "ms");
            System.out.println("Expected timer higher range: " + higherRange + "ms");
            System.out.println("--------------------------------------------------");
            assertTrue("Timer lower value " + lowerRange +"ms is not in range: " + timerMs + "ms for i: " + i, timerMs > lowerRange);
            assertTrue("Timer higher value " + higherRange + "ms is not in range: " + timerMs + "ms for i: " + i, timerMs < higherRange);
        }
    }
}
