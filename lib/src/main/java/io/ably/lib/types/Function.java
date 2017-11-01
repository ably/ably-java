package io.ably.lib.types;

public interface Function<Arg, Result> {
    public Result call(Arg arg);

    public static interface Binary<Arg1, Arg2, Result> {
        public Result call(Arg1 arg1, Arg2 arg2);
    }
}
