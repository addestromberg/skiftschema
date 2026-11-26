import SwiftUI

struct OnboardingView: View {
    let schedule: Schedule
    @Binding var profile: Profile
    @Environment(\.shiftPalette) private var c
    @State private var step = 0
    @State private var kind = ScheduleKind.five
    @State private var crew = "A"
    @State private var vacation = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                switch step {
                case 0:
                    title("Välj schema")
                    Text("Välj den skiftgång som gäller för dig.").foregroundStyle(c.muted)
                    ForEach(ScheduleKind.allCases) { choice in
                        option(choice.label, selected: kind == choice) {
                            if kind != choice { crew = "A"; vacation = "" }
                            kind = choice
                        }
                    }
                case 1:
                    title("Välj skiftlag")
                    ForEach(kind.teams, id: \.self) { team in
                        option("Skift \(team)", selected: crew == team) { crew = team }
                    }
                case 2:
                    let year = schedule.currentYear(kind)
                    title("Semestergrupp")
                    Text("Välj färgen som gäller för dig \(year.formatted(.number.grouping(.never))). Du kan också välja gruppen senare.")
                        .foregroundStyle(c.muted)
                    option("Ingen semestergrupp", selected: vacation.isEmpty) { vacation = "" }
                    ForEach(schedule.roster(kind, year)?.vacation ?? []) { group in
                        option(group.label, detail: group.detail, selected: vacation == group.id) { vacation = group.id }
                    }
                default:
                    title("Så använder du schemat")
                    GestureGuide()
                }

                Button(step == 3 ? "Visa schemat" : "Fortsätt", action: next)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .frame(maxWidth: .infinity)
            }
            .padding(22)
        }
        .foregroundStyle(c.ink)
        .background(c.canvas)
        .navigationTitle("Skiftschema")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if step > 0 {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Tillbaka") { step = step == 3 && kind == .six ? 1 : step - 1 }
                }
            }
        }
    }

    private func title(_ text: String) -> some View {
        Text(text).font(.system(size: 36, weight: .bold, design: .serif))
            .accessibilityAddTraits(.isHeader)
    }

    private func option(_ title: String, detail: String? = nil, selected: Bool,
                        action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    if let detail { Text(detail).font(.caption).foregroundStyle(c.muted) }
                }
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? c.action : c.edge)
            }
            .padding(14)
            .background(c.paper, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func next() {
        guard step == 3 else {
            step = step == 1 && kind == .six ? 3 : step + 1
            return
        }
        profile.kind = kind
        profile.crews[kind.rawValue] = crew
        if kind == .five && !vacation.isEmpty {
            profile.vacations[schedule.key(kind, schedule.currentYear(kind))] = vacation
        }
        profile.onboarded = true
    }
}

