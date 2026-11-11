# AI notes: #34 Snackbars queue up and show stale messages

Raw material for `AI_COMMUNICATION.md`.

## Tools
- Claude Code (desktop app), model `claude-sonnet-5-5`.

## Key prompts
- Ticket brief: fix queued snackbars with a `core/ui` helper, use it in actions and config sections.

## Where AI helped
- Found that both sections already dismissed the current snackbar, but `collect` waited for the suspended `showSnackbar`, so effects still queued; switched to `collectLatest` plus a shared `showLatest` helper.

## Where AI was wrong or had to iterate
- Nothing needed iteration. The optional coalescing of repeated track messages was skipped on purpose.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug :demo:testDebugUnitTest` passed.
- Not verified on a device: 10x "Track Event" then "Clear All".

## AI version vs final version
```kotlin
// Before
viewModel.effects.collect { effect ->
    snackbarHostState.currentSnackbarData?.dismiss()
    snackbarHostState.showSnackbar(message)
}
```
```kotlin
// Final
viewModel.effects.collectLatest { effect ->
    snackbarHostState.showLatest(message)
}
```
`collect` blocked on the suspended snackbar, so later effects waited; `collectLatest` cancels the old one.

## Time
- About 10 minutes; roughly 50 minutes saved against the 1 hour estimate.
