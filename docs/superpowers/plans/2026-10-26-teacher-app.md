# EngQuest Teacher App (pilot) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phone-first teacher screens (Attendance, Submissions, Give XP, Scores) in a new `teacher-app/` folder, loaded by the EngQuest Teacher Android app v2.0.

**Architecture:** A single-page app in `teacher-app/index.html` with hash routes (`#/attendance`, `#/submissions`, `#/xp`, `#/scores`, `#/more`). It's built from plain HTML/CSS/vanilla JS like the rest of the site, and uses the existing `EQApi` (`shared/js/api.js`, Worker `/exec`) and `EQClasses`. Pure logic lives in small UMD files that run in both the browser and Node, so `node --test` can cover it. Screens are verified in the browser pane against a local mock API (`?mock=1` on localhost), because the real backend needs the owner's password. The Android shell only changes its start URL and its chrome.

**Tech Stack:** HTML/CSS/vanilla JS (ES5-style like the repo, `var` + functions), Node 24 `node --test` for logic tests, Android Java (AGP 8.5.2) for the shell.

**Spec:** `docs/superpowers/specs/2026-10-26-teacher-app-design.md`. Visual reference: `.superpowers/brainstorm/422-1793010945/content/teacher-app-screens.html` (local).

## Global Constraints

- Don't modify existing pages or shared files (`teacher/*`, `grade10/*`, `grade11/*`, `shared/js/auth.js`, `shared/js/api.js`). Everything new goes under `teacher-app/` (plus the Android project).
- Load order in `teacher-app/index.html`: `../shared/js/classes-20260617.js?v=20260712`, `../shared/js/api.js`, then `teacher-app/js/*`.
- Teacher accent `#4338CA` (brand), `#6366F1` (brand-2), font Plus Jakarta Sans. Clean + Friendly look per the mockup.
- Attendance statuses are exactly `Present`, `Absent`, `Sick`, `Permit`, with button letters P / A / S / I.
- XP reasons come from `teacher/activeness.html` ACTS keys: `Vocabulary Box`, `Answering`, `Presenting`, `Doing Task On Time`, `Helping Hand`, `Quiz`. Chip labels show XP per tap: Vocabulary Box +2, Answering +5, Presenting +20, Doing Task On Time +20, Helping Hand +20, Quiz +10. Each tap = `incrementActiveness` with `delta: 1`. Undo = `delta: -1`.
- **Chapter numbering:** the UI's "Chapter n" = backend `chapter: String(n + 3)` (Chapter 1 → `'4'`, as in `teacher/scores.html` modes 4/5/6). Task code `C<n>T<k>` → chapter `n+3`, column `Task k`. `C<n>F` → `Formative`. `C<n>S` → `Summative`.
- Scores are valid only when empty or 0–100. Under 75 shows red ("Safe" line).
- Every write sends `username` + `password` from the app session. A response `error === 'Unauthorized'` → clear the session and show the login screen.
- Nothing writes without a tap. Opening attendance never saves.
- Run `node scripts/check-site.mjs` before every push. Push `main` after every commit.

## Review Focus

1. **Session rejected mid-use** (password changed on the website, backend says `Unauthorized`): expect the login screen with a friendly line, no dead end, no crash. Test in Task 2.
2. **A save fails (offline / timeout)**: attendance keeps the unsaved marks and shows "Not saved, tap to retry". A score input turns red and keeps the typed value. An XP tap shows an error toast and rolls back the optimistic +N. Tests in Tasks 1 and 2 (state helpers), checked in the browser in Tasks 4/6/7 with the mock set to fail.
3. **Leaving with unsaved attendance** (switching tab, class or date): expect a confirm prompt. Test in Task 1 (`attendanceDirty`), checked in Task 4.
4. **Rapid repeated taps** on Approve / XP tiles / Save: no double writes. Buttons disable while their call is in flight, and XP taps queue sequentially. Checked in Tasks 5/6.
5. **Odd rosters:** a long name (single line, ellipsis), 36 students, an empty class, a class with no pending submissions ("All caught up"), a submission whose task code doesn't map (no score box, approve still works). Tests in Task 1 (`taskToChapterCol` unmappable) and the mock data in Task 3 includes these cases.

---

### Task 1: Pure logic helpers

**Files:**
- Create: `teacher-app/js/core.js`
- Test: `teacher-app/tests/core.test.mjs`

