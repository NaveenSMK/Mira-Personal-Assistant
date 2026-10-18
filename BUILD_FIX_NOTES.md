# MIRA build compatibility fix

This source archive updates the Kotlin toolchain to match the Kotlin metadata used by the current LiteRT-LM Android dependency.

Changes:
- Kotlin Gradle Plugin: 2.1.20 -> 2.4.21.
- Explicit kotlinx-coroutines-android dependency: 1.10.2 -> 1.11.0.
- Java source/target compatibility: 17.
- Kotlin JVM target: 17.
- Existing package/application ID, SDK settings, and app source architecture remain unchanged.

Build with JDK 17 and a Gradle version supported by Android Gradle Plugin 8.10.1 (Gradle 8.11.1 is a suitable pinned version).

The prior `Unable to strip ... .so` messages are native-library packaging warnings. They are distinct from the Kotlin metadata compilation failure.

Build status: source configuration updated and static checks run; this environment does not have Android SDK/Gradle configured, so a successful APK build has not been confirmed here.
