# EngQuest Teacher (native app)

The teacher app for phones, written in Kotlin with Jetpack Compose. It replaces the
old WebView wrapper (`D:\android-dev\EngQuestTeacher`) and keeps the same app id,
`com.engquest.teacher`, so it installs over the old one.

## Build

From the repo folder:

```
pwsh -File native-teacher/build.ps1            # tests + debug APK
pwsh -File native-teacher/build.ps1 -Release   # tests + signed release APK
```

The APK ends up in `D:\android-dev\build\native-teacher\app\outputs\apk\debug` (or `release`).
The script sets up Java, the Android SDK and Gradle from `D:\android-dev`, and makes the
`build` folders here point to D: so nothing large lands on C:.

## Signing

The release build reads the keystore passwords from
`D:\android-dev\keystore\keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`). That file is never in git. Without it the release build still works but
the APK is unsigned. Another location can be given with the `ENGQUEST_KEYSTORE_PROPERTIES`
environment variable.

## Where things are

- `app/src/main/java/com/engquest/teacher/domain/Core.kt` - the score, attendance and
  submission rules, ported from `teacher-app/js/core.js` (keep the two in step).
- `app/src/main/java/com/engquest/teacher/domain/Models.kt` - students, meetings,
  submissions and the 8 classes.
- `app/src/main/java/com/engquest/teacher/ui/theme/Theme.kt` - brand colour `#4338CA`,
  rounded cards.
- `gradle/libs.versions.toml` - every library version, in one place.
