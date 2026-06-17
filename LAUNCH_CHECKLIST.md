# English Quest Launch Checklist

Use this checklist before merging `next-year` into `main`.

## Current `next-year` Status

- Grade 10 student pages are the launch baseline: student-facing, read-only where they show class data, and separated from teacher management actions.
- Teacher management pages live under `teacher/` and use the saved teacher session for write actions.
- Chapter 5 task-status sync is teacher-authenticated; students can only check received files.
- Grade 11 pages mirror the Grade 10 structure as placeholders and preserve student profile state unless the student explicitly logs out or changes name.
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
- [x] Confirmed class configuration is centralized for `XE1-XE5` and `XIF7-XIF9`.

## Teacher Track Checks

- [x] Teacher dashboard login works against the deployed Scores API.
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

- [x] Apps Script deployment URLs are current.
- [x] Google Apps Script permissions allow the public student reads needed by Grade 10.
- [x] Teacher write path still works after Apps Script deployment change.
- [x] Chapter 5 single-student sync requires teacher credentials.
- [x] Grade 10 spreadsheet tabs match the class names used by the frontend.
- [x] Grade 11 remains disconnected until its roster, sheets, and API contract exist.

## Next Semester Preview Checks

- [x] `apps-script-next/` stays separate from the current-year Apps Script.
- [x] `next-semester/` preview pages are reachable from Grade 10, Grade 11, and Teacher entry pages.
- [x] Preview bridge links do not replace current Grade 10, Grade 11, or Teacher data flows.
- [x] Data readiness, profile form spec, and teacher control summary endpoints are documented.
- [x] `NEXT_SEMESTER_MIGRATION.md` records the promotion checklist before the preview becomes the real system.
- [ ] Activate only real classes in the master Sheet.
- [ ] Replace placeholder students for active classes.
- [ ] Re-run readiness and resolve active-class warnings before real migration.
- [ ] Review write contracts before building next-semester teacher controls.

## Device Checks

- [x] Test on desktop width.
- [x] Test on phone width.
- [x] Confirm navigation, cards, buttons, and long student names do not overlap.
- [x] Confirm tables or lists are readable on mobile.
- [x] Confirm student-facing buttons have clear tap targets.

## Final Merge Checks

- [x] Run local href/src audit.
- [x] Run JavaScript syntax checks.
- [x] Run local HTTP smoke check for root, Grade 10, Grade 11, Teacher, and shared CSS.
- [x] Push the final `next-year` commit after each completed batch.
- [x] Open the exact pushed commit preview if branch preview is stale.
- [ ] Merge to `main` only when the user decides the new version should become live.
- [ ] After merge, check the real GitHub Pages live URL again.
