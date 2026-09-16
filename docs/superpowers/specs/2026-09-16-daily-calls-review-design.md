# Daily Calls Review — Design Spec

Date: 2026-09-16
Status: Approved (brainstorming), pending implementation plan

## Purpose

A personal Android app that tracks how many calls the user makes/receives with
tagged coworkers/team members each day, total talk time, first/last call of
the day, and an "unofficial shift span" derived from call activity. It also
flags calls that fall outside normal working hours, on weekly off days, or on
holidays, and supports browsing history by day, week, month, or custom range.

## Core Requirements

- Auto-read call history from Android's built-in call log (no manual entry).
- Track calls only with contacts explicitly tagged as "coworker/team" by the
  user (not all calls on the phone).
- Missed calls (0 duration) count toward the call count, same as answered
  calls. Specifically: Android's call log distinguishes `MISSED`, `REJECTED`,
  and `BLOCKED` call types — `MISSED` and `REJECTED` count (both are real
  call attempts the user engaged with), `BLOCKED` is excluded (auto-blocked,
  never reached the user).
- Daily summary includes:
  - Total calls, total talk time
  - Per-coworker breakdown (calls + time per tagged contact)
  - First call time and last call time of the day (tagged calls only)
  - "Unofficial shift span" = last call time − first call time (tagged calls
    only); shown as a duration (e.g. "8h 45m, 9:02 AM – 5:47 PM")
- Calls outside configured working hours, on non-working weekdays, or on a
  holiday:
  - Still count toward the normal daily/range totals
  - Additionally flagged/badged so they're visible at a glance
- History browsing:
  - Month calendar view — tap a date to see that day's detail
  - Week view — Mon–Sun rollup between daily and monthly
  - Custom date range — pick start/end date, see aggregated totals for the
    range, including average shift span across the range and busiest
    coworker (most calls/time) in that range
- Filters (single-select), available in Month, Week, and Range Detail views
  alike — the filter always applies to whichever range is currently on
  screen, not just "the month":
  - **All** — no filter
  - **Sundays** (or configured off-days) in the current range — list of
    matching days with their stats
  - **Holidays** in the current range — list of matching days with their
    stats
  - **Outside working hours** — list of *individual calls* (not days) that
    fell outside the configured working-hours window, since off-hours calls
    can occur on any day
- Export — generate a CSV of exactly what's currently on screen (respects
  the active filter, if any) via Android's share sheet. PDF export may be
  added later without a redesign.
- Home screen widget (Jetpack Glance) — shows today's call count and total
  time; tapping opens the app.

## Settings (user-configurable)

- Working hours: start time, end time
- Working days: each weekday individually toggled on/off (custom, not a
  fixed Mon–Fri or Mon–Sat default)
- Holidays: manually added list of dates + labels (no external calendar
  dependency)
- Tagged coworkers/team: contacts picked from the phone's contact list,
  addable/removable at any time. Tagging a contact tags ALL phone numbers on
  that contact card (a coworker may have a mobile and a work line) — not
  just one selected number.

## Architecture

- **Language/UI:** Kotlin, Jetpack Compose, MVVM (ViewModel + Repository),
  single-module app.
- **Call data:** never duplicated into app storage. Every screen queries
  `CallLog.Calls` via `ContentResolver` on demand, always with a date-range
  `WHERE` clause bounding the query to the range actually being viewed
  (CallLog supports filtering on the `DATE` column) — never a full-history
  scan. Results are filtered to tagged phone numbers and aggregated in
  memory. Android's call log already retains full history, so no background
  sync job is needed for this.
- **Phone number matching:** numbers are compared by their last 10 digits
  (stripping country codes, spaces, dashes, and other formatting), which is
  robust to the different ways the same number can appear in the call log
  vs. the contacts provider.
- **Local storage (Room):**
  - `TaggedContact` — phone number (normalized to last-10-digits form) +
    display name + source contact ID. One row per tagged phone number; a
    contact with two numbers produces two rows sharing the same contact ID
    so they can still be grouped in per-coworker breakdowns.
  - `AppSettings` — working-hours start/end, per-weekday working flags
  - `Holiday` — date + label
- **Widget refresh:** a WorkManager periodic job (~15 min, the platform
  minimum) updates the Glance widget with today's totals. This is the only
  background work in the app.

## Permissions

- `READ_CALL_LOG` — required to read call history. Requested with a
  rationale screen on first launch.
- `READ_CONTACTS` — required only for the "pick coworkers" screen.
- If either permission is denied, the app shows an empty state explaining
  the app can't function without it and offers a button to open the
  permission settings screen. No silent failure.

## Screens

1. **Permission/onboarding** — request permissions with rationale.
2. **Team setup** — pick/add/remove tagged coworker contacts.
3. **Settings** — working hours, working days, holiday list management.
4. **Home (Today)** — today's full summary (see Daily Summary above).
5. **History** — month calendar (tap date → Day Detail) with a toggle for
   Week view, plus the single-select filter chips (All / Sundays / Holidays
   / Outside working hours).
6. **Day Detail** — same layout as Home, for any selected past date.
7. **Range Detail** — custom start/end date → aggregated totals, per-
   coworker breakdown, busiest coworker, average shift span.
8. **Export** — triggered from History or Range Detail; generates and
   shares a CSV of the visible data.
9. **Home screen widget** — today's call count + total time.

## Edge Cases

- Day boundaries use the device's local calendar day.
- If a date is both a holiday and would otherwise be a working day, the
  holiday flag takes precedence for styling/flagging purposes.
- A day with zero tagged-coworker calls shows "—" for shift span (not 0h).
- Calls with untagged/unmatched numbers are excluded from all stats.
- Contact display names are resolved fresh from the contacts provider at
  query time (so renames in Contacts are reflected without extra syncing);
  matching itself is by normalized phone number, not by contact ID, so a
  tagged number still matches even if removed from Contacts entirely.
- If the same person is tagged via two separate contact cards (e.g. a
  personal and a work entry), their stats are tracked as two separate
  coworkers rather than merged. Known limitation, out of scope for v1.

## Known Limitations

- Working hours assume a same-day window (start time < end time). A window
  crossing midnight (e.g. a night shift) is not supported in v1.
- Day/range grouping uses the device's local timezone at query time, not at
  call time. If the device's timezone changes (e.g. travel), which calendar
  day a past call is grouped under can shift accordingly.
- All data stays on-device — the app makes no network calls and has no
  cloud sync or analytics. Worth stating explicitly given it handles call
  logs and contacts, both sensitive personal data.
- Widget behavior before setup is complete (no permissions granted yet, or
  no coworkers tagged yet) shows a neutral placeholder state prompting the
  user to open the app, rather than a blank or error widget.
- Team Setup is skippable on first launch (the user can reach Home with an
  empty coworker list, which just shows all-zero stats) rather than being a
  mandatory gate — avoids blocking the user if they want to explore the app
  first.

## Testing Approach

- Aggregation logic (daily/range totals, off-hours flagging, shift span,
  busiest coworker) is implemented as pure functions taking a list of call
  records + settings as input — unit-testable without any Android framework
  dependency.
- Room DAOs get lightweight instrumented tests.
- Manual verification of CallLog/Contacts queries and the widget on a real
  device or emulator (content provider behavior can't be fully unit tested).

## Explicitly Out of Scope (for this spec)

- PDF export (CSV only for now)
- Multi-select / combinable filters in History (single-select only)
- Per-SIM breakdown on dual-SIM devices
- Real-time live-refresh via ContentObserver (on-demand query on screen
  load/pull-to-refresh is sufficient for v1; can be layered in later without
  a redesign)
