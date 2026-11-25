# Checks that need your phone

Things CI's emulator can't cover. Try them on the Nothing Phone (3a) when you can. The debug APK for the phone (arm64) from each CI run is attached to the run as `space-debug-apk`.

## Milestone 1
- [ ] Quick Settings tile: add "Save to Space" to Quick Settings, tap it, type, Save. It should close and the item shows in the app.
- [ ] Share sheet: share a link from Chrome to "Save to Space". The sheet opens with the title and link filled in.

## Milestone 2
- [ ] Editor: open an item, change text, add `#tag`, pin, go Back. The change, the tag chip on the home screen and "Pinned" all show.
- [ ] Long-press two items, Delete, then Undo within 5 s. Both come back.
- [ ] Shopping chip: add "milk, bread, soap". They sort under Dairy & eggs / Bakery / Household. Tick one: it moves to Bought, struck through.
- [ ] Search "dentsit" finds a note containing "dentist".

## Milestone 3
- [ ] Reminder in 2 minutes (quick entry → Reminder chip → time). It rings as a notification with Done / Snooze buttons, with the screen off too.
- [ ] Snooze 30 min from the notification. The card shows the new time.
- [ ] Settings → Reminder style → Alarm. A reminder now rings full screen over the lock screen until Done/Snooze, also in Do Not Disturb.
- [ ] Reboot the phone with a reminder set for later. It still rings.
- [ ] Settings → "Allow unrestricted battery" opens Android's prompt.
- [ ] From the Quick Settings tile, save a Reminder: a "Reminder set" notification with Undo/Edit appears. Undo removes it.

## Milestone 4
- [ ] Quick entry: type "remind me to call mom at 6". The Reminder chip lights up and the line under the text reads "Reminder · call mom · …, 18:00". Save: a reminder "call mom" appears.
- [ ] Type "buy milk, eggs and bread": three shopping items. Tap Note before Save: one note instead.
- [ ] Type "what's on my grocery list?" and tap Show: the shopping list opens.

## Milestone 5
- [ ] Quick entry → Speak, say "remind me to call mom at six", Stop. The text appears and the Reminder chip lights up.
- [ ] Music playing in another app pauses while recording and resumes after.
- [ ] Tap Speak and Stop without talking: "Didn't hear anything", nothing saved.
- [ ] During a phone call, Speak says "Can't record during a call".
- [ ] Open a saved voice item: "Play recording" plays what you said. Delete it: the recording goes too.
- [ ] Time a 10 s voice note on the phone (transcribing should take a second or two; report if it's slow).

## Milestone 6
- [ ] Settings → Essential Key → "Turn on in Accessibility settings", enable "Space: Essential Key". Back in Settings: "Set up", press the Essential Key once. It shows the key code. **Tell me the code** so I can make it the default.
- [ ] Disable stock Essential Space first (ADB), or both will react.
- [ ] Press the key on any screen: the sheet opens with the screenshot. Save it without typing: a "Screenshot" note appears and within a few seconds is renamed to its first line of text, with #tags for receipts/tickets/recipes.
- [ ] In Chrome, press the key: the page's link is added to the note and is tappable in the list.
- [ ] Screenshot an event poster with a date: it becomes a reminder and a "Reminder set" notification with Undo appears.
- [ ] Drag on the screenshot to crop before saving; the saved image is cropped.
- [ ] "Longer" on a long web page: the sheet comes back with a taller, stitched screenshot (check the joins look right).
- [ ] Hold the key: the sheet opens already listening. Double-press: Space opens.
- [ ] Press the key in a banking app or Netflix: "This app doesn't allow screenshots…" and you can still type or speak.
- [ ] Search for a word that's only inside a screenshot: it's found.

## Milestone 7
- [ ] Settings → Calendar → pick your Google (or other) calendar. Type "dentist thursday 3pm": it shows in the calendar app. Delete it in Space: gone from the calendar too.
- [ ] Settings → Markdown folder → pick a folder (e.g. your Obsidian vault). Items appear as `.md` files; edit one in Space and the file updates; delete it and the file goes.
- [ ] Export with a password, then open the .zip on a computer with 7-Zip using that password. Import it on the phone: "Imported 0 items" (all already there).
- [ ] Add the Space widget to the home screen: it lists upcoming reminders and open tasks; "+" opens the capture sheet; tapping an item opens it.
- [ ] Screenshot (or share a photo of) a business card: a "Business card · Name" notification opens the filled-in contact form. The note also has "Add to contacts".

## Milestone 8
- [ ] Install the **release** APK (`./gradlew assembleRelease` → `app/build/outputs/apk/release/app-arm64-v8a-release.apk`, ~99 MB) and repeat a quick pass: type a reminder, speak a note, screenshot with the key, search, export. R8 minification can only be checked on a real install.
- [ ] Settings → Theme: Light / Dark switch immediately, regardless of the phone's setting.
- [ ] Settings → Morning summary on: at 8 AM a notification lists open tasks with no time.
- [ ] Battery: after a day of normal use, Settings → Battery → Space should show almost nothing in the background.
