package io.ably.lib.util;

import static io.ably.lib.test.common.Helpers.assertMessagesEqual;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Message;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

@RunWith(Parameterized.class)
public class CryptoMessageTest {
	private static final String AES128 = "cipher+aes-128-cbc";
	private static final String AES256 = "cipher+aes-256-cbc";

	@Parameters(name= "{0}_{1}")
	public static Object[][] data() {
		return new Object[][] {
			{ "crypto-data-128", AES128 },
			{ "crypto-data-256", AES256 },
			{ "crypto-data-256-variable-lengths", AES256 },
		};
	}

	private final String fileName;
	private final String expectedEncryptedEncoding;

	public CryptoMessageTest(final String fileName, final String expectedEncryptedEncoding) {
		this.fileName = fileName;
		this.expectedEncryptedEncoding = expectedEncryptedEncoding;
	}

	private static final CryptoTestData loadTestData(final String fileName) throws IOException {
		return (CryptoTestData)Setup.loadJson(
			"ably-common/test-resources/" + fileName + ".json",
			CryptoTestData.class);
	}

	@Test
	public void testDecrypt() throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException, IOException {
		final CryptoTestData testData = loadTestData(fileName);
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

	@Test
	public void testEncrypt() throws NoSuchAlgorithmException, CloneNotSupportedException, AblyException, IOException {
		final CryptoTestData testData = loadTestData(fileName);
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
