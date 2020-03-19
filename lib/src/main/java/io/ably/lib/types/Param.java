package io.ably.lib.types;

/**
 * A class encapsulating a key/value pair.
 */
public final class Param {
	public Param(final String key, final String value) {
		this.key = key;
		this.value = value;
	}

	public Param(final String key, final Object value) {
		this(key, value.toString());
	}

	public final String key;
	public final String value;

	public static Param[] array(final Param val) {
		return new Param[] { val };
	}

	public static Param[] array(final String key, final String value) {
		return array(new Param(key, value));
	}

	private static Param[] push(final Param[] params, final Param param) {
		if (params == null) {
			return array(param);
		}

		final int len = params.length;
		final Param[] result = new Param[len + 1];
		System.arraycopy(params, 0, result, 0, len);
		result[len] = param;
		return result;
	}

	private static Param[] remove(final Param[] params, final String key) {
		int count = 0;
		for (final Param param : params) {
			if (key.equals(param.key)) {
				count += 1;
			}
		}

		final Param[] result = new Param[params.length - count];
		int i = 0;
		for (final Param param : params) {
			if (!key.equals(param.key)) {
				result[i++] = param;
			}
		}

		return result;
	}

	/**
	 * Returns a new array containing a copy of the given params with an additional
	 * Param instance added to the end, initialised with the given key and value.
	 */
	public static Param[] push(Param[] params, String key, String value) {
		return push(params, new Param(key, value));
	}

	/**
	 * Returns a new array containing a copy of the given params with all instances
	 * of Param with a key of val.key removed, then val added to the end.
	 */
	public static Param[] set(final Param[] params, final Param param) {
		return push(remove(params, param.key), param);
	}

	public static boolean containsKey(final Param[] params, final String key) {
		return getFirst(params, key) != null;
	}

	public static String getFirst(final Param[] params, final String key) {
		if (params == null)
			return null;
		for (Param param : params)
			if (param.key.equals(key))
				return param.value;
		return null;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		final Param param = (Param) o;

		if (key != null ? !key.equals(param.key) : param.key != null)
			return false;
		return value != null ? value.equals(param.value) : param.value == null;

	}

	@Override
	public int hashCode() {
		int result = key != null ? key.hashCode() : 0;
		result = 31 * result + (value != null ? value.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return key + ":" + value;
	}
}
