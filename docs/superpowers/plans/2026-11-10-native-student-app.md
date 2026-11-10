# EngQuest Student 2.0 (native Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Owner's choice for this project: native/inline execution (one builder, one final review). No per-task subagents.**

**Goal:** Replace the WebView student app (`com.engquest.app` v1.8) with a native Kotlin/Compose app, v2.0, in the Quest look. It has every student menu, a bell of events, and opt-in phone notifications.

**Architecture:**
- **App:** a new `native-student/` Android project, cloned from `native-teacher/`'s setup. It talks to the existing Worker `engquest-api` with the same action API the website uses, caches replies in Room, and queues photo sends in a WorkManager outbox.
- **Server:** the backend (`C:\Users\acer\Documents\Engquest-turso-lab`, branch `turso-migration`) gains two tables (`student_events`, `student_devices`). The existing teacher actions write events, and FCM pushes go out through `pilot/push.mjs`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, WorkManager, OkHttp, Firebase Messaging, all at the versions in `native-teacher/gradle/libs.versions.toml`. Backend: Node ESM, libSQL/Turso, Cloudflare Worker, `node --test`.

**Spec:** `docs/superpowers/specs/2026-11-10-native-student-app-design.md`. Mockups: `.superpowers/brainstorm/1987-1794296182/content/app-quest-v2.html`.

## Global Constraints

- applicationId `com.engquest.app`, versionCode **10**, versionName **"2.0"**, minSdk 24. Signed with the same keystore as v1.8 (`D:\android-dev\keystore\engquest.jks`, alias `engquest`). Read it through `D:\android-dev\keystore\keystore.properties` like `native-teacher` does, after checking that the file points at `engquest.jks`.
- **One release.** Nothing is published to students (no APK replaced, no `app-version.json` bump) until Task 16. No screen may fall back to a WebView of the website.
- Build output on D: (junctions), like `native-teacher/build.ps1`. Nothing large on C:.
- **Colours:**
  - Grade 10 header gradient `#1d4ed8 → #1e40af → #3730a3`. Grade 11 teal built the same way from `#0f766e` (`#0f766e → #115e59 → #134e4a`).
  - Act colour (Send button, level ring) `#f59e0b → #ea580c`.
  - Background `#eef2ff`. Cards white, radius 18–22dp.
  - Font: Nunito (bundled in `res/font`).
- Level curve: level 1 starts at 0 XP; going from level N to N+1 needs `5 + 5N` XP (10, 15, 20 …). Titles `["Beginner","Explorer","Achiever","Rising Star","Champion","Legend"]`, index `min(level-1, 5)`.
- XP total uses the server's `leaderboard` view weights (VB×2, Answering×5, Presenting×20, DoingTask×20, HelpingHand×20, Quiz×10, plus custom). The app reads `Total` from `getAllActiveness`; it never recomputes it.
- Task keys are `C<chapter>T<task>` (see `grade10/submission.html:175`). Max 10 photos per send. Compress to longest side 1000 px, JPEG quality 50, like the website.
- Teacher-side behaviour and replies must not change. Every new server write is best-effort and never fails or slows a teacher action (push via `waitUntil`).
- **Approvals:** git push, Worker deploy and prod DB migration happen only after the owner says so. Use the terminal tool for `git push`. Commit only files you changed.

## Review Focus

1. **Student renumbered or left the class while the app was closed.** Expect the app to follow the name to the new number (same rule as `shared/js/student.js` `checkRoster`), or return to the name picker if the student is gone. Test: `RosterTest` in Task 6.
2. **A send queued offline, then the app killed.** Expect the send to resume when the signal is back, never sent twice (same `rid`), and the history to show "Waiting to send" until then. Test: `OutboxTest` in Task 10.
3. **Teacher removes XP, a strike or an absence right after giving it.** Expect the matching bell event to disappear, and no "removed" push. Test: `events undo` cases in Task 1.
4. **Notifications switched off in Settings or denied by Android.** Expect no push for switched-off kinds, while the bell still lists them. Test: `push respects prefs` in Task 2, and `SettingsTest` in Task 15.
5. **An announcement to "both" grades with ~280 students.** Expect one event row (not 280), and the teacher's save returning as fast as before. Test: `class-wide event stored once` in Task 1.

---

## Part A: Server (repo `Engquest-turso-lab`)

