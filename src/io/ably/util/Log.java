/*
 * Copyright 2011-2012 Paddy Byers
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.ably.util;

import java.io.PrintStream;

public class Log {

	public interface LogHandler {
		public void println(int severity, String tag, String msg, Throwable tr);
	}

	private static class PrintStreamHandler implements LogHandler {
		private final PrintStream mPrintStream;
		PrintStreamHandler(PrintStream printStream) {
			mPrintStream = printStream;
		}
		@Override
		public void println(int severity, String tag, String msg, Throwable tr) {
			mPrintStream.print("(" + severities[severity] + "): ");
			if(tag != null && tag.length() != 0)
				mPrintStream.print(tag + ": ");
			if(msg != null && msg.length() != 0)
				mPrintStream.print(msg);
			mPrintStream.println();
			if(tr != null) {
				tr.printStackTrace(mPrintStream);
			}
		}
	}

	/**
     * Priority constant to suppress all logging.
     */
    public static final int NONE = 99;

	/**
     * Priority constant; use Log.v.
     */
    public static final int VERBOSE = 2;

    /**
     * Priority constant; use Log.d.
     */
    public static final int DEBUG = 3;

    /**
     * Priority constant; use Log.i.
     */
    public static final int INFO = 4;

    /**
     * Priority constant; use Log.w.
     */
    public static final int WARN = 5;

    /**
     * Priority constant; use Log.e.
     */
    public static final int ERROR = 6;

	public static int v(String tag, String msg) {
		print(VERBOSE, tag, msg, null);
		return 0;
	}

	public static int v(String tag, String msg, Throwable tr) {
		print(VERBOSE, tag, msg, tr);
		return 0;
	}

	public static int d(String tag, String msg) {
		print(DEBUG, tag, msg, null);
		return 0;
	}

	public static int d(String tag, String msg, Throwable tr) {
		print(DEBUG, tag, msg, tr);
		return 0;
	}

	public static int i(String tag, String msg) {
		print(INFO, tag, msg, null);
		return 0;
	}

	public static int i(String tag, String msg, Throwable tr) {
		print(INFO, tag, msg, tr);
		return 0;
	}

	public static int w(String tag, String msg) {
		print(WARN, tag, msg, null);
		return 0;
	}

	public static int w(String tag, String msg, Throwable tr) {
		print(WARN, tag, msg, tr);
		return 0;
	}

	public static int w(String tag, Throwable tr) {
		print(WARN, tag, null, tr);
		return 0;
	}

	public static int e(String tag, String msg) {
		print(ERROR, tag, msg, null);
		return 0;
	}

	public static int e(String tag, String msg, Throwable tr) {
		print(ERROR, tag, msg, tr);
		return 0;
	}

    public static void setLevel(int level) { Log.level = (level != 0) ? level : defaultLevel; }
    public static int defaultLevel = NONE;
    public static int level = defaultLevel;

    public static void setHandler(LogHandler handler) { Log.handler = (handler != null) ? handler : defaultHandler; }
    public static LogHandler defaultHandler = new PrintStreamHandler(System.out);
    public static LogHandler handler = defaultHandler;

	private static String[] severities = new String[]{"", "", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT"};
	
	private static void print(int severity, String tag, String msg, Throwable tr) {
		if(severity >= level) {
			handler.println(severity, tag, msg, tr);
		}
	}
}
