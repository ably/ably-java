package io.ably.lib.types;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.ChannelCipher;

public class ChannelOptions {

	/**
	 * Cipher in use.
	 */
	private ChannelCipher cipher;

	/**
	 * Parameters for the cipher.
	 */
	public Object cipherParams;

	/**
	 * Are these options encrypted or not?
	 */
	public boolean encrypted;

	public ChannelCipher getCipher() throws AblyException {
		if(!this.encrypted) {
			return null;
		}
		if(this.cipher != null) {
			return this.cipher;
		} else {
			this.cipher = Crypto.getCipher(this);
			return this.cipher;
		}
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
}
