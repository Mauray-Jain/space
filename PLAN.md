# Space: a better Essential Space for Nothing phones

Working name: **Space**. A personal Android app for the Nothing Phone (3a): press the Essential Key, capture anything (voice, text, screen), and it becomes a note, task or reminder. **Everything runs on the phone.** No cloud AI, no account needed, no internet permission, and deleting something really deletes it.

See [RESEARCH.md](RESEARCH.md) for what Essential Space does today and the user wishlist.

---

## 1. Decisions so far

| Topic | Decision |
|---|---|
| Target phone | Nothing Phone (3a) first (Snapdragon 7s Gen 3, 8/12 GB RAM) |
| Audience | Personal; fine if others install it |
| AI | **All on-device**, light, loaded only when needed. No Claude, no cloud, ever. |
| Speech → text | **Moonshine Base** (English) |
| Understanding | **MiniLM** (intent + word tags) trained in `training/`; dates and times by our own rule-based parser. |
| Notes | Stored in the app + optional one-way Markdown folder sync (Obsidian/Syncthing) |
| Tasks / to-dos | In the app only, with notifications |
| Reminders/events | **Automatic** by default with an Undo/Edit notification; "confirm first" is a setting |
| Reminder style | Setting: **Notification** (default) or **Alarm** (rings, full screen, goes through Do Not Disturb) |
| Calendar | Optional, a setting. Written through Android's calendar system, so it works with any calendar on the phone (Google, Outlook, Proton…) |
| Backup | **No cloud backup, no sign-in.** Manual export/import of everything as one file (you choose where to save it). |
| Search | Keyword, typo-tolerant, over notes + transcripts + screenshot text (v1); by meaning in v2 |
| Key gestures | Nothing's defaults: press = capture screen (+ text/voice, tap to skip the screenshot), long-press = voice, double = open |
| Stock Essential Space | You disable it yourself (ADB) for now; official remapping later |
| **Deletion** | **Non-negotiable: deleting removes the note, audio, image, search index entry, Markdown file, alarms and calendar event.** Export files you saved yourself, and Markdown copies already synced to other devices, are outside the app and aren't touched. |

---

## 2. On-device stack

Rule: every model is **loaded only when it's used and unloaded right after**, and nothing heavy runs in the background.

Fully open source, no Google ML Kit, nothing sent anywhere. The app has **no INTERNET permission**. Models ship inside the APK (a Gradle task downloads them at build time; they're not committed). One AI engine: **ONNX Runtime**, a single `onnxruntime-android` build (Maven Central) that runs every model directly, so the APK has exactly one `libonnxruntime.so`.

| Job | Model / tool | Size | In v1? |
|---|---|---|---|
| Speech → text | **Moonshine Base** (English, int8, onnx-community export), run directly on ONNX Runtime | ~63 MB | Yes |
| ↳ lighter option | Moonshine Tiny | ~28 MB | Later: v1 has no downloads, and shipping both is ~90 MB |
| Trim silence / detect speech | Silero VAD | ~2 MB | Yes |
| Understanding what you meant (note / task / reminder / event / list / question) + the important words (title, list items, search words) | **MiniLM-L6** (all-MiniLM-L6-v2, Apache-2.0) fine-tuned for intent + word tagging, int8, loaded only while the capture sheet is open. Text cleanup (fillers, number words, self-corrections) stays rule-based. | ~23 MB | Yes |
| Dates, times, repeats, fuzzy times | **Own rule-based parser** (port of the *chrono* library + our additions) | ~0 | Yes |
| Phones, emails, URLs, money | **libphonenumber** + small regexes (not `Patterns`, so the rules run as JVM unit tests) | ~1 MB | Yes |
| Text in screenshots & photos | **PaddleOCR**: PP-OCRv3 detection + PP-OCRv5 English recognition (monkt/paddleocr-onnx, Apache-2.0) on ONNX Runtime | ~10 MB | Yes |
| QR codes / barcodes | **ZXing** core (pure Java, so it runs in JVM tests) | <1 MB | Yes |
| Tagging captures | Rules on OCR text + the source app's name | ~0 | Yes |
| Names/companies on business cards | Heuristic (largest/top line) | ~0 | Yes |
| Search | SQLite **FTS5 with trigram tokenizer**, via Room's `BundledSQLiteDriver` (Android's own SQLite on API 31 is too old for trigram) | ~1–2 MB | Yes |
| Other languages | Moonshine / Whisper language models | ~30–75 MB each | v2 |
| Search by meaning | Small embedding model | ~25 MB | v2 |

