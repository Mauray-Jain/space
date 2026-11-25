# Space

A local-first Android app for the Nothing Phone (3a): a better Essential Space. You capture voice, text or the screen with the Essential Key, and it becomes a note, task or reminder. Everything runs on the phone.

- Plan, decisions and milestones: [PLAN.md](PLAN.md)
- Edge cases: [EDGE_CASES.md](EDGE_CASES.md). The milestone 4 test set built from them: [testdata/](testdata/README.md)
- Background research: [RESEARCH.md](RESEARCH.md)

## Hard rules

- **Nothing leaves the phone.** No cloud AI, no analytics, no network calls. The manifest has **no INTERNET permission**. The only thing written outside the app is the optional calendar write (through Android's `CalendarContract`).
- **Deletion really deletes.** Every delete goes through the single `delete(item)` path, which removes the DB row, FTS entry, files, Markdown file, alarms and calendar event, then checkpoints the WAL. Never add a second delete path or a soft-delete/trash.
- **Models stay light.** Load a model only while it's used and unload it after. Any new model needs a size/RAM justification in PLAN.md first.
- **Background work only where it's truly needed, and kept tiny:** the accessibility service (listens for the key only), one short WorkManager job per capture, alarms, the boot re-arm, the optional morning summary. No model and no long-running work in the background. Any new background job needs a reason in PLAN.md §4 first.
- One ML engine: a single ONNX Runtime build (`onnxruntime-android`) that runs every model, voice included. No Google ML Kit, no LiteRT, no second runtime.

## Code style

- **Occam's razor: try the simplest thing that works first.** Plain functions before classes, a rule before a model, the platform API before a library, one module before several. Add complexity only when a real case (ideally an EDGE_CASES.md row) shows the simple version fails, and say which case in the commit.
- **No speculative abstractions.** No interfaces with one implementation, no plugin systems, no "for later" config. Three similar lines beat a premature helper.
- **No micro-optimisations** without a measurement on the actual phone showing a problem.
- **Keep the architecture clean:** capture → process (rules/models) → act (note/task/reminder/calendar). Each layer only talks to the next. UI never calls models or the DB directly; it goes through a ViewModel.
- Kotlin + Jetpack Compose, Room (bundled SQLite driver for FTS5), WorkManager, Hilt. Min SDK 31.
- Prefer deleting code over adding it.

## Workflow

- Work milestone by milestone, in the order in PLAN.md §5.
- **Write the tests first, then the code.** Every feature or fix starts with a failing test: unit tests for rules and parsers, instrumented tests for the DB, delete and alarms.
- A milestone is done when its features work on the phone/emulator and its tests pass. For milestone 4, that means `testdata/understanding_test.jsonl` passes (rows marked `known_weak` are reported, not required). Never tune rules or training data just to chase a `known_weak` row, and never train on the test set.
- **Understanding is MiniLM** (trained in `training/`, committed in `models/`); dates stay rule-based. Retrain after changing intents or the generator, and re-run the test set.
- Keep PLAN.md and EDGE_CASES.md up to date when a decision changes.
