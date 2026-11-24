# Space: a better Essential Space for Nothing phones

Working name: **Space**. A personal Android app for the Nothing Phone (3a): press the Essential Key, capture anything (voice, text, screen), and it becomes a note, task or reminder. **Everything runs on the phone.** No cloud AI, no account needed, and deleting something really deletes it.

See [RESEARCH.md](RESEARCH.md) for what Essential Space does today, what models similar apps use, and the user wishlist.

---

## 1. Decisions so far

| Topic | Decision |
|---|---|
| Target phone | Nothing Phone (3a) first (Snapdragon 7s Gen 3, 8/12 GB RAM) |
| Audience | Personal; fine if others install it |
| AI | **All on-device**, light, loaded only when needed. No Claude, no cloud by default. |
| Speech → text | **Moonshine** |
| Notes | Stored in the app + optional Markdown folder sync (Obsidian/Syncthing) |
| Tasks / to-dos | In the app only, with notifications |
| Reminders/events | **Automatic** by default with an Undo/Edit notification; "confirm first" is a setting |
| Google Calendar | Optional, a setting (written through Android's calendar system) |
| Backup | **No cloud backup, no Google sign-in.** Manual export/import of everything as one file (you choose where to save it). |
| Search | Keyword, typo-tolerant, over notes + transcripts + screenshot text (v1); by meaning in v2 |
| Key gestures | Nothing's defaults: press = capture screen (+ text/voice, tap to skip the screenshot), long-press = voice, double = open |
| Stock Essential Space | You disable it yourself (ADB) for now; official remapping later |
| **Deletion** | **Non-negotiable: deleting removes the note, audio, image, search index entry, Markdown file, alarms and calendar event.** Export files you saved yourself are outside the app and aren't touched. |

---

## 2. On-device model stack

Rule: every model is **loaded only when it's used and unloaded right after**, nothing runs in the background, and the big optional ones are **downloaded only if you turn the feature on**.

Fully open source, no Google ML Kit, nothing sent anywhere. Everything uses one AI engine: **ONNX Runtime** (through sherpa-onnx for voice).

| Job | Model / tool | Size | Always installed? |
|---|---|---|---|
| Speech → text | **Moonshine Base** (English), via sherpa-onnx | ~60–70 MB | Yes |
| ↳ lighter option | Moonshine Tiny | ~30 MB | Setting |
| ↳ other languages | Moonshine language models (Arabic, Chinese, Japanese, Korean, Ukrainian, Vietnamese), or Whisper base multilingual for the rest | ~30–75 MB each | Download on demand |
| Trim silence / detect speech | Silero VAD | ~2 MB | Yes |
| Dates, times, repeats, fuzzy times | **Own rule-based parser** (port of the *chrono* library + our additions) | ~0 | Yes |
| Phones, emails, URLs, money | Android `Patterns` + **libphonenumber** | ~1 MB | Yes |
| Text in screenshots & photos | **PaddleOCR mobile** (PP-OCR) on ONNX Runtime | ~10–15 MB | Yes |
| QR codes / barcodes | **ZXing** (zxing-cpp) | <1 MB | Yes |
| Tagging captures | Rules on OCR text + the source app's name | ~0 | Yes |
| Names/companies on business cards | Heuristic (largest/top line), later optional **GLiNER** small | 0 / ~50–150 MB | Optional |
| Understanding what you meant (note / task / reminder / event / list / question) + marking the important words (title, items, time phrase, place) | **MiniLM-L6** fine-tuned for intent + slot tagging, int8, on ONNX Runtime. Rules handle negation guards and override the model when they're certain. | ~25 MB | Yes |
| ↳ fallback if MiniLM misses too much in tests | FunctionGemma 270M | ~150–300 MB | Only if needed |
| Search | SQLite **FTS5 with trigram tokenizer** (typo-tolerant) | 0 (built in) | Yes |
| Search by meaning (v2) | Small embedding model (e.g. MiniLM ~25 MB) | ~25 MB | v2, optional |

**Base install: ~100–120 MB of models.** MiniLM training: a few thousand synthetic labelled sentences generated from EDGE_CASES.md patterns, trained on a laptop, exported to ONNX int8. Retrain whenever an action is added. Edge cases and how each is handled: [EDGE_CASES.md](EDGE_CASES.md). With FunctionGemma: ~250–400 MB.

Not usable on the 3a: Gemini Nano (Nothing isn't on Google's supported list), Nothing's own AI (no SDK). No cloud AI at all, not even as an opt-in: everything stays local.

### How a capture flows
```
voice ─► Silero VAD ─► Moonshine ─┐
text ─────────────────────────────┼─► MiniLM (intent + slots) + rules ─► date/time parser ─► note / task / reminder / event / list / question
screen/photo ─► PaddleOCR + ZXing ┘          │ (not sure? only if MiniLM fails the M4 tests)
                                             └─► FunctionGemma (if installed) ─► same
```

### "Ask about your stuff" (v1, no LLM)
"What's on my grocery list?" → MiniLM tags it as a question (and the slot "grocery list"), rules back it up → search finds the matching list/notes → show them as a checklist. No generated answer, just the right items. A generated answer (FunctionGemma or a slightly larger local model) can come later if needed.

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
- Optional Google Calendar sync, with deletes propagating so nothing comes back (#8)
- Real deletion everywhere

**Reminders & tasks**
- Snooze 30 min / 1 h from the notification
- Recurring reminders (daily/weekly/custom)
- Location-based reminders ("when I'm at the pharmacy"), using Android geofencing
- Subtasks with checkboxes
- Shared shopping list: grocery items from any capture land on one list
- Morning summary of tasks with no time (optional)

**Capture extras**
- Pause music while recording (audio focus), resume after
- Long (scrolling) screenshots
- Business card → contact: PaddleOCR + libphonenumber/Patterns + name rules fill a new-contact form
- Ask about your own stuff ("what's on my grocery list?")
- Scrollable widget showing several upcoming items

### v2
- Web / PC access and cross-device sync (#4)
- Better transcription for more languages (#9): more Moonshine/Whisper language packs
- Integrations: Notion, Obsidian (beyond Markdown export), OneNote, Google Keep (#10)
- Moving your data to a new phone
- Search by meaning
- FunctionGemma fine-tuned on our own intents (if the rules fall short)

---

## 4. Architecture

**Stack:** Kotlin, Jetpack Compose, Room (on SQLite with FTS5), WorkManager, Hilt. Min SDK 31.

- **Capture service:** `AccessibilityService` with key filtering for the Essential Key, `takeScreenshot()` for captures, scroll-and-stitch for long screenshots, reads the current URL from browsers.
- **Processing:** a WorkManager job per capture, so it's processed immediately and not only when the app is opened (a top Essential Space complaint).
- **Reminders:** `AlarmManager` exact alarms, re-armed after reboot; geofences for location reminders; notification actions Done / Snooze 30m / Snooze 1h / Undo.
- **Calendar:** `CalendarContract`, opt-in. Each item stores the event ID it created so deleting the item deletes the event.
- **Deletion:** one `delete(item)` path removes the DB row, FTS entry, files, Markdown file, alarms/geofences and calendar event. SQLite `secure_delete` is on.
- **Export / import:** Settings → Export writes one `.zip` (notes as Markdown, tasks/reminders as JSON, audio and images) through Android's file picker, so you choose the location (phone storage, USB, any app). An optional password encrypts the zip. Import reads the same file back, skipping items that already exist. No network involved.

---

## 5. Milestones

1. **Skeleton:** Compose app, Room + FTS5 schema, quick-entry sheet, QS tile, share target, the single delete path
2. **Notes & tasks:** editor, tags/pin/archive/bulk delete, subtasks, shopping list, search
3. **Reminders:** alarms, snooze, recurring, Undo notification, reboot re-arm
4. **Understanding:** MiniLM intent + slots, rules, date/time parser, auto-filing, "ask about your stuff"; passes the EDGE_CASES.md test set
5. **Voice:** Moonshine + VAD, pause music while recording
6. **Screen capture:** accessibility service, Essential Key, crop, OCR/labels, source links, long screenshots
7. **Extras:** location reminders, business cards, Calendar sync, scrollable widget, Markdown folder sync, export/import
8. **Polish:** Nothing-style theme, settings, battery and size checks
9. **v2 items**

---

## 6. Open questions (for the next grilling round)

Decided:
- **No Gemini, ever.** The app stays 100% local; screenshots use on-phone OCR only.
- **Languages:** English Moonshine built in; other language packs downloadable in settings.
- **Snooze:** 30 min, 1 h, Tomorrow 9 AM.
- **Shopping list:** one list, auto-grouped by section (produce, dairy…). Per-store lists maybe later.
- **Undo:** the Undo/Edit notification stays until swiped away. "Confirm first" turns it into Add / Edit / Discard.

- **Backup:** Drive backup and Google sign-in dropped. Manual export/import only.

Nothing open right now.

## 7. Risks

- Essential Key interception is undocumented and may break with Nothing OS updates.
- The accessibility service causes friction with some banking apps. Keep it minimal, with a toggle.
- Reading browser URLs via accessibility varies by browser.
- Android battery optimization: ask for an "unrestricted" exemption.
