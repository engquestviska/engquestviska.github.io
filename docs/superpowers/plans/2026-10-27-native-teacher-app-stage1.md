# Native Teacher App — Stage 1 + Server Changes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A real Kotlin/Compose Android app (`com.engquest.teacher`, v3.0) with native Attendance, Submissions, Give XP and Scores screens that work offline and get submission notifications, plus the Worker changes they need.

**Architecture:** The app calls the existing Worker bridge `/exec?action=…` (plain JSON, no `callback`). Every read is cached in Room and shown first; every write goes into a Room outbox sent in order by WorkManager, each with a request id (`rid`) the Worker uses to ignore repeats. Pure rules (chapter mapping, score checks, final score, attendance) live in one Kotlin file ported from `teacher-app/js/core.js` and are JVM-tested; screens are Compose, tested with Robolectric.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Material 3), Room, WorkManager, OkHttp, kotlinx.serialization, Firebase Cloud Messaging; Worker: Node ESM on Cloudflare, tests with `node --test` + `node:sqlite`.

**Spec:** `docs/superpowers/specs/2026-10-27-native-teacher-app-design.md`. Reference implementation for every behaviour: the pilot `teacher-app/js/` (core.js, screens/*.js).

## Global Constraints

- applicationId `com.engquest.teacher`; versionCode 3, versionName "3.0"; label "EngQuest Teacher"; same keystore `D:\android-dev\keystore\engquest.jks` (alias `engquest`).
- Signing passwords are read from `D:\android-dev\keystore\keystore.properties` (not in git). Never commit a password or keystore.
- Toolchain on D: only: JDK `D:\android-dev\jdk`, SDK `D:\android-dev\android-sdk` (android-34, build-tools 34.0.0), Gradle `D:\android-dev\gradle\gradle-8.7`, `GRADLE_USER_HOME=D:\android-dev\.gradle`. Build output goes to `D:\android-dev\build\native-teacher` (owner keeps C: free).
- AGP 8.5.2, compileSdk/targetSdk 34, minSdk 24, Java 17. Pinned libs (bump only with a ledgered ruling if compileSdk 34 refuses): Compose BOM 2024.09.03, activity-compose 1.9.3, navigation-compose 2.8.3, lifecycle 2.8.7, Room 2.6.1 + KSP 2.0.21-1.0.27, work-runtime-ktx 2.9.1, okhttp/mockwebserver 4.12.0, kotlinx-serialization-json 1.7.3, security-crypto 1.1.0-alpha06, Firebase BOM 33.5.1 + google-services plugin 4.4.2, junit 4.13.2, robolectric 4.13, kotlinx-coroutines-test 1.8.1.
- Source lives in this repo at `native-teacher/` (backed up by git). Worker code lives in `C:\Users\acer\Documents\Engquest-turso-lab` (repo `engquest-backend`).
- API base: `https://engquest-api.engquestviska.workers.dev/exec`. Reads = GET with query params; writes = POST JSON body. Every teacher write carries `username`, `password`, `rid`.
- Classes (fixed): XE1–XE5 (grade 10), XIF7–XIF9 (grade 11), labels "X E-1"… "XI F-9" as in `shared/js/classes-20260617.js`.
- UI chapter n = backend chapter n+3 ('4','5','6'); only C1–C3 / T1–T3 / C1–3 F/S get a score box; backend column `ASAT` is shown as "ASAS"; Final shows `calc['Final Score']`; 75 or more = Safe.
- XP weights: Answering 5, Presenting 20, Helping Hand 20, Doing Task On Time 20, Quiz 10, Vocabulary Box 2; backend keys exactly as pilot `REASONS`.
- Look: brand `#4338CA`, rounded cards, bottom tab bar Attendance · Submissions · XP · Scores (Home arrives in stage 2). No WebView anywhere.
- Production stops (ask the owner, never do alone): Worker deploy, prod DB migration, `git push` of either repo, Firebase sign-in.
- Plain-language copy everywhere (owner and app user are the same non-technical teacher).

## Review Focus

1. Airplane mode during a run of XP taps, then reconnect → every tap reaches the server exactly once, in order (Task 2 + Task 7 tests).
2. Password changed on the website while the outbox holds writes → nothing dropped; the app asks to log in again and sends after (Task 7, Task 8 tests).
3. App swiped away / phone restarted with unsent writes → they still send (WorkManager persisted work; Task 7 test).
4. A crafted or huge submission photo (bad mime, 12 MP image) → no crash, shown downsampled (Task 12 test).
5. Switching class while writes are queued → each write goes to the class it was made in (Task 7 test).

---

## Part A — Worker (in `C:\Users\acer\Documents\Engquest-turso-lab`)

### Task 1: Test harness + `getAllStrikes`

**Files:**
- Create: `pilot/test/sqlite-db.mjs`, `pilot/test/compat.test.mjs`
- Modify: `pilot/compat.mjs` (new action near `getStudentStrikes`, ~line 304)

**Interfaces:**
- Produces: `makeTestDb(): {query(sql,args)→rows[], run(stmts)→rows[][], close()}` — in-memory `node:sqlite` loaded with `pilot/db/schema.sql`, same call shapes as `turso-core.mjs`; `seedTeacher(db, user='teacher', pass='pw')` writes `teacher_user`/`pw_salt`/`pw_hash` via `auth.mjs`; `seedStudents(db, 'XE1', n)`.
- Produces: action `getAllStrikes {className}` → `{students:[{no,name,nickname,total,reason,date,strikes:[{id,reason,date}]}]}` — every student of the class sorted by `no`; `reason`/`date` = latest strike's (empty when none); `strikes` newest first. Shape matches what `teacher/strikes.html` reads (`student.total/reason/date/name/nickname/no`).

- [ ] **Step 1: Write failing tests** `getAllStrikes lists every student with counts and latest reason`: seed XE1 with 3 students, two strikes for no 2 (`'Late to class'` at 2026-10-20, `'Cheating'` at 2026-10-21); assert `students.length===3`, student 2 `total===2`, `reason==='Cheating'`, `date==='2026-10-21'`, `strikes[0].reason==='Cheating'`; student 1 `total===0`, `reason===''`.
- [ ] **Step 2:** Run `node --test "pilot/test/*.test.mjs"` → FAIL `Action not bridged yet: getAllStrikes`.
- [ ] **Step 3:** Implement the harness and the action (one query joining students ⟕ strikes).
- [ ] **Step 4:** Run tests → PASS.
- [ ] **Step 5:** Commit `feat(compat): getAllStrikes for the teacher strikes page`.

### Task 2: Repeat-safe writes (`rid`) + client date

**Files:** Modify `pilot/compat.mjs`, `pilot/db/schema.sql`; Test `pilot/test/compat.test.mjs`

**Interfaces:**
- Produces: table `request_log(rid TEXT PRIMARY KEY, action TEXT NOT NULL, result TEXT NOT NULL, at TEXT NOT NULL)`.
- Produces: for any `TEACHER_ACTIONS` call with a non-empty `rid` (after the auth check): if `rid` is logged, return the stored result without running the action; otherwise run it and log `JSON.stringify(result)` only when the result has no `error` and `success !== false`.
- Produces: optional `clientDate` (`/^\d{4}-\d{2}-\d{2}$/`, else ignored) used instead of `today` by `addStrike`, `reviewTaskSubmission`, `addMaterial`, `setAnnouncement`.

- [ ] **Step 1: Write failing tests:**
  - `incrementActiveness with the same rid twice counts once` → Answering counter goes 0→1, not 2; second reply equals the first.
  - `different rids both count` → 2.
  - `removeStrike with a repeated rid removes only one strike`.
  - `a failed write is not logged, so a retry with its rid runs` (wrong password first → Unauthorized; correct password, same rid → applied).
  - `addStrike uses clientDate when valid` (`'2026-10-20'` stored) and `falls back to today for junk` (`'yesterday'`).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement (wrap the dispatch in a helper; add the table to `schema.sql`).
- [ ] **Step 4:** Run → PASS. Also run `node --check worker/index.mjs`.
- [ ] **Step 5:** Commit `feat(compat): rid de-duplication and clientDate for offline writes`.

### Task 3: Device registration + submission push

**Files:**
- Create: `pilot/push.mjs`
- Modify: `pilot/compat.mjs` (`registerDevice`, `unregisterDevice`, `submitTaskPhoto`), `pilot/router.mjs` + `worker/index.mjs` (pass `push` alongside `storage`), `pilot/db/schema.sql`, `wrangler.toml` (comment documenting secret `FCM_SERVICE_ACCOUNT`)
- Test: `pilot/test/push.test.mjs`, `pilot/test/compat.test.mjs`

**Interfaces:**
- Produces: table `devices(token TEXT PRIMARY KEY, at TEXT NOT NULL)`; actions `registerDevice {token}` / `unregisterDevice {token}` — both teacher-gated (add to `TEACHER_ACTIONS`).
- Produces: `makePush({serviceAccountJson, fetch=globalThis.fetch, now=Date.now}) → {send(tokens: string[], msg: {title, body, data: Record<string,string>}) → Promise<{sent, invalid: string[]}>}` — FCM HTTP v1: signs an RS256 JWT with WebCrypto from the service account (`scope https://www.googleapis.com/auth/firebase.messaging`), exchanges it at `https://oauth2.googleapis.com/token`, caches the access token until 60 s before expiry, POSTs to `https://fcm.googleapis.com/v1/projects/{project_id}/messages:send` per token with `android.notification.tag = "sub-{className}-{studentNo}"` (later uploads by the same student replace the notification — this is the "one per burst" rule). Tokens answered 404/`UNREGISTERED` come back in `invalid`.
- `handleCompat(db, action, p, storage, push)`: after a successful `submitTaskPhoto`, if `push` exists, send to all `devices` with title `"{Student name} ({class})"`, body `"sent {task label}"` where the task label is `C1 Task 2` / `C2 Formative` / `C3 Summative` / raw key; data `{type:'submission', className, studentNo, taskKey}`; delete `invalid` tokens; push errors never fail the upload.
- Worker: `push = env.FCM_SERVICE_ACCOUNT ? makePush({serviceAccountJson: env.FCM_SERVICE_ACCOUNT}) : null`; send inside `ctx.waitUntil` when available.

- [ ] **Step 1: Write failing tests** (fake `fetch` recording calls, test RSA key generated with `crypto.subtle.generateKey`):
  - `push gets one access token and reuses it for two sends`.
  - `push reports UNREGISTERED tokens as invalid`.
  - `submitTaskPhoto notifies every registered device with student, class and task label` (body `'sent C1 Task 2'` for `taskKey 'C1T2'`).
  - `submitTaskPhoto still succeeds when push throws`.
  - `registerDevice needs the teacher login`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run all Worker tests → PASS.
- [ ] **Step 5:** Commit `feat: push notifications for new submissions`.

### Task 4: Migration script + deploy — STOP for owner

**Files:** Create `migrate/add-native-tables.mjs` (idempotent `CREATE TABLE IF NOT EXISTS` for `request_log`, `devices`; prints table list after).

- [ ] **Step 1:** Run it against the lab DB (`.env` points at `engquest-lab`) → prints both tables; run again → no error.
- [ ] **Step 2:** Commit `chore: migration for native app tables`.
- [ ] **Step 3: STOP.** Tell the owner in plain words what the update does (Strikes page fixed, no double taps, notifications ready) and ask for the go-ahead. On yes: run the migration against prod (same script with prod credentials from `migrate/read-prod.mjs`'s source), `npx wrangler deploy`, then check live: `GET /exec?action=getAllStrikes&className=XE1` returns `students`, and `teacher/strikes.html` loads strikes. Pushing `engquest-backend` is the owner's call.
- `FCM_SERVICE_ACCOUNT` is set later in Task 13.

## Part B — Android app (in this repo, `native-teacher/`)

### Task 5: Project skeleton + core rules

**Files:**
- Create: `native-teacher/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/engquest/teacher/MainActivity.kt`, `ui/theme/Theme.kt`, `domain/Core.kt`, `native-teacher/build.ps1`, `native-teacher/README.md`, `native-teacher/.gitignore` (`build/`, `.gradle/`, `local.properties`, `app/google-services.json`)
- Test: `app/src/test/java/com/engquest/teacher/domain/CoreTest.kt`
- Modify: root `.gitignore` if needed; `scripts/check-site.mjs` only if it flags `native-teacher/` (ledger a ruling).

**Interfaces — `domain/Core.kt` (`object Core`), ported 1:1 from `teacher-app/js/core.js`:**
- `val STATUSES = listOf("Present","Absent","Sick","Permit")`
- `data class ChapterCol(val chapter: String, val column: String)`; `fun taskToChapterCol(code: String?): ChapterCol?`
- `data class ScoreCheck(val ok: Boolean, val value: String, val error: String? = null)`; `fun validateScore(v: String?): ScoreCheck`; `fun isLowScore(v: String?): Boolean`
- `data class FinalCalc(val average: Double?, val final: Int?, val indicator: String)`; `fun finalScore(calc: Map<String, String?>, exam: String?): FinalCalc`
- `fun colLabel(col: String): String`; `fun initials(name: String?): String`; `fun firstName(name: String?): String`
- `fun safeMime(mime: String?): String` (whitelist `image/(png|jpeg|jpg|webp|gif|heic|heif)`, else `image/jpeg`)
- `fun taskLabel(code: String): String` (`C1T2`→`C1 Task 2`, `C2F`→`C2 Formative`, `C3S`→`C3 Summative`, else code)
- `fun attendanceInitial(students: List<Student>, meetings: List<Meeting>, date: String): Pair<Meeting?, Map<String,String>>`, `attendanceCounts`, `attendanceDirty`, `groupSubmissions(rows, className, filterAll)` — same rules as core.js; `data class Student(no, name, nickname, attendance: Map<String,String>)`, `Meeting(number: Int, date: String)`, `SubmissionRow(subId, className, studentNo, studentName, task, timestamp, status)` in `domain/Models.kt`.
- `object Classes { val ALL: List<ClassInfo> }`, `data class ClassInfo(id, label, grade)` with the 8 classes.
- `build.ps1 [-Release]` sets the env vars from Global Constraints and runs `gradle.bat -p native-teacher testDebugUnitTest` then `assembleDebug`/`assembleRelease`.

- [ ] **Step 1: Write failing tests** in `CoreTest.kt` — port every case in `teacher-app/tests/core.test.mjs` (same inputs, same expected values), plus `taskLabel` (`C1T2`, `C2F`, `C3S`, `ZZ9`) and `Classes.ALL.size == 8`.
- [ ] **Step 2:** Run `pwsh native-teacher/build.ps1` → FAIL (unresolved `Core`).
- [ ] **Step 3:** Implement the Gradle setup (build dir redirected to `D:\android-dev\build\native-teacher`, signing from `keystore.properties`, Robolectric `isIncludeAndroidResources = true`), theme (`#4338CA`, rounded 16 dp cards), an empty `MainActivity`, and `Core.kt`. Create `D:\android-dev\keystore\keystore.properties` with `storePassword`, `keyPassword`, `keyAlias` from the APK memory note.
- [ ] **Step 4:** Run → tests PASS, debug APK built.
- [ ] **Step 5:** Commit `feat(native-teacher): project skeleton and core rules`.

### Task 6: API client, session and cache

**Files:** Create `data/Api.kt`, `data/Session.kt`, `data/Db.kt` (Room: `CacheEntry`, `OutboxItem`, DAOs), `data/Repo.kt`; Tests `data/ApiTest.kt` (MockWebServer), `data/SessionTest.kt`, `data/RepoTest.kt` (Robolectric, in-memory Room)

**Interfaces:**
- `class Api(baseUrl: String, client: OkHttpClient)`: `suspend fun read(action: String, params: Map<String,String>): JsonObject` (GET); `suspend fun write(action: String, params: Map<String,String>): JsonObject` (POST JSON). Throws `ApiError.Offline` (IO failure / timeout 20 s), `ApiError.Unauthorized` (`error == "Unauthorized"` or `ok/ success == false` with that error), `ApiError.Server(message)` (other `error` or `success:false`).
- `class Session(prefs: SharedPreferences, api: Api)`: `val user: StateFlow<Creds?>`; `suspend fun login(u, p): Boolean` (via `checkLogin`, `ok`); `fun logout()`; `fun creds(): Creds?`; `fun markExpired()` (clears password, keeps username, emits `expired = true`). Prefs = `EncryptedSharedPreferences` in the app, plain prefs in tests.
- `class Repo(api, session, db)`: `fun <T> cached(key: String, fetch: suspend () -> JsonObject): Flow<Cached>` where `data class Cached(val json: JsonObject?, val savedAt: Long?, val refreshing: Boolean, val error: String?)` — emits the cached entry first, then the fresh one (saved to Room). **Rule:** if the outbox holds unsent items for the same `className`, the fresh response is not emitted or saved; a re-read happens when the outbox drains (`outboxDrained: SharedFlow<String /*className*/>`). Reads needing login (`getTaskSubmissions`, `getSubmissionPhoto`) get `username`/`password` added.
- Cache keys: `"{action}|{className}|{chapter?}"`.

- [ ] **Step 1: Write failing tests:**
  - `read sends GET without callback and parses JSON`; `write posts JSON with all params`.
  - `Unauthorized reply throws ApiError.Unauthorized`; `connection refused throws Offline`.
  - `login stores creds only when checkLogin ok`; `markExpired keeps username, drops password`.
  - `cached emits stored value before network value`; `cached does not overwrite while the class has unsent writes`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(native-teacher): api, session, cache`.

### Task 7: Outbox

**Files:** Create `data/Outbox.kt`, `data/OutboxWorker.kt`; Test `data/OutboxTest.kt` (Robolectric + `work-testing` `TestListenableWorkerBuilder`, MockWebServer)

**Interfaces:**
- `class Outbox(db, session, api, work: WorkManager)`: `suspend fun enqueue(action: String, className: String, params: Map<String,String>): Long` — stores `OutboxItem(id, rid = UUID, action, className, paramsJson, createdAt, state = PENDING, error = null)`, adds `clientDate` (phone's local `yyyy-MM-dd`) for `addStrike/reviewTaskSubmission/addMaterial/setAnnouncement`, then schedules unique work `"outbox"` (`ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`).
- `val pending: Flow<Int>`; `val failed: Flow<List<OutboxItem>>`; `fun retry(id)`, `fun discard(id)`; `suspend fun cancelIfPending(id): Boolean` — removes the item only if it is not being sent right now (worker marks the item `SENDING` before the request); returns false otherwise (used by XP Undo).
- `OutboxWorker.doWork()`: oldest first, one at a time, sends with current creds + the item's stored `rid`. Offline → `Result.retry()` (item stays, order kept). Unauthorized → `session.markExpired()`, stop, items kept (`Result.success()`, re-scheduled on next login). Server error → item `FAILED` with message, continue with the next. Success → delete item; when no pending items remain for a class emit `Repo.outboxDrained(className)`.
- Credentials are read at send time, never stored in the item.

- [ ] **Step 1: Write failing tests:**
  - `items are sent oldest first, each with its own rid`.
  - `offline keeps every item and asks to retry` (server shut down).
  - `a resend after a lost reply uses the same rid` (first response dropped via MockWebServer `SocketPolicy.NO_RESPONSE`).
  - `unauthorized keeps items and marks the session expired`.
  - `a server error fails only that item`.
  - `items keep the class they were made for` (enqueue XE1 then XE2 → requests carry XE1 then XE2).
  - `pending items survive a new Outbox instance on the same database` (restart).
  - `cancelIfPending refuses an item that is being sent`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(native-teacher): offline outbox`.

### Task 8: App shell — login, class picker, tabs, status

**Files:** Create `ui/App.kt`, `ui/LoginScreen.kt`, `ui/ClassPicker.kt`, `ui/StatusBar.kt` (pending/failed chip + sheet), `AppGraph.kt` (manual DI: one `Api`, `Session`, `Repo`, `Outbox`); Modify `MainActivity.kt`; Test `ui/AppShellTest.kt` (Robolectric Compose)

**Interfaces:**
- `@Composable fun TeacherApp(graph: AppGraph)`: not logged in → `LoginScreen`; else scaffold with top bar (screen title, class chip opening `ClassPicker` bottom sheet, ⋮ menu: Log out) and bottom bar Attendance · Submissions · XP · Scores. Selected class + tab saved in prefs (`class`, `tab`). Session expired → login screen with username filled and the line "Please log in again — your unsent changes are kept."
- `StatusBar`: shows "N changes waiting to send" when `pending > 0` (with "offline" wording when no network) and "N not saved — tap to see" when failures exist; the sheet lists failed items in plain words with Retry / Discard.
- Log out: confirm dialog; if `pending > 0` the dialog warns they will be lost; calls `unregisterDevice` (best effort), clears session.
- Screen routes are placeholders here; Tasks 9–12 fill them.

- [ ] **Step 1: Write failing tests:** `shows login when signed out`; `wrong password shows a message`; `after login the Attendance tab is shown`; `class picker lists 8 classes and remembers the choice`; `expired session keeps the username and says changes are kept`; `pending count appears`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat(native-teacher): login, class picker, tabs`.

### Task 9: Attendance

**Files:** Create `ui/attendance/AttendanceScreen.kt`, `AttendanceViewModel.kt`; Test `ui/attendance/AttendanceTest.kt`

Behaviour = pilot `teacher-app/js/screens/attendance.js`: date (default today, picker), rows with P/A/S/I buttons, summary chips, "Save" bar when dirty, ⋯ menu "Mark everyone Present" and "Remove this meeting" (confirm), leaving with unsaved marks asks first. Save → `Outbox.enqueue("saveAttendance", cls, {className, date, records: JSON of Core.attendanceRecords})`; remove → `removeAttendanceMeeting {className, date}`. The cached `getClassData` entry is updated locally on save so reopening shows the saved marks offline.

- [ ] **Step 1: Write failing tests:** `defaults everyone to Present for a new date`; `loads stored marks for an existing meeting`; `save enqueues saveAttendance with all records`; `saved marks show again offline`; `remove asks first then enqueues`; `mark everyone present`.
- [ ] **Step 2–4:** FAIL → implement → PASS. **Step 5:** Commit `feat(native-teacher): attendance`.

### Task 10: Give XP

**Files:** Create `ui/xp/XpScreen.kt`, `XpViewModel.kt`; Test `ui/xp/XpTest.kt`

Behaviour = pilot `xp.js`: Give/Take back toggle (resets to Give on each visit), reason chips (remembered), student tile grid (number, initials avatar, first name, total XP), tap → +/− pop, snackbar with Undo. Take back blocked at 0 for that reason ("X has no Answering XP to take back"). Each tap → `Outbox.enqueue("incrementActiveness", cls, {className, studentNo, column: reason.key, delta: "1"|"-1"})`. Undo: `Outbox.cancelIfPending(id)`; if that returns false, enqueue the opposite delta. Totals update instantly from local state.

- [ ] **Step 1: Write failing tests:** `tap gives XP and updates the total by the reason's points`; `take back lowers it`; `take back at zero is refused with the message`; `undo of a pending tap removes it from the outbox`; `undo of a sent tap enqueues the opposite`; `mode resets to Give when reopened`.
- [ ] **Step 2–4.** **Step 5:** Commit `feat(native-teacher): give XP`.

### Task 11: Scores

**Files:** Create `ui/scores/ScoresScreen.kt`, `ScoresViewModel.kt`; Test `ui/scores/ScoresTest.kt`

Behaviour = pilot `scores.js`: chips Ch 1/Ch 2/Ch 3/Final (backend '4','5','6', final), column segment (labels via `Core.colLabel`: Task 1/2/3, Form., Summ.; ASAS), list of rows with a number field, IME "Next" moves down, value checked with `Core.validateScore` (red + message when bad), low (<75) shown amber. Each changed value → `saveChapterScore {className, chapter, studentNo, column, value}` or `saveFinalScore {className, studentNo, column, value}`. Right column: "avg" (chapters: plain average of entered inputs, 1 decimal) or "final" (`calc['Final Score']`, recomputed with `Core.finalScore` after an edit). Chapter/column remembered.

- [ ] **Step 1: Write failing tests:** `Final tab labels the exam column ASAS`; `Final tab shows the final score, not Chapter 1`; `typing 120 shows the range message and enqueues nothing`; `a valid score enqueues one save with the backend chapter id`; `editing the exam recomputes the final score`; `next moves focus to the next student`.
- [ ] **Step 2–4.** **Step 5:** Commit `feat(native-teacher): scores`.

### Task 12: Submissions

**Files:** Create `ui/submissions/SubmissionsScreen.kt`, `SubmissionsViewModel.kt`, `ui/submissions/PhotoViewer.kt`, `data/Photos.kt`; Test `ui/submissions/SubmissionsTest.kt`, `data/PhotosTest.kt`

Behaviour = pilot `submissions.js`: Waiting/All toggle, queue strip of students, one student at a time, swipeable photos with full-screen pinch-zoom viewer, per-photo score box only when `Core.taskToChapterCol` maps it, Approve and Send back (confirm). Approve enqueues, in order: `saveChapterScore` (if a score was typed) → `reviewTaskSubmission {subId, decision:'approved'}` → `saveTaskStatus`. Send back → `reviewTaskSubmission {decision:'rejected'}`. Tab badge = waiting count.
- `Photos.load(fileId): ImageBitmap?` — `getSubmissionPhoto` (login), base64 decoded off the main thread with `BitmapFactory` `inSampleSize` so the long side ≤ 2048 px; undecodable → null (shows "Photo can't be shown"); cached on disk (`cacheDir/photos/{fileId}`), so photos seen once open offline.
- Deep link: `MainActivity` handles intent extras `className`, `studentNo` → Submissions tab, that class, that student.

- [ ] **Step 1: Write failing tests:** `approve with a score enqueues score, review, status in that order`; `no score box for a task code it can't map`; `send back asks first`; `bad mime still shows the photo`; `garbage base64 shows the can't-be-shown message`; `a 4000×3000 photo is decoded at most 2048 px wide`; `deep link opens that student`.
- [ ] **Step 2–4.** **Step 5:** Commit `feat(native-teacher): submissions`.

### Task 13: Notifications — STOP for owner's Firebase sign-in

**Files:** Create `push/EqMessagingService.kt`, `native-teacher/FIREBASE-SETUP.md`; Modify `app/build.gradle.kts` (google-services plugin, Firebase BOM, messaging), manifest (service, `POST_NOTIFICATIONS`), `ui/App.kt` (ask permission once after login with "Get a notification when a student sends a submission?"); Test `push/PushTest.kt`

**Interfaces:**
- `EqMessagingService.onNewToken(t)` and first login → `Outbox.enqueue("registerDevice", "", {token})`.
- `onMessageReceived` with `data.type == "submission"` (app in foreground) → show the same notification locally, tag `sub-{className}-{studentNo}`; tap → `MainActivity` deep link (Task 12).
- Notification channel "Submissions".

- [ ] **Step 1:** Write `FIREBASE-SETUP.md`: click-by-click (create project "EngQuest", add Android app `com.engquest.teacher`, download `google-services.json` into `native-teacher/app/`, Project settings → Service accounts → Generate new private key, then `npx wrangler secret put FCM_SERVICE_ACCOUNT` and paste the file's contents). **STOP:** owner does these steps; continue when `google-services.json` exists.
- [ ] **Step 2: Write failing tests:** `a submission message builds a notification with the student and task and the deep-link extras`; `new token enqueues registerDevice`.
- [ ] **Step 3–4:** implement → PASS.
- [ ] **Step 5:** Commit `feat(native-teacher): submission notifications`. Then (owner OK) live check: owner's phone gets a notification within a minute of a test upload.

### Task 14: Update check + release

**Files:** Create `ui/UpdateBanner.kt`, `teacher-app-version.json` (repo root: `{versionCode, versionName, apk, notes}`); Modify `ui/App.kt`; Test `ui/UpdateTest.kt`

**Interfaces:**
- On start, fetch `https://engquestviska.github.io/teacher-app-version.json`; if `versionCode > BuildConfig.VERSION_CODE` show a banner "New version {name} — {notes}" with Update → DownloadManager to `getExternalFilesDir(null)/EngQuestTeacher-update.apk`, then the system installer via FileProvider (same approach as the student app, see APK memory note). Silent when offline.
- APK hosted at repo root as `EngQuestTeacher-k4m8q2zt.apk` (unguessable, not linked anywhere).

- [ ] **Step 1: Write failing tests:** `newer version shows the banner`; `same version shows nothing`; `offline shows nothing`.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** `pwsh native-teacher/build.ps1 -Release`; confirm APK signed with the same key (`apksigner verify --print-certs` matches the v2.0 APK's SHA-256); copy to Desktop `EngQuest-Teacher.apk` and repo root under the hosted name; write `teacher-app-version.json` (versionCode 3, "3.0"); run `node scripts/check-site.mjs`.
- [ ] **Step 6:** Commit `release: native teacher app 3.0`. **STOP:** owner installs over v2.0 and pushes.

---

## Execution notes

- Part A (Tasks 1–4) and Part B up to Task 12 do not depend on each other except through the API contract above; Task 7's rid only takes effect once Task 4 is deployed.
- Owner check-ins only at the STOP steps (Task 4 deploy, Task 13 Firebase, Task 14 install). A debug APK after Task 12 can be offered for an early look.
- Stages 2 and 3 get their own plans after the owner has used Stage 1.
