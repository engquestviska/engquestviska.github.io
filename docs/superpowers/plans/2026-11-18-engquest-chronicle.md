# EngQuest Chronicle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Owner's choice (2026-11-18): Native.** Build every task inline in one session, then one fresh review of the whole change at the end. No per-task subagents.

**Goal:** From a fixed date, the student website and student app open EngQuest Chronicle (an animated 8-slide semester story, a 9:16 share picture, and a records page) instead of the normal student pages.

**Architecture:** The backend gets one bundled read action (`getChronicle`) plus a cheap status check (`getChronicleStatus`), both deciding "is it on?" from a server-side date setting. One web page, `chronicle.html`, renders the story, records, and share card from that bundle. Student web pages redirect to it when it's on; the native app swaps its tabs for a WebView of the same page, with a small JS bridge for the student profile and Android sharing.

**Tech Stack:** Cloudflare Worker + Turso (`C:\Users\acer\Documents\Engquest-turso-lab`, `pilot/compat.mjs`, tests with `node --test`), plain HTML/CSS/JS website (`C:\Users\acer\Documents\Github Engquest`, GitHub Pages), Kotlin/Compose student app (`native-student/`).

**Spec:** `docs/superpowers/specs/2026-11-18-engquest-chronicle-design.md`. Visual target: `docs/superpowers/specs/2026-11-18-engquest-chronicle-concept.html` (open in a browser; it plays).

## Global Constraints

