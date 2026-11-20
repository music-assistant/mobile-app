# Development environment

## Requirements

- JDK 21. The CI workflows use Temurin 21. The modules compile to JVM 17 bytecode.
- Android SDK. The project compiles against the SDK version in `android-compileSdk` in `gradle/libs.versions.toml`.
- For iOS: a Mac with Apple silicon and Xcode. Refer to [IOS-BUILD-INSTRUCTIONS](IOS-BUILD-INSTRUCTIONS.md).

The Gradle version is in `gradle/wrapper/gradle-wrapper.properties`. The library versions are in `gradle/libs.versions.toml`.

## iOS

Refer to [IOS-BUILD-INSTRUCTIONS](IOS-BUILD-INSTRUCTIONS.md) for these subjects:

- The tools and the JDK that the build needs
- The WebRTC framework setup
- The signing configuration
- The build commands for the simulator and for a device
- The tests and the troubleshooting

## Android

To build the debug app:

```bash
./gradlew :androidApp:assembleDebug
```

To build a release APK that is not debuggable, use the `selfSignedRelease` build type. It signs the APK with the debug keystore, so you can test the release performance locally.

```bash
./gradlew :androidApp:assembleSelfSignedRelease
```

## Structure

The project supports the Android and iOS targets. The shared code is in the Kotlin Multiplatform module `composeApp`. The platform apps `androidApp` and `iosApp` use it.

These modules are also part of the build:

- `sendspin-kmp`: the Sendspin player library. Refer to `sendspin-kmp/README.md`.
- `nav` and `ui-utils`: shared navigation and UI helpers.
- `shared-icons`: the icon sets.

## Tests and checks

Run the tests of the shared code (`commonTest` and `androidHostTest` of `composeApp`) on the JVM:

```bash
./gradlew :composeApp:testAndroidHostTest
```

Run the Compose UI tests. They are in the `androidApp` module and use Robolectric, so they run on the JVM. [Multiplatform Compose testing](https://kotlinlang.org/docs/multiplatform/compose-test.html) needs the desktop target to run on a local JVM, and the project does not have this target.

```bash
./gradlew :androidApp:testDebugUnitTest
```

Run the tests of the Sendspin library:

```bash
./gradlew :sendspin-kmp:testAndroidHostTest
```

Run Detekt on all modules. The CI runs the same check.

```bash
CI=true ./gradlew detektAll
```

Run Android Lint:

```bash
./gradlew :androidApp:lintDebug
```

## Running MA Server locally

You can run a local MA server in Docker with the included script:

```bash
./scripts/run-local-ma.sh
```

The script starts a server at `http://localhost:8095`. The server keeps its data in `.server`.
