# EngQuest Student: native Android app (design)

Date: 2026-11-10. Status: draft for the owner's approval.

## Why

The current student app (`com.engquest.app`, v1.8) is the website inside an app frame. The owner wants a real app instead, the way the teacher app was rebuilt as native Kotlin in October (`native-teacher/`). Students mainly open EngQuest to **send a photo of their task**, but they use almost every other menu too.

The 2026-11-05 decision ("no native student app, improve the website") is **reversed for Android only**. iPhone students keep using the website, which will be revamped later as a separate project.

## Goals

- A native Android app that looks and feels built for phones, in the **Quest** look the owner picked (mockups: `.superpowers/brainstorm/1987-1794296182/content/app-quest-v2.html`).
- Sending a task is fast and always one tap away.
- Every menu the website has today is reachable in the app.
- A **bell** inside the app lists everything that happened to the student, newest first.
- **Phone notifications** for the same events. They are optional: each student can turn them all off, or choose which kinds they get, in a Settings screen.
- Ships as an update to the existing student app, so students don't uninstall or log in from scratch.

## Non-goals

- No iPhone app. No changes to the student website in this project, except that the bell's events also become available to the website later if wanted.
- No Google Play listing. Updates go through the existing in-app update notice (`app-version.json`).
- No student passwords or PINs (the owner dropped the PIN idea).
- No new features beyond today's menus, apart from the bell, notifications, Settings and the level title.

## Anti-goals (would count as failure even if it works)

- It still feels like the website in a frame.
- The send button dominates the screen. The owner asked for it smaller than the first mockup.
- Students get buzzed with no way to turn it off.
- Any teacher-only data or action shows up in the student app.

## The look (owner chose)

- **Quest style:** playful, game-like. Rounded font (Nunito or the closest bundled equivalent), soft lavender-white background (`#eef2ff`), white rounded cards.
- **Header:** a coloured gradient band greeting the student by name ("Good morning 👋 / Dimas Pratama") with a class pill ("XE2 · No. 14").
- **Grade colours:** Grade 10 is blue (`#1d4ed8` → `#1e40af` → `#3730a3` gradient). Grade 11 is teal, built the same way from `#0f766e`. Orange (`#f59e0b` → `#ea580c`) is the "act" colour, used for the Send button and the level ring.
- **Level card:** overlaps the header, with a ring that fills toward the next level, the level number inside, a **level title** beside it (e.g. "Word Warrior"), and "1,240 XP · 160 XP to level 8".
- **Stat chips:** Score (blue), Tasks done (green), Strikes (red).

## Layout (owner chose layout A)

The bottom bar has five tabs: **Home · Scores · Send · Learn · More**.

- **Send** is a small orange rounded-square camera button, about the size of the other tabs and lifted slightly above the bar. It is not a big floating circle.
- The **bell** sits in the top-right of the Home header, with a red dot when there's something unread.

| Tab | Contains |
|---|---|
| Home | Greeting header, level card, three stat chips, "What's new" (latest 3 bell items, with "See all" opening the bell), recent attendance |
| Scores | Today's Scores page: chapter scores, final scores |
| Send | Pick the chapter (chips; done chapters ticked), Take photo / From gallery, previews with + to add more, "Send to teacher". Below it: my past submissions with their status (waiting / approved with score / sent back) and delete for unreviewed ones, as the website allows today |
| Learn | Materials, Assignments, Reading, My Vocabulary (with the weekly 30-word cap and countdown) |
| More | **My progress:** Level/XP, My Statistics, Task Status, Attendance, My Strikes. **My class:** Student List. **App:** Settings, Log out |

## Login (unchanged from the website)

The student picks a grade, then a class, then their name. The app stores class, absen number and name.

