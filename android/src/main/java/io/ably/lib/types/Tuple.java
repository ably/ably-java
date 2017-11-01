package io.ably.lib.types;

public class Tuple<First, Second> {
    public First first;
    public Second second;

    public Tuple(First first, Second second) {
        this.first = first;
        this.second = second;
    }
}
