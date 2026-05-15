package io.ably.lib.util;

public interface Clock {
    long currentTimeMillis();
    NamedTimer newTimer(String name);
}
