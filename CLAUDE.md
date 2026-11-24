# Space

A local-first Android app for the Nothing Phone (3a): a better Essential Space. You capture voice, text or the screen with the Essential Key, and it becomes a note, task or reminder. Everything runs on the phone.

- Plan, decisions and milestones: [PLAN.md](PLAN.md)
- Edge cases (doubles as the milestone 4 test set): [EDGE_CASES.md](EDGE_CASES.md)
- Background research: [RESEARCH.md](RESEARCH.md)

## Hard rules

- **Nothing leaves the phone.** No cloud AI, no analytics, no network calls. The only exceptions are the optional Google Calendar write (through Android's `CalendarContract`) and language-pack downloads the user starts.
- **Deletion really deletes.** Every delete goes through the single `delete(item)` path, which removes the DB row, FTS entry, files, Markdown file, alarms/geofences and calendar event. Never add a second delete path or a soft-delete/trash.
- **Models stay light.** Load a model only while it's used and unload it after. Nothing runs in the background. Any new model needs a size/RAM justification in PLAN.md first.
- One ML engine: ONNX Runtime (sherpa-onnx for voice). No Google ML Kit.

## Code style

- **Occam's razor: try the simplest thing that works first.** Plain functions before classes, a rule before a model, the platform API before a library, one module before several. Add complexity only when a real case (ideally an EDGE_CASES.md row) shows the simple version fails, and say which case in the commit.
- **No speculative abstractions.** No interfaces with one implementation, no plugin systems, no "for later" config. Three similar lines beat a premature helper.
- **No micro-optimisations** without a measurement on the actual phone showing a problem.
- **Keep the architecture clean:** capture → process (rules/models) → act (note/task/reminder/calendar). Each layer only talks to the next. UI never calls models or the DB directly; it goes through a ViewModel.
- Kotlin + Jetpack Compose, Room, WorkManager, Hilt. Min SDK 31.
- Prefer deleting code over adding it.

## Workflow

- Work milestone by milestone, in the order in PLAN.md §5.
- A milestone is done when its features work on the phone/emulator and its tests pass. For milestone 4, that means the EDGE_CASES.md cases.
- Keep PLAN.md and EDGE_CASES.md up to date when a decision changes.