**Interfaces:**
- Produces (global `EQT.core` in the browser, `module.exports` in Node):
  - `STATUSES: ['Present','Absent','Sick','Permit']`
  - `taskToChapterCol(code: string) -> {chapter: string, column: string} | null`
  - `validateScore(value: string) -> {ok: boolean, value: string, error?: string}` (trims, `''` ok, else integer-or-decimal 0–100)
  - `isLowScore(value: string|number) -> boolean` (non-empty and < 75)
  - `attendanceInitial(students: Array<{no, attendance}>, meetings: Array<{number,date}>, date: string) -> {meeting: {number,date}|null, marks: {[no: string]: string}}` (no meeting → all `'Present'`; meeting → stored value or `'Present'`)
  - `attendanceCounts(marks) -> {Present, Absent, Sick, Permit}`
  - `attendanceDirty(initialMarks, marks) -> boolean`
  - `attendanceRecords(marks) -> Array<{no, status}>` (the `records` payload `saveAttendance` takes)
  - `initials(name: string) -> string` (max 2 letters, uppercase, `'EQ'` fallback)
  - `firstName(name: string) -> string`
  - `groupSubmissions(rows, {className, filter: 'pending'|'all'}) -> Array<{studentNo, studentName, className, items}>` (same grouping/sorting as `teacher/submissions.html` `buildGroups` + `compareTasks`, filtered by class + status, pending-first students ordered by student number)

- [ ] **Step 1: Write the failing tests** with `node:test` + `node:assert/strict`, importing via `createRequire`:
  - `taskToChapterCol('C1T2')` → `{chapter:'4', column:'Task 2'}`. `'c3f'` → `{chapter:'6', column:'Formative'}`. `'C2S'` → `{chapter:'5', column:'Summative'}`. `'XYZ'`, `''`, `null` → `null`.
  - `validateScore('85').ok === true`. `validateScore('').ok === true`. `validateScore('101').ok === false`. `validateScore('-1').ok === false`. `validateScore('abc').ok === false`. `validateScore(' 70 ').value === '70'`.
  - `isLowScore('74') === true`, `isLowScore('75') === false`, `isLowScore('') === false`.
  - `attendanceInitial` with no meeting on the date → every mark `'Present'` and `meeting === null`. With meeting `{number:3,date:'2026-10-26'}` and a student `attendance:{m3:'Sick'}` → that mark `'Sick'`.
  - `attendanceCounts` sums correctly. `attendanceDirty` is false for equal marks and true after one change. `attendanceRecords` returns `[{no:'1',status:'Present'},…]` in student order.
  - `initials('Adit Pratama') === 'AP'`, `initials('') === 'EQ'`.
  - `groupSubmissions` drops other classes and non-pending rows when `filter:'pending'`, and orders items C1T1 < C1T2 < C1F < C1S < C2T1.
- [ ] **Step 2: Run** `node --test teacher-app/tests/` → FAIL (module not found).
- [ ] **Step 3: Implement** `teacher-app/js/core.js` as a UMD: `(function (root) { … if (typeof module === 'object' && module.exports) module.exports = core; else { root.EQT = root.EQT || {}; root.EQT.core = core; } })(this);`
- [ ] **Step 4: Run** `node --test teacher-app/tests/` → all PASS.
- [ ] **Step 5: Commit** `feat(teacher-app): core logic helpers + tests`.

### Task 2: Remembered login + API wrapper

**Files:**
- Create: `teacher-app/js/session.js`
- Test: `teacher-app/tests/session.test.mjs`

**Interfaces:**
- Consumes: `EQApi.get(params) -> Promise<object>` (injected for tests).
- Produces `EQT.createSession({storage, api}) -> session` (factory for tests). The browser global `EQT.session = EQT.createSession({storage: window.localStorage, api: window.EQApi})`:
  - `session.get() -> {username, password} | null` (localStorage key `eqt_login`, JSON)
  - `session.login(username, password) -> Promise<{ok: boolean, error?: string}>`: calls `checkLogin`, and stores only on `success`/`ok:true` (match how `teacher/index.html` reads the `checkLogin` response)
  - `session.logout()`: removes `eqt_login`
  - `session.call(params) -> Promise<object>`: merges `username`/`password` into params, calls `api.get`. If the response has `error === 'Unauthorized'`, it calls `logout()`, fires `session.onExpired()` (assignable callback) and rejects with `Error('Session expired')`. Any other `error` or `success === false` → rejects with `Error(res.error || 'Save failed')`.
  - `session.read(params) -> Promise<object>`: like `call`, but without credentials (for open GETs like `getClassData`) and the same error handling.
  - Class choice: `session.getClass() -> string|null` / `session.setClass(id)` (key `eqt_class`).

