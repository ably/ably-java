package io.ably.types;

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
	public T[] items();

	/**
	 * Obtain params required to perform the given relative query
	 */
	public abstract PaginatedResult<T> first() throws AblyException;
	public abstract PaginatedResult<T> current() throws AblyException;
	public abstract PaginatedResult<T> next() throws AblyException;

	public abstract boolean hasFirst();
	public abstract boolean hasCurrent();
	public abstract boolean hasNext();
}
