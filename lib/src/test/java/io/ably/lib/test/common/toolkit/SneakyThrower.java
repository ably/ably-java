package io.ably.lib.test.common.toolkit;

public class SneakyThrower<T extends Throwable> {

    /**
     * Will throw the given {@link Throwable}.
     */
    public static void sneakyThrow(Throwable t) {
        new SneakyThrower<Error>().sneakyThrow2(t);
    }

    private void sneakyThrow2(Throwable t) throws T {
        throw (T) t;
    }
}
