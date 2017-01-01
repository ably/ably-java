# Change Log

## [v0.8.9](https://github.com/ably/ably-java/tree/v0.8.9) (2017-01-01)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.8...v0.8.9)

**Fixed bugs:**

- Msgpack truncates data member [\#261](https://github.com/ably/ably-java/issues/261)

## [0.8.8](https://github.com/ably/ably-java/tree/v0.8.8)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.7...v0.8.8)

**Fixed bugs:**

- Fix bug causing infinite loop if exception thrown in Transport.send()
- Bump msgpack-core dependency to 0.8.11

## [v0.8.7](https://github.com/ably/ably-java/tree/v0.8.7)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.6...v0.8.7)

**Fixed bugs:**

- Transport state change events suppressed after loss of network [\#233](https://github.com/ably/ably-java/issues/233)

**Merged pull requests:**

- Fixed issue 233, made changes to allow ITransport mocking [\#236](https://github.com/ably/ably-java/pull/236) ([psolstice](https://github.com/psolstice))

## [v0.8.6](https://github.com/ably/ably-java/tree/v0.8.6)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.5...v0.8.6)

**Merged pull requests:**

- Relocated java-websocket library to bintray [\#229](https://github.com/ably/ably-java/pull/229) ([psolstice](https://github.com/psolstice))
- Made Auth.TokenRequest constructors public [\#228](https://github.com/ably/ably-java/pull/228) ([psolstice](https://github.com/psolstice))
- Fixed BuildConfig problems [\#227](https://github.com/ably/ably-java/pull/227) ([psolstice](https://github.com/psolstice))

## [v0.8.5](https://github.com/ably/ably-java/tree/v0.8.5)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.4...v0.8.5)

**Implemented enhancements:**

- Add reauth capability [\#129](https://github.com/ably/ably-java/issues/129)
- Remove unused HexDump file [\#81](https://github.com/ably/ably-java/issues/81)
- Final 0.8 spec updates [\#53](https://github.com/ably/ably-java/issues/53)
- HAS\_BACKLOG flag [\#6](https://github.com/ably/ably-java/issues/6)

**Fixed bugs:**

- ConnectionManager.failQueuedMessages() does not remove messages once the callback is called [\#222](https://github.com/ably/ably-java/issues/222)
- ConnectionManager.setSuspendTime() isn't called when a transport disconnects [\#220](https://github.com/ably/ably-java/issues/220)

**Closed issues:**

- "Trust anchor for certification path not found" exception on android [\#197](https://github.com/ably/ably-java/issues/197)
- travis jdk7 build gets buffer overflow fault [\#191](https://github.com/ably/ably-java/issues/191)
- never valid to provide both a restHost and environment value [\#187](https://github.com/ably/ably-java/issues/187)
- fallback problems [\#178](https://github.com/ably/ably-java/issues/178)
- Complete Android build work [\#148](https://github.com/ably/ably-java/issues/148)
- Add shutdown hook to close a connection when the VM exits [\#71](https://github.com/ably/ably-java/issues/71)
- AuthOptions constructor is not unambiguous [\#62](https://github.com/ably/ably-java/issues/62)

**Merged pull requests:**

- Messages are now removed from the queue after onError\(\) call [\#225](https://github.com/ably/ably-java/pull/225) ([psolstice](https://github.com/psolstice))
- Ensure that suspendTime is set on disconnection [\#221](https://github.com/ably/ably-java/pull/221) ([paddybyers](https://github.com/paddybyers))
- Added logging, clarified code [\#219](https://github.com/ably/ably-java/pull/219) ([psolstice](https://github.com/psolstice))
- RSL6b test, log errors [\#215](https://github.com/ably/ably-java/pull/215) ([psolstice](https://github.com/psolstice))
- Fixed travis crash when using OpenJDK 7 [\#213](https://github.com/ably/ably-java/pull/213) ([psolstice](https://github.com/psolstice))
- Fixed init\_default\_log\_output\_stream test on Windows [\#209](https://github.com/ably/ably-java/pull/209) ([psolstice](https://github.com/psolstice))
- Worked around RealtimeCryptoTest.set\_cipher\_params intermittent failure [\#203](https://github.com/ably/ably-java/pull/203) ([trenouf](https://github.com/trenouf))
- Fixed and re-enabled RestAppStatsTest [\#201](https://github.com/ably/ably-java/pull/201) ([trenouf](https://github.com/trenouf))
- Used hardcoded constant for protocol version [\#200](https://github.com/ably/ably-java/pull/200) ([trenouf](https://github.com/trenouf))
- Add note on proguard to readme [\#199](https://github.com/ably/ably-java/pull/199) ([SimonWoolf](https://github.com/SimonWoolf))
- useTokenAuth forces token authorization [\#196](https://github.com/ably/ably-java/pull/196) ([trenouf](https://github.com/trenouf))
- RSC7a: X-Ably-Version header [\#195](https://github.com/ably/ably-java/pull/195) ([trenouf](https://github.com/trenouf))
- HttpTest: fixed test to allow for fallback hosts with same IP [\#192](https://github.com/ably/ably-java/pull/192) ([trenouf](https://github.com/trenouf))
- Don't modify ClientOptions; Fixed tests to not set both host and environment [\#190](https://github.com/ably/ably-java/pull/190) ([trenouf](https://github.com/trenouf))
- TO3k2,TO3k3: disallow restHost/realtimeHost with environment [\#189](https://github.com/ably/ably-java/pull/189) ([trenouf](https://github.com/trenouf))
- Separate java and android builds [\#188](https://github.com/ably/ably-java/pull/188) ([trenouf](https://github.com/trenouf))
- Tests for host fallback behaviour on rest [\#184](https://github.com/ably/ably-java/pull/184) ([trenouf](https://github.com/trenouf))
- 0.8 authorisation changes [\#182](https://github.com/ably/ably-java/pull/182) ([trenouf](https://github.com/trenouf))
- Removed unused HexDump class [\#181](https://github.com/ably/ably-java/pull/181) ([trenouf](https://github.com/trenouf))
- issues/178: fix fallback [\#179](https://github.com/ably/ably-java/pull/179) ([trenouf](https://github.com/trenouf))
- custom fallback support [\#176](https://github.com/ably/ably-java/pull/176) ([trenouf](https://github.com/trenouf))
- RSC11 environment prefix [\#162](https://github.com/ably/ably-java/pull/162) ([VOstopolets](https://github.com/VOstopolets))
- Reauth capability [\#149](https://github.com/ably/ably-java/pull/149) ([VOstopolets](https://github.com/VOstopolets))
- RTN2g: Param "Lib" with header value \(RSC7b\) [\#147](https://github.com/ably/ably-java/pull/147) ([VOstopolets](https://github.com/VOstopolets))

## [v0.8.4](https://github.com/ably/ably-java/tree/v0.8.4) (2016-10-07)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.3...v0.8.4)

**Fixed bugs:**

- Connect whilst suspended does not appear to be connecting immediately [\#167](https://github.com/ably/ably-java/issues/167)
- Prep for 0.9 spec [\#145](https://github.com/ably/ably-java/issues/145)

**Closed issues:**

- RSC11: Environment option [\#160](https://github.com/ably/ably-java/issues/160)
- ably-java 0..8.3 release isn't available on jcenter [\#155](https://github.com/ably/ably-java/issues/155)

**Merged pull requests:**

- issues/170: Fixed message serial out of sync after recover [\#175](https://github.com/ably/ably-java/pull/175) ([trenouf](https://github.com/trenouf))
- heartbeat support [\#173](https://github.com/ably/ably-java/pull/173) ([trenouf](https://github.com/trenouf))
- tpr/issue167: 	Fixed explicit connect after connection has disconnected [\#172](https://github.com/ably/ably-java/pull/172) ([trenouf](https://github.com/trenouf))

## [v0.8.3](https://github.com/ably/ably-java/tree/v0.8.3) (2016-08-25)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.2...v0.8.3)

**Implemented enhancements:**

- README not complete [\#88](https://github.com/ably/ably-java/issues/88)
- authCallback must accept TokenDetails or token strings [\#34](https://github.com/ably/ably-java/issues/34)
- PaginatedResult\#isLast method missing [\#33](https://github.com/ably/ably-java/issues/33)

**Fixed bugs:**

- A post-suspend clean connection removes all channels instead of moving them to DETACHED [\#133](https://github.com/ably/ably-java/issues/133)
- Reauthentication on external URLs  [\#92](https://github.com/ably/ably-java/issues/92)
- Attach CompletionListener [\#84](https://github.com/ably/ably-java/issues/84)
- Implicit attach on Publish or Subscribe [\#45](https://github.com/ably/ably-java/issues/45)

**Closed issues:**

- Library doesn't seem to serialise Map objects properly [\#112](https://github.com/ably/ably-java/issues/112)
- Host ClientOptions [\#22](https://github.com/ably/ably-java/issues/22)

**Merged pull requests:**

- Detach on suspend [\#146](https://github.com/ably/ably-java/pull/146) ([paddybyers](https://github.com/paddybyers))
- Header X-Ably-Lib \(RSC7b\) [\#143](https://github.com/ably/ably-java/pull/143) ([VOstopolets](https://github.com/VOstopolets))
- Ensure interoperability with other libraries over JSON. [\#140](https://github.com/ably/ably-java/pull/140) ([tcard](https://github.com/tcard))
- Update README.md [\#138](https://github.com/ably/ably-java/pull/138) ([hauleth](https://github.com/hauleth))
- Ensure that messages with invalid data type are rejected. [\#137](https://github.com/ably/ably-java/pull/137) ([tcard](https://github.com/tcard))
- Add messages encoding fixtures test. [\#136](https://github.com/ably/ably-java/pull/136) ([tcard](https://github.com/tcard))
- Ensure graceful handling of DETACH and DISCONNECT. [\#131](https://github.com/ably/ably-java/pull/131) ([tcard](https://github.com/tcard))
- Proxy support [\#123](https://github.com/ably/ably-java/pull/123) ([paddybyers](https://github.com/paddybyers))
- RTN17 [\#122](https://github.com/ably/ably-java/pull/122) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Avoid stalled state from previous connection when reusing Realtime. [\#117](https://github.com/ably/ably-java/pull/117) ([tcard](https://github.com/tcard))
- AuthOptions javadoc enhancements and testcases [\#116](https://github.com/ably/ably-java/pull/116) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add implicit attach test cases for channel publish and subscribe [\#115](https://github.com/ably/ably-java/pull/115) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add isLast API to PaginatedResult [\#111](https://github.com/ably/ably-java/pull/111) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add CompletionListener to Channel's attach API [\#108](https://github.com/ably/ably-java/pull/108) ([gokhanbarisaker](https://github.com/gokhanbarisaker))

## [v0.8.2](https://github.com/ably/ably-java/tree/v0.8.2) (2016-03-14)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.1...v0.8.2)

**Implemented enhancements:**

- Lower case PresenceMessage.Action enum [\#90](https://github.com/ably/ably-java/issues/90)
- Switch arity of auth methods [\#44](https://github.com/ably/ably-java/issues/44)
- Realtime Presence and Channel untilAttach functionality is missing [\#36](https://github.com/ably/ably-java/issues/36)
- Proposal: errorReason instead of reason [\#30](https://github.com/ably/ably-java/issues/30)
- Presence subscribe with presence action [\#21](https://github.com/ably/ably-java/issues/21)
- Message publish overloaded without a listener [\#17](https://github.com/ably/ably-java/issues/17)
- Emit errors [\#16](https://github.com/ably/ably-java/issues/16)
- README to include code examples and follow common format [\#15](https://github.com/ably/ably-java/issues/15)

**Fixed bugs:**

- force is an attribute of AuthOptions, not an argument [\#103](https://github.com/ably/ably-java/issues/103)
- Lower case PresenceMessage.Action enum [\#90](https://github.com/ably/ably-java/issues/90)
- Presence enter, update, leave methods need to be overloaded [\#89](https://github.com/ably/ably-java/issues/89)
- Message constructor is inconsistent [\#87](https://github.com/ably/ably-java/issues/87)
- Channel state should be initialized not initialised for consistency [\#85](https://github.com/ably/ably-java/issues/85)
- Unsubscribe all and off all is missing [\#83](https://github.com/ably/ably-java/issues/83)
- Presence data assumed to be a string, Map not supported [\#75](https://github.com/ably/ably-java/issues/75)
- Host fallback for REST [\#54](https://github.com/ably/ably-java/issues/54)
- Switch arity of auth methods [\#44](https://github.com/ably/ably-java/issues/44)
- NullPointerException: Attempt to invoke interface method 'java.lang.String java.security.Principal.getName\(\)' on a null object reference [\#41](https://github.com/ably/ably-java/issues/41)
- Unable to deploy client lib in Android Studio project on OSX [\#39](https://github.com/ably/ably-java/issues/39)
- Realtime Presence and Channel untilAttach functionality is missing [\#36](https://github.com/ably/ably-java/issues/36)
- Java logLevel [\#26](https://github.com/ably/ably-java/issues/26)
- Timeout in test suite [\#24](https://github.com/ably/ably-java/issues/24)
- Presence subscribe with presence action [\#21](https://github.com/ably/ably-java/issues/21)

**Closed issues:**

- Fix missing JCE dependency on Travis [\#69](https://github.com/ably/ably-java/issues/69)
- Remove eclipse artifact [\#68](https://github.com/ably/ably-java/issues/68)
- Typo on Presence\#history javadoc [\#63](https://github.com/ably/ably-java/issues/63)
- Spec validation [\#23](https://github.com/ably/ably-java/issues/23)

**Merged pull requests:**

- 0.8.2 [\#119](https://github.com/ably/ably-java/pull/119) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update changelog for v0.8.2 release [\#118](https://github.com/ably/ably-java/pull/118) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add information for listening specific connection state changes to readme [\#114](https://github.com/ably/ably-java/pull/114) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add null check [\#113](https://github.com/ably/ably-java/pull/113) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Move force argument to AuthOptions as a variable [\#107](https://github.com/ably/ably-java/pull/107) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update MessageListener and PresenceListener interface [\#106](https://github.com/ably/ably-java/pull/106) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add until attach functionality to Presence & Channel [\#102](https://github.com/ably/ably-java/pull/102) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add unsubscribe all and off all [\#101](https://github.com/ably/ably-java/pull/101) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix channel state initialised spelling to initialized [\#100](https://github.com/ably/ably-java/pull/100) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix constructor signature [\#99](https://github.com/ably/ably-java/pull/99) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Overload publish APIs [\#98](https://github.com/ably/ably-java/pull/98) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add presence subscribe with presence action APIs [\#97](https://github.com/ably/ably-java/pull/97) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update Auth\#requestToken signature for spec id RSA8e [\#96](https://github.com/ably/ably-java/pull/96) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Presence overloading [\#95](https://github.com/ably/ably-java/pull/95) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Convert enum variable naming to lowercase [\#94](https://github.com/ably/ably-java/pull/94) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix recover spec regex pattern [\#86](https://github.com/ably/ably-java/pull/86) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add httpMaxRetryCount && Simplify http fallback flow [\#80](https://github.com/ably/ably-java/pull/80) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Remove eclipse artifact [\#79](https://github.com/ably/ably-java/pull/79) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Upgrade gradle version [\#78](https://github.com/ably/ably-java/pull/78) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Upgrade dependencies [\#77](https://github.com/ably/ably-java/pull/77) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix leaking non-AblyExceptions on ConnectionManager\#onMessage callback [\#74](https://github.com/ably/ably-java/pull/74) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add custom test suite tasks to travis config [\#70](https://github.com/ably/ably-java/pull/70) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add maven package export script [\#67](https://github.com/ably/ably-java/pull/67) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix typo on Presence\#history javadoc [\#66](https://github.com/ably/ably-java/pull/66) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Readme enhancement [\#65](https://github.com/ably/ably-java/pull/65) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add Auth\#requestToken test cases [\#60](https://github.com/ably/ably-java/pull/60) ([gokhanbarisaker](https://github.com/gokhanbarisaker))

## [v0.8.1](https://github.com/ably/ably-java/tree/v0.8.1) (2016-01-01)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.0...v0.8.1)

**Implemented enhancements:**

- Travis.CI support [\#4](https://github.com/ably/ably-java/issues/4)

**Fixed bugs:**

- Gradle build should be able to build library without Android SDK installed [\#46](https://github.com/ably/ably-java/issues/46)
- Token authentication "Request mac doesn't match" [\#40](https://github.com/ably/ably-java/issues/40)
- Re-enable temporarily disabled test [\#31](https://github.com/ably/ably-java/issues/31)
- Travis.CI support [\#4](https://github.com/ably/ably-java/issues/4)
- Key length case and ably common [\#35](https://github.com/ably/ably-java/pull/35) ([mattheworiordan](https://github.com/mattheworiordan))

**Closed issues:**

- Re-enable temporarily disabled test [\#32](https://github.com/ably/ably-java/issues/32)
- Additional encoding / decoding tests [\#1](https://github.com/ably/ably-java/issues/1)

**Merged pull requests:**

- Async http [\#59](https://github.com/ably/ably-java/pull/59) ([paddybyers](https://github.com/paddybyers))
- changes to run provided RestInit test case [\#58](https://github.com/ably/ably-java/pull/58) ([gorodechnyj](https://github.com/gorodechnyj))
- Allow connection manager thread to exit when closed or failed, and re… [\#50](https://github.com/ably/ably-java/pull/50) ([paddybyers](https://github.com/paddybyers))
- Add script for running tests in CI [\#49](https://github.com/ably/ably-java/pull/49) ([lmars](https://github.com/lmars))
- Publish implicit attach [\#48](https://github.com/ably/ably-java/pull/48) ([paddybyers](https://github.com/paddybyers))
- Make inclusion of android-test project conditional on whether or not … [\#47](https://github.com/ably/ably-java/pull/47) ([paddybyers](https://github.com/paddybyers))
- RSC1 and RSC2 - initialisation and default logging behaviour [\#43](https://github.com/ably/ably-java/pull/43) ([iliyakostadinov](https://github.com/iliyakostadinov))

## [v0.8.0](https://github.com/ably/ably-java/tree/v0.8.0) (2015-05-07)
**Implemented enhancements:**

- EventEmitter interface [\#11](https://github.com/ably/ably-java/issues/11)
- Change pagination API [\#10](https://github.com/ably/ably-java/issues/10)
- Stats types are out of date [\#7](https://github.com/ably/ably-java/issues/7)

**Fixed bugs:**

- CipherParams type [\#12](https://github.com/ably/ably-java/issues/12)
- Change pagination API [\#10](https://github.com/ably/ably-java/issues/10)
- Stats types are out of date [\#7](https://github.com/ably/ably-java/issues/7)

**Closed issues:**

- Builds are not failing with the correct exit code [\#5](https://github.com/ably/ably-java/issues/5)

**Merged pull requests:**

- Fix comment in connection failure test [\#3](https://github.com/ably/ably-java/pull/3) ([mattheworiordan](https://github.com/mattheworiordan))
- Allow recovery string that includes -1 serial [\#2](https://github.com/ably/ably-java/pull/2) ([mattheworiordan](https://github.com/mattheworiordan))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*