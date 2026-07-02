# English Quest - Next Year Draft

This branch is a safe draft space for preparing the website for the next school year.

- Live website branch: `main`
- Draft branch: `next-year`
- Changes here do not affect the live website until they are merged into `main`.

## What This Site Contains

English Quest is a static GitHub Pages website being rebuilt into three clear tracks:

- `teacher/` - teacher-only dashboard and Grade 10 management tools.
- `grade10/` - Grade 10 student-facing pages for class work and personal data.
- `grade11/` - Grade 11 student-facing placeholders until real Grade 11 data exists.

Important files and folders:

- `index.html` - landing page for choosing Grade 10, Grade 11, or Teacher.
- `docs/` - all planning, contract, and reference docs; see `docs/README.md` for the index (roadmap, product rules, launch checklist, roster/next-semester specs, folder map).
- `grade10/index.html` - Grade 10 student home and profile setup.
- `grade10/dashboard.html` - Grade 10 personal statistics dashboard.
- `grade10/strikes.html` - Grade 10 student-facing strike tracker.
- `teacher/index.html` - teacher control room.
- `teacher/materials.html`, `teacher/assignments.html`, `teacher/submissions.html`, `teacher/attendance.html`, `teacher/activeness.html`, `teacher/announcements.html`, `teacher/scores.html`, `teacher/tasks.html`, `teacher/strikes.html` - Grade 10 teacher managers.
- `grade11/` - matching Grade 11 placeholder structure without spreadsheet connections yet.
- `shared/css/system.css` - shared design system styles.
- `shared/js/api.js`, `shared/js/auth.js`, `shared/js/sidebar.js`, `shared/js/teacher-sidebar.js` - shared API, auth, and navigation helpers.
- `activities/quiz.html`, `activities/jeopardyquiz.html`, `activities/chapter5_guess_who.html`, `activities/submission_status.html`, `activities/summative.html`, `activities/gallery.html` - legacy classroom pages still used during the transition; old root URLs redirect here.
- `apps-script/` - Google Apps Script backend source for the main data API.
- `chapter5-submission/` - Google Apps Script source for the Chapter 5 submission flow.
- `images/` - local image assets used by activities.

## Preview

GitHub Pages normally serves the live `main` branch, so this branch may not appear at the normal website URL.

Quick draft preview:

https://raw.githack.com/engquestviska/engquestviska.github.io/next-year/index.html

This preview is useful for checking layout and basic behavior, but the final live website should still be tested after merging to `main`.

## Editing Workflow

1. Work on the `next-year` branch.
2. Preview pages locally with a static server or by opening `index.html` in a browser.
3. Commit related changes with clear messages.
4. Push `next-year` to GitHub.
5. Test the draft preview link above. If RawGitHack branch cache lags, test the exact commit URL.
6. Merge into `main` only when the next-year version is ready to become live.

Useful commands:

```bash
git status
git switch next-year
git add .
git commit -m "Describe the change"
git push
```

## Deployment Notes

The live website is served from GitHub Pages on the `main` branch. The `next-year` branch is for preparation only. After merging `next-year` into `main`, check the live site again because GitHub Pages may behave differently from the RawGithack preview.

Some pages depend on Google Apps Script, Google Sheets, Google Drive, and YouTube links. If a page loads but data does not appear, check that the Apps Script deployment URL and permissions are still valid.

## Next-Year Goals

- Prepare structure for a new school year.
- Support Grade 10 and Grade 11 as separate student tracks.
- Keep student lists/data separate by school year and class.
- Keep teacher editing tools inside `teacher/`.
- Improve mobile layout, performance, and teacher workflow.

## Before Going Live

- Work through `docs/LAUNCH_CHECKLIST.md`.
- Confirm all class names and student lists are for the correct school year.
- Check teacher login and protected actions.
- Test the main pages on phone and desktop.
- Confirm Google Apps Script deployments are current.
- Make sure no draft-only text or test data is visible on the live site.
