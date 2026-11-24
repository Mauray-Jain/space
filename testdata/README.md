# Understanding test set (milestone 4)

`understanding_test.jsonl` has one test case per line. It covers every row in [EDGE_CASES.md](../EDGE_CASES.md) §1–§7, plus harder variants built to break the rules: negations in odd places, self-corrections, times in words, ambiguous days, list pairs, tasks that look like shopping, questions phrased as commands, speech-to-text noise and OCR text.

**Never train on this file.** If MiniLM is ever needed, its training sentences go in `training/`, written separately.

## Format

```json
{"id": "t001", "ref": "§1", "source": "text", "now": "2026-11-25T14:00", "input": "remind me at 6 to call mom",
 "expect": {"type": "reminder", "title": "call mom", "when": "2026-11-25T18:00"}}
```

| Field | Meaning |
|---|---|
| `id` | Stable id; never reuse one |
| `ref` | EDGE_CASES.md section the case belongs to |
| `source` | `text`, `voice` (Moonshine-style: lowercase, no punctuation, fillers, misheard words) or `ocr` |
| `now` | The phone's local time when the capture happens. Zone is always `Asia/Kolkata`. `2026-11-25` is a **Wednesday**. |
| `locale` | Only when it matters (date order). Default `en-IN` (day first). |
| `input` | The text (for `ocr`: lines joined by `\n`) |
| `ocr` | For `ocr` cases where layout matters: a list of `{"text", "h"}` lines, `h` = box height in px |
| `expect` | One object, or a list of them when one capture should become several items |
| `known_weak` | `true` = a known weak spot. Reported separately, **doesn't fail M4**. Don't add rules just to pass these. |

### `expect` fields
Only the fields given are checked.

- `type`: `note`, `task`, `reminder`, `event`, `list` (shopping list), `question`, `contact` or `discard` (nothing saved)
- `title`: compared case-insensitively, with whitespace and trailing punctuation ignored
- `when`: first occurrence, local time `YYYY-MM-DDTHH:MM`. `null` = no time must be set.
- `repeat`: RFC 5545 RRULE (without `DTSTART`)
- `items`: shopping list items, in order
- `alts`: other dates found (flyers), shown in the Undo/Edit card
- `target` / `search`: for questions, either a list to open (`shopping_list`) or the search text
- `phones`, `emails`, `links`: extracted values (phones in E.164)
- `name`, `company`, `job`: business-card fields

## Rules the expected values follow
These are the decisions behind the expected values. The rules must implement them.

1. **Bare hours** (no am/pm, no part of day):
   - 1–6 → pm;
   - 12 → noon;
   - 7–11 with no day → the next future occurrence (am or pm);
   - 7–11 with a day or a repeat → am.
   If the result is in the past and no day was given → the same time tomorrow.
2. **Day with no time** → 09:00. If that's already past (e.g. "on the 25th" on the 25th at 14:00) → the next occurrence.
3. **Parts of the day:** morning 09:00, noon 12:00, afternoon 15:00, evening 18:00, tonight 20:00, night 21:00. "morning" with no day after 09:00 → tomorrow.
4. **Phrases:** this weekend = Sat 10:00, end of month = last day 09:00, EOD = today 17:00, EOW = Fri 17:00, next week = Mon 09:00, next month = 1st 09:00.
5. **Days:** "next Friday" = "this Friday" = "Friday" = the coming Friday. "Friday after next" = +7 days.
6. **"in N minutes/hours"** → now + N. **"in N days/weeks"** keeps the current time of day.
7. **Midnight / 12 am:** with no day → 00:00 of the next day. With a day → 00:00 at the start of that day.
8. **Intent:**
   - "remind me" + time → reminder;
   - "remind me" with no time → task;
   - starts with a verb + time → reminder;
   - starts with a verb, no time → task;
   - starts with a noun + time → event;
   - "buy/grab/get/need" + short items → list;
   - a question word → question;
   - otherwise → note.
9. **Negation guard:** "don't / no need / not / cancel / never" before the main verb → note, no auto-action. Exceptions: "don't forget to…" and "remember to…" are positive.
10. **Self-correction:** after "actually", "no wait", "I mean", "sorry", the last time given wins.
11. **Long input** (> 40 words) → note, no auto-actions, even if it contains times.
12. **Discard:** empty input or fillers only.
13. **Location phrases** ("when I'm at…", "when I get home") → task, phrase kept in the title. Location reminders are v2.
14. **Holidays and birthdays** → note, `when: null`.
