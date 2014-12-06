package io.ably.http;

import io.ably.rest.Auth;
import io.ably.types.AblyException;
import io.ably.util.Base64Coder;
import io.ably.util.Log;

import java.security.Principal;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.params.AuthParams;
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
class TokenAuth extends RFC2617Scheme implements AuthSchemeFactory {

	final static String SCHEME_NAME = "X-Ably-Token";

	static class TokenCredentials implements Credentials {
		@Override
		public String getPassword() { return b64Token; }
		@Override
		public Principal getUserPrincipal() { return null; }
		TokenCredentials(String token) { this.token = token; b64Token = Base64Coder.encodeString(token); }
		private final String token;
		private final String b64Token;
	}

	TokenAuth(Http http, Auth auth) {
		this.http = http;
		this.auth = auth;
	}

	@Override
	public AuthScheme newInstance(HttpParams params) {
		return new TokenAuth(http, auth);
	}

	/** Whether the basic authentication process is complete */
	private boolean complete;

	@Override
	public void processChallenge(final Header header) throws MalformedChallengeException {
		super.processChallenge(header);
		if(http.credentials == null) {
			try {
				auth.authorise(null, null, false);
			} catch (AblyException e) {
				Log.e("TokenAuth.processChallenge()", "Unexpected exception from authorise()", e);
			}
		}
		this.complete = true;
	}

	public String getSchemeName() {
		return SCHEME_NAME;
	}

	public boolean isComplete() {
		return complete;
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

	static Header authenticate(
			final Credentials credentials,
			final String charset,
			boolean proxy) {
		if(charset == null)
			throw new IllegalArgumentException("charset may not be null");

		if(credentials == null)
			throw new IllegalArgumentException("credentials may not be null");

		CharArrayBuffer buffer = new CharArrayBuffer(32);
		if (proxy) {
			buffer.append(AUTH.PROXY_AUTH_RESP);
		} else {
			buffer.append(AUTH.WWW_AUTH_RESP);
		}
		buffer.append(": Bearer ");
		buffer.append(credentials.getPassword());
		return new BufferedHeader(buffer);
	}

	@Override
	public Header authenticate(Credentials credentials, HttpRequest request)
			throws AuthenticationException {
		if (credentials == null) {
			throw new IllegalArgumentException("Credentials may not be null");
		}
		if (request == null) {
			throw new IllegalArgumentException("HTTP request may not be null");
		}

		String charset = AuthParams.getCredentialCharset(request.getParams());
		return authenticate(credentials, charset, isProxy());
	}

	public String toString() {
		return "io.ably.http.TokenAuth";
	}

	private Http http;
	private Auth auth;
}
