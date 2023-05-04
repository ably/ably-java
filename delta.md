# Changelog

## [Unreleased](https://github.com/ably/ably-java/tree/HEAD)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.25...HEAD)

**Fixed bugs:**

- Provide an error code and error message for failed queued messages [\#920](https://github.com/ably/ably-java/issues/920)
- presence.enter fails on reconnection [\#884](https://github.com/ably/ably-java/issues/884)
- Consider upgrading vulnerable version of java-websocket [\#731](https://github.com/ably/ably-java/issues/731)
- Dead channels after reconnection [\#605](https://github.com/ably/ably-java/issues/605)

**Merged pull requests:**

- Remove unused ExecutorCompletionService [\#923](https://github.com/ably/ably-java/pull/923) ([ikbalkaya](https://github.com/ikbalkaya))
- Add reason to pending message instead of creating an ErrorInfo [\#922](https://github.com/ably/ably-java/pull/922) ([ikbalkaya](https://github.com/ikbalkaya))

## [v1.2.25](https://github.com/ably/ably-java/tree/v1.2.25) (2023-02-07)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.24...v1.2.25)

**Fixed bugs:**

- Released channel re-added to the channel map after DETACHED message [\#913](https://github.com/ably/ably-java/issues/913)

**Merged pull requests:**

- Release/1.2.25 [\#915](https://github.com/ably/ably-java/pull/915) ([AndyTWF](https://github.com/AndyTWF))
- Drop messages where channel does not exist [\#914](https://github.com/ably/ably-java/pull/914) ([AndyTWF](https://github.com/AndyTWF))
- Improve `1.2`-series Release Process [\#912](https://github.com/ably/ably-java/pull/912) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix link formatting in changelog [\#911](https://github.com/ably/ably-java/pull/911) ([AndyTWF](https://github.com/AndyTWF))

## [v1.2.24](https://github.com/ably/ably-java/tree/v1.2.24) (2023-02-02)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.23...v1.2.24)

**Fixed bugs:**

- Presence messages superseded whilst channel in attaching state [\#908](https://github.com/ably/ably-java/issues/908)
- A failed resume incorrectly retries queued messages prior to reattachment [\#905](https://github.com/ably/ably-java/issues/905)
- Pending messages are not failed when transitioning to suspended [\#904](https://github.com/ably/ably-java/issues/904)

**Merged pull requests:**

- Release/1.2.24 [\#910](https://github.com/ably/ably-java/pull/910) ([ikbalkaya](https://github.com/ikbalkaya))
- 908 presence message superseded [\#909](https://github.com/ably/ably-java/pull/909) ([AndyTWF](https://github.com/AndyTWF))
- Improve after resume failure logic [\#906](https://github.com/ably/ably-java/pull/906) ([ikbalkaya](https://github.com/ikbalkaya))

## [v1.2.23](https://github.com/ably/ably-java/tree/v1.2.23) (2023-01-25)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.22...v1.2.23)

**Fixed bugs:**

- Re-attach fails due to previous detach request [\#885](https://github.com/ably/ably-java/issues/885)
- Lib is not re-sending pending messages on new transport after a resume [\#474](https://github.com/ably/ably-java/issues/474)

**Closed issues:**

- Check and fix argument ordering in test assertions [\#892](https://github.com/ably/ably-java/issues/892)

**Merged pull requests:**

- Release/1.2.23 [\#903](https://github.com/ably/ably-java/pull/903) ([ikbalkaya](https://github.com/ikbalkaya))
- Connection resumption improvements [\#900](https://github.com/ably/ably-java/pull/900) ([ikbalkaya](https://github.com/ikbalkaya))
- Bug Fixes and Improve CI, including Run REST and Realtime integration tests as discrete jobs [\#891](https://github.com/ably/ably-java/pull/891) ([QuintinWillison](https://github.com/QuintinWillison))
- Ignore consistently failing test : auth\_renewAuth\_callback\_invoked [\#890](https://github.com/ably/ably-java/pull/890) ([ikbalkaya](https://github.com/ikbalkaya))
- Make EventEmitter.on\(\) documentation reflect implementation [\#889](https://github.com/ably/ably-java/pull/889) ([AndyTWF](https://github.com/AndyTWF))
- Fix attach/detach race condition [\#887](https://github.com/ably/ably-java/pull/887) ([ikbalkaya](https://github.com/ikbalkaya))

## [v1.2.22](https://github.com/ably/ably-java/tree/v1.2.22) (2023-01-05)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.21...v1.2.22)

**Merged pull requests:**

- Release/1.2.22 [\#886](https://github.com/ably/ably-java/pull/886) ([QuintinWillison](https://github.com/QuintinWillison))
- Skip checking WS hostname when not using SSL [\#883](https://github.com/ably/ably-java/pull/883) ([cruickshankpg](https://github.com/cruickshankpg))

## [v1.2.21](https://github.com/ably/ably-java/tree/v1.2.21) (2022-12-12)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.20...v1.2.21)

**Closed issues:**

- Presence.endSync throws NullPointerException when processing a message [\#853](https://github.com/ably/ably-java/issues/853)
- handling of channel options in InternalChannels.get is not thread safe [\#663](https://github.com/ably/ably-java/issues/663)
- Remove hardcoded name from Maven Gradle files [\#565](https://github.com/ably/ably-java/issues/565)
- Android CI fails due to unaccepted licenses [\#554](https://github.com/ably/ably-java/issues/554)
- AsyncHttpScheduler.dispose\(\) is never used [\#523](https://github.com/ably/ably-java/issues/523)
- More Encapsulation Needed [\#508](https://github.com/ably/ably-java/issues/508)

**Merged pull requests:**

- Release/1.2.1 fixup [\#880](https://github.com/ably/ably-java/pull/880) ([QuintinWillison](https://github.com/QuintinWillison))
- Release/1.2.21 [\#879](https://github.com/ably/ably-java/pull/879) ([davyskiba](https://github.com/davyskiba))
- added null check to prevent NullPointerExceptions [\#873](https://github.com/ably/ably-java/pull/873) ([davyskiba](https://github.com/davyskiba))
- Stop hiding flakey test failures [\#861](https://github.com/ably/ably-java/pull/861) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.20](https://github.com/ably/ably-java/tree/v1.2.20) (2022-11-24)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.19...v1.2.20)

**Fixed bugs:**

- Automatic presence re-enter after network connection is back does not work [\#857](https://github.com/ably/ably-java/issues/857)

**Merged pull requests:**

- Release/1.2.20 [\#865](https://github.com/ably/ably-java/pull/865) ([QuintinWillison](https://github.com/QuintinWillison))
- Revert to protocol 1.0 [\#864](https://github.com/ably/ably-java/pull/864) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.19](https://github.com/ably/ably-java/tree/v1.2.19) (2022-11-23)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.18...v1.2.19)

**Implemented enhancements:**

- Implement incremental backoff and jitter [\#795](https://github.com/ably/ably-java/issues/795)
- Implement backoff and jitter timeout by spec RTB1 [\#852](https://github.com/ably/ably-java/pull/852) ([qsdigor](https://github.com/qsdigor))

**Fixed bugs:**

- channel.publish\(\) does not call CompletionListener when network is down [\#855](https://github.com/ably/ably-java/issues/855)

**Closed issues:**

- Merge `main` \(v1\) branch into `integration/version-2` \(v2\) branch [\#844](https://github.com/ably/ably-java/issues/844)
- Populate feature compliance for `Realtime: Authentication: Get Confirmed Client Identifier` [\#828](https://github.com/ably/ably-java/issues/828)
- Create Feature Compliance Manifest for `ably-java` [\#817](https://github.com/ably/ably-java/issues/817)
- Remove references to GCM and simplify code [\#703](https://github.com/ably/ably-java/issues/703)

**Merged pull requests:**

- Release/1.2.19 [\#860](https://github.com/ably/ably-java/pull/860) ([QuintinWillison](https://github.com/QuintinWillison))
- Revert to protocol 1.1 [\#858](https://github.com/ably/ably-java/pull/858) ([KacperKluka](https://github.com/KacperKluka))

## [v1.2.18](https://github.com/ably/ably-java/tree/v1.2.18) (2022-09-23)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.17...v1.2.18)

**Closed issues:**

- \[EDX-278\] Add/ update docstring comments in Java SDK per the latest state of the canonical table [\#830](https://github.com/ably/ably-java/issues/830)

**Merged pull requests:**

- Release 1.2.18 [\#841](https://github.com/ably/ably-java/pull/841) ([qsdigor](https://github.com/qsdigor))
- Add overview page and config when generating javadoc [\#836](https://github.com/ably/ably-java/pull/836) ([qsdigor](https://github.com/qsdigor))
- Add or update doc comment [\#835](https://github.com/ably/ably-java/pull/835) ([qsdigor](https://github.com/qsdigor))
- Javadoc workflow in GH actions [\#832](https://github.com/ably/ably-java/pull/832) ([qsdigor](https://github.com/qsdigor))

## [v1.2.17](https://github.com/ably/ably-java/tree/v1.2.17) (2022-09-20)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.16...v1.2.17)

**Implemented enhancements:**

- Deploy to Maven Central from GitHub Actions [\#659](https://github.com/ably/ably-java/issues/659)

**Fixed bugs:**

- RSA4d is not implemented correctly [\#829](https://github.com/ably/ably-java/issues/829)
- JSONUtilsObject.add\(\) silently discards data of unsupported type [\#501](https://github.com/ably/ably-java/issues/501)

**Merged pull requests:**

- Release/1.2.17 [\#840](https://github.com/ably/ably-java/pull/840) ([KacperKluka](https://github.com/KacperKluka))
- Fail Ably connection if auth callback throws specific errors [\#834](https://github.com/ably/ably-java/pull/834) ([KacperKluka](https://github.com/KacperKluka))

## [v1.2.16](https://github.com/ably/ably-java/tree/v1.2.16) (2022-07-19)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.15...v1.2.16)

**Fixed bugs:**

- waiter.close\(\) is invoked early on onAuthUpdatedAsync method [\#823](https://github.com/ably/ably-java/issues/823)
- call waiter.close\(\) after breaking from while loop [\#825](https://github.com/ably/ably-java/pull/825) ([ikbalkaya](https://github.com/ikbalkaya))

**Closed issues:**

- Increase minimum required Android API Level to 21 \(or above\) [\#813](https://github.com/ably/ably-java/issues/813)
- Rename the "lib" module to "core" [\#811](https://github.com/ably/ably-java/issues/811)
- Increase emulation test coverage to include our minimum supported Android API Level [\#809](https://github.com/ably/ably-java/issues/809)

**Merged pull requests:**

- Release/1.2.16 [\#826](https://github.com/ably/ably-java/pull/826) ([ikbalkaya](https://github.com/ikbalkaya))
- Add multiple android emulation devices [\#812](https://github.com/ably/ably-java/pull/812) ([qsdigor](https://github.com/qsdigor))

## [v1.2.15](https://github.com/ably/ably-java/tree/v1.2.15) (2022-07-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.14...v1.2.15)

**Implemented enhancements:**

- Invalid method implementation in README [\#819](https://github.com/ably/ably-java/issues/819)
- Prepare the "lib" module configuration for publishing to Maven Central [\#772](https://github.com/ably/ably-java/issues/772)
- Split library into core and platform modules [\#728](https://github.com/ably/ably-java/issues/728)
- Add new renew async method [\#816](https://github.com/ably/ably-java/pull/816) ([ikbalkaya](https://github.com/ikbalkaya))

**Fixed bugs:**

- Early return from  onAuthUpdated creates issues [\#814](https://github.com/ably/ably-java/issues/814)

**Closed issues:**

- Document which thread is whole SDK or callbacks using [\#800](https://github.com/ably/ably-java/issues/800)
- Use OIDC to publish from GitHub workflow runners to AWS S3 for `sdk.ably.com` deployments [\#786](https://github.com/ably/ably-java/issues/786)
- Use the "java-library" plugin for ably-java [\#780](https://github.com/ably/ably-java/issues/780)
- Improve build.gradle files configuration [\#779](https://github.com/ably/ably-java/issues/779)
- Update dependency: Gradle and Gradle Android plugin com.android.tools.build:gradle [\#778](https://github.com/ably/ably-java/issues/778)
- Update dependency: org.msgpack:msgpack-core [\#775](https://github.com/ably/ably-java/issues/775)
- Update dependency: com.google.firebase:firebase-messaging [\#774](https://github.com/ably/ably-java/issues/774)
- Replace the deprecated "maven" plugin with "maven-publish" [\#773](https://github.com/ably/ably-java/issues/773)

**Merged pull requests:**

- Release/1.2.15 [\#821](https://github.com/ably/ably-java/pull/821) ([ikbalkaya](https://github.com/ikbalkaya))
- Update onChannelStateChanged readme with current implementation [\#820](https://github.com/ably/ably-java/pull/820) ([qsdigor](https://github.com/qsdigor))
- Document thread policy for callbacks and add missing documentation for callbacks [\#818](https://github.com/ably/ably-java/pull/818) ([qsdigor](https://github.com/qsdigor))

## [v1.2.14](https://github.com/ably/ably-java/tree/v1.2.14) (2022-06-23)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.13...v1.2.14)

**Fixed bugs:**

- NoSuchMethodError in ably-android for API lower than 24 [\#802](https://github.com/ably/ably-java/issues/802)
- Threads remain in parked \(waiting\) state indefinitely when `AblyRest` instance is freed [\#801](https://github.com/ably/ably-java/issues/801)
- Minimum API Level supported for Android is 19 \(KitKat, v.4.4\) [\#804](https://github.com/ably/ably-java/pull/804) ([QuintinWillison](https://github.com/QuintinWillison))

**Merged pull requests:**

- Release/1.2.14 [\#810](https://github.com/ably/ably-java/pull/810) ([KacperKluka](https://github.com/KacperKluka))
- Fix Java-WebSocket problem on Android below 24 [\#808](https://github.com/ably/ably-java/pull/808) ([KacperKluka](https://github.com/KacperKluka))
- Add `finalize()` and `AutoCloseable` support to `AblyRest` instances [\#807](https://github.com/ably/ably-java/pull/807) ([QuintinWillison](https://github.com/QuintinWillison))
- Increase minimum JRE version to 1.8 [\#805](https://github.com/ably/ably-java/pull/805) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.13](https://github.com/ably/ably-java/tree/v1.2.13) (2022-06-16)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.12...v1.2.13)

**Closed issues:**

- Test issue [\#784](https://github.com/ably/ably-java/issues/784)
- Update dependency: com.google.code.gson:gson [\#777](https://github.com/ably/ably-java/issues/777)
- Update dependency: org.java-websocket:Java-WebSocket [\#776](https://github.com/ably/ably-java/issues/776)
- Fix Sonatype Nexus Maven Central Release Procedure [\#566](https://github.com/ably/ably-java/issues/566)
- Missing Maven dependency [\#533](https://github.com/ably/ably-java/issues/533)

**Merged pull requests:**

- Release/1.2.13 [\#799](https://github.com/ably/ably-java/pull/799) ([KacperKluka](https://github.com/KacperKluka))
- Update dependencies that contain known vulnerabilities [\#798](https://github.com/ably/ably-java/pull/798) ([KacperKluka](https://github.com/KacperKluka))

## [v1.2.12](https://github.com/ably/ably-java/tree/v1.2.12) (2022-05-05)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.11...v1.2.12)

**Fixed bugs:**

- Cannot automatically re-enter channel due to mismatched connectionId [\#761](https://github.com/ably/ably-java/issues/761)
- RTP5c is still implemented in Java code  [\#760](https://github.com/ably/ably-java/issues/760)
- Ensure that weak SSL/TLS protocols are not used [\#749](https://github.com/ably/ably-java/issues/749)

**Closed issues:**

- java Update urls in readme [\#759](https://github.com/ably/ably-java/issues/759)

**Merged pull requests:**

- Release/1.2.12 [\#766](https://github.com/ably/ably-java/pull/766) ([KacperKluka](https://github.com/KacperKluka))
- Update documentation URLs [\#764](https://github.com/ably/ably-java/pull/764) ([KacperKluka](https://github.com/KacperKluka))
- Use only the clientId and data of the original presence message when automatically re-entering [\#763](https://github.com/ably/ably-java/pull/763) ([KacperKluka](https://github.com/KacperKluka))
- Add missing syntax information to code snippets in the README [\#756](https://github.com/ably/ably-java/pull/756) ([KacperKluka](https://github.com/KacperKluka))
- Use only the secure SSL/TLS protocols [\#754](https://github.com/ably/ably-java/pull/754) ([KacperKluka](https://github.com/KacperKluka))
- Fix connection example in README [\#751](https://github.com/ably/ably-java/pull/751) ([owenpearson](https://github.com/owenpearson))

## [v1.2.11](https://github.com/ably/ably-java/tree/v1.2.11) (2022-02-04)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.10...v1.2.11)

**Fixed bugs:**

- `ConcurrentModificationException` when `unsubscribe` then `detach` channel presence listener [\#743](https://github.com/ably/ably-java/issues/743)
- `IllegalStateException` in `Crypto` `CBCCipher`'s `decrypt` method [\#741](https://github.com/ably/ably-java/issues/741)
- Incorrect use of locale sensitive String APIs [\#713](https://github.com/ably/ably-java/issues/713)
- `push.listSubscriptionsImpl` method not respecting params [\#705](https://github.com/ably/ably-java/issues/705)
- Read and persist `state` returned in `LocalDevice`/ `DeviceDetails` [\#697](https://github.com/ably/ably-java/issues/697)
- Detaching connection listeners in onAuthUpdated  [\#668](https://github.com/ably/ably-java/issues/668)

**Closed issues:**

- Write tests to confirm encrypted messages will correctly be received from message history [\#740](https://github.com/ably/ably-java/issues/740)

**Merged pull requests:**

- Release/1.2.11 [\#748](https://github.com/ably/ably-java/pull/748) ([QuintinWillison](https://github.com/QuintinWillison))
- Split ChannelCipher implementation into encrypt and decrypt specialisms [\#746](https://github.com/ably/ably-java/pull/746) ([QuintinWillison](https://github.com/QuintinWillison))
- Multicaster encapsulation [\#744](https://github.com/ably/ably-java/pull/744) ([QuintinWillison](https://github.com/QuintinWillison))
- Tweak CI [\#738](https://github.com/ably/ably-java/pull/738) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix Maven Central metadata [\#737](https://github.com/ably/ably-java/pull/737) ([QuintinWillison](https://github.com/QuintinWillison))
- Debug / Fix Tests [\#732](https://github.com/ably/ably-java/pull/732) ([QuintinWillison](https://github.com/QuintinWillison))
- Improve release process [\#725](https://github.com/ably/ably-java/pull/725) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix indentation and typos in authCallback example [\#724](https://github.com/ably/ably-java/pull/724) ([QuintinWillison](https://github.com/QuintinWillison))
- Added explicit locale for string manipulation methods [\#722](https://github.com/ably/ably-java/pull/722) ([martin-morek](https://github.com/martin-morek))
- Removed params overwrite [\#710](https://github.com/ably/ably-java/pull/710) ([martin-morek](https://github.com/martin-morek))

## [v1.2.10](https://github.com/ably/ably-java/tree/v1.2.10) (2021-09-30)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.9...v1.2.10)

**Implemented enhancements:**

- Add example for typical use of authCallback in README [\#134](https://github.com/ably/ably-java/issues/134)

**Fixed bugs:**

- Using Firebase installation ID as registration token: Users cannot reactivate the device after deactivating [\#715](https://github.com/ably/ably-java/issues/715)

**Closed issues:**

- Fix checkstyle error message [\#719](https://github.com/ably/ably-java/issues/719)
- Add example of publishing a JsonObject to readme? [\#307](https://github.com/ably/ably-java/issues/307)

**Merged pull requests:**

- Release/1.2.10 [\#723](https://github.com/ably/ably-java/pull/723) ([QuintinWillison](https://github.com/QuintinWillison))
- Fixed checkstyle errors [\#720](https://github.com/ably/ably-java/pull/720) ([martin-morek](https://github.com/martin-morek))
- Add steps to build AAR locally and to use it in another project locally [\#718](https://github.com/ably/ably-java/pull/718) ([ben-xD](https://github.com/ben-xD))
- Fix: Use `FirebaseMessaging#getToken()` to get registration token [\#717](https://github.com/ably/ably-java/pull/717) ([ben-xD](https://github.com/ben-xD))

## [v1.2.9](https://github.com/ably/ably-java/tree/v1.2.9) (2021-09-13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.8...v1.2.9)

**Fixed bugs:**

- IllegalArgumentException: No enum constant io.ably.lib.http.HttpAuth.Type.BASİC [\#711](https://github.com/ably/ably-java/issues/711)
- ProGuard warnings emitted by Android build against 1.1.6 [\#529](https://github.com/ably/ably-java/issues/529)

**Closed issues:**

- Conform ReadMe and create Contributing Document [\#688](https://github.com/ably/ably-java/issues/688)

**Merged pull requests:**

- Release/1.2.9 [\#714](https://github.com/ably/ably-java/pull/714) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix incorrect parsing of HTTP auth type for some locales [\#712](https://github.com/ably/ably-java/pull/712) ([QuintinWillison](https://github.com/QuintinWillison))
- Suppressed warning in ProGuard [\#709](https://github.com/ably/ably-java/pull/709) ([martin-morek](https://github.com/martin-morek))
- README.md and CONTRIGUTING.md restructure [\#704](https://github.com/ably/ably-java/pull/704) ([martin-morek](https://github.com/martin-morek))

## [v1.2.8](https://github.com/ably/ably-java/tree/v1.2.8) (2021-09-01)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.7...v1.2.8)

**Implemented enhancements:**

- Update Stats fields with latest MessageTraffic types [\#394](https://github.com/ably/ably-java/issues/394)

**Fixed bugs:**

- Push Activation State Machine exception handling needs improvement [\#685](https://github.com/ably/ably-java/issues/685)
- WebsocketNotConnectedException on send [\#430](https://github.com/ably/ably-java/issues/430)

**Closed issues:**

- Tests from EventTest are falling [\#699](https://github.com/ably/ably-java/issues/699)
- Replace ULID with Android's UUID [\#680](https://github.com/ably/ably-java/issues/680)
- CI test suites are not being run on Android [\#674](https://github.com/ably/ably-java/issues/674)

**Merged pull requests:**

- Release/1.2.8 [\#707](https://github.com/ably/ably-java/pull/707) ([QuintinWillison](https://github.com/QuintinWillison))
- Replaced ULID with UUID for deviceID [\#702](https://github.com/ably/ably-java/pull/702) ([martin-morek](https://github.com/martin-morek))
- Separate handling WebsocketNotConnectedException [\#701](https://github.com/ably/ably-java/pull/701) ([martin-morek](https://github.com/martin-morek))
- Fixed failing EventTest tests to follow current implementation [\#700](https://github.com/ably/ably-java/pull/700) ([martin-morek](https://github.com/martin-morek))
- Updated Stats fields with the latest MessageTraffic types [\#698](https://github.com/ably/ably-java/pull/698) ([martin-morek](https://github.com/martin-morek))
- Add standard "About Ably" info to all public repos [\#692](https://github.com/ably/ably-java/pull/692) ([marklewin](https://github.com/marklewin))
- Add Android emulation workflow [\#684](https://github.com/ably/ably-java/pull/684) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.7](https://github.com/ably/ably-java/tree/v1.2.7) (2021-08-05)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.6...v1.2.7)

**Implemented enhancements:**

- Implement RSC7d \(Ably-Agent header\) [\#665](https://github.com/ably/ably-java/issues/665)
- Conform toString\(\) implementations [\#631](https://github.com/ably/ably-java/issues/631)

**Fixed bugs:**

- Remove use of forClass method in push activation state machine implementation [\#686](https://github.com/ably/ably-java/issues/686)
- Race condition releasing short lived channels [\#570](https://github.com/ably/ably-java/issues/570)
- Using a clientId should no longer be forcing token auth in the 1.1 spec [\#473](https://github.com/ably/ably-java/issues/473)
- Ensure correct feedback to developer when malformed key is supplied [\#382](https://github.com/ably/ably-java/issues/382)

**Closed issues:**

- Create code snippets for homepage \(kotlin\) [\#676](https://github.com/ably/ably-java/issues/676)
- Create code snippets for homepage \(java\) [\#673](https://github.com/ably/ably-java/issues/673)
- Fail connection immediately if authorize\(\) called and 403 returned [\#620](https://github.com/ably/ably-java/issues/620)
- FCM getToken method is deprecated [\#597](https://github.com/ably/ably-java/issues/597)
- Support for encryption of shared preferences [\#593](https://github.com/ably/ably-java/issues/593)
- RSC7c TI1 addRequestIds on ClientOptions and requestId on ErrorInfo [\#574](https://github.com/ably/ably-java/issues/574)
- Review JDK 7 and Android API Level requirements [\#555](https://github.com/ably/ably-java/issues/555)

**Merged pull requests:**

- Fix Android release [\#695](https://github.com/ably/ably-java/pull/695) ([QuintinWillison](https://github.com/QuintinWillison))
- Release/1.2.7 [\#693](https://github.com/ably/ably-java/pull/693) ([QuintinWillison](https://github.com/QuintinWillison))
- Increase minimum SDK version to Android 4.1 \(Jelly Bean, API Level 16\) [\#691](https://github.com/ably/ably-java/pull/691) ([KacperKluka](https://github.com/KacperKluka))
- Throws exception when AuthOptions are initialized with an empty string [\#690](https://github.com/ably/ably-java/pull/690) ([martin-morek](https://github.com/martin-morek))
- Removed forName method  [\#689](https://github.com/ably/ably-java/pull/689) ([martin-morek](https://github.com/martin-morek))
- Updated Firebase cloud messaging dependency [\#687](https://github.com/ably/ably-java/pull/687) ([martin-morek](https://github.com/martin-morek))
- Unified custom toString\(\) method implementations to use curly bracket… [\#683](https://github.com/ably/ably-java/pull/683) ([martin-morek](https://github.com/martin-morek))
- Support for encryption of shared preferences [\#681](https://github.com/ably/ably-java/pull/681) ([martin-morek](https://github.com/martin-morek))
- Add request\_id query param if addRequestIds is enabled [\#678](https://github.com/ably/ably-java/pull/678) ([martin-morek](https://github.com/martin-morek))
- Using a clientId should no longer be forcing token auth [\#675](https://github.com/ably/ably-java/pull/675) ([martin-morek](https://github.com/martin-morek))
- Checking if error code is 403 and failing connection [\#672](https://github.com/ably/ably-java/pull/672) ([martin-morek](https://github.com/martin-morek))
- Add Ably-Agent header [\#671](https://github.com/ably/ably-java/pull/671) ([KacperKluka](https://github.com/KacperKluka))
- Release/1.2.6 [\#670](https://github.com/ably/ably-java/pull/670) ([QuintinWillison](https://github.com/QuintinWillison))
- Changing Capability.addResource\(\) to take varargs as last parameter [\#664](https://github.com/ably/ably-java/pull/664) ([Thunderforge](https://github.com/Thunderforge))

## [v1.2.6](https://github.com/ably/ably-java/tree/v1.2.6) (2021-05-12)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.5...v1.2.6)

**Fixed bugs:**

- Fix channel presence members [\#669](https://github.com/ably/ably-java/pull/669) ([sacOO7](https://github.com/sacOO7))

**Closed issues:**

- Android 4.2.2 cannot connect anymore [\#666](https://github.com/ably/ably-java/issues/666)
- Formalise Coding Style [\#537](https://github.com/ably/ably-java/issues/537)

**Merged pull requests:**

- Readme: Remove Bintray and Update Gradle Instructions [\#667](https://github.com/ably/ably-java/pull/667) ([QuintinWillison](https://github.com/QuintinWillison))
- Conform license and copyright [\#660](https://github.com/ably/ably-java/pull/660) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.5](https://github.com/ably/ably-java/tree/v1.2.5) (2021-03-04)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.4...v1.2.5)

**Fixed bugs:**

- Crypto.getRandomMessageId isn't working as intended [\#654](https://github.com/ably/ably-java/issues/654)
- Hosts class is not thread safe [\#650](https://github.com/ably/ably-java/issues/650)
- AblyBase.InternalChannels is not thread-safe [\#649](https://github.com/ably/ably-java/issues/649)
- Fix getRandomMessageId [\#656](https://github.com/ably/ably-java/pull/656) ([sacOO7](https://github.com/sacOO7))

**Merged pull requests:**

- Release/1.2.5 [\#658](https://github.com/ably/ably-java/pull/658) ([QuintinWillison](https://github.com/QuintinWillison))
- Makes the Hosts class safe to be called from any thread [\#657](https://github.com/ably/ably-java/pull/657) ([QuintinWillison](https://github.com/QuintinWillison))
- Improve channel map operations in respect of thread-safety [\#655](https://github.com/ably/ably-java/pull/655) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.4](https://github.com/ably/ably-java/tree/v1.2.4) (2021-03-02)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.3...v1.2.4)

**Fixed bugs:**

- Many instances of ConnectionWaiter spawned while app is running, with authentication token flow [\#651](https://github.com/ably/ably-java/issues/651)
- capability tokendetails adds to HTTP Request as a query parameter   [\#647](https://github.com/ably/ably-java/issues/647)
- ClientOptions idempotentRestPublishing default may be wrong [\#590](https://github.com/ably/ably-java/issues/590)
- Presence blocking get sometimes has missing members [\#467](https://github.com/ably/ably-java/issues/467)
- Remove empty capability query parameter [\#648](https://github.com/ably/ably-java/pull/648) ([vzhikserg](https://github.com/vzhikserg))
- Add unit test for idempotentRestPublishing in ClientOptions [\#636](https://github.com/ably/ably-java/pull/636) ([vzhikserg](https://github.com/vzhikserg))
- Fix Member Presence [\#607](https://github.com/ably/ably-java/pull/607) ([sacOO7](https://github.com/sacOO7))

**Closed issues:**

- on\(ConnectionState, listener\) marked as deprecated but used in documentation [\#640](https://github.com/ably/ably-java/issues/640)
- Potential breaking change in android-java v1.2.3 [\#638](https://github.com/ably/ably-java/issues/638)

**Merged pull requests:**

- Release/1.2.4 [\#653](https://github.com/ably/ably-java/pull/653) ([QuintinWillison](https://github.com/QuintinWillison))
- Unregister ConnectionWaiter listeners once connected [\#652](https://github.com/ably/ably-java/pull/652) ([QuintinWillison](https://github.com/QuintinWillison))
- Update references from 1 -\> l to match client spec [\#646](https://github.com/ably/ably-java/pull/646) ([natdempk](https://github.com/natdempk))
- Add workflow status badges [\#645](https://github.com/ably/ably-java/pull/645) ([QuintinWillison](https://github.com/QuintinWillison))
- Add maintainers file [\#644](https://github.com/ably/ably-java/pull/644) ([niksilver](https://github.com/niksilver))
- Add workflows [\#643](https://github.com/ably/ably-java/pull/643) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix CI pipeline [\#642](https://github.com/ably/ably-java/pull/642) ([vzhikserg](https://github.com/vzhikserg))
- Fix/doc 233 update readme [\#641](https://github.com/ably/ably-java/pull/641) ([tbedford](https://github.com/tbedford))
- Log error message to get clear understanding of exception [\#632](https://github.com/ably/ably-java/pull/632) ([sacOO7](https://github.com/sacOO7))
- Refactor MessageExtras [\#595](https://github.com/ably/ably-java/pull/595) ([sacOO7](https://github.com/sacOO7))

## [v1.2.3](https://github.com/ably/ably-java/tree/v1.2.3) (2020-11-23)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.2...v1.2.3)

**Implemented enhancements:**

- Defaults: Generate environment fallbacks [\#603](https://github.com/ably/ably-java/issues/603)
- Improve error messages for channel attach when realtime is not active [\#594](https://github.com/ably/ably-java/issues/594)
- Improve error messages for channel attach when realtime is not active [\#627](https://github.com/ably/ably-java/pull/627) ([vzhikserg](https://github.com/vzhikserg))
- Make Ably version more robust [\#619](https://github.com/ably/ably-java/pull/619) ([vzhikserg](https://github.com/vzhikserg))
- Defaults: Generate environment fallbacks [\#618](https://github.com/ably/ably-java/pull/618) ([vzhikserg](https://github.com/vzhikserg))
- Remove unnecessary calls to the toString method [\#617](https://github.com/ably/ably-java/pull/617) ([vzhikserg](https://github.com/vzhikserg))
- Remove redundant public keywords in the interfaces' definitions [\#608](https://github.com/ably/ably-java/pull/608) ([vzhikserg](https://github.com/vzhikserg))

**Fixed bugs:**

- connectionKey attribute missing from Message object [\#614](https://github.com/ably/ably-java/issues/614)
- Add connectionKey attribute missing from the Message object [\#630](https://github.com/ably/ably-java/pull/630) ([vzhikserg](https://github.com/vzhikserg))

**Closed issues:**

- Add/modify generate environment fallback tests [\#628](https://github.com/ably/ably-java/issues/628)

**Merged pull requests:**

- Release/1.2.3 [\#633](https://github.com/ably/ably-java/pull/633) ([QuintinWillison](https://github.com/QuintinWillison))
- Refactor unit tests related to hosts and environmental fallbacks [\#629](https://github.com/ably/ably-java/pull/629) ([vzhikserg](https://github.com/vzhikserg))
- Move tests for EventEmitter to unit tests [\#626](https://github.com/ably/ably-java/pull/626) ([vzhikserg](https://github.com/vzhikserg))
- Adopt more Groovy conventions in Gradle scripts [\#625](https://github.com/ably/ably-java/pull/625) ([QuintinWillison](https://github.com/QuintinWillison))
- Gradle conform and reformat [\#624](https://github.com/ably/ably-java/pull/624) ([QuintinWillison](https://github.com/QuintinWillison))
- Add verbose logs in push notification related code [\#623](https://github.com/ably/ably-java/pull/623) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix param and return javadoc statements [\#622](https://github.com/ably/ably-java/pull/622) ([vzhikserg](https://github.com/vzhikserg))
- Update EditorConfig [\#616](https://github.com/ably/ably-java/pull/616) ([QuintinWillison](https://github.com/QuintinWillison))
- Upgrade Gradle wrapper to version 6.6.1 [\#615](https://github.com/ably/ably-java/pull/615) ([QuintinWillison](https://github.com/QuintinWillison))
- Checkstyle: AvoidStarImport [\#613](https://github.com/ably/ably-java/pull/613) ([QuintinWillison](https://github.com/QuintinWillison))
- Checkstyle: UnusedImports [\#612](https://github.com/ably/ably-java/pull/612) ([QuintinWillison](https://github.com/QuintinWillison))
- Convert tabs to spaces in all Java source files [\#610](https://github.com/ably/ably-java/pull/610) ([QuintinWillison](https://github.com/QuintinWillison))
- Introduce Checkstyle [\#609](https://github.com/ably/ably-java/pull/609) ([QuintinWillison](https://github.com/QuintinWillison))
- Rest.publishBatch: support overloaded method that takes params [\#604](https://github.com/ably/ably-java/pull/604) ([SimonWoolf](https://github.com/SimonWoolf))

## [v1.2.2](https://github.com/ably/ably-java/tree/v1.2.2) (2020-09-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.1...v1.2.2)

**Implemented enhancements:**

- Build takes too long [\#510](https://github.com/ably/ably-java/issues/510)

**Fixed bugs:**

- Restoral of ActivationStateMachine events fails because not all event types have a no-argument constructor [\#598](https://github.com/ably/ably-java/issues/598)
- Fatal Exception on API level below 19 [\#596](https://github.com/ably/ably-java/issues/596)
- Replace use of StandardCharsets [\#601](https://github.com/ably/ably-java/pull/601) ([QuintinWillison](https://github.com/QuintinWillison))

**Closed issues:**

- ClientOptions should be a Builder State Machine [\#527](https://github.com/ably/ably-java/issues/527)

**Merged pull requests:**

- Release/1.2.2 [\#602](https://github.com/ably/ably-java/pull/602) ([QuintinWillison](https://github.com/QuintinWillison))
- Discard persisted events with non-nullary constructors [\#599](https://github.com/ably/ably-java/pull/599) ([tcard](https://github.com/tcard))
- Rename master to main [\#592](https://github.com/ably/ably-java/pull/592) ([QuintinWillison](https://github.com/QuintinWillison))
- Bump protocol version to 1.2 [\#591](https://github.com/ably/ably-java/pull/591) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.1](https://github.com/ably/ably-java/tree/v1.2.1) (2020-06-15)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.0...v1.2.1)

**Fixed bugs:**

- Address impact of change to interface on extras field on Message [\#580](https://github.com/ably/ably-java/issues/580)

**Merged pull requests:**

- Release/1.2.1 [\#585](https://github.com/ably/ably-java/pull/585) ([QuintinWillison](https://github.com/QuintinWillison))
- Support outbound message extras [\#581](https://github.com/ably/ably-java/pull/581) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.0](https://github.com/ably/ably-java/tree/v1.2.0) (2020-06-08)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.11...v1.2.0)

**Merged pull requests:**

- Version Bump and Change Log [\#578](https://github.com/ably/ably-java/pull/578) ([QuintinWillison](https://github.com/QuintinWillison))
- learnings from release 1.1.11 [\#577](https://github.com/ably/ably-java/pull/577) ([QuintinWillison](https://github.com/QuintinWillison))
- Version 1.2 [\#550](https://github.com/ably/ably-java/pull/550) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.1.11](https://github.com/ably/ably-java/tree/v1.1.11) (2020-05-18)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.10...v1.1.11)

**Merged pull requests:**

- Release/1.1.11 [\#575](https://github.com/ably/ably-java/pull/575) ([QuintinWillison](https://github.com/QuintinWillison))
- Push Activation State Machine: validate an already-registered device on activation [\#543](https://github.com/ably/ably-java/pull/543) ([paddybyers](https://github.com/paddybyers))

## [v1.1.10](https://github.com/ably/ably-java/tree/v1.1.10) (2020-03-04)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.9...v1.1.10)

**Implemented enhancements:**

- Remove capability to bundle messages [\#567](https://github.com/ably/ably-java/pull/567) ([QuintinWillison](https://github.com/QuintinWillison))

**Closed issues:**

- Avoid message bundling, conforming to updated RTL6d [\#548](https://github.com/ably/ably-java/issues/548)

**Merged pull requests:**

- Release/1.1.10 [\#568](https://github.com/ably/ably-java/pull/568) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.1.9](https://github.com/ably/ably-java/tree/v1.1.9) (2020-03-03)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.8...v1.1.9)

**Implemented enhancements:**

- Upload to Maven Central [\#505](https://github.com/ably/ably-java/issues/505)
- Maven deployment: add task for deploy to staging [\#560](https://github.com/ably/ably-java/pull/560) ([paddybyers](https://github.com/paddybyers))

**Fixed bugs:**

- ConnectionManager.checkConnectivity\(\) fails every time for Android 9 [\#541](https://github.com/ably/ably-java/issues/541)
- ably-java sometimes failing to decrypt Messages [\#531](https://github.com/ably/ably-java/issues/531)
- Channels visibility improvements [\#558](https://github.com/ably/ably-java/pull/558) ([QuintinWillison](https://github.com/QuintinWillison))
- ConnectionManager: use HTTPS for the internet-up check [\#542](https://github.com/ably/ably-java/pull/542) ([paddybyers](https://github.com/paddybyers))

**Closed issues:**

- Remove develop branch [\#547](https://github.com/ably/ably-java/issues/547)

**Merged pull requests:**

- Release/1.1.9 [\#564](https://github.com/ably/ably-java/pull/564) ([QuintinWillison](https://github.com/QuintinWillison))
- Get AndroidPushTest to pass again [\#553](https://github.com/ably/ably-java/pull/553) ([tcard](https://github.com/tcard))
- Fix reference to param that wasn't updated when param name changed. [\#552](https://github.com/ably/ably-java/pull/552) ([tcard](https://github.com/tcard))

## [v1.1.8](https://github.com/ably/ably-java/tree/v1.1.8) (2020-02-07)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.8-RC1...v1.1.8)

**Merged pull requests:**

- Update master in readiness for deleting develop [\#549](https://github.com/ably/ably-java/pull/549) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.1.8-RC1](https://github.com/ably/ably-java/tree/v1.1.8-RC1) (2019-12-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.7...v1.1.8-RC1)

**Fixed bugs:**

- Rework and reinstate invalid ConnectionManager tests [\#524](https://github.com/ably/ably-java/issues/524)
- After loss of connectivity, and transport closure due to timeout, the ConnectionManager still thinks the transport is active [\#495](https://github.com/ably/ably-java/issues/495)

## [v1.1.7](https://github.com/ably/ably-java/tree/v1.1.7) (2019-12-04)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.6...v1.1.7)

## [v1.1.6](https://github.com/ably/ably-java/tree/v1.1.6) (2019-11-15)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.5...v1.1.6)

**Implemented enhancements:**

- Github Pages docs website [\#507](https://github.com/ably/ably-java/issues/507)

**Fixed bugs:**

- Unexpected exception in WsClient causing connection errors [\#519](https://github.com/ably/ably-java/issues/519)
- bad rsv 4 error from WebsocketClient if transport is forced to close during handshake [\#503](https://github.com/ably/ably-java/issues/503)
- fromCipherKey does not match spec [\#492](https://github.com/ably/ably-java/issues/492)

**Closed issues:**

- HttpScheduler.AsyncRequest\<T\> Ignores withCredentials Parameter [\#517](https://github.com/ably/ably-java/issues/517)
- AblyRealtime should implement Autocloseable [\#514](https://github.com/ably/ably-java/issues/514)
- Indentation and Line Length [\#509](https://github.com/ably/ably-java/issues/509)

**Merged pull requests:**

- Update websocket dependency [\#520](https://github.com/ably/ably-java/pull/520) ([paddybyers](https://github.com/paddybyers))
- Fixes in HttpScheduler.AsyncRequest [\#518](https://github.com/ably/ably-java/pull/518) ([amihaiemil](https://github.com/amihaiemil))
- \#514 AblyRealtime implements Autocloseable [\#515](https://github.com/ably/ably-java/pull/515) ([amihaiemil](https://github.com/amihaiemil))
- ChannelOptions.withCipherKey + tests [\#513](https://github.com/ably/ably-java/pull/513) ([amihaiemil](https://github.com/amihaiemil))
- Added test for \#474 [\#511](https://github.com/ably/ably-java/pull/511) ([amihaiemil](https://github.com/amihaiemil))

## [v1.1.5](https://github.com/ably/ably-java/tree/v1.1.5) (2019-10-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.4...v1.1.5)

**Fixed bugs:**

- WebSocketTransport: don't null the wsConnection in onClose\(\) [\#500](https://github.com/ably/ably-java/pull/500) ([paddybyers](https://github.com/paddybyers))

## [v1.1.4](https://github.com/ably/ably-java/tree/v1.1.4) (2019-10-12)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.3...v1.1.4)

**Merged pull requests:**

- Connectionmanager deadlock fix [\#497](https://github.com/ably/ably-java/pull/497) ([paddybyers](https://github.com/paddybyers))
- Push: delete all locally persisted state when deregistering [\#494](https://github.com/ably/ably-java/pull/494) ([paddybyers](https://github.com/paddybyers))

## [v1.1.3](https://github.com/ably/ably-java/tree/v1.1.3) (2019-07-18)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.2...v1.1.3)

**Merged pull requests:**

- Async callback fix [\#493](https://github.com/ably/ably-java/pull/493) ([amsurana](https://github.com/amsurana))

## [v1.1.2](https://github.com/ably/ably-java/tree/v1.1.2) (2019-07-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.14...v1.1.2)

**Implemented enhancements:**

- Add interactive test notes to the README [\#486](https://github.com/ably/ably-java/issues/486)
- Add RTN20 support - react to operating system network connectivity events [\#415](https://github.com/ably/ably-java/issues/415)

**Fixed bugs:**

- Push problems with push-subscribe permission [\#484](https://github.com/ably/ably-java/issues/484)
- Push: LocaDevice.deviceSecret serialisation issue [\#480](https://github.com/ably/ably-java/issues/480)
- Push: LocalDevice.reset\(\) doesn't clear persisted device state [\#478](https://github.com/ably/ably-java/issues/478)
- PUSH\_ACTIVATE intent broadcast is not always sent when activating push [\#477](https://github.com/ably/ably-java/issues/477)
- Stop using deprecated FirebaseInstanceIdService [\#475](https://github.com/ably/ably-java/issues/475)
- Expired token never renewed [\#470](https://github.com/ably/ably-java/issues/470)
- Problem using ably-java with newrelic [\#258](https://github.com/ably/ably-java/issues/258)
- Presence: fix a couple test regressions [\#490](https://github.com/ably/ably-java/pull/490) ([paddybyers](https://github.com/paddybyers))

**Closed issues:**

- Push: late-initialised clientId not updated in LocalDevice [\#481](https://github.com/ably/ably-java/issues/481)
- Exceptions when attempting to send with null WsClient [\#447](https://github.com/ably/ably-java/issues/447)

**Merged pull requests:**

- README: add a note about the push example/test app [\#491](https://github.com/ably/ably-java/pull/491) ([paddybyers](https://github.com/paddybyers))
- Reenable REST publish tests that depend on idempotency [\#489](https://github.com/ably/ably-java/pull/489) ([paddybyers](https://github.com/paddybyers))
- ConnectionManager: ensure that cached token details are cleared on any connection error [\#487](https://github.com/ably/ably-java/pull/487) ([paddybyers](https://github.com/paddybyers))
- Push fixes for 112 [\#485](https://github.com/ably/ably-java/pull/485) ([paddybyers](https://github.com/paddybyers))
- Local device reset fix [\#479](https://github.com/ably/ably-java/pull/479) ([amsurana](https://github.com/amsurana))

## [v1.0.14](https://github.com/ably/ably-java/tree/v1.0.14) (2019-04-24)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.1...v1.0.14)

## [v1.1.1](https://github.com/ably/ably-java/tree/v1.1.1) (2019-04-10)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.13...v1.1.1)

**Closed issues:**

- ConcurrentModificationException in 1.1 when running multiple library instances [\#468](https://github.com/ably/ably-java/issues/468)

**Merged pull requests:**

- NetworkConnectivity: ensure all accesses to listeners set are synchronised [\#469](https://github.com/ably/ably-java/pull/469) ([paddybyers](https://github.com/paddybyers))
- Truncated firebase ID \(registration token\) logging [\#466](https://github.com/ably/ably-java/pull/466) ([amsurana](https://github.com/amsurana))
- Auth RSA4b1 spec update: conditional token validity check [\#463](https://github.com/ably/ably-java/pull/463) ([paddybyers](https://github.com/paddybyers))
- Add some notes about log options [\#461](https://github.com/ably/ably-java/pull/461) ([paddybyers](https://github.com/paddybyers))
- Feature matrix linked from README [\#458](https://github.com/ably/ably-java/pull/458) ([Srushtika](https://github.com/Srushtika))

## [v1.0.13](https://github.com/ably/ably-java/tree/v1.0.13) (2019-04-10)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.0...v1.0.13)

**Implemented enhancements:**

- Improve handling of clock skew [\#462](https://github.com/ably/ably-java/issues/462)

**Fixed bugs:**

- java.lang.NoClassDefFoundError: org/msgpack/value/Value [\#460](https://github.com/ably/ably-java/issues/460)
- Possible Realtime and REST authCallback race condition [\#459](https://github.com/ably/ably-java/issues/459)

## [v1.1.0](https://github.com/ably/ably-java/tree/v1.1.0) (2019-02-13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.12...v1.1.0)

## [v1.0.12](https://github.com/ably/ably-java/tree/v1.0.12) (2019-02-13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.11...v1.0.12)

**Merged pull requests:**

- Implemented feature Spec - TP4 [\#451](https://github.com/ably/ably-java/pull/451) ([amsurana](https://github.com/amsurana))
- Implemented Spec: TM3, Message.fromEncoded [\#446](https://github.com/ably/ably-java/pull/446) ([amsurana](https://github.com/amsurana))

## [v1.0.11](https://github.com/ably/ably-java/tree/v1.0.11) (2019-01-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.0-RC1...v1.0.11)

**Implemented enhancements:**

- Move Push.publish -\> PushAdmin.publish [\#379](https://github.com/ably/ably-java/issues/379)

**Fixed bugs:**

- InternalError when attempting to create a reattach timer [\#452](https://github.com/ably/ably-java/issues/452)
- Realtime Channel: exceptions thrown when attempting attach do not result in the client listener being called [\#448](https://github.com/ably/ably-java/issues/448)
- Readme refers to a nonexistant gradlew.bat [\#422](https://github.com/ably/ably-java/issues/422)

**Closed issues:**

- ConcurrentModificationException in 1.0 [\#321](https://github.com/ably/ably-java/issues/321)
- Intermittent connect\_token\_expire\_disconnected failure [\#183](https://github.com/ably/ably-java/issues/183)

**Merged pull requests:**

- Make the Channels collection a ConcurrentHashMap to permit mutation o… [\#454](https://github.com/ably/ably-java/pull/454) ([paddybyers](https://github.com/paddybyers))
- Wrap construction of Timer instances to handle exceptions … [\#453](https://github.com/ably/ably-java/pull/453) ([paddybyers](https://github.com/paddybyers))
- Attach exception handling [\#449](https://github.com/ably/ably-java/pull/449) ([paddybyers](https://github.com/paddybyers))

## [v1.1.0-RC1](https://github.com/ably/ably-java/tree/v1.1.0-RC1) (2018-12-13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.10...v1.1.0-RC1)

**Implemented enhancements:**

- Add support for remembered REST fallback host [\#431](https://github.com/ably/ably-java/issues/431)
- Update idempotent REST according to spec [\#413](https://github.com/ably/ably-java/issues/413)

**Closed issues:**

- EventEmitter: mutations of `listeners` within a listener callback shouldn't crash [\#424](https://github.com/ably/ably-java/issues/424)
- Fix failing tests [\#352](https://github.com/ably/ably-java/issues/352)

## [v1.0.10](https://github.com/ably/ably-java/tree/v1.0.10) (2018-12-13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.9...v1.0.10)

**Merged pull requests:**

- Implemented RTE6a specification [\#444](https://github.com/ably/ably-java/pull/444) ([amsurana](https://github.com/amsurana))
- Add .editorconfig [\#443](https://github.com/ably/ably-java/pull/443) ([paddybyers](https://github.com/paddybyers))
- Release 1.0.9 [\#442](https://github.com/ably/ably-java/pull/442) ([paddybyers](https://github.com/paddybyers))
- Expose msgpack serialisers/deserialisers for Message, PresenceMessage [\#440](https://github.com/ably/ably-java/pull/440) ([paddybyers](https://github.com/paddybyers))
- Add support for bulk rest publish API [\#439](https://github.com/ably/ably-java/pull/439) ([paddybyers](https://github.com/paddybyers))
- RTL6c: implement transient realtime publishing [\#436](https://github.com/ably/ably-java/pull/436) ([paddybyers](https://github.com/paddybyers))
- Implement idempotent REST publishing [\#435](https://github.com/ably/ably-java/pull/435) ([paddybyers](https://github.com/paddybyers))
- Add support for ErrorInfo.href \(TI4/TI5\) [\#434](https://github.com/ably/ably-java/pull/434) ([paddybyers](https://github.com/paddybyers))
-  RSC15f: implement fallback affinity [\#433](https://github.com/ably/ably-java/pull/433) ([paddybyers](https://github.com/paddybyers))
- Pass the environment option into echoserver JWT requests [\#432](https://github.com/ably/ably-java/pull/432) ([paddybyers](https://github.com/paddybyers))
- Abstract getting environment variables for tests [\#414](https://github.com/ably/ably-java/pull/414) ([paddybyers](https://github.com/paddybyers))

## [v1.0.9](https://github.com/ably/ably-java/tree/v1.0.9) (2018-12-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.8...v1.0.9)

**Closed issues:**

- Idempotent publishing is not enabled in the upcoming 1.1 release [\#438](https://github.com/ably/ably-java/issues/438)
- Failed to resolve: io.ably:ably-android:1.0.8 [\#429](https://github.com/ably/ably-java/issues/429)

## [v1.0.8](https://github.com/ably/ably-java/tree/v1.0.8) (2018-11-03)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.7...v1.0.8)

**Implemented enhancements:**

- Ensure request method accepts UPDATE, PATCH & DELETE verbs [\#416](https://github.com/ably/ably-java/issues/416)

**Closed issues:**

- Error in release mode due to missing proguard exclusion [\#427](https://github.com/ably/ably-java/issues/427)
- Exception when failing to decode a message with unexpected payload type [\#425](https://github.com/ably/ably-java/issues/425)
- Recover resume not working [\#423](https://github.com/ably/ably-java/issues/423)

## [v1.0.7](https://github.com/ably/ably-java/tree/v1.0.7) (2018-08-16)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.6...v1.0.7)

**Closed issues:**

- IllegalStateException scheduling transport activity timer [\#418](https://github.com/ably/ably-java/issues/418)

**Merged pull requests:**

- Release 1.0.6 [\#412](https://github.com/ably/ably-java/pull/412) ([funkyboy](https://github.com/funkyboy))

## [v1.0.6](https://github.com/ably/ably-java/tree/v1.0.6) (2018-07-25)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.5...v1.0.6)

**Fixed bugs:**

- ably-java gets into a channel attach retry loop [\#410](https://github.com/ably/ably-java/issues/410)

**Merged pull requests:**

- RTL13b: ensure that detached+error responses form the server do not result in a busy loop of attach requests [\#411](https://github.com/ably/ably-java/pull/411) ([paddybyers](https://github.com/paddybyers))

## [v1.0.5](https://github.com/ably/ably-java/tree/v1.0.5) (2018-07-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.4...v1.0.5)

**Implemented enhancements:**

- Async HTTP thread pool issues [\#405](https://github.com/ably/ably-java/issues/405)
- Implement connection state freshness check [\#358](https://github.com/ably/ably-java/issues/358)

**Fixed bugs:**

- "Attempt to invoke virtual method 'int io.ably.lib.types.ProtocolMessage$Action.ordinal\(\)' on a null object reference" [\#398](https://github.com/ably/ably-java/issues/398)
- Exit blocked by Ably Realtime when main thread exits  [\#73](https://github.com/ably/ably-java/issues/73)

**Merged pull requests:**

- Release 1.0.5 [\#409](https://github.com/ably/ably-java/pull/409) ([paddybyers](https://github.com/paddybyers))
- Fix problem with the asyncHttp threadpool [\#408](https://github.com/ably/ably-java/pull/408) ([paddybyers](https://github.com/paddybyers))
- Exit with a non zero code if any of the two suites \(realtime or rest\) fails [\#407](https://github.com/ably/ably-java/pull/407) ([funkyboy](https://github.com/funkyboy))
- Fix some flaky tests [\#406](https://github.com/ably/ably-java/pull/406) ([funkyboy](https://github.com/funkyboy))
- Fix cm thread exit [\#404](https://github.com/ably/ably-java/pull/404) ([paddybyers](https://github.com/paddybyers))
- Trigger Travis when a branch name ends with -ci [\#402](https://github.com/ably/ably-java/pull/402) ([funkyboy](https://github.com/funkyboy))
- Add fast forward description in release process [\#401](https://github.com/ably/ably-java/pull/401) ([funkyboy](https://github.com/funkyboy))
- Improve release description [\#400](https://github.com/ably/ably-java/pull/400) ([funkyboy](https://github.com/funkyboy))
- Release 1.0.4 [\#399](https://github.com/ably/ably-java/pull/399) ([funkyboy](https://github.com/funkyboy))
- Ensure any Message.id is serialised [\#396](https://github.com/ably/ably-java/pull/396) ([paddybyers](https://github.com/paddybyers))
- Add Travis tests on Java 9 [\#395](https://github.com/ably/ably-java/pull/395) ([funkyboy](https://github.com/funkyboy))
- Add jwt tests [\#393](https://github.com/ably/ably-java/pull/393) ([funkyboy](https://github.com/funkyboy))
- Release 1.0.3 [\#392](https://github.com/ably/ably-java/pull/392) ([funkyboy](https://github.com/funkyboy))
- Prevent Travis timeout on Android tests [\#391](https://github.com/ably/ably-java/pull/391) ([funkyboy](https://github.com/funkyboy))
- Add connectionStateTtl [\#389](https://github.com/ably/ably-java/pull/389) ([funkyboy](https://github.com/funkyboy))
- Fix invalid data test [\#385](https://github.com/ably/ably-java/pull/385) ([funkyboy](https://github.com/funkyboy))
- Update README with supported platforms [\#380](https://github.com/ably/ably-java/pull/380) ([funkyboy](https://github.com/funkyboy))
- Fix creation of ErrorInfo when authCallback is invalid [\#378](https://github.com/ably/ably-java/pull/378) ([funkyboy](https://github.com/funkyboy))
- Use exception instead of deprecation notice [\#376](https://github.com/ably/ably-java/pull/376) ([funkyboy](https://github.com/funkyboy))
- Add/fix Travis tests [\#372](https://github.com/ably/ably-java/pull/372) ([funkyboy](https://github.com/funkyboy))
- Fix android:assembleRelease [\#370](https://github.com/ably/ably-java/pull/370) ([paddybyers](https://github.com/paddybyers))

## [v1.0.4](https://github.com/ably/ably-java/tree/v1.0.4) (2018-06-22)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.3...v1.0.4)

**Implemented enhancements:**

- Add test for JWT token [\#384](https://github.com/ably/ably-java/issues/384)

**Closed issues:**

- Maven devpendency failed [\#383](https://github.com/ably/ably-java/issues/383)

## [v1.0.3](https://github.com/ably/ably-java/tree/v1.0.3) (2018-05-18)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.2...v1.0.3)

**Implemented enhancements:**

- Add \(or fix\) CI tests on different platforms [\#364](https://github.com/ably/ably-java/issues/364)
- Document supported platforms [\#363](https://github.com/ably/ably-java/issues/363)
- 0.9 spec: fromJson [\#235](https://github.com/ably/ably-java/issues/235)
- For 0.9, replace deprecation notice by an exception in BaseMessage.encode.  [\#139](https://github.com/ably/ably-java/issues/139)

**Fixed bugs:**

- Received messages have no event names [\#366](https://github.com/ably/ably-java/issues/366)
- Tests failing because of "no output in the last 10m" [\#330](https://github.com/ably/ably-java/issues/330)

**Closed issues:**

- codes in the wrong order? [\#377](https://github.com/ably/ably-java/issues/377)
- android:assembleRelease is broken [\#369](https://github.com/ably/ably-java/issues/369)
- Gradle version should be upgraded [\#335](https://github.com/ably/ably-java/issues/335)
- Test failing on Travis \(JDK7, JDK8, Android\) [\#159](https://github.com/ably/ably-java/issues/159)
- Android, build and test documentation [\#38](https://github.com/ably/ably-java/issues/38)

## [v1.0.2](https://github.com/ably/ably-java/tree/v1.0.2) (2018-03-01)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.0-beta.push.1...v1.0.2)

**Fixed bugs:**

- When using token auth with client-side signing, renewing a token is broken [\#350](https://github.com/ably/ably-java/issues/350)
- Android push notification beta crash + API issue [\#323](https://github.com/ably/ably-java/issues/323)

**Closed issues:**

- Push release include problem [\#359](https://github.com/ably/ably-java/issues/359)
- TokenRequest.asJson should omit TTL if default [\#349](https://github.com/ably/ably-java/issues/349)
- Full test coverage of push functionality before GA release [\#346](https://github.com/ably/ably-java/issues/346)
- Push activate is not broadcasting result [\#326](https://github.com/ably/ably-java/issues/326)

**Merged pull requests:**

- Fix connectionmgr regressions [\#368](https://github.com/ably/ably-java/pull/368) ([paddybyers](https://github.com/paddybyers))
- Avoid depending on reference equality of interned strings and literals; this seems to fail sometimes on Android [\#367](https://github.com/ably/ably-java/pull/367) ([paddybyers](https://github.com/paddybyers))
- Update to latest gradle and tools plugins [\#362](https://github.com/ably/ably-java/pull/362) ([paddybyers](https://github.com/paddybyers))
- Auth.assertValidToken: always remove old token when force == true. [\#354](https://github.com/ably/ably-java/pull/354) ([tcard](https://github.com/tcard))
- Omit TTL in TokenRequest as JSON if unset. [\#353](https://github.com/ably/ably-java/pull/353) ([tcard](https://github.com/tcard))
- Add ability to generalize over a HTTP request being async or not. [\#347](https://github.com/ably/ably-java/pull/347) ([tcard](https://github.com/tcard))
- Implement and add test for AblyRealtime.connect\(\) [\#345](https://github.com/ably/ably-java/pull/345) ([paddybyers](https://github.com/paddybyers))
- Connectionmgr sync transport [\#344](https://github.com/ably/ably-java/pull/344) ([paddybyers](https://github.com/paddybyers))
- Fix issue where a close\(\) would not abort an existing in-progress connection [\#343](https://github.com/ably/ably-java/pull/343) ([paddybyers](https://github.com/paddybyers))
- New test RealtimeResumeTest.resume\_none [\#204](https://github.com/ably/ably-java/pull/204) ([trenouf](https://github.com/trenouf))

## [v1.1.0-beta.push.1](https://github.com/ably/ably-java/tree/v1.1.0-beta.push.1) (2017-08-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.1...v1.1.0-beta.push.1)

**Implemented enhancements:**

- Implement AblyRealtime.connect\(\) [\#305](https://github.com/ably/ably-java/issues/305)
- 0.9 presence spec amendments [\#265](https://github.com/ably/ably-java/issues/265)
- Remove calls to System.xxx.println\(\) [\#217](https://github.com/ably/ably-java/issues/217)
- Auth header included in HTTP requests [\#166](https://github.com/ably/ably-java/issues/166)
- autoConnect & useTokenAuth [\#27](https://github.com/ably/ably-java/issues/27)
- authParams & authMethod ClientOptions [\#25](https://github.com/ably/ably-java/issues/25)
- Sync complete method and/or callback [\#20](https://github.com/ably/ably-java/issues/20)

**Fixed bugs:**

- Race condition when lib is closed soon after being instantiated [\#319](https://github.com/ably/ably-java/issues/319)
- Crash inside a library [\#309](https://github.com/ably/ably-java/issues/309)
- Android System.out: \(ERROR\): io.ably.lib.transport.WebSocketTransport: No activity for 25000ms, closing connection [\#306](https://github.com/ably/ably-java/issues/306)
- RSC19 is not implemented according to the spec in 0.9 [\#278](https://github.com/ably/ably-java/issues/278)
- Invalid binary error message [\#247](https://github.com/ably/ably-java/issues/247)

**Closed issues:**

- Crash on Android with api level 18 and below [\#332](https://github.com/ably/ably-java/issues/332)
- 0.9 spec: UPDATE event, replacing ERROR [\#244](https://github.com/ably/ably-java/issues/244)

## [v1.0.1](https://github.com/ably/ably-java/tree/v1.0.1) (2017-08-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.0...v1.0.1)

**Implemented enhancements:**

- Allow custom transportParams [\#327](https://github.com/ably/ably-java/issues/327)
- 0.9 release [\#312](https://github.com/ably/ably-java/issues/312)

**Fixed bugs:**

- authHeaders are being included in requests to non authUrl endpoints [\#331](https://github.com/ably/ably-java/issues/331)
- 1.0 Maven dependency issue [\#325](https://github.com/ably/ably-java/issues/325)
- 1.0.0 sending v=0.9 [\#324](https://github.com/ably/ably-java/issues/324)
- 1.0 not automatically re-authing when token expires if initialized with key + clientId? [\#322](https://github.com/ably/ably-java/issues/322)

**Closed issues:**

- UTF-8 / ASCII detection issue in compile [\#334](https://github.com/ably/ably-java/issues/334)
- Allow authUrl to contain querystring params [\#328](https://github.com/ably/ably-java/issues/328)
- Regression in 1.0 ? [\#317](https://github.com/ably/ably-java/issues/317)
- Dependency management for ably-android [\#316](https://github.com/ably/ably-java/issues/316)
- Exceptions thrown in client onMessage callbacks are silently swallowed [\#314](https://github.com/ably/ably-java/issues/314)
- Explicitly define charset with String.getBytes\(\) [\#82](https://github.com/ably/ably-java/issues/82)

**Merged pull requests:**

- Spec RTC1f: implement support for ClientOptions.transportParams [\#342](https://github.com/ably/ably-java/pull/342) ([paddybyers](https://github.com/paddybyers))
- Implement spec for handling of queryParams in authURL [\#340](https://github.com/ably/ably-java/pull/340) ([paddybyers](https://github.com/paddybyers))
- Preemptive HTTP authentication [\#339](https://github.com/ably/ably-java/pull/339) ([paddybyers](https://github.com/paddybyers))
- Rest token renewal fix + tests [\#338](https://github.com/ably/ably-java/pull/338) ([paddybyers](https://github.com/paddybyers))
- Don't send authHeaders or authParams in calls to requestToken [\#337](https://github.com/ably/ably-java/pull/337) ([paddybyers](https://github.com/paddybyers))
- RSE2: Crypto.generateRandomKey\(\) implementation and test [\#336](https://github.com/ably/ably-java/pull/336) ([paddybyers](https://github.com/paddybyers))
- Replace StandardCharset.UTF-8 with Charset.forName\(“UTF-8”\) [\#333](https://github.com/ably/ably-java/pull/333) ([liuzhen2008](https://github.com/liuzhen2008))
- Crypto default 256 bit length like all other libraries [\#329](https://github.com/ably/ably-java/pull/329) ([mattheworiordan](https://github.com/mattheworiordan))
- Add log message if a client's listener throws an exception whilst handling a message [\#318](https://github.com/ably/ably-java/pull/318) ([paddybyers](https://github.com/paddybyers))

## [v1.0.0](https://github.com/ably/ably-java/tree/v1.0.0) (2017-03-08)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.9.0beta1...v1.0.0)

**Implemented enhancements:**

- Missing generateRandomKey method from Crypo [\#313](https://github.com/ably/ably-java/issues/313)

## [v0.9.0beta1](https://github.com/ably/ably-java/tree/v0.9.0beta1) (2017-03-07)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.11...v0.9.0beta1)

**Closed issues:**

- Test instructions [\#311](https://github.com/ably/ably-java/issues/311)
- 0.8.10  bug during dex translation [\#288](https://github.com/ably/ably-java/issues/288)

**Merged pull requests:**

- RSA8c1b: added authMethod to AuthOptions, implemented POST for authUrl [\#302](https://github.com/ably/ably-java/pull/302) ([psolstice](https://github.com/psolstice))
- RTN16b, RTN16c: added recoveryKey to Connection [\#301](https://github.com/ably/ably-java/pull/301) ([psolstice](https://github.com/psolstice))
- RTL15: moved Channel.attachSerial to Channel.properties.attachSerial [\#300](https://github.com/ably/ably-java/pull/300) ([psolstice](https://github.com/psolstice))
- RSE1, TB3: implementation and tests [\#299](https://github.com/ably/ably-java/pull/299) ([psolstice](https://github.com/psolstice))
- RTP6 fixes and tests [\#298](https://github.com/ably/ably-java/pull/298) ([psolstice](https://github.com/psolstice))
- Add test for handling of timeout on authUrl request [\#297](https://github.com/ably/ably-java/pull/297) ([paddybyers](https://github.com/paddybyers))
- RSA4c1: Wrap auth callback err [\#296](https://github.com/ably/ably-java/pull/296) ([paddybyers](https://github.com/paddybyers))
- Fixes and tests for RTP8i, RTP8f [\#294](https://github.com/ably/ably-java/pull/294) ([psolstice](https://github.com/psolstice))
- Fixed Android test suite compilation [\#290](https://github.com/ably/ably-java/pull/290) ([psolstice](https://github.com/psolstice))
- RTP11c, RTP11c, RTP11d implementation and tests [\#287](https://github.com/ably/ably-java/pull/287) ([psolstice](https://github.com/psolstice))
- Implement Auth.clientId and all associated tests \(except for presence-related\) [\#286](https://github.com/ably/ably-java/pull/286) ([paddybyers](https://github.com/paddybyers))
- RSA14 implementation, RSC1, RSC18 tests [\#284](https://github.com/ably/ably-java/pull/284) ([paddybyers](https://github.com/paddybyers))
- Remove proguard warnings for missing dependencies of msgpack library [\#281](https://github.com/ably/ably-java/pull/281) ([paddybyers](https://github.com/paddybyers))
- RTP2 \(except RTP2f\) tests, RTP18c test [\#277](https://github.com/ably/ably-java/pull/277) ([psolstice](https://github.com/psolstice))
- Remove ProtocolMessage.connectionKey [\#271](https://github.com/ably/ably-java/pull/271) ([paddybyers](https://github.com/paddybyers))
- Change unexpected field message into log entry instead of System.out [\#270](https://github.com/ably/ably-java/pull/270) ([paddybyers](https://github.com/paddybyers))
- Update workaround for Android msgpack bugs [\#269](https://github.com/ably/ably-java/pull/269) ([paddybyers](https://github.com/paddybyers))
- Parameterise tests so all applicable tests are run with text and binary protocol [\#268](https://github.com/ably/ably-java/pull/268) ([paddybyers](https://github.com/paddybyers))

## [v0.8.11](https://github.com/ably/ably-java/tree/v0.8.11) (2017-01-19)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.10-beta...v0.8.11)

**Implemented enhancements:**

- Remove deprecated ProtocolMessage\#connectionKey [\#262](https://github.com/ably/ably-java/issues/262)
- Add Proguard support [\#223](https://github.com/ably/ably-java/issues/223)

**Closed issues:**

- Message keys leaked \[incorrectly posted\] [\#282](https://github.com/ably/ably-java/issues/282)
- Add proguard warning for org.msgpack.core.buffer.\*\* [\#279](https://github.com/ably/ably-java/issues/279)
- Add support for ConnectionDetails.connectionStateTtl [\#267](https://github.com/ably/ably-java/issues/267)
- Msgpack truncates data member [\#261](https://github.com/ably/ably-java/issues/261)

## [v0.8.10-beta](https://github.com/ably/ably-java/tree/v0.8.10-beta) (2017-01-01)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.9...v0.8.10-beta)

## [v0.8.9](https://github.com/ably/ably-java/tree/v0.8.9) (2017-01-01)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.8...v0.8.9)

## [v0.8.8](https://github.com/ably/ably-java/tree/v0.8.8) (2017-01-01)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.7...v0.8.8)

**Fixed bugs:**

- authorise signature for 0.8 is incorrect [\#186](https://github.com/ably/ably-java/issues/186)

**Merged pull requests:**

- 0.8.8 [\#256](https://github.com/ably/ably-java/pull/256) ([psolstice](https://github.com/psolstice))
- Fixed race condition in failing Android test [\#249](https://github.com/ably/ably-java/pull/249) ([psolstice](https://github.com/psolstice))
- Fixed log message [\#248](https://github.com/ably/ably-java/pull/248) ([psolstice](https://github.com/psolstice))
- Android travis build [\#246](https://github.com/ably/ably-java/pull/246) ([psolstice](https://github.com/psolstice))
- Set minimum Android SDK version to 14 \(4.0+\) [\#243](https://github.com/ably/ably-java/pull/243) ([psolstice](https://github.com/psolstice))
- Updated README.md [\#242](https://github.com/ably/ably-java/pull/242) ([psolstice](https://github.com/psolstice))
- Fixed proguard definition for library [\#241](https://github.com/ably/ably-java/pull/241) ([psolstice](https://github.com/psolstice))
- Added Android library proguard configuration [\#240](https://github.com/ably/ably-java/pull/240) ([psolstice](https://github.com/psolstice))
- Fixes for Android testing, refactored gradle build scripts [\#239](https://github.com/ably/ably-java/pull/239) ([psolstice](https://github.com/psolstice))

## [v0.8.7](https://github.com/ably/ably-java/tree/v0.8.7) (2016-11-18)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.6...v0.8.7)

**Implemented enhancements:**

- Make `TokenRequest` constructor public [\#226](https://github.com/ably/ably-java/issues/226)
- Document what proguard flags needed to make lib work with proguard [\#198](https://github.com/ably/ably-java/issues/198)
- Change JCenter package name [\#171](https://github.com/ably/ably-java/issues/171)
- Move java-websocket dependency to jcenter [\#161](https://github.com/ably/ably-java/issues/161)
- Maven / Ivy support [\#28](https://github.com/ably/ably-java/issues/28)

**Fixed bugs:**

- PaginatedResult\#items should be an attribute [\#234](https://github.com/ably/ably-java/issues/234)
- ConnectionManager.failQueuedMessages\(\) does not remove messages once the callback is called [\#222](https://github.com/ably/ably-java/issues/222)
- ConnectionManager.setSuspendTime\(\) isn't called when a transport disconnects [\#220](https://github.com/ably/ably-java/issues/220)

**Merged pull requests:**

- Fixed issue 233, made changes to allow ITransport mocking [\#236](https://github.com/ably/ably-java/pull/236) ([psolstice](https://github.com/psolstice))

## [v0.8.6](https://github.com/ably/ably-java/tree/v0.8.6) (2016-11-15)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.5...v0.8.6)

**Merged pull requests:**

- Changed version to 0.8.6 [\#231](https://github.com/ably/ably-java/pull/231) ([psolstice](https://github.com/psolstice))
- Updated README and CHANGELOG for version 0.8.6 [\#230](https://github.com/ably/ably-java/pull/230) ([paddybyers](https://github.com/paddybyers))
- Relocated java-websocket library to bintray [\#229](https://github.com/ably/ably-java/pull/229) ([psolstice](https://github.com/psolstice))
- Made Auth.TokenRequest constructors public [\#228](https://github.com/ably/ably-java/pull/228) ([psolstice](https://github.com/psolstice))
- Fixed BuildConfig problems [\#227](https://github.com/ably/ably-java/pull/227) ([psolstice](https://github.com/psolstice))

## [v0.8.5](https://github.com/ably/ably-java/tree/v0.8.5) (2016-11-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.4...v0.8.5)

**Implemented enhancements:**

- Add reauth capability [\#129](https://github.com/ably/ably-java/issues/129)
- Remove unused HexDump file [\#81](https://github.com/ably/ably-java/issues/81)
- Final 0.8 spec updates [\#53](https://github.com/ably/ably-java/issues/53)
- HAS\_BACKLOG flag [\#6](https://github.com/ably/ably-java/issues/6)

**Fixed bugs:**

- Publish method succeeds in publishing but fails to call the success/failure callback [\#177](https://github.com/ably/ably-java/issues/177)
- Aeroplane mode appears to be removing listeners [\#170](https://github.com/ably/ably-java/issues/170)
- HTTP Version Not Supported [\#124](https://github.com/ably/ably-java/issues/124)
- CI is failing [\#110](https://github.com/ably/ably-java/issues/110)
- authorise should store AuthOptions and TokenParams as defaults for subsequent requests [\#104](https://github.com/ably/ably-java/issues/104)
- Host fallback for Realtime is not working [\#93](https://github.com/ably/ably-java/issues/93)
- Do not persist authorise attributes force & timestamp  [\#72](https://github.com/ably/ably-java/issues/72)
- Ensure generated pom file contains the correct public Github repo links [\#61](https://github.com/ably/ably-java/issues/61)
- Intermittent REST test issues [\#37](https://github.com/ably/ably-java/issues/37)
- Token expiry causes alternative host names to be used [\#14](https://github.com/ably/ably-java/issues/14)
- Releases [\#8](https://github.com/ably/ably-java/issues/8)

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
- Disabled more intermittently failing tests [\#194](https://github.com/ably/ably-java/pull/194) ([trenouf](https://github.com/trenouf))
- Various test fixes and disabling to get to 100% pass on travis build [\#193](https://github.com/ably/ably-java/pull/193) ([trenouf](https://github.com/trenouf))
- HttpTest: fixed test to allow for fallback hosts with same IP [\#192](https://github.com/ably/ably-java/pull/192) ([trenouf](https://github.com/trenouf))
- Don't modify ClientOptions; Fixed tests to not set both host and environment [\#190](https://github.com/ably/ably-java/pull/190) ([trenouf](https://github.com/trenouf))
- TO3k2,TO3k3: disallow restHost/realtimeHost with environment [\#189](https://github.com/ably/ably-java/pull/189) ([trenouf](https://github.com/trenouf))
- Separate java and android builds [\#188](https://github.com/ably/ably-java/pull/188) ([trenouf](https://github.com/trenouf))
- Fixed param order mix-up in new RestAuthAttributeTest.auth\_authorise\_… [\#185](https://github.com/ably/ably-java/pull/185) ([trenouf](https://github.com/trenouf))
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
- Important: Ensure DETACHED or DISCONNECTED with error is non-fatal [\#130](https://github.com/ably/ably-java/issues/130)
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
- Connection\#isConnected function [\#19](https://github.com/ably/ably-java/issues/19)
- Message publish overloaded without a listener [\#17](https://github.com/ably/ably-java/issues/17)
- Emit errors [\#16](https://github.com/ably/ably-java/issues/16)
- README to include code examples and follow common format [\#15](https://github.com/ably/ably-java/issues/15)

**Fixed bugs:**

- force is an attribute of AuthOptions, not an argument [\#103](https://github.com/ably/ably-java/issues/103)
- Presence enter, update, leave methods need to be overloaded [\#89](https://github.com/ably/ably-java/issues/89)
- Message constructor is inconsistent [\#87](https://github.com/ably/ably-java/issues/87)
- Channel state should be initialized not initialised for consistency [\#85](https://github.com/ably/ably-java/issues/85)
- Unsubscribe all and off all is missing [\#83](https://github.com/ably/ably-java/issues/83)
- Presence data assumed to be a string, Map not supported [\#75](https://github.com/ably/ably-java/issues/75)
- Host fallback for REST [\#54](https://github.com/ably/ably-java/issues/54)
- NullPointerException: Attempt to invoke interface method 'java.lang.String java.security.Principal.getName\(\)' on a null object reference [\#41](https://github.com/ably/ably-java/issues/41)
- Unable to deploy client lib in Android Studio project on OSX [\#39](https://github.com/ably/ably-java/issues/39)
- Java logLevel [\#26](https://github.com/ably/ably-java/issues/26)
- Timeout in test suite [\#24](https://github.com/ably/ably-java/issues/24)

**Closed issues:**

- Message & PresenceMessage Listeners provide arrays of messages, unlike the IDL [\#91](https://github.com/ably/ably-java/issues/91)
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

**Closed issues:**

- Re-enable temporarily disabled test [\#32](https://github.com/ably/ably-java/issues/32)
- Additional encoding / decoding tests [\#1](https://github.com/ably/ably-java/issues/1)

**Merged pull requests:**

- Async http [\#59](https://github.com/ably/ably-java/pull/59) ([paddybyers](https://github.com/paddybyers))
- changes to run provided RestInit test case [\#58](https://github.com/ably/ably-java/pull/58) ([gorodechnyj](https://github.com/gorodechnyj))
- Allow connection manager thread to exit when closed or failed, and re… [\#50](https://github.com/ably/ably-java/pull/50) ([paddybyers](https://github.com/paddybyers))
- Publish implicit attach [\#48](https://github.com/ably/ably-java/pull/48) ([paddybyers](https://github.com/paddybyers))
- Make inclusion of android-test project conditional on whether or not … [\#47](https://github.com/ably/ably-java/pull/47) ([paddybyers](https://github.com/paddybyers))

## [v0.8.0](https://github.com/ably/ably-java/tree/v0.8.0) (2015-05-07)

[Full Changelog](https://github.com/ably/ably-java/compare/e8643b9889584de797f83b48227c6f476c25be1d...v0.8.0)

**Implemented enhancements:**

- ClientOptions instead of Options [\#13](https://github.com/ably/ably-java/issues/13)
- EventEmitter interface [\#11](https://github.com/ably/ably-java/issues/11)
- Change pagination API [\#10](https://github.com/ably/ably-java/issues/10)
- Stats types are out of date [\#7](https://github.com/ably/ably-java/issues/7)

**Fixed bugs:**

- CipherParams type [\#12](https://github.com/ably/ably-java/issues/12)

**Closed issues:**

- Builds are not failing with the correct exit code [\#5](https://github.com/ably/ably-java/issues/5)

**Merged pull requests:**

- Fix comment in connection failure test [\#3](https://github.com/ably/ably-java/pull/3) ([mattheworiordan](https://github.com/mattheworiordan))
- Allow recovery string that includes -1 serial [\#2](https://github.com/ably/ably-java/pull/2) ([mattheworiordan](https://github.com/mattheworiordan))



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
