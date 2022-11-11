package io.ably.lib.types;

import java.util.Map;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.ChannelCipher;
import io.ably.lib.util.Crypto.ChannelCipherSet;

/**
 * Passes additional properties to a {@link io.ably.lib.rest.Channel} or {@link io.ably.lib.realtime.Channel} object,
 * such as encryption, {@link ChannelMode} and channel parameters.
 */
public class ChannelOptions {
    /**
     * <a href="https://ably.com/docs/realtime/channels/channel-parameters/overview">Channel Parameters</a>
     * that configure the behavior of the channel.
     * <p>
     * Spec: TB2c
     */
    public Map<String, String> params;

    /**
     * An array of {@link ChannelMode} objects.
     * <p>
     * Spec: TB2d
     */
    public ChannelMode[] modes;

    private ChannelCipherSet cipherSet;

    /**
     * Requests encryption for this channel when not null,
     * and specifies encryption-related parameters (such as algorithm, chaining mode, key length and key).
     * See <a href="https://ably.com/docs/realtime/encryption#getting-started">an example</a>.
     * <p>
     * Spec: RSL5a, TB2b
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
     * Returns a wrapper around the cipher set to be used for this channel. This wrapper is only available in this API
     * to support customers who may have been using it in their applications with version 1.2.10 or before.
     *
     * @deprecated Since version 1.2.11, this method (which was only ever intended for internal use within this library
     * has been replaced by {@link #getCipherSet()}. It will be removed in the future.
     */
    @Deprecated
    public ChannelCipher getCipher() throws AblyException {
        return new ChannelCipher() {
            @Override
            public byte[] encrypt(byte[] plaintext) throws AblyException {
                return getCipherSet().getEncipher().encrypt(plaintext);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext) throws AblyException {
                return getCipherSet().getDecipher().decrypt(ciphertext);
            }

            @Override
            public String getAlgorithm() {
                try {
                    return getCipherSet().getEncipher().getAlgorithm();
                } catch (final AblyException e) {
                    throw new IllegalStateException("Unexpected exception when using legacy crypto cipher interface.", e);
                }
            }
        };
    }

    /**
     * Internal; this method is not intended for use by application developers. It may be changed or removed in future.
     *
     * Returns the cipher set to be used for encrypting and decrypting data on a channel, given the current state of
     * this instance. On the first call to this method a new cipher set instance is created, with subsequent callers to
     * this method being returned that same cipher set instance. This method is safe to be called from any thread.
     *
     * @apiNote Once this method has been called then the cipher set is fixed based on the value of the
     * {@link #cipherParams} field at that time. If that field is then mutated, the cipher set will not be updated.
     * This is not great API design and we should fix this under https://github.com/ably/ably-java/issues/745
     */
    public synchronized ChannelCipherSet getCipherSet() throws AblyException {
        if (!encrypted) {
            throw new IllegalStateException("ChannelOptions encrypted field value is false.");
        }
        if (null == cipherSet) {
            cipherSet = Crypto.createChannelCipherSet(cipherParams);
        }
        return cipherSet;
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
     * Constructor withCipherKey, that takes a key only.
     * <p>
     * Spec: TB3
     * @param key A private key used to encrypt and decrypt payloads.
     * @return A ChannelOptions object.
     * @throws AblyException If something goes wrong.
     */
    public static ChannelOptions withCipherKey(byte[] key) throws AblyException {
        ChannelOptions options = new ChannelOptions();
        options.encrypted = true;
        options.cipherParams = Crypto.getDefaultParams(key);
        return options;
    }

    /**
     * Constructor withCipherKey, that takes a key only.
     * <p>
     * Spec: TB3
     * @param base64Key A private key used to encrypt and decrypt payloads.
     * @return A ChannelOptions object.
     * @throws AblyException If something goes wrong.
     */
    public static ChannelOptions withCipherKey(String base64Key) throws AblyException {
        return withCipherKey(Base64Coder.decode(base64Key));
    }
}
