package io.ably.lib.types;

import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.ChannelCipher;

public class ChannelOptions {
	public boolean encrypted;
	public Object cipherParams;

	public ChannelCipher getCipher() throws AblyException {
		if(!encrypted) return null;
		if(cipher != null) return cipher;
		return (cipher = Crypto.getCipher(this));
	}

	private ChannelCipher cipher;
}
