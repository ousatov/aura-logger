# AI notes: #21 Clear All message does not say unseen events are kept

## Tools
- Claude Code (desktop app), model `claude-sonnet-5-5`.

## Key prompts
- Ticket session brief: implement GitHub issue #21 in the actions package only, contract change in its own commit, no tests.

## Where AI helped
- Read the issue and the three actions files, added `kept: Int?` to `Effect.Cleared`, read `getStats().totalCount` after the clear without failing the clear, added plurals and the dialog text, and applied the paddings from the issue comment.

## Where AI was wrong or had to iterate
- Nothing needed correction; the build passed on the first run.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug` passed.
- Not run on a device; no unit tests written (per ticket rules).

## AI version vs final version
Same as first version; the first contract commit used `kept = null` as a placeholder so the module compiled, the second commit reads the real value.

## Time
- Single short session (see git timestamps); roughly 10 minutes spent versus about 30 by hand.
