package io.ably.transport;

import io.ably.http.HttpUtils;
import io.ably.realtime.ConnectionState;
import io.ably.transport.ConnectionManager.StateIndication;
import io.ably.types.AblyException;
import io.ably.types.ErrorInfo;
import io.ably.types.Param;
import io.ably.types.ProtocolMessage;
import io.ably.types.ProtocolMessage.Action;
import io.ably.types.ProtocolSerializer;
import io.ably.util.Log;

import java.net.URI;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

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
	 * private constructor
	 ******************/

	private WebSocketTransport(TransportParams params, ConnectionManager connectionManager) {
		this.params = params;
		this.connectionManager = connectionManager;
		this.channelBinaryMode = params.options.useBinaryProtocol;
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
						send(new ProtocolMessage(Action.CLOSE));
					} catch (AblyException e) {
						Log.e(TAG, "Unexpected exception sending CLOSE", e);
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
			if(channelBinaryMode)
				wsConnection.send(ProtocolSerializer.asMsgpack(msg));
			else
				wsConnection.send(ProtocolSerializer.asJSON(msg));
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
		}

		@Override
		public void onMessage(ByteBuffer blob) {
			try {
				connectionManager.onMessage(ProtocolSerializer.fromMsgpack(blob.array()));
			} catch (AblyException e) {
				String msg = "Unexpected exception processing received binary message";
				Log.e(TAG, msg, e);
			}
		}

		@Override
		public void onMessage(String string) {
			try {
				connectionManager.onMessage(ProtocolSerializer.fromJSON(string));
			} catch (AblyException e) {
				String msg = "Unexpected exception processing received binary message";
				Log.e(TAG, msg, e);
			}
		}

		@Override
		public void onClose(int wsCode, String wsReason, boolean remote) {
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
	}

	public String toString() {
		return WebSocketTransport.class.getName() + " [" + wsUri + "]";
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
