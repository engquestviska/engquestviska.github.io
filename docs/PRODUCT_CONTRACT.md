# English Quest Product Contract

This branch is a next-year rebuild, not a patch of the current-year student site.
The old spreadsheet-backed pages are reference material only when they still match
the product direction below.

## Confirmed Tracks

- Teacher: private control room for the teacher.
- Grade X: student-facing experience for `XE1`, `XE2`, `XE3`, `XE4`, and `XE5`.
- Grade XI: student-facing experience for `XIF7`, `XIF8`, and `XIF9`.

## Page Ownership

Teacher pages may create, edit, remove, publish, sync, and review class data.
Student pages are read-only unless the action is clearly student-owned, such as
choosing a local profile, opening a file, checking a personal record, or submitting
work through a student form.

## Navigation Meaning

- Homepage: the student's next actions and class hub.
- My Statistics: personal score, task, attendance, submission, XP, rank, and strike overview.
- Profile: student identity and customization.
- Materials: read-only learning files for students.
- Assignments: read-only task instructions for students.
- Submission: student-owned submit/check flow.
- Attendance: read-only personal attendance statistics for students.
- Scores: read-only personal/class score view for students.
- Task Status: read-only task completion view for students.
- Level / XP: replaces old activeness wording on the student side.
- Strikes: read-only warning status for students; maximum 3 strikes.

## Teacher Navigation

The teacher shell must include direct access to both student tracks:

- Grade X Student Site: `../grade10/`
- Grade XI Student Site: `../grade11/`

Teacher class links must use the confirmed class configuration and must not route
every class to the same class by accident.

## Content Rules

- Do not assume Semester 1 is Chapter 4 and Chapter 5.
- Do not introduce previous-year students as next-year data.
- Use mock or empty states while official rosters are unavailable.
- Placeholder rosters must be inactive test data only; they must not become
  student login identities or receive personal records.
- Class IDs and grade ownership must come from the shared class configuration,
  not page-level hardcoded lists.
- Residual current-year logic must be isolated to teacher tools or removed from
  student pages during cleanup.
- Grade XI should mirror Grade X structurally, even when the real data source is
  not ready yet.

## UI Rules

- Desktop and mobile are both required launch targets.
- The sidebar must keep the same visual structure across pages in the same track.
- Active sidebar state must follow the current route.
- Typography must be readable on phones and laptops; avoid tiny dashboard text.
- Homepage and dashboard must not duplicate the same purpose.
