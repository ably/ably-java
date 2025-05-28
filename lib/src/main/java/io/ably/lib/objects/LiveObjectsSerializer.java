package io.ably.lib.objects;

import io.ably.lib.plugins.PluginSerializer;
import org.jetbrains.annotations.NotNull;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public abstract class LiveObjectsSerializer implements PluginSerializer {
    @NotNull
    public Object[] readMsgpackArray(@NotNull MessageUnpacker unpacker) throws IOException {
        int count = unpacker.unpackArrayHeader();
        Object[] result = new Object[count];
        for(int i = 0; i < count; i++)
            result[i] = readMsgpack(unpacker);
        return result;
    }

    public void writeMsgpackArray(@NotNull Object[] objects, @NotNull MessagePacker packer) throws IOException {
        int count = objects.length;
        packer.packArrayHeader(count);
        for(Object object : objects) {
            writeMsgpack(object, packer);
        }
    }
}