### Task 1: Event recording and `getMyEvents`

**Files:**
- Modify: `pilot/db/schema.sql`: add tables.
- Create: `pilot/events.mjs`: `levelFromXp`, `recordEvents`, `eventsFor`.
- Modify: `pilot/compat.mjs`: hook teacher actions, add `getMyEvents`.
- Test: `pilot/test/events.test.mjs` (uses `pilot/test/sqlite-db.mjs`).

**Schema:**
```sql
CREATE TABLE IF NOT EXISTS student_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  student_id INTEGER REFERENCES students(id) ON DELETE CASCADE, -- NULL = class-wide
  grade TEXT,          -- for class-wide rows: '10' | '11' | 'both'
  kind TEXT NOT NULL,  -- approved | sent_back | xp | level | attendance | strike | material | announcement
  title TEXT NOT NULL,
  detail TEXT NOT NULL DEFAULT '',
  link TEXT NOT NULL DEFAULT '',   -- app screen: send | levelxp | attendance | strikes | materials | assignments | home
  ref TEXT NOT NULL DEFAULT '',    -- undo key, e.g. 'xp:answering', 'att:2026-11-07', 'strike:<id>', 'sub:<id>'
  created_at TEXT NOT NULL         -- 'YYYY-MM-DD HH:MM' WIB, same as xp_awards.at
);
CREATE INDEX IF NOT EXISTS student_events_sid ON student_events(student_id, id);
```

**Interfaces:**
- Produces: `levelFromXp(xp) -> { level, into, needForNext }` (port of `grade10/index.html:309`).
- Produces: `recordEvents(db, rows[])`, which inserts rows and deletes rows older than 60 days in the same batch.
- Produces: `eventsFor(db, { sid, grade, beforeId, limit=30 })`, returning own rows plus class-wide rows matching `grade` or `'both'`, newest first, last 60 days only.
- Produces: action `getMyEvents {className, studentNo, beforeId?}` → `{ success, events: [{ id, kind, title, detail, link, at }] }`. Unknown student → `{ success: true, events: [] }`.

**Hooks (event title and detail, exact wording):**

| Action | Condition | Event row | Undo |
|---|---|---|---|
| `reviewTaskSubmission` | approved | kind `approved`, title "Task approved", detail `"<task label> · Score <n>"`, or `"<task label>"` if no score yet (score from `chapter_scores` chapter n+3, as `shared/js/student-home.js` does) | |
| `reviewTaskSubmission` | rejected | kind `sent_back`, title "Task sent back", detail `"<task label> · please redo it"` | |
| `incrementActiveness` | delta > 0 | kind `xp`, title `"+<delta×weight> XP"`, detail the column's display name (Vocabulary Box, Answering, Presenting, Doing Task, Helping Hand, Quiz), ref `xp:<column>` | delta < 0 deletes the newest row with that ref |
| `awardXp` | delta ≠ -1 | title `"+<points> XP"`, detail the reason, ref `xpa:<reason>` | delta -1 deletes the newest matching ref |
| `saveAttendance` | status A, S or P (check the stored codes in `attendance`) | kind `attendance`, title "Absent", "Sick" or "Permit", detail the date as `"7 Nov"`, ref `att:<date>` | status cleared, or changed to present, deletes rows with that ref |
| `removeAttendanceMeeting` | | | deletes `att:<date>` rows for that class |
| `addStrike` | | kind `strike`, title "You got a strike", detail the reason, ref `strike:<new id>` | `removeStrike` deletes the newest strike event for that student |
| `addMaterial` | | class-wide, grade = p.grade, kind `material`, title "New material", detail the title | |
| `setAnnouncement` | | class-wide, grade = audience, kind `announcement`, title the announcement title, detail the first 120 chars of the body | |

**Level-up:** after any XP write for one student, compare `levelFromXp` of the leaderboard `total` read before and after the write. If it went up, add kind `level`, title `"Level <n> reached!"`, detail the level title.

