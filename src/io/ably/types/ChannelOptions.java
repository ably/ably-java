package io.ably.types;

import io.ably.util.Crypto;
import io.ably.util.Crypto.ChannelCipher;

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
