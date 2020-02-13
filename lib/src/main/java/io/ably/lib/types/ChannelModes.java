package io.ably.lib.types;

import java.util.Collections;
import java.util.HashSet;

public class ChannelModes extends HashSet<ChannelMode> {
	ChannelModes() { }

	public void add(ChannelMode... modes) {
		Collections.addAll(this, modes);
	}

	@Override
	public ChannelModes clone() {
		ChannelModes clonedModes = new ChannelModes();
		clonedModes.addAll(this);
		return clonedModes;
	}
}
