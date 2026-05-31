# Next Semester Migration Plan

This file is the holding line between the current `next-year` launch work and the real next-semester rollout.

## Current State

- Current Grade 10 pages still use the current-year data flow.
- Grade 11 pages are structural placeholders until real Grade 11 data exists.
- Teacher manager pages still manage the current Grade 10 system.
- `next-semester/` is an isolated preview for the new master Sheet and separate Apps Script API.
- The real Grade 10, Grade 11, and Teacher entry pages link to the preview as a bridge only.
- `main` remains untouched until the user explicitly asks to make the new version live.

## What Is Safe Now

- Use `next-semester/index.html` to test class/name profile login with template students.
- Use `next-semester/dashboard.html` to inspect the future student statistics layout.
- Use `next-semester/teacher.html` to review readiness, profile form rules, XP rules, record counts, and queued controls.
- Keep editing the next-semester master Sheet template.
- Keep deploying `apps-script-next/NextSemesterAPI.js` independently from `apps-script/ScoresAPI.js`.

## What Is Not Safe Yet

- Do not replace current Grade 10 student pages with next-semester API data.
- Do not wire Grade 11 pages to the next-semester API until Grade 11 classes are real.
- Do not merge `next-year` into `main`.
- Do not edit `apps-script/ScoresAPI.js` for next-semester data.
- Do not build write controls against the new master Sheet until the write contract is reviewed.

## Promotion Checklist

Before `next-semester/` becomes the real student system:

- Mark only the real teaching classes as `TRUE` in `Classes.active`.
- Replace placeholder names only for active classes.
- Keep `class_id + student_no` stable for every real student.
- Re-run `getDataReadiness` and resolve every error.
- Review every warning from `getDataReadiness`; active classes should not have placeholder students.
- Confirm the Google Form writes normalized rows into `Profiles`.
- Confirm profile approval behavior with real form responses.
- Confirm XP rules and rank thresholds match the class policy.
- Define teacher write behavior for scores, tasks, attendance, submissions, XP, profile approval, and strikes.
- Verify desktop and mobile previews after real data is added.

## After Promotion

When the user explicitly asks to migrate:

- Wire Grade 10 and Grade 11 student pages to active next-semester classes only.
- Move teacher write controls into `teacher/`.
- Keep student pages read-only except for allowed submission/profile flows.
- Remove or rename preview wording after the preview becomes the real system.
- Keep a final rollback path by committing and pushing each verified batch.
