package io.ably.lib.types;

public interface VCDiffPluggableCodec extends Plugin {
	byte[] decode(byte[] delta, byte[] base) throws MessageDecodeException;
}
