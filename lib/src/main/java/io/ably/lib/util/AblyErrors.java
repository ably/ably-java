package io.ably.lib.util;

import java.util.HashMap;
import java.util.Map;

public enum AblyErrors {

    NO_ERROR(10000, "no error"),

    BAD_REQUEST(40000, "bad request"),
    INVALID_REQUEST_BODY(40001, "invalid request body"),
    INVALID_PARAMETER_NAME(40002, "invalid parameter name"),
    INVALID_PARAMETER_VALUE(40003, "invalid parameter value"),
    INVALID_HEADER(40004, "invalid header"),
    INVALID_CREDENTIALS(40005, "invalid credential"),
    INVALID_CONNECTION_ID(40006, "invalid connection id"),
    INVALID_MESSAGE_ID(40007, "invalid message id"),
    INVALID_CONTENT_LENGTH(40008, "invalid content length"),
    MAXIMUM_LENGHT_EXCEEDED(40009, "maximum message length exceeded"),
    INVALID_CHANNEL_NAME(40010, "invalid channel name"),
    STALE_RING_STATE(40011, "stale ring state"),
    INVALID_CLIENT_ID(40012, "invalid client id"),
    INVALID_MESSAGE_DATA_OR_ENCODING(40013, "Invalid message data or encoding"),
    RESOURCE_DISPOSED(40014, "Resource disposed"),
    INVALID_DEVICE_ID(40015, "Invalid device id"),
    INVALID_MESSAGE_NAME(40016, "Invalid message name"),
    UNSUPPORTED_PROTOCOL_VERSION(40017, "Unsupported protocol version"),
    UNABLE_TO_DECODE_MESSAGE(40018, "Unable to decode message; channel attachment no longer viable"),
    REQUIRED_CLIENT_PLUGIN_NOT_PRESENT(40019, "Required client library plugin not present"),
    BATCH_ERROR(40020, "Batch error"),
    FEATURE_REQUIRES_NEWER_PLATFORM(40021, "Feature requires a newer platform version"),
    INVALID_PUBLISH_REQUEST_UNSPECIFIED(40030, "Invalid publish request (unspecified)"),
    INVALID_PUBLISH_REQUEST_CLIENT_ID(40031, "Invalid publish request (invalid client-specified id)"),
    INVALID_PUBLISH_REQUEST_IMPERMISSIBLE_EXTRAS(40032, "Invalid publish request (impermissible extras field)"),
    RESERVED_FOR_ARTIFICIAL_ERROR(40099, "Reserved for artificial errors for testing"),
    UNAUTHORIZED(40100, "unauthorized"),
    INVALID_CREDENTIALS_AUTH(40101, "invalid credentials"),
    INCOMPATIBLE_CREDENTIALS(40102, "incompatible credentials"),
    INVALID_USE_OF_BASIC_AUTH(40103, "invalid use of Basic auth over non-TLS transport"),
    TIMESTAMP_NOT_CURRENT(40104, "timestamp not current"),
    NONCE_VALUE_REPLAYED(40105, "nonce value replayed"),
    UNABLE_TO_OBTAIN_CREDENTIALS(40106, "Unable to obtain credentials from given parameters"),
    ACCOUNT_DISABLED(40110, "account disabled"),
    ACCOUNT_RESTRICTED_CONNECTION_LIMIT_EXCEED(40111, "account restricted (connection limits exceeded)"),
    ACCOUNT_BLOCKED_MESSAGE_LIMIT_EXCEED(40112, "account blocked (message limits exceeded)"),
    ACCOUNT_BLOCKED(40113, "account blocked"),
    ACCOUNT_RESTRICTED(40114, "account restricted (channel limits exceeded)"),
    MAXIMUM_NUMBER_OF_PERMITTED_APPLICATION_EXCEEDED(40115, "maximum number of permitted applications exceeded"),
    APPLICATION_DISABLED(40120, "application disabled"),
    TOKEN_REVOCATION_NOT_ENABLED(40121, "token revocation not enabled for this application"),
    MAXIMUM_NUMBER_OF_RULES_EXCEED(40125, "maximum number of rules per application exceeded"),
    MAXIMUM_NUMBER_OF_NAMESPACES_EXCEED(40126, "maximum number of namespaces per application exceeded"),
    MAXIMUM_NUMBER_OF_KEYS_EXCEED(40127, "maximum number of keys per application exceeded"),
    KEY_ERROR_UNSPECIFIED(40130, "key error (unspecified)"),
    KEY_REVOKED(40131, "key revoked"),
    KEY_EXPIRED(40132, "key expired"),
    KEY_DISABLED(40133, "key disabled"),
    WRONG_KEY(40133, "wrong key; cannot revoke tokens with a different key to the one that issued them"),
    TOKEN_ERROR_UNSPECIFIED(40140, "token error (unspecified)"),
    TOKEN_REVOKED(40141, "token revoked"),
    TOKEN_EXPIRED(40142, "token expired"),
    TOKEN_UNRECOGNISED(40143, "token unrecognised"),
    INVALID_JWT_FORMAT(40144, "invalid JWT format"),
    INVALID_TOKEN_FORMAT(40145, "invalid token format"),
    CONNECTION_BLOCKED_LIMIT_EXCEED(40150, "connection blocked (limits exceeded)"),
    OPERATION_NOT_PERMITTED_WITH_PROVIDED_CAPABILITY(40160, "operation not permitted with provided capability"),
    OPERATION_NOT_PERMITTED_REQUIRES_IDENTIFIED_CLIENT(40161, "operation not permitted as it requires an identified client"),
    OPERATION_NOT_PERMITTED_WITH_TOKEN_REQUIRES_BASIC_AUTH(40162, "operation not permitted with a token, requires basic auth"),
    OPERATION_NOT_PERMITTED_KEY_NOT_PERMITTING_REVOCABLE_TOKENS(40163, "operation not permitted, key not marked as permitting revocable tokens"),
    ERROR_CLIENT_TOKEN_CALLBACK(40170, "error from client token callback"),
    NO_MEANS_TO_RENEW_AUTH_TOKEN(40171, "no means provided to renew auth token"),
    FORBIDDEN(40300, "forbidden"),
    ACCOUNT_DOES_NOT_PERMIT_TLS(40310, "account does not permit tls connection"),
    OPERATION_REQUIRES_TLS(40311, "operation requires tls connection"),
    APPLICATION_REQUIRES_AUTH(40320, "application requires authentication"),
    UNABLE_TO_ACTIVATE_ACCOUNT_UNSPECIFIED(40330, "unable to activate account due to placement constraint (unspecified)"),
    UNABLE_TO_ACTIVATE_ACCOUNT_INCOMPATIBLE_ENVIRONMENT(40331, "unable to activate account due to placement constraint (incompatible environment)"),
    UNABLE_TO_ACTIVATE_ACCOUNT_INCOMPATIBLE_SITE(40332, "unable to activate account due to placement constraint (incompatible site)"),
    NOT_FOUND(40400, "not found"),
    METHOD_NOT_ALLOWED(40500, "method not allowed"),
    PUSH_DEVICE_REGISTRATION_EXPIRED(41001, "push device registration expired"),
    UNPROCESSABLE_ENTITY(42200, "Unprocessable entity"),
    RATE_LIMIT_EXCEED_REQUEST_REJECTED(42910, "rate limit exceeded (nonfatal): request rejected (unspecified)"),
    MAX_PUBLISH_RATE_LIMIT_EXCEED(42911, "max per-connection publish rate limit exceeded (nonfatal): unable to publish message"),
    ONE_ACTIVE_CHANNEL_ITERATION_ALLOWED(42912, "there is a channel iteration call already in progress; only 1 active call is permitted to execute at any one time"),
    RATE_LIMIT_EXCEED_FATAL(42920, "rate limit exceeded (fatal)"),
    MAX_PUBLISH_RATE_LIMIT_EXCEED_CLOSING_CONNECTION(42921, "max per-connection publish rate limit exceeded (fatal); closing connection"),

