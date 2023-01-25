# Contributing

## Development Flow

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Ensure you have added suitable tests and the test suite is passing(`./gradlew java:testRestSuite java:testRealtimeSuite android:connectedAndroidTest`)
5. Push to the branch (`git push origin my-new-feature`)
6. Create a new Pull Request

### Building

The library consists of JRE-specific library (in `java/`) and an Android-specific library (in `android/`). The libraries are largely common-sourced; the `lib/` directory contains the common parts.

A gradle wrapper is included so these tasks can run without any prior installation of gradle. The Linux/OSX form of the commands, given below, is:

    ./gradlew <task name>

but on Windows there is a batch file:

    gradlew.bat <task name>

The JRE-specific library JAR is built with:

    ./gradlew java:jar

There is also a task to build a fat JAR containing the dependencies:

    ./gradlew java:fullJar

The Android-specific library AAR is built with:

    ./gradlew android:assemble

(The `ANDROID_HOME` environment variable must be set appropriately.)

### Code Standard

#### Checkstyle

We use [Checkstyle](https://checkstyle.org/) to enforce code style and spot for transgressions and illogical constructs
in our Java source files.
The Gradle build has been configured to run these on `java:assembleRelease`.
It does not run for the Android build yet.

You can run just the Checkstyle rules on their own using:

    ./gradlew checkstyleMain

#### CodeNarc

We use [CodeNarc](https://codenarc.org/) to enforce code style in our Gradle build scripts, which are all written in Groovy.

You can run CodeNarc over all build scripts in this repository using:

    ./gradlew checkWithCodenarc

For more details see the [`gradle-lint`](gradle-lint) project.


### Supported Platforms

We regression-test the library against a selection of Java and Android platforms (which will change over time, but usually consists of the versions that are supported upstream). Please refer to [.travis.yml](./.travis.yml) for the set of versions that currently undergo CI testing..

We'll happily support (and investigate reported problems with) any reasonably-widely-used platform, Java or Android.
If you find any compatibility issues, please [do raise an issue](https://github.com/ably/ably-java/issues/new) in this repository or [contact Ably customer support](https://support.ably.io/) for advice.

### IDE Support

We have a root [`.editorconfig`](.editorconfig) file, supporting [EditorConfig](https://editorconfig.org/), which should be of assistance within most IDEs. e.g.:

- [VS Code](https://code.visualstudio.com/) using the [EditorConfig plugin](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) using the [bundled plugin](https://www.jetbrains.com/help/idea/configuring-code-style.html#editorconfig).

#### Developing this library with an IDE

The gradle project files can be imported to create projects in IntelliJ IDEA, Eclipse and Android Studio.

#### Importing into IntelliJ

The top-level ably-java project can be imported into IntelliJ IDEA, enabling development of both the java and android projects. This has been tested with IntelliJ IDEA Ultimate 2017.2. To import into IDEA:

- do File->New->Project from Existing Sources...
- select ably-java/settings.gradle
- in the import dialog, check "Use auto-import" and uncheck "Create separate module per source set"
- select "ok"

This will create a project with separate java and android modules.

Interactive run/debug configurations to execute the unit tests can be created as follows:
- select Run->Edit configurations ...
- for the java project, create a new "JUnit" run configuration; or for the android project create a new "Android Instrumented Tests" configuration;
- select the Class as RealtimeSuite or RestSuite;
- select the relevant module for the classpath.

In order to run the Android configuration it is necessary to set up the Android SDK path by selecting a project of module and opening the module settings. The Android SDK needs to be added under Platform Settings->SDKs.

#### Importing into Eclipse

The top-level ably-java project can be imported into Eclipse, enabling development of the java project only. The Eclipse Android development plugin (ADT) is no longer supported. This has been tested with Eclipse Oxygen.2

To import into Eclipse:

- do File->Import->Gradle->Existing Gradle project;
- follow the wizard steps, selecting the ably-java root directory.

This will create two projects in the workspace; one for the top-level ably-java project, and one for the java project.

Interactive run/debug configurations for the java project can be created as follows:
- select Run->Run configurations ...
- create a new JUnit configuration
- select the java project;
- select the Class as RealtimeSuite or RestSuite;
- select JUnit 4 as the test runner.

#### Importing into Android studio

Android studio does not include the components required to support development of the java project, it is not capable of importing the multi-level ably-java gradle project. It is possible to import the android project as a standalone project into Android Studio by deleting the top-level settings.gradle file, which effectively decouples the android and java projects.

This has been tested with Android Studio 3.0.1.

To import into Android Studio:
- do Import project (Gradle, Eclipse ADT, etc);
- select ably-java/android/build.gradle;
- select OK to Gradle Sync.

This creates a single android project and module.

Configuration of Run/Debug configurations for running the unit tests on Android is the same as for IntelliJ IDEA (above).

## Running Tests

A gradle wrapper is included so these tasks can run without any prior installation of gradle. The Linux/OSX form of the commands, given below, is:

    ./gradlew <task name>

but on Windows there is a batch file:

    gradlew.bat <task name>

Tests are based on JUnit, and there are separate suites for the REST and Realtime libraries, with gradle tasks
for the JRE-specific library:

    ./gradlew java:testRestSuite

    ./gradlew java:testRealtimeSuite

To run tests against a specific host, specify in the environment:

    env ABLY_ENV=staging ./gradlew testRealtimeSuite

Tests will run against the sandbox environment by default.

Tests can be run on the Android-specific library. An Android device must be connected,
either a real device or the Android emulator.

    ./gradlew android:connectedAndroidTest

We also have a small, fledgling set of unit tests which do not communicate with Ably's servers.
The plan is to expand this collection of tests in due course:

    ./gradlew java:runUnitTests

### Interactive push tests

End-to-end tests for push notifications (ie where the Android client is the target) can be tested interactively via a [separate app](https://github.com/ably/push-example-android).
There are [instructions there](https://github.com/ably/push-example-android#using-this-app-yourself) for setting up the necessary FCM account, configuring the credentials and other parameters,
in order to get end-to-end FCM notifications working.

## Building an Android Archive (AAR) file locally

An [Android Archive (AAR)](https://developer.android.com/studio/projects/android-library) can be used in other projects as a dependency, unlike APKs. It does not contain dependencies, so you may face build and runtime errors if dependencies are not installed in projects which make use of the AAR.

- Set up the GPG signing configuration:
  - Create a GPG key pair: `gpg --expert --full-generate-key`.
  - Export a secret key ring file: `gpg --export-secret-keys -o ably-java-secring.gpg`.
  - Add the details of the GPG key pair inside `./gradle/gradle.properties`:
```bash
signing.keyId=XXXXXXXX
signing.password=ably-debug-key
signing.secretKeyRingFile=/Users/username/.ably/ably-java-secring.gpg
```
- Run `./gradlew android:assembleRelease` or `./gradlew android:assembleDebug`.

## Using `ably-java` / `ably-android` locally in other projects

You may wish to make changes to Ably Java or Ably Android, and test it immediately in a separate project. For example, during development for [Ably Flutter](https://github.com/ably/ably-flutter) which depends on `ably-android`, a bug was found in `ably-android`. A small fix was done, the AAR was built and tested in [Ably Flutter](https://github.com/ably/ably-flutter).

- Build the AAR: See [Building an Android Archive (AAR) file locally](#building-an-android-archive-aar-file-locally)
- Open the directory printed from the output of that command. Inside that folder, get the `ably-android-x.y.z.aar`, and place it your Android project's `libs/` directory. Create this directory if it doesn't exist.
- Add an `implementation` dependency on the `.aar`:
```groovy
implementation files('libs/ably-android-1.2.23.aar')
```
- Add the `implementation` (not `testImplementation`) dependencies found in `dependencies.gradle` to your project. This is because the `.aar` does not contain dependencies.
- Build/run your application.

## Release Process

This library uses [semantic versioning](http://semver.org/). For each release, the following needs to be done:

1. Create a branch for the release, named like `release/1.2.4` (where `1.2.4` is what you're releasing, being the new version)
2. Replace all references of the current version number with the new version number (check the [README.md](./README.md) and [common.gradle](./common.gradle)) and commit the changes
3. Run the [GitHub Changelog Generator](https://github.com/github-changelog-generator/github-changelog-generator) to update the [CHANGELOG](./CHANGELOG.md): something like: `github_changelog_generator -u ably -p ably-java --since-tag v1.2.3 --output delta.md` and then manually merge the delta contents in to the main change log (where `1.2.3` is the preceding release)
4. Commit [CHANGELOG](./CHANGELOG.md)
5. Make a PR against `main`
6. Once the PR is approved, merge it into `main`
7. From the updated `main` branch on your local workstation, assemble and upload:
    1. Comment out local `repository` lines in the two `maven.gradle` files temporarily (this is horrible but is [in our backlog to be fixed](https://github.com/ably/ably-java/issues/566))
    2. Run `./gradlew java:assembleRelease` to build and upload `ably-java` to Nexus staging repository
    3. Run `./gradlew android:assembleRelease` build and upload `ably-android` to Nexus staging repository
    4. Find the new staging repository using the [Nexus Repository Manager](https://oss.sonatype.org/#stagingRepositories)
    5. Check that it contains `ably-android` and `ably-java` releases
    6. "Close" it - this will take a few minutes during which time it will say (after a refresh of your browser) that "Activity: Operation in Progress"
    7. Once it has closed you will have "Release" available. You can allow it to "automatically drop" after successful release. A refresh or two later of the browser and the staging repository will have disappeared from the list (i.e. it's been dropped which implies it was released successfully)
    8. A [search for Ably packages](https://oss.sonatype.org/#nexus-search;quick~io.ably) should now list the new version for both `ably-android` and `ably-java`
8. Add a tag and push to origin - e.g.: `git tag v1.2.4 && git push origin v1.2.4`
9. Create the release on Github including populating the release notes
10. Create the entry on the [Ably Changelog](https://changelog.ably.com/) (via [headwayapp](https://headwayapp.co/))

### Signing

If you've not configured the signing key in your [Gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties) then release builds will complain:

    Cannot perform signing task ':java:signArchives' because it has no configured signatory

You need to [configure Signatory credentials](https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials), for example via the `gradle.properties` file in your `GRADLE_USER_HOME` folder (usually `~/.gradle`).

The GPG key file is internal and private to Ably.

### Sonatype Nexus for Maven Central

We publish to Maven Central via Sonatype's [OSSRH](https://issues.sonatype.org/browse/OSSRH-52871) / [Nexus](https://oss.sonatype.org/#nexus-search;quick~io.ably)
