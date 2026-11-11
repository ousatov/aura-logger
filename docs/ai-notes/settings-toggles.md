# AI notes: #36 Settings toggles for navigation events and clearing unseen events

## Tools
- Claude Code (desktop app), model `claude-sonnet-5-5`.

## Key prompts
- Ticket session brief: implement issue #36 within `config/`, `MainViewModel` and `strings_config.xml`; contract changes in a separate first commit.

## Where AI helped
- Persistence of both switches on tab leave / lifecycle stop: `DisposableEffect` + `LifecycleEventEffect` send `ScreenHidden`; the ViewModel serialises saves with a `Mutex` and re-checks inside the lock so tab switch + stop do not save twice.
- Kept the SDK protection flags other than `onClearAll` intact by deriving the new config from the stored one (also in the existing Save path).

## Where AI was wrong or had to iterate
- First plan was a compile-clean contract commit; the second `DemoConfigRepository` fake in `ConfigViewModelSaveTest` was missed until grep, then amended into the first commit.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug :demo:testDebugUnitTest` passed.
- Not verified on a device; no new tests added (per brief).

## AI version vs final version
Not applicable: the first version was kept apart from the amend above.

## Time
- Single session, roughly 30 minutes; estimate about 1.5 h saved against the ticket's 2 h.
