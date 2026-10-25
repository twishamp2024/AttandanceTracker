# Design notes

Why this app is shaped the way it is. The *what* lives in [README.md](README.md);
this is the *why*, including the places where the original specification was
deliberately not followed literally.

---

## 1. The product thesis

One sentence: **make the common case one tap, and make the consequence of that tap
obvious.**

Everything else is subordinate to it. A student leaving a lecture hall has roughly
five seconds of attention. If marking attendance costs a form, a date picker or a
confirmation, it stops happening after the second week, and an attendance tracker
with gaps is worse than none — it reports a percentage that is confidently wrong.

So the design budget was spent on two things:

1. **Today** opens on the class that just happened, with oversized `Present` /
   `Absent` buttons. Cold start to recorded attendance: two taps.
2. Every subject card ends in an **instruction**, not a statistic. "You can miss 3
   more classes" rather than "76.9%". The number is the evidence; the sentence is the
   answer.

Everything rarer — cancelled, excused, notes, corrections — is one tap deeper. Never
impossible, never in the way.

---

## 2. Deliberate deviations from the specification

These are judgement calls where following the spec literally would have produced a
worse app. Each is defensible and each is documented in the code.

### 2.1 Session *kind* was split out from attendance *status*

The spec listed one status enum:

```
PRESENT, ABSENT, EXCUSED, CANCELLED, RESCHEDULED, EXTRA_CLASS
```

`RESCHEDULED` and `EXTRA_CLASS` are not attendance outcomes — they describe how a
session came to exist. Folded into one enum, **"an extra class that I attended" is
inexpressible**, which is precisely the case that affects the maths: an extra class
you turned up to must raise both attended and conducted.

So there are two orthogonal fields:

- `AttendanceStatus` — `NOT_MARKED`, `PRESENT`, `ABSENT`, `EXCUSED`, `CANCELLED`
- `SessionKind` — `SCHEDULED`, `EXTRA`, `RESCHEDULED`

This removes an entire family of data bugs. The export format carries both, so
nothing is lost. It is the single most significant deviation and the one I would
defend hardest.

### 2.2 `NOT_MARKED` was added as a real status

The spec had no "unanswered" state. Without one, a session generated for today is
indistinguishable from an absence, and the dashboard lies every afternoon until you
get round to marking. `NOT_MARKED` is excluded from both numerator and denominator,
and the UI reports how many are outstanding.

### 2.3 Percentages are integers, not floats

The spec said "avoid floating-point mistakes that could produce confusing UI values."
Rather than rounding carefully at the edges, the type system enforces it:
`Percent` is a value class over **basis points**, and every comparison in
`AttendanceCalculator` is cross-multiplied into `Long`:

```
A / C ≥ R / 10000   ⟺   A × 10000 ≥ R × C
```

A student sitting on exactly 75% is told they are safe, because the comparison is
exact rather than nearly exact. Rounding happens once, at the display boundary,
half-up.

### 2.4 The spec's worked examples contained arithmetic slips

The spec offered "Algorithms 48/52, required 75% → you can miss 10 classes." The
correct answer is **12**: 48/64 is exactly 75%, and 48/65 is 73.8%. Similarly, the
stretch-target example implied 125 classes to reach 90% from 35/50; the true minimum
is 100, since 135/150 is exactly 90%.

The implementation follows the mathematics, not the illustration, and the tests
verify each boundary by replaying the hypothetical in both directions rather than
trusting a hard-coded expectation.

### 2.5 Excused handling became a setting

The spec did not say how excused absences affect the percentage. Institutions
genuinely differ — some drop them from the calculation, some count them as present.
Guessing would silently produce a wrong number for half of all users, so
`ExcusedPolicy` is a user setting, defaulting to *Don't count at all*, and Settings
explains the effect of each option in words.

### 2.6 Overall attendance is pooled, and says so

Averaging per-subject percentages would weight a 2-class seminar the same as a
40-class lecture course. Overall is therefore `Σattended / Σconducted`, labelled
"Pooled across every subject this semester".

More importantly: because subjects carry **different** requirements, one overall
number *cannot* answer "am I safe?" — 84% overall can hide a subject at 60%. So the
counts of safe / caution / critical subjects always travel with it, and the
subject list is sorted worst-first.

### 2.7 Start destination is Today, not the dashboard

The spec called the dashboard the home screen, and also demanded that opening the app
and marking a class be as fast as possible. Those conflict. Resolution: **Today** is
the start destination, and the dashboard is one tap away in the bottom bar. Nothing
is lost and the optimised path stays optimal.

