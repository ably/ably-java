package io.ably.rest;

import io.ably.http.Http;
import io.ably.http.Http.ResponseHandler;
import io.ably.http.TokenAuth;
import io.ably.types.*;
import io.ably.util.Base64Coder;
import io.ably.util.JSONHelpers;
import io.ably.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
		 * A callback to call to obtain a signed token request.
		 * This enables a client to obtain token requests from
		 * another entity, so tokens can be renewed without the
		 * client requiring access to keys.
		 */
		public TokenCallback authCallback;

		/**
		 * A URL to queryto obtain a signed token request.
		 * This enables a client to obtain token requests from
		 * another entity, so tokens can be renewed without the
		 * client requiring access to keys.
		 */
		public String authUrl;

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
				throw new AblyException("key string cannot be null", 40000, 400);
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
		public static TokenDetails fromJSON(JSONObject json) {
			TokenDetails details = new TokenDetails();
			details.token = JSONHelpers.getString(json, "token");
			details.expires = json.optLong("expires");
			details.issued = json.optLong("issued");
			details.capability = JSONHelpers.getString(json, "capability");
			details.clientId = JSONHelpers.getString(json, "clientId");
			return details;
		}

		/**
		 * Internal; convert a TokenDetails to JSON
		 * @return
		 * @throws JSONException
		 */
		public JSONObject asJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("token", token);
			json.put("expires", expires);
			json.put("issued", issued);
			json.put("capability", capability);
			json.put("clientId", clientId);
			return json;
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
		public static TokenRequest fromJSON(JSONObject json) {
			TokenRequest params = new TokenRequest();
			params.keyName = json.optString("keyName");
			params.ttl = json.optLong("ttl");
			params.capability = JSONHelpers.getString(json, "capability");
			params.clientId = JSONHelpers.getString(json, "clientId");
			params.timestamp = json.optLong("timestamp");
			params.nonce = JSONHelpers.getString(json, "nonce");
			params.mac = JSONHelpers.getString(json, "mac");
			return params;
		}

		/**
		 * Internal; convert a TokenParams into a JSON object.
		 */
		public JSONObject asJSON() {
			JSONObject json = new JSONObject();
			try {
				if(keyName != null) json.put("keyName", keyName);
				if(ttl != 0) json.put("ttl", ttl);
				if(capability != null) json.put("capability", capability);
				if(clientId != null) json.put("clientId", clientId);
				if(timestamp != 0) json.put("timestamp", timestamp);
				if(nonce != null) json.put("nonce", nonce);
				if(mac != null) json.put("mac", mac);
				return json;
			} catch (JSONException e) {
				return null;
			}
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
	 * - force       (optional) boolean indicating that a new token should be requested,
	 *               even if a current token is still valid.
	 *
	 * @param callback (err, tokenDetails)
	 */
	public TokenDetails authorise(AuthOptions options, TokenParams params, boolean force) throws AblyException {
		return tokenAuth.authorise(options, params, force);
	}

	/**
	 * Make a token request. This will make a token request now, even if the library already
	 * has a valid token. It would typically be used to issue tokens for use by other clients.
	 * @param options: see {@link #authorise} for options
	 * @param params: see {@link #authorise} for params
	 * @return: the TokenDetails
	 * @throws AblyException
	 */
	public TokenDetails requestToken(AuthOptions options, TokenParams params) throws AblyException {
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
					throw new AblyException("Invalid authCallback response", 40000, 400);
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
			JSONObject authUrlResponse = null;
			try {
				authUrlResponse = (JSONObject)ably.http.getUri(tokenOptions.authUrl, tokenOptions.authHeaders, requestParams, new ResponseHandler() {
					@Override
					public Object handleResponse(int statusCode, String contentType, String[] linkHeaders, byte[] body) throws AblyException {
						if(contentType != null && !contentType.startsWith("application/json"))
							throw new AblyException("Unacceptable content type from auth callback", 406, 40170);

						try {
							return new JSONObject(new String(body));
						} catch(JSONException je) {
							throw new AblyException("Unable to parse response from auth callback", 400, 40170);
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
			if(authUrlResponse.has("issued")) {
				/* we assume this is a token */
				return TokenDetails.fromJSON(authUrlResponse);
			}
			/* otherwise it's a signed token request */
			signedTokenRequest = TokenRequest.fromJSON(authUrlResponse);
		} else if(tokenOptions.key != null) {
			Log.i("Auth.requestToken()", "using token auth with client-side signing");
			signedTokenRequest = createTokenRequest(tokenOptions, params);
		} else {
			throw new AblyException("Auth.requestToken(): options must include valid authentication parameters", 400, 40000);
		}

		String tokenPath = "/keys/" + signedTokenRequest.keyName + "/requestToken";
		return (TokenDetails)ably.http.post(tokenPath, tokenOptions.authHeaders, tokenOptions.authParams, new Http.JSONRequestBody(signedTokenRequest.asJSON().toString()), new ResponseHandler() {
			@Override
			public Object handleResponse(int statusCode, String contentType, String[] linkHeaders, byte[] body) throws AblyException {
				JSONObject json;
				try {
					json = new JSONObject(new String(body));
				} catch (JSONException e) {
					throw AblyException.fromThrowable(e);
				}
				return TokenDetails.fromJSON(json);
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
			throw new AblyException("No key specified", 401, 40101);

		String[] keyParts = key.split(":");
		if(keyParts.length != 2)
			throw new AblyException("Invalid key specified", 401, 40101);

		String keyName = keyParts[0], keySecret = keyParts[1];
		if(request.keyName == null)
			request.keyName = keyName;
		else if(!request.keyName.equals(keyName))
			throw new AblyException("Incompatible keys specified", 401, 40102);

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
			authorise(null, null, false);
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
