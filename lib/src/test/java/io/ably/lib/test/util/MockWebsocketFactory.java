package io.ably.lib.test.util;

import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ITransport;
import io.ably.lib.transport.WebSocketTransport;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.HttpCode;

/**
 * Websocket factory that creates transport with capability of modifying the behaviour of send() and other calls
 */
public class MockWebsocketFactory implements ITransport.Factory {

    enum SendBehaviour {
        allow,
        block,
        fail
    }
    enum ConnectBehaviour {
        allow,
        fail
    }

    public interface MessageFilter {
        boolean matches(ProtocolMessage message);
    }

    public interface HostFilter {
        boolean matches(String hostname);
    }

    public interface HostTransform {
        String transformHost(String givenHost);
    }

    SendBehaviour sendBehaviour = SendBehaviour.allow;
    ConnectBehaviour connectBehaviour = ConnectBehaviour.allow;
    MessageFilter messageFilter = null;
    HostFilter hostFilter = null;
    HostTransform hostTransform = null;

    public ITransport lastCreatedTransport =  null;

    public static class TransformParams extends ITransport.TransportParams {
        private HostTransform hostTransform;

        TransformParams(ITransport.TransportParams src, HostTransform hostTransform) {
            super(src.getClientOptions(), new EmptyPlatformAgentProvider());
            this.hostTransform = hostTransform;
            this.host = hostTransform.transformHost(src.getHost());
            this.port = src.getPort();
        }
    }

    @Override
    public ITransport getTransport(final ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
        ITransport.TransportParams transformParams = transportParams;
        if(hostTransform != null) {
            transformParams = new TransformParams(transportParams, hostTransform);
        }
        lastCreatedTransport = new MockWebsocketTransport(transportParams, transformParams, connectionManager);
        return lastCreatedTransport;
    }

    public void blockSend(MessageFilter filter) {
        messageFilter = filter;
        sendBehaviour = SendBehaviour.block;
    }
    public void blockSend() { blockSend(null); }

    public void allowSend(MessageFilter filter) {
        messageFilter = filter;
        sendBehaviour = SendBehaviour.allow;
    }
    public void allowSend() { allowSend(null);}

    public void failSend(MessageFilter filter) {
        messageFilter = filter;
        sendBehaviour = SendBehaviour.fail;
    }
    public void failSend() { failSend(null); }

    public void setMessageFilter(MessageFilter filter) {
        messageFilter = filter;
    }

    public void failConnect(HostFilter filter) {
        hostFilter = filter;
        connectBehaviour = ConnectBehaviour.fail;
    }
    public void failConnect() { failConnect(null); }

    public void setHostTransform(HostTransform transform) {
        hostTransform = transform;
    }

    /*
     * Special transport class that allows blocking send() and other operations
     */
    private class MockWebsocketTransport extends WebSocketTransport {
        private final TransportParams givenTransportParams;
        private final TransportParams transformedTransportParams;

        private MockWebsocketTransport(TransportParams givenTransportParams, TransportParams transformedTransportParams, ConnectionManager connectionManager) {
            super(transformedTransportParams, connectionManager);
            this.givenTransportParams = givenTransportParams;
            this.transformedTransportParams = transformedTransportParams;
        }

        @Override
        public void send(ProtocolMessage msg) throws AblyException {
            switch (sendBehaviour) {
                case allow:
                    if (messageFilter == null || messageFilter.matches(msg)) {
                        super.send(msg);
                    }
                    break;
                case block:
                    if (messageFilter == null || messageFilter.matches(msg)) {
                        /* do nothing */
                    } else {
                        super.send(msg);
                    }
                    break;
                case fail:
                    if (messageFilter == null || messageFilter.matches(msg)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Mock", 40000));
                    } else {
                        super.send(msg);
                    }
                    break;
            }
        }

        @Override
        public void connect(ConnectListener connectListener) {
            String host = givenTransportParams.getHost();
            switch (connectBehaviour) {
                case allow:
                    if (hostFilter == null || hostFilter.matches(host)) {
                        System.out.println("MockWebsocketTransport: allowing " + host);
                        super.connect(connectListener);
                    } else {
                        System.out.println("MockWebsocketTransport: disallowing " + host);
                        connectListener.onTransportUnavailable(this, new ErrorInfo("MockWebsocketTransport: connection disallowed by hostFilter", HttpCode.INTERNAL_SERVER_ERROR, 50000));
                    }
                    break;
                case fail:
                    if (hostFilter == null || hostFilter.matches(host)) {
                        System.out.println("MockWebsocketTransport: failing " + host);
                        connectListener.onTransportUnavailable(this, new ErrorInfo("MockWebsocketTransport: connection failed by hostFilter", HttpCode.INTERNAL_SERVER_ERROR, 50000));
                    } else {
                        System.out.println("MockWebsocketTransport: not failing " + host);
                        super.connect(connectListener);
                    }
                    break;
            }
        }
    }
}
