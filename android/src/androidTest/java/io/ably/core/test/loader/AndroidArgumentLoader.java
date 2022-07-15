package io.ably.core.test.loader;

import android.os.Bundle;
import android.support.test.InstrumentationRegistry;

public class AndroidArgumentLoader implements ArgumentLoader {
    public String getTestArgument(String name) {
        Bundle arguments = InstrumentationRegistry.getArguments();
        return arguments.getString(name);
    }
}
