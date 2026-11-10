# EngQuest Student (native app, 2.0)

The student app for Android phones, written in Kotlin with Jetpack Compose. It replaces the
WebView app (`D:\android-dev\EngQuestApp`, v1.8) and keeps its app id `com.engquest.app` and
signing key, so it installs over it as an update.

Design: `docs/superpowers/specs/2026-11-10-native-student-app-design.md`.
Plan: `docs/superpowers/plans/2026-11-10-native-student-app.md`.

## Build

```
pwsh -File native-student/build.ps1            # tests + debug APK
pwsh -File native-student/build.ps1 -Release   # tests + signed release APK
pwsh -File native-student/build.ps1 -Tests ThemeTest,HomeTest   # only those tests
```

Output: `D:\android-dev\build\native-student\app\outputs\apk\...`. Signing reads
`D:\android-dev\keystore\keystore.properties` (same key as v1.8 and the teacher app).
Notifications need `app/google-services.json` (git-ignored, see FIREBASE-SETUP.md); without it
the app builds and runs without them.
