# English Quest

Class website and Android apps for the English classes: Grade 10 (X E-1 to X E-5) and Grade 11 (XI F-7 to XI F-9).

- **Website:** https://engquestviska.github.io (GitHub Pages, served from `main`; a push goes live in about a minute)
- **Server:** the Cloudflare Worker `engquest-api` at `https://engquest-api.engquestviska.workers.dev/exec`, with data in the Turso database `engquest-prod`. Its code lives in the separate folder `Engquest-turso-lab` (not in this repo).

## Folders

| Folder | What it is |
|---|---|
| `grade10/`, `grade11/` | Student pages for each grade (home, scores, tasks, attendance, vocabulary, submissions...) |
| `teacher/` | Teacher pages (login, scores, attendance, XP, strikes, materials, announcements, submissions, settings) |
| `shared/js/`, `shared/css/` | Code and styles used by every page. `api.js` holds the server address; `auth.js` the teacher login |
| `activities/` | Older classroom pages still in use (quiz, jeopardy, guess who, summative, submission status) |
| `native-student/` | Student Android app (Kotlin). APK + update info: `EngQuest-app-*.apk`, `app-version.json` |
| `native-teacher/` | Teacher Android app (Kotlin). APK + update info: `EngQuestTeacher-*.apk`, `teacher-app-version.json` |
| `next-semester/` | Next-semester profile pages; still on their own Google Apps Script server (`apps-script-next/`) |
| `apps-script/`, `chapter5-submission/` | Old Google Apps Script server code, kept only as a backup since the move to Cloudflare (2026-07-30) |
| `teacher-app/` | Old web version of the teacher app; the native app's rules were copied from `teacher-app/js/core.js` |
| `docs/` | Planning and reference docs. Some describe the old Google Sheets setup |
| `mockups/`, `samples/` | Design mockups and sample files, not served to students |

The root pages (`quiz.html`, `strikes.html`...) only redirect to their new place.

## Before you push

```
node scripts/check-site.mjs
node --test scripts/chronicle-data.test.mjs
```

The first must say `PASS`. Teacher pages must never be linked from student pages.

## Android apps

Build with `build.ps1` in `native-student/` or `native-teacher/` (output goes to `D:\android-dev\build\...`; see each folder's README). To release: copy the APK to the repo root, update `app-version.json` or `teacher-app-version.json`, commit and push. The apps check that file for updates.
