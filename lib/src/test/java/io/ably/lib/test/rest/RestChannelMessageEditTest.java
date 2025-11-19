package io.ably.lib.test.rest;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers.AsyncWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageAction;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for REST channel message edit and delete operations
 */
public class RestChannelMessageEditTest extends ParameterizedTest {

    private AblyRest ably;

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Before
    public void setUpBefore() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        ably = new AblyRest(opts);
    }

    /**
     * Test getMessage: Publish a message and retrieve it by serial
     */
    @Test
    public void getMessage_retrieveBySerial() {
        String channelName = "persisted:get_message_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Test message data");

            // Get the message from history to obtain its serial
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Retrieve the message by serial
            Message retrievedMessage = channel.getMessage(publishedMessage.serial);

            // Verify the retrieved message
            assertNotNull("Expected non-null retrieved message", retrievedMessage);
            assertEquals("Expected same message name", publishedMessage.name, retrievedMessage.name);
            assertEquals("Expected same message data", publishedMessage.data, retrievedMessage.data);
            assertEquals("Expected same serial", publishedMessage.serial, retrievedMessage.serial);

        } catch (AblyException e) {
            fail("getMessage_retrieveBySerial: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test getMessage async: Retrieve a message by serial using async API
     */
    @Test
    public void getMessage_async() {
        String channelName = "persisted:get_message_async_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Test message data async");

            // Get the message from history to obtain its serial
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            final Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Retrieve the message by serial using async API
            AsyncWaiter<Message> waiter = new AsyncWaiter<>();
            channel.getMessageAsync(publishedMessage.serial, waiter);

            waiter.waitFor();
            assertNull("Expected no error", waiter.error);

            Message retrievedMessage = waiter.result;
            assertNotNull("Expected non-null retrieved message", retrievedMessage);
            assertEquals("Expected same message name", publishedMessage.name, retrievedMessage.name);
            assertEquals("Expected same message data", publishedMessage.data, retrievedMessage.data);

        } catch (AblyException e) {
            fail("getMessage_async: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test updateMessage: Update a message's data
     */
    @Test
    public void updateMessage_updateData() {
        String channelName = "persisted:update_message_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Original message data");

            // Get the message from history to obtain its serial
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Update the message
            Message updateMessage = new Message();
            updateMessage.data = "Updated message data";
            updateMessage.name = "updated_event";

            channel.updateMessage(publishedMessage.serial, updateMessage);

            // Retrieve the updated message
            Message updatedMessage = channel.getMessage(publishedMessage.serial);

            // Verify the message was updated
            assertNotNull("Expected non-null updated message", updatedMessage);
            assertEquals("Expected updated message data", "Updated message data", updatedMessage.data);
            assertEquals("Expected updated message name", "updated_event", updatedMessage.name);
            assertEquals("Expected action to be MESSAGE_UPDATE", MessageAction.MESSAGE_UPDATE, updatedMessage.action);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("updateMessage_updateData: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test updateMessage async: Update a message using async API
     */
    @Test
    public void updateMessage_async() {
        String channelName = "persisted:update_message_async_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Original message data");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            final Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Update the message using async API
            Message updateMessage = new Message();
            updateMessage.data = "Updated message data async";

            CompletionSet updateComplete = new CompletionSet();
            channel.updateMessageAsync(publishedMessage.serial, updateMessage, updateComplete.add());

            ErrorInfo[] updateErrors = updateComplete.waitFor();
            assertNull("Expected no errors from update", updateErrors);

            // Retrieve the updated message
            Message updatedMessage = channel.getMessage(publishedMessage.serial);
            assertNotNull("Expected non-null updated message", updatedMessage);
            assertEquals("Expected updated message data", "Updated message data async", updatedMessage.data);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("updateMessage_async: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test deleteMessage: Soft delete a message
     */
    @Test
    public void deleteMessage_softDelete() {
        String channelName = "persisted:delete_message_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Message to be deleted");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Delete the message
            Message deleteMessage = new Message();
            deleteMessage.data = "Message deleted";

            channel.deleteMessage(publishedMessage.serial, deleteMessage);

            // Retrieve the deleted message
            Message deletedMessage = channel.getMessage(publishedMessage.serial);

            // Verify the message was soft deleted
            assertNotNull("Expected non-null deleted message", deletedMessage);
            assertEquals("Expected action to be MESSAGE_DELETE", MessageAction.MESSAGE_DELETE, deletedMessage.action);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("deleteMessage_softDelete: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test deleteMessage async: Delete a message using async API
     */
    @Test
    public void deleteMessage_async() {
        String channelName = "persisted:delete_message_async_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Message to be deleted async");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            final Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Delete the message using async API
            Message deleteMessage = new Message();
            deleteMessage.data = "Message deleted async";

            CompletionSet deleteComplete = new CompletionSet();
            channel.deleteMessageAsync(publishedMessage.serial, deleteMessage, deleteComplete.add());

            ErrorInfo[] deleteErrors = deleteComplete.waitFor();
            assertNull("Expected no errors from delete", deleteErrors);

            // Retrieve the deleted message
            Message deletedMessage = channel.getMessage(publishedMessage.serial);
            assertNotNull("Expected non-null deleted message", deletedMessage);
            assertEquals("Expected action to be MESSAGE_DELETE", MessageAction.MESSAGE_DELETE, deletedMessage.action);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("deleteMessage_async: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test getMessageVersions: Retrieve version history of a message
     */
    @Test
    public void getMessageVersions_retrieveHistory() {
        String channelName = "persisted:message_versions_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Original data");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Update the message to create version history
            Message updateMessage1 = new Message();
            updateMessage1.data = "First update";
            channel.updateMessage(publishedMessage.serial, updateMessage1);

            Message updateMessage2 = new Message();
            updateMessage2.data = "Second update";
            channel.updateMessage(publishedMessage.serial, updateMessage2);

            // Retrieve version history
            PaginatedResult<Message> versions = channel.getMessageVersions(publishedMessage.serial);

            // Verify version history
            assertNotNull("Expected non-null versions", versions);
            assertTrue("Expected at least 3 versions (original + 2 updates)", versions.items().length >= 3);

            // Versions should be in reverse chronological order (newest first)
            Message latestVersion = versions.items()[0];
            assertEquals("Expected latest version to have second update data", "Second update", latestVersion.data);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("getMessageVersions_retrieveHistory: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test getMessageVersions async: Retrieve version history using async API
     */
    @Test
    public void getMessageVersions_async() {
        String channelName = "persisted:message_versions_async_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Original data");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            final Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Update the message
            Message updateMessage = new Message();
            updateMessage.data = "Updated data";
            channel.updateMessage(publishedMessage.serial, updateMessage);

            // Retrieve version history using async API
            AsyncWaiter<AsyncPaginatedResult<Message>> waiter = new AsyncWaiter<>();
            channel.getMessageVersionsAsync(publishedMessage.serial, waiter);

            waiter.waitFor();
            assertNull("Expected no error", waiter.error);

            AsyncPaginatedResult<Message> versions = waiter.result;
            assertNotNull("Expected non-null versions", versions);
            assertTrue("Expected at least 2 versions", versions.items().length >= 2);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("getMessageVersions_async: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test getMessageVersions with pagination parameters
     */
    @Test
    public void getMessageVersions_withParams() {
        String channelName = "persisted:message_versions_params_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Publish a message
            channel.publish("test_event", "Original data");

            // Get the message from history
            PaginatedResult<Message> history = channel.history(null);
            assertNotNull("Expected non-null history", history);
            assertTrue("Expected at least 1 message", history.items().length > 0);

            Message publishedMessage = history.items()[0];
            assertNotNull("Expected message to have a serial", publishedMessage.serial);

            // Create multiple versions
            for (int i = 1; i <= 5; i++) {
                Message updateMessage = new Message();
                updateMessage.data = "Update " + i;
                channel.updateMessage(publishedMessage.serial, updateMessage);
            }

            // Retrieve version history with limit
            Param[] params = new Param[]{new Param("limit", "3")};
            PaginatedResult<Message> versions = channel.getMessageVersions(publishedMessage.serial, params);

            // Verify pagination
            assertNotNull("Expected non-null versions", versions);
            assertTrue("Expected limited number of versions", versions.items().length <= 3);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("getMessageVersions_withParams: Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Test error handling: getMessage with invalid serial
     */
    @Test
    public void getMessage_invalidSerial() {
        String channelName = "persisted:get_message_invalid_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            // Try to retrieve a message with an invalid serial
            Message message = channel.getMessage("invalid_serial_12345");
            fail("Expected AblyException for invalid serial");
        } catch (AblyException e) {
            // Expected exception
            assertNotNull("Expected error info", e.errorInfo);
        }
    }

    /**
     * Test error handling: updateMessage with null serial
     */
    @Test
    public void updateMessage_nullSerial() {
        String channelName = "persisted:update_message_null_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            Message updateMessage = new Message();
            updateMessage.data = "Update data";

            channel.updateMessage(null, updateMessage);
            fail("Expected AblyException for null serial");
        } catch (AblyException e) {
            // Expected exception
            assertNotNull("Expected error info", e.errorInfo);
            assertTrue("Expected error message about serial",
                e.errorInfo.message.toLowerCase().contains("serial"));
        }
    }

    /**
     * Test error handling: deleteMessage with empty serial
     */
    @Test
    public void deleteMessage_emptySerial() {
        String channelName = "persisted:delete_message_empty_" + UUID.randomUUID().toString() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        try {
            Message deleteMessage = new Message();
            deleteMessage.data = "Delete data";

            channel.deleteMessage("", deleteMessage);
            fail("Expected AblyException for empty serial");
        } catch (AblyException e) {
            // Expected exception
            assertNotNull("Expected error info", e.errorInfo);
            assertTrue("Expected error message about serial",
                e.errorInfo.message.toLowerCase().contains("serial"));
        }
    }

    /**
     * Test complete workflow: publish, update, get versions, delete
     */
    @Test
    public void completeWorkflow_publishUpdateVersionsDelete() throws Exception {
        String channelName = "persisted:complete_workflow_" + UUID.randomUUID() + "_" + testParams.name;
        Channel channel = ably.channels.get(channelName);

        // 1. Publish a message
        channel.publish("workflow_event", "Initial data");

        // Get the published message
        PaginatedResult<Message> history = channel.history(null);
        Message publishedMessage = history.items()[0];
        String serial = publishedMessage.serial;

        // 2. Update the message
        Message updateMessage = new Message();
        updateMessage.data = "Updated data";
        updateMessage.name = "workflow_event_updated";
        channel.updateMessage(serial, updateMessage);

        // 3. Verify update
        Message retrieved = channel.getMessage(serial);
        assertEquals("Expected updated data", "Updated data", retrieved.data);
        assertEquals("Expected MESSAGE_UPDATE action", MessageAction.MESSAGE_UPDATE, retrieved.action);

        // 4. Check versions
        PaginatedResult<Message> versions = channel.getMessageVersions(serial);
        assertTrue("Expected at least 2 versions", versions.items().length >= 2);

        // 5. Delete the message
        Message deleteMessage = new Message();
        deleteMessage.data = "Deleted";
        channel.deleteMessage(serial, deleteMessage);

        // 6. Verify deletion
        Message deleted = channel.getMessage(serial);
        assertEquals("Expected MESSAGE_DELETE action", MessageAction.MESSAGE_DELETE, deleted.action);

        // 7. Verify delete appears in versions
        PaginatedResult<Message> finalVersions = channel.getMessageVersions(serial);
        assertTrue("Expected at least 3 versions (create, update, delete)", finalVersions.items().length >= 3);
    }
}
