package io.ably.transport;

import io.ably.types.AblyException;
import io.ably.types.ErrorInfo;
import io.ably.types.Options;
import io.ably.types.Param;
import io.ably.types.ProtocolMessage;
import io.ably.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ITransport {

	public static final String TAG = ITransport.class.getName();
	
	public interface Factory {
		/**
		 * Obtain and instance of this transport based on the specified options.
		 */
		public ITransport getTransport(TransportParams transportParams, ConnectionManager connectionManager);
	}

	public enum Mode {
		CLEAN,
		RESUME,
		RECOVER
	}

	public static class TransportParams {
		Options options;
		String host;
		int port;
		String connectionId;
		String connectionSerial;
		Mode mode;

		public Param[] getConnectParams(Param[] baseParams) {
			List<Param> paramList = new ArrayList<Param>(Arrays.asList(baseParams));
			if(options.useBinaryProtocol)
				paramList.add(new Param("format", "msgpack"));
			if(!options.echoMessages)
				paramList.add(new Param("echo", "false"));
			if(connectionId != null) {
				mode = Mode.RESUME;
				paramList.add(new Param("resume", connectionId));
				if(connectionSerial != null)
					paramList.add(new Param("connection_serial", connectionSerial));
			} else if(options.recover != null) {
				mode = Mode.RECOVER;
				Pattern recoverSpec = Pattern.compile("^(\\w+):(\\-?\\w+)$");
				Matcher match = recoverSpec.matcher(options.recover);
				if(match.matches()) {
					paramList.add(new Param("recover", match.group(1)));
					paramList.add(new Param("connection_serial", match.group(2)));
				} else {
					Log.e(TAG, "Invalid recover string specified");
				}
			}
			if(options.clientId != null)
				paramList.add(new Param("client_id", options.clientId));

			return paramList.toArray(new Param[paramList.size()]);
		}
	}

	public static interface ConnectListener {
		public void onTransportAvailable(ITransport transport, TransportParams params);
		public void onTransportUnavailable(ITransport transport, TransportParams params, ErrorInfo reason);
	}

	/**
	 * Initiate a connection attempt; the transport will be activated,
	 * and attempt to remain connected, until disconnect() is called.
	 * @throws AblyException 
	 */
	public void connect(ConnectListener connectListener);

	/**
	 * Close this transport.
	 */
	public void close(boolean sendDisconnect);

	/**
	 * Kill this transport.
	 */
	public void abort(ErrorInfo reason);

	/**
	 * Send a message on the channel
	 * @param msg
	 * @throws IOException
	 */
	public void send(ProtocolMessage msg) throws AblyException;

	public String getHost();
}
