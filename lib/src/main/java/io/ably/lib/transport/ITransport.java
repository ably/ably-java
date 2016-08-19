package io.ably.lib.transport;

import io.ably.lib.http.HttpUtils;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;

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
		clean,
		resume,
		recover
	}

	public static class TransportParams {
		/* Param keys */
		public static final String LIB_PARAM_KEY = "lib";

		ClientOptions options;
		String host;
		int port;
		String connectionKey;
		String connectionSerial;
		Mode mode;

		public Param[] getConnectParams(Param[] baseParams) {
			List<Param> paramList = new ArrayList<Param>(Arrays.asList(baseParams));
			if(options.useBinaryProtocol)
				paramList.add(new Param("format", "msgpack"));
			if(!options.echoMessages)
				paramList.add(new Param("echo", "false"));
			if(connectionKey != null) {
				mode = Mode.resume;
				paramList.add(new Param("resume", connectionKey));
				if(connectionSerial != null)
					paramList.add(new Param("connection_serial", connectionSerial));
			} else if(options.recover != null) {
				mode = Mode.recover;
				Pattern recoverSpec = Pattern.compile("^([\\w\\-\\!]+):(\\-?\\d+)$");
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

			paramList.add(new Param(LIB_PARAM_KEY, HttpUtils.X_ABLY_LIB_VALUE));

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
