# Daily Calls Review — UI/UX Redesign Design Spec

**Date:** 2026-09-19
**Status:** Approved via brainstorming (visual companion), ready for implementation planning
**Builds on:** `2026-09-16-daily-calls-review-design.md` (original app spec, still authoritative for data model, permissions, call classification, aggregation rules, and on-device-only constraint — this doc only changes UI/UX and adds a small number of net-new display features called out explicitly below)

## 1. Problem

The app functions correctly (verified end-to-end on-device) but the UI is the Compose/Material3 default with zero customization: no color scheme, no typography, no icons, raw `Row`/`Column`/`Text`/`Checkbox` everywhere, and native Android date/time picker dialogs that visually clash with everything else. Two screens were called out specifically as hard to use:

- **Team Setup** — a flat, unsearchable checkbox list over the entire phone contact list.
- **Settings** — native picker popups plus a raw list of day-of-week checkboxes.

There is also no app branding (launcher icon, app name treatment) and no real navigation shell — screens other than Home can only be reached by going back to Home first.

## 2. Goals

- Apply a consistent visual design system (color, type, spacing, component style) across every screen.
- Redesign Team Setup's selection flow to be fast even with a large contact list.
- Redesign Settings so working-hours/day/holiday configuration feels native to the app rather than bolted-on system dialogs.
- Add a small number of new display features that make existing data more useful (see §6) — these are data-derivation/UI additions on top of the existing `CallAggregator`/`CallLogRepository`, not new data sources.
- Add app branding (launcher icon) and a real navigation shell (top app bar + bottom navigation).
- Preserve every behavior from the original spec unchanged: phone-number matching, tag-all-numbers-on-a-contact, MISSED+REJECTED counted / BLOCKED excluded, on-device-only, date-bounded `ContentProvider` queries, filters apply to whatever range is on screen, CSV export respects the active filter.

## 3. Design System

### 3.1 Color

Light and dark color schemes, switching automatically with the system theme (`isSystemInDarkTheme()` / Material3 dynamic scheme selection — no in-app toggle).

| Role | Light | Dark |
|---|---|---|
| Background | `#F5F7FA` | `#14151C` |
| Surface (cards) | `#FFFFFF` | `#1E2030` |
| Primary | `#6C4FF5` | `#8B6CFF` |
| Primary container (chips, avatar bg) | `#EDE9FE` | `#2E2450` |
| On-primary-container text | `#6C4FF5` | `#A78BFA` |
| Warning/off-hours bg | `#FFF1E6` | `#3A2A17` |
| Warning/off-hours text | `#C2540A` | `#FF9F5A` |
| Destructive (remove/trash) | `#DC2626` on `#FEE2E2` | same hue, dark-adjusted container |
| Primary text | `#1A1F36` | `#F3F4F6` |
| Secondary/muted text | `#6B7280` | `#9CA3AF` |
| Divider | `#F3F4F6` | `#2A2C3D` |

Avatar background colors are assigned deterministically per person (hash of `contactId` mod a fixed palette of ~6 pastel pairs — purple/orange/blue/green/pink/teal, each with a matching darker text color), so a given coworker's avatar color is stable across Home, Team Setup, History, and Range Detail.

### 3.2 Components (new shared composables)

These replace ad-hoc `Row`/`Text` usage and are reused across every screen below:

- **`StatTile`** — big bold number/value + small uppercase label underneath. Used in 2-column grids for Calls/Talk Time/First Call/Last Call (Home, Day Detail) and Calls/Talk Time/Avg Shift Span/Busiest Coworker (Range Detail).
- **`SummaryCard`** — white/surface rounded card (14dp corner, subtle shadow) wrapping a `StatTile` grid plus a footer row for secondary info (shift span, off-hours badge, holiday/off-day badges).
- **`CoworkerRow`** — avatar (initials, colored) + name + call count/talk time + a relative-volume bar (width = this coworker's call count ÷ busiest coworker's call count in the same list). Used on Home, Range Detail, and the "BY COWORKER" off-hours breakdown.
- **`WarningBadge`** / **`InfoBadge`** — small pill, warning-colored or neutral, for off-hours counts, holiday flags, off-day flags.
- **`ToggleChip`** — outlined when off, filled `primary` when on. Replaces `Checkbox` for Team Setup tagging and History/Range filter selection.
- **`DayCircle`** — 30dp circle, filled `primary` when the day is a working day, gray when not (Settings working-days row).

### 3.3 Typography & icons

Use the existing Material3 type scale (no custom font) but apply it consistently via a real `Typography` definition instead of default. Replace every emoji/unicode symbol used in the brainstorming mockups (🔍 ⟳ 🕘 ⚠ 🎉 ↙ ↗ 🏆) with **Material Symbols vector icons** (`androidx.compose.material:material-icons-extended`) — emoji rendering is inconsistent across OEM skins (this app is verified on MIUI, which reskins emoji) and vector icons theme correctly with the color scheme above. Emoji in this document's mockups were placeholders for icon *meaning*, not the literal implementation.

