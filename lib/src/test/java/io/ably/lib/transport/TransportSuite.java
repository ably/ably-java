package io.ably.lib.transport;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

/**
 * Created by piotrkazmierczak on 17.08.16.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        HostsTest.class
})
public class TransportSuite {

    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(TransportSuite.class);
        for(Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }
    }
}
