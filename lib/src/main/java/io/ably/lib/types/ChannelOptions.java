package io.ably.lib.types;

import java.util.Map;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.EncryptingChannelCipher;
import io.ably.lib.util.Crypto.DecryptingChannelCipher;

public class ChannelOptions {
    public Map<String, String> params;

    public ChannelMode[] modes;

    private EncryptingChannelCipher encryptingCipher;
    private DecryptingChannelCipher decryptingCipher;

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

    /**
     * Returns the cipher to be used for encrypting data on a channel, given the current state of this instance.
     * On the first call to this method a new cipher instance is created, with subsequent callers to this method being
     * returned that same cipher instance. This method is safe to be called from any thread.
     *
     * @apiNote Once this method has been called then the cipher is fixed based on the value of the
     * {@link #cipherParams} field at that time. If that field is then mutated, the cipher will not be updated.
     * This is not great API design and we should fix this under https://github.com/ably/ably-java/issues/745
     */
    public synchronized EncryptingChannelCipher getEncryptingCipher() throws AblyException {
        if (!encrypted) {
            throw new IllegalStateException("ChannelOptions encrypted field value is false.");
        }
        if (null == encryptingCipher) {
            encryptingCipher = Crypto.getEncryptingCipher(cipherParams);
        }
        return encryptingCipher;
    }

    /**
     * Returns the cipher to be used for decrypting data on a channel, given the current state of this instance.
     * On the first call to this method a new cipher instance is created, with subsequent callers to this method being
     * returned that same cipher instance. This method is safe to be called from any thread.
     *
     * @apiNote Once this method has been called then the cipher is fixed based on the value of the
     * {@link #cipherParams} field at that time. If that field is then mutated, the cipher will not be updated.
     * This is not great API design and we should fix this under https://github.com/ably/ably-java/issues/745
     */
    public synchronized DecryptingChannelCipher getDecryptingCipher() throws AblyException {
        if (!encrypted) {
            throw new IllegalStateException("ChannelOptions encrypted field value is false.");
        }
        if (null == decryptingCipher) {
            decryptingCipher = Crypto.getDecryptingCipher(cipherParams);
        }
        return decryptingCipher;
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
