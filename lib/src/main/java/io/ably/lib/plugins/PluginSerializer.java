package io.ably.lib.plugins;

import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/**
 * The `PluginSerializer` interface defines methods for serializing and deserializing objects
 * using the MessagePack format. Implementations of this interface are responsible for
 * converting objects to and from MessagePack binary format.
 */
public interface PluginSerializer {

    /**
     * Reads and deserializes an object from a `MessageUnpacker` instance.
     *
     * @param unpacker The `MessageUnpacker` used to read the serialized data.
     * @return The deserialized object.
     * @throws IOException If an I/O error occurs during deserialization.
     */
    @NotNull
    Object readMsgpack(@NotNull MessageUnpacker unpacker) throws IOException;

    /**
     * Serializes an object and writes it to a `MessagePacker` instance.
     *
     * @param obj The object to be serialized.
     * @param packer The `MessagePacker` used to write the serialized data.
     * @throws IOException If an I/O error occurs during serialization.
     */
    void writeMsgpack(@NotNull Object obj, @NotNull MessagePacker packer) throws IOException;
}
