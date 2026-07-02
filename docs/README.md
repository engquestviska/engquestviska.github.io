# English Quest Documentation

All planning, contract, and reference docs live here. The repo `README.md` (one level up) is the starting point for the project itself.

## Start here
- **[STRUCTURE.md](STRUCTURE.md)** — map of every folder and file in the repo.

## Active planning (how the rebuild is being run)
These describe current direction and the order of work. Read these first if you're picking up the project.
- **[ROADMAP.md](ROADMAP.md)** — revamp priorities and suggested work order.
- **[PRE_ROSTER_PLAN.md](PRE_ROSTER_PLAN.md)** — what to build now, while real student names are not available yet.
- **[PRODUCT_CONTRACT.md](PRODUCT_CONTRACT.md)** — the rules for what each track/page is allowed to do (student vs. teacher, Grade X vs. Grade XI).
- **[LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md)** — the readiness checklist to work through before the site goes live.

## Reference (used when real data / the next-semester system is worked on)
Not needed for day-to-day cleanup; these are specifications you consult when wiring real data.
- **[ROSTER_IMPORT_CONTRACT.md](ROSTER_IMPORT_CONTRACT.md)** — the exact CSV format and rules for importing an official student roster.
- **[NEXT_SEMESTER_API.md](NEXT_SEMESTER_API.md)** — the separate next-semester Google Apps Script API (kept isolated from the current-year backend).
- **[NEXT_SEMESTER_MIGRATION.md](NEXT_SEMESTER_MIGRATION.md)** — the checklist for turning the isolated `next-semester/` preview into the real student system.
- **[NEXT_SEMESTER_PROFILE_FORM.md](NEXT_SEMESTER_PROFILE_FORM.md)** — how to set up the Google Form that collects student profile answers.

## Also useful
- `scripts/check-site.mjs` (repo root) — run `node scripts/check-site.mjs` to check for broken links, script errors, and unused CSS. Runs automatically on every push via GitHub Actions.
