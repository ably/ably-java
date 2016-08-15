package io.ably.lib.test.utils;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HttpUtilsTest.class,
})
public class UtilsSuite {

    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(UtilsSuite.class);
        for(Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }
    }
}
