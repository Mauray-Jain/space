# Edge cases: does the local stack hold up?

Stack: Moonshine + Silero VAD → own date/intent rules (+ libphonenumber, Android Patterns) · PaddleOCR + ZXing for images. FunctionGemma is optional.

Legend: ✅ handled by the stack · 🔧 handled by a specific rule we must write · 🙋 the app asks / you fix it · ❌ not handled in v1

This list doubles as the **test set for milestone 4**: each row becomes a test input with its expected result.

---

## 1. Times & dates

| Input | Expected | Status | How |
|---|---|---|---|
| "remind me at 6 to call mom" | Today 18:00 (or tomorrow if 6 pm has passed) | 🔧 | Bare hour → next future occurrence. 6 = 6 pm unless it's before 6 am now and context says morning. |
| "at 6" when it's 5:50 pm | 18:00 today | 🔧 | Same rule |
| "tomorrow morning" | Tomorrow 09:00 | 🔧 | Default table: morning 9, noon 12, afternoon 15, evening 18, tonight 20, night 21 (editable in settings) |
| "4ish" / "around 4" | 16:00 | 🔧 | Strip "ish/around/about/roughly" |
| "half past 3", "quarter to 5" | 15:30 / 16:45 | 🔧 | chrono handles most; add "quarter to" |
| "in 20 minutes", "in an hour and a half" | now + duration | ✅ | chrono |
| "next Friday" said on a Wednesday | Ambiguous: this week's or next week's Friday? | 🔧 | Rule: "next Friday" = the coming Friday; "Friday after next" = +7. Undo notification shows the actual date. |
| "this weekend" | Saturday 10:00 | 🔧 | Default table |
| "end of month", "EOD", "EOW" | Last day 09:00 / today 17:00 / Fri 17:00 | 🔧 | Phrase table |
| "on the 5th" | Next 5th of a month | ✅ | chrono |
| "05/06" | 5 June or 6 May? | 🔧 | Use the phone's locale date order |
| "midnight", "12 am" | 00:00 of the **next** day | 🔧 | Explicit rule (classic off-by-one-day bug) |
| "Diwali", "Christmas", "my birthday" | Holiday date | ❌ | v1: no holiday calendar. Item saved as a note with "no time found". |
| Time change days (DST) | Alarm at correct local time | ✅ | Store zone + local time, not UTC offset |
| Travel to another timezone | "6 pm" stays 6 pm local, or follows home? | 🙋 | Default: follows the phone's current zone. Flagged in settings. |

## 2. Repeats

| Input | Expected | Status | How |
|---|---|---|---|
| "every day at 8", "daily" | RRULE daily 08:00 | 🔧 | Repeat parser |
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
| "remind me to…" + time | Reminder | ✅ | Keyword |
| "call the bank" (no time) | Task | 🔧 | Starts with a verb, no time → task |
| "dentist Thursday 3pm" | Event | 🔧 | Noun + time, no "remind" → event (or reminder if Calendar off) |
| "wifi password is hunter2" | Note | 🔧 | Default when nothing else matches |
| "buy milk, eggs and bread" | 3 items on the shopping list | 🔧 | "buy/grab/get/need" + split on commas and "and" |
| "buy a birthday gift for Sam" | Task, not a shopping item | ⚠️ 🔧 | Rule: more than ~3 words or a "for/before" clause → task. Will sometimes misfile; Edit fixes it. |
| "salt and pepper" in a list | One item, not two | ⚠️ | Small list of common pairs; otherwise splits. Known weakness. |
| "what's on my grocery list?" | Opens the shopping list | 🔧 | Question words + list name |
| "when is the dentist?" | Shows the dentist event | 🔧 | Question → search |
| "don't remind me about the gym anymore" | Delete/stop a reminder | ❌ | v1 doesn't edit by voice. FunctionGemma territory. |
| "remind me tomorrow… actually no, Friday" | Friday | 🔧 | Self-correction words ("actually", "no wait", "I mean") → keep the last time |
| Two things in one capture: "plumber Thursday at 4 and buy milk" | Event + shopping item | ⚠️ | Split on " and " only when both halves parse on their own; otherwise one item. FunctionGemma does this better. |
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
| Mixed language (Hinglish) | ❌ | English Moonshine will garble the Hindi parts. Whisper multilingual is a v2 language pack. |
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

## 7. Location reminders

| Situation | Status | How |
|---|---|---|
| "when I'm at the pharmacy" (first time) | 🙋 | Asks you to pick the place on a map once; saved as "pharmacy" |
| Same phrase later | ✅ | Uses the saved place |
| "when I get home / to work" | 🙋 | Set Home and Work once in settings |
| "any pharmacy" | ❌ | Would need a places database; not local, not in v1 |
| Location permission off | 🙋 | Saved as a task with a note to enable location |
| Geofence limit (Android allows 100 per app) | ✅ | Far beyond personal use |

## 8. Reminders firing reliably

| Situation | Status | How |
|---|---|---|
| Phone reboots | 🔧 | Re-arm all alarms on boot |
| Battery saver / Nothing OS killing the app | 🔧 | Exact alarms + ask for the "unrestricted battery" exemption during setup |
| Time set in the past ("at 3" at 4 pm, meant tomorrow?) | 🔧 | Moves to the next occurrence; shown in Undo |
| Do Not Disturb | ✅ | Optional "alarm-style" reminder that bypasses DND (setting) |
| Snooze then edit | 🔧 | Editing clears the snooze |

## 9. Deletion (non-negotiable)

| Situation | Status | How |
|---|---|---|
| Delete an item | 🔧 | One delete path: DB row, FTS entry, audio/image files, Markdown file, alarm, geofence, calendar event |
| Deleted text left in database pages | 🔧 | SQLite `secure_delete=ON` + periodic `VACUUM` |
| Deleted item in the search index | 🔧 | FTS row removed in the same transaction |
| Deleted calendar event edited in Google Calendar meanwhile | 🔧 | Delete by stored event ID regardless of edits |
| Calendar sync off at delete time, on later | 🔧 | Remembered pending deletes are applied on next sync |
| Item is inside an export file you saved earlier | ❌ | The file is yours, outside the app. Deletion can't reach it. Settings warns about this on export. |
| Item was shared out (e.g. to WhatsApp) | ❌ | Can't be recalled. Out of our control. |
| Undo after delete | 🔧 | Held in memory only for ~5 s; never written to disk |
| Thumbnails / cache files | 🔧 | Stored inside the item's own folder so they go with it |
| Screenshot also in the Gallery | 🔧 | Captures are saved to app-private storage, never to the Gallery |

---

## Summary

- **Well covered:** clear times, durations, phones/emails/links, QR codes, screenshot text, reminders firing, deletion.
- **Covered only if we write the rules** (the bulk of the work): fuzzy times, repeats, intent, shopping lists, self-corrections, negation, flyer titles, business-card names.
- **Known weak spots, accepted for v1:** splitting multi-item sentences, handwriting, Hinglish/mixed languages, holidays, "any pharmacy", voice edits ("stop reminding me").
- **Safety net:** every automatic action shows **Undo / Edit**, long rambles never auto-act, and the original audio and screenshot are always kept, so a wrong guess is a one-tap fix, never lost data.

**If the weak spots matter more than expected,** FunctionGemma (multi-item, voice edits) and Whisper multilingual (Hinglish) are the two upgrades that fix most of them.
