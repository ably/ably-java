package io.ably.lib.util;

public class TimerUtil {

    /**
     * Spec: RTB1a
     * @param count The retry count
     * @return The backoff coefficient
     */
    public static int getBackoffCoefficient(int count) {
        return Math.min((count + 2) / 3, 2);
    }

    /**
     * Spec: RTB1b
     * @return The jitter coefficient
     */
    public static int getJitterCoefficient() {
        return Double.valueOf(1 - Math.random() * 0.2).intValue();
    }

    /**
     * Spec: RTB1
     * @param timeout The initial timeout value
     * @param count The retry count
     * @return The overall retry time calculation
     */
    public static int getRetryTime(int timeout, int count) {
        return timeout * getJitterCoefficient() * getBackoffCoefficient(count);
    }
}
