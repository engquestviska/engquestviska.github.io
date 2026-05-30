# English Quest Launch Checklist

Use this checklist before merging `next-year` into `main`.

## Current `next-year` Status

- Grade 10 student pages are the launch baseline: student-facing, read-only where they show class data, and separated from teacher management actions.
- Teacher management pages live under `teacher/` and use the saved teacher session for write actions.
- Chapter 5 task-status sync is teacher-authenticated; students can only check received files.
- Grade 11 pages mirror the Grade 10 structure as placeholders and clear any saved Grade 10 student profile when opened.
- `main` is still untouched; merge only when the user explicitly decides the new version should become live.

## Branch Safety

- Stay on `next-year` while preparing the new version.
- Keep `main` untouched until the planned switchover.
- Commit and push every verified cleanup batch.
- Preview the pushed branch before merge.
- If RawGitHack branch preview looks stale, test the exact commit URL.

## Student Track Checks

- [x] Root landing opens Grade 10, Grade 11, and Teacher routes.
- [x] Grade 10 home opens all student modules.
- [x] Grade 10 student pages do not expose teacher editing controls.
- [x] Grade 10 profile login, change name, and logout refresh immediately after confirmation.
- [x] Grade 10 My Statistics loads only after a student profile is selected.
- [x] Grade 10 strikes show student warning status only; strike edits stay in `teacher/`.
- [x] Grade 11 pages remain placeholders until real Grade 11 data exists.
- [x] Grade 11 pages do not read Grade 10 spreadsheets or APIs.

## Teacher Track Checks

- [ ] Teacher dashboard login works in the deployed preview.
- [x] Teacher dashboard links open each Grade 10 manager.
- [x] Teacher manager pages stay inside `teacher/`.
- [x] Teacher-only actions are not linked from student pages.
- [x] Materials and assignments can be managed from teacher pages only.
- [x] Scores, task status, strikes, submissions, attendance, activeness, and announcements are checked from the teacher route.

## Legacy Route Checks

- [x] `quiz.html` remains student-safe.
- [x] `jeopardyquiz.html` routes back to the activity hub.
- [x] `chapter5_guess_who.html` stays teacher-gated.
- [x] `gallery.html`, `summative.html`, and `submission_status.html` do not expose unsafe teacher workflows to students.
- [x] Root `strikes.html` redirects to `grade10/strikes.html`.

## Data And Backend Checks

- [ ] Apps Script deployment URLs are current.
- [ ] Google Apps Script permissions allow the public student reads needed by Grade 10.
- [ ] Teacher write actions still work after any Apps Script deployment change.
- [x] Chapter 5 single-student sync requires teacher credentials.
- [ ] Grade 10 spreadsheet tabs match the class names used by the frontend.
- [x] Grade 11 remains disconnected until its roster, sheets, and API contract exist.

## Device Checks

- [ ] Test on desktop width.
- [ ] Test on phone width.
- [ ] Confirm navigation, cards, buttons, and long student names do not overlap.
- [ ] Confirm tables or lists are readable on mobile.
- [ ] Confirm student-facing buttons have clear tap targets.

## Final Merge Checks

- [x] Run local href/src audit.
- [x] Run JavaScript syntax checks.
- [x] Run local HTTP smoke check for root, Grade 10, Grade 11, Teacher, and shared CSS.
- [x] Push the final `next-year` commit after each completed batch.
- [ ] Open the exact pushed commit preview if branch preview is stale.
- [ ] Merge to `main` only when the user decides the new version should become live.
- [ ] After merge, check the real GitHub Pages live URL again.
