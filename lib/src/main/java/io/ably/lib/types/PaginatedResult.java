package io.ably.lib.types;

/**
 * A type that represents a page of results from a paginated query.
 * The response is accompanied by metadata that indicates the relative
 * queries available.
 *
 * @param <T>
 */
public interface PaginatedResult<T> {

	/**
	 * Get the contents as an array of component type
	 */
	T[] items();

	/**
	 * Obtain params required to perform the given relative query
	 */
	PaginatedResult<T> first() throws AblyException;
	PaginatedResult<T> current() throws AblyException;
	PaginatedResult<T> next() throws AblyException;

	boolean hasFirst();
	boolean hasCurrent();
	boolean hasNext();

	boolean isLast();
}
