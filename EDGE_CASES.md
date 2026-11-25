# Edge cases: does the local stack hold up?

Stack: Moonshine + Silero VAD on ONNX Runtime → own rules for intent and slots + own date/time parser (+ libphonenumber, Android Patterns) · PaddleOCR + ZXing for images. MiniLM is added only if the rules fail the test set.

Legend: ✅ handled by the stack · 🔧 handled by a specific rule we must write · 🙋 the app asks / you fix it · ⚠️ works, but known to be weak · ❌ not handled in v1

This list is the source of the **milestone 4 test set**, [testdata/understanding_test.jsonl](testdata/understanding_test.jsonl). That file has every row below plus harder variants built to break the rules (format: [testdata/README.md](testdata/README.md)). The test set is **never used for training**. If MiniLM is ever needed, it gets its own generated set in `training/`.

---

## 1. Times & dates

| Input | Expected | Status | How |
|---|---|---|---|
| "remind me at 6 to call mom" | Today 18:00 (or tomorrow if 6 pm has passed) | 🔧 | Bare hour 1–6 → pm unless "am"/"morning" |
| "at 6" when it's 5:50 pm | 18:00 today | 🔧 | Same rule |
| "at 9" with no day | Next 09:00 or 21:00, whichever comes first | 🔧 | Bare hour 7–11, no day → next future occurrence |
| "tomorrow at 9" | Tomorrow 09:00 | 🔧 | Bare hour 7–11 with a day → am |
| "tomorrow morning" | Tomorrow 09:00 | 🔧 | Default table: morning 9, noon 12, afternoon 15, evening 18, tonight 20, night 21 (editable in settings) |
| "4ish" / "around 4" | 16:00 | 🔧 | Strip "ish/around/about/roughly" |
| "half past 3", "quarter to 5" | 15:30 / 16:45 | 🔧 | chrono handles most; add "quarter to" |
| "in 20 minutes", "in an hour and a half" | now + duration | ✅ | chrono |
| "next Friday" said on a Wednesday | Ambiguous: this week's or next week's Friday? | 🔧 | Rule: "next Friday" = the coming Friday; "Friday after next" = +7. Undo notification shows the actual date. |
| "this weekend" | Saturday 10:00 | 🔧 | Default table |
| "end of month", "EOD", "EOW" | Last day 09:00 / today 17:00 / Fri 17:00 | 🔧 | Phrase table |
| "on the 5th" | Next 5th of a month, 09:00 | ✅ | chrono; a date with no time → 09:00 |
| "05/06" | 5 June or 6 May? | 🔧 | Use the phone's locale date order |
| "midnight", "12 am" with no day | 00:00 of the **next** day | 🔧 | Explicit rule (classic off-by-one-day bug) |
| "12 am Friday", "midnight on Friday" | 00:00 at the **start** of Friday | 🔧 | With a day given, midnight belongs to that day |
| "Diwali", "Christmas", "my birthday" | Holiday date | ❌ | v1: no holiday calendar. Item saved as a note with "no time found". |
| Time change days (DST) | Alarm at correct local time | ✅ | Store zone + local time, not UTC offset |
| Travel to another timezone | "6 pm" stays 6 pm local, or follows home? | 🙋 | Default: follows the phone's current zone. Flagged in settings. |

## 2. Repeats

| Input | Expected | Status | How |
|---|---|---|---|
| "every day at 8", "daily" | RRULE daily 08:00 | 🔧 | Repeat parser; bare hours in repeats follow the "with a day" rule |
| "every Monday and Thursday" | Weekly MO,TH | 🔧 | |
| "every other week" | Interval 2 | 🔧 | |
| "weekdays at 7" | MO–FR | 🔧 | |
| "first Monday of every month" | Monthly BYDAY=1MO | 🔧 | |
| "every 31st" | Months without a 31st → last day | 🔧 | Explicit rule |
| "every 3 hours until 9 pm" | Hourly interval with end time | 🔧 | Supported but rare; may defer |
| "take pills twice a day" | 2 times, no times given | 🙋 | Default 09:00 + 21:00, shown in Undo so you can edit |

## 3. Intent (what kind of thing is it?)

