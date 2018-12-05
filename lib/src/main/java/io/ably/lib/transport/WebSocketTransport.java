package io.ably.lib.transport;

import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.transport.ConnectionManager.StateIndication;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolSerializer;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.util.Log;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.WebSocket;

public class WebSocketTransport implements ITransport {

	private static final String TAG = WebSocketTransport.class.getName();

	/******************
	 * public factory API
	 ******************/

	public static class Factory implements ITransport.Factory {
		@Override
		public WebSocketTransport getTransport(TransportParams params, ConnectionManager connectionManager) {
			return new WebSocketTransport(params, connectionManager);
		}
	}

	/******************
	 * protected constructor
	 ******************/

	protected WebSocketTransport(TransportParams params, ConnectionManager connectionManager) {
		this.params = params;
		this.connectionManager = connectionManager;
		this.channelBinaryMode = params.options.useBinaryProtocol;
		/* We do not require Ably heartbeats, as we can use WebSocket pings instead. */
		params.heartbeats = false;
	}

	/******************
	 * ITransport methods
	 ******************/

	@Override
	public void connect(ConnectListener connectListener) {
		this.connectListener = connectListener;
		try {
			boolean isTls = params.options.tls;
			String wsScheme = isTls ? "wss://" : "ws://";
			wsUri = wsScheme + params.host + ':' + String.valueOf(params.port) + "/";
			Param[] authParams = connectionManager.ably.auth.getAuthParams();
			Param[] connectParams = params.getConnectParams(authParams);
			if(connectParams.length > 0)
				wsUri = HttpUtils.encodeParams(wsUri, connectParams);
			synchronized(this) {
				wsConnection = new WsClient(URI.create(wsUri));
				if(isTls) {
					SSLContext sslContext = SSLContext.getInstance("TLS");
					sslContext.init( null, null, null );
					SSLSocketFactory factory = sslContext.getSocketFactory();// (SSLSocketFactory) SSLSocketFactory.getDefault();
					wsConnection.setSocket( factory.createSocket() );
				}
			}
			wsConnection.connect();
		} catch(AblyException e) {
			Log.e(TAG, "Unexpected exception attempting connection; wsUri = " + wsUri, e);
			connectListener.onTransportUnavailable(this, params, e.errorInfo);
		} catch(Throwable t) {
			Log.e(TAG, "Unexpected exception attempting connection; wsUri = " + wsUri, t);
			connectListener.onTransportUnavailable(this, params, AblyException.fromThrowable(t).errorInfo);
		}
	}

	@Override
	public void close(boolean sendClose) {
		synchronized(this) {
			if(wsConnection != null) {
				if(sendClose) {
					try {
						send(new ProtocolMessage(Action.close));
					} catch (AblyException e) {
						Log.e(TAG, "Unexpected exception sending close", e);
					}
				}
				wsConnection.close();
				wsConnection = null;
			}
		}
	}

	@Override
	public void abort(ErrorInfo reason) {
		synchronized(this) {
			if(wsConnection != null) {
				wsConnection.close();
				wsConnection = null;
			}
		}
		connectionManager.notifyState(this, new StateIndication(ConnectionState.failed, reason));
	}

	@Override
	public void send(ProtocolMessage msg) throws AblyException {
		try {
			if(channelBinaryMode) {
				byte[] encodedMsg = ProtocolSerializer.writeMsgpack(msg);
				if (Log.level <= Log.VERBOSE) {
					ProtocolMessage decodedMsg = ProtocolSerializer.readMsgpack(encodedMsg);
					Log.v(TAG, "send(): " + decodedMsg.action + ": " + new String(ProtocolSerializer.writeJSON(decodedMsg)));
				}
				wsConnection.send(encodedMsg);
			} else {
				if (Log.level <= Log.VERBOSE)
					Log.v(TAG, "send(): " + new String(ProtocolSerializer.writeJSON(msg)));
				wsConnection.send(ProtocolSerializer.writeJSON(msg));
			}
		} catch (Exception e) {
			throw AblyException.fromThrowable(e);
		}
	}

	@Override
	public String getHost() {
		return params.host;
	}

	/**************************
	 * WebSocketHandler methods
	 **************************/

	class WsClient extends WebSocketClient {

		public WsClient(URI serverUri) {
			super(serverUri);
		}

		@Override
		public void onOpen(ServerHandshake handshakedata) {
			if(connectListener != null) {
				connectListener.onTransportAvailable(WebSocketTransport.this, params);
				connectListener = null;
			}
			flagActivity();
		}

		@Override
		public void onMessage(ByteBuffer blob) {
			try {
				connectionManager.onMessage(WebSocketTransport.this, ProtocolSerializer.readMsgpack(blob.array()));
			} catch (AblyException e) {
				String msg = "Unexpected exception processing received binary message";
				Log.e(TAG, msg, e);
			}
			flagActivity();
		}

		@Override
		public void onMessage(String string) {
			try {
				connectionManager.onMessage(WebSocketTransport.this, ProtocolSerializer.fromJSON(string));
			} catch (AblyException e) {
				String msg = "Unexpected exception processing received text message";
				Log.e(TAG, msg, e);
			}
			flagActivity();
		}

		/* This allows us to detect a websocket ping, so we don't need Ably pings. */
		@Override
		public void onWebsocketPing( WebSocket conn, Framedata f ) {
			/* Call superclass to ensure the pong is sent. */
			super.onWebsocketPing( conn, f );
			flagActivity();
		}

