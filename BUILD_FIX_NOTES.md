# MIRA build compatibility fix

This source archive updates the Kotlin toolchain to match the Kotlin metadata used by the current LiteRT-LM Android dependency.

Changes:
- Kotlin Gradle Plugin: 2.1.20 -> 2.4.21.
- Explicit kotlinx-coroutines-android dependency: 1.10.2 -> 1.11.0.
- Java source/target compatibility: 17.
- Kotlin JVM target: 17.
- Existing package/application ID, SDK settings, and app source architecture remain unchanged.

Build with JDK 17 and a Gradle version supported by Android Gradle Plugin 8.10.1 (Gradle 8.14.4 is the CI-pinned version).

The prior `Unable to strip ... .so` messages are native-library packaging warnings. They are distinct from the Kotlin metadata compilation failure.

Build status: source configuration updated and static checks run; this environment does not have Android SDK/Gradle configured, so a successful APK build has not been confirmed here.


## GitHub Actions compile errors reported 2026-10-18
- Replaced `ScrollView.LayoutParams` with `android.widget.FrameLayout.LayoutParams`, which is the correct layout-parameter type for a child in a ScrollView.
- Enabled Android `BuildConfig` generation because the About screen reads VERSION_NAME, VERSION_CODE, and APPLICATION_ID.
- Updated CI Gradle from 8.11.1 to 8.14.4 to address the Kotlin Gradle Plugin deprecation warning.
- A full Android Gradle build must still run in GitHub Actions to confirm compilation and reveal any subsequent errors.


## Wrapper reproducibility note
The ZIP still does not bundle `gradle-wrapper.jar` because it could not be retrieved or generated in this environment. Both checked-in GitHub Actions workflows provision Gradle 8.14.4 and generate the official wrapper before invoking it. For a local checkout, install Gradle 8.14.4 and run `gradle wrapper --gradle-version 8.14.4` once. A full Android build was not run in this preparation environment.
