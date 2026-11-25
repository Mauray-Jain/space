# Checks that need your phone

Things CI's emulator can't cover. Try them on the Nothing Phone (3a) when you can. The debug APK from each CI run is attached to the run as `space-debug-apk`.

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
