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
  - `Scores`
  - `Tasks`
  - `Attendance`
  - `Submissions`
  - `XP_Log`
  - `XP_Rules`
  - `Ranks`
  - `Strikes`

## Apps Script Project

- Folder: `apps-script-next/`
- Script ID: `1WvWDIGD-FX67lSLjSbC6Gx1pufZ87WN6Kv1o74sKf_dU7aYBQ8p7v6Hq`
- Initial deployment URL: `https://script.google.com/macros/s/AKfycbyNLYf1615P2rAZsW16sGxPO3jCGGK6TpOwpBmmwNNnCRI8hmwR0eN6d2DVAqj4EsELbg/exec`
- Purpose: next-semester read API only until the data model is proven.

The source spreadsheet must be shared with the Google account that owns/runs this Apps Script project. If it is not shared, public endpoint tests will return Google Drive access denied even though the web app deployment exists.

The web app deployment must also be configured for public access. If even `?action=ping` returns a Google Drive "Anda memerlukan akses" page, the deployment itself is private and the code is not running yet.

## Preview Frontend

- `next-semester/index.html` - isolated student profile login for the new master Sheet.
- `next-semester/dashboard.html` - isolated student dashboard for XP/rank, attendance, tasks, submissions, profile answers, and strikes.
- `shared/js/next-semester-api.js` - small client for the separate deployment URL.

These pages are not linked into the current Grade 10 or Teacher flow yet.

## Read Endpoints

All endpoints use `GET` with an `action` parameter.

- `ping`
- `healthCheck`
- `getSettings`
- `getActiveClasses`
- `getStudentsByClass&classId=XE1`
- `getStudentProfile&classId=XE1&studentNo=1`
- `getXpSummary&classId=XE1&studentNo=1`
- `getRankings&classId=XE1`
- `getStudentDashboard&classId=XE1&studentNo=1`

`getActiveClasses` and `getStudentsByClass` support `includeInactive=true` for template/testing work.

## Important Boundary

Do not add next-semester endpoints to `apps-script/ScoresAPI.js`.

Do not point Grade 10 or teacher pages to the next-semester API until the new API has its own deployed URL and the user explicitly asks to start wiring the next-semester frontend.