struct PreferencesView: View {
    let schedule: Schedule
    @Binding var profile: Profile
    let busy: Bool
    let sync: () -> Void
    let remove: (ExportCategory, Bool) -> Void
    let preferencesChanged: () -> Void
    @Environment(\.shiftPalette) private var c
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Form {
            Section("Ditt schema") {
                Picker("Schema", selection: $profile.kind) {
                    ForEach(ScheduleKind.allCases) { Text($0.label).tag($0) }
                }
                .onChange(of: profile.kind) { _, kind in
                    if profile.crews[kind.rawValue] == nil { profile.crews[kind.rawValue] = "A" }
                    preferencesChanged()
                }
                Picker("Skiftlag", selection: Binding(get: { profile.crew }, set: {
                    profile.crews[profile.kind.rawValue] = $0
                    preferencesChanged()
                })) {
                    ForEach(profile.kind.teams, id: \.self) { Text($0).tag($0) }
                }
                if profile.kind == .five {
                    NavigationLink("Semestergrupp") {
                        VacationSettings(schedule: schedule, profile: $profile, changed: preferencesChanged)
                    }
                }
            }.listRowBackground(c.paper)
            Section("App och kalender") {
                NavigationLink("Utseende") { appearance }
                NavigationLink("Kalenderexport") { exportSettings }
                NavigationLink("Avancerat") {
                    TimingSettings(schedule: schedule, profile: $profile, changed: preferencesChanged)
                }
                NavigationLink("Kalendergester") {
                    ScrollView { GestureGuide().padding(22) }
                        .background(c.canvas)
                        .navigationTitle("Kalendergester")
                }
            }.listRowBackground(c.paper)
        }
        .foregroundStyle(c.ink)
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .navigationTitle("Preferenser")
    }

    private var appearance: some View {
        Form {
            Picker("Välj tema", selection: $profile.appearance) {
                Text("System").tag("system")
                Text("Ljust").tag("light")
                Text("Mörkt").tag("dark")
            }.listRowBackground(c.paper)
            Section("Färgpalett") {
                ForEach(Palette.choices.map { $0.0 }, id: \.self) { id in
                    let label = Palette.choices.first { $0.0 == id }?.1 ?? id
                    let preview = Palette(id, dark: colorScheme == .dark)
                    Button { profile.palette = id } label: {
                        HStack {
                            Circle().fill(preview.action).frame(width: 18, height: 18)
                            Text(label).foregroundStyle(c.ink)
                            Spacer()
                            if profile.palette == id { Image(systemName: "checkmark") }
                        }
                    }
                    .accessibilityAddTraits(profile.palette == id ? .isSelected : [])
                }
            }.listRowBackground(c.paper)
        }
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .navigationTitle("Utseende")
    }

    private var exportSettings: some View {
        Form {
            Section {
                Toggle("Kalenderexport", isOn: $profile.export.enabled)
                if profile.export.enabled {
                    Toggle("Synk", isOn: $profile.export.automatic)
                        .onChange(of: profile.export.automatic) { _, enabled in
                            if enabled { preferencesChanged() }
                        }
                    if profile.export.pending {
                        Label("Ändringar väntar", systemImage: "exclamationmark.circle")
                            .foregroundStyle(c.vacInk)
                    }
                }
            } footer: {
                Text("Visa valda pass i telefonens kalender Skiftschema.")
            }.listRowBackground(c.paper)
            if profile.export.enabled {
                Section("Exportera") {
                    ForEach(ExportCategory.allCases) { category in
                        Toggle(category.title, isOn: Binding(
                            get: { profile.export.categories.contains(category.rawValue) },
                            set: { enabled in
                                if enabled { profile.export.categories.insert(category.rawValue) }
                                else { profile.export.categories.remove(category.rawValue) }
                                preferencesChanged()
                            }
                        ))
                    }
                    Picker("Påminnelser", selection: $profile.export.reminder) {
                        Text("Ingen påminnelse").tag(0)
                        ForEach([15, 30, 60, 120], id: \.self) { minutes in
                            Text("\(minutes) minuter före").tag(minutes)
                        }
                    }
                    .onChange(of: profile.export.reminder) { _, _ in preferencesChanged() }
                    Button(busy ? "Uppdaterar…" : "Uppdatera kalender", action: sync)
                        .disabled(busy || profile.export.categories.isEmpty)
                }.listRowBackground(c.paper)
            }
            if profile.export.calendarID != nil {
                NavigationLink("Hantera exporterade händelser") {
                    ExportManagement(busy: busy, remove: remove)
                }.listRowBackground(c.paper)
            }
        }
        .foregroundStyle(c.ink)
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .navigationTitle("Kalenderexport")
    }
}

struct VacationSettings: View {
    let schedule: Schedule
    @Binding var profile: Profile
    let changed: () -> Void
    @Environment(\.shiftPalette) private var c
    @State private var selectedYear: Int?

    var body: some View {
        let year = selectedYear ?? schedule.currentYear(.five)
        let key = schedule.key(.five, year)
        Form {
            Picker("Schemaår", selection: Binding(get: { year }, set: { selectedYear = $0 })) {
                ForEach(schedule.years(.five), id: \.self) { Text($0, format: .number.grouping(.never)).tag($0) }
            }.listRowBackground(c.paper)
            Section("Din grupp för året") {
                choice("Ingen semestergrupp", selected: profile.vacations[key] == nil) {
                    profile.vacations.removeValue(forKey: key)
                    changed()
                }
                ForEach(schedule.roster(.five, year)?.vacation ?? []) { group in
                    choice(group.label, detail: group.detail, color: c.vacationColor(group.id),
                           selected: profile.vacations[key] == group.id) {
                        profile.vacations[key] = group.id
                        changed()
                    }
                }
            }.listRowBackground(c.paper)
        }
        .foregroundStyle(c.ink)
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .navigationTitle("Semestergrupp")
    }

