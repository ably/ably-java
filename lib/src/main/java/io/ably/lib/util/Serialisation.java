package io.ably.lib.util;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Serialisation {
	public static final ObjectMapper msgpackObjectMapper = new ObjectMapper(new MessagePackFactory());
	public static final ObjectMapper jsonObjectMapper = new ObjectMapper(new JsonFactory());
}
