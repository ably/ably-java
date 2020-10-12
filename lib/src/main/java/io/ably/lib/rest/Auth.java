package io.ably.lib.rest;

import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.ably.lib.http.HttpConstants;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.BaseMessage;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

/**
 * Token-generation and authentication operations for the Ably API.
 * See the Ably Authentication documentation for details of the
 * authentication methods available.
 *
 */
public class Auth {

    /**
     * Authentication methods
     */
    public enum AuthMethod {
        basic,
        token;
    }

    /**
     * Authentication options when instancing the Ably library
     */
    public static class AuthOptions {

        /**
         * A callback to call to obtain a signed TokenRequest,
         * TokenDetails or a token string. This enables a client
         * to obtain token requests or tokens from another entity,
         * so tokens can be renewed without the client requiring a
         * key
         */
        public TokenCallback authCallback;

        /**
         * A URL to query to obtain a signed TokenRequest,
         * TokenDetails or a token string. This enables a client
         * to obtain token request or token from another entity,
         * so tokens can be renewed without the client requiring
         * a key
         */
        public String authUrl;

        /**
         * TO3j7: authMethod: The HTTP verb to be used when a request
         * is made by the library to the authUrl. Defaults to GET,
         * supports GET and POST
         */
        public String authMethod;

        /**
         * Full Ably key string as obtained from dashboard.
         */
        public String key;

        /**
         * An authentication token issued for this application
         * against a specific key and {@link TokenParams}
         */
        public String token;

        /**
         * An authentication token issued for this application
         * against a specific key and {@link TokenParams}
         */
        public TokenDetails tokenDetails;

        /**
         * Headers to be included in any request made by the library
         * to the authURL.
         */
        public Param[] authHeaders;

        /**
         * Query params to be included in any request made by the library
         * to the authURL.
         */
        public Param[] authParams;

        /**
         * This may be set in instances that the library is to sign
         * token requests based on a given key. If true, the library
         * will query the Ably system for the current time instead of
         * relying on a locally-available time of day.
         */
        public boolean queryTime;

        /**
         * TO3j4: Use token authorization even if no clientId
         */
        public boolean useTokenAuth;

        /**
         * Default constructor
         */
        public AuthOptions() {}

        /**
         * Convenience constructor, to create an AuthOptions based
         * on the key string obtained from the application dashboard.
         * @param key the full key string as obtained from the dashboard
         * @throws AblyException
         */
        public AuthOptions(String key) throws AblyException {
            if (key == null) {
                throw AblyException.fromErrorInfo(new ErrorInfo("key string cannot be null", 40000, 400));
            }
            if(key.indexOf(':') > -1)
                this.key = key;
            else
                this.token = key;
        }

        /**
         * Stores the AuthOptions arguments as defaults for subsequent authorizations
         * with the exception of the attributes {@link AuthOptions#timestamp()} and
         * {@link AuthOptions#queryTime}
         * <p>
         * Spec: RSA10g
         * </p>
         */
        private AuthOptions storedValues() {
            AuthOptions result = new AuthOptions();
            result.key = this.key;
            result.authUrl = this.authUrl;
            result.authMethod = this.authMethod;
            result.authParams = this.authParams;
            result.authHeaders = this.authHeaders;
            result.token = this.token;
            result.tokenDetails = this.tokenDetails;
            result.authCallback = this.authCallback;
            return result;
        }

        /**
         * Create a new copy of object
         *
         * @return copied object
         */
        private AuthOptions copy() {
            AuthOptions result = new AuthOptions();
            result.key = this.key;
            result.authUrl = this.authUrl;
            result.authMethod = this.authMethod;
            result.authParams = this.authParams;
            result.authHeaders = this.authHeaders;
            result.token = this.token;
            result.tokenDetails = this.tokenDetails;
            result.authCallback = this.authCallback;
            result.queryTime = this.queryTime;
            return result;
        }
    }

    /**
     * A class providing details of a token and its associated metadata,
     * provided when the system successfully requests a token from the system.
     *
     */
    public static class TokenDetails {

        /**
         * The token itself
         */
        public String token;

        /**
         * The time (in millis since the epoch) at which this token expires.
         */
        public long expires;

        /**
         * The time (in millis since the epoch) at which this token was issued.
         */
        public long issued;