| Input | Expected | Status | How |
|---|---|---|---|
| "remind me to…" + time | Reminder | ✅ | Rule: "remind" keyword |
| "call the bank" (no time) | Task | 🔧 | Rule: starts with a verb, no time → task |
| "dentist Thursday 3pm" | Event | 🔧 | Rule: noun + time, no "remind" → event (or reminder if Calendar off) |
| "wifi password is hunter2" | Note | 🔧 | Default when nothing else matches |
| "buy milk, eggs and bread" | 3 items on the shopping list | 🔧 | "buy/grab/get/need" + split on commas and "and" |
| "buy a birthday gift for Sam" | Task, not a shopping item | ⚠️ 🔧 | Rule: more than ~3 words per item or a "for/before" clause → task. Will sometimes misfile; Edit fixes it. |
| "salt and pepper" in a list | One item, not two | ⚠️ | Small list of common pairs; otherwise splits. Known weakness. |
| "what's on my grocery list?" | Opens the shopping list | 🔧 | Question words + list name |
| "when is the dentist?" | Shows the dentist event | 🔧 | Question → search |
| "don't remind me about the gym anymore" | Delete/stop a reminder | ❌ | v1 doesn't edit by voice. Saved as a note. |
| "remind me tomorrow… actually no, Friday" | Friday | 🔧 | Self-correction words ("actually", "no wait", "I mean") → keep the last time |
| Two things in one capture: "plumber Thursday at 4 and buy milk" | Event + shopping item | ⚠️ | Split on " and " only when both halves parse on their own; otherwise one item |
| Rambling 2-minute voice note | Note with transcript | ✅ | Long input (> ~40 words) → always a note, no auto-actions |
| Negation: "I don't need to call mom tomorrow" | Note, **no** reminder | 🔧 | Negation guard: "don't / no need / cancel" before the verb blocks auto-actions |

## 4. Voice

| Situation | Status | How |
|---|---|---|
| Music playing | ✅ | Audio focus pauses it, resumes after |
| Noisy street / wind | ⚠️ | Silero VAD trims silence; accuracy drops. Audio is always kept, so you can replay it. |
| Long pause mid-sentence | 🔧 | VAD end-of-speech timeout ~2 s while the key is held; release ends recording |
| Accidental 0.5 s press | 🔧 | Under 1 s of speech → discarded, no empty items |
| Names Moonshine doesn't know ("call Priyanka") | ⚠️ | May be misspelled. Optional: bias with contact names later. |
| Numbers: "six thirty" vs "6:30" | 🔧 | Number-word normalizer before the date parser |
| No punctuation, fillers ("um", "uh") | 🔧 | Strip fillers before the rules; rules don't rely on punctuation |
| Mixed language (Hinglish) | ❌ | English Moonshine will garble the Hindi parts. Language packs are v2. |
| Phone call in progress | 🔧 | Recording blocked; show a toast |
| Bluetooth headset mic | ✅ | Uses the system's default input |

## 5. Screenshots & images

| Situation | Status | How |
|---|---|---|
| Normal app screenshot | ✅ | PaddleOCR is very accurate on screen text |
| Dark mode / colored backgrounds | ✅ | Usually fine; invert-and-retry if confidence is low |
| Tiny or stylized text, text over photos | ⚠️ | Lower accuracy; screenshot still saved and viewable |
| Handwriting (photo of a notebook) | ❌ | Not supported well by any light model |
| Flyer → event | 🔧 | OCR → date parser on all text; title = largest text line (from box heights) |
| Several dates on one flyer | 🙋 | Pick the first future one, list the others in the Undo/Edit card |
| QR code on a poster | ✅ | ZXing → clickable link |
| Screenshot of a URL bar | ✅ | OCR + URL pattern |
| Source link while in a browser | ⚠️ 🔧 | Read the address bar via accessibility. Works in Chrome/Firefox/Brave; some browsers hide it. |
| Secure screens (banking, Netflix) | ✅ | Android blocks the capture → save as text/voice only, show a note |
| Long scrolling screenshot | 🔧 | Scroll + stitch; stops at the end or after N screens. Apps with custom scrolling may fail. |
| Landscape screenshot | ✅ | |

## 6. Business cards

