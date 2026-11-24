# Skiftschema

Skiftschema is a Swedish-language mobile app for looking up your work shifts. It includes the 2026 and 2027 rosters for **5-skift** and **6-skift**. The schedules work offline, and your preferences and overtime are stored on your device.

Android has been tested on a physical phone. The project is also configured for iPhone, where a device build and signing still need to be verified.

## Using the app

1. Choose your schedule and shift team. If you work 5-skift, you can also select your vacation group. You can change these choices under **Preferenser**.
2. Tap a day to select it. Double-tap in **Månad** to open that day’s week. Swipe sideways to move through the calendar. Use **Månad** or Android Back to return to the month with your selected day highlighted.
3. Hold a day to see every shift and its times. You can log overtime for all or part of another team’s shift.

The calendar highlights your shifts and shows your next one. Choose Light, Dark, or System under **Preferenser → Utseende**, where you can also change the color palette.

### Phone calendar export

Enable **Preferenser → Kalenderexport**, choose what to export, then tap **Uppdatera kalender**. Skiftschema exports to a separate calendar named **Skiftschema**. Automatic updates are optional, and reminders are off by default.

On Android, the exported calendar is stored locally. If it’s missing from Google Calendar’s menu, open **☰ → Inställningar → Hantera konton** and enable **se.skiftschema.app** under **Konton utanför Google**. Then check **Skiftschema** in the calendar menu.

## Development

The app is built with Expo, React Native, and TypeScript. It reads the bundled JSON rosters in `data/` and uses SQLite for local app data.

Use Node.js 22.13+, 24.3+, or 26+. Install the locked dependencies and start the browser preview.

```sh
npm ci
npm run preview
```

The demo opens a preconfigured July 2027 calendar. The default preview starts with onboarding. Both simulate phone calendar export. [Demo](http://localhost:8081/?demo=1) · [Default preview](http://localhost:8081/)

To build and install the Android debug app, set up the Android SDK and JDK, then connect a phone with USB debugging enabled or start an emulator.

```sh
npm run android
```

Expo generates the native projects from `app.json`. If a USB-connected debug build can’t reach the development server, run `adb reverse tcp:8081 tcp:8081`.

Run `npm run check` for TypeScript and `npm test` for the automated tests.

Keep signing keys and release credentials outside the repository. [Schedule import guide](docs/development.md) · [Editable FigCraft design](docs/Skiftschema.figcraft)
