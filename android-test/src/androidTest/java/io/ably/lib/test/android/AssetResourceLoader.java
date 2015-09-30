package io.ably.lib.test.android;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import java.io.InputStream;
import java.io.IOException;
import io.ably.lib.test.common.ResourceLoader;

public class AssetResourceLoader implements ResourceLoader {
	public byte[] read(String resourceName) throws IOException {
		InputStream is = instrumentationCtx.getAssets().open(resourceName);
		try {
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			return bytes;
		} finally {
			is.close();
		}
	}

	private Context instrumentationCtx = InstrumentationRegistry.getContext();
}
