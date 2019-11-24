package io.ably.lib.types;

public interface VCDiffPluggableCodec {
	byte[] Decode(byte[] delta, byte[] base) throws AblyException;
}