- [ ] **Step 1:** Write `pilot/test/events.test.mjs`. Tests:
  - `levelFromXp(0)` → `{level:1, into:0, needForNext:10}`; `levelFromXp(10)` → level 2; `levelFromXp(25)` → level 3.
  - Approve writes one `approved` event.
  - `incrementActiveness` answering +1 writes `+5 XP`; then -1 removes it.
  - Crossing 10 XP adds a `level` event.
  - Attendance A then cleared leaves 0 events.
  - **events undo**: `addStrike` then `removeStrike` leaves 0 strike events.
  - **class-wide event stored once**: `setAnnouncement` audience both → exactly 1 row with `student_id IS NULL`; `getMyEvents` for one G10 and one G11 student both include it; a G11-only material doesn't reach G10.
  - Rows older than 60 days are not returned.
  - Every existing teacher action still returns the same reply.
- [ ] **Step 2:** Run `node --test pilot/test/events.test.mjs`. Expect FAIL (no module).
- [ ] **Step 3:** Implement `pilot/events.mjs` and the hooks. Put every hook inside try/catch, so an event failure never changes the action's reply.
- [ ] **Step 4:** Run `node --test pilot/test/compat.test.mjs pilot/test/worker-cors.test.mjs pilot/test/push.test.mjs pilot/test/events.test.mjs`. Expect all PASS.
- [ ] **Step 5:** Commit `feat(events): record student events from teacher actions`.

### Task 2: Student devices, preferences and pushes

**Files:** Modify `pilot/db/schema.sql`, `pilot/events.mjs`, `pilot/compat.mjs`. Test: `pilot/test/student-push.test.mjs`.

```sql
CREATE TABLE IF NOT EXISTS student_devices (
  token TEXT PRIMARY KEY,
  student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  prefs TEXT NOT NULL DEFAULT '{}',  -- {"on":true,"approved":true,...} missing kind = on
  at TEXT NOT NULL
);
```

**Interfaces:**
- Action `registerStudentDevice {token, className, studentNo, prefs}`: upsert. Re-registering a token moves it to the new student. Same length guard as `DEVICE_TOKEN_MAX`.
- Action `unregisterStudentDevice {token}`.
- `recordEvents` returns the inserted rows. Then `pushEvents(db, push, rows)` sends to matching devices whose `prefs.on !== false && prefs[kind] !== false`.
  - Class-wide rows go to all devices of students in that grade.
  - FCM `data` payload: `{ kind, link, eventId }`; notification title and body = event title and detail.
  - Invalid tokens are deleted.
  - Runs via `push.waitUntil` like `notifySubmission`.
- Not in `TEACHER_ACTIONS` (no password; students have none).
- **Repeat-safe photo sends:** `submitTaskPhoto` with a `rid` replays the first result from `request_log` instead of saving the photos again. Without a `rid` it behaves exactly as today (the website sends none). No password is required. Test: `submitTaskPhoto twice with same rid stores one set`.

- [ ] **Step 1:** Write tests with a fake `push` ({ send(tokens,msg) records calls, returns {invalid:[]}}). Cases:
  - **push respects prefs**: device with `{"xp":false}` gets no XP push but does get an approval push.
  - `{"on":false}` gets nothing.
  - Announcement to grade 11 reaches only G11 devices.
  - An invalid token is removed.
  - No push object → actions still succeed.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run all four backend test files plus this one. Expect PASS.
- [ ] **Step 5:** Commit `feat(push): student device registration and event pushes`.

### Task 3: Migration and deploy (owner approval)

**Files:** Create `migrate/add-student-events.mjs` (pattern: `migrate/add-native-tables.mjs`, safe to run repeatedly).

- [ ] **Step 1:** Run it against the lab DB (`engquest-lab`). Check with `node migrate/read-prod.mjs`-style reads that both tables exist there.
- [ ] **Step 2:** Ask the owner: "yes, deploy". Then run the migration on prod, then `npx --yes wrangler deploy` from the lab folder.
- [ ] **Step 3:** Smoke test on prod, read-only plus one harmless check: `getMyEvents` for a real student returns `{success:true}`. After the owner next gives XP in class, confirm a row appears via `node migrate/read-prod.mjs "SELECT kind,title,created_at FROM student_events ORDER BY id DESC LIMIT 5"`.
- [ ] **Step 4:** Commit `chore(migrate): student events tables`. Push the lab repo only on the owner's "push it".

---

## Part B: App (`native-student/` in the website repo)

Every task: run `pwsh -File native-student/build.ps1 -Tests <TestClasses>` (in the Terminal panel for long runs). Expect `BUILD SUCCESSFUL`. Then commit only `native-student/` files.

