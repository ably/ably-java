package io.ably.lib.util;

import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;

public class ParamsUtils {

    public static Param[] enrichParams(Param[] params, ClientOptions options) {
        if (options.pushFullWait) {
            params = Param.push(params, "fullWait", "true");
        }
        if (options.addRequestIds) { // RSC7c
            params = Param.set(params, Crypto.generateRandomRequestId());
        }

        return params;
    }
}
