# Skiftschema development

The editable design is [Skiftschema.figcraft](Skiftschema.figcraft). It specifies the Swedish screens, layout, and the six palettes in Light and Dark. System follows the device. Select the palette under **Utseende → Färgpalett**. Palette tokens live in `src/theme.tsx`. Havsblå, Plommon, and Bärnsten use Material HCT tones. Nord adapts the [Nord color palettes](https://www.nordtheme.com/docs/colors-and-palettes/). Cthulhu Deep One adapts the [Omarchy theme by sonderbydk](https://github.com/sonderbydk/omarchy-cthulhu-deep-one-theme) and pairs its dark colors with a new Light version. Treat the dated roster PDFs as the source for actual assignments, closures, and vacation periods. The 2027 five-shift PDF shows yellow from 19 July to 8 August and green from 9 to 29 August.

## Preview and builds

```sh
npm ci
npm run preview
```

Open http://localhost:8081/?demo=1 to review July 2027 with 5-skift, crew B, and yellow vacation. Open http://localhost:8081/ without the query to review onboarding and the calendar for the current date. These previews use separate browser storage. The demo freezes the clock. The browser simulates phone-calendar actions. No web app is published.

Run `npm run android` with the Android SDK and a connected Android device or emulator for native development. Browser preview cannot verify the real calendar provider, Android permissions, or touch gestures. Run `npm run check` and `npm test` before handing off changes. Keep any future Android release keystore and credentials outside Git. The first public download is intended to be a locally signed APK on GitHub Releases.

## Scheduling rules

- The schedule data is bundled JSON. Preferences, overtime, and calendar export state live in SQLite on Android. The app needs no network to look up shifts.
- Resolve roster dates in Europe/Stockholm with the selected schedule year's cutoff. Night shifts end at the following roster day's cutoff, including at New Year. Store custom hours per schedule and year.
- A matching five-shift vacation period shows Semester and suppresses regular personal work. All source assignments stay visible. Overtime is recorded separately with actual start and end times.
- Tap to select the entire day in either calendar view. Double-tap a day in the monthly view to open its week. Use Månad or Android Back to return to the month. Hold the day or use Visa pass to open its shifts and choose a shift for overtime. Swipe moves between weeks or months. The gesture guide is the last onboarding step and is also available under Preferenser → Kalendergester.
- On Android, Back returns to the previous sheet, preferences page, or onboarding step. Back from the weekly view opens the selected day's month and keeps that day highlighted. If no day is selected in the week, Back restores the previous monthly view. Android handles Back at the monthly overview.
- Overtime can be added or edited on shifts assigned to another crew. Existing overtime on your current crew can still be removed if your assignment changes.
- Export only selected categories to the dedicated device calendar. Leave alarms off by default. Require approval before changes to preferences affect that calendar when sync is enabled. Preserve pending approval across restarts. Identify the app's calendar events by their own stable marker.

## Import another year

The PDF reader supports the four original 2026 and 2027 rosters. Place local copies in `docs/` to reproduce the bundled JSON with the commands below. Git ignores those PDF files.

```sh
npm run import:schedule -- docs/Skiftschema-2026.pdf --schedule five --year 2026
npm run import:schedule -- docs/Skiftschema-Energi-2026.pdf --schedule six --year 2026
npm run import:schedule -- docs/Skiftschema-2027.pdf --schedule five
npm run import:schedule -- docs/Skiftschema-Energi-2027.pdf --schedule six
```

For a new year, copy the approved data into a spreadsheet with these sheet and column names. This normalized template makes the import independent of the original print layout.

| Sheet | Row 1 columns | Values |
| --- | --- | --- |
| Schema | `key`, `value` | `schedule` (five or six), `year`, `cutoff`, `weekdayEm`, `weekdayNa`, `weekendNa` |
| Dagar | `date`, `FM`, `EM`, `NA`, `closure`, `note` | One date per row. Use crew letters, leave weekend EM blank, and give five-shift closures an explicit label with blank shifts. |
| Semester | `color`, `from`, `to` | Inclusive dates for `blue`, `red`, `yellow`, or `green`. Omit for 6-skift. |

Use `YYYY-MM-DD` or spreadsheet date cells for dates. Use 24-hour `HH:MM` times. Run `npm run import:schedule -- path/to/new-roster.xlsx`. The importer validates coverage, crew counts, shift ordering, and vacation ranges. Review the generated data against the official roster and retain the earlier JSON files. A new app build bundles all recognized files in `data/`.
