package io.ably.lib.util;

public class Exceptions {
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