**Base install: ~100 MB of models** (MiniLM included).

MiniLM is trained on synthetic sentences generated in `training/` (see its README). The test set in `testdata/` is **never** used for training; it only judges the model.

### How typo-tolerant search works
The trigram index cuts text into 3-letter pieces ("dentist" → den, ent, nti, tis, ist), so any part of a word can be found. A normal search needs *every* piece to match, so the typo "dentsit" finds nothing. So: if the normal search finds nothing, search again for items sharing *any* piece and rank them by how many they share. Searches shorter than 3 letters use plain `LIKE`.

### How a capture flows
```
voice ─► Silero VAD ─► Moonshine ─┐
text ─────────────────────────────┼─► rules (intent + slots) ─► date/time parser ─► note / task / reminder / event / list / question
screen/photo ─► PaddleOCR + ZXing ┘
```

### "Ask about your stuff" (v1, no LLM)
"What's on my grocery list?" → the rules see a question word and a list name → search finds the matching list/notes → show them as a checklist. No generated answer, just the right items.

---

## 3. Features

### v1
**Core**
- Capture: Essential Key (accessibility service), Quick Settings tile, share sheet, home widget
- Voice notes (Moonshine), text notes, screen captures with crop
- Notes, tasks and reminders, all **editable** after creation (wishlist #2)
- Plain notes and to-dos with no screenshot or date (#3)
- Organization: tags, sorting, pin, archive, bulk delete (#5)
- Typo-tolerant search across everything (#6)
- **Save the source link** of a screen capture and make links clickable (#7). Read the URL from the browser via the accessibility service, plus QR/URL detection in the image.
- Optional calendar sync, with deletes propagating so nothing comes back (#8)
- Real deletion everywhere

**Reminders & tasks**
- Reminder style: Notification (default) or Alarm, in settings
- Snooze 30 min / 1 h / Tomorrow 9 AM from the notification
- Recurring reminders (daily/weekly/custom)
- Subtasks with checkboxes
- One shopping list: grocery items from any capture land on it, auto-grouped by section
- Morning summary of tasks with no time (optional)

**Capture extras**
- Pause music while recording (audio focus), resume after
- Long (scrolling) screenshots
- Business card → contact: PaddleOCR + libphonenumber + name rules fill a new-contact form
- Ask about your own stuff ("what's on my grocery list?")
- Scrollable widget showing several upcoming items

### v2
- Web / PC access and cross-device sync (#4)
- More languages (#9): Moonshine/Whisper language packs
- Location-based reminders ("when I'm at the pharmacy"). Needs a way to save places without a map or network.
- Integrations: Notion, Obsidian (beyond Markdown export), OneNote, Google Keep (#10)
- Moving your data to a new phone
- Search by meaning

---

## 4. Architecture

**Stack:** Kotlin, Jetpack Compose, Room (with the bundled SQLite driver, FTS5), WorkManager, Hilt. Min SDK 31.

- **Capture service:** `AccessibilityService` with key filtering for the Essential Key, `takeScreenshot()` for captures, scroll-and-stitch for long screenshots, reads the current URL from browsers.
- **Processing:** one short WorkManager job per capture, so it's processed immediately and not only when the app is opened (a top Essential Space complaint).
- **Background work, all of it:** the accessibility service (listens for the key only), one short job per capture, alarms, re-arming alarms after a reboot, and the optional morning summary. Nothing else. Any new background job needs a reason here first.
- **Reminders:** `AlarmManager` exact alarms (`USE_EXACT_ALARM`) for both styles, re-armed after a reboot. Notification actions: Done / Snooze 30m / Snooze 1h / Tomorrow 9 AM / Undo. The Alarm style uses a full-screen intent and an alarm sound.
- **Calendar:** `CalendarContract`, opt-in. Each item stores the event ID it created, so deleting the item deletes the event.
- **Deletion:** one `delete(item)` path removes the DB row, FTS entry, files, Markdown file, alarms and calendar event in one go, then runs `PRAGMA wal_checkpoint(TRUNCATE)` so deleted text doesn't linger in the WAL file. SQLite `secure_delete` is on.
- **Markdown sync:** one-way, app → a folder you choose. Edits made in the folder aren't read back.
- **Export / import:** Settings → Export writes one `.zip` (notes as Markdown, tasks/reminders as JSON, audio and images) through Android's file picker, so you choose the location. An optional password encrypts the zip. Import reads the same file back, skipping items that already exist.

---

## 5. Milestones

Every milestone is test-first: write the failing tests, then the code.

1. **Skeleton:** Compose app, Room + FTS5 trigram schema (check the bundled driver works), quick-entry sheet, QS tile, share target, the single delete path
2. **Notes & tasks:** editor, tags/pin/archive/bulk delete, subtasks, shopping list, typo-tolerant search
3. **Reminders:** alarms, Notification/Alarm style setting, snooze, recurring, Undo notification, reboot re-arm
4. **Understanding:** MiniLM (intent + word tags) + date/time parser, auto-filing, "ask about your stuff"; passes `testdata/understanding_test.jsonl` (rows marked `known_weak` are reported, not required).
5. **Voice:** Moonshine + VAD on the shared ONNX Runtime, pause music while recording
6. **Screen capture:** accessibility service, Essential Key, crop, OCR/labels, source links, long screenshots
7. **Extras:** business cards, calendar sync, scrollable widget, Markdown folder sync (then open a GitHub issue for the "synced copies survive deletion" known issue), export/import
8. **Polish:** Nothing-style theme, settings, battery and size checks
9. **v2 items**

---

## 6. Decisions log

- **No Gemini, no cloud, ever.** The app stays 100% local; screenshots use on-phone OCR only.
- **No INTERNET permission.** Language packs moved to v2, so v1 never downloads anything.
- ~~Understanding is rules first.~~ **Changed 25 Nov 2026 (owner's decision): MiniLM replaces the intent rules.** The rules passed the test set, but a trained model generalises to phrasings no rule anticipated. Dates and times stay rule-based (a small tagger can't do date arithmetic). FunctionGemma dropped.
- **Location reminders → v2.** Picking a place needed a map, and maps need the network.
- **Reminder style:** Notification by default, Alarm as a setting.
- **Snooze:** 30 min, 1 h, Tomorrow 9 AM.
- **Shopping list:** one list, auto-grouped by section (produce, dairy…). Per-store lists maybe later.
- **Undo:** the Undo/Edit notification stays until swiped away. "Confirm first" turns it into Add / Edit / Discard.
- **Backup:** Drive backup and Google sign-in dropped. Manual export/import only.
- **Markdown sync** is one-way. Copies already synced elsewhere survive deletion (known issue).
- **Package name:** `space.app`.
- **Main screen:** one feed of everything, newest first, pinned on top, with filter chips (All / Notes / Tasks / Reminders / Shopping).
- **Tags:** `#hashtags` in the text (typed or spoken) become tags; also editable as chips in the editor.
- **Done tasks and fired one-off reminders** stay in the list, struck through, until you delete or archive them.
- **Quick entry:** type chips (Note / Task, Reminder from M3). From M4 the rules pre-select one and the chips become an override.
- **Testing:** unit tests run on the JVM; instrumented tests (DB, delete, alarms, UI) run on an emulator in GitHub Actions on every push.
- **Working while you're away:** when something can only be checked on the phone, or a CI step stays broken, it's listed in [PHONE_CHECKS.md](PHONE_CHECKS.md) and work moves on.
- **MiniLM training** runs in the cloud dev container (CPU, ~35 min), on sentences generated in `training/` only.
- **M4 result:** the rules passed all 253 required rows (13 of 34 `known_weak`). **MiniLM (25 Nov 2026) also passes all 253, and 17 of 34 `known_weak`**: lists without commas, word pairs ("salt and pepper"), "out of coffee", gifts as tasks. Four training rounds, each adding generated sentences (never the test set). `training/probe.py` checks 32 phrasings outside both the templates and the test set: 26/32.
- **Events are saved as reminders** until calendar sync (M7).
- **Voice without sherpa-onnx (M5):** sherpa-onnx isn't on Maven Central, its GitHub downloads are blocked in the dev container, and it bundles its own ONNX Runtime. Moonshine's encoder + merged decoder and Silero VAD run directly on `onnxruntime-android` (~150 lines), tested on the JVM and on the emulator.
- **Voice details:** recordings over ~20 s are cut at pauses before transcribing; under 1 s of speech is discarded; the WAV is kept in the item's folder (playable in the editor, deleted with it).
- **Screen capture (M6):** the Essential Key's key code isn't documented, so Settings learns it ("press the key now"). Press = screenshot, hold (0.5 s) = voice, double (0.3 s) = open. Screenshots stay in the app's cache until saved, then move to the item's folder; never the Gallery. One expedited WorkManager job per capture reads it (OCR, QR, labels) and files a flyer as a reminder with Undo/Edit. Screenshot text is a column in the search index.
- **Long screenshots** scroll with an accessibility gesture, up to 5 screens, and are joined at their overlap; toolbars that don't move are kept once.
- **M7 extras:** calendar sync writes only events (noun + time), keeps each event's id, deletes it with the item, and remembers deletes made while sync was off. Markdown sync writes `<slug>-<id>.md` with front matter into a folder picked through Android's file picker. Export is a zip4j `.zip` (AES-256 with a password) with `items.json`, Markdown and files; import skips items already present. The widget uses the platform's `RemoteCollectionItems` (no library) and is redrawn on every change. Business cards open Android's pre-filled contact form; photos shared to Space are read like screenshots.
- **M8 checks (25 Nov 2026):** release APK for the phone (arm64, R8 + resource shrinking) is **98.9 MB**: ONNX Runtime 36 MB, Moonshine 46 MB compressed (63 MB raw), OCR 9 MB, VAD 2 MB, code 3.4 MB. Next lever: a minimal ONNX Runtime build with only the operators these models use (needs building ORT from source). Background work is exactly the CLAUDE.md list: the key service, one job per capture, reminder alarms, the boot re-arm and the optional morning summary (one inexact daily alarm); the widget redraws only when items change.
- **Theme:** Nothing-style monochrome with a red accent and monospace headings (Nothing's dot-matrix font isn't open); Light / Dark / Like the system, independent of the phone (wishlist).
- **"Confirm first" setting** waits for captures that have no sheet (voice via the key in M5, screenshots in M6). Typed captures already show what they'll become before Save, and the chips override it.
- **Bundled SQLite checked (M1):** `androidx.sqlite:sqlite-bundled` 2.7.1 is SQLite 3.50.1 with FTS5 and `SECURE_DELETE` compiled in; the trigram tokenizer works.

Nothing open right now.

## 7. Risks

- Essential Key interception is undocumented and may break with Nothing OS updates.
- The accessibility service causes friction with some banking apps. Keep it minimal, with a toggle.
- Reading browser URLs via accessibility varies by browser.
- Android battery optimization: ask for an "unrestricted" exemption.
- ~~sherpa-onnx ships its own ONNX Runtime.~~ Gone in M5: no sherpa-onnx, one `onnxruntime-android`.
- `libonnxruntime.so` is 36 MB on arm64, the largest part of the APK. A minimal ORT build (only the operators used) is the next size step.
- ~~The bundled SQLite driver must include FTS5 with the trigram tokenizer.~~ Checked in M1: it does.
