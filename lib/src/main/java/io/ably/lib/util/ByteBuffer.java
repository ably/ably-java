package io.ably.lib.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ByteBuffer {
	
	@SuppressWarnings("serial")
	public static class BufferUnderflowException extends RuntimeException {}
	@SuppressWarnings("serial")
	public static class BufferOverflowException extends RuntimeException {}
	
	private byte[] buffer;
	private int pos = 0;
	private int limit = 0;
	
	public static ByteBuffer allocateDirect(int size) { return new ByteBuffer(size); }
	
	public ByteBuffer(int size) {
		buffer = new byte[size];
	}
	
	public int capacity() { return buffer.length; }

	public ByteBuffer clear() { pos = 0; limit = buffer.length; return this; }
	
	public ByteBuffer flip() { limit = pos; pos = 0; return this; }
	
	public int limit() { return limit; }
	
	public boolean hasRemaining() { return limit != pos; }
	
	public void rewind() { pos = 0; }
	
	public int position() { return pos; }
	
	public ByteBuffer position(int pos) {
		if(pos < 0 || pos >= buffer.length || pos > limit)
			throw new IllegalArgumentException("ByteBuffer.position(): new position out of bounds");
		this.pos = pos;
		return this;
	}
	
	public ByteBuffer limit(int limit) {
		if(limit < 0 || limit >= buffer.length)
			throw new IllegalArgumentException("ByteBuffer.limit(): new limit out of bounds");
		if(pos > limit)
			pos = limit;
		this.limit = limit;
		return this;
	}
	
	public int remaining() { return limit - pos; }
	
	public byte get() {
		if(pos < limit)
			return buffer[pos++];
		throw new BufferUnderflowException();
	}
	
	public byte get(int i) {
		if(i >= 0 && i < limit)
			return buffer[i];
		throw new IndexOutOfBoundsException();
	}
	
	public ByteBuffer get(byte[] dst, int offset, int length) {
		if(length <= this.remaining()) {
			System.arraycopy(this.buffer, this.pos, dst, offset, length);
			this.pos += length;
			return this;
		}
		throw new BufferUnderflowException();
	}

	public ByteBuffer put(byte b) {
		if(pos < limit) {
			buffer[pos++] = b;
			return this;
		}
		throw new BufferOverflowException();
	}
	
	public ByteBuffer put(int i, byte b) {
		if(i >= 0 && i < limit) {
			buffer[i] = b;
			return this;
		}
		throw new IndexOutOfBoundsException();
	}

	public ByteBuffer put(byte[] src, int offset, int length) {
		if(length <= this.remaining()) {
			System.arraycopy(src, offset, this.buffer, this.pos, length);
			this.pos += length;
			return this;
		}
		throw new BufferOverflowException();
	}

	public ByteBuffer put(ByteBuffer buf) {
		int length = buf.remaining();
		if(length <= this.remaining()) {
			System.arraycopy(buf.buffer, buf.pos, this.buffer, this.pos, length);
			this.pos += length;
			return this;
		}
		throw new BufferOverflowException();
	}

	public byte[] array() { return buffer; }
	
	public void compact() {
		int toCopy = remaining();
		System.arraycopy(buffer, pos, buffer, 0, toCopy);
		limit = buffer.length;
		pos = toCopy;
	}

	public int read(InputStream is) throws IOException {
		int read = is.read(buffer, pos, remaining());
		if(read != -1)
			pos += read;
		return read;
	}

	public int write(OutputStream os) throws IOException {
		int written = remaining();
		os.write(buffer, pos, written);
		pos += written;
		return written;
	}
}