- Name on screen: **EngQuest Chronicle**. Never "Wrapped".
- Look: paper `#F4F2ED`, ink `#141414`, accent G10 `#2450E0`, G11 = the existing site teal. One accent per slide. Fonts: Playfair Display (numbers, italic words), Bricolage Grotesque (labels), Nunito (body).
- Numbers: stat slides 150–160px with only a slight crop; Final Score fully readable; Class Rank is the biggest number in the story and fully visible.
- Motion is required: every slide has an entrance; the story auto-advances every 4.5 s and a tap skips ahead. Respect `prefers-reduced-motion` (show slides at rest).
- Real numbers for every student, including rank. No softening.
- Switch: on at 00:00 WIB (`+07:00`) on the date in setting `chronicle_start`; the **server** decides, never the phone clock.
- Teacher website and teacher app: no changes.
- Share picture: 1080×1920 PNG. Instagram sticker: the same card at 720×1280 with rounded corners on a transparent background.
- Transitions: same background → push (old fades left, new glides in from the right, ~0.55 s); background change → circle wipe from 50% 80% (~0.8 s); leaving slides shrink their circle and drop their big number. Idle motion per slide as listed in the spec's "Transitions and idle motion" section (XP bar fill 2.4 s, XP count 2.6 s, vocabulary rows loop in 22–38 s).
- Final Score is always a whole number (no decimals) on slides, records, and the share card.
- Rank numerals scale with digit count: 1 digit 250px, 2 digits 170px, 3 digits 120px. Grade Rank uses 170px for 1–2 digits and 120px for 3.
- The story is 8 slides (cover, attendance, tasks, vocab, level, final, rank, gradeRank), then the share card.
- Sharing: in the app, the primary button is **Share to Instagram Story** (hidden if Instagram isn't installed) and the second is **More ways to share** (share sheet). On the website: **Share** (Web Share), or **Download picture** when Web Share with files is unavailable.
- Story plays once per device (localStorage `eq_chronicle_seen`); Replay plays it again.
- Missing data: skip that slide, never show a celebratory 0.
- Copy rules (owner): short and plain; no exclamation marks in system text.
- Repo rules: run `node scripts/check-site.mjs` before every website commit. Claude does not push (a hook blocks it); the owner pushes. Deploying the Worker to production and releasing the app need the owner's OK.

## Review Focus

1. **Tied scores**: two students on the same Final Score share a place, and the next student skips (88, 88, 85 → #1, #1, #3), matching the Scores "Class Rank" tab. Test in Task 1.
2. **Student with no Final Score yet** (blank `''`): rank is `null`, slides 6–7 are skipped, and the share card shows "—" for Final and Rank. Tests in Tasks 1 and 3.
3. **Before the start date / setting missing**: `active: false`, and every page behaves exactly as today. Tests in Tasks 1 and 7.
4. **Long names** ("Muhammad Rafi Alfarizi Pratama"): the name shrinks to fit two lines on the cover, card, and records page, never cropped. Check in Tasks 4 and 6 using the `?demo=longname` sample.
5. **A logged-in student no longer in the class** (inactive or unknown number): `getChronicle` returns `{ error: 'Student not found' }`, and the page shows "We couldn't find your record. Talk to your teacher." instead of a broken story. Tests in Tasks 1 and 5.

---

## File map

Backend (`C:\Users\acer\Documents\Engquest-turso-lab`):
- Modify `pilot/compat.mjs`: add `classFinalScores(query, className)` (extracted from `getFinalScores`, which then calls it), `rankPlaces(students)`, and the actions `getChronicleStatus` and `getChronicle`.
- Create `pilot/test/chronicle.test.mjs`.
- Create `migrate/set-chronicle.mjs`: writes `chronicle_start` / `chronicle_label` to a chosen database.

Website (`C:\Users\acer\Documents\Github Engquest`):
- Create `chronicle.html`: the page.
- Create `shared/css/chronicle.css`: tokens, slides, player, records, motion (ported from the concept page).
- Create `shared/js/chronicle-data.js`: pure functions that turn the API bundle into a view model (loads in the browser and in Node).
- Create `scripts/chronicle-data.test.mjs`: Node tests for it.
- Create `shared/js/chronicle-story.js`: renders slides + the story player.
- Create `shared/js/chronicle-card.js`: draws the share card to a canvas and returns a PNG Blob.
- Create `shared/js/chronicle-page.js`: loads data and runs story → records, Replay, Share/Download.
- Create `shared/js/chronicle-gate.js`: redirects student pages to `chronicle.html` when it's on.
- Modify every page that loads `shared/js/student.js`: add the gate script.

App (`native-student/`):
- Modify `data/Repo.kt`: `suspend fun chronicleActive(): Boolean`.
- Create `ui/chronicle/ChronicleScreen.kt`: WebView + `ChronicleBridge`.
- Modify `ui/App.kt` (`Shell`): show `ChronicleScreen` instead of the tabs when active.
- Modify `app/build.gradle.kts`: version 2.1 (versionCode 14) at release.
- Tests: `app/src/test/.../RepoTest.kt`, `ShellTest.kt`, new `ChronicleTest.kt`.

---

### Task 1: Backend: `getChronicleStatus` and `getChronicle`

**Files:**
- Modify: `pilot/compat.mjs` (`getFinalScores` block, about lines 217–262)
- Test: `pilot/test/chronicle.test.mjs`

**Interfaces:**
- Produces `getChronicleStatus` with params `{}` → `{ success: true, active: boolean, start: string }`. `start` is the stored value or `''`; `active` = `start !== '' && Date.now() >= Date.parse(start)`.
- Produces `getChronicle` with params `{ className, studentNo }` → when the student is found:
```js
{ success: true, active, start, label,            // label from setting chronicle_label, default 'Semester 1 · 2026/27'
  student: { name, nickname, classId, grade, no },
  attendance: { meetings, present, sick, permit, absent, days: [{ date, status }] }, // status '' when unmarked
  tasks: { done, best: { label, score } | null }, // best = highest task1..3 across units; label `${unitName} · Task ${n}`
  vocab: { total, words: [english...], latest: string | '', classTop5: boolean }, // words oldest→newest
  xp: { total, level, toNext },                  // levelFromXp from pilot/events.mjs
  scores: { units: [{ label, score }], asat, final }, // same numbers as getFinalScores; final '' when none
  rank: { place: number | null, of: number },     // of = active students in class with a Final Score
  gradeRank: { place: number | null, of: number, classes: [classId...] } } // across all active classes of the student's grade
```
  Otherwise `{ error: 'Student not found' }`.
- Produces exported helpers `classFinalScores(query, className)` → the `students` array `getFinalScores` returns today, and `rankPlaces(students, key = (s) => s.no)` → `Map<key, place>` using competition ranking on `calc['Final Score']` (blank excluded). For the grade rank, call `classFinalScores` for every active class of that grade, tag each row with `classId`, and rank with `key = (s) => s.classId + ':' + s.no`.

- [ ] **Step 1: Write the failing tests** in `pilot/test/chronicle.test.mjs`, using `makeTestDb`, `seedTeacher`, and `seedStudents` like `compat.test.mjs`:

```js
test('status is off when chronicle_start is missing', async () => {
  const db = makeTestDb();
  const r = await handleCompat(db, 'getChronicleStatus', {}, null);
  assert.deepEqual(r, { success: true, active: false, start: '' });
});
test('status is on once the start time has passed, off before it', async () => {
  // set chronicle_start to an hour ago -> active true; to tomorrow -> active false
});
test('rank ties share a place and the next one skips', () => {
  const s = [{ no: 1, calc: { 'Final Score': 88 } }, { no: 2, calc: { 'Final Score': 88 } },
             { no: 3, calc: { 'Final Score': 85 } }, { no: 4, calc: { 'Final Score': '' } }];
  assert.deepEqual([...rankPlaces(s)], [[1, 1], [2, 1], [3, 3]]);
});
test('getChronicle bundles one student', async () => {
  // seed XE1 x3, unit names, chapter_scores (student 1 task scores 80/96/70 in chapter '5'),
  // attendance on 3 dates (Present, Sick, Absent), 4 vocabulary words, xp row, 2 task_status done
  const r = await handleCompat(db, 'getChronicle', { className: 'XE1', studentNo: 1 }, null);
  assert.equal(r.attendance.meetings, 3);
  assert.equal(r.attendance.present, 1); assert.equal(r.attendance.sick, 1); assert.equal(r.attendance.absent, 1);
  assert.deepEqual(r.tasks.best, { label: '<unit 2 name> · Task 2', score: 96 });
  assert.equal(r.vocab.total, 4); assert.equal(r.vocab.latest, r.vocab.words[3]);
  assert.equal(r.tasks.done, 2);
  assert.equal(r.rank.of, 3);
  assert.equal(typeof r.xp.level, 'number');
});
test('getChronicle with no Final Score gives rank null', async () => { /* student with no chapter rows: rank.place and gradeRank.place null */ });
test('grade rank spans every class of the grade and ignores the other grade', async () => {
  // XE1 x2 (finals 90, 80), XE2 x2 (finals 85, 80), XIF7 x1 (final 99)
  // Grade 10 finals 90, 85, 80, 80 -> places 1, 2, 3, 3; the G11 student is not counted
  const r = await handleCompat(db, 'getChronicle', { className: 'XE2', studentNo: 2 }, null);
  assert.deepEqual([r.gradeRank.place, r.gradeRank.of], [3, 4]);
  assert.deepEqual(r.gradeRank.classes, ['XE1', 'XE2']);
});
test('getChronicle for an unknown or inactive student returns an error', async () => {
  assert.deepEqual(await handleCompat(db, 'getChronicle', { className: 'XE1', studentNo: 99 }, null), { error: 'Student not found' });
});
```

- [ ] **Step 2: Run them and confirm they fail.** Run: `node --test pilot/test/chronicle.test.mjs` in `Engquest-turso-lab`. Expected: FAIL (unknown action / `rankPlaces` not exported).

- [ ] **Step 3: Implement.** Extract `classFinalScores` from the `getFinalScores` block without changing its output (`getFinalScores` returns `{ students: await classFinalScores(...), inputCols, calcCols }`). Add `rankPlaces`, then the two actions. Read the settings with the existing `getSettingVal`. Both actions are student reads: no teacher auth, the same as `getVocabulary`.

- [ ] **Step 4: Run all backend tests.** Run: `node --test pilot/test/`. Expected: all PASS, including the existing `getFinalScores` tests (output unchanged).

- [ ] **Step 5: Commit** in `Engquest-turso-lab`: `git add pilot/compat.mjs pilot/test/chronicle.test.mjs && git commit -m "feat(chronicle): getChronicle + getChronicleStatus"`.

### Task 2: Setting script

**Files:** Create `migrate/set-chronicle.mjs` (in `Engquest-turso-lab`).

**Interfaces:** CLI `node migrate/set-chronicle.mjs --db lab|prod --start 2026-12-19 [--label "Semester 1 · 2026/27"] [--off]`. It stores `chronicle_start` = `${date}T00:00:00+07:00`, and `--off` stores `''`. It reuses the connection setup `migrate/read-prod.mjs` uses, and prints the stored values back.

- [ ] **Step 1:** Write the script. `--db prod` must print the values and ask for `--yes` before writing.
- [ ] **Step 2:** Run it against lab with `--start` set to yesterday. Expected output: `chronicle_start = <yesterday>T00:00:00+07:00`. Then confirm `getChronicleStatus` on lab returns `active: true` (via the lab Worker, or the test db helper).
- [ ] **Step 3: Commit:** `git commit -m "chore(chronicle): set-chronicle script"`.

### Task 3: View model (`chronicle-data.js`)

**Files:** Create `shared/js/chronicle-data.js` and `scripts/chronicle-data.test.mjs`.

**Interfaces:**
- Consumes: the `getChronicle` response (Task 1).
- Produces `EQChronicleData.build(bundle, { grade })` → `{ name, classLabel, gradeLabel, label, accent, slides: [{ id, ...fields }], card: { name, classLine, rank, final, level, present, tasks, words }, records: {...} }`. Slide ids, in order: `cover, attendance, tasks, vocab, level, final, rank, gradeRank` (the share card is slide 9, added by the story). The card gains `gradeRank` (e.g. `'#11/178'`, or `'—'`). Also produces `rankFontSize(place: number, base: 250 | 170) → number`, implementing the digit-count rule from Global Constraints. Also `EQChronicleData.SAMPLE` (the concept's Rafi numbers) and `EQChronicleData.SAMPLE_LONGNAME`.
- The file is a UMD-style IIFE that sets `window.EQChronicleData`, and also sets `module.exports` when `module` exists, so Node can `require` it.

- [ ] **Step 1: Write the failing tests** (`node:test`, loading the file with `createRequire`):

```js
test('a full bundle gives 8 slides in order', () => {
  assert.deepEqual(build(SAMPLE, { grade: '10' }).slides.map(s => s.id), ['cover','attendance','tasks','vocab','level','final','rank','gradeRank']);
});
test('rank numerals shrink with more digits', () => {
  assert.deepEqual([4, 24, 105].map(n => rankFontSize(n, 250)), [250, 170, 120]);
  assert.deepEqual([11, 105].map(n => rankFontSize(n, 170)), [170, 120]);
});
test('final score shows as a whole number', () => { /* final 87 -> card.final === '87' */ });
test('no attendance meetings skips the attendance slide', () => { /* meetings: 0 -> no 'attendance' */ });
test('no vocabulary skips the vocab slide', () => { /* total: 0 */ });
test('blank final skips final and rank, card shows a dash', () => {
  const vm = build({ ...SAMPLE, scores: { ...SAMPLE.scores, final: '' }, rank: { place: null, of: 36 } }, { grade: '10' });
  assert.ok(!vm.slides.some(s => ['final', 'rank', 'gradeRank'].includes(s.id)));
  assert.equal(vm.card.final, '—'); assert.equal(vm.card.rank, '—');
});
test('present percent rounds to a whole number', () => { /* 31 of 34 -> '91%' */ });
test('grade 11 uses the teal accent', () => { assert.notEqual(build(SAMPLE, { grade: '11' }).accent, '#2450E0'); });
test('class label reads X-E2 for XE2', () => { assert.equal(build(SAMPLE, { grade: '10' }).classLabel, 'X-E2'); });
```

- [ ] **Step 2:** Run `node --test scripts/chronicle-data.test.mjs`. Expected: FAIL (module missing).
- [ ] **Step 3:** Implement `build`. Use the class labels from `shared/js/classes-20260617.js` when it's loaded; in Node, fall back to inserting a dash after the grade letter. Take the G11 teal from the existing student CSS variable.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Commit:** `git commit -m "feat(chronicle): view model"`.

### Task 4: Page, slides, and story player

**Files:** Create `chronicle.html`, `shared/css/chronicle.css`, `shared/js/chronicle-story.js`.

**Interfaces:**
- Consumes `EQChronicleData.build` (Task 3).
- Produces `EQChronicleStory.render(container, vm)` → `{ play(), onDone(fn), destroy() }`. The player shows `vm.slides` plus the share-card slide, advances every 4500 ms, goes forward on tap, back on a left-third tap, and pauses while the page is hidden. `onDone` fires after the last slide.
- `chronicle.html?demo=1` (and `?demo=longname`) renders `SAMPLE` without calling the API, for checking the look.

- [ ] **Step 1:** Port the concept's CSS tokens, masthead, rules, giant numerals, blob, sticker, tiles, dots, marquee wall, ruler, score scale, and keyframes into `chronicle.css`. Use the final sizes from the concept page (stat numerals 150–160px; rank 250px, fully visible). Then port each slide's markup into a render function per slide id in `chronicle-story.js`, filled from the view model rather than hard-coded values.
- [ ] **Step 1b:** Port the concept's transitions and idle motion: the `leaving`/`push`/`wipe` classes with the `kind()` rule (paper/dark/blue) from the concept's `show()`, and the idle keyframes (`wave`, `bob`, `float`, `breathe`, marquee rows at 9–17 s). The player owns transitions; slides own entrance and idle motion.
- [ ] **Step 2:** Fit long names: shrink the font until the name fits two lines (stop at 28px). Use the same helper for the cover, card, and records.
- [ ] **Step 3: Check it in the browser.** Add a `.claude/launch.json` static server entry if none exists, open `/chronicle.html?demo=1`, and watch all 8 slides play. Compare against the concept page. Then open `?demo=longname` and confirm the name isn't cropped. Use `resize_window` mobile (375×812) and confirm the slides fill the screen with no sideways scroll. Turn on reduced motion and confirm the slides show at rest.
- [ ] **Step 4:** `node scripts/check-site.mjs`. Expected: no errors.
- [ ] **Step 5: Commit:** `git commit -m "feat(chronicle): story page and player"`.

### Task 5: Live data, records page, Replay

**Files:** Create `shared/js/chronicle-page.js`. Modify `chronicle.html` (records markup) and `shared/css/chronicle.css`.

**Interfaces:**
- Consumes `EQApi.get({ action: 'getChronicle', className, studentNo })`, `EQStudent` (profile), `EQChronicleStory`, and `EQChronicleCard` (Task 6, loaded later; buttons are wired in that task).
- Profile source: `window.EngQuestApp.profile()` (JSON `{ grade, classId, studentNo, name }`) when present (app WebView), otherwise `EQStudent`'s stored profile. No profile → go to the grade's normal login page.
- Flow: if `active` is false → redirect to the grade home (`/grade10/` or `/grade11/`). If `localStorage.eq_chronicle_seen` isn't set → play the story; on done → set it and show the records. If it's set → show the records. **Replay** → play the story again.
- Errors: `{ error }` → "We couldn't find your record. Talk to your teacher." Network failure → "Can't reach EngQuest right now. Try again." with a Try again button.

- [ ] **Step 1:** Build the records layout from the concept ("After the story"): masthead with "Sem 1 · Ended", name plus a smaller accent `#rank`, and buttons Share my Chronicle (website: Download picture) and Replay. Then rule-separated rows: Final, Scores (unit names + ASAT), Present (dots), Level, Words (marquee). The app version is one column; at ≥ 900px the website uses the two-column version with the card on the left.
- [ ] **Step 2:** Wire the flow above. Wrap every localStorage access in try/catch.
- [ ] **Step 3: Check it in the browser** against the lab Worker. Temporarily point `EQApi` at the lab URL locally (don't commit that change). Use a lab student with the start date in the past (Task 2): first visit plays the story, a reload goes straight to records, and Replay works. Set an unknown student number in localStorage and confirm the "couldn't find your record" message appears.
- [ ] **Step 4:** `node scripts/check-site.mjs`.
- [ ] **Step 5: Commit:** `git commit -m "feat(chronicle): records page and replay"`.

### Task 6: Share picture

**Files:** Create `shared/js/chronicle-card.js`. Modify `chronicle-page.js` to wire the buttons.

**Interfaces:**
- Produces `EQChronicleCard.toBlob(vm.card, { accent }) → Promise<Blob>` (PNG, 1080×1920), drawn on a canvas with the share-card layout (masthead, Starring + name, giant accent `#rank` with one line under it ("#11 of 178 in Grade 10"), Final alone on the right, a 4-cell bottom strip: Level, Present, Tasks, Words). Await `document.fonts.ready` before drawing. No shine effect in the image.
- Also produces `EQChronicleCard.stickerBlob(vm.card, { accent }) → Promise<Blob>`: the card at 720×1280, rounded corners, transparent outside.
- Buttons: in the app (`window.EngQuestApp`): if `EngQuestApp.canShareToInstagram()` returns true, show **Share to Instagram Story** → `EngQuestApp.shareToInstagramStory(stickerBase64)`, and **More ways to share** → `EngQuestApp.shareImage(cardBase64)`; otherwise show only **Share** → `shareImage`. On the website: if `navigator.canShare({ files })` → **Share** via `navigator.share`; else **Download picture** as `EngQuest-Chronicle-<Name>.png`.

- [ ] **Step 1:** Implement `toBlob`, reusing the name-fit rule from Task 4 (measure with `ctx.measureText`).
- [ ] **Step 2: Check it.** On `?demo=1`, press Download and open the PNG: it should be 1080×1920, with every number matching the records page and the fonts correct (not a fallback). Repeat with `?demo=longname`.
- [ ] **Step 3:** `node scripts/check-site.mjs`. **Commit:** `git commit -m "feat(chronicle): share picture"`.

### Task 7: Gate on student web pages

**Files:** Create `shared/js/chronicle-gate.js`. Modify every HTML page that loads `shared/js/student.js` (list them with `grep -l "shared/js/student.js" -r grade10 grade11 *.html`).

**Interfaces:** `EQChronicleGate.check()` runs on load. It calls `getChronicleStatus`, caches the result in `sessionStorage` (`eq_chronicle_status`, 10 minutes), and when `active` is true calls `location.replace('/chronicle.html')`. On a network failure it does nothing, so the page works as today.

- [ ] **Step 1:** Add `<script src="…/shared/js/chronicle-gate.js"></script>` after `api.js` on each listed page. Pages that don't load `student.js` (teacher pages) are left untouched.
- [ ] **Step 2: Check it in the browser** with the lab URL. Start date in the past: opening `/grade10/` and `/grade10/scores.html` both land on Chronicle. Run `set-chronicle --off`, clear sessionStorage: both pages work normally. Open a teacher page and confirm it never redirects.
- [ ] **Step 3:** `node scripts/check-site.mjs`. **Commit:** `git commit -m "feat(chronicle): send student pages to Chronicle when it's on"`.

### Task 8: Student app: switch to Chronicle

**Files:** Modify `native-student/app/src/main/java/com/engquest/app/data/Repo.kt` and `ui/App.kt`. Create `ui/chronicle/ChronicleScreen.kt`. Tests: `RepoTest.kt`, `ShellTest.kt`, and new `app/src/test/java/com/engquest/app/ui/ChronicleTest.kt`.

**Interfaces:**
- `suspend fun Repo.chronicleActive(): Boolean`: reads `getChronicleStatus` via `Api.read`. Returns false on any error, so the app works as today.
- `@Composable fun ChronicleScreen(profile: Profile, url: String = CHRONICLE_URL)`, where `const val CHRONICLE_URL = "https://engquestviska.github.io/chronicle.html"`. It's a full-screen WebView with JavaScript and DOM storage on, and a JS interface named `EngQuestApp` (class `ChronicleBridge`):
  - `@JavascriptInterface fun profile(): String`, which returns `profileJson(profile)`.
  - `@JavascriptInterface fun shareImage(base64: String)`, which writes the PNG to `cacheDir/share/chronicle.png` and fires `ACTION_SEND` (`image/png`) through the existing FileProvider (add a `share/` cache path to its xml if missing).
  - `@JavascriptInterface fun canShareToInstagram(): Boolean`: true when `com.instagram.android` is installed (add a `<queries><package android:name="com.instagram.android"/></queries>` entry to the manifest) and `BuildConfig.META_APP_ID` isn't blank.
  - `@JavascriptInterface fun shareToInstagramStory(base64: String)`: writes `cacheDir/share/chronicle-sticker.png`, then starts `Intent("com.instagram.share.ADD_TO_STORY")` with `setType("image/png")`, extra `source_application` = `BuildConfig.META_APP_ID`, `interactive_asset_uri` = the FileProvider uri, `top_background_color` = `#F4F2ED`, `bottom_background_color` = `#141414`, plus `grantUriPermission("com.instagram.android", uri, FLAG_GRANT_READ_URI_PERMISSION)`. If the activity can't be resolved, fall back to `shareImage`.
  - `META_APP_ID` is a `buildConfigField` read from `local.properties` (`meta.appId`, git-ignored like `google-services.json`), and empty when missing.
- `fun instagramStoryIntent(uri: Uri, appId: String): Intent` (pure, so it can be tested).
- `fun profileJson(p: Profile): String` → `{"grade":"10","classId":"XE2","studentNo":"5","name":"…"}`.
- In `Shell`: run `chronicleActive()` once per app start in a `LaunchedEffect`. While it's loading, show the normal shell. When true, render `ChronicleScreen` instead of the tabs. The update banner stays visible above it. Back on Chronicle closes the app with the same double-back rule as Home.

- [ ] **Step 1: Write the failing tests:**
```kotlin
@Test fun chronicleActive_true_when_server_says_active() // FakeServer returns {"success":true,"active":true,"start":"…"}
@Test fun chronicleActive_false_on_error()                 // FakeServer returns 500
@Test fun shell_shows_chronicle_when_active()              // tabs (Home/Scores/Send/Learn/More) not shown; chronicle tag shown
@Test fun shell_shows_tabs_when_not_active()
@Test fun profileJson_has_all_fields()
@Test fun instagramStoryIntent_has_action_type_and_extras() // action, image/png, source_application, both background colors
```
- [ ] **Step 2:** Run `pwsh -File native-student/build.ps1 -Tests RepoTest,ShellTest,ChronicleTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the interfaces above. Use `Modifier.testTag("chronicle")` on the WebView host so `ShellTest` can find it.
- [ ] **Step 4:** Run the full suite: `pwsh -File native-student/build.ps1`. Expected: all tests pass (85 existing + new).
- [ ] **Step 5: Check it on a device or emulator** against lab (temporarily point the app at lab, don't commit that): the story plays; Share to Instagram Story opens Instagram's Story editor with the card as a sticker; More ways to share opens the share sheet; with Instagram uninstalled only Share shows; with the setting off the normal tabs show.
- [ ] **Step 6: Commit:** `git commit -m "feat(student-app): Chronicle mode"`.

### Task 9: Go live (owner approval needed at each step)

- [ ] **Step 0 (before Task 8's device check):** The owner creates a free Meta developer app at developers.facebook.com and gives Claude the App ID. Claude walks them through it; Claude never creates the account. The ID goes into `native-student/local.properties` as `meta.appId=…`.
- [ ] **Step 1:** The owner gives the start date. Ask for approval, then deploy the Worker to production following `Engquest-turso-lab/DEPLOY.md`, and run `node migrate/set-chronicle.mjs --db prod --start <date> --yes`.
- [ ] **Step 2:** On prod, confirm `getChronicleStatus` reads `active: false` before the date. Confirm `getChronicle` for one real student returns sensible numbers (cross-check Final Score and rank against the Scores "Class Rank" tab).
- [ ] **Step 3:** Release the student app: `versionCode = 14`, `versionName = "2.1"`. Build release, copy over the hidden APK name, and add an `app-version.json` entry with a short note ("EngQuest Chronicle: your semester story, ready on <date>."). Run `node scripts/check-site.mjs` and commit. The owner pushes the website repo and the lab repo.
- [ ] **Step 4:** On the start date, open the website and the app as a real student and confirm Chronicle appears.