- [ ] **Step 1: Write the failing tests** using an in-memory `storage` ({getItem,setItem,removeItem}) and a fake `api.get` that records params and returns scripted responses:
  - `login` success stores `eqt_login`, and `get()` returns it. A login failure stores nothing and resolves `{ok:false, error}`.
  - `call({action:'saveAttendance'})` sends `username` + `password`.
  - An `Unauthorized` response → `get()` becomes `null`, `onExpired` is called once, and the promise rejects with `'Session expired'`.
  - `{success:false,error:'Boom'}` → rejects with `'Boom'`, and the session is kept.
  - `setClass('XE2')` then `getClass() === 'XE2'`.
- [ ] **Step 2: Run** `node --test teacher-app/tests/` → session tests FAIL.
- [ ] **Step 3: Implement** `teacher-app/js/session.js` (same UMD pattern, export `createSession`).
- [ ] **Step 4: Run** `node --test teacher-app/tests/` → all PASS.
- [ ] **Step 5: Commit** `feat(teacher-app): remembered login session + API wrapper`.

### Task 3: App frame, login, class picker, More, mock API

**Files:**
- Create: `teacher-app/index.html`, `teacher-app/app.css`, `teacher-app/js/app.js`, `teacher-app/js/screens/more.js`, `teacher-app/dev/mock-api.js`, `teacher-app/dev/serve.mjs`, `.claude/launch.json`

**Interfaces:**
- Consumes: `EQT.core`, `EQT.session`, `EQApi.classLabels`, `EQClasses.all`.
- Produces in `teacher-app/js/app.js`:
  - `EQT.registerScreen(name: 'attendance'|'submissions'|'xp'|'scores'|'more', {title: string, render(el: HTMLElement, ctx), leave?(): boolean})`. `leave()` returning `false` cancels navigation (used for unsaved attendance).
  - `ctx = {className: string, classLabel: string, session, core, toast(msg, type?: 'ok'|'err', action?: {label, onClick}), setSubtitle(text), setBadge(tab, count|null)}`.
  - Re-render the current screen when the class changes (after the `leave()` check).
- Behaviour:
  - Login screen when `session.get()` is null. It's a full-screen indigo card with username/password and a Login button that disables while checking, and shows errors inline. `session.onExpired` → login screen with "Please log in again."
  - App bar: title + subtitle line + class pill. Tapping the pill opens a bottom sheet listing the 8 classes, grouped Grade 10 / Grade 11. The choice is stored with `session.setClass`. The default is the first class.
  - A bottom tab bar with the 5 tabs (icons inline SVG as in the mockup). Hash routing: the default is `#/attendance`, and the Android back button goes through history.
  - Toast area above the tab bar.
  - More screen: a list of links to `../teacher/materials.html`, `assignments.html`, `announcements.html`, `tasks.html`, `classes.html`, `vocabulary.html`, `flashcards.html`, `strikes.html`, `settings.html` (labels from `NAV_ITEMS` in `shared/js/teacher-sidebar.js`), plus "Open full teacher website" (`../teacher/`) and **Log out** (confirm → `session.logout()` → login screen).
  - Loading the mock: an inline script in `index.html` that, only when `location.hostname` is `localhost`/`127.0.0.1` and the query has `mock=1`, `document.write`s `<script src="dev/mock-api.js">` after `api.js`. `mock-api.js` replaces `EQApi.get` with an in-memory fake for every action in the spec. The fake has 2 classes, one with 36 students including a very long name, an empty class, submissions with an unmappable task code, `checkLogin` accepting any non-empty password, and `?fail=1` making every write reject.
  - `dev/serve.mjs`: a zero-dependency static server for the repo root on port 5173 (serves `.html/.js/.css/.json/.png/.jpg/.svg`, blocks dotfiles). `.claude/launch.json` gets config `teacher-app` → `node teacher-app/dev/serve.mjs`, port 5173.

- [ ] **Step 1:** Build the files above. Screens other than More register as placeholders ("Coming next").
- [ ] **Step 2: Verify in the browser pane** (`preview_start teacher-app`, mobile preset), at `http://localhost:5173/teacher-app/?mock=1`: login works, then the tabs switch with matching hashes, the class sheet changes the pill, and the choice survives a reload. Logout returns to login. No console errors. No horizontal scroll at 375px (`document.documentElement.scrollWidth === 375`).
- [ ] **Step 3:** Run `node --test teacher-app/tests/` → PASS, and `node scripts/check-site.mjs` → PASS. If check-site flags `teacher-app/dev/*` or the tests, exclude `teacher-app/dev` and `teacher-app/tests` in the script's ignore list rather than reshaping the files.
- [ ] **Step 4: Commit** `feat(teacher-app): app frame, login, class picker, More`.

