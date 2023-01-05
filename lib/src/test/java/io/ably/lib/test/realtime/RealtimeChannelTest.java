package io.ably.lib.test.realtime;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.Channel.MessageListener;
import io.ably.lib.realtime.ChannelEvent;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelMode;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeChannelTest extends ParameterizedTest {

    private Comparator<Message> messageComparator = new Comparator<Message>() {
        @Override
        public int compare(Message o1, Message o2) {
            int result = o1.name.compareTo(o2.name);
            return (result == 0)?(((String) o1.data).compareTo((String) o2.data)):(result);
        }
    };


    /**
     * Connect to the service and attach to a channel,
     * confirming that the attached state is reached.
     */
    @Test
    public void attach() {
        String channelName = "attach_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach before the connected state is reached.
     */
    @Test
    public void attach_before_connect() {
        String channelName = "attach_before_connect_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach, then detach
     */
    @Test
    public void attach_detach() {
        String channelName = "attach_detach_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.detached);
            assertEquals("Verify detached state reached", channel.state, ChannelState.detached);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void attach_with_channel_params_channels_get() {
        String channelName = "attach_with_channel_params_channels_get_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<String, String>();
            options.params.put("modes", "subscribe");
            options.params.put("delta", "vcdiff");

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", channel.getParams(), options.params);
            assertArrayEquals("Verify channel modes", new ChannelMode[] { ChannelMode.subscribe }, channel.getModes());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void attach_with_channel_params_set_options() {
        String channelName = "attach_with_channel_params_set_options_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params.put("modes", "subscribe");
            options.params.put("delta", "vcdiff");

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.setOptions(options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", channel.getParams(), options.params);
            assertArrayEquals("Verify channel modes", new ChannelMode[] { ChannelMode.subscribe }, channel.getModes());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void channels_get_should_throw_when_would_cause_reattach() {
        String channelName = "channels_get_should_throw_when_would_cause_reattach_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params.put("modes", "subscribe");
            options.params.put("delta", "vcdiff");

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);

            try {
                ably.channels.get(channelName, options);
            } catch (AblyException e) {
                assertEquals("Verify error code", 400, e.errorInfo.code);
                assertEquals("Verify error status code", 40000, e.errorInfo.statusCode);
                assertTrue("Verify error message", e.errorInfo.message.contains("setOptions"));
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void attach_with_channel_params_modes_and_channel_modes() {
        String channelName = "attach_with_channel_params_modes_and_channel_modes_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<String, String>();
            options.params.put("modes", "presence,subscribe");
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.presence_subscribe
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel params", channel.getParams(), options.params);
            assertArrayEquals("Verify channel modes", new ChannelMode[] { ChannelMode.subscribe, ChannelMode.presence }, channel.getModes());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void attach_with_channel_modes() {
        String channelName = "attach_with_channel_modes_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.presence_subscribe,
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            assertEquals("Verify channel modes", channel.getModes(), options.modes);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /*@Test*/
    public void attach_with_params_delta_and_channel_modes() {
        String channelName = "attach_with_params_delta_and_channel_modes_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ConnectionState.connected, ably.connection.state);

            ChannelOptions options = new ChannelOptions();
            options.params = new HashMap<String, String>();
            options.params.put("delta", "vcdiff");
            options.modes = new ChannelMode[] {
                ChannelMode.publish,
                ChannelMode.subscribe,
                ChannelMode.presence_subscribe,
            };

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName, options);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", ChannelState.attached, channel.state);
            options.params.put("modes", "publish,subscribe,presence_subscribe");
            assertEquals("Verify channel params", channel.getParams(), options.params);
            assertEquals("Verify channel modes", channel.getModes(), options.modes);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service and attach, then subscribe and unsubscribe
     */
    @Test
    public void subscribe_unsubscribe() {
        String channelName = "subscribe_unsubscribe_" + testParams.name;
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageListener testListener = new MessageListener() {
                @Override
                public void onMessage(Message message) {
                }};
            channel.subscribe("test_event", testListener);
            /* unsubscribe */
            channel.unsubscribe("test_event", testListener);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Verifies that unsubscribe call with no argument removes all listeners,
     * and any of the previously subscribed listeners doesn't receive any message
     * after that.
     * </p>
     * <p>
     * Spec: RTL8a
     * </p>
     */
    @Test
    public void unsubscribe_all() throws AblyException {
        /* Ably instance that will emit messages */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive messages */
        AblyRealtime ably2 = null;

        String channelName = "test.channel.unsubscribe.all" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem"),
                new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Channel#subscribe()} */
            channel2.subscribe(listener);
            channel2.subscribe(messages[0].name, listener);
            channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

            /* Unsubscribe */
            channel2.unsubscribe();

            /* Start emitting channel with ably client 1 (emitter) */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel1.publish(messages, waiter);
            waiter.waitFor();

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates channel removes a subscriber,
     * when {@code Channel#unsubscribe()} gets called with a listener argument.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void unsubscribe_single() throws AblyException {
        /* Ably instance that will emit messages */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive messages */
        AblyRealtime ably2 = null;

        String channelName = "test.channel.unsubscribe.single" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem"),
                new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            /* Subscribe using various alternatives of {@code Channel#subscribe()} */
            channel2.subscribe(listener);
            channel2.subscribe(messages[0].name, listener);
            channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

            /* Unsubscribe */
            channel2.unsubscribe(listener);

            /* Start emitting channel with ably client 1 (emitter) */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel1.publish(messages, waiter);
            waiter.waitFor();

            /* Validate that we didn't received anything
             */
            assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to all messages.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_all() throws AblyException {
        /* Ably instance that will emit channel messages */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive channel messages */
        AblyRealtime ably2 = null;

        String channelName = "test.channel.subscribe.all" + System.currentTimeMillis();
        Message[] messages = new Message[]{
                new Message("name1", "Lorem ipsum dolor sit amet,"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem.")
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collects received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);

            channel2.subscribe(listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish(messages, null);

            /* Wait until receiver client (ably2) observes {@code }
             * is emitted from emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length);

            /* Validate that,
             *  - we received every message that has been published
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to multiple messages.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_multiple() throws AblyException {
        /* Ably instance that will emit channel messages */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive channel messages */
        AblyRealtime ably2 = null;

        String channelName = "test.channel.subscribe.multiple" + System.currentTimeMillis();
        Message[] messages = new Message[] {
                new Message("name1", "Lorem ipsum dolor sit amet,"),
                new Message("name2", "Consectetur adipiscing elit."),
                new Message("name3", "Pellentesque nulla lorem.")
        };

        String[] messageNames = new String[] {
                messages[0].name,
                messages[1].name,
                messages[2].name
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            /* Create a listener that collect received messages */
            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);
            channel2.subscribe(messageNames, listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);
            channel1.publish(messages, null);
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);

            /* Wait until receiver client (ably2) observes {@code Message}
             * on subscribed channel (channel2) emitted by emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

            /* Validate that,
             *  - we received specific messages
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }

    /**
     * <p>
     * Validates a client can observe channel messages of other client,
     * when they entered to the same channel and observing client subscribed
     * to a single message.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void subscribe_single() throws AblyException {
        /* Ably instance that will emit channel messages */
        AblyRealtime ably1 = null;
        /* Ably instance that will receive channel messages */
        AblyRealtime ably2 = null;

        String channelName = "test.channel.subscribe.single" + System.currentTimeMillis();
        String messageName = "name";
        Message[] messages = new Message[] {
                new Message(messageName, "Lorem ipsum dolor sit amet,"),
                new Message(messageName, "Consectetur adipiscing elit."),
                new Message(messageName, "Pellentesque nulla lorem.")
        };

        try {
            ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
            option1.clientId = "emitter client";
            ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
            option2.clientId = "receiver client";

            ably1 = new AblyRealtime(option1);
            ably2 = new AblyRealtime(option2);

            Channel channel1 = ably1.channels.get(channelName);
            channel1.attach();
            new ChannelWaiter(channel1).waitFor(ChannelState.attached);

            Channel channel2 = ably2.channels.get(channelName);
            channel2.attach();
            new ChannelWaiter(channel2).waitFor(ChannelState.attached);

            ArrayList<Message> receivedMessageStack = new ArrayList<>();
            MessageListener listener = new MessageListener() {
                List<Message> messageStack;

                @Override
                public void onMessage(Message message) {
                    messageStack.add(message);
                }

                public MessageListener setMessageStack(List<Message> messageStack) {
                    this.messageStack = messageStack;
                    return this;
                }
            }.setMessageStack(receivedMessageStack);
            channel2.subscribe(messageName, listener);

            /* Start emitting channel with ably client 1 (emitter) */
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);
            channel1.publish(messages, null);
            channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);

            /* Wait until receiver client (ably2) observes {@code Message}
             * on subscribed channel (channel2) emitted by emitter client (ably1)
             */
            new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

            /* Validate that,
             *  - received same amount of emitted specific message
             *  - received messages are the ones we emitted
             */
            assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

            Collections.sort(receivedMessageStack, messageComparator);
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
                    fail("Unable to find expected message: " + message);
                }
            }
        } finally {
            if (ably1 != null) ably1.close();
            if (ably2 != null) ably2.close();
        }
    }


    /**
     * Connect to the service using the default (binary) protocol
     * and attempt to attach to a channel with credentials that do
     * not have access, confirming that the failed state is reached.
     */
    @Test
    public void attach_fail() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_fail");
            channel.attach();
            ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client attaches to a channel successfully, verify
     * attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void attach_success_callback() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_success");
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify failed state reached", channel.state, ChannelState.attached);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client failed to attach to a channel, verify
     * attach {@code CompletionListener#onError(ErrorInfo)}
     * gets called.
     */
    @Test
    public void attach_fail_callback() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("attach_fail");
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

            /* Verify error callback gets called with correct status code */
            waiter.waitFor();
            assertThat(waiter.error.statusCode, is(equalTo(401)));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after initialized state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_initialized() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            assertEquals("Verify failed state reached", channel.state, ChannelState.initialized);

            /* detach */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after attached state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_attached() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after detaching state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_detaching() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            assertEquals("Verify detaching state reached", channel.state, ChannelState.detaching);
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }


    /**
     * When client attaches to a channel in detaching state, verify that attach call will be done after detach
     * response is received
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    // Spec: RTL4h
    // https://github.com/ably/ably-java/issues/885
    @Test
    public void attach_when_channel_in_detaching_state() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.logLevel = Log.VERBOSE;
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final String channelName = "attach_channel";
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            assertEquals("Verify detaching state reached", channel.state, ChannelState.detaching);
            final Helpers.CompletionWaiter attachCompletionWaiter = new Helpers.CompletionWaiter();
            channel.attach();

            final Helpers.CompletionWaiter detachCompletionWaiter = new Helpers.CompletionWaiter();
            channel.detach(detachCompletionWaiter);

            /* Verify onSuccess callback gets called */
            detachCompletionWaiter.waitFor();
            assertThat(detachCompletionWaiter.success, is(true));
            //verify reattach - after detach
            attachCompletionWaiter.waitFor();
            assertThat(attachCompletionWaiter.success,is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * When client detaches from a channel successfully after detached state,
     * verify attach {@code CompletionListener#onSuccess()} gets called.
     */
    @Test
    public void detach_success_callback_detached() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("detach_success");
            channel.attach();
            new ChannelWaiter(channel).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* detach */
            channel.detach();
            new ChannelWaiter(channel).waitFor(ChannelState.detached);

            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.detach(waiter);

            /* Verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * <p>
     * Validate publish will succeed without triggering an attach when connected
     * if not already attached
     * </p>
     * <p>
     * Spec: RTL6c1
     * </p>
     *
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void transient_publish_connected() throws AblyException {
        AblyRealtime pubAbly = null, subAbly = null;
        String channelName = "transient_publish_connected_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            subAbly = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(subAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", subAbly.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel subChannel = subAbly.channels.get(channelName);
            Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(subChannel);
            new ChannelWaiter(subChannel).waitFor(ChannelState.attached);

            pubAbly = new AblyRealtime(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.connected);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            final Channel pubChannel = pubAbly.channels.get(channelName);
            pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            ErrorInfo errorInfo = completionWaiter.waitFor();
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            messageWaiter.waitFor(1);
            assertEquals("Verify expected message received", messageWaiter.receivedMessages.get(0).name, "Lorem");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
            if(subAbly != null) {
                subAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will succeed without triggering an attach when connecting
     * if not already attached
     * </p>
     * <p>
     * Spec: RTL6c2
     * </p>
     *
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void transient_publish_connecting() throws AblyException {
        AblyRealtime pubAbly = null, subAbly = null;
        String channelName = "transient_publish_connecting_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            subAbly = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(subAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", subAbly.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel subChannel = subAbly.channels.get(channelName);
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(subChannel);
            Helpers.MessageWaiter messageWaiter = new Helpers.MessageWaiter(subChannel);
            channelWaiter.waitFor(ChannelState.attached);

            pubAbly = new AblyRealtime(opts);
            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            ErrorInfo errorInfo = completionWaiter.waitFor();
            assertEquals("Verify channel remains in initialized state", pubChannel.state, ChannelState.initialized);

            messageWaiter.waitFor(1);
            assertEquals("Verify expected message received", messageWaiter.receivedMessages.get(0).name, "Lorem");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
            if(subAbly != null) {
                subAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will fail when connection is failed
     * </p>
     * <p>
     * Spec: RTL6c4
     * </p>
     *
     */
    @Test
    public void transient_publish_connection_failed() {
        AblyRealtime pubAbly = null;
        String channelName = "transient_publish_connection_failed_" + testParams.name;
        try {
            ClientOptions opts = createOptions("not:a.key");
            pubAbly = new AblyRealtime(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.failed);
            assertEquals("Verify failed state reached", pubAbly.connection.state, ConnectionState.failed);

            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            try {
                pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
                fail("failed to raise expected exception");
            } catch(AblyException e) {
            }
        } catch(AblyException e) {
            fail("unexpected exception");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate publish will fail when channel is failed
     * </p>
     * <p>
     * Spec: RTL6c4
     * </p>
     *
     */
    @Test
    public void transient_publish_channel_failed() {
        AblyRealtime pubAbly = null;
        String channelName = "transient_publish_channel_failed_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            pubAbly = new AblyRealtime(opts);
            new ConnectionWaiter(pubAbly.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", pubAbly.connection.state, ConnectionState.connected);

            final Channel pubChannel = pubAbly.channels.get(channelName);
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(pubChannel);
            pubChannel.attach();
            channelWaiter.waitFor(ChannelState.failed);

            Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
            try {
                pubChannel.publish("Lorem", "Ipsum!", completionWaiter);
                fail("failed to raise expected exception");
            } catch(AblyException e) {
                assertEquals(pubChannel.state, ChannelState.failed);
            }
        } catch(AblyException e) {
            fail("unexpected exception");
        } finally {
            if(pubAbly != null) {
                pubAbly.close();
            }
        }
    }

    /**
     * <p>
     * Validate subscribe will result in an error, when the channel moves
     * to the FAILED state before the operation succeeds
     * </p>
     * <p>
     * Spec: RTL7c
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void attach_implicit_subscribe_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[1].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and subscribe */
            final Channel channel = ably.channels.get("subscribe_fail");
            channel.subscribe(null);
            assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

            ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void ensure_detach_with_error_does_not_move_to_failed() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final Channel channel = ably.channels.get("test");
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            ProtocolMessage protoMessage = new ProtocolMessage(ProtocolMessage.Action.detach, "test");
            protoMessage.error = new ErrorInfo("test error", 123);

            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.onMessage(null, protoMessage);

            /* Because of (RTL13) channel should now be in either attaching or attached state */
            assertNotEquals("channel state shouldn't be failed", channel.state, ChannelState.failed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void channel_state_on_connection_suspended() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            final String channelName = "test_state_channel";
            final Channel channel = ably.channels.get(channelName);
            channel.attach();

            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channelWaiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* switch to suspended state */
            ably.connection.connectionManager.requestState(ConnectionState.suspended);

            channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify suspended state reached", channel.state, ChannelState.suspended);

            /* switch to connected state */
            ably.connection.connectionManager.requestState(ConnectionState.connected);

            channelWaiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* switch to failed state */
            ably.connection.connectionManager.requestState(ConnectionState.failed);

            channelWaiter.waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Establish connection, attach channel, simulate sending attached and detached messages
     * from the server, test correct behaviour
     *
     * Tests RTL12, RTL13a
     */
    @Test
    public void channel_server_initiated_attached_detached() throws AblyException {
        AblyRealtime ably = null;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        final String channelName = "channel_server_initiated_attach_detach";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            /* Make test faster */
            Defaults.realtimeRequestTimeout = 1000;
            opts.channelRetryTimeout = 1000;

            ably = new AblyRealtime(opts);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);

            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            final int[] updateEventsEmitted = new int[]{0};
            final boolean[] resumedFlag = new boolean[]{true};
            channel.on(ChannelEvent.update, new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    updateEventsEmitted[0]++;
                    resumedFlag[0] = stateChange.resumed;
                }
            });

            /* Inject attached message as if received from the server */
            ProtocolMessage attachedMessage = new ProtocolMessage() {{
                action = Action.attached;
                channel = channelName;
                flags |= Flag.resumed.getMask();
            }};
            ably.connection.connectionManager.onMessage(null, attachedMessage);

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* Channel should transition to attaching, then to attached */
            channelWaiter.waitFor(ChannelState.attaching);
            channelWaiter.waitFor(ChannelState.attached);

            /* Verify received UPDATE message on channel */
            assertEquals("Verify exactly one UPDATE event was emitted on the channel", updateEventsEmitted[0], 1);
            assertTrue("Verify resumed flag set in UPDATE event", resumedFlag[0]);
        } finally {
            if (ably != null)
                ably.close();
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Establish connection, attach channel, disconnection and failed resume
     * verify that subsequent attaches are performed, and give rise to update events
     *
     * Tests RTN15c3
     */
    @Test
    public void channel_resume_lost_continuity() throws AblyException {
        AblyRealtime ably = null;
        final String attachedChannelName = "channel_resume_lost_continuity_attached";
        final String suspendedChannelName = "channel_resume_lost_continuity_suspended";

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* prepare channels */
            Channel attachedChannel = ably.channels.get(attachedChannelName);
            ChannelWaiter attachedChannelWaiter = new ChannelWaiter(attachedChannel);
            attachedChannel.attach();
            attachedChannelWaiter.waitFor(ChannelState.attached);

            Channel suspendedChannel = ably.channels.get(suspendedChannelName);
            suspendedChannel.state = ChannelState.suspended;
            ChannelWaiter suspendedChannelWaiter = new ChannelWaiter(suspendedChannel);

            final boolean[] suspendedStateReached = new boolean[2];
            final boolean[] attachingStateReached = new boolean[2];
            final boolean[] attachedStateReached = new boolean[2];
            final boolean[] resumedFlag = new boolean[]{true, true};
            attachedChannel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    switch(stateChange.current) {
                        case suspended:
                            suspendedStateReached[0] = true;
                            break;
                        case attaching:
                            attachingStateReached[0] = true;
                            break;
                        case attached:
                            attachedStateReached[0] = true;
                            resumedFlag[0] = stateChange.resumed;
                            break;
                        default:
                            break;
                    }
                }
            });
            suspendedChannel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    switch(stateChange.current) {
                        case attaching:
                            attachingStateReached[1] = true;
                            break;
                        case attached:
                            attachedStateReached[1] = true;
                            resumedFlag[1] = stateChange.resumed;
                            break;
                        default:
                            break;
                    }
                }
            });

            /* disconnect, and sabotage the resume */
            String originalConnectionId = ably.connection.id;
            ably.connection.key = "_____!ably___test_fake-key____";
            ably.connection.id = "ably___tes";
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            /* suppress automatic retries by the connection manager */
            try {
                Method method = ably.connection.connectionManager.getClass().getDeclaredMethod("disconnectAndSuppressRetries");
                method.setAccessible(true);
                method.invoke(ably.connection.connectionManager);
            } catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e) {
                fail("Unexpected exception in suppressing retries");
            }

            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

            /* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException e) {}

            /* wait for connection to be reestablished */
            System.out.println("channel_resume_lost_continuity: initiating reconnection (resume)");
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);

            /* verify a new connection was assigned */
            assertNotEquals("A new connection was created", originalConnectionId, ably.connection.id);

            /* previously suspended channel should transition to attaching, then to attached */
            suspendedChannelWaiter.waitFor(ChannelState.attached);

            /* previously attached channel should remain attached */
            attachedChannelWaiter.waitFor(ChannelState.attached);

            /*
             * Verify each channel undergoes relevant events:
             * - previously attached channel does attaching, attached, without visiting suspended;
             * - previously suspended channel does attaching, attached
             */
            assertEquals("Verify channel was not suspended", suspendedStateReached[0], false);
            assertEquals("Verify channel was attaching", attachingStateReached[0], true);
            assertEquals("Verify channel was attached", attachedStateReached[0], true);
            assertFalse("Verify resumed flag set false in ATTACHED event", resumedFlag[0]);

            assertEquals("Verify channel was attaching", attachingStateReached[1], true);
            assertEquals("Verify channel was attached", attachedStateReached[1], true);
            assertFalse("Verify resumed flag set false in ATTACHED event", resumedFlag[1]);
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /*
     * Initiate connection, block send on transport to simulate network packet loss, try to attach, wait for
     * channel to eventually attach when send is re-enabled on transport.
     *
     * Then suspend connection, resume it and immediately block sending packets failing channel
     * reattach. Verify that the channel goes back to suspended state on timeout with correct error code.
     *
     * Try to detach channel while blocking send, channel should go back to attached state through detaching
     *
     * Tests features RTL4c, RTL4f, RTL5f, RTL5e
     */
    @Test
    public void channel_attach_retry_failed() {
        AblyRealtime ably = null;
        String channelName = "channel_attach_retry_failed_" + testParams.name;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 1000;

            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* Block send() and attach */
            mockTransport.blockSend();

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attaching);

            /* Should get to suspended soon because send() is blocked */
            channelWaiter.waitFor(ChannelState.suspended);

            /* Re-enable send() and wait for channel to attach */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

            /* Suspend connection: channel state should change to suspended */
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            channelWaiter.waitFor(ChannelState.suspended);

            /* Reconnect and immediately block transport's send(). This should fail channel reattach */
            ably.connection.once(ConnectionState.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    mockTransport.blockSend();
                }
            });
            ably.connection.connectionManager.requestState(ConnectionState.connected);

            /* Channel should move to attaching state */
            channelWaiter.waitFor(ChannelState.attaching);
            /*
             * Then within realtimeRequestTimeout interval it should get back to suspended
             */
            channelWaiter.waitFor(ChannelState.suspended);

            /* In channelRetryTimeout we should get back to attaching state again */
            channelWaiter.waitFor(ChannelState.attaching);
            /* And then suspended in another 1 second */
            channelWaiter.waitFor(ChannelState.suspended);

            /* Enable message sending again */
            mockTransport.allowSend();

            /* And wait for attached state of the channel */
            channelWaiter.waitFor(ChannelState.attached);

            final ErrorInfo[] errorDetaching = new ErrorInfo[] {null};

            /* Block and detach */
            mockTransport.blockSend();
            channel.detach(new CompletionListener() {
                @Override
                public void onSuccess() {
                    fail("Detach succeeded");
                }

                @Override
                public void onError(ErrorInfo reason) {
                    synchronized (errorDetaching) {
                        errorDetaching[0] = reason;
                        errorDetaching.notify();
                    }
                }
            });

            /* Should get to detaching first */
            channelWaiter.waitFor(ChannelState.detaching);
            /* And then back to attached on timeout */
            channelWaiter.waitFor(ChannelState.attached);
            try {
                synchronized (errorDetaching) {
                    if (errorDetaching[0] != null)
                        errorDetaching.wait(1000);
                }
            } catch (InterruptedException e) {}

            assertNotNull("Verify detach operation failed", errorDetaching[0]);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Initiate connection, and attach to a channel. Simulate a server-initiated detach of the channel
     * and verify that the client attempts to reattach. Block the transport to prevent that from
     * succeeding. Verify that the channel enters the suspended state with the appropriate error.
     *
     * Tests features RTL13b
     */
    @Test
    public void channel_reattach_failed_timeout() {
        AblyRealtime ably = null;
        final String channelName = "channel_reattach_failed_timeout_" + testParams.name;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 1000;

            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attached);

            /* Block send() */
            mockTransport.blockSend();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* Should get to suspended soon because send() is blocked */
            ErrorInfo suspendReason = channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify the suspended event contains the detach reason", 91200, suspendReason.code);

            /* Unblock send(), and expect a transition to attached */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("channel_reattach_failed_timeout: unexpected exception");
        } finally {
            if (ably != null) {
                ably.close();
            }
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /*
     * Initiate connection, and attach to a channel. Simulate a server-initiated detach of the channel
     * and verify that the client attempts to reattach. Block the transport and respond instead with
     * an attach error. Verify that the channel enters the suspended state with the appropriate error.
     *
     * Tests features RTL13b
     */
    @Test
    public void channel_reattach_failed_error() {
        AblyRealtime ably = null;
        final String channelName = "channel_reattach_failed_error_" + testParams.name;
        final int errorCode = 12345;
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = 1000;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            /* Reduce timeout for test to run faster */
            Defaults.realtimeRequestTimeout = 5000;

            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            ChannelWaiter channelWaiter = new ChannelWaiter(channel);
            channel.attach();

            channelWaiter.waitFor(ChannelState.attached);

            /* Block send() */
            mockTransport.blockSend();

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* wait for the client reattempt attachment */
            channelWaiter.waitFor(ChannelState.attaching);

            /* Inject detached+error message as if from the server */
            ProtocolMessage errorMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, errorMessage);

            /* Should get to suspended soon because there was an error response to the attach attempt */
            ErrorInfo suspendReason = channelWaiter.waitFor(ChannelState.suspended);
            assertEquals("Verify the suspended event contains the detach reason", errorCode, suspendReason.code);

            /* Unblock send(), and expect a transition to attached */
            mockTransport.allowSend();
            channelWaiter.waitFor(ChannelState.attached);

        } catch(AblyException e)  {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

    /**
     * Initiate an attach when not connected; verify that the given listener is called
     * with the attach error
     */
    @Test
    public void attach_exception_listener_called() {
        try {
            final String channelName = "attach_exception_listener_called_" + testParams.name;

            /* init Ably */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            AblyRealtime ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel; put into failed state */
            ably.connection.connectionManager.requestState(new ConnectionManager.StateIndication(ConnectionState.failed, new ErrorInfo("Test error", 400, 12345)));
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.failed);
            assertEquals("Verify failed state reached", ably.connection.state, ConnectionState.failed);

            /* attempt to attach */
            Channel channel = ably.channels.get(channelName);
            final ErrorInfo[] listenerError = new ErrorInfo[1];
            synchronized(listenerError) {
                channel.attach(new CompletionListener() {
                    @Override
                    public void onSuccess() {
                        synchronized (listenerError) {
                            listenerError.notify();
                        }
                        fail("Unexpected attach success");
                    }

                    @Override
                    public void onError(ErrorInfo reason) {
                        synchronized (listenerError) {
                            listenerError[0] = reason;
                            listenerError.notify();
                        }
                    }
                });

                /* wait until the listener is called */
                while(listenerError[0] == null) {
                    try { listenerError.wait(); } catch(InterruptedException e) {}
                }
            }

            /* verify that the listener was called with an error */
            assertNotNull("Verify the error callback was called", listenerError[0]);
            assertEquals("Verify the given error is indicated", listenerError[0].code, 12345);

            /* tidy */
            ably.close();
        } catch(AblyException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void no_messages_when_channel_state_not_attached() {

        AblyRealtime senderReceiver = null;
        final String testMessage1 = "{ foo: \"bar\", count: 1, status: \"active\" }";
        final String testMessage2 = "{ foo: \"bar\", count: 2, status: \"active\" }";

        String testName = "no_messages_when_channel_state_not_attached";
        try {

            DebugOptions common_opts = createOptions(testVars.keys[0].keyStr);
            common_opts.protocolListener = new DetachingProtocolListener();
            senderReceiver = new AblyRealtime(common_opts);

            Channel sender_channel = senderReceiver.channels.get(testName);
            ((DetachingProtocolListener)common_opts.protocolListener).theChannel = sender_channel;


            sender_channel.attach();
            (new ChannelWaiter(sender_channel)).waitFor(ChannelState.attached);

            Helpers.MessageWaiter messageWaiter_1 = new Helpers.MessageWaiter(sender_channel);

            sender_channel.publish("1", testMessage1);

            messageWaiter_1.waitFor(1);
            assertEquals("Verify rewound message", testMessage1, messageWaiter_1.receivedMessages.get(0).data);
            messageWaiter_1.reset();

            sender_channel.publish("2", testMessage2);
            messageWaiter_1.waitFor(1, 7000);
            assertEquals("Verify no message received on attach_rewind", 0, messageWaiter_1.receivedMessages.size());

        } catch(Exception e) {
            fail(testName + ": Unexpected exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if(senderReceiver != null)
                senderReceiver.close();
        }
    }

    class DetachingProtocolListener implements DebugOptions.RawProtocolListener {

        public Channel theChannel;
        boolean messageReceived;

        DetachingProtocolListener() {
            messageReceived = false;
        }

        @Override
        public void onRawConnect(String url) {}
        @Override
        public void onRawConnectRequested(String url) {}
        @Override
        public void onRawMessageSend(ProtocolMessage message) {
        }
        @Override
        public void onRawMessageRecv(ProtocolMessage message) {
            if(message.action == ProtocolMessage.Action.message) {
                if (!messageReceived) {
                    messageReceived = true;
                    return;
                }

                theChannel.state = ChannelState.attaching;
            }
        }
    };
}
