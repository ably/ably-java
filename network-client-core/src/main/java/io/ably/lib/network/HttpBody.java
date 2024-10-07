package io.ably.lib.network;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;

@Data
@Setter(AccessLevel.NONE)
@AllArgsConstructor
public class HttpBody {
    private final String contentType;
    private final byte[] content;
}
