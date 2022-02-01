package io.ably.lib.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;

/**
 * Utility classes and interfaces for message payload encryption.
 *
 * This class supports AES/CBC/PKCS5 with a default key length of 256 bits
 * but supporting other key lengths. Other algorithms and chaining modes are
 * not supported directly, but supportable by extending/implementing the base
 * classes and interfaces here.
 *
 * Secure random data for creation of Initialisation Vectors (IVs) and keys
 * is obtained from the default system SecureRandom. Future extensions of this
 * class might make the SecureRandom pluggable or at least seedable with
 * client-provided entropy.
 *
 * Each message payload is encrypted with an IV in CBC mode, and the IV is
 * concatenated with the resulting raw ciphertext to construct the "ciphertext"
 * data passed to the recipient.
 */
public class Crypto {

    public static final String DEFAULT_ALGORITHM = "aes";
    public static final int DEFAULT_KEYLENGTH = is256BitsSupported() ? 256 : 128; // bits
    public static final int DEFAULT_BLOCKLENGTH = 16; // bytes

    /**
     * A class encapsulating the client-specifiable parameters for
     * the cipher.
     *
     * algorithm is the name of the algorithm in the default system provider,
     * or the lower-cased version of it; eg "aes" or "AES".
     *
     * Clients may instance a CipherParams directly and populate it, or may
     * query the implementation to obtain a default system CipherParams.
     */
    public static class CipherParams {
        private final String algorithm;
        private final int keyLength;
        private final SecretKeySpec keySpec;
        private final IvParameterSpec ivSpec;

        CipherParams(String algorithm, byte[] key, byte[] iv) throws NoSuchAlgorithmException {
            this.algorithm = (null == algorithm) ? DEFAULT_ALGORITHM : algorithm;
            keyLength = key.length * 8;
            keySpec = new SecretKeySpec(key, this.algorithm.toUpperCase(Locale.ROOT));
            ivSpec = new IvParameterSpec(iv);
        }

        /**
         * Returns the length of the key in bits (e.g. 256 for a 32 byte key).
         *
         * This method is package scoped as it is exposed for unit testing purposes.
         */
        int getKeyLength() {
            return keyLength;
        }

        /**
         * Returns the algorithm in the case that it was supplied on construction.
         *
         * Package scoped for unit testing purposes.
         */
        String getAlgorithm() {
            return algorithm;
        }
    }

    /**
     * Obtain a default CipherParams. This uses default algorithm, mode and
     * padding and key length. A key and IV are generated using the default
     * system SecureRandom; the key may be obtained from the returned CipherParams
     * for out-of-band distribution to other clients.
     * @return the CipherParams
     */
    public static CipherParams getDefaultParams() {
        return getParams(DEFAULT_ALGORITHM, DEFAULT_KEYLENGTH);
    }

    /**
     * Obtain a default CipherParams. This uses default algorithm, mode and
     * padding and initialises a key based on the given key data. The cipher
     * key length is derived from the length of the given key data. An IV is
     * generated using the default system SecureRandom.
     *
     * Use this method of constructing CipherParams if initialising a Channel
     * with a client-provided key, or to obtain a system-generated key of a
     * non-default key length.
     * @return the CipherParams
     */
    public static CipherParams getDefaultParams(byte[] key) {
        try {
            return getParams(DEFAULT_ALGORITHM, key);
        } catch (NoSuchAlgorithmException e) { return null; }
    }

    /**
     * Package scoped method for unit testing purposes.
     */
    static CipherParams getDefaultParams(byte[] key, byte[] iv) throws NoSuchAlgorithmException {
        return new CipherParams(DEFAULT_ALGORITHM, key, iv);
    }

    /**
     * Obtain a default CipherParams using Base64-encoded key. Same as above, throws
     * IllegalArgumentException if base64Key is invalid
     *
     * @param base64Key
     * @return
     */
    public static CipherParams getDefaultParams(String base64Key) {
        return getDefaultParams(Base64Coder.decode(base64Key));
    }

    /**
     * Package scoped method for unit testing purposes.
     */
    static CipherParams getDefaultParams(String base64Key, byte[] iv) throws NoSuchAlgorithmException {
        return new CipherParams(null, Base64Coder.decode(base64Key), iv);
    }

