package io.ably.http;

import io.ably.rest.Auth;
import io.ably.rest.Auth.AuthOptions;
import io.ably.rest.Auth.TokenDetails;
import io.ably.rest.Auth.TokenParams;
import io.ably.types.AblyException;
import io.ably.util.Base64Coder;
import io.ably.util.Log;

import java.security.Principal;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.params.AuthParams;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;

/**
 * TokenAuth
 * Implements the bearer-token authentication scheme used for Ably.
 * Internal
 * @author paddy
 *
 */
public class TokenAuth extends RFC2617Scheme implements AuthSchemeFactory, CredentialsProvider {

	final static String SCHEME_NAME = "X-Ably-Token";

	static class TokenCredentials implements Credentials {
		@Override
		public String getPassword() { return b64Token; }

		@Override
		public Principal getUserPrincipal() { return null; }

		TokenCredentials(String token) { b64Token = Base64Coder.encodeString(token); }
		private final String b64Token;
	}

	public TokenAuth(Auth auth) {
		this.auth = auth;
	}

	/***************************
	 *    AuthSchemeFactory
	 ***************************/

	@Override
	public AuthScheme newInstance(HttpParams params) {
		return this;
	}

	/***************************
	 *     RFC2617Scheme
	 ***************************/

	@Override
	public void processChallenge(final Header header) throws MalformedChallengeException {
		super.processChallenge(header);
		if(header.getValue().contains("stale=\"true\"")) {
			Log.i("TokenAuth.processChallenge()", "Clearing stale cached token");
			clear();
		}
	}

	public String getSchemeName() {
		return SCHEME_NAME;
	}

	public boolean isComplete() {
		return tokenDetails != null && tokenValid(tokenDetails);
	}

	public boolean isConnectionBased() {
		return false;
	}

	@Override
	public Header authenticate(
			Credentials credentials,
			final HttpRequest request,
			final HttpContext context) throws AuthenticationException {

		if(request == null)
			throw new IllegalArgumentException("HTTP request may not be null");

		String charset = AuthParams.getCredentialCharset(request.getParams());
		return authenticate(credentials, charset, isProxy());
	}

	@Override
	public Header authenticate(Credentials credentials, HttpRequest request)
			throws AuthenticationException {
		if(credentials == null) {
			throw new IllegalArgumentException("Credentials may not be null");
		}
		if(request == null) {
			throw new IllegalArgumentException("HTTP request may not be null");
		}

		String charset = AuthParams.getCredentialCharset(request.getParams());
		return authenticate(credentials, charset, isProxy());
	}

	private Header authenticate(
			final Credentials credentials,
			final String charset,
			boolean proxy) {
		if(charset == null)
			throw new IllegalArgumentException("charset may not be null");

		if(credentials == null)
			throw new IllegalArgumentException("credentials may not be null");

		authorise();
		CharArrayBuffer buffer = new CharArrayBuffer(32);
		if (proxy) {
			buffer.append(AUTH.PROXY_AUTH_RESP);
		} else {
			buffer.append(AUTH.WWW_AUTH_RESP);
		}
		buffer.append(": Bearer ");
		buffer.append(tokenCredentials.getPassword());
		return new BufferedHeader(buffer);
	}

	public String toString() {
		return "io.ably.http.TokenAuth";
	}

	private void authorise() {
		try {
			authorise(null, null, false);
		} catch (AblyException e) {
			Log.e("TokenAuth.authorise()", "Unexpected exception", e);
		}
	}

	/***************************
	 *   CredentialsProvider
	 ***************************/

	@Override
	public void clear() {
		Log.i("TokenAuth.clear()", "");
		tokenDetails = null;
		tokenCredentials = null;
	}

	@Override
	public Credentials getCredentials(AuthScope authScope) {
		Log.i("TokenAuth.getCredentials()", "");
		authorise();
		return tokenCredentials;
	}

	@Override
	public void setCredentials(AuthScope authScope, Credentials credentials) {
		Log.i("TokenAuth.setCredentials()", "");
		tokenCredentials = (TokenCredentials)credentials;
	}

	/***************************
	 *       Internal
	 ***************************/

	public TokenDetails getTokenDetails() {
		Log.i("TokenAuth.getTokenDetails()", "");
		return tokenDetails;
	}

	public void setTokenDetails(TokenDetails tokenDetails) {
		Log.i("TokenAuth.setTokenDetails()", "");
		this.tokenDetails = tokenDetails;
		this.tokenCredentials = new TokenCredentials(tokenDetails.token);
	}

	public TokenDetails authorise(AuthOptions options, TokenParams params, boolean force) throws AblyException {
		Log.i("TokenAuth.authorise()", "");
		if(tokenDetails != null) {
			if(tokenDetails.expires == 0 || tokenValid(tokenDetails)) {
				if(!force) {
					Log.i("TokenAuth.authorise()", "using cached token; expires = " + tokenDetails.expires);
					return tokenDetails;
				}
			} else {
				/* expired, so remove */
				Log.i("TokenAuth.authorise()", "deleting expired token");
				clear();
			}
		}
		Log.i("TokenAuth.authorise()", "requesting new token");
		setTokenDetails(auth.requestToken(options, params));
		return tokenDetails;
	}

	private static boolean tokenValid(TokenDetails tokenDetails) {
		return tokenDetails.expires > Auth.timestamp();
	}

	private Auth auth;
	private TokenDetails tokenDetails;
	private TokenCredentials tokenCredentials;
}
