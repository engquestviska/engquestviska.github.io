# English Quest - Next Year Draft

This branch is a safe draft space for preparing the website for the next school year.

- Live website branch: `main`
- Draft branch: `next-year`
- Changes here do not affect the live website until they are merged into `main`.

## What This Site Contains

English Quest is a static GitHub Pages website for class activities, scores, attendance, task tracking, submissions, lessons, and gallery pages.

Important files and folders:

- `index.html` - main landing/dashboard page.
- `sidebar.js` - shared sidebar/navigation script used by pages on this branch.
- `scores.html`, `attendance.html`, `students.html`, `tasks.html`, `strikes.html` - teacher/student tracking pages.
- `submission.html`, `submission_status.html` - submission workflow pages.
- `lessons.html`, `assignments.html`, `session.html`, `quiz.html`, `jeopardyquiz.html`, `chapter5_guess_who.html` - learning/activity pages.
- `gallery.html` - class photo gallery.
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
2. Preview pages locally by opening `index.html` in a browser.
3. Commit related changes with clear messages.
4. Push `next-year` to GitHub.
5. Test the draft preview link above.
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
- Support Grade 10 and possibly Grade 11 separately.
- Keep student lists/data separate by school year and class.
- Clean up the large `index.html` file over time.
- Improve mobile layout, performance, and teacher workflow.

## Before Going Live

- Confirm all class names and student lists are for the correct school year.
- Check teacher login and protected actions.
- Test the main pages on phone and desktop.
- Confirm Google Apps Script deployments are current.
- Make sure no draft-only text or test data is visible on the live site.
