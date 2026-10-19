# Lint and Gradle CI fixes

- Moved `android:windowLightNavigationBar` out of `values/styles.xml` into `values-v27/styles.xml`, so API 26 does not resolve an API 27-only theme attribute.
- Updated both GitHub Actions workflows to regenerate the Gradle wrapper at 8.14.4 on every run. This prevents a pre-existing wrapper pinned to 8.11.1 from overriding the Gradle version installed by setup-gradle.
- Updated build/release notes that still described Gradle 8.11.1.

The supplied CI log states there are two lint errors but only includes the details for `windowLightNavigationBar`. That reported issue is addressed here. The second lint error cannot be identified from the excerpt; review the full `lint-results-debug.txt` report if CI still fails. No full Android Gradle build was run in this environment.
