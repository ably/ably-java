package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.KeyGenerator;

import org.junit.Before;
import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.Crypto;
import io.ably.lib.util.Crypto.CipherParams;

public class RestCryptoTest extends ParameterizedTest {

    private static final String TAG = RestCryptoTest.class.getName();
    private AblyRest ably;
    private AblyRest ably_alt;

    @Before
    public void setUpBefore() throws Exception {
        final ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = new AblyRest(opts);
        final ClientOptions opts_alt = createOptions(testVars.keys[0].keyStr);
        opts_alt.useBinaryProtocol = testParams.useBinaryProtocol;
        ably_alt = new AblyRest(opts_alt);
    }

    /**
     * Publish events with data of various datatypes using text protocol
     */
    @Test
    public void crypto_publish() throws AblyException {
        /* first, publish some messages */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel publish0 = ably.channels.get("persisted:crypto_publish_" + testParams.name, channelOpts);

        publish0.publish("publish0", "This is a string message payload");
        publish0.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final PaginatedResult<Message> messages = publish0.history(null);
        assertNotNull(messages);
        assertEquals(messages.items().length, 2);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals(messageContents.get("publish0"), "This is a string message payload");
        assertEquals(new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
    }

    /**
     * Publish events with data of various datatypes using text protocol with a 256-bit key
     */
    @Test
    public void crypto_publish_256() throws NoSuchAlgorithmException, AblyException {
        /* first, publish some messages */
        /* create a key */
        final KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        byte[] key = keygen.generateKey().getEncoded();
        final CipherParams params = Crypto.getDefaultParams(key);

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; this.cipherParams = params; }};
        final Channel publish0 = ably.channels.get("persisted:crypto_publish_256_" + testParams.name, channelOpts);

        publish0.publish("publish0", "This is a string message payload");
        publish0.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final PaginatedResult<Message> messages = publish0.history(null);
        assertNotNull(messages);
        assertEquals(messages.items().length, 2);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals(messageContents.get("publish0"), "This is a string message payload");
        assertEquals(new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
    }

    /**
     * Connect twice to the service, using the default (binary) protocol
     * and the text protocol. Publish an encrypted message on that channel using
     * the default cipher params and verify correct receipt.
     */
    @Test
    public void crypto_publish_alt() throws AblyException {
        /* first, publish some messages */
        final String channelName = "persisted:crypto_publish_alt_" + testParams.name;

        /* create a key */
        final CipherParams params = Crypto.getDefaultParams();

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; cipherParams = params; }};
        final Channel tx_publish = ably.channels.get(channelName, channelOpts);

        tx_publish.publish("publish0", "This is a string message payload");
        tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final Channel rx_publish = ably_alt.channels.get(channelName, channelOpts);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(messages.items().length, 2);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals(messageContents.get("publish0"), "This is a string message payload");
        assertEquals(new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
    }

    /**
     * Connect twice to the service, using different cipher keys.
     * Publish an encrypted message on that channel using
     * the default cipher params and verify that the decrypt failure
     * is noticed as bad recovered plaintext.
     */
    @Test
    public void crypto_publish_key_mismatch() throws AblyException {
        /* first, publish some messages */
        final String channelName = "persisted:crypto_publish_key_mismatch_" + testParams.name;

        /* create a channel */
        final ChannelOptions tx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel tx_publish = ably.channels.get(channelName, tx_channelOpts);

        tx_publish.publish("publish0", "This is a string message payload");
        tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final ChannelOptions rx_channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel rx_publish = ably.channels.get(channelName, rx_channelOpts);

        final PaginatedResult<Message> messages = rx_publish.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "2") });
        for (final Message failedMessage: messages.items())
            assertTrue(failedMessage.encoding.contains("cipher"));
    }

    /**
     * Connect twice to the service, one with and one without encryption.
     * Publish an unencrypted message and verify that the receiving connection
     * does not attempt to decrypt it.
     */
    @Test
    public void crypto_send_unencrypted() throws AblyException {
        final String channelName = "persisted:crypto_send_unencrypted_" + testParams.name;
        /* first, publish some messages */

        /* create a channel */
        final Channel tx_publish = ably.channels.get(channelName);

        tx_publish.publish("publish0", "This is a string message payload");
        tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel rx_publish = ably.channels.get(channelName, channelOpts);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(messages.items().length, 2);
        final HashMap<String, Object> messageContents = new HashMap<String, Object>();

        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message.data);
        assertEquals(messageContents.get("publish0"), "This is a string message payload");
        assertEquals(new String((byte[])messageContents.get("publish1")), "This is a byte[] message payload");
    }

    /**
     * Connect twice to the service, one with and one without encryption.
     * Publish an encrypted message and verify that the receiving connection
     * is unable to decrypt it and leaves it as encoded cipher data
     */
    @Test
    public void crypto_send_encrypted_unhandled() throws AblyException {
        final String channelName = "persisted:crypto_send_encrypted_unhandled_" + testParams.name;

        /* first, publish some messages */

        /* create a channel */
        final ChannelOptions channelOpts = new ChannelOptions() {{ encrypted = true; }};
        final Channel tx_publish = ably.channels.get(channelName, channelOpts);

        tx_publish.publish("publish0", "This is a string message payload");
        tx_publish.publish("publish1", "This is a byte[] message payload".getBytes());

        /* get the history for this channel */
        final Channel rx_publish = ably_alt.channels.get(channelName);
        final PaginatedResult<Message> messages = rx_publish.history(null);
        assertNotNull(messages);
        assertEquals(messages.items().length, 2);
        final HashMap<String, Message> messageContents = new HashMap<String, Message>();
        /* verify message contents */
        for (final Message message : messages.items())
            messageContents.put(message.name, message);
        assertTrue(messageContents.get("publish0").encoding.contains("cipher"));
        assertTrue(messageContents.get("publish1").encoding.contains("cipher"));
    }
}