    private func choice(_ title: String, detail: String? = nil, color: Color? = nil,
                        selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                if let color { Circle().fill(color).frame(width: 14, height: 14) }
                VStack(alignment: .leading) {
                    Text(title).foregroundStyle(c.ink)
                    if let detail { Text(detail).font(.caption).foregroundStyle(c.muted) }
                }
                Spacer()
                if selected { Image(systemName: "checkmark") }
            }
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct TimingSettings: View {
    let schedule: Schedule
    @Binding var profile: Profile
    let changed: () -> Void
    @Environment(\.shiftPalette) private var c
    @State private var year: Int?
    @State private var draft: ShiftTimes?
    @State private var issue: String?
    @State private var reset = false

    var body: some View {
        let selected = year ?? schedule.currentYear(profile.kind)
        let times = draft ?? schedule.times(profile, profile.kind, selected)
        Form {
            Section("Schematider") {
                Picker("Schemaår", selection: Binding(get: { selected }, set: {
                    year = $0
                    draft = nil
                    issue = nil
                })) {
                    ForEach(schedule.years(profile.kind), id: \.self) {
                        Text($0, format: .number.grouping(.never)).tag($0)
                    }
                }
                timePicker("Bryttid för schemadag", \.cutoff, times)
            }.listRowBackground(c.paper)
            Section("Måndag–fredag") {
                timePicker("Skiftbyte FM till EM", \.weekdayEm, times)
                timePicker("Skiftbyte EM till NA", \.weekdayNa, times)
            }.listRowBackground(c.paper)
            Section("Lördag–söndag") {
                timePicker("Skiftbyte FM till NA", \.weekendNa, times)
            }.listRowBackground(c.paper)
            Section {
                if let issue { Text(issue).foregroundStyle(c.warnInk) }
                Button("Spara schematider") { save(times, year: selected) }
                Button("Återställ schematider") { reset = true }
            }.listRowBackground(c.paper)
        }
        .foregroundStyle(c.ink)
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .environment(\.timeZone, RosterDate.zone)
        .navigationTitle("Avancerat")
        .confirmationDialog("Återställ schematider?", isPresented: $reset, titleVisibility: .visible) {
            Button("Återställ") { save(nil, year: selected) }
        }
    }

    private func timePicker(_ label: String, _ field: WritableKeyPath<ShiftTimes, String>, _ times: ShiftTimes) -> some View {
        let day = RosterDate.make(2026, 1)
        return DatePicker(label, selection: Binding(
            get: { RosterDate.at(times[keyPath: field], on: day) },
            set: {
                var value = times
                value[keyPath: field] = RosterDate.clock($0)
                draft = value
                issue = nil
            }
        ), displayedComponents: .hourAndMinute)
    }

    private func save(_ times: ShiftTimes?, year: Int) {
        guard times?.valid != false else {
            issue = "Skiftbytena måste komma i ordning efter bryttiden."
            return
        }
        var candidate = profile
        candidate.overrides[schedule.key(profile.kind, year)] = times
        if let day = schedule.invalidDay(candidate, year: year) {
            issue = "Tiderna ger ett ogiltigt pass \(RosterDate.label(day))."
            return
        }
        profile = candidate
        changed()
        draft = nil
        issue = nil
    }
}

private struct ExportManagement: View {
    let busy: Bool
    let remove: (ExportCategory, Bool) -> Void
    @Environment(\.shiftPalette) private var c
    @State private var category: ExportCategory?

    var body: some View {
        Form {
            ForEach(ExportCategory.allCases) { item in
                Button(item.title) { category = item }.disabled(busy)
                    .listRowBackground(c.paper)
            }
        }
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .navigationTitle("Exporterade händelser")
        .confirmationDialog("Ta bort valda händelser?", isPresented: Binding(
            get: { category != nil }, set: { if !$0 { category = nil } }
        ), titleVisibility: .visible, presenting: category) { item in
            Button("Ta bort kommande händelser", role: .destructive) { remove(item, true) }
            Button("Ta bort alla exporterade", role: .destructive) { remove(item, false) }
        } message: { item in
            Text(item.title)
        }
    }
}