- Like `shared/js/student.js` `checkRoster` does today, on each start the app checks the saved name against `getStudents`. If the student was renumbered, it follows them to their new number. If they left the class, it asks them to pick again.
- On the first start after updating from v1.8, the old app's saved grade (SharedPreferences `engquest/grade`) can preselect the grade. The student still picks their name once, because the old app kept the profile in the WebView's localStorage. *(Builder: if the WebView's localStorage can be read cheaply on upgrade, use it to skip this step. Otherwise picking the name once is acceptable.)*

## The bell and notifications

### Events (owner approved this list)

1. Task approved, with the score
2. Task sent back to redo
3. Teacher gave XP (counter buttons and custom XP)
4. New level reached
5. Marked absent, sick or permit (not "present")
6. Got a strike
7. New material or assignment posted (whole class or grade)
8. New announcement from the teacher (matches its audience: grade 10, 11 or both)

### Bell

- A list of all events for this student, newest first, kept for at least 60 days.
- Unread ones are highlighted, and opening the bell marks them all read.
- Tapping an event goes to the matching screen (a task opens Send's history, XP opens Level/XP, and so on).
- The bell works whether notifications are on or off.

### Phone notifications

- **Off until the student says yes.** On Android 13+ the app asks for the notification permission after first login, with a short "Want a buzz when your task is approved?" screen, not straight away on launch. It is never forced.
- **Settings screen:** a master on/off switch, plus one switch per event kind (the 8 above). These preferences are saved on the server against the phone, so the server only sends what the student wants.
- Tapping a notification opens the app at the matching screen.
- Same Firebase project as the teacher app (`engquest-15b64`). The student app needs its own Android app entry in that project and its own `google-services.json` (git-ignored, like the teacher's).

### Server side (backend repo `Engquest-turso-lab`, one Worker update, deployed only with the owner's go-ahead)

Today the server keeps no dated record of many of these events. XP counters especially have no dates, which is why the website's "What's new" relies on a phone-side snapshot. So:

- **New table `student_events`:** id, student_id (null for class-wide events), class_id / grade (for class-wide events), kind, title, detail, link target, created_at (WIB, same format as `xp_awards.at`). Class-wide events (materials, assignments, announcements) are stored once and shown to every student they apply to.
- **Written by the existing teacher actions** at the moment they happen: reviewing a submission, XP counter changes and custom XP awards, attendance marking (absent/sick/permit only), adding a strike, posting a material/assignment, saving an announcement.
  - Level-up is detected after any XP write by comparing the level before and after. This uses the **same level curve as the website** (find it in `shared/js`), so the two never disagree.
- **Undo:** if the teacher removes XP, a strike or an attendance mark, the matching event is deleted rather than "undone". Keep it simple: no "XP removed" notification.
- **New table `student_devices`:** FCM token, student_id, preferences (JSON of the 8 switches plus master), updated_at. One phone holds one student; logging out or switching students re-registers the token.
- **New read actions:** `getMyEvents` (student's events plus class-wide ones, newest first, paged) and `registerStudentDevice` / `setNotifyPrefs`.
- **Sending:** reuse `pilot/push.mjs`. Sends happen after the write succeeds and never block or fail the teacher's action. Invalid tokens get removed, as the teacher side already does.
- Must not slow the teacher app. Pushes for a whole class (an announcement to ~36 students × 8 classes) go out in the background (`ctx.waitUntil`).
- The teacher app and teacher website need **no changes**. They keep calling the same actions, which now also record events.

## Data and offline

- The app talks to the same Worker (`engquest-api`) with the same old action API the website uses (`getStudents`, `getChapterScores`, `getMySubmissions`, `submitTaskPhoto`, `getMaterials`, `getVocabulary`, `addVocabulary`, etc.). There are no student-only shortcuts that bypass existing rules (the vocab weekly cap is still enforced by the server).
- Last-loaded data is cached on the phone (Room, like the teacher app), so screens open instantly and still show something with no signal, with a small "offline, last updated …" note.
- **Sending a task with no signal:** photos queue and send automatically when the signal returns, using the teacher app's outbox pattern (WorkManager plus a one-time `rid`, so a retry never double-sends). A queued send shows clearly as "Waiting to send" in Send's history.

## Updates and install

- **Same app identity:** `com.engquest.app`, signed with the same keystore as v1.8 (`D:\android-dev\keystore\engquest.jks`, see the APK memory), version **2.0** (versionCode 10). It installs over v1.8 as an update.
- Release the same way as before: same hosted file name `EngQuest-app-7fq93kx2.apk`, then bump `app-version.json` with notes. v1.8 phones then see the update dialog.
- v2.0 keeps the in-app update notice, now native (a banner like the teacher app's `UpdateBanner`).

## One release: 2.0 is the whole revamp (owner decided)

The owner would rather wait longer for one great release than see a string of 2.1, 2.2 updates. Students get nothing until **everything** in this document is done, and it ships as a single version **2.0**. There is no "open the website page inside the app" fallback. Every screen is native.

Internal build order (not separate releases; the owner sees progress on an emulator or test phone along the way):

1. App shell (Quest look, both grades, tabs), login, Home, Send (with offline queue and history), Scores, update banner.
2. Learn tab and More pages, in this order: My Vocabulary, Task Status, Level/XP, Attendance, Strikes, Materials, Assignments, Reading, My Statistics, Student List.
3. Server events, bell, notifications, Settings. The Worker deploy needs the owner's go-ahead. It can go live before 2.0 ships, because recording events changes nothing the website or teacher app shows.
4. Polish pass over all screens in both grades, then release.

## Builder's calls (my decisions; the owner can overrule)

- Code lives in this repo at `native-student/`, built and tested the same way as `native-teacher/` (Kotlin, Jetpack Compose, Room, WorkManager, OkHttp, FCM). Shared helper code is copied, not split into a shared library. Two small apps are easier to keep working than one shared module.
- **Level titles:** the website already has them (`grade10/index.html` `LEVEL_TITLES`: Beginner, Explorer, Achiever, Rising Star, Champion, Legend; level 6+ stays Legend). The app uses the same list and the same level curve (level N→N+1 needs 5+5N XP), so the app and website always agree.
- Links inside content (a material's PDF or Drive link, a Reading link) open in the phone's browser or viewer. They are not shown in a web view inside the app.
- Notifications are **off by default** and only switched on when the student says yes.
- "What's new" on Home shows the latest bell events.
- **Security note:** student reads are open today on the website (anyone choosing a class and name can see that student's pages). The bell and device registration follow the same rule. No new protection is added, and none is lost.

## Done looks like

- Each build step: builds with `native-student/build.ps1`, unit and UI tests pass, checked on a real phone or emulator in both grades (blue and teal). Then `node scripts/check-site.mjs` passes for any website-repo change, it's committed, and it's pushed only after the owner says "push it".
- Step 3 additionally: backend tests pass (`node --test pilot/test/...`), and the Worker is deployed only after "yes, deploy". The teacher app is checked to still work: approving a task on the teacher side produces a bell item, and a notification if switched on, on a test student's phone.
- Final release: APK and `app-version.json` updated, and a v1.8 phone updates to 2.0 in place.
