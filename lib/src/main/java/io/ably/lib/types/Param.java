package io.ably.lib.types;

/**
 * A class encapsulating a key/value pair
 */
public class Param {

	public Param(String key, String value) { this.key = key; this.value = value; }
	public String key;
	public String value;

	public static Param[] push(Param[] params, Param val) {
		int len = params.length;
		Param[] result = new Param[len + 1];
		System.arraycopy(params, 0, result, 0, len);
		result[len] = val;
		return result;
	}

	public static boolean containsKey(Param[] params, String key) {
		if(params == null)
			return false;
		for(Param param : params)
			if(param.key.equals(key))
				return true;
		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Param param = (Param) o;

		if (key != null ? !key.equals(param.key) : param.key != null) return false;
		return value != null ? value.equals(param.value) : param.value == null;

	}

	@Override
	public int hashCode() {
		int result = key != null ? key.hashCode() : 0;
		result = 31 * result + (value != null ? value.hashCode() : 0);
		return result;
	}
}
