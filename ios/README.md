# Skiftschema for iOS

A native SwiftUI app for iOS 17 or later. Open `Skiftschema.xcodeproj` in Xcode 16 or later, select the **Skiftschema** scheme and an iPhone simulator, then run. To install on an iPhone, select your signing team in **Signing & Capabilities**.

```sh
xcodebuild -project ios/Skiftschema.xcodeproj -scheme Skiftschema \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Android and iOS bundle the same roster files from [`data/`](../data/) at build time. Follow the [import guide](../docs/development.md#import-another-year) to add a year to both apps. Each app computes shift times in Europe/Stockholm and keeps preferences and overtime on the device. Calendar export uses EventKit and asks for access when you update the calendar. iOS prefers a local calendar source when available and otherwise uses the default calendar account. If **Synk** is enabled, overtime changes are exported automatically after you approve any pending preference changes with **Uppdatera kalender**.
