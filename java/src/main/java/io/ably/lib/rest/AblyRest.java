package io.ably.lib.rest;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.JavaPlatformAgentProvider;

/**
 * The top-level class to be instanced for the Ably REST library for JRE.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class AblyRest extends AblyBase {
    /**
     * Instance the Ably library using a key only.
     * This is simply a convenience constructor for the
     * simplest case of instancing the library with a key
     * for basic authentication and no other options.
     * @param key; String key (obtained from application dashboard)
     * @throws AblyException
     */
    public AblyRest(String key) throws AblyException {
        super(key, new JavaPlatformAgentProvider());
    }

    /**
     * Instance the Ably library with the given options.
     * @param options: see {@link io.ably.lib.types.ClientOptions} for options
     * @throws AblyException
     */
    public AblyRest(ClientOptions options) throws AblyException {
        super(options, new JavaPlatformAgentProvider());
    }
}
