# Next Semester API

This API is separate from the current-year `apps-script/ScoresAPI.js`.

Current-year data stays on the existing Apps Script deployment. Next-semester work uses a new Apps Script project in `apps-script-next/` so prototype data and template-sheet changes cannot break the current site.

## Source Sheet

- Spreadsheet ID: `1Z5RlW3GfsUR3sbBBP4gY8BrjFxr6JbHFbDOTcxa0rfo`
- Expected tabs:
  - `Settings`
  - `Classes`
  - `Students`
  - `Profiles`
  - `Form responses 1`
  - `Scores`
  - `Tasks`
  - `Attendance`
  - `Submissions`
  - `XP_Log`
  - `XP_Rules`
  - `Ranks`
  - `Strikes`
  - `Profile_Form_Questions`

## Step 3 Data Rules

The master Sheet can stay as a full template, but only active classes should be treated as real classes.

- `Classes.active`
  - `TRUE` means the class is used by the next-semester site.
  - `FALSE` means the class stays in the template but should not appear in normal student flows later.
  - Keep unused classes as `FALSE` until the teaching assignment is final.
- `Students`
  - Keep rows for `Student 1` through `Student 36` while class lists are unknown.
  - Replace placeholder names with real names only when the roster is confirmed.
  - Keep `student_no` stable because profiles, scores, attendance, submissions, XP, and strikes all join by `class_id + student_no`.
  - Do not reuse the same `student_no` twice inside one class.
- Frontend migration rule
  - Preview can use inactive/template classes with `includeInactive=true`.
  - Real Grade 10/Grade 11 pages should use active classes only.
  - Do not migrate the preview into real grade pages until active classes are chosen and readiness warnings are understood.

## Step 4 Profile Form Rules

The student profile form feeds profile data only. It should not be used for scores, attendance, XP, strikes, or submissions.

- Form source-of-truth tab: `Profile_Form_Questions`
- Raw Google Form response tab: `Form responses 1`
- Manual/normalized profile tab: `Profiles`
- Required join fields: `class_id`, `student_no`, `full_name`
- Optional profile fields: `preferred_name`, `photo_url`, `learning_goal`, `english_strength`, `english_weakness`, `favorite_activity`, `quote`
- Approval field: `approved`
  - `Profiles`: blank or `TRUE` is visible to the student dashboard.
  - `Form responses 1`: only `TRUE` is visible to the student dashboard.
  - `FALSE` is hidden from the student dashboard.

See `NEXT_SEMESTER_PROFILE_FORM.md` for the full form checklist.

## Apps Script Project

- Folder: `apps-script-next/`
- Script ID: `1WvWDIGD-FX67lSLjSbC6Gx1pufZ87WN6Kv1o74sKf_dU7aYBQ8p7v6Hq`
- Deployment URL: `https://script.google.com/macros/s/AKfycbzWnQIk7l7nsjaseTrzULYgTm1FbwJJcO4T7eRNGcj-NsMjsTLsE1EQmyD2F6By-l4WEQ/exec`
- Purpose: next-semester read API only until the data model is proven.

The source spreadsheet must be shared with the Google account that owns/runs this Apps Script project. If it is not shared, public endpoint tests will return Google Drive access denied even though the web app deployment exists.

The web app deployment must also be configured for public access. If even `?action=ping` returns a Google Drive "Anda memerlukan akses" page, the deployment itself is private and the code is not running yet.

## Preview Frontend

- `next-semester/index.html` - isolated student profile login for the new master Sheet.
- `next-semester/dashboard.html` - isolated student dashboard for XP/rank, attendance, tasks, submissions, profile answers, and strikes.
- `next-semester/teacher.html` - isolated read-only teacher console for readiness, profile form, XP rules, records, and queued controls.
- `shared/js/next-semester-api.js` - small client for the separate deployment URL.
- `shared/js/next-semester-profile.js` - local profile storage helper shared by the preview pages.
- `shared/css/next-semester.css` - isolated preview styling shared by the preview pages.

