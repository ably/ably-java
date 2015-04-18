package io.ably.realtime;

import io.ably.types.ErrorInfo;

/**
 * An interface whereby a client may be notified of state changes for a channel.
 */
public interface ChannelStateListener {
	public void onChannelStateChanged(ChannelState state, ErrorInfo reason);

	static class Multicaster extends io.ably.util.Multicaster<ChannelStateListener> implements ChannelStateListener {
		@Override
		public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
			for(ChannelStateListener member : members)
				try {
					member.onChannelStateChanged(state, reason);
				} catch(Throwable t) {}
		}
	}

	static class Filter implements ChannelStateListener {
		@Override
		public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
			if(state == this.state)
				listener.onChannelStateChanged(state, reason);
		}
		Filter(ChannelState state, ChannelStateListener listener) { this.state = state; this.listener = listener; }
		ChannelState state;
		ChannelStateListener listener;
	}
}