    INTERNAL_ERROR(50000, "internal error"),
    INTERNAL_CHANNEL_ERROR(50001, "internal channel error"),
    INTERNAL_CONNECTION_ERROR(50002, "internal connection error"),
    TIMEOUT_ERROR(50003, "timeout error"),
    REQUEST_FAILED_OVERLOAD(50004, "Request failed due to overloaded instance"),
    SERVICE_UNAVAILABLE(50005, "Service unavailable (service temporarily in lockdown)"),
    EDGE_PROXY_UNKNOWN_INTERNAL_ERROR(50010, "Ably's edge proxy service has encountered an unknown internal error whilst processing the request"),
    EDGE_PROXY_INVALID_RESPONSE(50210, "Ably's edge proxy service received an invalid (bad gateway) response from the Ably platform"),
    EDGE_PROXY_UNAVAILABLE_RESPONSE(50310, "Ably's edge proxy service received a service unavailable response code from the Ably platform"),
    TRAFFIC_REDIRECTED_TO_BACKUP(50320, "Active Traffic Management: traffic for this cluster is being temporarily redirected to a backup service"),
    WRONG_CLUSTER_RETRY(50330, "request reached the wrong cluster; retry (used during dns changes for cluster migrations)"),
    EDGE_PROXY_SERVICE_TIMEOUT(50410, "Ably's edge proxy service timed out waiting for the Ably platform"),

