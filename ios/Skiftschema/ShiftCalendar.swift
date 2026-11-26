import SwiftUI

struct ShiftCalendar: View {
    let schedule: Schedule
    @Binding var profile: Profile
    let overtimeChanged: () -> Void
    let showPreferences: () -> Void

    @Environment(\.shiftPalette) private var c
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var anchor: Date?
    @State private var selected: Date?
    @State private var week = false
    @State private var previousMonth: Date?
    @State private var previousSelection: Date?
    @State private var presentedDay: Date?
    @State private var editingShift: Shift?
    @State private var showMonths = false
    @State private var pickerYear = 2026

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { context in
            let today = schedule.activeDay(profile, now: context.date)
            let focus = anchor ?? today
            ScrollView {
                VStack(alignment: .leading, spacing: 13) {
                    hero(now: context.date).padding(.horizontal, 4)
                    navigation(focus, today: today)
                    modes(focus)
                    grid(focus, today: today)
                    footer(focus)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 16)
            }
            .background(c.canvas)
        }
        .foregroundStyle(c.ink)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                HStack(spacing: 9) {
                    Image(systemName: "calendar")
                        .foregroundStyle(c.onAction)
                        .frame(width: 30, height: 30)
                        .background(c.action, in: RoundedRectangle(cornerRadius: 8))
                        .accessibilityHidden(true)
                    Text("Skiftschema").font(.headline).foregroundStyle(c.ink)
                        .accessibilityAddTraits(.isHeader)
                }
            }
        }
        .sheet(isPresented: $showMonths) { monthPicker }
        .sheet(isPresented: Binding(get: { presentedDay != nil }, set: {
            if !$0 { presentedDay = nil; editingShift = nil }
        })) {
            if let day = presentedDay {
                NavigationStack {
                    if let shift = editingShift {
                        OvertimeEditor(schedule: schedule, profile: $profile, shift: shift) {
                            editingShift = nil
                            overtimeChanged()
                        } back: {
                            editingShift = nil
                        }
                    } else {
                        dayDetails(day)
                    }
                }
                .presentationDragIndicator(.visible)
                .interactiveDismissDisabled(editingShift != nil)
            }
        }
    }

    private func hero(now: Date) -> some View {
        let work = schedule.next(profile, now: now)
        let days = work.map {
            RosterDate.calendar.dateComponents([.day], from: RosterDate.day(now),
                                               to: RosterDate.day($0.span.start)).day ?? 0
        }
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Nästa pass").font(.caption.bold()).foregroundStyle(c.heroMuted)
                Spacer()
                if let days {
                    Text(days == 0 ? "Idag" : days == 1 ? "Imorgon" : "Om \(days) dagar")
                        .font(.caption.bold())
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(c.heroBadge, in: Capsule())
                        .foregroundStyle(c.heroBadgeInk)
                }
            }
            HStack {
                if let work {
                    Text("\(RosterDate.label(work.span.start)), \(RosterDate.clock(work.span.start))")
                } else {
                    Text("–")
                }
                Spacer()
                Image(systemName: "clock").font(.title3).foregroundStyle(c.heroMuted)
            }
            .font(.system(size: 25, weight: .bold))
            .foregroundStyle(c.heroMain)
            if let work {
                Text("\(work.overtime ? "Övertid" : work.shift.slot.title) · Skift \(work.shift.crew)")
                    .font(.caption)
                    .foregroundStyle(c.heroMuted)
            }
        }
        .padding(15)
        .frame(minHeight: 96)
        .background(c.hero, in: RoundedRectangle(cornerRadius: 20))
    }

    private func navigation(_ focus: Date, today: Date) -> some View {
        let monthTitle = "\(RosterDate.months[RosterDate.month(focus) - 1]) \(RosterDate.year(focus))"
        let weekNumber = RosterDate.calendar.component(.weekOfYear, from: focus)
        return HStack(spacing: 4) {
            Button {
                pickerYear = RosterDate.year(focus)
                showMonths = true
            } label: {
                HStack(spacing: 4) {
                    Text(week ? "Vecka \(weekNumber)" : monthTitle)
                        .font(.system(size: 27, weight: .bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.66)
                    Image(systemName: "chevron.down").font(.caption.bold()).foregroundStyle(c.muted)
                }
                .foregroundStyle(c.ink)
            }
            .accessibilityHint("Gå till månad")
            Spacer(minLength: 2)
            arrow("chevron.left", week ? "Föregående vecka" : "Föregående månad") { move(-1, focus) }
            Button("Idag") { navigate(today) }
                .font(.caption.bold()).padding(.horizontal, 8).frame(minHeight: 36)
                .background(c.paper, in: Capsule())
            arrow("chevron.right", week ? "Nästa vecka" : "Nästa månad") { move(1, focus) }
        }
        .buttonStyle(.plain)
    }

    private func arrow(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.footnote.bold())
                .frame(width: 36, height: 40)
                .background(c.paper, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func modes(_ focus: Date) -> some View {
        HStack {
            HStack(spacing: 0) {
                mode("Månad", active: !week) {
                    guard week else { return }
                    navigate(selected ?? previousMonth ?? focus, selection: selected ?? previousSelection, showWeek: false)
                }
                mode("Vecka", active: week) {
                    guard !week else { return }
                    showWeek(from: focus, day: selected ?? focus, selection: selected)
                }
            }
            .padding(4)
            .background(c.subtle, in: RoundedRectangle(cornerRadius: 12))
            Spacer()
            Button(action: showPreferences) {
                Text("\(profile.kind.label) · \(profile.crew)")
                    .font(.caption.bold())
                    .padding(.horizontal, 10).padding(.vertical, 7)
                    .background(c.paper, in: Capsule())
            }
            .accessibilityHint("Öppnar preferenser")
        }
        .buttonStyle(.plain)
    }

    private func mode(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.caption.bold())
                .frame(width: 70, height: 30)
                .foregroundStyle(active ? c.ink : c.muted)
                .background(active ? c.paper : c.subtle, in: RoundedRectangle(cornerRadius: 9))
        }
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    private func grid(_ focus: Date, today: Date) -> some View {
        let days = RosterDate.cells(focus, week: week)
        return VStack(alignment: .leading, spacing: 5) {
            if week, let first = days.first, let last = days.last {
                Text("\(RosterDate.label(first)) – \(RosterDate.label(last)) \(RosterDate.year(last).formatted(.number.grouping(.never)))")
                    .font(.caption).foregroundStyle(c.muted)
                ForEach(days, id: \.self) { day in cell(day, focus: focus, today: today) }
            } else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(minimum: 0), spacing: 3), count: 7), spacing: 5) {
                    ForEach(RosterDate.weekdays, id: \.self) {
                        Text($0).font(.caption2.bold()).foregroundStyle(c.muted)
                    }
                    ForEach(days, id: \.self) { day in cell(day, focus: focus, today: today) }
                }
            }
        }
        .overlay {
            if !covered(focus) {
                Label("Inget schema för perioden", systemImage: "calendar")
                    .font(.headline).padding(18)
                    .background(c.paper, in: RoundedRectangle(cornerRadius: 16))
                    .allowsHitTesting(false)
            }
        }
        .contentShape(Rectangle())
        .simultaneousGesture(DragGesture(minimumDistance: 20).onEnded { value in
            let x = value.translation.width
            let y = value.translation.height
            if abs(x) > 45 && abs(x) > abs(y) * 1.5 { move(x < 0 ? 1 : -1, focus) }
        })
    }

    private func cell(_ day: Date, focus: Date, today: Date) -> some View {
        let assignment = schedule.assignment(profile.kind, day)
        let shifts = schedule.shifts(profile.kind, day)
        let vacation = schedule.vacation(profile, day)
        let inMonth = week || RosterDate.month(day) == RosterDate.month(focus)
        let current = day == today
        let chosen = day == selected
        let extra = shifts.contains { profile.overtime[$0.id] != nil }
        let fill = vacation != nil ? c.vac : chosen || current ? c.mine :
            !inMonth ? c.subtle : RosterDate.weekend(day) ? c.weekend : c.paper
        let shape = RoundedRectangle(cornerRadius: week ? 12 : 8)
        let content = cellContents(day, today: current, inMonth: inMonth, extra: extra,
                                   closure: assignment?.closure, shifts: shifts, vacation: vacation)
        let border = chosen || current ? c.action : c.line
        let borderWidth: CGFloat = chosen ? 2 : current ? 1.3 : 1
        let styled = content
            .padding(week ? 6 : 3)
            .frame(maxWidth: .infinity, minHeight: week ? 58 : 73, alignment: week ? .center : .topLeading)
            .background(fill, in: shape)
            .overlay(shape.strokeBorder(border, lineWidth: borderWidth))
            .contentShape(Rectangle())

        let doubleTap = TapGesture(count: 2).onEnded {
            if week { selected = day } else { showWeek(from: focus, day: day, selection: day) }
        }
        let tap = TapGesture().onEnded { selected = day }
        let longPress = LongPressGesture(minimumDuration: 0.45).onEnded { _ in open(day) }
        let gestures = longPress.exclusively(before: doubleTap.exclusively(before: tap))

        var description = [RosterDate.label(day)]
        if vacation != nil {
            description.append("Semester")
        } else if let closure = assignment?.closure {
            description.append(closure)
        }
        description += shifts.map { "\($0.slot.rawValue) skift \($0.crew)" }
        if extra { description.append("Övertid") }

        return styled
            .gesture(gestures)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(description.joined(separator: ", "))
            .accessibilityAddTraits(chosen ? [.isButton, .isSelected] : .isButton)
            .accessibilityAction { selected = day }
            .accessibilityAction(named: Text("Visa pass")) { open(day) }
    }

    private func cellContents(_ day: Date, today: Bool, inMonth: Bool, extra: Bool,
                              closure: String?, shifts: [Shift], vacation: Vacation?) -> some View {
        let layout = week ? AnyLayout(HStackLayout(spacing: 5)) : AnyLayout(VStackLayout(alignment: .leading, spacing: 2))
        return layout {
            dayLabel(day, current: today, inMonth: inMonth, extra: extra, vacation: vacation)
            if let closure {
                Text(vacation == nil ? closure : "Semester")
                    .font(.system(size: week ? 12 : 8, weight: .semibold))
                    .foregroundStyle(vacation == nil ? c.muted : c.vacInk)
                    .lineLimit(2).minimumScaleFactor(0.65)
                    .frame(maxWidth: .infinity)
            } else if week {
                ForEach(Slot.allCases) { slot in
                    if let shift = shifts.first(where: { $0.slot == slot }) {
                        shiftLabel(shift, vacation: vacation != nil)
                    } else {
                        Text("–").font(.caption).foregroundStyle(c.quiet).frame(maxWidth: .infinity)
                    }
                }
            } else {
                ForEach(shifts) { shiftLabel($0, vacation: vacation != nil) }
            }
        }
    }

    private func dayLabel(_ day: Date, current: Bool, inMonth: Bool, extra: Bool, vacation: Vacation?) -> some View {
        let layout = week ? AnyLayout(VStackLayout(spacing: 1)) : AnyLayout(HStackLayout(spacing: 2))
        return layout {
            if week {
                Text(RosterDate.weekdays[RosterDate.weekday(day)]).font(.caption2.bold()).foregroundStyle(c.muted)
            }
            Text(RosterDate.number(day), format: .number)
                .font(.system(size: week ? 14 : 11, weight: .bold))
                .foregroundStyle(current ? c.onAction : inMonth ? c.ink : c.quiet)
                .frame(minWidth: 17, minHeight: 17)
                .background(current ? c.action : .clear, in: Capsule())
            if !week { Spacer(minLength: 0) }
            if let vacation {
                Text("S").font(.system(size: 8, weight: .bold)).foregroundStyle(.black)
                    .padding(.horizontal, 3)
                    .background(c.vacationColor(vacation.id), in: RoundedRectangle(cornerRadius: 3))
            } else if !week && extra {
                Text("ÖT").font(.system(size: 8, weight: .bold)).foregroundStyle(c.otInk)
            }
        }
        .frame(width: week ? 34 : nil)
    }

    private func shiftLabel(_ shift: Shift, vacation: Bool) -> some View {
        let overtime = profile.overtime[shift.id]
        let own = !vacation && shift.crew == profile.crew
        let fill = overtime != nil ? c.ot : own ? c.mine : week ? c.subtle : .clear
        let ink = overtime != nil ? c.otInk : own ? c.mineInk : c.ink
        return VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 2) {
                Text(shift.slot.rawValue).font(.system(size: week ? 11 : 8, weight: .bold))
                    .foregroundStyle(overtime != nil || own ? ink : c.muted)
                if !week { Spacer(minLength: 0) }
                Text(shift.crew).font(.system(size: week ? 13 : 10, weight: .bold)).foregroundStyle(ink)
                if week && overtime != nil {
                    Text("ÖT").font(.system(size: 9, weight: .bold)).foregroundStyle(c.otInk)
                }
            }
            if week {
                Text(RosterDate.clock(overtime?.start ?? schedule.span(profile, shift).start))
                    .font(.system(size: 10, design: .monospaced)).foregroundStyle(c.muted)
            }
        }
        .lineLimit(1).minimumScaleFactor(0.66)
        .padding(.horizontal, week ? 5 : 2)
        .frame(maxWidth: .infinity, minHeight: week ? 44 : 14, alignment: .leading)
        .background(fill, in: RoundedRectangle(cornerRadius: week ? 7 : 4))
    }

    @ViewBuilder private func footer(_ focus: Date) -> some View {
        if let selected {
            HStack {
                Text("Vald dag · \(RosterDate.label(selected))").font(.subheadline.bold())
                Spacer()
                Button("Visa pass") { open(selected) }
                    .buttonStyle(.bordered)
            }
            .foregroundStyle(c.mineInk)
            .padding(10)
            .background(c.mine, in: RoundedRectangle(cornerRadius: 16))
        } else if covered(focus) {
            HStack(spacing: 13) {
                legend("Ditt pass", fill: c.mine, edge: c.mineInk)
                if profile.kind == .five { legend("Semester", fill: c.vac, edge: c.vacInk) }
                legend("Övertid", fill: c.ot, edge: c.otInk)
            }
        }
    }

    private func legend(_ title: String, fill: Color, edge: Color) -> some View {
        HStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 4).fill(fill)
                .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(edge))
                .frame(width: 12, height: 12)
            Text(title).font(.caption2.bold()).foregroundStyle(c.muted)
        }
    }

    private var monthPicker: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack {
                    arrow("chevron.left", "Föregående år") { pickerYear = max(1900, pickerYear - 1) }
                    Spacer()
                    Text(pickerYear, format: .number.grouping(.never)).font(.title2.bold())
                    Spacer()
                    arrow("chevron.right", "Nästa år") { pickerYear = min(9999, pickerYear + 1) }
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4)) {
                    ForEach(1...12, id: \.self) { month in
                        Button {
                            navigate(RosterDate.make(pickerYear, month), showWeek: false)
                            showMonths = false
                        } label: {
                            Text(String(RosterDate.months[month - 1].prefix(3)))
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(c.subtle, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(18)
            .foregroundStyle(c.ink)
            .background(c.canvas)
            .navigationTitle("Gå till månad")
           .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Stäng") { showMonths = false } } }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private func dayDetails(_ day: Date) -> some View {
        let entry = schedule.assignment(profile.kind, day)
        let vacation = schedule.vacation(profile, day)
        let shifts = schedule.shifts(profile.kind, day)
        return ScrollView {
            VStack(alignment: .leading, spacing: 9) {
                Text(shifts.isEmpty ? profile.kind.label : "\(profile.kind.label) · Välj pass för övertid")
                    .font(.subheadline).foregroundStyle(c.muted)
                if let vacation {
                    Label("Semester · \(vacation.label)", systemImage: "sun.max")
                        .font(.headline)
                        .foregroundStyle(c.vacInk)
                        .padding(11)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(c.vac, in: RoundedRectangle(cornerRadius: 12))
                }
                if shifts.isEmpty && vacation == nil {
                    Text(entry?.closure ?? (entry == nil ? "Inget schema tillgängligt" : "Inga pass"))
                        .font(.headline)
                }
                ForEach(shifts) { shift in
                    let own = shift.crew == profile.crew
                    let extra = profile.overtime[shift.id]
                    if !own || extra != nil {
                        Button { editingShift = shift } label: {
                            shiftCard(shift, own: own, extra: extra)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(extra == nil ? "Markera övertid" : "Hantera övertid")
                    } else {
                        shiftCard(shift, own: own, extra: extra)
                    }
                }
                if let note = entry?.note {
                    Text("Schemaanteckning · \(note)").font(.caption).foregroundStyle(c.muted)
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(c.ink)
        .background(c.canvas)
        .navigationTitle(RosterDate.label(day))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Stäng") { presentedDay = nil } } }
    }

    private func shiftCard(_ shift: Shift, own: Bool, extra: Span?) -> some View {
        let fill = extra != nil ? c.ot : own ? c.mine : c.paper
        let shape = RoundedRectangle(cornerRadius: 14)
        return HStack(spacing: 11) {
            Text(shift.slot.rawValue)
                .font(.caption.bold())
                .foregroundStyle(extra != nil ? c.otInk : c.muted)
                .frame(width: 36, height: 34)
                .background(extra != nil ? c.paper : c.subtle, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 3) {
                Text("\(shift.slot.title) · Skiftlag \(shift.crew)").font(.subheadline.bold())
                Text(schedule.span(profile, shift).label).foregroundStyle(c.muted).font(.caption)
                if own { Text("Ditt skiftlag").font(.caption.bold()).foregroundStyle(c.mineInk) }
                if let extra { Text("Övertid · \(extra.label)").font(.caption.bold()).foregroundStyle(c.otInk) }
            }
            Spacer(minLength: 0)
            if !own || extra != nil {
                Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(c.muted)
            }
        }
        .padding(12)
        .frame(minHeight: 64)
        .background(fill, in: shape)
        .overlay(shape.strokeBorder(c.line))
        .accessibilityElement(children: .combine)
    }

    private func covered(_ focus: Date) -> Bool {
        if !week { return schedule.roster(profile.kind, RosterDate.year(focus)) != nil }
        return RosterDate.cells(focus, week: true).contains { schedule.assignment(profile.kind, $0) != nil }
    }

    private func open(_ day: Date) {
        selected = day
        presentedDay = day
    }

    private func showWeek(from focus: Date, day: Date, selection: Date?) {
        previousMonth = focus
        previousSelection = selected
        navigate(day, selection: selection, showWeek: true)
    }

    private func move(_ direction: Int, _ focus: Date) {
        let component: Calendar.Component = week ? .weekOfYear : .month
        if let date = RosterDate.calendar.date(byAdding: component, value: direction, to: focus) { navigate(date) }
    }

    private func navigate(_ day: Date, selection: Date? = nil, showWeek: Bool? = nil) {
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) {
            anchor = day
            selected = selection
            if let showWeek { week = showWeek }
        }
    }
}

struct OvertimeEditor: View {
    let schedule: Schedule
    @Binding var profile: Profile
    let shift: Shift
    let complete: () -> Void
    let back: () -> Void

    @Environment(\.shiftPalette) private var c
    @State private var span: Span
    @State private var partial: Bool
    @State private var confirmRemoval = false
    @State private var issue: String?
    private let full: Span

    init(schedule: Schedule, profile: Binding<Profile>, shift: Shift,
         complete: @escaping () -> Void, back: @escaping () -> Void) {
        self.schedule = schedule
        _profile = profile
        self.shift = shift
        self.complete = complete
        self.back = back
        let official = schedule.span(profile.wrappedValue, shift)
        full = official
        let existing = profile.wrappedValue.overtime[shift.id]
        let initial = existing.flatMap { value in
            let start = max(value.start, official.start)
            let end = min(value.end, official.end)
            return start < end ? Span(start: start, end: end) : nil
        }
        _span = State(initialValue: initial ?? official)
        _partial = State(initialValue: existing.map { $0 != official } ?? false)
    }

    var body: some View {
        Form {
            Section {
                Text("\(shift.slot.title) · Skift \(shift.crew)").font(.headline)
                Text(full.label).foregroundStyle(c.muted)
            }.listRowBackground(c.paper)
            if shift.crew != profile.crew && full.start < full.end {
                Section {
                    Picker("Omfattning", selection: $partial) {
                        Text("Hela passet").tag(false)
                        Text("Del av passet").tag(true)
                    }
                    .pickerStyle(.segmented)
                    if partial {
                        DatePicker("Start", selection: $span.start, in: full.start...full.end)
                        DatePicker("Slut", selection: $span.end, in: full.start...full.end)
                    }
                    if let issue { Text(issue).foregroundStyle(c.warnInk) }
                    Button("Spara övertid", action: save).fontWeight(.semibold)
                }.listRowBackground(c.paper)
            }
            if profile.overtime[shift.id] != nil {
                Button("Ta bort övertid", role: .destructive) { confirmRemoval = true }
                    .listRowBackground(c.paper)
            }
        }
        .foregroundStyle(c.ink)
        .scrollContentBackground(.hidden)
        .background(c.canvas)
        .environment(\.timeZone, RosterDate.zone)
        .environment(\.calendar, RosterDate.calendar)
        .navigationTitle("Övertid")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Tillbaka", action: back) } }
        .confirmationDialog("Ta bort övertid?", isPresented: $confirmRemoval, titleVisibility: .visible) {
            Button("Ta bort övertid", role: .destructive) {
                profile.overtime.removeValue(forKey: shift.id)
                complete()
            }
        }
    }

    private func save() {
        let value = partial ? span : full
        guard value.start >= full.start, value.end <= full.end, value.start < value.end else {
            issue = "Välj en start- och sluttid inom passet."
            return
        }
        guard !schedule.conflict(profile, shift: shift, span: value) else {
            issue = "Övertiden överlappar ett annat pass."
            return
        }
        profile.overtime[shift.id] = value
        complete()
    }
}
