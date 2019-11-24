package io.ably.lib.types;

public interface VCDiffPluggableCodec {
	byte[] decode(byte[] delta, byte[] base) throws AblyException;
}
