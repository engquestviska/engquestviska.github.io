# Project Structure

English Quest is being reorganized into three tracks.

## Root

- `index.html` - landing page for Grade 10, Grade 11, and Teacher.
- `LAUNCH_CHECKLIST.md` - launch readiness checklist before merging `next-year` into `main`.
- `PRE_ROSTER_PLAN.md` - active development order while official student rosters are unavailable.
- `ROSTER_IMPORT_CONTRACT.md` - canonical roster CSV fields, validation rules, and spreadsheet mapping.
- `NEXT_SEMESTER_API.md` - next-semester spreadsheet/API contract and boundary notes.
- `NEXT_SEMESTER_MIGRATION.md` - promotion checklist for moving the isolated preview into the real grade pages later.
- `NEXT_SEMESTER_PROFILE_FORM.md` - next-semester student profile Google Form setup contract.
- `ROADMAP.md` - revamp priorities.
- `STRUCTURE.md` - current folder structure.
- `apps-script/` - current-year Apps Script source.
- `apps-script-next/` - separate next-semester Apps Script source for the uploaded master spreadsheet.
- `next-semester/` - isolated preview pages for the next-semester profile login, student dashboard, and read-only teacher console. It is a bridge, not the real Grade 10 or Grade 11 data flow yet.
- `shared/` - shared CSS and JavaScript for the new structure.
- `samples/roster-import.sample.csv` - inactive synthetic roster rows for testing the import contract.

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

- `shared/js/api.js` - shared Apps Script API helper and class labels.
- `shared/js/classes-20260617.js` - canonical class configuration for confirmed Grade X and Grade XI classes.
- `shared/js/auth.js` - shared teacher session helpers with backend verification before teacher panels open.
- `shared/js/attendance.js` - shared Grade 10 attendance API helper.
- `shared/js/next-semester-api.js` - isolated client for the next-semester Apps Script deployment.
- `shared/js/next-semester-profile.js` - next-semester preview profile storage helper.
- `shared/js/sidebar.js` - shared sidebar for Grade 10 secondary pages.
- `shared/js/teacher-sidebar.js` - shared sidebar for teacher dashboard and manager pages.
- `shared/css/system.css` - shared legacy/content styles used by existing pages.
- `shared/css/app-shell-20260611.css` - active universal app-shell layout for Teacher, Grade X, and Grade XI pages.
- `shared/css/system-20260611.css` - active shared content/component styles for app pages.
- `shared/js/sidebar-20260611.js` and `shared/js/teacher-sidebar-20260611.js` - active student and teacher shell controllers.
- Older shared stylesheet filenames remain as compatibility assets for cached previews.
- `shared/css/next-semester.css` - isolated preview styles for the next-semester login and dashboard.

## Legacy Root Pages

Some older pages still live at the root while the revamp is in progress. They should either be moved into the correct track or removed later.
- `chapter5_guess_who.html` - legacy classroom game.
- `gallery.html` - legacy gallery with student upload and teacher approval controls.
- `jeopardyquiz.html` - legacy classroom quiz game.
- `quiz.html` - legacy Grade 10 quiz/activity hub, linked from the Grade 10 home while the games are still at the root.
- `strikes.html` redirects to `grade10/strikes.html` for old student links.
- `submission_status.html` - legacy read-only submission status overview.
- `summative.html` - legacy summative topic lookup with teacher reveal controls.
