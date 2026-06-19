# English Quest Roster Import Contract

This contract is the only supported boundary for adding official student rosters
to the `next-year` system. It can be prepared before names arrive and used for
both Grade X and Grade XI.

## Canonical CSV Columns

Keep the header names and order exactly as shown:

```text
grade,class_id,student_no,full_name,preferred_name,profile_photo,active
```

| Column | Required | Accepted value |
| --- | --- | --- |
| `grade` | Yes | `10` or `11` |
| `class_id` | Yes | `XE1`-`XE5` for Grade X; `XIF7`-`XIF9` for Grade XI |
| `student_no` | Yes | Whole number from `1` to `36`, unique inside its class |
| `full_name` | Yes | Official student name, trimmed, not a placeholder |
| `preferred_name` | No | Student's preferred display name |
| `profile_photo` | No | Public HTTPS image URL or blank |
| `active` | Yes | `TRUE` or `FALSE` |

CSV encoding must be UTF-8. Keep the header row, do not merge cells, and do not
put formulas in roster fields.

## Identity Rule

The permanent student key is:

```text
class_id + student_no
```

Names are display data and may be corrected later. Never use a name as the join
key. Once personal scores, attendance, submissions, XP, or strikes exist, do not
change a student's `class_id` or `student_no` without migrating every related
record.

## Class And Grade Rules

The only accepted production combinations are:

| Grade | Class IDs |
| --- | --- |
| `10` | `XE1`, `XE2`, `XE3`, `XE4`, `XE5` |
| `11` | `XIF7`, `XIF8`, `XIF9` |

Class IDs are uppercase and contain no spaces or hyphens. An import must reject a
row when its grade does not match its class ID.

## Validation Rules

Reject the complete import before writing anything when:

- A required header is missing or renamed.
- A required cell is blank.
- A class ID is outside the confirmed eight classes.
- Grade and class ID disagree.
- `student_no` is not a whole number from `1` to `36`.
- Two rows have the same `class_id + student_no`.
- `active` is not exactly `TRUE` or `FALSE`.
- An active row uses a placeholder name such as `Student 1`.
- `profile_photo` is present but is not an HTTPS URL.

Report every invalid row together rather than stopping at the first error. The
import is atomic: either every accepted row is written, or none are.

## Active, Inactive, And Placeholder Records

These states are intentionally different:

- **Active student:** real official name and `active=TRUE`. May appear in login,
  student lists, dashboards, and class data.
- **Inactive student:** real official name and `active=FALSE`. Retained for
  history but hidden from normal student flows.
- **Placeholder:** synthetic name used only while testing. It must remain
  `active=FALSE` and must never receive personal scores or profile data.

The sample CSV uses obvious `TEST STUDENT` names and `active=FALSE`. Those rows
are not production students and must not be activated.

## Spreadsheet Mapping

The CSV is a transport format, not a new source of truth. After validation:

| CSV field | Destination |
| --- | --- |
| `class_id` | `Students.class_id` |
| `student_no` | `Students.student_no` |
| `full_name` | `Students.full_name` |
| `preferred_name` | `Students.nickname` |
| `active` | `Students.active` |
| `grade` | Validated against `Classes.grade`; not duplicated in `Students` |
| `profile_photo` | Initial approved `Profiles.photo_url`, or blank until the student adds one |

Existing personal records must not be deleted when a student becomes inactive.
An import updates roster identity fields only; it does not overwrite scores,
attendance, tasks, submissions, XP, strikes, or profile answers.

## Import Procedure

1. Export the official roster as UTF-8 CSV using the canonical headers.
2. Validate all rows without writing to the master Sheet.
3. Review duplicate, missing-value, class, grade, and placeholder errors.
4. Import one class first.
5. Confirm student login and read-only personal pages for that class.
6. Import the remaining classes.
7. Run `getDataReadiness` and resolve every error and active-class warning.
8. Keep the original CSV as a dated, access-controlled backup.

## Sample Data

Use `samples/roster-import.sample.csv` only for schema testing. Its rows are
inactive and synthetic. A successful sample test proves column and validation
compatibility; it does not make the site ready for real students.

