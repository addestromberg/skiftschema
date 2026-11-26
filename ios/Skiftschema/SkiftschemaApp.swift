import SwiftUI

@main
struct SkiftschemaApp: App {
    @AppStorage("skiftschema.profile") private var savedProfile = Data()
    private let schedule = Result { try Schedule() }

    var body: some Scene {
        WindowGroup {
            switch schedule {
            case .success(let schedule):
                RootView(schedule: schedule, savedProfile: $savedProfile)
            case .failure:
                ContentUnavailableView("Schemat kunde inte läsas", systemImage: "calendar.badge.exclamationmark")
            }
        }
    }
}

struct RootView: View {
    let schedule: Schedule
    @Binding private var savedProfile: Data
    @State private var profile: Profile

    init(schedule: Schedule, savedProfile: Binding<Data>) {
        self.schedule = schedule
        _savedProfile = savedProfile
        _profile = State(initialValue: (try? JSONDecoder().decode(Profile.self, from: savedProfile.wrappedValue)) ?? Profile())
    }

    var body: some View {
        AppContent(schedule: schedule, profile: $profile)
            .preferredColorScheme(profile.appearance == "light" ? .light : profile.appearance == "dark" ? .dark : nil)
            .onChange(of: profileData) { _, data in
                if let data { savedProfile = data }
            }
    }

    private var profileData: Data? {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try? encoder.encode(profile)
    }
}

@MainActor
struct AppContent: View {
    let schedule: Schedule
    @Binding var profile: Profile
    @Environment(\.colorScheme) private var colorScheme
    @State private var exporter = CalendarExport()
    @State private var busy = false
    @State private var preferenceRevision = 0
    @State private var overtimeRevision = 0
    @State private var message: String?

    private var palette: Palette { Palette(profile.palette, dark: colorScheme == .dark) }

    var body: some View {
        NavigationStack {
            Group {
                if profile.onboarded {
                    ShiftCalendar(schedule: schedule, profile: $profile, overtimeChanged: overtimeChanged) {
                        PreferencesView(schedule: schedule, profile: $profile, busy: busy,
                                        sync: { updateCalendar() }, remove: remove,
                                        preferencesChanged: preferencesChanged)
                    }
                } else {
                    OnboardingView(schedule: schedule, profile: $profile)
                }
            }
            .background(palette.canvas.ignoresSafeArea())
        }
        .environment(\.shiftPalette, palette)
        .environment(\.locale, RosterDate.locale)
        .tint(palette.action)
        .alert("Kalenderexport", isPresented: Binding(
            get: { message != nil }, set: { if !$0 { message = nil } }
        )) {
            Button("OK") { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    private func preferencesChanged() {
        preferenceRevision += 1
        if profile.export.enabled && profile.export.automatic { profile.export.pending = true }
    }

    private func overtimeChanged() {
        overtimeRevision += 1
        guard profile.export.enabled && profile.export.automatic, !profile.export.pending else { return }
        updateCalendar(quiet: true)
    }

    private func updateCalendar(quiet: Bool = false) {
        guard !busy && profile.export.enabled else { return }
        busy = true
        let snapshot = profile
        let preferencesAtStart = preferenceRevision
        let overtimeAtStart = overtimeRevision
        Task {
            do {
                try await exporter.sync(schedule: schedule, profile: snapshot) { id in
                    profile.export.calendarID = id
                }
                if preferenceRevision == preferencesAtStart && !quiet {
                    profile.export.pending = false
                }
                if !quiet { message = "Kalendern Skiftschema har uppdaterats." }
                busy = false
                if overtimeRevision != overtimeAtStart && profile.export.automatic && !profile.export.pending {
                    updateCalendar(quiet: true)
                }
            } catch {
                busy = false
                message = error.localizedDescription
            }
        }
    }

    private func remove(_ category: ExportCategory, _ future: Bool) {
        guard !busy else { return }
        busy = true
        let snapshot = profile
        Task {
            defer { busy = false }
            do {
                try await exporter.remove(category: category, futureOnly: future, schedule: schedule, profile: snapshot)
                message = "Valda händelser har tagits bort."
            } catch {
                message = error.localizedDescription
            }
        }
    }
}

struct GestureGuide: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label("Tryck för att välja en dag", systemImage: "hand.tap")
            Label("Dubbeltryck i månadsvyn för att öppna veckan", systemImage: "hand.tap.fill")
            Label("Håll ned en dag eller välj Visa pass", systemImage: "hand.point.up.left")
            Label("Svep åt sidan för att byta månad eller vecka", systemImage: "arrow.left.and.right")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
