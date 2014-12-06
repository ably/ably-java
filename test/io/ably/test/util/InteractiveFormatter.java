package io.ably.test.util;

import java.io.OutputStream;
import java.io.PrintStream;

import junit.framework.AssertionFailedError;
import junit.framework.Test;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;

public class InteractiveFormatter implements JUnitResultFormatter {
    private PrintStream out = System.out;
    private long startTimestamp;

    @Override
    public void addError(Test test, Throwable error) {
        logResult(test, "ERR", System.currentTimeMillis() - startTimestamp);
        out.println(error.getMessage());
    }

    @Override
    public void addFailure(Test test, AssertionFailedError failure) {
        logResult(test, "FAIL", System.currentTimeMillis() - startTimestamp);
        out.println(failure.getMessage());
    }

    @Override
    public void endTest(Test test) {
        logResult(test, "PASS", System.currentTimeMillis() - startTimestamp);
    }

    @Override
    public void startTest(Test test) {
    	startTimestamp = System.currentTimeMillis();
    }

    @Override
    public void endTestSuite(JUnitTest testSuite) { }

    @Override
    public void setOutput(OutputStream out) {
        this.out = new PrintStream(out);
    }

    @Override
    public void setSystemError(String err) {
    	System.err.println(err);
    }

    @Override
    public void setSystemOutput(String out) {
    	System.out.println(out);
    }

    @Override
    public void startTestSuite(JUnitTest testSuite) { }

    private void logResult(Test test, String result, long duration) {
        out.println("[" + result + "] " + String.valueOf(test) + " (" + duration + " ms)");
        out.flush();
    }

}
