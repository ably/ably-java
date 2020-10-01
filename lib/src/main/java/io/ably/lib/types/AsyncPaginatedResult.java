package io.ably.lib.types;

/**
 * A type that represents a page of results from a paginated query.
 * The response is accompanied by metadata that indicates the relative
 * queries available.
 *
 * @param <T>
 */
public interface AsyncPaginatedResult<T> {

	/**
	 * Get the contents as an array of component type
	 */
	T[] items();

	/**
	 * Obtain params required to perform the given relative query
	 */
	void first(Callback<AsyncPaginatedResult<T>> callback);
	void current(Callback<AsyncPaginatedResult<T>> callback);
	void next(Callback<AsyncPaginatedResult<T>> callback);

	boolean hasFirst();
	boolean hasCurrent();
	boolean hasNext();
}