### Task 4: Attendance screen

**Files:**
- Create: `teacher-app/js/screens/attendance.js` (+ styles in `app.css`)

**Interfaces:**
- Consumes: `ctx`, `EQT.core.attendanceInitial/Counts/Dirty/Records`, `STATUSES`; `session.read({action:'getClassData', className})` → `{students:[{no,name,nickname,attendance}], meetings:[{number,date}]}`; `session.call({action:'saveAttendance', className, date, records: JSON.stringify(records)})` → `{success, meetingNumber}`; `session.call({action:'removeAttendanceMeeting', className, date})`.
- Behaviour per spec. The subtitle shows "Monday, 26 Oct · Meeting N", or "· New meeting" when there's no meeting yet. Tapping the subtitle opens a date input (defaults to today, local time). Show 4 summary chips, rows with P/A/S/I, and a sticky save bar ("Not saved yet · N changes" / "All saved ✓"). Save disables while saving. On failure keep the marks and show "Not saved, tap to retry". `leave()` confirms when dirty. In an overflow "⋯" on the app bar area: "Mark everyone Present" and "Remove this meeting" (only when a meeting exists, with confirm).
- [ ] **Step 1:** Implement.
- [ ] **Step 2: Verify in the browser** with the mock: mark 2 students, check the counts update, then Save → "All saved". Reload → the marks come back from the mock. Switching tab while dirty prompts. `?fail=1` → the save error keeps the marks. 36 students, the long name ellipsizes, and there's no horizontal scroll.
- [ ] **Step 3:** Take a screenshot for the record. Commit `feat(teacher-app): attendance screen`.

### Task 5: Submissions screen

**Files:**
- Create: `teacher-app/js/screens/submissions.js` (+ styles)

**Interfaces:**
- Consumes: `session.call({action:'getTaskSubmissions'})` → `{success, submissions:[{subId, className, studentNo, studentName, task, timestamp, status:'pending'|'reviewed'|'rejected'}], pending}`; `session.call({action:'getSubmissionPhoto', fileId: subId})` → `{success, mimeType, dataBase64}`; `session.call({action:'reviewTaskSubmission', subId, decision:'reviewed'|'rejected'})`; `session.call({action:'saveTaskStatus', className, studentNo, tasks: JSON.stringify({[task]: true})})`; `session.call({action:'saveChapterScore', className, chapter, studentNo, column, value})`; `EQT.core.groupSubmissions`, `taskToChapterCol`, `validateScore`; `ctx.setBadge('submissions', pendingCountForClass)`.
- Behaviour per spec: one student at a time (from `groupSubmissions` for the current class), with a Pending/All toggle. A photo carousel (swipe + dots + "1 / 3") lazy-loads photos into an in-memory cache. Tapping a photo opens a full-screen viewer with pinch-zoom (`touch-action: pinch-zoom` on the img container). Each pending photo shows its task chip and a score box when `taskToChapterCol` maps. **Approve & next** approves every pending photo of the student currently shown in sequence. For each: `reviewTaskSubmission reviewed` → `saveTaskStatus` → `saveChapterScore` if a valid score was typed for that photo. Then advance. **Send back** rejects the currently shown photo, with confirm. Buttons disable during calls. A queue strip shows the remaining students with pending counts, and tapping one jumps to them. When empty: "🎉 All caught up in X E-1".
- [ ] **Step 1:** Implement.
- [ ] **Step 2: Verify in the browser** with the mock: carousel, full-screen view, approve with score (the mock records `saveChapterScore` with chapter `'4'` for `C1T*`), unmappable task (no score box, approve works), Send back, advance, the empty state, and the badge count drops. `?fail=1` → error toast, stays on that student.
- [ ] **Step 3:** Commit `feat(teacher-app): submissions screen`.

### Task 6: Give XP screen

**Files:**
- Create: `teacher-app/js/screens/xp.js` (+ styles)