    public static CipherParams getParams(String algorithm, int keyLength) {
        if(algorithm == null) algorithm = DEFAULT_ALGORITHM;
        try {
            KeyGenerator keygen = KeyGenerator.getInstance(algorithm.toUpperCase(Locale.ROOT));
            keygen.init(keyLength);
            byte[] key = keygen.generateKey().getEncoded();
            return getParams(algorithm, key);
        } catch(NoSuchAlgorithmException e) { return null; }

    }

    public static CipherParams getParams(String algorithm, byte[] key) throws NoSuchAlgorithmException {
        byte[] ivBytes = new byte[DEFAULT_BLOCKLENGTH];
        secureRandom.nextBytes(ivBytes);
        return getParams(algorithm, key, ivBytes);
    }

    public static CipherParams getParams(String algorithm, byte[] key, byte[] iv) throws NoSuchAlgorithmException {
        return new CipherParams(algorithm, key, iv);
    }

    public static byte[] generateRandomKey(int keyLength) {
        byte[] result = new byte[(keyLength + 7)/8];
        secureRandom.nextBytes(result);
        return result;
    }

    public static byte[] generateRandomKey() {
        return generateRandomKey(DEFAULT_KEYLENGTH);
    }

    /**
     * Interface for a ChannelCipher instance that may be associated with a Channel.
     */
    public interface ChannelCipher {
        String getAlgorithm();
    }

    public interface EncryptingChannelCipher extends ChannelCipher {
        byte[] encrypt(byte[] plaintext) throws AblyException;
    }

    public interface DecryptingChannelCipher extends ChannelCipher {
        byte[] decrypt(byte[] ciphertext) throws AblyException;
    }

    private static CipherParams getParams(final Object cipherParams) throws AblyException {
        if (null == cipherParams)
            return Crypto.getDefaultParams();
        else if (cipherParams instanceof CipherParams)
            return (CipherParams)cipherParams;
        else
            throw AblyException.fromErrorInfo(new ErrorInfo("ChannelOptions not supported", 400, 40000));
    }

    /**
     * Internal; get an encrypting cipher instance based on the given channel options.
     */
    public static EncryptingChannelCipher getEncryptingCipher(final Object cipherParams) throws AblyException {
        return new EncryptingCBCCipher(getParams(cipherParams));
    }

    /**
     * Internal; get an decrypting cipher instance based on the given channel options.
     */
    public static DecryptingChannelCipher getDecryptingCipher(final Object cipherParams) throws AblyException {
        return new DecryptingCBCCipher(getParams(cipherParams));
    }

    /**
     * Internal: a class that implements a CBC mode ChannelCipher.
     * A single block of secure random data is provided for an initial IV.
     * Consecutive messages are chained in a manner that allows each to be
     * emitted with an IV, allowing each to be deciphered independently,
     * whilst avoiding having to obtain further entropy for IVs, and reinit
     * the cipher, between successive messages.
     *
     */
    private static class CBCCipher implements ChannelCipher {
        protected final SecretKeySpec keySpec;
        protected final IvParameterSpec ivSpec;
        protected final Cipher cipher;
        protected final int blockLength;
        private final String algorithm;

