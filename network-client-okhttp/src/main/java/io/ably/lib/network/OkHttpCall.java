package io.ably.lib.network;

import okhttp3.Call;
import okhttp3.Response;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class OkHttpCall implements HttpCall {
    private final Call call;

    public OkHttpCall(Call call) {
        this.call = call;
    }

    @Override
    public HttpResponse execute() {
        try (Response response = call.execute()) {
            return HttpResponse.builder()
                .headers(response.headers().toMultimap())
                .code(response.code())
                .message(response.message())
                .body(
                    response.body() != null && response.body().contentType() != null
                        ? new HttpBody(response.body().contentType().toString(), response.body().bytes())
                        : null
                )
                .build();

        } catch (ConnectException | SocketTimeoutException | UnknownHostException | NoRouteToHostException fce) {
            throw new FailedConnectionException(fce);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

    }

    @Override
    public void cancel() {
        call.cancel();
    }
}