**Interfaces:**
- Consumes: `session.read({action:'getAllActiveness', className})` → `{students:[{no,name,nickname,Total,Indicator, …}]}`; `session.call({action:'incrementActiveness', className, studentNo, column, delta})`.
- Behaviour per spec: reason chips (Global Constraints list, Answering selected by default, choice remembered in `localStorage` key `eqt_xp_reason`) and a 3-column tile grid (initials avatar with a stable colour per student no., first name, `Total` XP). A tap is optimistic: +N on the tile, a pop animation, and a toast "+5 XP · Adit · Answering" with **Undo**. Taps go through one sequential promise queue. A failure rolls back that tile and shows an error toast. Undo sends `delta:-1` for that tap's column. Re-fetch totals with `getAllActiveness` after the queue drains (debounced 1.5 s) so `Total` stays true to the backend.
- [ ] **Step 1:** Implement.
- [ ] **Step 2: Verify in the browser** with the mock: 5 fast taps → 5 sequential calls, Undo → `delta:-1` recorded, `?fail=1` → rollback, switching the reason changes the +N.
- [ ] **Step 3:** Commit `feat(teacher-app): give XP screen`.

### Task 7: Scores screen

**Files:**
- Create: `teacher-app/js/screens/scores.js` (+ styles)

**Interfaces:**
- Consumes: `session.read({action:'getChapterScores', className, chapter})` / `session.read({action:'getFinalScores', className})` → `{students, inputCols, calcCols}`; `session.call({action:'saveChapterScore'|'saveFinalScore', className, chapter?, studentNo, column, value})` → `{success, calc}`; `EQT.core.validateScore`, `isLowScore`.
- Behaviour per spec: chapter chips Ch 1 / Ch 2 / Ch 3 (backend `'4'/'5'/'6'`, matching `teacher/scores.html` at build time; if it lists more chapters for a grade, mirror them) + Final. A column segmented control is built from `inputCols`, with the last choice remembered per chapter. Rows show number, name, an input (`inputmode="decimal"`, `enterkeyhint="next"`) and the running average from `calcCols` / `calc`. Enter/Next focuses the next row's input. Save on `change`/blur only when the value differs. Invalid → red + toast, nothing sent. Saving → "Saved ✓ just now" line. Failure → red input with the value kept.
- [ ] **Step 1:** Implement.
- [ ] **Step 2: Verify in the browser** with the mock: type → Next → saved call with chapter `'4'`, 101 rejected locally, red under 75, Final tab uses `saveFinalScore`, `?fail=1` keeps the value red.
- [ ] **Step 3:** Commit `feat(teacher-app): scores screen`.

### Task 8: Android shell v2.0

**Files:**
- Modify: `D:\android-dev\EngQuestTeacher\app\src\main\java\com\engquest\app\MainActivity.java`, `…\res\layout\activity_main.xml`, `…\app\build.gradle`, and the manifest/theme if needed for the status bar colour

**Interfaces:**
- Start URL `https://engquestviska.github.io/teacher-app/`. Last-URL restore accepts URLs starting with `…/teacher-app/` or `…/teacher/`.
- [ ] **Step 1:** Remove the native top bar (`topBar`, `menuBtn`, `homeBtn`) from the layout and the code. Set the status bar colour to `#4338CA` (`getWindow().setStatusBarColor`). Keep the splash, file chooser, back button, download handling and `onPause` URL save.
- [ ] **Step 2:** Add a floating "← App" pill (bottom-left, indigo, rounded), visible only when the current URL is not under `/teacher-app/`. Update it in `onPageStarted`/`doUpdateVisitedHistory`. Tapping loads the start URL.
- [ ] **Step 3:** `versionCode 2`, `versionName "2.0"`. Build with the PowerShell commands from the project memory (`gradle.bat -p D:\android-dev\EngQuestTeacher --no-daemon --offline assembleRelease`) → BUILD SUCCESSFUL. Check with `aapt dump badging` → `versionCode='2'`, and `apksigner verify` → exit 0.
- [ ] **Step 4:** Copy to `C:\Users\acer\Desktop\EngQuest-Teacher.apk` (overwrite v1.0).

### Task 9: Publish the pilot

- [ ] **Step 1:** `node --test teacher-app/tests/` → PASS. `node scripts/check-site.mjs` → PASS.
- [ ] **Step 2:** Commit any remaining files, then push `main`. Fast-forward `next-year` to `main` and push it if it fast-forwards cleanly. If it doesn't, leave it and tell the owner.
- [ ] **Step 3:** Once Pages serves it (a GET to `https://engquestviska.github.io/teacher-app/` returns the new page), open it in the browser pane at mobile size and confirm the login screen renders with no console errors. Logging in is the owner's step.
- [ ] **Step 4:** Hand the owner: the Desktop APK, how to install over v1.0, and what to try in class (the 4 jobs). Collect feedback for iteration.
