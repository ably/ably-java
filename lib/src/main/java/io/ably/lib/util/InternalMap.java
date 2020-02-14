package io.ably.lib.util;

import java.util.Map;
import java.util.Map.Entry;

import io.ably.lib.types.ReadOnlyMap;

public abstract class InternalMap<K, V> implements ReadOnlyMap<K, V> {
    protected final Map<K, V> map;

    public InternalMap(final Map<K, V> map) {
        this.map = map;
    }

    @Override
    public final boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public final boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @Override
    public final Iterable<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public final V get(final Object key) {
        return map.get(key);
    }

    @Override
    public final boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public final Iterable<K> keySet() {
        return map.keySet();
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final Iterable<V> values() {
        return map.values();
    }
}