		@Override
		public void onClose(int wsCode, String wsReason, boolean remote) {
			flagActivity();
			ConnectionState newState;
			ErrorInfo reason;
			switch(wsCode) {
			case NEVER_CONNECTED:
				newState = ConnectionState.disconnected;
				reason = ConnectionManager.REASON_NEVER_CONNECTED;
				break;
			case CLOSE_NORMAL:
			case BUGGYCLOSE:
			case GOING_AWAY:
			case ABNORMAL_CLOSE:
				/* we don't know the specific reason that the connection closed in these cases,
				 * but we have to assume it's a problem with connectivity rather than some other
				 * application problem */
				newState = ConnectionState.disconnected;
				reason = ConnectionManager.REASON_DISCONNECTED;
				break;
			case REFUSE:
			case POLICY_VALIDATION:
				newState = ConnectionState.failed;
				reason = ConnectionManager.REASON_REFUSED;
				break;
			case TOOBIG:
				newState = ConnectionState.failed;
				reason = ConnectionManager.REASON_TOO_BIG;
				break;
			case NO_UTF8:
			case CLOSE_PROTOCOL_ERROR:
			case UNEXPECTED_CONDITION:
			case EXTENSION:
			case TLS_ERROR:
			default:
				/* we don't know the specific reason that the connection closed in these cases,
				 * but we have to assume it's an application problem, and the problem will
				 * recur if we try again. The failed state means that we won't automatically
				 * try again. */
				newState = ConnectionState.failed;
				reason = ConnectionManager.REASON_FAILED;
				break;
			}
			synchronized(WebSocketTransport.this) {
				wsConnection = null;
			}
			connectionManager.notifyState(WebSocketTransport.this, new StateIndication(newState, reason));
			dispose();
		}

		@Override
		public void onError(Exception e) {
			String msg = "Unexpected exception in WsClient";
			Log.e(TAG, msg, e);
			if(connectListener != null) {
				connectListener.onTransportUnavailable(WebSocketTransport.this, params, new ErrorInfo(e.getMessage(), 503, 80000));
				connectListener = null;
			}
		}

		private void dispose() {
			/* dispose timer */
			if(timer != null) {
				timer.cancel();
				timer = null;
			}
		}

		private void flagActivity() {
			lastActivityTime = System.currentTimeMillis();
			connectionManager.setLastActivity(lastActivityTime);
			if (timer == null && connectionManager.maxIdleInterval != 0) {
				/* No timer currently running because previously there was no
				 * maxIdleInterval configured, but now there is a
				 * maxIdleInterval configured.  Call checkActivity so a timer
				 * gets started.  This happens when flagActivity gets called
				 * just after processing the connect message that configures
				 * maxIdleInterval. */
				checkActivity();
			}
		}

		private void checkActivity() {
			long timeout = connectionManager.maxIdleInterval;
			if (timeout == 0) {
				Log.v(TAG, "checkActivity: infinite timeout");
				timer = null;
				return;
			}
			timeout += connectionManager.ably.options.realtimeRequestTimeout;
			long now = System.currentTimeMillis();
			long next = lastActivityTime + timeout;
			if (now < next) {
				/* We have not reached maxIdleInterval+realtimeRequestTimeout
				 * of inactivity.  Schedule a new timer for that long after the
				 * last activity time. */
				Log.v(TAG, "checkActivity: ok");
				if (timer == null) {
					synchronized(this) {
						if (timer == null)
							timer = new Timer();
					}
				}
				try {
					timer.schedule(new WsClientTimerTask(this), next - now);
				} catch(IllegalStateException ise) {
					Log.e(TAG, "Unexpected exception scheduling activity timer", ise);
				}
			} else {
				/* Timeout has been reached. Close the connection. */
				Log.e(TAG, "No activity for " + timeout + "ms, closing connection");
				closeConnection(CloseFrame.ABNORMAL_CLOSE, "timed out");
			}
		}

		/* The TimerTask used to implement disconnection if no activity (inc
		 * pings) is seen within a certain time.
		 */
		class WsClientTimerTask extends TimerTask {
			private final WsClient client;

			public WsClientTimerTask(WsClient client) {
				this.client = client;
			}

			public void run() {
				try {
					client.checkActivity();
				} catch(Throwable t) {
					Log.e(TAG, "Unexpected exception in activity timer handler", t);
				}
			}
		}

		/***************************
		 * WsClient private members
		 ***************************/

		private Timer timer;
		private long lastActivityTime;

	}

	public String toString() {
		return WebSocketTransport.class.getName() + " [" + getURL() + "]";
	}

	public String getURL() {
		return wsUri;
	}

	/******************
	 * private members
	 ******************/

	private final TransportParams params;
	private final ConnectionManager connectionManager;
	private final boolean channelBinaryMode;
	private String wsUri;
	private ConnectListener connectListener;

	private WsClient wsConnection;

	private static final int NEVER_CONNECTED      =   -1;
	private static final int BUGGYCLOSE           =   -2;
	private static final int CLOSE_NORMAL         = 1000;
	private static final int GOING_AWAY           = 1001;
	private static final int CLOSE_PROTOCOL_ERROR = 1002;
	private static final int REFUSE               = 1003;
/*	private static final int UNUSED               = 1004; */
/*	private static final int NOCODE               = 1005; */
	private static final int ABNORMAL_CLOSE       = 1006;
	private static final int NO_UTF8              = 1007;
	private static final int POLICY_VALIDATION    = 1008;
	private static final int TOOBIG               = 1009;
	private static final int EXTENSION            = 1010;
	private static final int UNEXPECTED_CONDITION = 1011;
	private static final int TLS_ERROR            = 1015;

}
