package io.ably.lib.transport;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class ConnectionManagerPingErrorTest {

    /**
     * Pinging while not connected reports Ably code 40000 with HTTP status code 400.
     */
    @Test
    public void ping_when_not_connected_error_codes() throws AblyException {
        ClientOptions options = new ClientOptions("appid.keyid:keysecret");
        options.autoConnect = false;
        AtomicReference<ErrorInfo> error = new AtomicReference<>();
        try (AblyRealtime ably = new AblyRealtime(options)) {
            ably.connection.ping(new CompletionListener() {
                @Override
                public void onSuccess() {}

                @Override
                public void onError(ErrorInfo reason) {
                    error.set(reason);
                }
            });
        }
        assertThat(error.get(), is(notNullValue()));
        assertThat(error.get().code, is(40000));
        assertThat(error.get().statusCode, is(400));
    }
}
