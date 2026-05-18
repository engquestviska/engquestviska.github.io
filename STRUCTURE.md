# Project Structure

English Quest is being reorganized into three tracks.

## Root

- `index.html` - landing page for Grade 10, Grade 11, and Teacher.
- `ROADMAP.md` - revamp priorities.
- `STRUCTURE.md` - current folder structure.
- `shared/` - shared CSS and JavaScript for the new structure.

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
- `grade11/lessons.html` - Lesson Materials placeholder.
- `grade11/assignments.html` - Assignments placeholder.
- `grade11/submission.html` - Submission placeholder.
- `grade11/session.html` - Reading placeholder.
- `grade11/students.html` - Student List placeholder.
- `grade11/attendance.html` - Attendance placeholder.
- `grade11/scores.html` - Student Scores placeholder.
- `grade11/tasks.html` - Task Status placeholder.
- `grade11/activeness.html` - Activeness placeholder.
- Grade 11 pages are scaffolded without spreadsheet connections until real Grade 11 data exists.

## Teacher

- `teacher/index.html` - Teacher dashboard and future management hub for both grades.
- `teacher/materials.html` - Teacher materials manager for Grade 10 dynamic lesson files.
- `teacher/scores.html` - Teacher score manager for Grade 10.
- `teacher/tasks.html` - Teacher task status manager for Grade 10.
- `teacher/strikes.html` - Teacher strike management for Grade 10.

## Shared

- `shared/js/api.js` - shared Apps Script API helper and class labels.
- `shared/js/auth.js` - shared teacher session helpers.
- `shared/js/sidebar.js` - shared sidebar for Grade 10 secondary pages.
- `shared/css/` - reserved for shared styles as pages are cleaned up.

## Legacy Root Pages

Some older pages still live at the root while the revamp is in progress. They should either be moved into the correct track or removed later.
- `strikes.html` redirects to `teacher/strikes.html` for old links.
