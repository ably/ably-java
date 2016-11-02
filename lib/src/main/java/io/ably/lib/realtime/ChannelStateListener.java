package io.ably.lib.realtime;

import io.ably.lib.types.ErrorInfo;

/**
 * An interface whereby a client may be notified of state changes for a channel.
 */
public interface ChannelStateListener {

	public void onChannelStateChanged(ChannelStateChange stateChange);

	/**
	 * Channel state change. See Ably Realtime API documentation for more details.
	 */
	public class ChannelStateChange {
		/* (TH2) The ChannelStateChange object contains the current state in
		 * attribute current, the previous state in attribute previous. */
		final public ChannelState current;
		final public ChannelState previous;
		/* (TH3) If the channel state change includes error information, then
		 * the reason attribute will contain an ErrorInfo object describing the
		 * reason for the error. */
		final public ErrorInfo reason;
		/* (TH4) The ChannelStateChange object contains an attribute resumed which
		 * in combination with an ATTACHED state, indicates whether the channel
		 * attach successfully resumed its state following the connection being
		 * resumed or recovered. If resumed is true, then the attribute indicates
		 * that the attach within Ably successfully recovered the state for the
		 * channel, and as such there is no loss of message continuity. In all
		 * other cases, resumed is false, and may be accompanied with a "channel
		 * state change error reason". */
		final boolean resumed;

		ChannelStateChange(ChannelState current, ChannelState previous, ErrorInfo reason, boolean resumed) {
			this.current = current;
			this.previous = previous;
			this.reason = reason;
			this.resumed = resumed;
		}
	}

	static class Multicaster extends io.ably.lib.util.Multicaster<ChannelStateListener> implements ChannelStateListener {
		@Override
		public void onChannelStateChanged(ChannelStateChange stateChange) {
			for(ChannelStateListener member : members)
				try {
					member.onChannelStateChanged(stateChange);
				} catch(Throwable t) {}
		}
	}

	static class Filter implements ChannelStateListener {
		@Override
		public void onChannelStateChanged(ChannelStateChange stateChange) {
			if(stateChange.current == this.state)
				listener.onChannelStateChanged(stateChange);
		}
		Filter(ChannelState state, ChannelStateListener listener) { this.state = state; this.listener = listener; }
		ChannelState state;
		ChannelStateListener listener;
	}
}
