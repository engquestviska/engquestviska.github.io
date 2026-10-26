# EngQuest Teacher App: Design

Date: 2026-10-26
Status: design approved in conversation (mockups accepted); written up for planning.

## Intent

The owner (the only teacher) uses the EngQuest Teacher Android app on their phone, mostly **while standing in the classroom**. Today that app (`D:\android-dev\EngQuestTeacher`, v1.0) only mirrors the teacher website. The pages are desktop-shaped, so on a phone you get two stacked headers, squeezed rows, cut-off buttons, small photos, and a 35-student attendance list that is 7 screens long with Save only at the top.

The owner wants an app that **looks good and works like a real app**, with screens designed for a phone, instead of a shrunk website. The teacher app is the **pilot**. If it turns out great, the student app gets the same treatment later as a separate project.

## Goals (in priority order)

1. **Mark attendance** fast: one tap per student who isn't present, Save always reachable.
2. **Review submissions**: big photos, score + approve in one action, auto-advance to the next student.
3. **Give Level/XP**: pick a reason once, tap students to award; instant, with undo.
4. **Give scores**: chapter + column picker, number-pad entry down the list, autosave.

Plus an app frame shared by all screens: a **class picker at the top** that every tab uses, a **bottom tab bar** (Attendance · Submissions · XP · Scores · More), the indigo teacher accent (#4338CA, matching the current teacher APK), and big thumb-reachable buttons.

## Non-goals

- Not rebuilding materials, announcements, flashcards, vocabulary, strikes, settings, classes or task status. "More" links out to the existing teacher website pages for those.
- Not changing the existing teacher website. It stays as-is for the computer.
- Not touching the student app or student pages (that comes later, as its own project).
- No backend (Cloudflare Worker / Turso) changes unless an action the screens need turns out to be missing. Stop and flag it if so.

## Anti-goals (failure even if it "works")

- Feels like a website inside an app: browser-style headers, desktop tables, horizontal scrolling, tiny tap targets.
- Saving something wrong in **real student data**. This runs against prod (engquest-prod via the Worker). A mis-tap must be easy to undo, and nothing may write on its own without a clear user action. Attendance must not save "all present" just because the screen opened.
- Students being able to reach or use the teacher app.

## The four screens (as shown in the accepted mockups)

The mockup file is `.superpowers/brainstorm/422-1793010945/content/teacher-app-screens.html` (local, gitignored). It is the visual reference for layout and feel.

**Attendance**
- Header: "Attendance", class pill, date + meeting info. The date defaults to today, and there must be a way to change it (marking a past day happens).
- Summary chips: Present / Absent / Sick / Permit counts.
- Compact row per student: number, name (single line, ellipsis), 4-button segmented control **P / A / S / I**. New meetings start all-Present. If the date already has saved attendance, load it instead.
- Sticky save bar above the tab bar showing unsaved state and a **Save** button. Leaving with unsaved changes warns.
- "Remove this meeting" exists in the website (`removeAttendanceMeeting`). Keep it reachable but tucked away (e.g. an overflow menu with confirm).

**Submissions**
- Header shows how many are waiting in this class and the position ("3 of 12").
- One student at a time: name, number, when sent, task code chip (e.g. C2T1).
- Large photo viewer, swipe between that student's photos, tap to view full-screen with zoom.
- Score box for the task's chapter column, when the task code maps to one (same mapping as `teacher/submissions.html` `taskToChapterCol`). Empty means approve without score.
- **Approve & next** (approve + optional `saveChapterScore` + `saveTaskStatus`, the same 3-in-1 as the website) and **Send back** (reject). Then auto-advance.
- A strip/queue of the remaining students with their pending counts.
- Pending vs All toggle like the website.

**Give XP**
- Reason chips: Answering +5, Presenting +20, Helping Hand +20, Doing Task On Time +20, Quiz +10 (the weights in the project memory / leaderboard view; take the real list from `teacher/activeness.html`). Vocabulary Box is earned by students, so it isn't a teacher-awarded reason unless the website lets the teacher award it.
- 3-column grid of student tiles: avatar initials, first name, current XP and level.
- Tap = `incrementActiveness` for the selected reason, with a "+N" pop and a toast with **Undo** (undo = decrement by the same amount).

**Scores**
- Chapter chips (the same chapters/modes as `teacher/scores.html` at build time, including any per-grade differences, plus Final) and a column segmented control (Task 1–3, Formative, Summative, or the columns the API returns).
- Row per student: number, name, numeric input, running average.
- Number pad entry, with **Next ↓** moving to the next student. Each score saves when the field is left (`saveChapterScore` / `saveFinalScore`), with a visible "Saved" state and a clear error on failure. Validate 0–100. Low scores (under 75, the "Safe" line) are shown in red.

**More**
- Links to the teacher website pages not rebuilt here, opened inside the app.
- Logout.

## How it's built (decided)

- **New phone-first web screens**, hosted on the same GitHub Pages site in a new folder (`teacher-app/`), and loaded by the existing Android teacher app shell. Updating a screen later needs no reinstall.
- **Same data**: talk to the existing backend through the existing `shared/js/api.js` (`EQApi`, Worker `/exec`) and the same actions the teacher website pages use:
  - Attendance: `getClassData`, `saveAttendance`, `removeAttendanceMeeting`
  - Submissions: `getTaskSubmissions`, `getSubmissionPhoto`, `reviewTaskSubmission`, `saveTaskStatus`, `saveChapterScore`
  - XP: `getAllActiveness`, `incrementActiveness`
  - Scores: `getChapterScores`, `getFinalScores`, `saveChapterScore`, `saveFinalScore`
  - Login: `checkLogin`, using the same plain-password auth the teacher website uses now (see the auth notes in the project memory: the Worker only accepts the plain password).
- Class list from `EQClasses` (shared), so all 8 classes (Grade 10 + 11) work.
- **Android shell** (`D:\android-dev\EngQuestTeacher`): the start URL becomes the new `teacher-app/`. Remove the native top bar, because the web screens now draw their own header. Colour the status bar indigo. Keep back-button → web history, the file chooser and the external-download handling. Bump the version (v2.0, versionCode 2), same keystore.
- Match repo conventions: plain HTML/CSS/vanilla JS, no build step, like the rest of the site. Run `node scripts/check-site.mjs` before pushing. Push after every commit to `main` and keep `next-year` fast-forwarded, per repo guardrails.

## Pilot, then release

1. **Pilot**: build the screens and a v2.0 teacher APK. The owner installs it and uses it in class. Iterate on what bothers them.
2. **Release**: after the owner OKs the pilot: deliver the final APK. (The student-facing announcement is deferred; see Owner's answers.)

## Settled from the code

- XP undo = `incrementActiveness` with the negative delta (the website already does −1 undos).
- `teacher/activeness.html` lets the teacher award **Vocabulary Box**, so it stays in the XP reason chips.
- `saveAttendance` sends the whole class for one date, and its `meetingNumber` feeds the header's meeting info.
- Score validation (0–100) and the save calls follow `teacher/scores.html`. The returned `calc` value drives the "Saved" state and average.
- v2.0 keeps applicationId `com.engquest.teacher` and the same keystore, so it installs over v1.0 as an upgrade.

## Builder's calls (the owner can overrule)

- **Pilot hosting:** `teacher-app/` is pushed to `main` during the pilot as an **unlinked folder**. Nothing on the site links to it, and every action needs the teacher login. The pilot APK needs a live URL, and this avoids a second build at release. The existing teacher website and student pages are untouched. "Release" then means: final APK + announcement.
- **Native shell, beyond the spec:** the last-page restore checks `teacher-app/` (and still allows `teacher/`). On old website pages opened from "More", the shell shows a small floating **"← App"** button that returns to `teacher-app/`, because the native top bar is gone.

## Owner's answers (2026-10-26)

- **Stay logged in:** yes. The app remembers the login on the owner's phone, so there's no retyping after Android closes the app. Do this with an app-only auth helper inside `teacher-app/` (persistent storage in the app's WebView). **Don't** change the shared `shared/js/auth.js`, so the teacher website keeps its current session-only behaviour. If the backend rejects the stored password (changed elsewhere), clear it and show the login screen. Logout in "More" clears it.
- **Update announcement:** it's meant for students, and **not a priority now**. Out of scope for this project, so no announcement work in the pilot or release.

## Left to the builder

- Exact component styling within the mockups' look. The Clean + Friendly direction, indigo teacher accent.
- Routing inside `teacher-app/` (hash routes per tab is the expected default so the Android back button works).
- Loading, empty and error states (offline, session expired → back to login, API errors). These must exist and be friendly.
- Caching of class rosters between tabs.
