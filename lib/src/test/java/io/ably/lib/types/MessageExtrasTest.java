package io.ably.lib.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

public class MessageExtrasTest {
	/**
	 * Construct an instance from a JSON source and validate that the
	 * serialised JSON is the same.
	 */
	@Test
	public void raw() {
		final JsonObject objectA = new JsonObject();
		objectA.addProperty("someKey", "someValue");

		final JsonObject objectB = new JsonObject();
		objectB.addProperty("someOtherKey", "someValue");

		final MessageExtras messageExtras = new MessageExtras(objectA);
		assertNull(messageExtras.getDelta());

		final MessageExtras.Serializer serializer = new MessageExtras.Serializer();
		final JsonElement serialised = serializer.serialize(messageExtras, null, null);

		assertEquals(objectA, serialised);
		assertNotEquals(objectB, serialised);
		assertNotEquals(objectB, objectA);
	}

	/**
	 * Construct an instance with DeltaExtras and validate that the
	 * serialised JSON is as expected. Also validate that the DeltaExtras
	 * retrieved is the same.
	 */
	@Test
	public void delta() {
		final DeltaExtras deltaExtrasA = new DeltaExtras("someFormat", "someSource");
		final DeltaExtras deltaExtrasB = new DeltaExtras("someFormat", "someOtherSource");

		final MessageExtras messageExtras = new MessageExtras(deltaExtrasA);
		assertEquals(deltaExtrasA, messageExtras.getDelta());
		assertNotEquals(deltaExtrasB, messageExtras.getDelta());
		assertNotEquals(deltaExtrasB, deltaExtrasA);

		final JsonObject expectedDeltaExtrasJsonElement = new JsonObject();
		expectedDeltaExtrasJsonElement.addProperty("format", "someFormat");
		expectedDeltaExtrasJsonElement.addProperty("from", "someSource");
		final JsonObject expectedJsonElement = new JsonObject();
		expectedJsonElement.add("delta", expectedDeltaExtrasJsonElement);

		final MessageExtras.Serializer serializer = new MessageExtras.Serializer();
		final JsonElement serialised = serializer.serialize(messageExtras, null, null);

		assertEquals(expectedJsonElement, serialised);
	}
}
