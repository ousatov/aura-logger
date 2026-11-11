# AI notes: #22, #25, #24 Events tab UX (seen indicator, day headers, seen while scrolling)

Raw material for `AI_COMMUNICATION.md`. Keep it short (about 40 lines), specific and honest.

## Tools
- Claude Code (desktop app, side session started by the main session), model `claude-opus-5-5`.

## Key prompts
- Main session brief: implement #22 + #25 in one PR (contract change in its own first commit), then #24 in a second PR stacked on the first; no unit tests; verify once per PR.

## Where AI helped
- Day headers: `insertSeparators` before `cachedIn`, so "Today"/"Yesterday" are recomputed from the app
  `Clock` + `ZoneProvider` on every reload after `changes`; the day key is the `DD/MM/YYYY` prefix of the
  already formatted timestamp, so headers and rows always agree on the zone.
- Kept the "shown" hint outside the `LazyColumn`, so list indices still equal paging indices for seen reporting.

## Where AI was wrong or had to iterate
- First draft keyed sticky headers by `dayKey` alone and claimed in KDoc it is unique; a time zone change
  between page loads can repeat a day and crash `LazyColumn` on a duplicate key. Key now includes the id of the
  event below the header; the KDoc claim was removed (folded into the contract commit before pushing).
- First draft used `LocalDate.ofInstant` / `toJavaInstant`; replaced with `Instant.ofEpochMilli(...).atZone(...)`
  to match the rest of the app and stay safe for `minSdk 26`.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug` per PR: green, no lint findings in `events/`.
- Not verified on a device by this session: visual look of badges/headers, sticky header behaviour.

## AI version vs final version
```kotlin
stickyHeader(key = "day_${entry.dayKey}") { DayHeaderRow(entry.label) }
```
```kotlin
stickyHeader(key = headerKey(items, index, entry), contentType = "header") { items[index]; DayHeaderRow(entry.label) }
```
Unique keys and `items[index]` so that headers also trigger page loads.

## Time
- 22:50–23:20 for #22/#25 (git timestamps); #24 see below. Estimated 1 h of manual work saved.
