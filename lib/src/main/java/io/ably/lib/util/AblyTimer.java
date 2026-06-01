package io.ably.lib.util;

import java.util.TimerTask;

public interface AblyTimer {
    TimerInstance schedule(TimerTask task, long delayMs);
    void cancel();
}
