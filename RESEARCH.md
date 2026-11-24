# Research: models & user wishlist (Nov 2026)

## 1. What similar apps use

| App | What it does | Speech → text | Understanding / AI | Where it runs |
|---|---|---|---|---|
| **Nothing Essential Space** | Capture key, summaries, reminders, Google Calendar sync (since Jul 2025), Events, semantic search | "Essential Voice" is processed on Nothing's servers. The Recorder also has an **offline transcript** button (per forum users). | Undisclosed model, server-side (community says it's hosted on Amazon's cloud) | Mostly cloud |
| **Google Pixel Screenshots** | Screenshot memory, suggested reminders/events, natural-language search | n/a | **Gemini Nano (multimodal)** | Fully on-device, Pixel 9+ only |
| **Todoist Ramble** | Speak a brain-dump and tasks appear live | Skips transcription entirely: raw audio goes to the model | **Gemini 2.5 Flash Live** via Vertex AI, using tool calls (add/edit/delete task) | Cloud. Audio not stored or used for training. |
| Open-source Android voice apps (2026) | Offline dictation / notes | **Whisper**, **Moonshine** or **Parakeet**, usually run through **sherpa-onnx** | — | On-device |

### Speech-to-text options for the Phone (3a)

| Model | Size | Strength | Weakness |
|---|---|---|---|
| Whisper base | ~75 MB (quantized) | 99 languages, well known | Slower on phones; not streaming |
| **Moonshine** (tiny/base) | ~30–125 MB | Fastest on phones, live text while you speak, very low battery | Fewer languages; check the license per model |
| Parakeet 0.6B | ~670 MB | Best accuracy | Too heavy for "light" |

Runtime: **sherpa-onnx** runs all three from one Android library, so we can swap models without rewriting code.

### Understanding (intent) options, lightest first

1. **No model:** keyword rules + **ML Kit Entity Extraction** (dates, times, addresses, phone numbers, URLs). A few MB, instant, offline.
2. **FunctionGemma 270M:** Gemma 3 270M tuned to turn a sentence into an action ("create reminder", "create calendar event"). Runs through LiteRT-LM. Google's Mobile Actions demo shows 58% accuracy out of the box and **85% after fine-tuning**.
3. **Gemini Flash-Lite via Firebase AI Logic:** cloud, for screenshots and messy input. This is the same family Todoist uses. The free (Spark) plan lets Google use your data; the paid plan doesn't.

**Takeaway:** the big apps (Nothing, Todoist) use cloud models, and Google's own is on-device but only on Pixels. A light, local-first app can realistically do: **Moonshine or Whisper base → rules + entity extraction → (optional) FunctionGemma → (optional) Gemini for screenshots.**

---

## 2. What users want from Essential Space

Source: the official "Essential Space | Feature Wishlist" thread on Nothing Community (172 posts, read in full). Reddit blocks automated access, so it wasn't searched. Rough number of posts per request:

### Top requests
| # | Request | ~Posts | Nothing shipped it? |
|---|---|---|---|
| 1 | **Remap / disable the Essential Key** (accidental presses, near power button) | 15+ | Announced for OS 5.1, not shipped |
| 2 | **Edit notes / reminders / dates after AI creates them** (AI got the date wrong) | 12+ | Partly (editable summaries) |
| 3 | **Plain notes & to-dos without a screenshot or date** ("buy milk") | 10+ | Workaround only (tap screen to skip the screenshot) |
| 4 | **Web / PC / cross-device access, sync** | 8+ | Cloud backup Apr 2026, no web app |
| 5 | **Search** | 6+ | Semantic search Mar 2026 |
| 6 | **Save the link / source URL of a screenshot, clickable links** | 6+ | No |
| 7 | **Google Calendar / Tasks / Keep integration, two-way sync** | 6+ | One-way Calendar sync (Jul 2025). Users report **deleted tasks coming back** from Google. |
| 8 | **Better transcription, non-English languages** (French, German, Telugu); "use Whisper locally" | 5+ | Offline transcript exists in the Recorder |
| 9 | **Integrations:** Notion, Obsidian, OneNote, Google Keep | 5+ | No |
| 10 | **Better organization:** auto-collections, sorting, tags, pin, archive, bulk delete | 8+ | Partly (Smart Collections) |

### Smaller but useful ideas
- Snooze a reminder by 30 min / 1 hour from the notification
- Recurring reminders (every week)
- Location-based reminders ("remind me when I'm at the pharmacy")
- Reminders auto-enable a notification when a time is given (currently they don't)
- Subtasks with checkboxes; shopping list that collects from several captures
- Scrollable widget showing more than one upcoming item; widget per collection
- Overdue tasks stay visible until done
- Creation timestamp (and location) on notes
- Ask questions over your stuff ("what's on my grocery list?")
- Business card → contact
- Scrolling (long) screenshots, landscape screenshots, crop
- Pause music while recording a voice note (it currently records the music)
- Export audio recordings; transfer data to a new phone
- Captured photos shouldn't also clutter the gallery
- Work offline; process captures immediately without opening the app
- Proton / Outlook calendar, not just Google
- Light theme for the app independent of system dark mode

### Already in our plan
Edit everything, plain notes/to-dos, search, Markdown export (Obsidian), local Whisper, optional Calendar, works offline, key remap (via accessibility), real deletion (this also fixes the "deleted items come back from Google" bug).

## Sources
- [Nothing Community: Essential Space Feature Wishlist](https://nothing.community/d/28946-essential-space-feature-wishlist)
- [9to5Google: Essential Space Google Calendar + Recorder update](https://9to5google.com/2025/07/17/nothing-essential-space-google-calendar-recorder-update/)
- [Android Central: Essential Voice](https://www.androidcentral.com/phones/nothing-phones/nothings-giving-its-phones-an-essential-voice-to-usher-in-smarter-transcriptions)
- [Google Store: Pixel Screenshots](https://store.google.com/intl/en/ideas/articles/pixel-screenshots)
- [TechCrunch: Todoist Ramble](https://techcrunch.com/2026/01/21/todoists-app-now-lets-you-add-tasks-to-your-to-do-list-by-speaking-to-its-ai/)
- [Product Talk: Building Todoist Ramble](https://www.producttalk.org/building-todoist-ramble-how-doist-turned-voice-braindumps-into-real-time-task-capture/)
- [Resonant: Moonshine vs Parakeet vs Whisper](https://www.onresonant.com/resources/local-stt-models-2026)
- [Moonshine Android STT (GitHub)](https://github.com/Jackfood2/MoonshineSpeechToText)
- [Google: FunctionGemma](https://blog.google/technology/developers/functiongemma/)
- [FunctionGemma model card](https://ai.google.dev/gemma/docs/functiongemma/model_card)
- [Firebase AI Logic pricing](https://firebase.google.com/docs/ai-logic/pricing)
