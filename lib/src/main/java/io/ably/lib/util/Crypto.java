package io.ably.lib.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ErrorInfo;

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
		public final String algorithm;
		public final int keyLength;
		public final SecretKeySpec keySpec;
		public IvParameterSpec ivSpec;

		public CipherParams(String algorithm, byte[] key) throws NoSuchAlgorithmException {
			if(algorithm == null) algorithm = DEFAULT_ALGORITHM;
			this.algorithm = algorithm;
			this.keyLength = key.length * 8;
			this.keySpec = new SecretKeySpec(key, algorithm.toUpperCase());
		}

		private CipherParams(String algorithm, int keyLength, SecretKeySpec keySpec, IvParameterSpec ivSpec) {
			this.algorithm = algorithm;
			this.keyLength = keyLength;
			this.keySpec = keySpec;
			this.ivSpec = ivSpec;
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
	 * Obtain default CipherParams using key and algorithm from other CipherParams object
	 * @param params
	 * @return
	 */
	public static CipherParams getDefaultParams(CipherParams params) {
		return new CipherParams(params.algorithm, params.keyLength, params.keySpec, params.ivSpec);
	}

	public static CipherParams getParams(String algorithm, int keyLength) {
		if(algorithm == null) algorithm = DEFAULT_ALGORITHM;
		try {
			KeyGenerator keygen = KeyGenerator.getInstance(algorithm.toUpperCase());
	        keygen.init(keyLength);
	        byte[] key = keygen.generateKey().getEncoded();
	        return getParams(algorithm, key);
		} catch(NoSuchAlgorithmException e) { return null; }

	}

	public static CipherParams getParams(String algorithm, byte[] key) throws NoSuchAlgorithmException {
		CipherParams params = new CipherParams(algorithm, key);
		byte[] ivBytes = new byte[DEFAULT_BLOCKLENGTH];
		secureRandom.nextBytes(ivBytes);
		params.ivSpec = new IvParameterSpec(ivBytes);
		return params;
	}

	/**
	 * Interface for a ChannelCipher instance that may be associated with a Channel.
	 *
	 */
	public static interface ChannelCipher {
		public byte[] encrypt(byte[] plaintext) throws AblyException;
		public byte[] decrypt(byte[] ciphertext) throws AblyException;
		public String getAlgorithm();
	}

	/**
	 * Internal; get a ChannelCipher instance based on the given ChannelOptions
	 * @param opts
	 * @return
	 * @throws AblyException
	 */
	public static ChannelCipher getCipher(ChannelOptions opts) throws AblyException {
		CipherParams params;
		if(opts.cipherParams == null)
			params = Crypto.getDefaultParams();
		else if(opts.cipherParams instanceof CipherParams)
			params = (CipherParams)opts.cipherParams;
		else
			throw AblyException.fromErrorInfo(new ErrorInfo("ChannelOptions not supported", 400, 40000));

		return new CBCCipher(params);
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
		private final SecretKeySpec keySpec;
		private final Cipher encryptCipher;
		private final Cipher decryptCipher;
		private final String algorithm;
		private final int blockLength;
		private byte[] iv;

		private CBCCipher(CipherParams params) throws AblyException {
			String transformation = params.algorithm.toUpperCase() + "/CBC/PKCS5Padding";
			try {
				algorithm = params.algorithm + '-' + params.keyLength + "-cbc";
				keySpec = params.keySpec;
				encryptCipher = Cipher.getInstance(transformation);
				encryptCipher.init(Cipher.ENCRYPT_MODE, params.keySpec, params.ivSpec);
				decryptCipher = Cipher.getInstance(transformation);
				iv = params.ivSpec.getIV();
				blockLength = iv.length;
			}
			catch (NoSuchAlgorithmException|NoSuchPaddingException|InvalidAlgorithmParameterException|InvalidKeyException e) {
				throw AblyException.fromThrowable(e);
			}
		}

		@Override
		public byte[] encrypt(byte[] plaintext) {
			if(plaintext == null) return null;
			int plaintextLength = plaintext.length;
			int paddedLength = getPaddedLength(plaintextLength);
			byte[] cipherIn = new byte[paddedLength];
			byte[] ciphertext = new byte[paddedLength + blockLength];
			int padding = paddedLength - plaintextLength;
			System.arraycopy(plaintext, 0, cipherIn, 0, plaintextLength);
			System.arraycopy(pkcs5Padding[padding], 0, cipherIn, plaintextLength, padding);
			System.arraycopy(getIv(), 0, ciphertext, 0, blockLength);
			byte[] cipherOut = encryptCipher.update(cipherIn);
			System.arraycopy(cipherOut, 0, ciphertext, blockLength, paddedLength);
			return ciphertext;
		}

		@Override
		public byte[] decrypt(byte[] ciphertext) throws AblyException {
			if(ciphertext == null) return null;
			byte[] plaintext = null;
			try {
				decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ciphertext, 0, blockLength));
				plaintext = decryptCipher.doFinal(ciphertext, blockLength, ciphertext.length - blockLength);
			}
			catch (InvalidKeyException|InvalidAlgorithmParameterException|IllegalBlockSizeException|BadPaddingException e) {
				Log.e(TAG, "decrypt()", e);
				throw AblyException.fromThrowable(e);
			}
			return plaintext;
		}

		@Override
		public String getAlgorithm() {
			return algorithm;
		}

		/**
		 * Internal: get an IV for the next message.
		 * Returns either the IV that was used to initialise the ChannelCipher,
		 * of generates an IV based on the current cipher state.
		 * @return
		 */
		private byte[] getIv() {
			if(iv == null)
				return encryptCipher.update(emptyBlock);

			byte[] result = iv;
			iv = null;
			return result;
		}

		/**
		 * Internal: calculate the padded length of a given plaintext
		 * using PKCS5.
		 * @param plaintextLength
		 * @return
		 */
		private static final int getPaddedLength(int plaintextLength) {
			return (plaintextLength + DEFAULT_BLOCKLENGTH) & -DEFAULT_BLOCKLENGTH;
		}

		/**
		 * Internal: a block containing zeros
		 */
		private static final byte[] emptyBlock = new byte[DEFAULT_BLOCKLENGTH];

		/**
		 * Internal: obtain the pkcs5 padding string for a given padded length;
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
