package io.ably.lib.test.common;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.ably.lib.types.Param;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import jdk.nashorn.internal.runtime.Debug;

@RunWith(Parameterized.class)
public class ParameterizedTest {
	@Parameters(name = "{0}")
	public static Iterable<Setup.TestParameters> data() {
		return Arrays.asList(
				Setup.TestParameters.TEXT,
				Setup.TestParameters.BINARY
			);
	}

	@Parameter
	public Setup.TestParameters testParams;

	@Rule
	public Timeout testTimeout = Timeout.seconds(10);

	protected static Setup.TestVars testVars;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testVars = Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	protected DebugOptions createOptions() throws AblyException {
		return testVars.createOptions(testParams);
	}

	protected DebugOptions createOptions(String key) throws AblyException {
		return testVars.createOptions(key, testParams);
	}

	protected void fillInOptions(ClientOptions opts) {
		testVars.fillInOptions(opts, testParams);
	}

	/**
	 * Helper method to merge auth parameters
	 */
	protected static Param[] mergeParams(Param[][] items) {
		Map<String, Param> merged = new HashMap<String, Param>();
		for(Param[] item : items) {
			if(item != null) {
				for(Param param : item) { merged.put(param.key, param); }
			}
		}
		return merged.values().toArray(new Param[merged.size()]);
	}

	protected static Param[] mergeParams(Param[] target, Param[] src) {
		return mergeParams(new Param[][]{target, src});
	}

}
