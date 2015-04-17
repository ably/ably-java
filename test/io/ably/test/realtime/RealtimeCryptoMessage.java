package io.ably.test.realtime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ably.realtime.AblyRealtime;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.ChannelOptions;
import io.ably.types.Message;
import io.ably.types.ClientOptions;
import io.ably.util.Base64Coder;
import io.ably.util.Crypto;
import io.ably.util.Crypto.CipherParams;

import java.io.IOException;

import javax.crypto.spec.IvParameterSpec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

public class RealtimeCryptoMessage {

	private static final String testDataFile128 = "test/io/ably/test/assets/crypto-data-128.json";
	private static final String testDataFile256 = "test/io/ably/test/assets/crypto-data-256.json";
	private static JSONObject testData128;
	private static JSONObject testData256;

	@BeforeClass
	public static void initTestData() {
		try {
			testData128 = Helpers.loadJSON(testDataFile128);
			testData256 = Helpers.loadJSON(testDataFile256);
		} catch(IOException ioe) {
			System.err.println("Unable to read spec file: " + ioe);
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	@Test
	public void encrypt_message_128() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.optString("key"));
			byte[] iv = Base64Coder.decode(testData128.optString("iv"));

			final CipherParams params = Crypto.getDefaultParams(key);
			params.ivSpec = new IvParameterSpec(iv);

			JSONArray items = testData128.optJSONArray("items");
			for(int i = 0; i < items.length(); i++) {
				JSONObject item = items.optJSONObject(i);

				/* read messages from test data */
				Message testMessage = Message.fromJSON(item.optJSONObject("encoded"));
				Message encryptedMessage = Message.fromJSON(item.optJSONObject("encrypted"));
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
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.optString("key"));
			byte[] iv = Base64Coder.decode(testData256.optString("iv"));

			final CipherParams params = Crypto.getDefaultParams(key);
			params.ivSpec = new IvParameterSpec(iv);

			JSONArray items = testData256.optJSONArray("items");
			for(int i = 0; i < items.length(); i++) {
				JSONObject item = items.optJSONObject(i);

				/* read messages from test data */
				Message testMessage = Message.fromJSON(item.optJSONObject("encoded"));
				Message encryptedMessage = Message.fromJSON(item.optJSONObject("encrypted"));
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
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData128.optString("key"));
			byte[] iv = Base64Coder.decode(testData128.optString("iv"));

			final CipherParams params = Crypto.getDefaultParams(key);
			params.ivSpec = new IvParameterSpec(iv);

			JSONArray items = testData128.optJSONArray("items");
			for(int i = 0; i < items.length(); i++) {
				JSONObject item = items.optJSONObject(i);

				/* read messages from test data */
				Message testMessage = Message.fromJSON(item.optJSONObject("encoded"));
				Message encryptedMessage = Message.fromJSON(item.optJSONObject("encrypted"));
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
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			byte[] key = Base64Coder.decode(testData256.optString("key"));
			byte[] iv = Base64Coder.decode(testData256.optString("iv"));

			final CipherParams params = Crypto.getDefaultParams(key);
			params.ivSpec = new IvParameterSpec(iv);

			JSONArray items = testData256.optJSONArray("items");
			for(int i = 0; i < items.length(); i++) {
				JSONObject item = items.optJSONObject(i);

				/* read messages from test data */
				Message testMessage = Message.fromJSON(item.optJSONObject("encoded"));
				Message encryptedMessage = Message.fromJSON(item.optJSONObject("encrypted"));
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
}
