package io.ably.lib.test.common;

import java.io.IOException;

public interface ResourceLoader {
	public byte[] read(String resourceName) throws IOException; 
}
