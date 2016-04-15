package io.ably.lib.util;

/**
 * A simple utility class to dump binary data.
 */
public class HexDump {

	/**
	 * Build a readable hex dump of the given string.
	 * @param str the string
	 * @return hex dump
	 */
	public static String dump(String str) {
		return dump(str.getBytes());
	}

	/**
	 * Build a readable hex dump of the given bytes.
	 * @param bytes the byte array
	 * @return hex dump
	 */
	public static String dump(byte[] bytes) {
		int i = 0, len = bytes.length;
		StringBuffer result = new StringBuffer();
		while(i < len) {
			StringBuffer charBuf = new StringBuffer();
			StringBuffer hexBuf = new StringBuffer();
			for(int j = 0; j < 16; j++) {
				if(i >= len) {
					hexBuf.append("   ");
					continue;
				}
				byte b = bytes[i++];
				hexBuf.append(hex(b)).append(' ');
				charBuf.append(printable(b));
			}
			result.append(hexBuf).append("  ").append(charBuf).append('\n');
		}
		return result.toString();
	}

	private static final char[] hexChars = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	private static int uint8(byte b) {
		return (int)b & 0xff;
	}

	private static String hex(byte b) {
		int i = uint8(b);
		return new String(new char[] {hexChars[i / 16], hexChars[i % 16]});
	}

	private static char printable(byte b) {
		int i = uint8(b);
		return (i >= 32 && i <= 126) ? (char)i : '.';
	}

}