    REACTOR_OPERATION_FAILED(70000, "reactor operation failed"),
    REACTOR_OPERATION_FAILED_POST_OP_FAIL(70001, "reactor operation failed (post operation failed)"),
    REACTOR_OPERATION_FAILED_UNEXPECTED_CODE(70002, "reactor operation failed (post operation returned unexpected code)"),
    REACTOR_OPERATION_FAILED_IN_FLIGHT_REQUEST_EXCEED(70003, "reactor operation failed (maximum number of concurrent in-flight requests exceeded)"),
    REACTOR_OPERATION_FAILED_INVALID_MESSAGE(70004, "reactor operation failed (invalid or unaccepted message contents)"),

    EXCHANGE_ERROR(71000, "Exchange error (unspecified)"),
    FORCED_REATTACHMENT_PERMISSION_CHANGE(71001, "Forced re-attachment due to permissions change"),
    EXCHANGE_PUBLISHER_ERROR(71100, "Exchange publisher error (unspecified)"),
    NO_PUBLISHER(71101, "No such publisher"),
    PUBLISHER_NOT_ENABLED(71102, "Publisher not enabled as an exchange publisher"),
    EXCHANGE_PRODUCT_ERROR(71200, "Exchange product error (unspecified)"),
    NO_PRODUCT(71201, "No such product"),
    PRODUCT_DISABLED(71202, "Product disabled"),
    NO_CHANNEL_IN_PRODUCT(71203, "No such channel in this product"),
    FORCED_REATTACHMENT_PRODUCT_REMAPPED(71204, "Forced re-attachment due to product being remapped to a different namespace"),
    EXCHANGE_SUBSCRIPTION_ERROR(71300, "Exchange subscription error (unspecified)"),
    SUBSCRIPTION_DISABLED(71301, "Subscription disabled"),
    REQUESTER_HAS_NO_SUBSCRIPTION(71302, "Requester has no subscription to this product"),
    CHANNEL_DOES_NOT_MATCH_FILTER(71303, "Channel does not match the channel filter specified in the subscription to this product"),

