package io.ably.core.test.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Implementation of ResourceLoader for JRE environment
 */
public class JavaResourceLoader implements ResourceLoader {
    public byte[] read(String resourceName) throws IOException {
        FileInputStream fis = null;
        System.out.println("Current dir: " + new File(".").getAbsolutePath());
        try {
            try {
                fis = new FileInputStream(new File("../core/src/test/resources", resourceName));
            } catch(FileNotFoundException fnfe) {
                fis = new FileInputStream(new File("core/src/test/resources", resourceName));
            }
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            return bytes;
        } finally {
            if(fis != null)
                fis.close();
        }
    }
}
