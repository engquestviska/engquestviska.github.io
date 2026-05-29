# English Quest Launch Checklist

Use this checklist before merging `next-year` into `main`.

## Branch Safety

- Stay on `next-year` while preparing the new version.
- Keep `main` untouched until the planned switchover.
- Commit and push every verified cleanup batch.
- Preview the pushed branch before merge.
- If RawGitHack branch preview looks stale, test the exact commit URL.

## Student Track Checks

- Root landing opens Grade 10, Grade 11, and Teacher routes.
- Grade 10 home opens all student modules.
- Grade 10 student pages do not expose teacher editing controls.
- Grade 10 profile login, change name, and logout refresh immediately after confirmation.
- Grade 10 My Statistics loads only after a student profile is selected.
- Grade 10 strikes show student warning status only; strike edits stay in `teacher/`.
- Grade 11 pages remain placeholders until real Grade 11 data exists.
- Grade 11 pages do not read Grade 10 spreadsheets or APIs.

## Teacher Track Checks

- Teacher dashboard login works.
- Teacher dashboard links open each Grade 10 manager.
- Teacher manager pages stay inside `teacher/`.
- Teacher-only actions are not linked from student pages.
- Materials and assignments can be managed from teacher pages only.
- Scores, task status, strikes, submissions, attendance, activeness, and announcements are checked from the teacher route.

## Legacy Route Checks

- `quiz.html` remains student-safe.
- `jeopardyquiz.html` routes back to the activity hub.
- `chapter5_guess_who.html` stays teacher-gated.
- `gallery.html`, `summative.html`, and `submission_status.html` do not expose unsafe teacher workflows to students.
- Root `strikes.html` redirects to `grade10/strikes.html`.

## Data And Backend Checks

- Apps Script deployment URLs are current.
- Google Apps Script permissions allow the public student reads needed by Grade 10.
- Teacher write actions still work after any Apps Script deployment change.
- Grade 10 spreadsheet tabs match the class names used by the frontend.
- Grade 11 remains disconnected until its roster, sheets, and API contract exist.

## Device Checks

- Test on desktop width.
- Test on phone width.
- Confirm navigation, cards, buttons, and long student names do not overlap.
- Confirm tables or lists are readable on mobile.
- Confirm student-facing buttons have clear tap targets.

## Final Merge Checks

- Run local href/src audit.
- Run JavaScript syntax checks.
- Run local HTTP smoke check for root, Grade 10, Grade 11, Teacher, and shared CSS.
- Push the final `next-year` commit.
- Open the exact pushed commit preview if branch preview is stale.
- Merge to `main` only when the user decides the new version should become live.
- After merge, check the real GitHub Pages live URL again.
