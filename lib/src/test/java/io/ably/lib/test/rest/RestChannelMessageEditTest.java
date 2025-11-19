package io.ably.lib.test.rest;

import io.ably.lib.network.EngineType;
import io.ably.lib.network.HttpEngineFactory;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageAction;
import io.ably.lib.types.MessageOperation;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.util.Crypto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for REST channel message edit and delete operations
 */
public class RestChannelMessageEditTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);
    private AblyRest ably;
    private EngineType engineType;

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = new AblyRest(opts);
        engineType = HttpEngineFactory.getFirstAvailable().getEngineType();
    }

    /**
     * Test getMessage: Publish a message and retrieve it by serial
     */
    @Test
    public void getMessage_retrieveBySerial() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:get_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Test message data");

        // Get the message from history to obtain its serial
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Retrieve the message by serial
        Message retrievedMessage = waitForUpdatedMessageAppear(channel, publishedMessage.serial);

        // Verify the retrieved message
        assertNotNull("Expected non-null retrieved message", retrievedMessage);
        assertEquals("Expected same message name", publishedMessage.name, retrievedMessage.name);
        assertEquals("Expected same message data", publishedMessage.data, retrievedMessage.data);
        assertEquals("Expected same serial", publishedMessage.serial, retrievedMessage.serial);
    }

    /**
     * Test updateMessage: Update a message's data
     */
    @Test
    public void updateMessage_updateData() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:update_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Original message data");

        // Get the message from history to obtain its serial
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Update the message
        Message updateMessage = new Message();
        updateMessage.serial = publishedMessage.serial;
        updateMessage.data = "Updated message data";
        updateMessage.name = "updated_event";

        channel.updateMessage(updateMessage);

        // Retrieve the updated message
        Message updatedMessage = waitForUpdatedMessageAppear(channel, publishedMessage.serial);

        // Verify the message was updated
        assertNotNull("Expected non-null updated message", updatedMessage);
        assertEquals("Expected updated message data", "Updated message data", updatedMessage.data);
        assertEquals("Expected updated message name", "updated_event", updatedMessage.name);
        assertEquals("Expected action to be MESSAGE_UPDATE", MessageAction.MESSAGE_UPDATE, updatedMessage.action);
    }

    /**
     * Test updateMessage: Update a message's data
     */
    @Test
    public void updateMessage_updateEncodedData() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:update_encodedmessage_" + UUID.randomUUID() + "_" + testParams.name;
        ChannelOptions channelOptions = ChannelOptions.withCipherKey(Crypto.generateRandomKey());
        Channel channel = ably.channels.get(channelName, channelOptions);

        // Publish a message
        channel.publish("test_event", "Original message data");

        // Get the message from history to obtain its serial
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Update the message
        Message updateMessage = new Message();
        updateMessage.serial = publishedMessage.serial;
        updateMessage.data = "Updated message data";
        updateMessage.name = "updated_event";

        channel.updateMessage(updateMessage);

        // Retrieve the updated message
        Message updatedMessage = waitForUpdatedMessageAppear(channel, publishedMessage.serial);

        // Verify the message was updated
        assertNotNull("Expected non-null updated message", updatedMessage);
        assertEquals("Expected updated message data", "Updated message data", updatedMessage.data);
        assertEquals("Expected updated message name", "updated_event", updatedMessage.name);
        assertEquals("Expected action to be MESSAGE_UPDATE", MessageAction.MESSAGE_UPDATE, updatedMessage.action);
    }

    /**
     * Test updateMessage async: Update a message using async API
     */
    @Test
    public void updateMessage_async() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:update_message_async_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Original message data");

        // Get the message from history
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        final Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Update the message using async API
        Message updateMessage = new Message();
        updateMessage.serial = publishedMessage.serial;
        updateMessage.data = "Updated message data async";

        CompletionSet updateComplete = new CompletionSet();
        channel.updateMessageAsync(updateMessage, updateComplete.add());

        ErrorInfo[] updateErrors = updateComplete.waitFor();
        assertEquals("Expected no errors from update", 0, updateErrors.length);

        // Retrieve the updated message
        Message updatedMessage = waitForUpdatedMessageAppear(channel, publishedMessage.serial);
        assertNotNull("Expected non-null updated message", updatedMessage);
        assertEquals("Expected updated message data", "Updated message data async", updatedMessage.data);
    }

    /**
     * Test deleteMessage: Soft delete a message
     */
    @Test
    public void deleteMessage_softDelete() throws Exception {
        String channelName = "mutable:delete_message_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Message to be deleted");

        // Get the message from history
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Delete the message
        Message deleteMessage = new Message();
        deleteMessage.serial = publishedMessage.serial;
        deleteMessage.data = "Message deleted";

        channel.deleteMessage(deleteMessage);

        // Retrieve the deleted message
        Message deletedMessage = waitForDeletedMessageAppear(channel, publishedMessage.serial);

        // Verify the message was soft deleted
        assertNotNull("Expected non-null deleted message", deletedMessage);
        assertEquals("Expected action to be MESSAGE_DELETE", MessageAction.MESSAGE_DELETE, deletedMessage.action);
    }

    /**
     * Test deleteMessage async: Delete a message using async API
     */
    @Test
    public void deleteMessage_async() throws Exception {
        String channelName = "mutable:delete_message_async_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Message to be deleted async");

        // Get the message from history
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        final Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Delete the message using async API
        Message deleteMessage = new Message();
        deleteMessage.serial = publishedMessage.serial;
        deleteMessage.data = "Message deleted async";

        CompletionSet deleteComplete = new CompletionSet();
        channel.deleteMessageAsync(deleteMessage, deleteComplete.add());

        ErrorInfo[] deleteErrors = deleteComplete.waitFor();
        assertEquals("Expected no errors from delete", 0, deleteErrors.length);

        // Retrieve the deleted message
        Message deletedMessage = waitForDeletedMessageAppear(channel, publishedMessage.serial);
        assertNotNull("Expected non-null deleted message", deletedMessage);
        assertEquals("Expected action to be MESSAGE_DELETE", MessageAction.MESSAGE_DELETE, deletedMessage.action);
    }

    /**
     * Test getMessageVersions: Retrieve version history of a message
     */
    @Test
    public void getMessageVersions_retrieveHistory() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:message_versions_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Original data");

        // Get the message from history
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);

        // Update the message to create version history
        Message updateMessage1 = new Message();
        updateMessage1.serial = publishedMessage.serial;
        updateMessage1.data = "First update";
        channel.updateMessage(updateMessage1);

        Message updateMessage2 = new Message();
        updateMessage2.serial = publishedMessage.serial;
        updateMessage2.data = "Second update";
        MessageOperation messageOperation = new MessageOperation();
        messageOperation.description = "description";
        messageOperation.metadata = new HashMap<>();
        messageOperation.metadata.put("key", "value");
        channel.updateMessage(updateMessage2, messageOperation);

        // Retrieve version history
        PaginatedResult<Message> versions = waitForMessageAppearInVersionHistory(channel, publishedMessage.serial, null, msgs ->
            msgs.length >= 3
        );

        // Verify version history
        assertNotNull("Expected non-null versions", versions);
        assertTrue("Expected at least 3 versions (original + 2 updates)", versions.items().length >= 3);

        Message latestVersion = versions.items()[versions.items().length - 1];
        assertEquals("Expected latest version to have second update data", "Second update", latestVersion.data);
        assertEquals("description", latestVersion.version.description);
        assertEquals("value", latestVersion.version.metadata.get("key"));
    }

    /**
     * Test getMessageVersions async: Retrieve version history using async API
     */
    @Test
    public void getMessageVersions_async() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:message_versions_async_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // Publish a message
        channel.publish("test_event", "Original data");

        // Get the message from history
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        assertNotNull("Expected non-null history", history);
        assertEquals(1, history.items().length);

        final Message publishedMessage = history.items()[0];
        assertNotNull("Expected message to have a serial", publishedMessage.serial);
    }

    /**
     * Test error handling: getMessage with invalid serial
     */
    @Test
    public void getMessage_invalidSerial() {
        String channelName = "mutable:get_message_invalid_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            channel.getMessage("invalid_serial_12345");
        });

        assertNotNull("Expected error info", exception.errorInfo);
    }

    /**
     * Test error handling: updateMessage with null serial
     */
    @Test
    public void updateMessage_nullSerial() {
        String channelName = "mutable:update_message_null_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            Message updateMessage = new Message();
            updateMessage.serial = null;
            updateMessage.data = "Update data";

            channel.updateMessage(updateMessage);
        });

        assertNotNull("Expected error info", exception.errorInfo);
        assertTrue("Expected error message about serial",
            exception.errorInfo.message.toLowerCase().contains("serial"));
    }

    /**
     * Test error handling: deleteMessage with empty serial
     */
    @Test
    public void deleteMessage_emptySerial() {
        String channelName = "mutable:delete_message_empty_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        AblyException exception = assertThrows(AblyException.class, () -> {
            Message deleteMessage = new Message();
            deleteMessage.serial = "";
            deleteMessage.data = "Delete data";

            channel.deleteMessage(deleteMessage);
        });

        assertNotNull("Expected error info", exception.errorInfo);
        assertTrue("Expected error message about serial",
            exception.errorInfo.message.toLowerCase().contains("serial"));
    }

    /**
     * Test complete workflow: publish, update, get versions, delete
     */
    @Test
    public void completeWorkflow_publishUpdateVersionsDelete() throws Exception {
        if (engineType == EngineType.DEFAULT) return;

        String channelName = "mutable:complete_workflow_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // 1. Publish a message
        channel.publish("workflow_event", "Initial data");

        // Get the published message
        PaginatedResult<Message> history = waitForMessageAppearInHistory(channel);
        Message publishedMessage = history.items()[0];
        String serial = publishedMessage.serial;

        // 2. Update the message
        Message updateMessage = new Message();
        updateMessage.serial = serial;
        updateMessage.data = "Updated data";
        updateMessage.name = "workflow_event_updated";
        channel.updateMessage(updateMessage);

        // 3. Verify update
        Message retrieved = waitForUpdatedMessageAppear(channel, serial);
        assertEquals("Expected updated data", "Updated data", retrieved.data);
        assertEquals("Expected MESSAGE_UPDATE action", MessageAction.MESSAGE_UPDATE, retrieved.action);

        // 4. Delete the message
        Message deleteMessage = new Message();
        deleteMessage.serial = serial;
        deleteMessage.data = "Deleted";
        channel.deleteMessage(deleteMessage);

        // 5. Verify deletion
        Message deleted = waitForDeletedMessageAppear(channel, serial);
        assertEquals("Expected MESSAGE_DELETE action", MessageAction.MESSAGE_DELETE, deleted.action);

        // 6. Verify delete appears in versions
        PaginatedResult<Message> finalVersions = waitForMessageAppearInVersionHistory(channel, serial, null, msgs ->
            msgs.length > 0 && msgs[msgs.length - 1].action == MessageAction.MESSAGE_DELETE
        );
        assertTrue("Expected at least 3 versions (create, update, delete)", finalVersions.items().length >= 3);
    }

    private PaginatedResult<Message> waitForMessageAppearInVersionHistory(Channel channel, String serial, Param[] params, Predicate<Message[]> predicate) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            PaginatedResult<Message> history = channel.getMessageVersions(serial, params);
            if (history.items().length > 0 && predicate.test(history.items()) || System.currentTimeMillis() > timeout)
                return history;
            Thread.sleep(200);
        }
    }

    private PaginatedResult<Message> waitForMessageAppearInHistory(Channel channel) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            PaginatedResult<Message> history = channel.history(null);
            if (history.items().length > 0 || System.currentTimeMillis() > timeout) return history;
            Thread.sleep(200);
        }
    }

    private Message waitForUpdatedMessageAppear(Channel channel, String serial) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            Message message = channel.getMessage(serial);
            if ((message != null && message.action == MessageAction.MESSAGE_UPDATE) || System.currentTimeMillis() > timeout)
                return message;
            Thread.sleep(200);
        }
    }

    private Message waitForDeletedMessageAppear(Channel channel, String serial) throws Exception {
        long timeout = System.currentTimeMillis() + 5_000;
        while (true) {
            Message message = channel.getMessage(serial);
            if ((message != null && message.action == MessageAction.MESSAGE_DELETE) || System.currentTimeMillis() > timeout)
                return message;
            Thread.sleep(200);
        }
    }
}