These pages are isolated preview surfaces. The current Grade 10, Grade 11, and Teacher data flows do not depend on them.

## Preview Isolation

The preview remains available inside `next-semester/` for reference and backend experiments, but it is no longer promoted from the real Grade X, Grade XI, or Teacher homepages.

Current Grade X, Grade XI, and Teacher pages keep their existing data flows until a future replacement architecture is explicitly approved.

## Step 7 Settlement

The preview stays in the repo as a controlled bridge instead of being deleted or silently merged into the live student pages.

- `NEXT_SEMESTER_MIGRATION.md` is the source-of-truth checklist for moving from preview to real rollout.
- The bridge links are safe because they open isolated preview pages.
- The bridge links are not permission to make Grade 10, Grade 11, or Teacher depend on the next-semester API.
- The next real milestone is data readiness: active classes, real rosters, profile form responses, and reviewed write contracts.

## Verification Log

- 2026-05-30: Public API smoke tests passed for `ping`, `healthCheck`, `getActiveClasses`, `getStudentsByClass`, and `getStudentDashboard`.
- 2026-05-30: Preview login/dashboard passed desktop and mobile headless checks with `XE1` / `Student 1`; profile save, immediate refresh, and dashboard rendering work.
- 2026-05-31: `getDataReadiness` deployed at version 2 and verified. Current template has 22 classes, 0 active classes, 792 placeholder students, and no structural errors.
- 2026-05-31: `getProfileFormSpec` deployed at version 4 and verified. Current form spec has 10 questions, 3 required fields, and 22 template class choices with `includeInactive=true`.
- 2026-05-31: `getTeacherControlSummary` deployed at version 5 and verified. Teacher console passed desktop/mobile checks with 22 classes, 10 profile questions, 6 XP rules, and 8 queued control areas.
- 2026-05-31: Step 6 bridge added from Grade 10, Grade 11, and Teacher entry pages to the isolated next-semester preview surfaces.
- 2026-05-31: Step 7 settled the preview boundary and migration checklist without replacing current-year data flows.
- 2026-05-31: Google Form responses were connected through `Form responses 1`; approved form rows can now feed student dashboards without making the raw form tab the frontend contract. Deployed at version 6 and verified with `XE1` / `Student 1`.

## Read Endpoints

All endpoints use `GET` with an `action` parameter.

- `ping`
- `healthCheck`
- `getDataReadiness`
- `getProfileFormSpec`
- `getApprovedProfileResponses`
- `getTeacherControlSummary`
- `getSettings`
- `getActiveClasses`
- `getStudentsByClass&classId=XE1`
- `getStudentProfile&classId=XE1&studentNo=1`
- `getXpSummary&classId=XE1&studentNo=1`
- `getRankings&classId=XE1`
- `getStudentDashboard&classId=XE1&studentNo=1`

`getActiveClasses` and `getStudentsByClass` support `includeInactive=true` for template/testing work.

`getDataReadiness` checks active classes, roster placeholders, duplicate student slots, unknown class references, capacity mismatches, and whether the Sheet is ready for frontend migration.

`getProfileFormSpec` returns the profile question list, required fields, target profile headers, active/template class choices, and approval behavior.

`getApprovedProfileResponses` returns normalized rows from `Form responses 1` where `Approved` is `TRUE`.

Pass `includeInactive=true` while the Sheet is still a template. Without it, the class list only includes rows where `Classes.active` is `TRUE`.

`getTeacherControlSummary` returns the read-only teacher console summary: readiness, profile form status, XP/rank setup, current record counts, and the queued write-control areas.

## Important Boundary

Do not add next-semester endpoints to `apps-script/ScoresAPI.js`.

Do not make Grade 10, Grade 11, or teacher pages depend on the next-semester API until the user explicitly asks to start the real migration.

Links from real entry pages into `next-semester/` are allowed as preview bridge links only.
