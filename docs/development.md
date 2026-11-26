# Skiftschema development

The editable design is [Skiftschema.figcraft](Skiftschema.figcraft). It specifies the Swedish screens, layout, and the six palettes in Light and Dark. System follows the device. Select the palette under **Utseende → Färgpalett**. Android palette tokens live in `android/app/src/main/java/se/skiftschema/app/Palette.java`. Havsblå, Plommon, and Bärnsten use Material HCT tones. Nord adapts the [Nord color palettes](https://www.nordtheme.com/docs/colors-and-palettes/). Cthulhu Deep One adapts the [Omarchy theme by sonderbydk](https://github.com/sonderbydk/omarchy-cthulhu-deep-one-theme) and pairs its dark colors with a new Light version. Treat the dated roster PDFs as the source for actual assignments, closures, and vacation periods. The 2027 five-shift PDF shows yellow from 19 July to 8 August and green from 9 to 29 August.

## Android builds and checks

Open `android/` in Android Studio with JDK 17 and Android SDK Platform 35. From that directory, run `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`. With an Android 35 emulator connected, run `./gradlew :app:connectedDebugAndroidTest` to check the bundled rosters, profile compatibility, and calendar-provider writes. GitHub Actions runs these checks on `main`, `Android`, and `fix-android-*` branches and on pull requests targeting `main` or `Android`. Use `./gradlew :app:installDebug` for manual checks such as calendar gestures. The Android build bundles the matching JSON files in `data/`. Keep release signing credentials outside Git.

## Scheduling rules

- The schedule data is bundled JSON. Preferences, overtime, and calendar export state live in SQLite on Android. The app needs no network to look up shifts.
- Resolve roster dates in Europe/Stockholm with the selected schedule year's cutoff. Night shifts end at the following roster day's cutoff, including at New Year. Store custom hours per schedule and year.
- A matching five-shift vacation period shows Semester and suppresses regular personal work. All source assignments stay visible. Overtime is recorded separately with actual start and end times.
- Tap to select the entire day in either calendar view. Double-tap a day in the monthly view to open its week. Use Månad or Android Back to return to the month. Hold the day or use Visa pass to open its shifts and choose a shift for overtime. Swipe moves between weeks or months. The gesture guide is the last onboarding step and is also available under Preferenser → Kalendergester.
- Swipe a bottom drawer down or tap outside it to dismiss it. Android Back closes the overtime editor and navigates back through other sheets, preferences pages, and onboarding. Back from the weekly view opens the selected day's month and keeps that day highlighted. If no day is selected in the week, Back restores the previous monthly view. Android handles Back at the monthly overview.
- Overtime can be added or edited on shifts assigned to another crew. Existing overtime on your current crew can still be removed if your assignment changes.
- Export only selected categories to the dedicated device calendar. Leave alarms off by default. Require approval before changes to preferences affect that calendar when sync is enabled. Preserve pending approval across restarts. Identify the app's calendar events by their own stable marker.

## Import another year

Run the importer from the repository root with Python 3. The PDF reader also requires `pdftotext` from Poppler. It supports the four original 2026 and 2027 rosters. Place local copies in `docs/` to reproduce the bundled JSON with the commands below. Git ignores those PDF files.

```sh
python3 scripts/import_schedule.py docs/Skiftschema-2026.pdf --schedule five --year 2026
python3 scripts/import_schedule.py docs/Skiftschema-Energi-2026.pdf --schedule six --year 2026
python3 scripts/import_schedule.py docs/Skiftschema-2027.pdf --schedule five
python3 scripts/import_schedule.py docs/Skiftschema-Energi-2027.pdf --schedule six
```

For a new year, copy the approved data into a spreadsheet with these sheet and column names. This normalized template makes the import independent of the original print layout.

| Sheet | Row 1 columns | Values |
| --- | --- | --- |
| Schema | `key`, `value` | `schedule` (five or six), `year`, `cutoff`, `weekdayEm`, `weekdayNa`, `weekendNa` |
| Dagar | `date`, `FM`, `EM`, `NA`, `closure`, `note` | One date per row. Use crew letters, leave weekend EM blank, and give five-shift closures an explicit label with blank shifts. |
| Semester | `color`, `from`, `to` | Inclusive dates for `blue`, `red`, `yellow`, or `green`. Omit for 6-skift. |

Use `YYYY-MM-DD` or spreadsheet date cells for dates. Use 24-hour `HH:MM` times. Run `python3 scripts/import_schedule.py path/to/new-roster.xlsx`. The importer validates coverage, crew counts, shift ordering, and vacation ranges. Review the generated data against the official roster and retain the earlier JSON files. A new app build bundles all recognized files in `data/`.
