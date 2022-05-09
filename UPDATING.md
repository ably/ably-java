# Upgrade / Migration Guide

## Version 1.x to 2.x

### Modularization

The SDK was divided into modules: lib, java, android.

### API changes

In order to divide the project into separate modules a refactoring of classes took place which caused below API changes:

- `io.ably.lib.rest.ChannelBase` renamed -> `io.ably.lib.rest.RestChannelBase`
- `io.ably.lib.realtime.ChannelBase` renamed -> `io.ably.lib.realtime.RealtimeChannelBase`
- `io.ably.lib.rest.AblyBase.Channels` moved -> `io.ably.lib.types.Channels`
- `io.ably.lib.realtime.Channel.MessageListener` removed -> use `io.ably.lib.realtime.ChannelBase.MessageListener` (old API still works)
- `io.ably.lib.push.Push.ChannelSubscription` moved -> `io.ably.lib.push.PushBase.ChannelSubscription` (old API still works)

### Other changes

Changes not related to the API but worth noting:

- `io.ably.lib.util.Serialization` - removed MsgPack optimization workaround for ably-android which was fixing a problem that has been resolved in MsgPack SDK 0.8.12
- `io.ably.lib.push.PushChannel` - replaced Channel object with channel name string in the constructor. This allows to easily create it from Realtime and Rest channels.
