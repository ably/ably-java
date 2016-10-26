package io.ably.lib.http;

import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.AuthOptions;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.types.AblyException;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;

/**
 * TokenAuth
 * Implements the bearer-token authentication scheme used for Ably.
 * Internal
 * @author paddy
 *
 */
public class TokenAuth {
	public TokenAuth(Auth auth) {
		this.auth = auth;
	}

	public TokenDetails getTokenDetails() {
		Log.i("TokenAuth.getTokenDetails()", "");
		return tokenDetails;
	}

	public String getEncodedToken() {
		Log.i("TokenAuth.getEncodedToken()", "");
		return encodedToken;
	}

	public void setTokenDetails(TokenDetails tokenDetails) {
		Log.i("TokenAuth.setTokenDetails()", "");
		this.tokenDetails = tokenDetails;
		this.encodedToken = Base64Coder.encodeString(tokenDetails.token).replace("=", "");
	}

	public TokenDetails authorize(TokenParams params, AuthOptions options, boolean force) throws AblyException {
		Log.i("TokenAuth.authorize()", "");
		if(tokenDetails != null) {
			if(tokenDetails.expires == 0 || tokenValid(tokenDetails)) {
				if (!force) {
					Log.i("TokenAuth.authorize()", "using cached token; expires = " + tokenDetails.expires);
					return tokenDetails;
				}
			} else {
				/* expired, so remove */
				Log.i("TokenAuth.authorize()", "deleting expired token");
				clear();
			}
		}
		Log.i("TokenAuth.authorize()", "requesting new token");
		setTokenDetails(auth.requestToken(params, options));
		return tokenDetails;
	}

	public void clear() {
		tokenDetails = null;
		encodedToken = null;
	}

	private static boolean tokenValid(TokenDetails tokenDetails) {
		return tokenDetails.expires > Auth.timestamp();
	}

	private Auth auth;
	private TokenDetails tokenDetails;
	private String encodedToken;
}
