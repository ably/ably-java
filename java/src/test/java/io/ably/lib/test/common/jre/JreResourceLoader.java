package io.ably.lib.test.common.jre;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.ably.lib.test.common.ResourceLoader;

public class JreResourceLoader implements ResourceLoader {
	public byte[] read(String resourceName) throws IOException {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new File("../lib/src/test/resources", resourceName));
			byte[] bytes = new byte[fis.available()];
			fis.read(bytes);
			return bytes;
		} finally {
			if(fis != null)
				fis.close();
		}
	} 
}
