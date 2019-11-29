package io.ably.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Test;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.util.Crypto.CipherParams;

public class CryptoTest {
	/**
	 * Test Crypto.getDefaultParams.
	 * @see <a href="https://docs.ably.io/client-lib-development-guide/features/#RSE1">RSE1</a>
	 */
	@Test
	public void cipher_params() throws AblyException, NoSuchAlgorithmException {
		/* 128-bit key */
		/* {0xFF, 0xFE, 0xFD, 0xFC, 0xFB, 0xFA, 0xF9, 0xF8, 0xF7, 0xF6, 0xF5, 0xF4, 0xF3, 0xF2, 0xF1, 0xF0}; */
		byte[] key = {-1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -15, -16};
		/* Same key but encoded with Base64 */
		String base64key = "//79/Pv6+fj39vX08/Lx8A==";
		/* Same key but encoded in URL style (RFC 4648 s.5) */
		String base64key2 = "__79_Pv6-fj39vX08_Lx8A==";

		/* IV */
		byte[] iv = {16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

		final CipherParams params1 = Crypto.getDefaultParams(key, iv);
		final CipherParams params2 = Crypto.getDefaultParams(base64key, iv);
		final CipherParams params3 = new CipherParams(null, key, iv);
		final CipherParams params4 = Crypto.getDefaultParams(base64key2, iv);

		assertEquals("aes", params1.getAlgorithm());

		assertTrue(
		    "Key length is incorrect",
		    params1.getKeyLength() == key.length*8 &&
		    params2.getKeyLength() == key.length*8 &&
		    params3.getKeyLength() == key.length*8 &&
		    params4.getKeyLength() == key.length*8
		);

		byte[] plaintext = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		Crypto.ChannelCipher channelCipher1 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params1; }});
		Crypto.ChannelCipher channelCipher2 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params2; }});
		Crypto.ChannelCipher channelCipher3 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params3; }});
		Crypto.ChannelCipher channelCipher4 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params4; }});

		byte[] ciphertext1 = channelCipher1.encrypt(plaintext);
		byte[] ciphertext2 = channelCipher2.encrypt(plaintext);
		byte[] ciphertext3 = channelCipher3.encrypt(plaintext);
		byte[] ciphertext4 = channelCipher4.encrypt(plaintext);

		assertTrue(
		    "All ciphertexts should be the same.",
		    Arrays.equals(ciphertext1, ciphertext2) &&
		    Arrays.equals(ciphertext1, ciphertext3) &&
		    Arrays.equals(ciphertext1, ciphertext4)
		);
	}
}
