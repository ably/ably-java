package io.ably.types;

import java.util.List;

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
	public T[] asArray();

	/**
	 * Get the contents as an list of component type
	 */
	public List<T> asList();

	/**
	 * Obtain params required to perform the given relative query
	 */
	public abstract Param[] getFirst() throws AblyException;
	public abstract Param[] getCurrent() throws AblyException;
	public abstract Param[] getNext() throws AblyException;
}
