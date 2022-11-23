package io.ably.lib.util;

public class TimerUtil {

    /**
     * Spec: RTB1a
     *
     * @param count The retry count
     * @return The backoff coefficient
     */
    private static float getBackoffCoefficient(int count) {
        return Math.min((count + 2) / 3f, 2f);
    }

    /**
     * Spec: RTB1b
     *
     * @return The jitter coefficient
     */
    private static double getJitterCoefficient() {
        return 1 - Math.random() * 0.2;
    }

    /**
     * Spec: RTB1
     *
     * @param timeout The initial timeout value
     * @param count   The retry count
     * @return The overall retry time calculation
     */
    public static int getRetryTime(int timeout, int count) {
        return Double.valueOf(timeout * getJitterCoefficient() * getBackoffCoefficient(count)).intValue();
    }
}
