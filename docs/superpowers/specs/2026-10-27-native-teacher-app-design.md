# EngQuest Teacher — native Android app (design)

Date: 2026-10-27 · Owner: the EngQuest teacher (non-technical; reacts best to rendered options)

## Why

The current teacher app (v2.0, `com.engquest.teacher`) is a ~60 KB WebView shell that loads
`https://engquestviska.github.io/teacher-app/` and opens other website pages from its More tab.
The owner liked the pilot's screens but wants "a real app, not a website pretending to be an app":
every screen built into the APK, working directly and reliably with the EngQuest database.

## Goals

1. **Every teacher screen is native.** No WebView screen anywhere, including links out to
   `teacher/*.html`. (Anti-goal: any screen that quietly shows a website page.)
2. **Opens instantly.** Classes, rosters and the last-seen data of each screen are stored on the
   phone and shown immediately; fresh data replaces them when it arrives.
3. **Works when the school Wi-Fi drops.** Attendance marks, XP taps (give and take back), scores,
   task status, strikes and submission reviews made offline are saved on the phone and sent, in
   order, once back online. (Anti-goal: losing a mark, XP tap or score because the connection cut.)
4. **Submission notifications.** A normal Android notification when a student uploads, naming the
   student, class and task, e.g. "Bella Kusuma (XE1) sent C1 Task 2". Tapping it opens that
   submission. Several photos from one student in a short burst = one notification.
5. **Stays logged in** until the teacher logs out.
6. **Same look as the approved pilot**: indigo `#4338CA` brand, rounded cards, bottom tab bar, the
   pilot's interactions (one-student-at-a-time submissions review, tap-to-give XP with Give/Take
   back toggle and Undo, type-down-the-list scores with ASAS label and Final score column,
   P/A/S/I attendance rows with a save bar).

## Non-goals

