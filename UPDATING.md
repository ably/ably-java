# Upgrade / Migration Guide

## Version 1.x to 2.x

### Modularization

The SDK has now been divided into three modules: `lib`, `java` and `android`. The `lib` module contains code that is common to both `java` and `android`.
This is better than the previous approach whereby `java` and `android` were monoliths linked only by shared source code sets.

### API changes

In order to achieve this [Modularization](#modularization), dividing the project into separate modules, a refactor was necessary which has created the following API changes:

- `io.ably.lib.rest.ChannelBase` renamed -> `io.ably.lib.rest.RestChannelBase`
- `io.ably.lib.realtime.ChannelBase` renamed -> `io.ably.lib.realtime.RealtimeChannelBase`
- `io.ably.lib.rest.AblyBase.Channels` moved -> `io.ably.lib.types.Channels`
- `io.ably.lib.realtime.Channel.MessageListener` removed -> use `io.ably.lib.realtime.ChannelBase.MessageListener` (old API still works)
- `io.ably.lib.push.Push.ChannelSubscription` moved -> `io.ably.lib.push.PushBase.ChannelSubscription` (old API still works)