### Task 4: Project scaffold, theme, signing

**Files:** Create:
- `native-student/` with `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/` (wrapper and `libs.versions.toml` copied from `native-teacher`), `gradlew(.bat)`, `build.ps1` (outRoot `D:\android-dev\build\native-student`), `README.md`, `.gitignore` (ignore `app/google-services.json`, `build/`).
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (INTERNET, CAMERA, POST_NOTIFICATIONS, REQUEST_INSTALL_PACKAGES, FileProvider).
- `app/src/main/java/com/engquest/app/{MainActivity.kt, StudentApplication.kt, AppGraph.kt}`.
- `ui/theme/Theme.kt`, `res/font/nunito_*.ttf` (OFL), launcher icon (the v1.8 blue graduation cap from `D:\android-dev\EngQuestApp`).

**Interfaces:**
- Produces: `EqTheme(grade: Grade, content)`, with `Grade { G10, G11 }` and `EqColors(headerStart, headerMid, headerEnd, accent, accentSoft, act1, act2, bg)`.
- Produces: `AppGraph` (manual DI like the teacher's), holding `api`, `db`, `profileStore`, `repo`, `outbox`.
- Firebase plugin applied only if `app/google-services.json` exists (the teacher's build has the same guard; copy it), so builds work before Task 15.

- [ ] **Step 1:** Test `ThemeTest`: G10 `headerStart == Color(0xFF1D4ED8)`, G11 `headerStart == Color(0xFF0F766E)`, `act1 == Color(0xFFF59E0B)`.
- [ ] **Step 2:** Run, FAIL.
- [ ] **Step 3:** Scaffold and theme. `MainActivity` shows `EqTheme { Text("EngQuest") }` for now.
- [ ] **Step 4:** `build.ps1 -Release -Tests ThemeTest` PASS. Then `apksigner verify --print-certs` on the output: the SHA-256 must equal v1.8's (`D:\android-dev\EngQuestApp\app\build\outputs\apk\release\app-release.apk`).
- [ ] **Step 5:** Commit `feat(student-app): scaffold native student app 2.0`.

### Task 5: Domain models and parsing

**Files:** Create `domain/Level.kt`, `domain/Models.kt`, `domain/Parse.kt`. Test: `LevelTest`, `ParseTest` (with JSON fixtures in `app/src/test/resources/`, captured from the lab Worker with `curl` and names replaced).

**Interfaces (produces):**
- `data class LevelInfo(val level: Int, val into: Int, val needForNext: Int)`, `fun levelFromXp(xp: Int): LevelInfo`, `fun levelTitle(level: Int): String`.
- `data class Profile(val grade: Grade, val classId: String, val studentNo: Int, val name: String)`.
- `data class Classmate(val no: Int, val name: String)`, `data class Activeness(val no: Int, val total: Int, val counts: Map<String, Int>)`, `data class ChapterScore(...)`, `data class FinalScores(...)`, `data class MySubmission(val id: Long, val task: String, val status: String, val date: String, val reviewedAt: String)`, `data class Material(val chapter: String, val title: String, val link: String, val type: String)`, `data class VocabWord(...)`, `data class Strike(val reason: String, val at: String)`, `data class AttendanceDay(val date: String, val status: String)`, `data class Event(val id: Long, val kind: String, val title: String, val detail: String, val link: String, val at: String)`.
  - Fields mirror each action's reply. Read the matching website page for meanings: `scores.html`, `vocabulary.html`, `attendance.html`, `strikes.html`, `tasks.html`, `dashboard.html`.
- One `parseX(json: JsonObject)` per action in `Parse.kt`.

- [ ] **Step 1:** Tests:
  - `levelFromXp` matches the server cases (0→1, 10→2, 25→3, 1240 → the same as the JS function's result; compute it with node in the test-writing step and paste the value).
  - `levelTitle(1)=="Beginner"`, `levelTitle(9)=="Legend"`.
  - Each fixture parses without throwing, with spot values.
- [ ] **Step 2:** Run, FAIL. **Step 3:** Implement. **Step 4:** Run, PASS. **Step 5:** Commit.

### Task 6: Data layer (API, cache, profile, roster)

**Files:** Create:
- `data/Api.kt`: copy from teacher, drop the password handling.
- `data/Db.kt`: Room tables `cache(key TEXT PK, json TEXT, at LONG)` and `outbox` (Task 10 adds the DAO).
- `data/ProfileStore.kt`: SharedPreferences `eq_student`.
- `data/Repo.kt`.

Test: `RepoTest` (MockWebServer), `RosterTest`.

**Interfaces (produces):**
- `class Repo { suspend fun <T> cached(action: String, params: Map<String,String>, parse: (JsonObject)->T): Fresh<T> }`, with `data class Fresh<T>(val value: T, val fromCache: Boolean, val savedAt: Long)`.
  - Order: network first, fall back to cache on `ApiError.Offline`, else throw. Screens show "Offline · last updated <time>" when `fromCache`.
  - Typed wrappers: `classmates(classId)`, `activeness(classId)`, `chapterScores(...)`, `finalScores(...)`, `mySubmissions(profile)`, `materials(grade)`, `unitNames(grade)`, `taskStatus(...)`, `strikes(profile)`, `classData(classId)`, `vocabulary(profile)`, `announcement()`, `myEvents(profile, beforeId)`.
- `ProfileStore.get(): Profile?`, `save(Profile)`, `clear()`.
- `suspend fun checkRoster(p: Profile, roster: List<Classmate>): RosterResult`, with `sealed RosterResult { Same; Moved(newNo: Int); Gone }`. Matching: same rule as `shared/js/student.js` `checkRoster` (read it, port it exactly, including name normalising).

- [ ] **Step 1:** Tests:
  - `RosterTest`: same number and name → Same; name now at another number → Moved; name absent → Gone.
  - `RepoTest`: server down after one success → `fromCache=true` with the old value; no cache and offline → throws Offline.
- [ ] **Step 2–4:** FAIL, implement, PASS. **Step 5:** Commit.

### Task 7: Login (grade → class → name)

**Files:** Create `ui/login/LoginScreen.kt`, `LoginViewModel.kt`. Test: `LoginTest` (Compose test, fake repo).

- Classes: G10 `XE1–XE5`, G11 `XIF7–XIF9` (check the exact ids in `domain/Models.kt` of `native-teacher`).
- Names come from `getStudents`. A search box filters the list.
- Saves the profile, then opens the shell.
- If v1.8's SharedPreferences `engquest/grade` exists, preselect that grade.
- On each app start: `checkRoster`. Moved → save the new number silently. Gone → back to the name picker with the note "We couldn't find your name in <class> any more. Please pick it again."

- [ ] Steps: test (picking G11 shows only XIF classes; picking a name saves the Profile; Gone shows the note) → FAIL → implement → PASS → commit.

### Task 8: App shell, tabs, update banner

**Files:** Create `ui/App.kt` (NavHost), `ui/shell/BottomBar.kt`, `ui/shell/Header.kt`, `ui/shell/OfflineNote.kt`, `ui/UpdateBanner.kt` (port from teacher, reading `https://engquestviska.github.io/app-version.json`). Test: `ShellTest`.

**Interfaces (produces):**
- Routes: `home`, `scores`, `send`, `learn`, `more`, `bell`, `settings`, `levelxp`, `stats`, `taskstatus`, `attendance`, `strikes`, `students`, `materials`, `assignments`, `reading`, `vocab`.
- `QuestHeader(title: String, subtitle: String?, showBell: Boolean, unread: Boolean, onBell: () -> Unit)`.
- The **bottom bar** matches the mockup:
  - Five items. Send is a 44dp rounded-square (radius 14dp) gradient button raised 12dp, label "Send" in `#c2410c`.
  - The active tab uses the grade accent.
  - Android back: from a tab root goes to Home; from Home, a second press within 2 s exits.

- [ ] Steps: test (5 tabs present; tapping Send shows route `send`; back from Scores lands on Home) → FAIL → implement → PASS → commit.

### Task 9: Home

**Files:** `ui/home/HomeScreen.kt`, `HomeViewModel.kt`. Test: `HomeTest`.

Content, top to bottom:
- Greeting header ("Good morning/afternoon/evening 👋" by the WIB hour; name; class pill `"XE2 · No. 14"`; bell).
- Level card overlapping the header by 56dp: ring progress `into/needForNext` in act colour, level number, title, `"<total> XP · <need-into> XP to level <n+1>"`.
- Chips: Score (final score from `getFinalScores`; read `student-home.js` for which field), Tasks `"<done>/<total>"` (as `student-home.js`), Strikes count.
- "What's new": the latest 3 events from `myEvents`, plus a "See all" link to the bell.
- "Recent attendance": the last 3 meetings.
- Pull to refresh.

- [ ] Steps: test (fake data total 1240 → shows the level from `levelFromXp(1240)` and its title; 0 events → "Nothing new yet"; fromCache → offline note shown) → FAIL → implement → PASS → commit.

### Task 10: Send (photos, outbox, history)

**Files:**
- `ui/send/SendScreen.kt`, `SendViewModel.kt`.
- `data/Photos.kt`: compress 1000 px, q50 JPEG, base64.
- `data/Outbox.kt`: Room `outbox(id PK, rid TEXT, classId, studentNo, taskKey, photoPaths TEXT json, state TEXT, error TEXT, createdAt)`.
- `data/OutboxWorker.kt`: patterned on teacher's `Outbox.kt`/`OutboxWorker.kt`.

Test: `OutboxTest`, `SendTest`.

**Behaviour:**
- Pick the chapter and task with chips (chapters from `getUnitNames`; tasks per chapter as `submission.html`; done ones ticked from `getMySubmissions.statuses`).
- "Take photo" (camera intent, FileProvider) and "From gallery" (multi-select). Previews with remove (×) and add (+). Max 10.
- "Send to teacher" copies the compressed photos into app storage, inserts an outbox row with a new UUID `rid`, and enqueues a unique WorkManager job (network required, exponential backoff).
- The worker calls `submitTaskPhoto {className, studentNo, taskKey, images[], rid}` and deletes the files on success.
- History below: outbox rows first ("Waiting to send", or "Couldn't send: <msg> · Retry"), then `getMySubmissions` items with status chips (Waiting for teacher / Approved · score / Sent back · redo). Delete via `deleteMySubmission` only for `pending` items, with a confirm dialog.
- Relies on Task 2's repeat-safe `submitTaskPhoto`. Until that is deployed, a retry after a lost reply could store the photos twice; the server already replaces an earlier send for the same task key, so the visible result is the same.

- [ ] Steps: tests:
  - `OutboxTest`: an offline enqueue keeps the row; a worker run with MockWebServer success deletes it; a retry after a server timeout reuses the same `rid`.
  - `SendTest`: the 11th photo is refused with the note "Maximum 10 photos"; Send is disabled with 0 photos.

  Then FAIL → implement → PASS → commit.

### Task 11: Scores

**Files:** `ui/scores/ScoresScreen.kt`, `ScoresViewModel.kt`. Test: `ScoresTest`. Shows what `grade10/scores.html` shows (chapter scores and final/summative, honouring `getSummative`/reveal rules the page applies) in Quest cards. Port the page's display rules exactly; read the page first.

- [ ] Steps: test (hidden summative not shown when reveal off; chapter rows render) → FAIL → implement → PASS → commit.

### Task 12: Learn tab (Materials, Assignments, Reading, My Vocabulary)

**Files:** `ui/learn/LearnScreen.kt` (four big cards), `MaterialsScreen.kt`, `AssignmentsScreen.kt`, `ReadingScreen.kt`, `VocabScreen.kt` + view models. Test: `LearnTest`, `VocabTest`.

- Materials, Assignments and Reading filter `getMaterials` the way `lessons.html`, `assignments.html` and `session.html` do (read their filters). Tapping an item opens the link with `ACTION_VIEW` (Drive and PDF links open outside the app).
- Vocabulary mirrors `vocabulary.html`:
  - Add a word plus meaning (`checkVocabulary`/`checkMeanings` then `addVocabulary`), remove, set type.
  - The weekly cap of 30 with a countdown to the reset. Port the page's reset rule exactly.

- [ ] Steps: tests (`VocabTest`: at 30 words the add button is disabled and the countdown text shows; Materials filter matches the page's for a fixture) → FAIL → implement → PASS → commit.

### Task 13: More tab and its pages

**Files:** `ui/more/MoreScreen.kt` (grouped list per mockup: My progress / My class / App), and `LevelXpScreen.kt`, `StatsScreen.kt`, `TaskStatusScreen.kt`, `AttendanceScreen.kt`, `StrikesScreen.kt`, `StudentsScreen.kt` with view models. Test: `MoreTest`.

Each page shows what its website page shows (`activeness.html`, `dashboard.html`, `tasks.html`, `attendance.html`, `strikes.html`, `students.html`). Read each page and port its rules. Log out clears the profile and unregisters the device token (Task 15), then shows Login.

- [ ] Steps: test (More lists all 8 entries; Log out returns to Login and clears ProfileStore) → FAIL → implement → PASS → commit.

### Task 14: Bell

**Files:** `ui/bell/BellScreen.kt`, `BellViewModel.kt`, `data/BellStore.kt` (last-seen event id in prefs). Test: `BellTest`.

- List newest first, with an icon and colour per kind (approved ✅ green, sent_back ↩ amber, xp ⭐ amber, level 🏆 indigo, attendance 📅 blue, strike ⚠️ red, material 📘 blue, announcement 📣 violet). Load more with `beforeId` paging.
- Unread = `id > lastSeenId`. Opening the bell sets `lastSeenId` to the newest id. The header dot shows when the newest id is greater than `lastSeenId`.
- Tapping an item navigates to its `link` route.

- [ ] Steps: test (unread dot on, then off after opening; tapping an `xp` event opens `levelxp`) → FAIL → implement → PASS → commit.

### Task 15: Notifications and Settings

**Files:** `push/StudentMessagingService.kt`, `push/Notify.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/NotifyAskScreen.kt`, `data/NotifyPrefs.kt`. Test: `SettingsTest`, `NotifyTest`.

**Firebase setup (needs the owner's Google account):** add an Android app `com.engquest.app` to project `engquest-15b64`, following `native-teacher/FIREBASE-SETUP.md`. Download `google-services.json` into `native-student/app/` (git-ignored). Write `native-student/FIREBASE-SETUP.md` with the same steps for the student app.

**Behaviour:**
- After the first login, show once: "Want a buzz when your task is approved or you get XP?" with "Yes, turn on" and "Not now".
  - Yes requests `POST_NOTIFICATIONS` on Android 13+. If granted, register the token with `registerStudentDevice`.
  - Not now: nothing is registered. Settings can turn it on later.
- Settings: a master switch plus 8 switches (labels: Task approved, Task sent back, XP from teacher, New level, Attendance, Strikes, New materials, Announcements). Each change calls `registerStudentDevice` with the new prefs.
  - If Android permission is denied, the master switch shows "Turn on in phone settings" with a button to the app's notification settings.
- The service's `onNewToken` re-registers. `onMessageReceived` shows a notification on channel "EngQuest updates"; tapping it opens `MainActivity` with extra `link` → navigate.

- [ ] Steps: tests:
  - `SettingsTest`: turning off "XP from teacher" sends prefs `{"on":true,"xp":false,...}`.
  - `NotifyTest`: a message with link `strikes` builds an intent whose extra is `strikes`.

  Then FAIL → implement → PASS → commit.

### Task 16: Polish, upgrade test, release (owner approval)

- [ ] **Step 1:** Walk every screen on the emulator or a phone in both grades (XE2 blue, XIF8 teal), at normal and largest font size. Fix clipping. Compare Home, Send and More to the mockups.
- [ ] **Step 2:** Upgrade test: install v1.8 (`Desktop\EngQuest.apk`), log in, then install the 2.0 release APK over it. The app opens to Login with the grade preselected; nothing crashes.
- [ ] **Step 3:** End to end with prod (after Task 3):
  - On the teacher app, approve a test student's task. The student phone gets the bell item and the notification within a minute.
  - Turn XP off in Settings, give XP: the bell shows it, with no buzz.
- [ ] **Step 4:** Release:
  - Copy the release APK over repo-root `EngQuest-app-7fq93kx2.apk`.
  - Bump `app-version.json` to `versionCode 10`, `versionName "2.0"`, notes "The new EngQuest app: rebuilt from scratch, with a bell for news and optional notifications.", and a new `history[]` entry.
  - Run `node scripts/check-site.mjs`, then commit.
- [ ] **Step 5:** Ask the owner "push it". Push via the terminal tool. Verify the live `app-version.json` with a GET.
- [ ] **Step 6:** Update memory `engquest-android-apk.md` (student app is now native 2.0, project path, build steps).
