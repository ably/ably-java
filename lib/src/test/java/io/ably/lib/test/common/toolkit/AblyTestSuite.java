package io.ably.lib.test.common.toolkit;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class AblyTestSuite extends Suite {
    public AblyTestSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
        this.setScheduler(new ParallelScheduler());
    }
}
