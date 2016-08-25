# Change Log

## [v0.8.3](https://github.com/ably/ably-java/tree/v0.8.3)

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

**Closed issues:**

- Fix missing JCE dependency on Travis [\#69](https://github.com/ably/ably-java/issues/69)

- Remove eclipse artifact [\#68](https://github.com/ably/ably-java/issues/68)

- Typo on Presence\#history javadoc [\#63](https://github.com/ably/ably-java/issues/63)

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

**Fixed bugs:**

- Gradle build should be able to build library without Android SDK installed [\#46](https://github.com/ably/ably-java/issues/46)

- Token authentication "Request mac doesn't match" [\#40](https://github.com/ably/ably-java/issues/40)

- Re-enable temporarily disabled test [\#31](https://github.com/ably/ably-java/issues/31)

**Closed issues:**

- Re-enable temporarily disabled test [\#32](https://github.com/ably/ably-java/issues/32)

**Merged pull requests:**

- Async http [\#59](https://github.com/ably/ably-java/pull/59) ([paddybyers](https://github.com/paddybyers))

- changes to run provided RestInit test case [\#58](https://github.com/ably/ably-java/pull/58) ([gorodechnyj](https://github.com/gorodechnyj))

- Allow connection manager thread to exit when closed or failed, and re… [\#50](https://github.com/ably/ably-java/pull/50) ([paddybyers](https://github.com/paddybyers))

- Add script for running tests in CI [\#49](https://github.com/ably/ably-java/pull/49) ([lmars](https://github.com/lmars))

- Publish implicit attach [\#48](https://github.com/ably/ably-java/pull/48) ([paddybyers](https://github.com/paddybyers))

- Make inclusion of android-test project conditional on whether or not … [\#47](https://github.com/ably/ably-java/pull/47) ([paddybyers](https://github.com/paddybyers))

- RSC1 and RSC2 - initialisation and default logging behaviour [\#43](https://github.com/ably/ably-java/pull/43) ([iliyakostadinov](https://github.com/iliyakostadinov))

- Key length case and ably common [\#35](https://github.com/ably/ably-java/pull/35) ([mattheworiordan](https://github.com/mattheworiordan))

## [v0.8.0](https://github.com/ably/ably-java/tree/v0.8.0) (2015-05-07)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
