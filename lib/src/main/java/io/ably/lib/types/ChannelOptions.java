package io.ably.lib.types;

import java.util.Map;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.ChannelCipher;

public class ChannelOptions {
	public Map<String, String> params;
	
	public ChannelMode[] modes;

	/**
	 * Cipher in use.
	 */
	private ChannelCipher cipher;

	/**
	 * Parameters for the cipher.
	 */
	public Object cipherParams;

	/**
	 * Whether or not this ChannelOptions is encrypted.
	 */
	public boolean encrypted;
	
	public boolean hasModes() {
		return null != modes && 0 != modes.length;
	}
	
	public boolean hasParams() {
		return null != params && !params.isEmpty();
	}
	
	public int getModeFlags() {
		int flags = 0;
		for (final ChannelMode mode : modes) {
			flags |= mode.getMask();
		}
		return flags;
	}
	
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

	/**
	 * <b>Deprecated. Use withCipherKey(byte[]) instead.</b><br><br>
	 * Create ChannelOptions from the given cipher key.
	 * @param key Byte array cipher key.
	 * @return Created ChannelOptions.
	 * @throws AblyException If something goes wrong.
	 */
	@Deprecated
	public static ChannelOptions fromCipherKey(byte[] key) throws AblyException {
		return withCipherKey(key);
	}

	/**
	 * <b>Deprecated. Use withCipherKey(String) instead.</b><br><br>
	 * Create ChannelOptions from the given cipher key.
	 * @param base64Key The cipher key as a base64-encoded String,
	 * @return Created ChannelOptions.
	 * @throws AblyException If something goes wrong.
	 */
	@Deprecated
	public static ChannelOptions fromCipherKey(String base64Key) throws AblyException {
		return fromCipherKey(Base64Coder.decode(base64Key));
	}

	/**
	 * Create ChannelOptions with the given cipher key.
	 * @param key Byte array cipher key.
	 * @return Created ChannelOptions.
	 * @throws AblyException If something goes wrong.
	 */
	public static ChannelOptions withCipherKey(byte[] key) throws AblyException {
		ChannelOptions options = new ChannelOptions();
		options.encrypted = true;
		options.cipherParams = Crypto.getDefaultParams(key);
		options.cipher = Crypto.getCipher(options);
		return options;
	}

	/**
	 * Create ChannelOptions with the given cipher key.
	 * @param base64Key The cipher key as a base64-encoded String,
	 * @return Created ChannelOptions.
	 * @throws AblyException If something goes wrong.
	 */
	public static ChannelOptions withCipherKey(String base64Key) throws AblyException {
		return withCipherKey(Base64Coder.decode(base64Key));
	}
}
