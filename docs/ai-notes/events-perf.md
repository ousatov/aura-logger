# AI notes: #32 rows switch to seen, #33 Events list stutters during fast scrolling

Raw material for `AI_COMMUNICATION.md`.

## Tools
- Claude Code (desktop app, side session), model `claude-opus-5-5`.
- Pixel 3a (Android, 720x1440 override) over adb; `dumpsys gfxinfo`, `input swipe`, `uiautomator dump`.

## Key prompts
- "Do #32 first; for #33 measure first and write the numbers (debug vs release, recomposition counts) in the PR."

## Where AI helped
- #32: `MutableStateFlow<Set<String>>` of reported ids combined with the cached `PagingData`, so rows turn seen without a reload; existing tests stayed green unchanged.
- #33: scripted a repeatable fling benchmark (200 fresh unseen events, 32 fast swipes) and a temporary, uncommitted counter/timer probe in place of Layout Inspector, which cannot be driven from the CLI.
- Found that the stutter is a debug-build effect: debug 28–47 % janky frames, release 0.1–2 %.

## Where AI was wrong or had to iterate
- The first device setup sent `BACK` to dismiss the keyboard, which left the app; the next taps landed on the launcher. Found from the UI dump, the setup was repeated.
- Tried `prefetchDistance = 2 * PAGE_SIZE`: no measurable difference (cold run 2.4 % vs 1.9 %, warm 0.1 % vs 0.1 %), so it was reverted instead of shipped "just in case".
- Single runs are noisy (debug 30–47 % for the same code); kept only the change whose effect showed up in repeated release runs.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug :demo:testDebugUnitTest`: passed, no lint findings in `events/`.
- On device: rows scrolled into view lose the "New" badge within ~1 s without the list jumping.
- Release was signed locally with the debug key only to install it; not committed.
- Not verified: Layout Inspector itself, other devices, lists above 1000 events.

## AI version vs final version
```kotlin
for (index in 0 until items.itemCount) {
    when (val entry = items.peek(index)) { /* one item(...) per event */ }
}
```
```kotlin
// headers stay single stickyHeader(...); each run of events between them is one
items(count, key = { peek(start + it)... }, contentType = { EVENT_CONTENT_TYPE }) { ... }
```
Keys are now computed lazily for laid-out rows only; list rebuild in release 0.35 → 0.15 ms (warm, 1000 events).

## Time
- 23:25–00:05 (about 40 min, most of it measurement); writing the probe and benchmark by hand would have taken longer.
