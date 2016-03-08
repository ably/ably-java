package io.ably.lib.rest;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.ably.lib.http.Http;
import io.ably.lib.http.Http.ResponseHandler;
import io.ably.lib.http.TokenAuth;
import io.ably.lib.types.AblyException;
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
		 * When true, indicates that a new token should be requested
		 */
		public boolean force;

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
		 * Default constructor
		 */
		public AuthOptions() {}

		/**
		 * Convenience constructor, to create an AuthOptions based
		 * on the key string obtained from the application dashboard.
		 * @param key: the full key string as obtained from the dashboard
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
		 * Internal
		 */
		public AuthOptions merge(AuthOptions defaults) {
			if(authCallback == null) authCallback = defaults.authCallback;
			if(authUrl == null) authUrl = defaults.authUrl;
			if(key == null) key = defaults.key;
			if(authHeaders == null) authHeaders = defaults.authHeaders;
			if(authParams == null) authParams = defaults.authParams;
			queryTime = queryTime & defaults.queryTime;
			return this;
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
		 * Internal; convert a JSON response body to a TokenDetails.
		 * @param json
		 * @return
		 */
		public static TokenDetails fromJSON(JsonObject json) {
			return Serialisation.gson.fromJson(json, TokenDetails.class);
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
		public List<Param> asParams() {
			List<Param> params = new ArrayList<Param>();
			if(ttl > 0) params.add(new Param("ttl", String.valueOf(ttl)));
			if(capability != null) params.add(new Param("capability", capability));
			if(clientId != null) params.add(new Param("client_id", clientId));
			if(timestamp > 0) params.add(new Param("timestamp", String.valueOf(timestamp)));
			return params;
		}
	}

	/**
	 * A class providing parameters of a token request.
	 */
	public static class TokenRequest extends TokenParams {

		TokenRequest() {}

		TokenRequest(TokenParams params) {
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
		 * Internal; convert a JSON response body to a TokenParams.
		 * @param json
		 * @return
		 */
		public static TokenRequest fromJSON(JsonObject json) {
			return Serialisation.gson.fromJson(json, TokenRequest.class);
		}

		/**
		 * Internal; convert a TokenParams into a JSON object.
		 */
		public JsonObject asJSON() {
			return (JsonObject)Serialisation.gson.toJsonTree(this);
		}
	}

	/**
	 * An interface implemented by a callback that provides either tokens,
	 * or signed token requests, in response to a request with given token params.
	 */
	public interface TokenCallback {
		public Object getTokenRequest(TokenParams params) throws AblyException;
	}

	/**
	 * Ensure valid auth credentials are present. This may rely in an already-known
	 * and valid token, and will obtain a new token if necessary or explicitly
	 * requested.
	 * Authorisation will use the parameters supplied on construction except
	 * where overridden with the options supplied in the call.
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
	 * @param callback (err, tokenDetails)
	 */
	public TokenDetails authorise(AuthOptions options, TokenParams params) throws AblyException {
		return tokenAuth.authorise(options, params);
	}

	/**
	 * Make a token request. This will make a token request now, even if the library already
	 * has a valid token. It would typically be used to issue tokens for use by other clients.
	 * @param params : see {@link #authorise} for params
	 * @param options : see {@link #authorise} for options
	 * @return: the TokenDetails
	 * @throws AblyException
	 */
	public TokenDetails requestToken(TokenParams params, AuthOptions options) throws AblyException {
		/* merge supplied options with the already-known options */
		final AuthOptions tokenOptions = (options == null) ? authOptions : options.merge(authOptions);

		/* set up the request params */
		if(params == null) params = new TokenParams();
		if(params.clientId == null)
			params.clientId = ably.clientId;

		if(params.capability != null)
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
					throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authCallback response", 40000, 400));
			} catch(AblyException e) {
				/* the auth callback threw an error */
				ErrorInfo errorInfo = e.errorInfo;
				if(errorInfo.code == 0) errorInfo.code = 40170;
				if(errorInfo.statusCode == 0) errorInfo.statusCode = 401;
				throw e;
			}
		} else if(tokenOptions.authUrl != null) {
			Log.i("Auth.requestToken()", "using token auth with auth_url");
			/* append any custom params to token params */
			List<Param> tokenParams = params.asParams();
			if(tokenOptions.authParams != null)
				tokenParams.addAll(Arrays.asList(tokenOptions.authParams));
			Param[] requestParams = tokenParams.toArray(new Param[tokenParams.size()]);

			/* the auth request can return either a signed token request as a TokenParams, or a TokenDetails */
			Object authUrlResponse = null;
			try {
				authUrlResponse = ably.http.getUri(tokenOptions.authUrl, tokenOptions.authHeaders, requestParams, new ResponseHandler<Object>() {
					@Override
					public Object handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
						try {
							if(contentType != null) {
								if(contentType.startsWith("text/plain")) {
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
								return TokenDetails.fromJSON(jsonObject);
							} else {
								/* otherwise it's a signed token request */
								return TokenRequest.fromJSON(jsonObject);
							}
						} catch(JsonParseException e) {
							throw AblyException.fromErrorInfo(new ErrorInfo("Unable to parse response from auth callback", 406, 40170));
						}
					}
				});
			} catch(AblyException e) {
				/* the auth url request returned an error, or there was an error processing the response */
				ErrorInfo errorInfo = e.errorInfo;
				if(errorInfo.code == 0) errorInfo.code = 40170;
				if(errorInfo.statusCode == 0) errorInfo.statusCode = 401;
				throw e;
			}
			if(authUrlResponse instanceof TokenDetails) {
				/* we're done */
				return (TokenDetails)authUrlResponse;
			}
			/* otherwise it's a signed token request */
			signedTokenRequest = (TokenRequest)authUrlResponse;
		} else if(tokenOptions.key != null) {
			Log.i("Auth.requestToken()", "using token auth with client-side signing");
			signedTokenRequest = createTokenRequest(tokenOptions, params);
		} else {
			throw AblyException.fromErrorInfo(new ErrorInfo("Auth.requestToken(): options must include valid authentication parameters", 400, 40000));
		}

		String tokenPath = "/keys/" + signedTokenRequest.keyName + "/requestToken";
		return ably.http.post(tokenPath, tokenOptions.authHeaders, tokenOptions.authParams, new Http.JSONRequestBody(signedTokenRequest.asJSON().toString()), new ResponseHandler<TokenDetails>() {
			@Override
			public TokenDetails handleResponse(int statusCode, String contentType, Collection<String> linkHeaders, byte[] body) throws AblyException {
				try {
					String jsonText = new String(body);
					JsonObject json = (JsonObject)Serialisation.gsonParser.parse(jsonText);
					return TokenDetails.fromJSON(json);
				} catch(JsonParseException e) {
					throw AblyException.fromThrowable(e);
				}
			}
		});
	}

	/**
	 * Create a signed token request based on known credentials
	 * and the given token params. This would typically be used if creating
	 * signed requests for submission by another client.
	 * @param options: see {@link #authorise} for options
	 * @param params: see {@link #authorise} for params
	 * @return: the params augmented with the mac.
	 * @throws AblyException
	 */
	public TokenRequest createTokenRequest(AuthOptions options, TokenParams params) throws AblyException {
		if(options == null) options = this.authOptions;
		else options.merge(this.authOptions);
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
		if (request.clientId == null) request.clientId = ably.clientId;
		String clientIdText = (request.clientId == null) ? "" : request.clientId;

		/* timestamp */
		if(request.timestamp == 0) {
			if(options.queryTime)
				request.timestamp = ably.time();
			else
				request.timestamp = timestamp();
		}

		/* nonce */
		request.nonce = random();

		String signText
		=	request.keyName + '\n'
		+	ttlText + '\n'
		+	capabilityText + '\n'
		+	clientIdText + '\n'
		+	request.timestamp + '\n'
		+	request.nonce + '\n';

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
			authorise(null, null);
			params = new Param[]{new Param("access_token", tokenAuth.getTokenDetails().token) };
			break;
		}
		return params;
	}

	public TokenAuth getTokenAuth() {
		return tokenAuth;
	}

	public void onAuthError(ErrorInfo err) {
		/* we're only interested in token expiry errors */
		if(err.code == 40140)
			tokenAuth.clear();
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
	Auth(AblyRest ably, ClientOptions options) throws AblyException {
		this.ably = ably;
		authOptions = options;

		/* decide default auth method */
		if(authOptions.key != null) {
			if(options.clientId == null) {
				/* we have the key and do not need to authenticate the client,
				 * so default to using basic auth */
				Log.i("Auth()", "anonymous, using basic auth");
				this.method = AuthMethod.basic;
				basicCredentials = authOptions.key;
				return;
			}
		}
		/* using token auth, but decide the method */
		this.method = AuthMethod.token;
		this.tokenAuth = new TokenAuth(this);
		if(authOptions.token != null)
			authOptions.tokenDetails = new TokenDetails(authOptions.token);
		if(authOptions.tokenDetails != null)
			tokenAuth.setTokenDetails(authOptions.tokenDetails);

		if(authOptions.authCallback != null) {
			Log.i("Auth()", "using token auth with authCallback");
		} else if(authOptions.authUrl != null) {
			Log.i("Auth()", "using token auth with authUrl");
		} else if(authOptions.key != null) {
			Log.i("Auth()", "using token auth with client-side signing");
		} else if(authOptions.tokenDetails != null) {
			Log.i("Auth()", "using token auth with supplied token only");
		} else {
			/* this is not a hard error - but any operation that requires
			 * authentication will fail */
			Log.i("Auth()", "no authentication parameters supplied");
		}
	}

	private static String random() { return String.format("%016d", (long)(Math.random() * 1E16)); }
	
	private static final String hmac(String text, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(), "HmacSHA256"));
			return new String(Base64Coder.encode(mac.doFinal(text.getBytes())));
		} catch (GeneralSecurityException e) { Log.e("Auth.hmac", "Unexpected exception", e); return null; }
	}

	private final AblyRest ably;
	private final AuthMethod method;
	private final AuthOptions authOptions;
	private String basicCredentials;
	private TokenAuth tokenAuth;
}
