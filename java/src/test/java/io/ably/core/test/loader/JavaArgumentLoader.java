package io.ably.core.test.loader;

public class JavaArgumentLoader implements ArgumentLoader {
    public String getTestArgument(String name) {
        return System.getenv(name);
    }
}