    CONNECTION_FAILED(80000, "connection failed"),
    CONNECTION_FAILED_NO_TRANSPORT(80001, "connection failed (no compatible transport)"),
    CONNECTION_SUSPENDED(80002, "connection suspended"),
    DISCONNECTED(80003, "disconnected"),
    ALREADY_CONNECTED(80004, "already connected"),
    INVALID_CONNECTION_ID_NO_REMOTE(80005, "invalid connection id (remote not found)"),
    FAIL_RECOVER_CONNECTION_MESSAGE_EXPIRED(80006, "unable to recover connection (messages expired)"),
    FAIL_RECOVER_CONNECTION_MESSAGE_LIMIT_EXCEED(80007, "unable to recover connection (message limit exceeded)"),
    FAIL_RECOVER_CONNECTION_EXPIRED(80008, "unable to recover connection (connection expired)"),
    CONNECTION_NOT_ESTABLISHED_NO_TRANSPORT(80009, "connection not established (no transport handle)"),
    INVALID_OPERATION_INVALID_TRANSPORT(80010, "invalid operation (invalid transport handle)"),
    FAIL_RECOVER_CONNECTION_INCOMPATIBLE_AUTH_PARAMS(80011, "unable to recover connection (incompatible auth params)"),
    FAIL_RECOVER_CONNECTION_INVALID_CONNECTION_SERIAL(80012, "unable to recover connection (invalid or unspecified connection serial)"),
    PROTOCOL_ERROR(80013, "protocol error"),
    CONNECTION_TIMEOUT(80014, "connection timed out"),
    INCOMPATIBLE_CONNECTION_PARAMS(80015, "incompatible connection parameters"),
    OPERATION_ON_SUPERSEDED_CONNECTION(80016, "operation on superseded connection"),
    CONNECTION_CLOSED(80017, "connection closed"),
    INVALID_CONNECTION_ID_BAD_FORMAT(80018, "invalid connection id (invalid format)"),
    CLIENT_AUTH_REQUEST_FAILED(80019, "client configured authentication provider request failed"),
    CONTINUITY_LOSS_SUBSCRIBE_RATE_EXCEED(80020, "continuity loss due to maximum subscribe message rate exceeded"),
    CREATE_CONNECTION_EXCEED(80021, "exceeded maximum permitted account-wide rate of creating new connections"),
    RESTRICTION_NOT_SATISFIED(80030, "client restriction not satisfied"),

    CHANNEL_OPERATION_FAILED(90000, "channel operation failed"),
    CHANNEL_OPERATION_FAILED_INVALID_STATE(90001, "channel operation failed (invalid channel state)"),
    CHANNEL_OPERATION_FAILED_EPOCH_EXPIRED(90002, "channel operation failed (epoch expired or never existed)"),
    CHANNEL_RECOVER_ERROR_MESSAGE_EXPIRED(90003, "unable to recover channel (messages expired)"),
    CHANNEL_RECOVER_ERROR_MESSAGE_LIMIT_EXCEED(90004, "unable to recover channel (message limit exceeded)"),
    CHANNEL_RECOVER_ERROR_NO_EPOCH(90005, "unable to recover channel (no matching epoch)"),
    CHANNEL_RECOVER_ERROR_UNBOUNDED_REQUEST(90006, "unable to recover channel (unbounded request)"),
    CHANNEL_RECOVER_ERROR_NO_RESPONSE(90007, "channel operation failed (no response from server)"),
    CHANNELS_PER_CONNECTION_EXCEED(90010, "maximum number of channels per connection/request exceeded"),
    CREATE_CHANNEL_EXCEED(90021, "exceeded maximum permitted account-wide rate of creating new channels"),
    CHANNEL_PRESENCE_ENTER_CLIENT_ID_ERROR(91000, "unable to enter presence channel (no clientId)"),
    CHANNEL_PRESENCE_ENTER_STATE_ERROR(91001, "unable to enter presence channel (invalid channel state)"),
    CHANNEL_PRESENCE_LEAVE_ERROR(91002, "unable to leave presence channel that is not entered"),
    CHANNEL_PRESENCE_ENTER_LIMIT_EXCEED(91003, "unable to enter presence channel (maximum member limit exceeded)"),
    CHANEL_PRESENCE_RE_ENTER_ERROR(91004, "unable to automatically re-enter presence channel"),
    PRESENCE_STATE_BAD_SYNC(91005, "presence state is out of sync"),
    MEMBER_LEFT_IMPLICITLY(91100, "member implicitly left presence channel (connection closed)");

    public final int code;
    public final String message;
    private static final Map<Integer, AblyErrors> BY_CODE = new HashMap<>();
    private static final Map<String, AblyErrors> BY_MESSAGE = new HashMap<>();

    static {
        for (AblyErrors e : values()) {
            BY_CODE.put(e.code, e);
            BY_MESSAGE.put(e.message, e);
        }
    }

    AblyErrors(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static AblyErrors valueOfCode(int code) {
        return BY_CODE.get(code);
    }

    public static AblyErrors valueOfMessage(String message) {
        return BY_MESSAGE.get(message);
    }
}