        protected CBCCipher(final CipherParams params) throws AblyException {
            final String cipherAlgorithm = params.getAlgorithm();
            String transformation = cipherAlgorithm.toUpperCase(Locale.ROOT) + "/CBC/PKCS5Padding";
            try {
                algorithm = cipherAlgorithm + '-' + params.getKeyLength() + "-cbc";
                keySpec = params.keySpec;
                ivSpec = params.ivSpec;
                blockLength = ivSpec.getIV().length;
                cipher = Cipher.getInstance(transformation);
            }
            catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw AblyException.fromThrowable(e);
            }
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }
    }

    private static class EncryptingCBCCipher extends CBCCipher implements EncryptingChannelCipher {
        private byte[] iv;

        EncryptingCBCCipher(final CipherParams params) throws AblyException {
            super(params);

            try {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
                throw AblyException.fromThrowable(e);
            }

            iv = params.ivSpec.getIV();
        }

        /**
         * A block containing zeros.
         */
        private static final byte[] emptyBlock = new byte[DEFAULT_BLOCKLENGTH];

        /**
         * The PKCS5 padding strings for given padded lengths.
         */
        private static final byte[][] pkcs5Padding = new byte[][] {
            new byte[] {16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16},
            new byte[] {1},
            new byte[] {2,2},
            new byte[] {3,3,3},
            new byte[] {4,4,4,4},
            new byte[] {5,5,5,5,5},
            new byte[] {6,6,6,6,6,6},
            new byte[] {7,7,7,7,7,7,7},
            new byte[] {8,8,8,8,8,8,8,8},
            new byte[] {9,9,9,9,9,9,9,9,9},
            new byte[] {10,10,10,10,10,10,10,10,10,10},
            new byte[] {11,11,11,11,11,11,11,11,11,11,11},
            new byte[] {12,12,12,12,12,12,12,12,12,12,12,12},
            new byte[] {13,13,13,13,13,13,13,13,13,13,13,13,13},
            new byte[] {14,14,14,14,14,14,14,14,14,14,14,14,14,14},
            new byte[] {15,15,15,15,15,15,15,15,15,15,15,15,15,15,15},
            new byte[] {16,16,16,16,16,16,16,16,16,16,16,16,16,16,16,16}
        };

        /**
         * Returns the padded length of a given plaintext, using PKCS5.
         */
        private static int getPaddedLength(int plaintextLength) {
            return (plaintextLength + DEFAULT_BLOCKLENGTH) & -DEFAULT_BLOCKLENGTH;
        }

        /**
         * Get an IV for the next message.
         * Returns either the IV that was used to initialise the ChannelCipher,
         * or generates an IV based on the current cipher state.
         */
        private byte[] getNextIv() {
            if (iv == null)
                return cipher.update(emptyBlock);

            final byte[] result = iv;
            iv = null;
            return result;
        }

        @Override
        public byte[] encrypt(byte[] plaintext) {
            if (plaintext == null) return null;
            final int plaintextLength = plaintext.length;
            final int paddedLength = getPaddedLength(plaintextLength);
            final byte[] cipherIn = new byte[paddedLength];
            final byte[] ciphertext = new byte[paddedLength + blockLength];
            final int padding = paddedLength - plaintextLength;
            System.arraycopy(plaintext, 0, cipherIn, 0, plaintextLength);
            System.arraycopy(pkcs5Padding[padding], 0, cipherIn, plaintextLength, padding);
            System.arraycopy(getNextIv(), 0, ciphertext, 0, blockLength);
            final byte[] cipherOut = cipher.update(cipherIn);
            System.arraycopy(cipherOut, 0, ciphertext, blockLength, paddedLength);
            return ciphertext;
        }
    }

    private static class DecryptingCBCCipher extends CBCCipher implements DecryptingChannelCipher {
        DecryptingCBCCipher(final CipherParams params) throws AblyException {
            super(params);
        }

        @Override
        public byte[] decrypt(byte[] ciphertext) throws AblyException {
            if(ciphertext == null) return null;
            byte[] plaintext = null;
            try {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ciphertext, 0, blockLength));
                plaintext = cipher.doFinal(ciphertext, blockLength, ciphertext.length - blockLength);
            }
            catch (InvalidKeyException|InvalidAlgorithmParameterException|IllegalBlockSizeException|BadPaddingException e) {
                Log.e(TAG, "decrypt()", e);
                throw AblyException.fromThrowable(e);
            }
            return plaintext;
        }
    }

    public static String getRandomId() {
        byte[] entropy = new byte[9];
        secureRandom.nextBytes(entropy);
        return Base64Coder.encodeToString(entropy);
    }

    /**
     * Returns a "request_id" query param, based on a sequence of 9 random bytes
     * which have been base64 encoded.
     *
     * Spec: RSC7c
     */
    public static Param generateRandomRequestId() {
        return new Param("request_id", Crypto.getRandomId());
    }

    /**
     * Determine whether or not 256-bit AES is supported. (If this determines that
     * it is not supported, install the JCE unlimited strength JCE extensions).
     * @return
     */
    private static boolean is256BitsSupported() {
        try {
            return Cipher.getMaxAllowedKeyLength(DEFAULT_ALGORITHM) >= 256;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * The default system SecureRandom
     */
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String TAG = Crypto.class.getName();
}