## 4. Branding

- **App icon:** "Team + phone badge" concept — a people/group glyph on the primary gradient (`#6C4FF5` → `#8B6CFF`), with a small circular phone-icon badge overlapping the bottom-right corner. Built as a proper Android adaptive icon (foreground vector + background layer) via Android Studio's Image Asset tool, composited from two Material Symbols glyphs (Group + Phone), not emoji.
- **App name display:** "Daily Calls Review" in the top app bar, next to a small in-app version of the icon glyph.

## 5. Navigation shell

Currently `HomeScreen` has a raw `Row` of 4 buttons (History/Team/Settings/Refresh) and no other screen has any way to navigate except back to Home. This is replaced by:

- **Top app bar** (all screens): icon + title, contextual title per screen (e.g. "History", "Team Setup", the date range on Range Detail). Detail screens (Day Detail, Range Detail) get a back arrow here instead of relying solely on system back. Home's app bar gets the Refresh action as a trailing icon button (replacing the old "Refresh" button).
- **Bottom navigation bar** (4 top-level destinations only: Home, History, Team, Settings) — persistent across those 4 screens, using `NavGraph.Routes` as the destination targets with `launchSingleTop = true` (already used) plus `restoreState`/`saveState` so switching tabs doesn't reset scroll position. Day Detail and Range Detail are reached by drilling in from History/Range and are **not** part of the bottom nav (no tab highlighted while on them — they're modal-feeling detail views under History).

This directly fixes a real navigation gap: today there's no way to go History → Settings without returning to Home first.

## 6. New display features (beyond pure visual polish)

These came out of the brainstorming session as genuine UX improvements, not just styling. All operate on data the app already collects — no new Android permissions or data sources.

| Feature | Screen | What it does | Computation |
|---|---|---|---|
| **Suggested contacts** | Team Setup | Shows untagged contacts ranked by call frequency (e.g. "31 calls this month") above the full alphabetical contact list, so tagging your real team takes seconds instead of scrolling your whole phone contacts. | New query: last-30-days call log entries, matched via `PhoneNumberNormalizer` against `ContactsRepository.getAllContactsWithPhoneNumbers()`, excluding already-tagged contacts, grouped by contact and sorted by count desc, top N (e.g. 10). |
| **Select all / Clear all** | Team Setup | Bulk-tags or bulk-untags. Operates on the currently visible (search-filtered) contact set, not the entire phone contact list, so a search first narrows what "Select all" affects. | Existing `toggleTag` called per visible untagged/tagged contact. |
| **A–Z fast-scroll index** | Team Setup | Right-edge letter rail (only shown on "All Contacts", since Tagged/Suggested are expected to be short) jumps the list to the first contact starting with that letter. | Precomputed first-index-per-letter map over the alphabetically sorted "All Contacts" list; `LazyListState.scrollToItem`. |
| **Edit mode for Tagged list** | Team Setup | Tapping "Edit" next to the Tagged header shows a remove (−) control per tagged row and hides Suggested/All Contacts while active, so you can't accidentally tag someone new mid-edit. "Done" exits. | Same underlying `toggleTag(untag)` call as the normal chip toggle, just gated behind an `isEditingTagged` UI state. |
| **Month/range summary strip** | History, Range Detail | Total calls, total talk time, avg calls/day for whatever range/filter is currently on screen. | Sum over the already-fetched day summaries in range; avg = total calls ÷ number of days in range that had ≥1 tagged call (consistent with how `averageShiftSpanSeconds` already excludes inactive days). |
| **Week subtotals** | History (Month view) | A small "WEEK 38 · 62 calls · 4h 10m" header between ISO weeks. | Client-side grouping of the fetched day list by ISO week. |
| **Busiest day / busiest month callout** | History, Range Detail (day list) / Year view | One-line highlight of the single highest-call day (or month, in Year view) in the visible range. | `maxBy { totalCalls }` over the fetched summaries. |
| **Year view** | History | 4th segment alongside Month/Week/Custom. Shows a yearly summary strip + busiest-month callout, then 12 month-rows (name, call count, talk time); tapping a month drills into Month view anchored there. | New aggregation: 12 calls to the existing range-summary computation (Jan 1–Jan 31, Feb 1–Feb 28, …) or one pass producing monthly buckets — fits the existing pure `CallAggregator` pattern, no new ContentProvider query shape. |
| **By-coworker off-hours breakdown + tap-to-filter** | History & Range Detail, "Outside Hours" filter | Above the existing flat off-hours call list, a "BY COWORKER" section shows each tagged coworker's off-hours call count. Tapping a coworker row filters the call list below to just their calls (tap again / an "All" chip clears it). | Client-side grouping of the already-fetched off-hours call list by matched coworker. |

