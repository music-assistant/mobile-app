# Development environment

## iOS

See [IOS-BUILD-INSTRUCTIONS](IOS-BUILD-INSTRUCTIONS.md) for a full step-by-step guide covering:

- Required tools and JDK version (JDK 21 LTS required — JDK 25 is not supported)
- WebRTC framework setup
- Signing and provisioning configuration
- Build commands for simulator and physical device
- Known limitations and troubleshooting

## Android

To build the app:

```bash
./gradlew :androidApp:assembleDebug
```

To build a non-debuggable "release mode" APK for testing performance using your local debug keystore:

```bash
./gradlew :androidApp:assembleSelfSignedRelease
```

## Structure

The project currently supports the iOS and Android targets. Common code is held within a KMP library module (`composeApp`) which the two platform specific app modules then depend on (`androidApp` and `iosApp`).

## Writing/running tests

Tests for shared multiplatform code live in the `composeApp` module's `commonTest` source set. These can be run locally in the JVM for the Android target using `./gradlew :composeApp:testAndroidHostTest`.

Tests for Compose UI code are in the `androidApp` module. This is because [multiplatform Compose testing is currently still experimental](https://kotlinlang.org/docs/multiplatform/compose-test.html) and tests written using the multiplatform approach cannot be easily run in a local JVM yet without the desktop target (which this project doesn't use). The Compose tests can be run with `./gradlew :androidApp:testDebug`.

## Running MA Server locally

You can run a local MA server using Docker using the included convenience script:

```bash
./scripts/run-local-ma.sh
```

This will start up a server at `http://localhost:8095`. Server data is stored in `.server`.