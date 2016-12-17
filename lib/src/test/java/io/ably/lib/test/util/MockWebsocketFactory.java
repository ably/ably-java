package io.ably.lib.test.util;

import io.ably.lib.test.realtime.RealtimeChannelTest;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ITransport;
import io.ably.lib.transport.WebSocketTransport;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ProtocolMessage;

/**
 * Websocket factory that creates transport with capability of blocking send() calls
 */
public class MockWebsocketFactory implements ITransport.Factory {
	@Override
	public ITransport getTransport(ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
		return new MockWebsocketTransport(transportParams, connectionManager);
	}

	public static void blockSend() {
		MockWebsocketTransport.blockSend = true;
	}
	public static void allowSend() {
		MockWebsocketTransport.blockSend = false;
	}

	/*
	 * Special transport class that allows blocking send()
	 */
	private static class MockWebsocketTransport extends WebSocketTransport {
		static boolean blockSend = false;

		private MockWebsocketTransport(TransportParams transportParams, ConnectionManager connectionManager) {
			super(transportParams, connectionManager);
		}

		@Override
		public void send(ProtocolMessage msg) throws AblyException {
			if (!blockSend)
				super.send(msg);
		}
	}
}

