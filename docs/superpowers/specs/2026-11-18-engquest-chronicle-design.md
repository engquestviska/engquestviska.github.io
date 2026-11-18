# EngQuest Chronicle: end-of-semester records mode

Date: 2026-11-18 · Status: APPROVED by owner 2026-11-18, not built (launch date still to come)
Visual reference: [2026-11-18-engquest-chronicle-concept.html](2026-11-18-engquest-chronicle-concept.html) (open in a browser; it plays). Published copy: https://claude.ai/artifact/SgghHR9g4rBpb7Jpm2Yp4h

## Intent

When the semester ends, the student side of EngQuest stops being a working tool (submit tasks, check scores, open materials) and becomes a record of what each student achieved. Students open it, watch a short animated story of their semester, and get a picture they can post on Instagram/WhatsApp stories. The owner calls it **EngQuest Chronicle** (a deliberate name so it isn't "Wrapped").

Why: the semester's work is done; what's left worth showing is the result. It's the students' "fruit of work", something to be proud of and share.

## Goals

- At semester end, the student website (Grade 10 and 11 pages) and the native student app switch to Chronicle mode.
- First open plays the story: 8 tap-through slides, then the share card.
- After that, the app/site opens to the records page, with Share and Replay story.
- The share card is a 9:16 image the student can save or share in one tap.
- Same design in both grades; the accent color follows the grade (G10 blue, G11 teal).

## Non-goals

- No change to the teacher website or teacher app. Teachers keep working normally (they may still fix scores after the switch; Chronicle shows the latest numbers).
- No new data collection. Chronicle only shows data EngQuest already has.
- No Semester 2 work here (reset/rollover is a separate task).
- No iPhone app (iPhone students use the website, as today).

## Anti-goals (would be a failure even if it works)

- Looking like Spotify Wrapped (name, wording, or copying its visuals).
- Looking "color-y". The owner rejected the multi-color poster; keep the one-accent paper look.
- Stiff, still screens. Motion is a requirement, not decoration.
- Numbers so big or so cropped that you have to work out what they say.
- Softening or hiding bad results. Owner decision: every student sees their real numbers, including rank and low scores. "It's just a number." No special treatment for low performers.

## Decisions made with the owner

| Topic | Decision |
|---|---|
| Format | Story first, then the card (like Wrapped's flow): ~8 slides, then a share card, then a records page with Replay |
| Visual direction | "A1 Paper": off-white paper, black ink, one accent color, a giant serif number per slide, a printed masthead at the top, thin rules |
| Name | EngQuest Chronicle |
| Motion | Each slide animates in (see per-slide notes); the story auto-advances, and tap skips ahead |
| Number size | Stat slides ~150–160px with only a slight crop; Final Score readable in full; Class Rank is the biggest number in the story but fully visible, just touching the edge |
| Honesty | Real numbers for everyone, including rank |
| After the story | Same poster style, no boxes or cards, thin rules between sections |

Rejected along the way: a plain card-only version (too much like a report card), Quest (dark gold trading card), Scrapbook, and multi-color Poster.

## Look

- Paper `#F4F2ED`, ink `#141414`, accent G10 `#2450E0` / G11 teal (match the existing G11 teal). Dark slides use ink as the background; the Class Rank slide is full accent.
- Fonts: Playfair Display (giant numbers, italic display words), Bricolage Grotesque (headings/labels), Nunito (body, already used by the student app).
- Masthead on every slide: "*EngQuest Chronicle*" left, slide topic in small caps right, thick rule above and thin rule below.
- Blue (the accent) is used once per slide, on the thing that matters.
- One soft accent circle per slide that slowly "breathes".

## The story (8 slides, ~4.5 s each, tap to skip)

1. **Cover**: "Chronicle" drops in letter by letter, then "Semester 1 · 2026/27 · Grade 10", then "Starring *Name*", class, "Tap to begin".
2. **Attendance**: present-days count (e.g. 31) slides in; "days you showed up, out of 34"; one dot per school day fills in one by one (present = ink, sick/permit = accent, absent = hollow); sticker with the %.
3. **Tasks**: tasks finished (giant); one tile per task pops in; the best-scoring task glows in accent; "Your best: <task> · <score>".
4. **Vocabulary**: dark slide; the student's own My Vocabulary words scroll in rows (alternating directions); a paper panel shows the word count and the latest word. Optional "top 5 in class" tag.
5. **Level and XP**: the level number in accent slides in; XP counts up; a ruler-style XP bar fills; "N XP to next level".
6. **Final Score**: dark slide; score counts up as a whole number (no decimals, anywhere in Chronicle); a marker slides along a 0–100 scale; chapter + ASAT scores fade in below.
7. **Class Rank**: full accent slide; "In <class> you finished"; the number counts down from class size to the student's rank; "out of N".
8. **Grade Rank** (owner, 2026-11-18): dark slide; "Across all of Grade 10"; a row of class chips with the student's class in accent; the number counts down from the grade size to the student's place, in accent; "out of N students in Grade 10". The ranking covers every active student in that grade the owner teaches (all G10 classes, or all G11 classes), by Final Score, with ties sharing a place.
9. **Share card**: see below.

Rank numerals scale with their digit count so they never run off the screen: 1 digit 250px, 2 digits 170px, 3 digits 120px (Grade Rank starts at 170px).

## Transitions and idle motion (owner, 2026-11-18)

- **Between slides:** same background → the old slide fades out and slides left while the new one glides in from the right; background changes (paper → black → blue) → the new slide's color spreads out in a circle from near the bottom. Leaving slides also shrink their circle away and drop their big number.
- **While a slide is on screen** (nothing sits fully still):
  1. Cover: the circle grows in and then slowly breathes; the name floats gently; both animate out when the slide leaves.
  2. Attendance: after the dots fill, they pulse in a slow wave.
  3. Tasks: after the tiles fill, they pulse in a slow wave; the best task keeps glowing.
  4. Vocabulary: the student's words run slowly as text, rows alternating left and right (each row loops in 22–38 s).
  5. Level: the XP bar fills over about 2.4 s while the XP number counts up alongside it.
  6. Final Score: the blue marker slides to the score, then keeps bobbing gently.
  7. Class Rank and 8. Share card: as in the concept (countdown; soft shine).
- `prefers-reduced-motion`: no transitions or idle motion; slides show at rest.

## Sharing (owner, 2026-11-18)

Students share without saving to the gallery first.

- **Level 2: "Share to Instagram Story" (student app, primary button).** One tap opens Instagram's Story editor directly with the Chronicle card as a movable sticker over a background in Chronicle colors (paper top / ink bottom), the way Spotify and Strava do it. Uses Instagram's `com.instagram.share.ADD_TO_STORY` intent, which requires a **Meta (Facebook) developer App ID**. The owner creates that (free, ~10 min at developers.facebook.com); only the ID number goes into the app. If Instagram isn't installed, the button is hidden.
- **Level 1: "More ways to share" (fallback, everywhere).** Opens the phone's share sheet with the picture attached (Instagram, WhatsApp status, TikTok…). App: Android share sheet. Website on a phone (incl. iPhone): Web Share. Website on a laptop: downloads the picture instead.
- Instagram Story sharing is not possible from a website, so iPhone and website users get level 1 only.

## Share card (the picture students post)

9:16 portrait (export at 1080×1920). Masthead; "Starring" + name + class/grade; giant accent "#rank" with "Class Rank" label; Final Score alone on the right; Grade Rank as one quiet line under the big number ("#11 of 178 in Grade 10"), so the card stays uncluttered; a bottom strip separated by thin rules with Level · Present % · Tasks · Words. On screen a soft shine passes over it; the exported image is static.

## Records page (after the story, until next semester)

- App: masthead "EngQuest Chronicle · Sem 1 · Ended"; name large with a smaller accent #rank beside it; buttons **Share my Chronicle** (primary) and **Replay**; rows separated by rules: Final (/100), Scores (Ch 3/4/5/ASAT), Present (count, %, day dots), Level (level, XP, tasks), Words (count + scrolling words).
- Website: the same content laid out wider, with the share card on the left and **Download picture** + **Replay** under it.
- Submit, materials, and the score-checking flow are not reachable from the student side in this mode.

## Data each screen needs (all exists today)

| Shown | Source |
|---|---|
| Name, class, grade | student record |
| Attendance per day + counts | attendance (Present/Absent/Sick/Permit) |
| Tasks finished, best task + score | task submissions/scores |
| Vocabulary count, word list, latest word | My Vocabulary |
| Level, XP, XP to next level | XP system |
| Final Score, chapter scores, ASAT | `getFinalScores` (blank ASAT/chapter = 0, owner decision 2026-11-18) |
| Class Rank + class size | same ranking as the Scores "Class Rank" tab (ties share a place) |
| Grade Rank + grade size | the same Final Scores across every active class of that grade (ties share a place) |

## Approaches considered (how it gets built)

1. **One shared web Chronicle page, shown in both the website and (inside a WebView) the native app.** One implementation of the animations and the share image (canvas → PNG). The app hands it the student's session. Downside: a web screen inside the native app.
2. **Build it twice: web for the website, Compose for the native app.** Fully native feel, but every slide and animation is built and maintained twice.
3. **Native app only, website shows just the records.** Cheaper, but iPhone students (website only) would miss the story.

**Picked: 1 (owner, 2026-11-18).** The story is a one-off seasonal screen where animation and type quality matter more than native feel, and one page keeps the website and app identical. The native student app shows the shared Chronicle page in a WebView while records mode is on.

## Switching on and off

**Owner decision (2026-11-18): a fixed date.** Chronicle switches on by itself at 00:00 WIB (Asia/Jakarta) on a set date. The date is stored in one backend setting that the website and app read at startup, so it can be moved without a new app release; before that date EngQuest works normally. The owner gives the actual date before launch. Known trade-off, accepted: if some scores are still missing on that date, students see incomplete numbers; any teacher fix after the switch shows up the next time Chronicle is opened. "Story already seen" is remembered per device, so the story plays once and Replay brings it back.

## Edge cases (my calls, marked so the owner can check)

- Missing data (no vocab words, no attendance yet): skip that slide rather than show 0 with fanfare. *(my call)*
- Ties in rank: show the shared place, e.g. "#4", as the Scores tab does.
- Long names: the name shrinks to fit one or two lines; never cropped.
- Students who left the class: excluded, as on the Scores tab.
- Stickers ("top 5 in class", "91%"): show the plain fact; the "top 5" tag only appears when it's true. *(my call)*

## Left to the builder

- Exact animation timings/easing (the concept page is the reference).
- How the share image is produced (canvas render vs. screenshot) and the Android share intent.
- Fitting the masthead/number positions to real phone sizes.
- Whether "latest word" needs a timestamp that My Vocabulary doesn't store yet (if not, use the last word added).

## Open questions for the owner

1. ~~Build approach~~ answered: 1, one shared page.
2. ~~Who flips the switch~~ answered: a fixed date (owner supplies the date before launch).
