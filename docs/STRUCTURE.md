# Project Structure

English Quest is being reorganized into three tracks.

## Root

- `index.html` - landing page for Grade 10, Grade 11, and Teacher.
- `README.md` - project intro and starting point.
- `docs/` - all planning, contract, and reference docs (see `docs/README.md` for the index).
- `scripts/check-site.mjs` - zero-dependency site checker (links, inline-script parse, unused-CSS report); also runs in CI.
- `activities/` - legacy classroom activity pages, see "Activities" section below.
- `shared/` - shared CSS and JavaScript for the new structure.
- `samples/roster-import.sample.csv` - inactive synthetic roster rows for testing the import contract.

## Docs (`docs/`)

- `docs/README.md` - documentation index (active planning vs. reference).
- `docs/STRUCTURE.md` - this file: current folder structure.
- `docs/ROADMAP.md` - revamp priorities and work order.
- `docs/PRE_ROSTER_PLAN.md` - active development order while official student rosters are unavailable.
- `docs/PRODUCT_CONTRACT.md` - route, ownership, and UI contract for the next-year rebuild.
- `docs/LAUNCH_CHECKLIST.md` - launch readiness checklist before merging `next-year` into `main`.
- `docs/ROSTER_IMPORT_CONTRACT.md` - canonical roster CSV fields, validation rules, and spreadsheet mapping.

## Grade 10

- `grade10/index.html` - Grade 10 student home and profile setup.
- `grade10/dashboard.html` - My Statistics dashboard for the logged-in student.
- `grade10/lessons.html` - Lesson Materials.
- `grade10/assignments.html` - Assignments.
- `grade10/submission.html` - Submission.
- `grade10/session.html` - Reading.
- `grade10/students.html` - Student List.
- `grade10/attendance.html` - Attendance.
- `grade10/scores.html` - Student Scores.
- `grade10/tasks.html` - Task Status.
- `grade10/activeness.html` - Activeness.
- `grade10/strikes.html` - Student-facing strike tracker.

## Grade 11

- `grade11/index.html` - Grade 11 student dashboard.
- `grade11/dashboard.html` - Grade 11 student statistics placeholder.
- `grade11/lessons.html` - Lesson Materials placeholder.
- `grade11/assignments.html` - Assignments placeholder.
- `grade11/submission.html` - Submission placeholder.
- `grade11/session.html` - Reading placeholder.
- `grade11/students.html` - Student List placeholder.
- `grade11/attendance.html` - Attendance placeholder.
- `grade11/scores.html` - Student Scores placeholder.
- `grade11/tasks.html` - Task Status placeholder.
- `grade11/activeness.html` - Activeness placeholder.
- `grade11/strikes.html` - Student-facing strike tracker placeholder.
- Grade 11 pages are scaffolded without spreadsheet connections until real Grade 11 data exists.

## Teacher

- `teacher/index.html` - Teacher dashboard and future management hub for both grades.
- `teacher/classes.html` - Teacher class switchboard for confirmed Grade X and Grade XI classes.
- `teacher/materials.html` - Teacher materials manager for Grade 10 dynamic lesson files.
- `teacher/assignments.html` - Teacher assignment manager for Grade 10 dynamic task files.
- `teacher/submissions.html` - Teacher submission review and Chapter 5 sync manager.
- `teacher/attendance.html` - Teacher attendance review for Grade 10 attendance records.
- `teacher/activeness.html` - Teacher activeness manager for Grade 10 participation points.
- `teacher/announcements.html` - Teacher announcement publisher for the current Grade 10 update.
- `teacher/scores.html` - Teacher score manager for Grade 10.
- `teacher/tasks.html` - Teacher task status manager for Grade 10.
- `teacher/strikes.html` - Teacher strike management for Grade 10.

## Shared

- `shared/js/api.js` - shared server API helper (Cloudflare Worker) and class labels.
- `shared/js/classes-20260617.js` - canonical class configuration for confirmed Grade X and Grade XI classes.
- `shared/js/auth.js` - shared teacher session helpers with backend verification before teacher panels open.
- `shared/js/attendance.js` - shared Grade 10 attendance API helper.
- `shared/js/student.js` - shared student profile storage and display helpers.
- `shared/js/sidebar.js` - canonical shared sidebar for Grade X and Grade XI pages.
- `shared/js/teacher-sidebar.js` - canonical shared sidebar for teacher dashboard and manager pages.
- `shared/css/app-shell.css` - canonical universal app-shell layout for Teacher, Grade X, and Grade XI pages.
- `shared/css/system.css` - canonical shared content/component styles for app pages.
- Dated shared filenames remain only as compatibility shims for cached previews.

## Activities (Legacy Classroom Pages)

Older classroom pages now live in `activities/` instead of loose at the root. Root paths still work via redirect stubs for old bookmarks/links.
- `activities/chapter5_guess_who.html` - legacy classroom game.
- `activities/jeopardyquiz.html` - legacy classroom quiz game.
- `activities/quiz.html` - legacy Grade 10 quiz/activity hub, linked from Grade 10 home (`grade10/index.html`).
- `activities/submission_status.html` - legacy read-only submission status overview.
- `activities/summative.html` - legacy summative topic lookup with teacher reveal controls.
- `activities/sidebar.js` - navigation used only by the pages above; current Grade 10/11 pages use `shared/js/sidebar.js` instead.
- Root `strikes.html`, and `chapter5_guess_who.html`/`jeopardyquiz.html`/`quiz.html`/`submission_status.html`/`summative.html` at root, are redirect stubs pointing at their real location (same pattern for all).
