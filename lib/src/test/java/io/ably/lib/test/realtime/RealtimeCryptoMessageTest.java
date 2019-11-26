package io.ably.lib.test.realtime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.spec.IvParameterSpec;

import io.ably.lib.types.*;
import org.junit.Test;

import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

public class RealtimeCryptoMessageTest extends ParameterizedTest {

	private static final String testDataFile128 = "ably-common/test-resources/crypto-data-128.json";
	private static final String testDataFile256 = "ably-common/test-resources/crypto-data-256.json";

	@Test
	public void encrypt_message_128() throws IOException, AblyException, NoSuchAlgorithmException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile128, CryptoTestData.class);
		byte[] key = Base64Coder.decode(testData.key);
		byte[] iv = Base64Coder.decode(testData.iv);
		String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key);
		params.ivSpec = new IvParameterSpec(iv);

		CryptoTestItem[] items = testData.items;
		for(int i = 0; i < items.length; i++) {
			CryptoTestItem item = items[i];

			/* read messages from test data */
			Message testMessage = item.encoded;
			Message encryptedMessage = item.encrypted;

			/* decode (ie remove any base64 encoding) */
			testMessage.decode(null);
			try {
				encryptedMessage.decode(null);
			} catch (MessageDecodeException e) {}

			/* reset channel cipher, to ensure it uses the given iv */
			ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

			/* encrypt plaintext message; encode() also to handle data that is not already string or buffer */
			testMessage.encode(options);

			/* compare */
			assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
		}
	}

	@Test
	public void encrypt_message_256() throws IOException, AblyException, NoSuchAlgorithmException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile256, CryptoTestData.class);
		byte[] key = Base64Coder.decode(testData.key);
		byte[] iv = Base64Coder.decode(testData.iv);
		String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key);
		params.ivSpec = new IvParameterSpec(iv);

		CryptoTestItem[] items = testData.items;
		for(int i = 0; i < items.length; i++) {
			CryptoTestItem item = items[i];

			/* read messages from test data */
			Message testMessage = item.encoded;
			Message encryptedMessage = item.encrypted;

			/* decode (ie remove any base64 encoding) */
			testMessage.decode(null);
			try {
				encryptedMessage.decode(null);
			} catch (MessageDecodeException e) {}

			/* reset channel cipher, to ensure it uses the given iv */
			ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

			/* encrypt plaintext message; encode() also to handle data that is not already string or buffer */
			testMessage.encode(options);

			/* compare */
			assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
		}
	}

	@Test
	public void decrypt_message_128() throws IOException, MessageDecodeException, NoSuchAlgorithmException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile128, CryptoTestData.class);
		byte[] key = Base64Coder.decode(testData.key);
		byte[] iv = Base64Coder.decode(testData.iv);
		String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key);
		params.ivSpec = new IvParameterSpec(iv);

		CryptoTestItem[] items = testData.items;
		for(int i = 0; i < items.length; i++) {
			CryptoTestItem item = items[i];

			/* read messages from test data */
			Message testMessage = item.encoded;
			Message encryptedMessage = item.encrypted;

			/* decode (ie remove any base64 encoding) */
			testMessage.decode(null);
			try {
				encryptedMessage.decode(null);
			} catch (MessageDecodeException e) {}

			/* reset channel cipher, to ensure it uses the given iv */
			ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

			/* decrypt message; decode() also to handle data that is not already string or buffer */
			encryptedMessage.decode(options);

			/* compare */
			assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
		}
	}

	@Test
	public void decrypt_message_256() throws IOException, MessageDecodeException, NoSuchAlgorithmException {
		final CryptoTestData testData = (CryptoTestData)Setup.loadJson(testDataFile256, CryptoTestData.class);
		byte[] key = Base64Coder.decode(testData.key);
		byte[] iv = Base64Coder.decode(testData.iv);
		String algorithm = testData.algorithm;

		final CipherParams params = Crypto.getParams(algorithm, key);
		params.ivSpec = new IvParameterSpec(iv);

		CryptoTestItem[] items = testData.items;
		for(int i = 0; i < items.length; i++) {
			CryptoTestItem item = items[i];

			/* read messages from test data */
			Message testMessage = item.encoded;
			Message encryptedMessage = item.encrypted;

			/* decode (ie remove any base64 encoding) */
			testMessage.decode(null);
			try {
				encryptedMessage.decode(null);
			} catch (MessageDecodeException e) {}

			/* reset channel cipher, to ensure it uses the given iv */
			ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

			/* decrypt message; decode() also to handle data that is not already string or buffer */
			encryptedMessage.decode(options);

			/* compare */
			assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
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
