# English Quest Revamp Roadmap

This roadmap keeps the next-year rebuild focused. The goal is one English Quest system with three clean tracks:

- Teacher Dashboard: manage both grades.
- Grade 10: student-facing Grade 10 content and data.
- Grade 11: student-facing Grade 11 content and data.

## Urgent

1. Define the new file/folder structure.
   - Decide where Grade 10 pages live.
   - Decide where Grade 11 pages live.
   - Decide where teacher-only pages live.
   - Decide where shared CSS, JS, and assets live.

2. Build a shared design system.
   - Shared layout shell.
   - Shared buttons, cards, tabs, forms, and status styles.
   - Shared mobile rules.
   - Track colors for Grade 10, Grade 11, and Teacher.

3. Make navigation consistent.
   - Landing page routes to Grade 10, Grade 11, and Teacher.
   - Grade 10 pages stay inside Grade 10.
   - Grade 11 pages stay inside Grade 11.
   - Teacher pages stay inside Teacher tools.
   - Every page should have a clear way back to its track dashboard.

4. Separate teacher tools from student pages.
   - Student pages should be for viewing and submitting.
   - Teacher dashboard should handle editing, posting, scoring, activeness, and management.
   - Existing Grade 10 pages may still contain teacher controls, so those need to move over time.

5. Revamp Grade 10 core pages first.
   - Lesson Materials.
   - Assignments.
   - Submission.
   - Reading.
   - Student List.
   - Attendance.
   - Student Scores.
   - Task Status.
   - Activeness.

## Important Next

1. Technical consolidation before more feature pages.
   - Move repeated API helpers into `shared/js/api.js`.
   - Move teacher login/session helpers into `shared/js/auth.js`.
   - Refactor newest teacher tools first: `teacher/tasks.html` and `teacher/scores.html`.
   - Then extract repeated teacher layout styles into shared CSS.
   - Initial API/auth and teacher tool shell extraction is in place; continue this pattern for new manager pages.
   - Do this before building more manager pages, otherwise the site will become harder to maintain.

2. Create Grade 11 page equivalents.
   - Use the same structure as Grade 10 first.
   - Keep data/content as placeholders until real Grade 11 data exists.
   - Avoid mixing Grade 10 data into Grade 11.

3. Improve Teacher Dashboard.
   - Manage Grade 10 and Grade 11 from one place.
   - Add sections for content, scores, attendance, tasks, submissions, activeness, and announcements.
   - Make it clear which grade is being edited.
   - Teacher dashboard now separates live Grade 10 managers, queued tools, and disabled Grade 11 placeholders.
   - Materials manager is now teacher-side for dynamic Grade 10 lesson files.
   - Assignment manager is now teacher-side for dynamic Grade 10 task files.

4. Plan data separation.
   - Decide how Google Sheets and Apps Script will separate Grade 10 and Grade 11.
   - Keep the current Apps Script as Grade 10 for now.
   - Add Grade 11 only when real data exists.

5. Improve mobile UX.
   - Larger tap targets.
   - Simpler navigation.
   - Cleaner cards.
   - Less crowded pages.
   - Readable tables or mobile-friendly alternatives.

## Quality Of Life

1. Student personalization.
   - Saved profile.
   - Saved grade, class, and name.
   - Quick access to personal scores and tasks.
   - Continue where I left off.

2. Dashboard summaries.
   - Recent assignments.
   - Missing tasks.
   - Latest materials.
   - Score/task status preview.

3. Better teacher workflow.
   - Quick grade switcher.
   - Bulk updates.
   - Clear publish/draft states.
   - Better success/error messages.

4. Cleaner code.
   - Move repeated CSS to shared files.
   - Move repeated JS to shared files.
   - Use folders if the page count keeps growing.
   - Keep files readable for future editing.

## Suggested Work Order

1. Create the new folder structure.
2. Add shared CSS and shared navigation.
3. Convert the Grade 10 dashboard to use shared styles.
4. Convert Grade 10 core pages one by one.
5. Build matching Grade 11 placeholder pages.
6. Expand Teacher Dashboard into real management tools.
7. Update Apps Script and Google Sheets when Grade 11 data is ready.