- Student app (later, separate project).
- Google Play. Updates are side-loaded APKs (owner's choice).
- Flashcards page — dropped; Quiz XP is given by hand on the XP screen.
- iOS.
- Changing the website. It keeps working as-is; the app and website share one database.

## Screens and stages

Each stage ends in an APK the owner installs over the previous one and tries on the phone.

Bottom tab bar (all stages): **Home · Attendance · Submissions · XP · Scores**. Home appears in
stage 2; until then the app opens on Attendance.

### Stage 1 — daily work (parity with the pilot, natively)
| Screen | Backend actions |
|---|---|
| Login | `checkLogin` |
| Class picker (all 8 classes, Grade 10 + 11) | from the class list (see Data) |
| Attendance | `getClassData`, `saveAttendance`, `removeAttendanceMeeting` |
| Submissions | `getTaskSubmissions`, `getSubmissionPhoto`, `reviewTaskSubmission`, `saveTaskStatus`, `saveChapterScore` (score box) |
| Give XP | `getAllActiveness`, `incrementActiveness` (delta +1 / −1) |
| Scores (Ch 1–3 + Final) | `getChapterScores`, `getFinalScores`, `saveChapterScore`, `saveFinalScore` |
| Offline outbox + "sending…/sent" indicator | — |
| Submission notifications | new (see Notifications) |
| "New version available" banner | new (see Updates) |

Behaviour carried over exactly from the pilot (`teacher-app/js/*`, which is the reference
implementation): chapter mapping UI n → backend n+3, only C1–C3 / T1–T3 get a score box,
approve order score → review → task status, ASAT column shown as "ASAS", Final shows
`calc['Final Score']` and recomputes with the backend formula after a save, XP take-back blocked
at 0 for that reason, attendance draft kept across a re-login.

### Stage 2 — weekly
| Screen | Backend actions |
|---|---|
| Home (design **A**, chosen): date + "Welcome back", Champions of the semester (top 3), Quick access grid → Tasks, Assignments, Materials, Strikes, Announcements, Vocabulary | `getLeaderboard` |
| Tasks | `getAllTasks`, `saveTaskStatus` |
| Assignments | `getMaterials`, `addMaterial`, `deleteMaterial`, `getUnitNames` |
| Materials | `getMaterials`, `addMaterial`, `deleteMaterial`, `getUnitNames`, `setUnitNames` |
| Strikes (per class: count per student, each strike's reason + date, quick reasons as on the website; 1 strike blocks summatives, 3 block more) | `getAllStrikes` (**missing on the Worker** — see Backend changes), `addStrike`, `removeStrike` |

### Stage 3 — rarely used
| Screen | Backend actions |
|---|---|
| Announcements (audience 10 / 11 / both) | `getAnnouncement`, `setAnnouncement`, `clearAnnouncement` |
| Classes (student lists) | `getClassData` / `getStudents` |
| Settings (change password, log out, app version) | `setTeacherPassword` |
| Vocabulary (view each student's word box; remove a word. **No checking** — owner, 2026-10-27) | `getClassVocabulary`, `removeVocabulary` |

Each screen must do everything its `teacher/*.html` page does today (the page is the spec for
fields and rules); the pilot's mobile layout patterns are the spec for how it looks.

## Data and backend

- **Server:** Cloudflare Worker `engquest-api` (`https://engquest-api.engquestviska.workers.dev`),
  code in `C:\Users\acer\Documents\Engquest-turso-lab` (`worker/`, `pilot/router.mjs`,
  `pilot/compat.mjs`), database Turso `engquest-prod`.
- The app calls the same `/exec?action=…` bridge as the website, **without** `callback`, so it
  gets plain JSON. Reads = GET, writes = POST JSON body. Teacher credentials go with every write,
  as the website does today.
- Class list: the 8 classes in `shared/js/classes-20260617.js` (bundled; builder may read it from
  the site at start-up instead).
- Photos come from `getSubmissionPhoto` (base64). Never trust `mimeType` (pilot's `photoSrc` rule).

## Backend changes (one Worker update, deployed with the owner's go-ahead)

Found in the builder check (2026-10-27) against `pilot/compat.mjs`:
1. **`getAllStrikes` does not exist** on the bridge (only `getStudentStrikes`, count only), so the
   website's Strikes page likely fails today. Add it, returning each student's strikes with reason
   and date, in the shape `teacher/strikes.html` expects (`{students:[...]}`). Fixes the website too.
2. **Repeat-safe writes.** Writes carry a client request id; the Worker records ids and ignores a
   repeat. Needed for `incrementActiveness`, `addStrike`, `removeStrike` (deletes the latest),
   `addMaterial` — all unsafe to resend.
3. **Client date.** `addStrike`, `reviewTaskSubmission`, `addMaterial`, `setAnnouncement` stamp the
   server's date; accept an optional client `date` so offline actions keep the day they were made.
4. **Push**: device-token registration action + send on `submitTaskPhoto` (see Notifications).
5. `checkVocabulary` is a stub today (returns no `words`); not used — the app has no checking.

All are additive; the website keeps working unchanged.

## Offline

- Every read is cached on the phone (per screen + class) and shown first, with a quiet "updated
  2 min ago" / refreshing state.
- Every write goes into an on-phone outbox, applied to the cached data at once (the screen
  updates immediately), and sent in order when online, surviving the app being closed or the
  phone restarting.
- No write may take effect twice: each queued write carries a request id and the Worker ignores
  repeats (Backend changes §2). Rule: no double XP, no double strike, no double material.
- If the website changed the same thing meanwhile, the later write wins (same as today).
- A visible count of "N changes waiting to send" so the teacher knows nothing is lost.

## Notifications

- Firebase Cloud Messaging (free). The app registers its device token with the server (new
  action, teacher-authenticated). The Worker's `submitTaskPhoto` handler sends a push to the
  registered device(s) after a successful upload.
- Grouping: one notification per student per short burst (builder picks the window, ~2 min).
- Tap → opens Submissions on that student. Android 13+ notification permission asked once, after
  login, with a plain sentence why.
- **Owner setup step:** a Firebase project needs a Google account. The builder prepares exact
  click-by-click steps; the owner does the sign-in. Sending uses FCM HTTP v1 (the legacy server
  key is retired): the owner downloads a service-account JSON key, stored as a Worker secret; the
  Worker signs a JWT with it to get an access token for each send (cache the token ~1 h).
- Worker change is a production backend change → deploy needs the owner's go-ahead.

## Updates (no Google Play)

- Same `applicationId` (`com.engquest.teacher`) and keystore (`D:\android-dev\keystore\engquest.jks`)
  so each new APK installs over the old one. versionCode continues from 2.
- The app checks a small version file (e.g. on GitHub Pages) at start-up; if newer, shows
  "New version available" with a button that downloads the APK. Owner can also be sent the file
  directly as today.

## Builder's calls (my decisions, owner can overrule)

- Kotlin + Jetpack Compose, Material 3 themed to the pilot's look; local storage via Room or
  DataStore; background sending via WorkManager; HTTP via OkHttp/Ktor. Size of several MB is
  expected and fine.
- Project lives at `D:\android-dev\EngQuestTeacherNative` (toolchain already on D:), and its source is
  also kept in git (new folder in this repo or its own repo — builder's call; it must be backed up).
- Login stored with Android encrypted storage; the plain password is still sent because the bridge
  needs it.
- Stage 1 ships before stage 2 starts; owner tries each stage on the phone.
- Notification grouping window, cache freshness wording, and empty/error states follow the pilot.

## Done looks like

- Stage 1 APK: all four daily screens work against the live database, survive airplane mode
  (queued, then sent), notification arrives within a minute of a student upload, no WebView.
- Stage 3 APK: every teacher website page except Flashcards has a native screen; the owner never
  needs to open the website for teacher work.