        /**
         * The capability associated with this token. See the Ably Authentication
         * documentation for details.
         */
        public String capability;

        /**
         * The clientId, if any, bound to this token. If a clientId is included,
         * then the token authenticates its bearer as that clientId, and the
         * token may only be used to perform operations on behalf of that clientId.
         */
        public String clientId;

        public TokenDetails() {}
        public TokenDetails(String token) { this.token = token; }

        /**
         * Convert a JSON response body to a TokenDetails.
         * Deprecated: use fromJson() instead
         * @param json
         * @return
         */
        @Deprecated
        public static TokenDetails fromJSON(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenDetails.class);
        }

        /**
         * Convert a JSON element response body to a TokenDetails.
         * Spec: TD7
         * @param json
         * @return
         */
        public static TokenDetails fromJson(String json) {
            return Serialisation.gson.fromJson(json, TokenDetails.class);
        }

        /**
         * Convert a JSON element response body to a TokenDetails.
         * @param json
         * @return
         */
        public static TokenDetails fromJsonElement(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenDetails.class);
        }

        /**
         * Convert a TokenDetails into a JSON object.
         */
        public JsonObject asJsonElement() {
            return (JsonObject)Serialisation.gson.toJsonTree(this);
        }

        /**
         * Convert a TokenDetails into a JSON string.
         */
        public String asJson() {
            return asJsonElement().toString();
        }

        /**
         * Check equality of a TokenDetails
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            TokenDetails details = (TokenDetails)obj;
            return equalNullableStrings(this.token, details.token) &
                    equalNullableStrings(this.capability, details.capability) &
                    equalNullableStrings(this.clientId, details.clientId) &
                    (this.issued == details.issued) &
                    (this.expires == details.expires);
        }

}

    /**
     * A class providing parameters of a token request.
     */
    public static class TokenParams {

        /**
         * Requested time to live for the token. If the token request
         * is successful, the TTL of the returned token will be less
         * than or equal to this value depending on application settings
         * and the attributes of the issuing key.
         *
         * 0 means Ably will set it to the default value.
         */
        public long ttl;

        /**
         * Capability of the token. If the token request is successful,
         * the capability of the returned token will be the intersection of
         * this capability with the capability of the issuing key.
         */
        public String capability;

        /**
         * A clientId to associate with this token. The generated token
         * may be used to authenticate as this clientId.
         */
        public String clientId;

        /**
         * The timestamp (in millis since the epoch) of this request.
         * Timestamps, in conjunction with the nonce, are used to prevent
         * token requests from being replayed.
         */
        public long timestamp;

        /**
         * Internal; convert a TokenParams to a collection of Params
         * @return
         */
        public Map<String, Param> asMap() {
            Map<String, Param> params = new HashMap<String, Param>();
            if(ttl > 0) params.put("ttl", new Param("ttl", String.valueOf(ttl)));
            if(capability != null) params.put("capability", new Param("capability", capability));
            if(clientId != null) params.put("clientId", new Param("clientId", clientId));
            if(timestamp > 0) params.put("timestamp", new Param("timestamp", String.valueOf(timestamp)));
            return params;
        }

        /**
         * Check equality of a TokenParams
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            TokenParams params = (TokenParams)obj;
            return (this.ttl == params.ttl) &
                    equalNullableStrings(this.capability, params.capability) &
                    equalNullableStrings(this.clientId, params.clientId) &
                    (this.timestamp == params.timestamp);
        }

        /**
         * Stores the TokenParams arguments as defaults for subsequent authorizations
         * with the exception of the attributes {@link TokenParams#timestamp}
         * <p>
         * Spec: RSA10g
         * </p>
         */
        private TokenParams storedValues() {
            TokenParams result = new TokenParams();
            result.ttl = this.ttl;
            result.capability = this.capability;
            result.clientId = this.clientId;
            return result;
        }

        /**
         * Create a new copy of object
         *
         * @return copied object
         */
        private TokenParams copy() {
            TokenParams result = new TokenParams();
            result.ttl = this.ttl;
            result.capability = this.capability;
            result.clientId = this.clientId;
            result.timestamp = this.timestamp;
            return result;
        }
    }

    /**
     * A class providing parameters of a token request.
     */
    public static class TokenRequest extends TokenParams {

        public TokenRequest() {}

        public TokenRequest(TokenParams params) {
            this.ttl = params.ttl;
            this.capability = params.capability;
            this.clientId = params.clientId;
            this.timestamp = params.timestamp;
        }

        /**
         * The keyName of the key against which this request is made.
         */
        public String keyName;

        /**
         * An opaque nonce string of at least 16 characters to ensure
         * uniqueness of this request. Any subsequent request using the
         * same nonce will be rejected.
         */
        public String nonce;

        /**
         * The Message Authentication Code for this request. See the Ably
         * Authentication documentation for more details.
         */
        public String mac;

        /**
         * Convert a JSON serialisation to a TokenParams.
         * Deprecated: use fromJson() instead
         * @param json
         * @return
         */
        @Deprecated
        public static TokenRequest fromJSON(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenRequest.class);
        }

        /**
         * Convert a parsed JSON response body to a TokenParams.
         * @param json
         * @return
         */
        public static TokenRequest fromJsonElement(JsonObject json) {
            return Serialisation.gson.fromJson(json, TokenRequest.class);
        }

        /**
         * Convert a string JSON response body to a TokenParams.
         * Spec: TE6
         * @param json
         * @return
         */
        public static TokenRequest fromJson(String json) {
            return Serialisation.gson.fromJson(json, TokenRequest.class);
        }

        /**
         * Convert a TokenParams into a JSON object.
         */
        public JsonObject asJsonElement() {
            JsonObject o = (JsonObject)Serialisation.gson.toJsonTree(this);
            if (this.ttl == 0) {
                o.remove("ttl");
            }
            if (this.capability != null && this.capability.isEmpty()) {
                o.remove("capability");
            }
            return o;
        }

        /**
         * Convert a TokenParams into a JSON string.
         */
        public String asJson() {
            return asJsonElement().toString();
        }

        /**
         * Check equality of a TokenRequest
         * @param obj
         */
        @Override
        public boolean equals(Object obj) {
            TokenRequest request = (TokenRequest)obj;
            return super.equals(obj) &
                    equalNullableStrings(this.keyName, request.keyName) &
                    equalNullableStrings(this.nonce, request.nonce) &
                    equalNullableStrings(this.mac, request.mac);
        }
    }

    /**
     * An interface implemented by a callback that provides either tokens,
     * or signed token requests, in response to a request with given token params.
     */
    public interface TokenCallback {
        Object getTokenRequest(TokenParams params) throws AblyException;
    }

    /**
     * The clientId for this library instance
     * Spec RSA7b
     */
    public String clientId;

    /**
     * Ensure valid auth credentials are present. This may rely in an already-known
     * and valid token, and will obtain a new token if necessary or explicitly
     * requested.
     * Authorization will use the parameters supplied on construction except
     * where overridden with the options supplied in the call.
     *
     * @param params
     * an object containing the request params:
     * - key:        (optional) the key to use; if not specified, the key
     *               passed in constructing the Rest interface may be used
     *
     * - ttl:        (optional) the requested life of any new token in ms. If none
     *               is specified a default of 1 hour is provided. The maximum lifetime
     *               is 24hours; any request exceeeding that lifetime will be rejected
     *               with an error.
     *
     * - capability: (optional) the capability to associate with the access token.
     *               If none is specified, a token will be requested with all of the
     *               capabilities of the specified key.
     *
     * - clientId:   (optional) a client Id to associate with the token
     *
     * - timestamp:  (optional) the time in ms since the epoch. If none is specified,
     *               the system will be queried for a time value to use.
     *
     * - queryTime   (optional) boolean indicating that the Ably system should be
     *               queried for the current time when none is specified explicitly.
     *
     * @param options
     */
    public TokenDetails authorize(TokenParams params, AuthOptions options) throws AblyException {
        /* Spec: RSA10g */
        if (options != null)
            this.authOptions = options.storedValues();
        if (params != null)
            this.tokenParams = params.storedValues();

        /* Spec: RSA10j */
        options = (options == null) ? this.authOptions : options.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        /* RSA10e (as clarified in PR https://github.com/ably/docs/pull/186 )
         * Use supplied token or tokenDetails if any. */
        if (authOptions.token != null) {
            authOptions.tokenDetails = new TokenDetails(authOptions.token);
        }
        TokenDetails tokenDetails;
        if(authOptions.tokenDetails != null) {
            tokenDetails = authOptions.tokenDetails;
            setTokenDetails(tokenDetails);
        } else {
            try {
                tokenDetails = assertValidToken(params, options, true);
            } catch (AblyException e) {
                /* Give AblyRealtime a chance to update its state and emit an event according to RSA4c */
                ably.onAuthError(e.errorInfo);
                throw e;
            }
        }
        ably.onAuthUpdated(tokenDetails.token, true);
        return tokenDetails;
    }

    /**
     * Alias of authorize() (0.9 RSA10l)
     */
    @Deprecated
    public TokenDetails authorise(TokenParams params, AuthOptions options) throws AblyException {
        Log.w(TAG, "authorise() is deprecated and will be removed in 1.0. Please use authorize() instead");
        return authorize(params, options);
    }

    /**
     * Make a token request. This will make a token request now, even if the library already
     * has a valid token. It would typically be used to issue tokens for use by other clients.
     * @param params : see {@link #authorize} for params
     * @param tokenOptions : see {@link #authorize} for options
     * @return the TokenDetails
     * @throws AblyException
     */
    public TokenDetails requestToken(TokenParams params, AuthOptions tokenOptions) throws AblyException {
        /* Spec: RSA8e */
        tokenOptions = (tokenOptions == null) ? this.authOptions : tokenOptions.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        /* Spec: RSA7d */
        if(params.clientId == null) {
            params.clientId = ably.options.clientId;
        }
        params.capability = Capability.c14n(params.capability);

        /* get the signed token request */
        TokenRequest signedTokenRequest;
        if(tokenOptions.authCallback != null) {
            Log.i("Auth.requestToken()", "using token auth with auth_callback");
            try {
                /* the callback can return either a signed token request, or a TokenDetails */
                Object authCallbackResponse = tokenOptions.authCallback.getTokenRequest(params);
                if(authCallbackResponse instanceof String)
                    return new TokenDetails((String)authCallbackResponse);
                if(authCallbackResponse instanceof TokenDetails)
                    return (TokenDetails)authCallbackResponse;
                if(authCallbackResponse instanceof TokenRequest)
                    signedTokenRequest = (TokenRequest)authCallbackResponse;
                else
                    throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authCallback response", 400, 40000));
            } catch(AblyException e) {
                throw AblyException.fromErrorInfo(e, new ErrorInfo("authCallback failed with an exception", 401, 80019));
            }
        } else if(tokenOptions.authUrl != null) {
            Log.i("Auth.requestToken()", "using token auth with auth_url");

            /* the auth request can return either a signed token request as a TokenParams, or a TokenDetails */
            Object authUrlResponse = null;
            try {
                HttpCore.ResponseHandler<Object> responseHandler = new HttpCore.ResponseHandler<Object>() {
                    @Override
                    public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                        if(error != null) {
                            throw AblyException.fromErrorInfo(error);
                        }
                        try {
                            String contentType = response.contentType;
                            byte[] body = response.body;
                            if(body == null || body.length == 0) {
                                return null;
                            }
                            if(contentType != null) {
                                if(contentType.startsWith("text/plain") || contentType.startsWith("application/jwt")) {
                                    /* assumed to be token string */
                                    String token = new String(body);
                                    return new TokenDetails(token);
                                }
                                if(!contentType.startsWith("application/json")) {
                                    throw AblyException.fromErrorInfo(new ErrorInfo("Unacceptable content type from auth callback", 406, 40170));
                                }
                            }
                            /* if not explicitly indicated, we will just assume it's JSON */
                            JsonElement json = Serialisation.gsonParser.parse(new String(body));
                            if(!(json instanceof JsonObject)) {
                                throw AblyException.fromErrorInfo(new ErrorInfo("Unexpected response type from auth callback", 406, 40170));
                            }
                            JsonObject jsonObject = (JsonObject)json;
                            if(jsonObject.has("issued")) {
                                /* we assume this is a token details */
                                return TokenDetails.fromJsonElement(jsonObject);
                            } else {
                                /* otherwise it's a signed token request */
                                return TokenRequest.fromJsonElement(jsonObject);
                            }
                        } catch(JsonParseException e) {
                            throw AblyException.fromErrorInfo(new ErrorInfo("Unable to parse response from auth callback", 406, 40170));
                        }
                    }
                };

                /* append all relevant params to token params */
                Map<String, Param> urlParams = null;
                URL authUrl = HttpUtils.parseUrl(authOptions.authUrl);
                String queryString = authUrl.getQuery();
                if(queryString != null && !queryString.isEmpty()) {
                    urlParams = HttpUtils.decodeParams(queryString);
                }
                Map<String, Param> tokenParams = params.asMap();
                if(tokenOptions.authParams != null) {
                    for(Param p : tokenOptions.authParams) {
                        /* (RSA8c2) TokenParams take precedence over any configured
                         * authParams when a name conflict occurs */
                        if(!tokenParams.containsKey(p.key)) {
                            tokenParams.put(p.key, p);
                        }
                    }
                }
                if (HttpConstants.Methods.POST.equals(tokenOptions.authMethod)) {
                    authUrlResponse = HttpHelpers.postUri(ably.httpCore, tokenOptions.authUrl, tokenOptions.authHeaders, HttpUtils.flattenParams(urlParams), HttpUtils.flattenParams(tokenParams), responseHandler);
                } else {
                    Map<String, Param> requestParams = (urlParams != null) ? HttpUtils.mergeParams(urlParams, tokenParams) : tokenParams;
                    authUrlResponse = HttpHelpers.getUri(ably.httpCore, tokenOptions.authUrl, tokenOptions.authHeaders, HttpUtils.flattenParams(requestParams), responseHandler);
                }
            } catch(AblyException e) {
                throw AblyException.fromErrorInfo(e, new ErrorInfo("authUrl failed with an exception", 401, 80019));
            }
            if(authUrlResponse == null) {
                throw AblyException.fromErrorInfo(null, new ErrorInfo("Empty response received from authUrl", 401, 80019));
            }
            if(authUrlResponse instanceof TokenDetails) {
                /* we're done */
                return (TokenDetails)authUrlResponse;
            }
            /* otherwise it's a signed token request */
            signedTokenRequest = (TokenRequest)authUrlResponse;
        } else if(tokenOptions.key != null) {
            Log.i("Auth.requestToken()", "using token auth with client-side signing");
            signedTokenRequest = createTokenRequest(params, tokenOptions);
        } else {
            throw AblyException.fromErrorInfo(new ErrorInfo("Auth.requestToken(): options must include valid authentication parameters", 400, 40106));
        }

        String tokenPath = "/keys/" + signedTokenRequest.keyName + "/requestToken";
        return HttpHelpers.postSync(ably.http, tokenPath, null, null, new HttpUtils.JsonRequestBody(signedTokenRequest.asJsonElement().toString()), new HttpCore.ResponseHandler<TokenDetails>() {
            @Override
            public TokenDetails handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                if(error != null) {
                    throw AblyException.fromErrorInfo(error);
                }
                try {
                    String jsonText = new String(response.body);
                    JsonObject json = (JsonObject)Serialisation.gsonParser.parse(jsonText);
                    return TokenDetails.fromJsonElement(json);
                } catch(JsonParseException e) {
                    throw AblyException.fromThrowable(e);
                }
            }
        }, false);
    }

    /**
     * Create a signed token request based on known credentials
     * and the given token params. This would typically be used if creating
     * signed requests for submission by another client.
     * @param params : see {@link #authorize} for params
     * @param options : see {@link #authorize} for options
     * @return the params augmented with the mac.
     * @throws AblyException
     */
    public TokenRequest createTokenRequest(TokenParams params, AuthOptions options) throws AblyException {
        /* Spec: RSA9h */
        options = (options == null) ? this.authOptions : options.copy();
        params = (params == null) ? this.tokenParams : params.copy();

        if(params.capability != null)
            params.capability = Capability.c14n(params.capability);
        TokenRequest request = new TokenRequest(params);

        String key = options.key;
        if(key == null)
            throw AblyException.fromErrorInfo(new ErrorInfo("No key specified", 401, 40101));

        String[] keyParts = key.split(":");
        if(keyParts.length != 2)
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid key specified", 401, 40101));

        String keyName = keyParts[0], keySecret = keyParts[1];
        if(request.keyName == null)
            request.keyName = keyName;
        else if(!request.keyName.equals(keyName))
            throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible keys specified", 401, 40102));

        /* expires */
        String ttlText = (request.ttl == 0) ? "" : String.valueOf(request.ttl);

        /* capability */
        String capabilityText = (request.capability == null) ? "" : request.capability;

        /* clientId */
        if (request.clientId == null) request.clientId = ably.options.clientId;
        String clientIdText = (request.clientId == null) ? "" : request.clientId;

        /* timestamp */
        if(request.timestamp == 0) {
            if(options.queryTime) {
                long oldNanoTimeDelta = nanoTimeDelta;
                long currentNanoTimeDelta = System.currentTimeMillis() - System.nanoTime()/(1000*1000);

                if (timeDelta != Long.MAX_VALUE) {
                    /* system time changed by more than 500ms since last time? */
                    if(Math.abs(oldNanoTimeDelta - currentNanoTimeDelta) > 500)
                        timeDelta = Long.MAX_VALUE;
                }

                if (timeDelta != Long.MAX_VALUE) {
                    request.timestamp = timestamp() + timeDelta;
                    nanoTimeDelta = currentNanoTimeDelta;
                } else {
                    request.timestamp = ably.time();
                    timeDelta = request.timestamp - timestamp();
                }
            }
            else {
                request.timestamp = timestamp();
            }
        }

        /* nonce */
        request.nonce = random();

        String signText
            = request.keyName + '\n'
            + ttlText + '\n'
            + capabilityText + '\n'
            + clientIdText + '\n'
            + request.timestamp + '\n'
            + request.nonce + '\n';

        request.mac = hmac(signText, keySecret);

        Log.i("Auth.getTokenRequest()", "generated signed request");
        return request;
    }

    /**
     * Get the authentication method for this library instance.
     * @return
     */
    public AuthMethod getAuthMethod() {
        return method;
    }

    /**
     * Get the credentials for HTTP basic auth, if available.
     * @return
     */
    public String getBasicCredentials() {
        return (method == AuthMethod.basic) ? basicCredentials : null;
    }

    /**
     * Get query params representing the current authentication method and credentials.
     * @return
     * @throws AblyException
     */
    public Param[] getAuthParams() throws AblyException {
        Param[] params = null;
        switch(method) {
        case basic:
            params = new Param[]{new Param("key", authOptions.key) };
            break;
        case token:
            assertValidToken();
            params = new Param[]{new Param("accessToken", getTokenDetails().token) };
            break;
        }
        return params;
    }

    /**
     * Get (a copy of) auth options currently set in this Auth.
     */
    public AuthOptions getAuthOptions() {
        return authOptions.copy();
    }

    /**
     * Renew auth credentials.
     * Will obtain a new token, even if we already have an apparently valid one.
     * Authorization will use the parameters supplied on construction.
     */
    public TokenDetails renew() throws AblyException {
        TokenDetails tokenDetails = assertValidToken(this.tokenParams, this.authOptions, true);
        ably.onAuthUpdated(tokenDetails.token, false);
        return tokenDetails;
    }

    public void onAuthError(ErrorInfo err) {
        /* we're only interested in token expiry errors */
        if(err.code >= 40140 && err.code < 40150)
            clearTokenDetails();
    }

    public static long timestamp() { return System.currentTimeMillis(); }

    /********************
     * internal
     ********************/

    /**
     * Private constructor.
     * @param ably
     * @param options
     * @throws AblyException
     */
    Auth(AblyBase ably, ClientOptions options) throws AblyException {
        this.ably = ably;
        authOptions = options;
        tokenParams = options.defaultTokenParams != null ?
                options.defaultTokenParams : new TokenParams();

        /* set clientId (spec Rsa7b1) */
        if(options.clientId != null) {
            if(options.clientId.equals(WILDCARD_CLIENTID)) {
                /* RSA7c */
                throw AblyException.fromErrorInfo(new ErrorInfo("Disallowed wildcard clientId in ClientOptions", 400, 40000));
            }
            /* RSC17 */
            setClientId(options.clientId);
            /* RSA7a4 */
            tokenParams.clientId = options.clientId;
        }

        /* decide default auth method (spec: RSA4) */
        if(authOptions.key != null) {
            if(options.clientId == null &&
                    !options.useTokenAuth &&
                    options.token == null &&
                    options.tokenDetails == null &&
                    options.authCallback == null &&
                    options.authUrl == null) {
                /* we have the key and do not need to authenticate the client,
                 * so default to using basic auth */
                Log.i("Auth()", "anonymous, using basic auth");
                this.method = AuthMethod.basic;
                basicCredentials = authOptions.key;
                setClientId(WILDCARD_CLIENTID);
                return;
            }
        }
        /* using token auth, but decide the method */
        this.method = AuthMethod.token;
        if(authOptions.token != null) {
            setTokenDetails(authOptions.token);
        }
        else if(authOptions.tokenDetails != null) {
            setTokenDetails(authOptions.tokenDetails);
        }

        if(authOptions.authCallback != null) {
            Log.i("Auth()", "using token auth with authCallback");
        } else if(authOptions.authUrl != null) {
            /* verify configured URL parses */
            HttpUtils.parseUrl(authOptions.authUrl);
            Log.i("Auth()", "using token auth with authUrl");
        } else if(authOptions.key != null) {
            Log.i("Auth()", "using token auth with client-side signing");
        } else if(tokenDetails != null) {
            Log.i("Auth()", "using token auth with supplied token only");
        } else {
            /* no means to authenticate (Spec: RSA14) */
            Log.e("Auth()", "no authentication parameters supplied");
            throw AblyException.fromErrorInfo(new ErrorInfo("No authentication parameters supplied", 400, 40000));
        }
    }

    public TokenDetails getTokenDetails() {
        Log.i("TokenAuth.getTokenDetails()", "");
        return tokenDetails;
    }

    public String getEncodedToken() {
        Log.i("TokenAuth.getEncodedToken()", "");
        return encodedToken;
    }

    private void setTokenDetails(String token) throws AblyException {
        Log.i("TokenAuth.setTokenDetails()", "");
        this.tokenDetails = new TokenDetails(token);
        this.encodedToken = Base64Coder.encodeString(token).replace("=", "");
    }

    private void setTokenDetails(TokenDetails tokenDetails) throws AblyException {
        Log.i("TokenAuth.setTokenDetails()", "");
        setClientId(tokenDetails.clientId);
        this.tokenDetails = tokenDetails;
        this.encodedToken = Base64Coder.encodeString(tokenDetails.token).replace("=", "");
    }

    private void clearTokenDetails() {
        Log.i("TokenAuth.clearTokenDetails()", "");
        this.tokenDetails = null;
        this.encodedToken = null;
        this.authHeader = null;
    }

    public TokenDetails assertValidToken() throws AblyException {
        return assertValidToken(tokenParams, authOptions, false);
    }

    private TokenDetails assertValidToken(TokenParams params, AuthOptions options, boolean force) throws AblyException {
        Log.i("Auth.assertValidToken()", "");
        if(tokenDetails != null) {
            if(!force && (tokenDetails.expires == 0 || tokenValid(tokenDetails))) {
                Log.i("Auth.assertValidToken()", "using cached token; expires = " + tokenDetails.expires);
                return tokenDetails;
            } else {
                /* expired, so remove */
                Log.i("Auth.assertValidToken()", "deleting expired token");
                clearTokenDetails();
            }
        }
        Log.i("Auth.assertValidToken()", "requesting new token");
        setTokenDetails(requestToken(params, options));
        return tokenDetails;
    }

    private boolean tokenValid(TokenDetails tokenDetails) {
        /* RSA4b1: only perform a local check for token validity if we have time sync with the server */
        return (timeDelta == Long.MAX_VALUE) || (tokenDetails.expires > serverTimestamp());
    }

    /**
     * Get the Authorization header, forcing the creation of a new token if requested
     * @param forceRenew
     * @return
     * @throws AblyException
     */
    public void assertAuthorizationHeader(boolean forceRenew) throws AblyException {
        if(authHeader != null && !forceRenew) {
            return;
        }
        if(getAuthMethod() == AuthMethod.basic) {
            authHeader = "Basic " + Base64Coder.encodeString(getBasicCredentials());
        } else {
            if (forceRenew) {
                renew();
            } else {
                assertValidToken();
            }
            authHeader = "Bearer " + getEncodedToken();
        }
    }

    public String getAuthorizationHeader() {
        return authHeader;
    }

    private static String random() { return String.format("%016d", (long)(Math.random() * 1E16)); }

    private static boolean equalNullableStrings(String one, String two) {
        return (one == null) ? (two == null) : one.equals(two);
    }

    private static String hmac(String text, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(Charset.forName("UTF-8")), "HmacSHA256"));
            return new String(Base64Coder.encode(mac.doFinal(text.getBytes(Charset.forName("UTF-8")))));
        } catch (GeneralSecurityException e) { Log.e("Auth.hmac", "Unexpected exception", e); return null; }
    }

    /**
     * Set the clientId, after first initialisation in the construction of the library
     * therefore an existing null value is significant - it means that ClientOptions.clientId
     * was null
     * @param clientId
     * @throws AblyException
     */
    public void setClientId(String clientId) throws AblyException {
        if(clientId == null) {
            /* do nothing - we received a token without a clientId */
            return;
        }

        if(this.clientId == null) {
            /* RSA12a, RSA12b, RSA7b2, RSA7b3, RSA7b4: the given clientId is now our clientId */
            this.clientId = clientId;
            this.ably.onClientIdSet(clientId);
            return;
        }
        /* now this.clientId != null */
        if(this.clientId.equals(clientId)) {
            /* this includes the wildcard case RSA7b4 */
            return;
        }
        if(WILDCARD_CLIENTID.equals(clientId)) {
            /* this signifies that the credentials permit the use of any specific clientId */
            return;
        }
        throw AblyException.fromErrorInfo(new ErrorInfo("Unable to set different clientId from that given in options", 401, 40101));
    }

    /**
     * Verify that a message, possibly containing a clientId,
     * is compatible with Auth.clientId if it is set
     * @param msg
     * @param allowNullClientId true if it is ok for there to be no resolved clientId
     * @param connected true if connected; if false it is ok for the library to be unidentified
     * @return the resolved clientId
     * @throws AblyException
     */
    public String checkClientId(BaseMessage msg, boolean allowNullClientId, boolean connected) throws AblyException {
        /* Check that the message doesn't contain the disallowed wildcard clientId
         * RTL6g3 */
        String msgClientId = msg.clientId;
        if(WILDCARD_CLIENTID.equals(msgClientId)) {
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid wildcard clientId specified in message", 400, 40000));
        }

        /* Check that any clientId given in the message is compatible with the library clientId */
        boolean undeterminedClientId = (clientId == null && !connected);
        if(msgClientId != null) {
            if(msgClientId.equals(clientId) || WILDCARD_CLIENTID.equals(clientId) || undeterminedClientId) {
                /* RTL6g4: be lenient checking against a null clientId if we're not connected */
                return msgClientId;
            }
            throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible clientId specified in message", 400, 40012));
        }

        if(clientId == null || clientId.equals(WILDCARD_CLIENTID)) {
            if(allowNullClientId || undeterminedClientId) {
                /* the message is sent with no clientId */
                return null;
            }
            /* this case only applies to presence, when allowNullClientId=false */
            throw AblyException.fromErrorInfo(new ErrorInfo("Invalid attempt to enter with no clientId", 400, 91000));
        }

        /* the message is sent with no explicit clientId, but implicitly has the library clientId */
        return clientId;
    }

    /**
     * Using time delta obtained before guess current server time
     */
    public long serverTimestamp() {
        long clientTime = timestamp();
        long delta = timeDelta;
        return delta != Long.MAX_VALUE ? clientTime + timeDelta : clientTime;
    }

    private static final String TAG = Auth.class.getName();
    private final AblyBase ably;
    private final AuthMethod method;
    private AuthOptions authOptions;
    private TokenParams tokenParams;
    private String basicCredentials;
    private TokenDetails tokenDetails;
    private String encodedToken;
    private String authHeader;

    /**
     * Time delta is server time minus client time, in milliseconds, MAX_VALUE if not obtained yet
     */
    private long timeDelta = Long.MAX_VALUE;
    /**
     * Time delta between System.nanoTime() and System.currentTimeMillis. If it changes significantly it
     * suggests device time/date has changed
     */
    private long nanoTimeDelta = System.currentTimeMillis() - System.nanoTime()/(1000*1000);

    public static final String WILDCARD_CLIENTID = "*";
    /**
     * For testing purposes we need method to clear cached timeDelta
     */
    public void clearCachedServerTime() {
        timeDelta = Long.MAX_VALUE;
    }
}
