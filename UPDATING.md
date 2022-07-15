# Upgrade / Migration Guide

## Version 1.x to 2.x

### API changes

In order to achieve the [Modularization](#modularization), dividing the project into separate modules, a refactor was necessary which has created the following API changes:

#### Channels moved

The `Channels` interface that's used to access realtime and rest channels has been moved from `AblyBase` to a separate file.
Because it's mainly accessed from either `AblyRest` or `AblyRealtime` for most users no changes would be required. 
For users who referenced this interface, only a change in the import statement would be required:

`io.ably.core.rest.AblyBase.Channels` -> `io.ably.core.types.Channels`

#### ChannelBase renamed

The `ChannelBase` was an internal, abstract type used to share channel logic between Java and Android channels. Due to
the project structure it was exposed, so it is possible that some users have been using it in their applications. In
this case, they will need to update the code referencing the old `ChannelBase` depending on whether they are using rest
or realtime channels.

For the rest channel:
`io.ably.core.rest.ChannelBase` -> `io.ably.core.rest.RestChannelBase`

For the realtime channel:
`io.ably.core.realtime.ChannelBase` -> `io.ably.core.realtime.RealtimeChannelBase`

#### MessageListener moved

The `MessageListener` was an empty interface (probably as some sort of the [marker interface pattern](https://en.wikipedia.org/wiki/Marker_interface_pattern)) 
implemented in the realtime `Channel` that extended from the `ChannelBase.MessageListener` interface. Now [after renaming](#channelbase-renamed)
the listener is present only in `RealtimeChannelBase.MessageListener`. However, this should not affect the API and the listener can still be used as `Channel.MessageListener`.

`io.ably.core.realtime.Channel.MessageListener` -> `io.ably.core.realtime.RealtimeChannelBase.MessageListener`

### Modularization

The SDK has now been divided into three modules: `lib`, `java` and `android`. The `lib` module contains code that is common to both `java` and `android`.
This is better than the previous approach whereby `java` and `android` were monoliths linked only by shared source code sets.

The new `lib` module has to be published separately in order to make the SDKs work. However, this won't change the way of importing 
the SDK into your project, as the new dependency is a transitive dependency of the `java` and `android` modules.
