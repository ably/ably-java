package io.ably.lib.test.realtime;

import static io.ably.lib.test.common.Helpers.assertMessagesEqual;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Message;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

public class RealtimeCryptoMessageTest extends ParameterizedTest {

	private static final String testDataFile128 = "ably-common/test-resources/crypto-data-128.json";
	private static final String testDataFile256 = "ably-common/test-resources/crypto-data-256.json";
	private static final String testDataFileCocoa = "ably-common/test-resources/crypto-data-256-cocoa.json";

	@Test
	public void encrypt_message_128() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile128, CryptoTestData.class);
		testEncrypt(testData, "cipher+aes-128-cbc");
	}

	@Test
	public void encrypt_message_256() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile256, CryptoTestData.class);
		testEncrypt(testData, "cipher+aes-256-cbc");
	}

	@Test
	public void encryptCocoa() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFileCocoa, CryptoTestData.class);
		testEncrypt(testData, "cipher+aes-256-cbc");
	}

	@Test
	public void decrypt_message_128() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile128, CryptoTestData.class);
		testDecrypt(testData, "cipher+aes-128-cbc");
	}

	@Test
	public void decrypt_message_256() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile256, CryptoTestData.class);
		testDecrypt(testData, "cipher+aes-256-cbc");
	}
	
	@Test
	public void decryptCocoa() throws IOException, NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFileCocoa, CryptoTestData.class);
		testDecrypt(testData, "cipher+aes-256-cbc");
	}

	private static void testDecrypt(final CryptoTestData testData, final String expectedEncryptedEncoding) throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final byte[] key = Base64Coder.decode(testData.key);
		final byte[] iv = Base64Coder.decode(testData.iv);
		final String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key, iv);
		final ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

		for(final CryptoTestItem item : testData.items) {
			final Message plain = item.encoded;
			final Message encrypted = item.encrypted;
			assertThat(encrypted.encoding, endsWith(expectedEncryptedEncoding + "/base64"));

			// if necessary, remove base64 encoding from plain-'text' message
			plain.decode(null);
			assertEquals(null, plain.encoding);

			// perform the decryption (via decode) which is the thing we need to test
			encrypted.decode(options);
			assertEquals(null, encrypted.encoding);

			// compare the expected plain-'text' bytes with those decrypted above
			assertMessagesEqual(plain, encrypted);
		}
	}

	private static void testEncrypt(final CryptoTestData testData, final String expectedEncryptedEncoding) throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException {
		final byte[] key = Base64Coder.decode(testData.key);
		final byte[] iv = Base64Coder.decode(testData.iv);
		final String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key, iv);

		for(final CryptoTestItem item : testData.items) {
			final ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};
			final Message plain = item.encoded;
			final Message encrypted = item.encrypted;
			assertThat(encrypted.encoding, endsWith(expectedEncryptedEncoding + "/base64"));

			// if necessary, remove base64 encoding from plain-'text' message
			plain.decode(null);
			assertEquals(null, plain.encoding);

			// perform the encryption (via encode) which is the thing we need to test
			plain.encode(options);
			assertThat(plain.encoding, endsWith(expectedEncryptedEncoding));

			// our test fixture always provides a string for the encrypted data, which means
			// that it's base64 encoded - so we need to base64 decode it to get the encrypted bytes
			final byte[] expected = Base64Coder.decode((String)encrypted.data);

			// compare the expected encrypted bytes with those encrypted above
			final byte[] actual = (byte[])plain.data;
			assertArrayEquals(expected, actual);
		}
	}

	static class CryptoTestData {
		public String algorithm;
		public String mode;
		public int keylength;
		public String key;
		public String iv;
		public CryptoTestItem[] items;
	}

	static class CryptoTestItem {
		public Message encoded;
		public Message encrypted;
	}
}
