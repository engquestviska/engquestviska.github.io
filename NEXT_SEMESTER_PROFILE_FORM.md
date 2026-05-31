# Next Semester Profile Form

This form feeds the next-semester student profile system.

The form is not for scores, attendance, XP, strikes, or submissions. It only collects the student's profile answers for the `Profiles` tab in the master spreadsheet.

## Target Sheet

- Spreadsheet: `English_Quest_Next_Semester_Template`
- Raw response tab: `Form responses 1`
- Dashboard profile source: approved rows from `Profiles` and approved rows from `Form responses 1`
- Join key: `class_id + student_no`
- Approval field: `approved`

The dashboard reads the latest profile row where:

- `class_id` matches the logged-in student.
- `student_no` matches the logged-in student.
- `Profiles.approved` is blank or `TRUE`.
- `Form responses 1.Approved` is `TRUE`.

## Form Questions

Build the Google Form using the `Profile_Form_Questions` tab as the source of truth.

| Question | Field Key | Required | Answer Type | Notes |
| --- | --- | --- | --- | --- |
| Choose your class | `class_id` | Yes | Dropdown | Use active class IDs only when real classes are known. |
| Student number | `student_no` | Yes | Number | 1-36. |
| Full name | `full_name` | Yes | Short answer | Must match the roster as closely as possible. |
| Preferred name | `preferred_name` | No | Short answer | Shown on profile card. |
| Profile photo link | `photo_url` | No | Short answer | Google Drive image link if used. |
| My English learning goal | `learning_goal` | No | Paragraph | For student profile. |
| My English strength | `english_strength` | No | Short answer | For My Statistics. |
| What I want to improve | `english_weakness` | No | Short answer | For My Statistics. |
| Favorite English activity | `favorite_activity` | No | Short answer | For personalization. |
| Quote or motto | `quote` | No | Short answer | Keep short. |

## Setup Rules

1. Link the Google Form response destination to the master spreadsheet.
2. Keep raw responses in `Form responses 1`.
3. Add an `Approved` column to the right of the form response headers.
4. Type `TRUE` for rows that can appear on the student dashboard, or `FALSE` for rows that should stay hidden.
5. Optional later: copy/normalize approved responses into `Profiles` with the exact headers below:

```text
timestamp, class_id, student_no, full_name, preferred_name, photo_url, learning_goal, english_strength, english_weakness, favorite_activity, quote, approved
```

6. Keep `student_no` stable. Do not use names as the primary connection because names can change or be typed differently.

## API Check

Use:

```text
?action=getProfileFormSpec&includeInactive=true
```

This returns the current question list, expected profile headers, active/template classes, and profile read rules.
