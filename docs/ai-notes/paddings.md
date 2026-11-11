# AI notes: #23 Demo paddings

Raw material for `AI_COMMUNICATION.md`.

## Tools
- Claude Code (desktop app), model `claude-sonnet-5-5`.

## Key prompts
- Ticket #23: add paddings and spacing to the screens in `main/`, `stats/`, `config/` and the debug section; UI only.

## Where AI helped
- Added `Dimens` (shared spacing values) and applied them in the shell and sections with one scripted edit.

## Where AI was wrong or had to iterate
- First idea was padding in the shell for all content; that would double the padding of `ActionsSection` (another session adds its own). Moved horizontal padding into each section; the shell only adds vertical padding and section gaps.

## Verification
- `./gradlew :demo:assembleDebug :demo:assembleRelease :demo:lintDebug`.
- Not checked on a device or emulator.

## AI version vs final version
```kotlin
Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { ... }
```
```kotlin
Column(scrollModifier, verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)) { ... }
```
Horizontal padding lives in the sections to avoid doubling with parallel tickets.

## Time
- Short session (about 15 minutes); layout edits are quick by hand too, saving is small.
