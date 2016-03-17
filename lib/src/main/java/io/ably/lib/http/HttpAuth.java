package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Base64Coder;

class HttpAuth {

	HttpAuth(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public String getHeader(String authHeader, String method, String uri, byte[] requestBody) throws AblyException {
		int delimiterIdx = authHeader.indexOf(' ');
		if(delimiterIdx == -1) { throw AblyException.fromErrorInfo(new ErrorInfo("Invalid authenticate header", 40000, 400)); }
		String authScheme = authHeader.substring(0,  delimiterIdx).trim();
		String authDetails = authHeader.substring(delimiterIdx + 1).trim();
		if(authScheme.equals("Basic")) {
			return "Basic " + Base64Coder.encodeString(username + ':' + password);
		}
		if(authScheme.equals("Digest")) {
			return getDigestHeader(authDetails, method, uri, requestBody);
		}
		return null;
	}

	private String getDigestHeader(String detailsString, String method, String uri, byte[] requestBody) throws AblyException {
		HashMap<String, String> authFields = splitAuthFields(detailsString);

		String realm = authFields.get("realm");
		String HA1 = digestString(username + ':' + realm + ':' + password);

		String qop = authFields.get("qop");
		if(qop != null) {
			String[] qops = qop.split(",");
			qop = null;
			for(String candidateQop : qops) {
				if(requestBody != null && candidateQop.trim().equals("auth-int")) {
					qop = "auth-int";
					break;
				}
				if(candidateQop.trim().equals("auth")) {
					qop = "auth";
					break;
				}
			}
		}
		String HA2, HA3, nc = null, cnonce = null, nonce = authFields.get("nonce");
		if(qop == null) {
			HA2 = digestString(method + ':' + uri);
			HA3 = digestString(HA1 + ':' + nonce + ':' + HA2);
		} else if(qop.equals("auth")) {
			nc = String.format("%05d", ncCounter++);
			cnonce = getNonce();
			HA2 = digestString(method + ':' + uri);
			HA3 = digestString(HA1 + ':' + nonce + ':' + nc + ':' + cnonce + ':' + qop + ':' + HA2);
		} else {
			nc = String.format("%05d", ncCounter++);
			cnonce = getNonce();
			HA2 = digestString(method + ':' + uri + ':' + digestBytes(requestBody));
			HA3 = digestString(HA1 + ':' + nonce + ':' + nc + ':' + cnonce + ':' + qop + ':' + HA2);
		}

		String opaque = authFields.get("opaque");
		StringBuilder sb = new StringBuilder(128);
		sb.append("Digest ");
		sb.append("username"    ).append("=\"").append(username).append("\",");
		sb.append("realm"       ).append("=\"").append(realm   ).append("\",");
		sb.append("nonce"       ).append("=\"").append(nonce   ).append("\",");
		sb.append("uri"         ).append("=\"").append(uri     ).append("\",");

		if(qop != null) {
			sb.append("qop"     ).append("=\"").append(qop     ).append("\",");
			sb.append("nc"      ).append("=\"").append(nc      ).append("\",");
			sb.append("cnonce"  ).append("=\"").append(cnonce  ).append("\",");
		}

		if(opaque != null) {
			sb.append("response").append("=\"").append(HA3     ).append("\",");
			sb.append("opaque"  ).append("=\"").append(opaque  ).append("\"");
		} else {
			sb.append("response").append("=\"").append(HA3     ).append("\"");
		}
		return sb.toString();
	}

	private static HashMap<String, String> splitAuthFields(String detailsString) {
        HashMap<String, String> values = new HashMap<String, String>();
        String keyValueArray[] = detailsString.split(",");
        for (String keyval : keyValueArray) {
            if (keyval.contains("=")) {
                String key = keyval.substring(0, keyval.indexOf("="));
                String value = keyval.substring(keyval.indexOf("=") + 1);
                values.put(key.trim(), value.replaceAll("\"", "").trim());
            }
        }
        return values;
	}

	private static String digestBytes(byte[] buf) {
		md5.reset();
		md5.update(buf);
		byte[] ha1bytes = md5.digest();
		return bytesToHexString(ha1bytes);
	}

	private static String digestString(String text) {
		try{
			return digestBytes(text.getBytes("ISO-8859-1"));
		}
		catch(UnsupportedEncodingException e){ return null; }
	}

	private static final String HEX_LOOKUP = "0123456789abcdef";
	private static String bytesToHexString(byte[] bytes)
	{
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for(int i = 0; i < bytes.length; i++){
			sb.append(HEX_LOOKUP.charAt((bytes[i] & 0xF0) >> 4));
			sb.append(HEX_LOOKUP.charAt((bytes[i] & 0x0F) >> 0));
		}
		return sb.toString();
	}

    private static String getNonce() {
        String fmtDate = (new SimpleDateFormat("yyyy:MM:dd:hh:mm:ss")).format(new Date());
        Integer randomInt = (new Random(100000)).nextInt();
        return digestString(fmtDate + randomInt.toString());
    }

	private static MessageDigest md5 = null;
	static {
		try{
			md5 = MessageDigest.getInstance("MD5");
		}
		catch(NoSuchAlgorithmException e) {}
	}

	private static int ncCounter = 1;
	private final String username;
	private final String password;
}
