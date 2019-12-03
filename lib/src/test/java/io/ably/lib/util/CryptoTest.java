package io.ably.lib.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Test;

import com.google.gson.stream.JsonWriter;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.util.Crypto.ChannelCipher;
import io.ably.lib.util.Crypto.CipherParams;
import io.ably.lib.util.CryptoMessageTest.FixtureSet;

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
		ChannelCipher channelCipher1 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params1; }});
		ChannelCipher channelCipher2 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params2; }});
		ChannelCipher channelCipher3 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params3; }});
		ChannelCipher channelCipher4 = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params4; }});

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

	/**
 	 * Test encryption using a 256 bit key and varying lengths of data.
	 *
 	 * The key, IV and message data are the same for every test run so that the
 	 * encrypted data may be exported from the console output for consumption by tests
 	 * run on other platforms. This output is manually merged into the file
 	 * test-resources/crypto-data-256.json in ably-common.
 	 *
	 * Equivalent to the following in ably-cocoa:
	 * testEncryptAndDecrypt in Spec/CryptoTest.m
	 * @throws IOException 
	 */
	@Test
	public void encryptAndDecrypt() throws NoSuchAlgorithmException, AblyException, IOException {
		final FixtureSet fixtureSet = FixtureSet.AES256;
		final CipherParams params = Crypto.getDefaultParams(fixtureSet.key, fixtureSet.iv);

		// Prepare message data.
		final int maxLength = 70;
		final byte[] message = new byte[maxLength];
		for (byte value=1; value<=maxLength; value++) {
			message[value - 1] = value;
		}

		final StringWriter target = new StringWriter();
		final JsonWriter writer = new JsonWriter(target);
		writer.setIndent("  ");
		writer.beginObject();

		writer.name("algorithm");
		writer.value("aes");

		writer.name("mode");
		writer.value("cbc");
		
		writer.name("keyLength");
		writer.value(256);
		
		writer.name("key");
		writer.value(Base64Coder.encodeToString(fixtureSet.key));
		
		writer.name("iv");
		writer.value(Base64Coder.encodeToString(fixtureSet.iv));

		// Perform encrypt and decrypt on message data trimmed at all lengths up
		// to and including maxLength.
		writer.name("items");
		writer.beginArray();
		for (int i=1; i<=maxLength; i++) {
			// We need to create a new ChannelCipher for each message we encode,
			// so that our IV gets used (being start of CBC chain).
			final ChannelCipher cipher = Crypto.getCipher(new ChannelOptions() {{ encrypted=true; cipherParams=params; }});

			// Encrypt i bytes from the start of the message data.
			final byte[] encoded = Arrays.copyOfRange(message, 0, i);
			final byte[] encrypted = cipher.encrypt(encoded);

			// Add encryption result to results in format ready for fixture.
			writeResult(writer, "byte 1 to " + i, encoded, encrypted);

			// Decrypt the encrypted data and verify the result is the same as what
			// we submitted for encryption.
			final byte[] verify = cipher.decrypt(encrypted);
			assertArrayEquals(verify, encoded);
		}
		writer.endArray();

		writer.endObject();

		System.out.println("Fixture JSON for test-resources:\n" + target.toString());
	}

	private void writeResult(final JsonWriter writer, final String name, final byte[] encoded, final byte[] encrypted) throws IOException {
		writer.beginObject();

		writer.name("encoded");
		writeData(writer, name, encoded, null);

		writer.name("encrypted");
		writeData(writer, name, encrypted, "cipher+aes-256-cbc");

		writer.endObject();
	}

	private static final String BASE64 = "base64";
	private void writeData(final JsonWriter writer, final String name, final byte[] data, final String encoding) throws IOException {
		writer.beginObject();

		writer.name("name");
		writer.value(name);

		writer.name("data");
		writer.value(Base64Coder.encodeToString(data));

		writer.name("encoding");
		writer.value(null == encoding ? BASE64 : encoding + "/" + BASE64);

		writer.endObject();
	}
}
