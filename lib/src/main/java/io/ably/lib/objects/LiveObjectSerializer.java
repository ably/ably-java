package io.ably.lib.objects;

import com.google.gson.JsonArray;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/**
 * Serializer interface for converting between LiveObject arrays and their
 * MessagePack or JSON representations.
 */
public interface LiveObjectSerializer {
    /**
     * Reads a MessagePack array from the given unpacker and deserializes it into an Object array.
     *
     * @param unpacker the MessageUnpacker to read from
     * @return the deserialized Object array
     * @throws IOException if an I/O error occurs during unpacking
     */
    @NotNull
    Object[] readMsgpackArray(@NotNull MessageUnpacker unpacker) throws IOException;

    /**
     * Serializes the given Object array as a MessagePack array using the provided packer.
     *
     * @param objects the Object array to serialize
     * @param packer the MessagePacker to write to
     * @throws IOException if an I/O error occurs during packing
     */
    void writeMsgpackArray(@NotNull Object[] objects, @NotNull MessagePacker packer) throws IOException;

    /**
     * Reads a JSON array from the given {@link JsonArray} and deserializes it into an Object array.
     *
     * @param json the {@link JsonArray} representing the array to deserialize
     * @return the deserialized Object array
     */
    @NotNull
    Object[] readFromJsonArray(@NotNull JsonArray json);

    /**
     * Serializes the given Object array as a JSON array.
     *
     * @param objects the Object array to serialize
     * @return the resulting JsonArray
     */
    @NotNull
    JsonArray asJsonArray(@NotNull Object[] objects);
}
