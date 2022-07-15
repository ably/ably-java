package io.ably.core.types;

import com.google.gson.JsonElement;

/**
 * A type that represents a page of results from a paginated http query.
 * The response is accompanied by response details and metadata that
 * indicates the relative queries available.
 */
public abstract class HttpPaginatedResponse {
    public boolean success;
    public int statusCode;
    public int errorCode;
    public String errorMessage;
    public Param[] headers;

    /**
     * Get the contents as an array of component type
     */
    public abstract JsonElement[] items();

    /**
     * Perform the given relative query
     */
    public abstract HttpPaginatedResponse first() throws AblyException;
    public abstract HttpPaginatedResponse current() throws AblyException;
    public abstract HttpPaginatedResponse next() throws AblyException;

    public abstract boolean hasFirst();
    public abstract boolean hasCurrent();
    public abstract boolean hasNext();
    public abstract boolean isLast();
}