### 2.8 Sample data is never seeded automatically

The spec asked for optional demo data. It is created only from an explicit Settings
action, into a clearly named semester ("Semester 5 (Demo)") that is *not* made active,
and can be deleted like any other. Demo rows quietly mixed into real attendance would
be worse than no demo data at all.

### 2.9 No DI framework

The spec suggested "MVVM or another well-justified architecture". Hilt would add an
annotation processor, a build step and generated indirection to solve a problem that
`AppContainer` solves in ~50 lines of `by lazy`. At one module and a dozen
collaborators, the framework is the more complex option, not the simpler one. This is
the justification.

---

## 3. Navigation structure

```
Onboarding  (only when no semester exists — the first real task, not a carousel)
│
├── Today ◀ start destination
│     └── Session editor sheet (all statuses, note, delete)
├── Overview
│     ├── Subject detail ── Subject edit
│     └── History (filters: range, subject, status) ── Session editor sheet
├── Timetable
│     └── Slot editor sheet
├── Calendar
│     └── Day sheet ── Session editor sheet
└── Settings
      ├── Semesters ── Semester edit
      └── Timetable
```

Four bottom-bar tabs. A fifth would have pushed the targets under a comfortable
thumb width, so Settings lives behind a top-bar icon on Today and Overview — it is a
"once a month" destination, not a daily one.

Below 600dp the app uses a **bottom bar**; at 600dp and above a **navigation rail**.
The switch is on measured width, not a device-class constant, so it does the right
thing on a tablet, an unfolded foldable, *and* a phone in landscape (where a bottom
bar would eat scarce vertical space).

The bottom bar hides itself on detail screens so they get the full height.

---

## 4. Visual design decisions

**Green seed colour, not Material purple.** The app's entire vocabulary is
green/amber/red status; a green primary keeps the chrome and the data speaking the
same language. Material You dynamic colour is used where available (Android 12+),
with the brand palette as fallback.

**Status colours are never dynamic.** They are held in a separate `StatusPalette`
outside the generated scheme. "Safe" must not become "slightly lilac" because of
someone's wallpaper — these colours carry meaning, not decoration.

**Colour is never the only signal.** Every status carries an icon *and* a word *and*
a supporting sentence. The three health icons differ in **shape** (tick / triangle /
filled error), not just hue. Subject accents are always accompanied by the subject's
name and code. This is what keeps the app usable for colour-blind users and under a
screen reader.

**Not everything is a card.** Cards are used where a unit of information is
genuinely tappable or self-contained (a subject, a session, a settings group).
Headers, list rows, the calendar grid and the timetable days are not wrapped in
cards, so hierarchy comes from spacing and type rather than from a uniform wall of
rounded rectangles.

**The attendance ring shows the requirement as a tick mark.** Without it the reading
is "is 76 a big number?"; with it the reading is "am I past the line?" — which is the
actual question.

**The calendar uses dots, not filled cells.** A full-bleed colour block makes the
date numbers hard to read and turns an ordinary month into an alarming wall of red.
The dot carries the same information while the grid stays legible. A legend is always
on screen, not hidden in a menu, because otherwise the dots mean nothing.

**The trend chart is cumulative, not per-class.** Each point is the percentage *as it
stood after that class*. A per-class 0/100 spike chart would be noise; the cumulative
line answers "was I above the line back then?" The series is thinned to ~40 points so
a full semester still reads as a curve on a phone.

**Motion is wayfinding, not decoration.** A ~220ms horizontal slide for
forward/back, a fade between tabs. Marking five classes in a row must never feel like
waiting.

---

## 5. Data model decisions

**The timetable is a template; sessions are the record.** Separating them is what
allows a one-off cancellation, a room change or a deleted class to be recorded
without corrupting the weekly plan — and what allows the plan to change without
rewriting history.

**Sessions are materialised lazily, and expansion is tracked.** A `materialised_days`
table records which days have been expanded. Without it, an empty day is ambiguous
("no classes" vs "not generated yet") and re-expansion would resurrect sessions the
user deliberately deleted. With it, expansion is idempotent, deletions stick, and
timetable edits re-expand only from today forward.

**`slotId` has no foreign key, on purpose.** Deleting a timetable slot must not
cascade into the attendance recorded against it.

**No destructive migration fallback.** The database is the only copy of the data.
An app that silently wipes a semester rather than failing to open has chosen the
wrong failure mode.

**Per-subject requirements, not one global figure.** Labs and theory papers routinely
differ; a single app-wide requirement would give a confidently wrong answer for at
least one subject.

---

## 6. Error handling and data safety

