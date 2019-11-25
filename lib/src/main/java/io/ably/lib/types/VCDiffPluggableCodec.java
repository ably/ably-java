package io.ably.lib.types;

public interface VCDiffPluggableCodec extends PluggableCodec {
	byte[] decode(byte[] delta, byte[] base) throws AblyException;
}
