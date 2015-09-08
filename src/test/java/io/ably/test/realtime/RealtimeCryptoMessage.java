package io.ably.test.realtime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ably.realtime.AblyRealtime;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.ChannelOptions;
import io.ably.types.ClientOptions;
import io.ably.types.Message;
import io.ably.util.Base64Coder;
import io.ably.util.Crypto;
import io.ably.util.Crypto.CipherParams;
import io.ably.util.Serialisation;

import java.io.IOException;

import javax.crypto.spec.IvParameterSpec;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

public class RealtimeCryptoMessage {

	private static final String testDataFile128 = "deps/ably-common/deps/ably-common/test-resources/crypto-data-128.json";
	private static final String testDataFile256 = "deps/ably-common/deps/ably-common/test-resources/crypto-data-256.json";
	private static CryptoTestData testData128;
	private static CryptoTestData testData256;

	@Test
	public void encrypt_message_128() {
		try {
			testData128 = (CryptoTestData)Helpers.loadJSON(testDataFile128, Serialisation.jsonObjectMapper, new TypeReference<CryptoTestData>(){});
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.key);
			byte[] iv = Base64Coder.decode(testData128.iv);

			final CipherParams params = Crypto.getDefaultParams(key);
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
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void encrypt_message_256() {
		try {
			testData256 = (CryptoTestData)Helpers.loadJSON(testDataFile256, Serialisation.jsonObjectMapper, new TypeReference<CryptoTestData>(){});
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.key);
			byte[] iv = Base64Coder.decode(testData256.iv);

			final CipherParams params = Crypto.getDefaultParams(key);
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
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void decrypt_message_128() {
		try {
			testData128 = (CryptoTestData)Helpers.loadJSON(testDataFile128, Serialisation.jsonObjectMapper, new TypeReference<CryptoTestData>(){});
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.key);
			byte[] iv = Base64Coder.decode(testData128.iv);

			final CipherParams params = Crypto.getDefaultParams(key);
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
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void decrypt_message_256() {
		try {
			testData256 = (CryptoTestData)Helpers.loadJSON(testDataFile256, Serialisation.jsonObjectMapper, new TypeReference<CryptoTestData>(){});
		} catch (IOException e1) {
			fail();
			return;
		}
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.key);
			byte[] iv = Base64Coder.decode(testData256.iv);

			final CipherParams params = Crypto.getDefaultParams(key);
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
