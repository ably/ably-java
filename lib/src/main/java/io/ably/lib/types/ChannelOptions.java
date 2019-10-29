package io.ably.lib.types;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.ChannelCipher;

public class ChannelOptions {
	public boolean encrypted;
	public Object cipherParams;

	public final ChannelParams params = new ChannelParams();
	public final ChannelModes modes = new ChannelModes();

	public ChannelCipher getCipher() throws AblyException {
		if(!encrypted) return null;
		if(cipher != null) return cipher;
		return (cipher = Crypto.getCipher(this));
	}

	public static ChannelOptions fromCipherKey(byte[] key) throws AblyException {
		ChannelOptions options = new ChannelOptions();
		options.encrypted = true;
		options.cipherParams = Crypto.getDefaultParams(key);
		options.cipher = Crypto.getCipher(options);
		return options;
	}

	public static ChannelOptions fromCipherKey(String base64Key) throws AblyException {
		return fromCipherKey(Base64Coder.decode(base64Key));
	}

	private ChannelCipher cipher;
}
