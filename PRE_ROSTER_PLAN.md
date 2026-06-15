# English Quest Pre-Roster Development Plan

This is the active development plan while official student names are unavailable.
Student rosters are expected only one or two weeks before classes begin. Missing
rosters must not block unrelated product, security, design, or infrastructure work.

## Confirmed Scope

- Grade X classes: `XE1`, `XE2`, `XE3`, `XE4`, `XE5`
- Grade XI classes: `XIF7`, `XIF8`, `XIF9`
- Student names and numbers: pending official school data
- Branch: `next-year`
- `main` must remain untouched until explicitly approved

## Working Rules

1. Never invent or reuse student names as production data.
2. Pages without roster data must show intentional empty states.
3. Features must accept roster import later without requiring a redesign.
4. Complete and push one verified development batch at a time.
5. When asked to continue or do next, follow the priority order below unless a
   newer request changes the priority.

## Priority Order Before Rosters Arrive

### 1. Canonical Class Configuration

Create one shared source for the eight confirmed class IDs and labels. Remove old
or inconsistent class lists from student pages, teacher tools, sidebars, and API
helpers.

Done when:

- Every class selector uses only the confirmed eight classes where appropriate.
- Grade X tools use `XE1-XE5`.
- Grade XI tools use `XIF7-XIF9`.
- No active page uses obsolete `XE6-XE11`.

### 2. Student Navigation Simplification

Make Grade X and Grade XI navigation consistent and student-focused. Show only
useful student routes and the selected student's class instead of exposing every
class as navigation.

Done when:

- Grade X and Grade XI use the same navigation structure.
- Student navigation does not look like a teacher class browser.
- Mobile navigation is verified.

### 3. Teacher Security Hardening

Audit teacher authentication and every write action. Browser storage may preserve
a session, but backend verification must remain the authority for protected writes.

Done when:

- Every teacher write action requires backend credential verification.
- Teacher pages do not reveal management content before login.
- Logout and expired-session behavior are consistent.
- Known security limitations are documented.

### 4. Roster Import Contract

Prepare a fast import path for the official roster without requiring the roster
itself yet.

Required fields:

- Grade
- Class ID
- Student number
- Full name
- Preferred name, optional
- Profile photo, optional
- Active status

Done when:

- Required columns and validation rules are documented.
- Duplicate class and student-number handling is defined.
- Placeholder and inactive records are safely distinguishable.
- Import can be tested with non-production sample rows.

### 5. Empty-State And Data-Readiness Pass

Ensure student pages remain useful and understandable before scores, attendance,
tasks, submissions, and rosters exist.

Done when:

- Empty pages explain what will appear without exposing developer terminology.
- Missing data never causes broken layouts or JavaScript errors.
- Pages automatically become useful when valid records are added.

### 6. Shared-Code Consolidation

Reduce repeated inline CSS, API helpers, class lists, and authentication logic.
Prioritize shared behavior that currently creates inconsistency or security risk.

Done when:

- Class configuration is shared.
- Repeated teacher authentication behavior is shared.
- New pages do not copy large blocks of existing code.
- Shared changes are verified across Teacher, Grade X, and Grade XI.

### 7. Final Pre-Roster QA

Run a complete desktop/mobile and functional audit before adding official names.

Done when:

- Local references and scripts pass.
- Confirmed routes return successfully.
- Student and teacher permissions are reviewed.
- The launch checklist reflects the actual current behavior.

## Work That Must Wait For Official Data

- Importing real student names and numbers
- Validating final class sizes
- Testing individual student login/profile selection with real rosters
- Publishing personal scores, attendance, strikes, ranks, or statistics
- Final privacy review using real student records

## Roster Arrival Procedure

When official names arrive:

1. Validate the file against the roster import contract.
2. Import one class first.
3. Test login, profile selection, and personal-data pages for that class.
4. Resolve duplicates and missing values.
5. Import the remaining classes.
6. Run final privacy, teacher-write, desktop, and mobile QA.

