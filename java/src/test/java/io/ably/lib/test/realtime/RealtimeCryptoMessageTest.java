package io.ably.lib.test.realtime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import javax.crypto.spec.IvParameterSpec;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

public class RealtimeCryptoMessageTest {

	private static final String testDataFile128 = "ably-common/test-resources/crypto-data-128.json";
	private static final String testDataFile256 = "ably-common/test-resources/crypto-data-256.json";
	private static CryptoTestData testData128;
	private static CryptoTestData testData256;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	@Test
	public void encrypt_message_128() {
		try {
			testData128 = (CryptoTestData)Setup.loadJSON(testDataFile128, CryptoTestData.class);
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.key);
			byte[] iv = Base64Coder.decode(testData128.iv);
			String algorithm = testData128.algorithm;

			final CipherParams params = Crypto.getParams(algorithm, key);
			params.ivSpec = new IvParameterSpec(iv);

			CryptoTestItem[] items = testData128.items;
			for(int i = 0; i < items.length; i++) {
				CryptoTestItem item = items[i];

				/* read messages from test data */
				Message testMessage = item.encoded;
				Message encryptedMessage = item.encrypted;

				/* decode (ie remove any base64 encoding) */
				testMessage.decode(null);
				encryptedMessage.decode(null);

				/* reset channel cipher, to ensure it uses the given iv */
				ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

				/* encrypt plaintext message; encode() also to handle data that is not already string or buffer */
				testMessage.encode(options);

				/* compare */
				assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (java.security.NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("Unexpected Algorithm exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void encrypt_message_256() {
		try {
			testData256 = (CryptoTestData)Setup.loadJSON(testDataFile256, CryptoTestData.class);
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.key);
			byte[] iv = Base64Coder.decode(testData256.iv);
			String algorithm = testData256.algorithm;

			final CipherParams params = Crypto.getParams(algorithm, key);
			params.ivSpec = new IvParameterSpec(iv);

			CryptoTestItem[] items = testData256.items;
			for(int i = 0; i < items.length; i++) {
				CryptoTestItem item = items[i];

				/* read messages from test data */
				Message testMessage = item.encoded;
				Message encryptedMessage = item.encrypted;

				/* decode (ie remove any base64 encoding) */
				testMessage.decode(null);
				encryptedMessage.decode(null);

				/* reset channel cipher, to ensure it uses the given iv */
				ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

				/* encrypt plaintext message; encode() also to handle data that is not already string or buffer */
				testMessage.encode(options);

				/* compare */
				assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (java.security.NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("Unexpected Algorithm exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void decrypt_message_128() {
		try {
			testData128 = (CryptoTestData)Setup.loadJSON(testDataFile128, CryptoTestData.class);
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.key);
			byte[] iv = Base64Coder.decode(testData128.iv);
			String algorithm = testData128.algorithm;

			final CipherParams params = Crypto.getParams(algorithm, key);
			params.ivSpec = new IvParameterSpec(iv);

			CryptoTestItem[] items = testData128.items;
			for(int i = 0; i < items.length; i++) {
				CryptoTestItem item = items[i];

				/* read messages from test data */
				Message testMessage = item.encoded;
				Message encryptedMessage = item.encrypted;

				/* decode (ie remove any base64 encoding) */
				testMessage.decode(null);
				encryptedMessage.decode(null);

				/* reset channel cipher, to ensure it uses the given iv */
				ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

				/* decrypt message; decode() also to handle data that is not already string or buffer */
				encryptedMessage.decode(options);

				/* compare */
				assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (java.security.NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("Unexpected Algorithm exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void decrypt_message_256() {
		try {
			testData256 = (CryptoTestData)Setup.loadJSON(testDataFile256, CryptoTestData.class);
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.key);
			byte[] iv = Base64Coder.decode(testData256.iv);
			String algorithm = testData256.algorithm;

			final CipherParams params = Crypto.getParams(algorithm, key);
			params.ivSpec = new IvParameterSpec(iv);

			CryptoTestItem[] items = testData256.items;
			for(int i = 0; i < items.length; i++) {
				CryptoTestItem item = items[i];

				/* read messages from test data */
				Message testMessage = item.encoded;
				Message encryptedMessage = item.encrypted;

				/* decode (ie remove any base64 encoding) */
				testMessage.decode(null);
				encryptedMessage.decode(null);

				/* reset channel cipher, to ensure it uses the given iv */
				ChannelOptions options = new ChannelOptions() {{encrypted = true; cipherParams = params;}};

				/* decrypt message; decode() also to handle data that is not already string or buffer */
				encryptedMessage.decode(options);

				/* compare */
				assertTrue(Helpers.compareMessage(testMessage, encryptedMessage));
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} catch (java.security.NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail("Unexpected Algorithm exception");
		} finally {
			if(ably != null)
				ably.close();
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
