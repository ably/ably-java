package io.ably.lib.test.util;

import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ITransport;
import io.ably.lib.transport.WebSocketTransport;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;

/**
 * Websocket factory that creates transport with capability of blocking send() calls
 */
public class MockWebsocketFactory implements ITransport.Factory {
	@Override
	public ITransport getTransport(ITransport.TransportParams transportParams, ConnectionManager connectionManager) {
		lastCreatedTransport = new MockWebsocketTransport(transportParams, connectionManager);
		return lastCreatedTransport;
	}

	public static void blockSend() {
		MockWebsocketTransport.sendBehaviour = MockWebsocketTransport.SendBehaviour.block;
	}
	public static void allowSend() {
		MockWebsocketTransport.messageFilter = null;
		MockWebsocketTransport.sendBehaviour = MockWebsocketTransport.SendBehaviour.allow;
	}
	public static void failSend() {
		MockWebsocketTransport.sendBehaviour = MockWebsocketTransport.SendBehaviour.fail;
	}
	public static void setMessageFilter(MessageFilter filter) {
		MockWebsocketTransport.messageFilter = filter;
	}

	public static ITransport lastCreatedTransport =  null;
	public interface MessageFilter {
		boolean matches(ProtocolMessage message);
	}

	/*
	 * Special transport class that allows blocking send()
	 */
	private static class MockWebsocketTransport extends WebSocketTransport {
		enum SendBehaviour {
			allow,
			block,
			fail
		}
		static SendBehaviour sendBehaviour = SendBehaviour.allow;
		static MessageFilter messageFilter = null;

		private MockWebsocketTransport(TransportParams transportParams, ConnectionManager connectionManager) {
			super(transportParams, connectionManager);
		}

		@Override
		public void send(ProtocolMessage msg) throws AblyException {
			switch (sendBehaviour) {
				case allow:
					super.send(msg);
					break;
				case block:
					if (messageFilter != null && !messageFilter.matches(msg))
						super.send(msg);
					break;
				case fail:
					if (messageFilter == null || messageFilter.matches(msg))
						throw AblyException.fromErrorInfo(new ErrorInfo("Mock", 40000));
					else
						super.send(msg);
					break;
			}
		}
	}
}

