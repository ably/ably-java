package io.ably.lib.types;

public class EncodingResult {
	private final Object newPayload;
	private final String newEncoding;

	public EncodingResult(Object newPayload) {
		this(newPayload, null);
	}

	public EncodingResult(Object newPayload, String newEncoding) {
		this.newPayload = newPayload;
		this.newEncoding = newEncoding;
	}

	Object getNewPayload() {
		return this.newPayload;
	}

	String getNewEncoding() {
		return this.newEncoding;
	}
}