The governing principle: **never silently lose or corrupt an attendance record.**

- **Import validates everything before writing anything.** The whole file is parsed
  and converted to domain objects — dates, times, enums, ranges, duplicates — and only
  then written, inside a single transaction. A truncated or malformed backup cannot
  leave the database half-imported.
- **Unknown enum values degrade, they do not crash.** A row written by a newer version
  reads back as `NOT_MARKED` rather than making the semester unreadable.
- **Duplicates are rejected by the database**, surfaced as a typed
  `DuplicateSessionException`, and turned into a sentence the user can act on.
- **Destructive actions state their cost.** "This permanently removes 6 subjects and
  312 attendance records." Archiving is offered more prominently than deleting,
  because a finished semester is still evidence.
- **Marking is undoable.** A snackbar with **Undo** follows every mark, since the
  most likely data error in this app is a mis-tap on the way out of a lecture.
- **Errors are sentences, not exception messages.** `Throwable.friendly()` maps the
  typed failures to language a person can act on.

---

## 7. Accessibility

- Every interactive target is at least 48dp; the primary mark buttons are larger.
- The attendance ring and the trend chart collapse into a single spoken sentence —
  individual arcs and path segments mean nothing out loud.
- Calendar cells carry a full spoken description ("Monday, 22 September 2026, today,
  3 present, 1 absent"); the grid is far too small to carry text.
- Status is conveyed by icon + word + sentence, never colour alone.
- All type is in `sp`, so the system font-size setting is respected.
- Radio rows are selectable across their whole width, not just the dot.
- Decorative elements (swatches, dots, dividers) are explicitly cleared from the
  accessibility tree so a screen reader is not read a list of colours.

---

## 8. Testing strategy

Attendance mathematics lives in plain Kotlin objects with no Android dependencies,
so the interesting cases run in milliseconds and can be exhaustive.

The property worth highlighting: **safe misses and classes-needed are verified against
brute force.** The test does not merely assert "expect 12" — it replays the
hypothetical and asserts that missing *k* still meets the requirement for every
`k ≤ reported`, and that `reported + 1` breaks it. That catches off-by-one errors in
both directions, which is exactly where this kind of arithmetic fails.

Database behaviour (semester separation, editing history in place, idempotent
materialisation, cascade deletes, the single-active-semester invariant) is tested
against a real in-memory Room database via Robolectric, because those are properties
of the schema and the SQL, not of the Kotlin.

Backup is tested by round trip — export, import, and assert the recomputed statistics
match the originals field by field — plus a set of hostile inputs (foreign format,
malformed JSON, newer version, unknown status, impossible date, out-of-range
percentage), each asserting both that it is rejected *and* that the existing data is
untouched.

---

## 9. Trade-offs consciously accepted

| Decision | Cost | Why it is worth it |
|---|---|---|
| Lazy session materialisation | More moving parts than generating the whole semester up front | Fast first launch, timetable edits do not rewrite history, deletions stick |
| Single module | No enforced layer boundaries at compile time | At this size, module ceremony costs more than it prevents; layering is by package and reviewed |
| Manual DI | No compile-time graph validation | ~50 lines instead of an annotation processor and a second build step |
| Hand-drawn charts | No zoom, tooltips or pan | Zero dependency, automatic theming, exactly the chart needed |
| Inexact reminder alarm | May arrive minutes late | Avoids the `SCHEDULE_EXACT_ALARM` grant for a nudge whose value does not depend on the minute |
| Day-list timetable, not a grid | Less impressive as a screenshot | Actually readable on a 360dp phone once 3-hour lab blocks exist |
| targetSdk 36 while compileSdk is 37 | A lint warning, suppressed with reasoning | New runtime behaviours should be opted into after testing on that platform |

---

## 10. Future extensions

The architecture leaves room for these without restructuring:

- **Home-screen widget / quick-settings tile** — `TodayViewModel`'s state is already
  the exact shape a widget needs.
- **Wear OS companion** — the domain layer is pure Kotlin and has no Android
  dependency; it could move to a shared module unchanged.
- **Holiday calendar** — a table of excluded dates consulted during materialisation;
  the expansion path is already the single choke point.
- **Per-semester requirement overrides by subject type** — `Subject.requirement` is
  already per-subject, so this is a defaulting rule, not a schema change.
- **Attendance goals and streaks** — derivable entirely from existing session rows.
- **Multi-device sync** — the backup format is already a complete, validated
  serialisation of the whole database.
- **Room migrations** — schema version 1 is exported to `app/schemas/`, so migration
  tests can diff against it from version 2 onward.