**Explicitly considered and declined this round:** an "Undo" snackbar toast on tag/untag (superseded by the chip design below, which makes accidental single-tap changes visually obvious and reversible with one more tap instead of needing a timed undo), and a calendar heatmap view for History (nice-to-have, not pursued now).

## 7. Screen-by-screen

### 7.1 Home

Top app bar (title + Refresh icon action) → `SummaryCard` with a 2×2 `StatTile` grid (Calls, Talk Time, First Call, Last Call) → footer row (Shift span text + `WarningBadge` for off-hours count, when > 0) → "PER COWORKER (N)" section of `CoworkerRow`s, sorted by call count descending.

### 7.2 Team Setup

Header ("N tagged" badge) → search box → Select all/Clear all chips → **TAGGED** section (fixed ~3-row height, internal scroll if it overflows; "Edit" toggles remove mode) → **SUGGESTED** section (call-frequency ranked, `+ Tag` outline chip) → **ALL CONTACTS** section (alphabetical, flows with the page — no internal scroll box — A–Z rail layered on the card's right edge). Every row uses `ToggleChip` (outline "+ Tag" ↔ filled "Tagged ✓") instead of a checkbox, and the whole row is the tap target.

The search box filters by contact display name (case-insensitive substring) across all three sections live as the user types. While a search query is non-empty, the A–Z rail hides (an alphabetical index over a filtered subset isn't useful) and "Select all"/"Clear all" apply only to the filtered results, per §6/§8.

### 7.3 Settings

**WORKING HOURS** — two tappable time cards (Start/End) side by side; tapping still opens the native Android `TimePickerDialog` (unavoidable without a custom picker, which is out of scope) but the trigger now looks like a deliberate in-app control. **WORKING DAYS** — a row of 7 `DayCircle`s (M–S), tap to toggle. **HOLIDAYS** — an add-row card (date trigger + label field + "Add Holiday" button) followed by a list with a trash icon per entry, replacing the plain "Remove" text button.

### 7.4 History

Top app bar (title + Export CSV outline action) → segmented control **Month / Week / Year / Custom** → period navigation (‹ September 2026 ›) → summary strip card (Calls/Talk Time/Avg-per-day `StatTile`s + busiest-day line) → filter chip row (All/Off Days/Holidays/Outside Hours, restyled `ToggleChip`s, single-select as today) → content area depends on filter:
- **All / Off Days / Holidays:** day cards (date, time span + shift duration, call-count badge, off-hours `WarningBadge` if any) with week-subtotal headers between ISO weeks; off/holiday days render grayed with a status label instead of numbers.
- **Outside Hours:** replaces the day-card list with the by-coworker breakdown (§6) above a flat chronological list of individual off-hours calls (avatar, name, date/time, direction icon, duration).
- **Year mode:** summary strip becomes yearly totals + busiest-month; content area becomes 12 month-rows instead of day cards; tapping a month row navigates into Month view anchored to that month. Filters still apply on top of Year (e.g. Year + Outside Hours = by-coworker breakdown scoped to the whole year).

### 7.5 Range Detail

Same shell as History minus the segmented control (it's always a fixed custom range): filter chip row, then for "All": a `SummaryCard` (Total Calls, Total Talk Time, Avg Shift Span, Busiest Coworker `StatTile`s + holiday/off-day summary line + off-hours `WarningBadge`) followed by the `CoworkerRow` list. Other filters reuse the exact same day-card list / by-coworker + call-list views built for History.

### 7.6 Day Detail

No screen-specific changes — it already renders via the shared `DailySummaryCard`, which becomes the same `SummaryCard` + `StatTile` grid used on Home. Gets a back arrow in its top app bar as part of the nav shell change (§5).

## 8. Explicit decisions made during brainstorming (so implementation doesn't have to re-guess)

- **Theme mode:** follows the system light/dark setting automatically (both palettes in §3.1 are real, not just the light one).
- **Coworker tap in off-hours breakdown:** tapping a coworker filters the call list below to just their calls; tapping again (or an "All" chip) clears the filter.
- **Team Setup sort:** Home/Range coworker lists sort by call count descending (busiest first).
- **Bulk select scope:** "Select all"/"Clear all" act on the currently search-filtered visible set, not the unfiltered full contact list.
- **Avg calls/day:** divides by days-with-≥1-tagged-call in range, matching the existing `averageShiftSpanSeconds` convention, not raw calendar days.

## 9. Out of scope (unchanged from original spec, or explicitly deferred here)

- No in-app custom time/date picker replacing the native Android dialogs (visual wrapper only).
- No calendar heatmap view, no undo-toast (see §6).
- No changes to permission handling, widget behavior, CSV export logic/format, phone-number matching, or on-device-only data storage — all inherited unchanged from `2026-09-16-daily-calls-review-design.md`.
- Actual launcher icon artwork (final exported PNGs/vector XML) is produced during implementation from the approved concept, not finalized pixel-by-pixel in this spec.
