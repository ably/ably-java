package io.ably.lib.types;

import com.davidehrmann.vcdiff.VCDiffDecoder;
import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import java.io.ByteArrayOutputStream;

public class VCDiffDecoderHelper {

	private final static VCDiffDecoder decoder = VCDiffDecoderBuilder.builder().buildSimple();

	public static byte[] decode(byte[] delta, byte[] base) throws MessageDecodeException {
		try {
			ByteArrayOutputStream decoded = new ByteArrayOutputStream();
			decoder.decode(base, delta, decoded);
			return decoded.toByteArray();
		} catch (Throwable t) {
			throw MessageDecodeException.fromThrowableAndErrorInfo(t, new ErrorInfo("VCDIFF delta decode failed", 400, 40018));
		}
	}
}
