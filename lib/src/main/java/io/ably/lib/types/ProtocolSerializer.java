package io.ably.lib.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.util.Serialisation;

public class ProtocolSerializer {

	/****************************************
	 *            Msgpack decode
	 ****************************************/
	
	public static ProtocolMessage readMsgpack(byte[] packed) throws AblyException {
		try {
			MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(packed);
			return ProtocolMessage.fromMsgpack(unpacker);
		} catch (IOException ioe) {
			throw AblyException.fromThrowable(ioe);
		}
	}

	/****************************************
	 *            Msgpack encode
	 ****************************************/
	
	public static byte[] writeMsgpack(ProtocolMessage message) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
		try {
			message.writeMsgpack(packer);
	
			packer.flush();
			return out.toByteArray();
		} catch(IOException e) { return null; }
	}

	/****************************************
	 *              JSON decode
	 ****************************************/
	
	public static ProtocolMessage fromJSON(String packed) throws AblyException {
		return Serialisation.gson.fromJson(packed, ProtocolMessage.class);
	}

	/****************************************
	 *              JSON encode
	 ****************************************/
	
	public static byte[] writeJSON(ProtocolMessage message) throws AblyException {
		return Serialisation.gson.toJson(message).getBytes();
	}
}
