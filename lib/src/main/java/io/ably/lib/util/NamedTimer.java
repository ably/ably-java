package io.ably.lib.util;

import java.util.TimerTask;

public interface NamedTimer {
    TimerInstance schedule(TimerTask task, long delayMs);
    void cancel();
}
