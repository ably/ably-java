package io.ably.lib.push;

import android.content.Context;
import com.google.gson.JsonObject;
import io.ably.lib.http.*;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.*;

public class PushChannel {
	protected final Channel channel;
	protected final AblyRest rest;

	public PushChannel(Channel channel, AblyRest rest) {
		this.channel = channel;
		this.rest = rest;
	}

	public void subscribeClient() throws AblyException {
		subscribeClientImpl().sync();
	}

	public void subscribeClientAsync(CompletionListener listener) {
		subscribeClientImpl().async(new CompletionListener.ToCallback(listener));
	}

	protected Http.Request<Void> subscribeClientImpl() {
		JsonObject bodyJson = new JsonObject();
		try {
			bodyJson.addProperty("clientId", getClientId());
		} catch (AblyException e) {
			return rest.http.failedRequest(e);
		}

		return postSubscription(bodyJson);
	}

	public void subscribeDevice() throws AblyException {
		subscribeDeviceImpl().sync();
	}

	public void subscribeDeviceAsync(CompletionListener listener) {
		subscribeDeviceImpl().async(new CompletionListener.ToCallback(listener));
	}

	protected Http.Request<Void> subscribeDeviceImpl() {
		try {
			DeviceDetails device = getDevice();
			JsonObject bodyJson = new JsonObject();
			bodyJson.addProperty("deviceId", device.id);

			return postSubscription(bodyJson);
		} catch(AblyException e) {
			return rest.http.failedRequest(e);
		}
	}

	protected Http.Request<Void> postSubscription(JsonObject bodyJson) {
		bodyJson.addProperty("channel", channel.name);
		final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);

		return rest.http.request(new Http.Execute<Void>() {
			@Override
			public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
				Param[] params = null;
				if (rest.options.pushFullWait) {
					params = Param.push(params, "fullWait", "true");
				}
				http.post("/push/channelSubscriptions", rest.push.pushRequestHeaders(true), params, body, null, true, callback);
			}
		});
	}

	public void unsubscribeClient() throws AblyException {
		unsubscribeClientImpl().sync();
	}

	public void unsubscribeClientAsync(CompletionListener listener) {
		unsubscribeClientImpl().async(new CompletionListener.ToCallback(listener));
	}

	protected Http.Request<Void> unsubscribeClientImpl() {
		try {
			Param[] params = new Param[] { new Param("channel", channel.name), new Param("clientId", getClientId()) };
			return delSubscription(params);
		} catch(AblyException e) {
			return rest.http.failedRequest(e);
		}
	}

	public void unsubscribeDevice(Context context) throws AblyException {
		unsubscribeDeviceImpl().sync();
	}

	public void unsubscribeDeviceAsync(CompletionListener listener) {
		unsubscribeDeviceImpl().async(new CompletionListener.ToCallback(listener));
	}

	protected Http.Request<Void> unsubscribeDeviceImpl() {
		try {
			DeviceDetails device = getDevice();
			Param[] params = new Param[] { new Param("channel", channel.name), new Param("deviceId", device.id) };
			return delSubscription(params);
		} catch(AblyException e) {
			return rest.http.failedRequest(e);
		}
	}

	protected Http.Request<Void> delSubscription(Param[] params) {
		if (rest.options.pushFullWait) {
			params = Param.push(params, "fullWait", "true");
		}
		final Param[] finalParams = params;
		return rest.http.request(new Http.Execute<Void>() {
			@Override
			public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
				http.del("/push/channelSubscriptions", rest.push.pushRequestHeaders(true), finalParams, null, true, callback);
			}
		});
	}

	public PaginatedResult<Push.ChannelSubscription> listSubscriptions() throws AblyException {
		return listSubscriptions(new Param[] {});
	}

	public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Param[] params) throws AblyException {
		return listSubscriptionsImpl(params).sync();
	}

	public void listSubscriptionsAsync(Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
		listSubscriptionsAsync(new Param[] {}, callback);
	}

	public void listSubscriptionsAsync(Param[] params, Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
		listSubscriptionsImpl(params).async(callback);
	}

	protected BasePaginatedQuery.ResultRequest<Push.ChannelSubscription> listSubscriptionsImpl(Param[] params) {
		try {
			params = Param.set(params, "deviceId", getDevice().id);
		} catch(AblyException e) {
			return new BasePaginatedQuery.ResultRequest.Failed(e);
		}
		params = Param.set(params, "channel", channel.name);
		String clientId = rest.auth.clientId;
		if (clientId != null) {
			params = Param.set(params, "clientId", clientId);
		}
		params = Param.set(params, "concatFilters", "true");

		return new BasePaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", rest.push.pushRequestHeaders(true), params, Push.ChannelSubscription.httpBodyHandler).get();
	}

	protected String getClientId() throws AblyException {
		String clientId = getDevice().clientId;
		if (clientId == null) {
			throw AblyException.fromThrowable(new Exception("cannot subscribe with null client ID"));
		}
		return clientId;
	}

	protected DeviceDetails getDevice() throws AblyException {
		LocalDevice localDevice = rest.push.getActivationContext().getLocalDevice();
		if (localDevice == null || localDevice.deviceIdentityToken == null) {
			// Alternatively, we could store a queue of pending subscriptions in the
			// device storage. But then, in order to know if this subscription operation
			// succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
			// Arguably that encourages just ignoring any errors, and forcing you to listen
			// to the broadcast after push.activate has finished before subscribing is
			// more robust.
			throw AblyException.fromThrowable(new Exception("cannot use device before AblyRest.push.activate has finished"));
		}
		return localDevice;
	}
}
