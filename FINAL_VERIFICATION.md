# Final verification instructions

Run these in a clean checkout with JDK 17, Gradle 8.14.4, Android SDK Platform 36, and Build Tools 36.0.0 installed:

```bash
python tools/static_audit.py
python tools/security_audit.py
kotlinc \
  app/src/main/java/com/naveensmk/mira/AndroidIntentParser.kt \
  app/src/main/java/com/naveensmk/mira/ScreenContextPolicy.kt \
  app/src/main/java/com/naveensmk/mira/ScreenContextStore.kt \
  tools/PureKotlinSmoke.kt -include-runtime -d /tmp/mira-pure-smoke.jar
java -jar /tmp/mira-pure-smoke.jar
gradle wrapper --gradle-version 8.14.4
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Before generating a signed private Play bundle, configure the four release signing environment variables described in `RELEASE_SIGNING_AND_PLAY.md` and run:

```bash
./gradlew testDebugUnitTest lintRelease bundleRelease
```

This preparation environment ran both source audits and the focused pure-Kotlin routing/context smoke test. It does not have the Android SDK or Gradle installed, so no full Android compile, Android Lint, APK/AAB packaging, or device instrumentation was run. CI and device results are required before release.
