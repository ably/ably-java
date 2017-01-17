/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package io.ably.lib.test.util;

import java.util.Map;
import java.util.Map.Entry;

import fi.iki.elonen.NanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.rest.Auth.TokenRequest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ErrorResponse;
import io.ably.lib.util.Serialisation;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class TokenServer extends NanoHTTPD {

	public TokenServer(AblyRest ably, int port) {
		super(port);
		this.ably = ably;
	}

	@Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String target = session.getUri();
        Map<String, String> params = session.getParms();
        Map<String, String> headers = session.getHeaders();

		if (!method.equals(Method.GET)) {
			return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not supported");
		}

		if(target.equals("/get-token")) {
			TokenParams tokenParams = params2TokenParams(params);
			try {
				TokenDetails token = ably.auth.requestToken(tokenParams, null);
				return json2Response(token);
			} catch (AblyException e) {
				e.printStackTrace();
				return error2Response(e.errorInfo);
			}
		}
		else if(target.equals("/get-token-request")) {
			TokenParams tokenParams = params2TokenParams(params);
			try {
				TokenRequest tokenRequest = ably.auth.createTokenRequest(tokenParams, null);
				return json2Response(tokenRequest.asJsonElement());
			} catch (AblyException e) {
				e.printStackTrace();
				return error2Response(e.errorInfo);
			}
		}
		else if(target.equals("/echo-params")) {
			return params2ErrorResponse(params, Response.Status.NOT_FOUND);
		}
		else if(target.equals("/echo-headers")) {
			return params2ErrorResponse(headers, Response.Status.NOT_FOUND);
		}
		else if(target.equals("/404")) {
			return error2Response(new ErrorInfo("Not found", 404, 0));
		}
		else {
			return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unexpected path: " + target);
		}
	}

	private static Response params2ErrorResponse(Map<String, String> params, Response.Status status) {
		StringBuilder builder = new StringBuilder();
		for(Entry<String, String> entry : params.entrySet()) {
			if(builder.length() != 0) builder.append('&');
			builder.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return error2Response(new ErrorInfo(builder.toString(), status.getRequestStatus(), 0));
	}

	private static TokenParams params2TokenParams(Map<String, String> params) {
		TokenParams tokenParams = new TokenParams();
		if(params.containsKey("client_id"))
			tokenParams.clientId = params.get("client_id");
		if(params.containsKey("timestamp"))
			tokenParams.timestamp = Long.valueOf(params.get("timestamp"));
		if(params.containsKey("ttl"))
			tokenParams.ttl = Long.valueOf(params.get("ttl"));
		if(params.containsKey("capability"))
			tokenParams.capability = params.get("capability");
		return tokenParams;
	}

	private static Response json2Response(Object obj) {
		return newFixedLengthResponse(Response.Status.OK, MIME_JSON, Serialisation.gson.toJson(obj));
	}

	private static Response.Status getStatus(int statusCode) {
		switch(statusCode) {
		case 200:
			return Response.Status.OK;
		case 400:
			return Response.Status.BAD_REQUEST;
		case 401:
			return Response.Status.UNAUTHORIZED;
		case 404:
			return Response.Status.NOT_FOUND;
		case 500:
		default:
			return Response.Status.INTERNAL_ERROR;
		}
	}

	private static Response error2Response(ErrorInfo errorInfo) {
		ErrorResponse err = new ErrorResponse();
		err.error = errorInfo;
		return newFixedLengthResponse(getStatus(errorInfo.statusCode), MIME_JSON, Serialisation.gson.toJson(err));
	}

	private final AblyRest ably;
	private static final String MIME_JSON = "application/json";	
}