| Field | Status | How |
|---|---|---|
| Phone, email, website | ✅ | libphonenumber + patterns |
| Name | ⚠️ 🔧 | Largest/top line that isn't a company word ("Ltd", "Inc", "Pvt") |
| Company, job title | ⚠️ 🔧 | Keyword lists ("Manager", "Engineer", "Ltd"…) |
| Two-sided / non-English cards | ❌ | One side, English/Latin only in v1 |
| Always opens Android's contact form pre-filled, so you check before saving | 🙋 | Never auto-saves contacts |

## 7. Location reminders (v2)

Moved to v2: picking a place needed a map, and maps need the network. In v1, "remind me when I'm at the pharmacy" is saved as a **task** ("at the pharmacy" stays in the title), with no reminder.

## 8. Reminders firing reliably

| Situation | Status | How |
|---|---|---|
| Phone reboots | 🔧 | Re-arm all alarms on boot |
| Battery saver / Nothing OS killing the app | 🔧 | Exact alarms (`USE_EXACT_ALARM`) + ask for the "unrestricted battery" exemption during setup |
| Time set in the past ("at 3" at 4 pm) | 🔧 | Moves to the next occurrence (tomorrow 15:00); shown in Undo |
| Do Not Disturb | ✅ | The "Alarm" reminder style (setting) goes through DND; the default "Notification" style respects it |
| Snooze then edit | 🔧 | Editing clears the snooze |

## 9. Deletion (non-negotiable)

| Situation | Status | How |
|---|---|---|
| Delete an item | 🔧 | One delete path: DB row, FTS entry, audio/image files, Markdown file, alarm, calendar event |
| Deleted text left in database pages | 🔧 | SQLite `secure_delete=ON` + periodic `VACUUM` |
| Deleted text left in the WAL file | 🔧 | `PRAGMA wal_checkpoint(TRUNCATE)` right after the delete |
| Deleted item in the search index | 🔧 | FTS row removed in the same transaction |
| Deleted calendar event edited in the calendar app meanwhile | 🔧 | Delete by stored event ID regardless of edits |
| Calendar sync off at delete time, on later | 🔧 | Remembered pending deletes are applied on next sync |
| Item is inside an export file you saved earlier | ❌ | The file is yours, outside the app. Deletion can't reach it. Settings warns about this on export. |
| Item's Markdown file already synced to another device (Syncthing etc.) | ❌ | Known issue: the app deletes its own copy only. GitHub issue to be opened once sync is built (M7). |
| Item was shared out (e.g. to WhatsApp) | ❌ | Can't be recalled. Out of our control. |
| Undo after delete | 🔧 | Held in memory only for ~5 s; never written to disk |
| Thumbnails / cache files | 🔧 | Stored inside the item's own folder so they go with it |
| Screenshot also in the Gallery | 🔧 | Captures are saved to app-private storage, never to the Gallery |

## 10. Search

| Input | Expected | Status | How |
|---|---|---|---|
| "dent" | Finds "dentist" | ✅ | Trigram index matches any part of a word |
| "dentsit" (typo) | Finds "dentist" | 🔧 | No exact hit → search for any shared 3-letter piece, rank by how many match |
| "wi" (under 3 letters) | Finds "wifi" | 🔧 | Falls back to `LIKE` |
| Text inside a screenshot | Finds the screenshot | ✅ | OCR text is indexed |

---

## Summary

- **Well covered:** clear times, durations, phones/emails/links, QR codes, screenshot text, reminders firing, deletion.
- **Covered only if we write the rules** (the bulk of the work): fuzzy times, repeats, intent, shopping lists, self-corrections, negation, flyer titles, business-card names, typo search.
- **Known weak spots, accepted for v1:** splitting multi-item sentences, handwriting, Hinglish/mixed languages, holidays, voice edits ("stop reminding me"), synced Markdown copies.
- **Safety net:** every automatic action shows **Undo / Edit**, long rambles never auto-act, and the original audio and screenshot are always kept, so a wrong guess is a one-tap fix, never lost data.

**If the rules miss too much** in the M4 test set, MiniLM (intent + slots) is the planned upgrade. Mixed languages wait for the v2 language packs.
