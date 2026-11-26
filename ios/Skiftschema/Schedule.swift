import Foundation

enum ScheduleKind: String, CaseIterable, Codable, Identifiable {
    case five, six
    var id: String { rawValue }
    var label: String { self == .five ? "5-skift" : "6-skift" }
    var teams: [String] { (self == .five ? "ABCDE" : "ABCDEF").map(String.init) }
}

enum Slot: String, CaseIterable, Identifiable {
    case fm = "FM", em = "EM", na = "NA"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .fm: return "Förmiddag"
        case .em: return "Eftermiddag"
        case .na: return "Natt"
        }
    }
}

enum RosterDate {
    static let zone = TimeZone(identifier: "Europe/Stockholm")!
    static let locale = Locale(identifier: "sv_SE")
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = zone
        calendar.locale = locale
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 4
        return calendar
    }()
    static let months = ["Januari", "Februari", "Mars", "April", "Maj", "Juni",
                         "Juli", "Augusti", "September", "Oktober", "November", "December"]
    static let weekdays = ["Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön"]

    static func day(_ date: Date) -> Date { calendar.startOfDay(for: date) }
    static func year(_ date: Date) -> Int { calendar.component(.year, from: date) }
    static func month(_ date: Date) -> Int { calendar.component(.month, from: date) }
    static func number(_ date: Date) -> Int { calendar.component(.day, from: date) }
    static func weekday(_ date: Date) -> Int { (calendar.component(.weekday, from: date) + 5) % 7 }
    static func weekend(_ date: Date) -> Bool { weekday(date) >= 5 }
    static func add(_ days: Int, to date: Date) -> Date {
        calendar.date(byAdding: .day, value: days, to: date)!
    }
    static func make(_ year: Int, _ month: Int, _ day: Int = 1) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
    static func key(_ date: Date) -> String {
        String(format: "%04d-%02d-%02d", year(date), month(date), number(date))
    }
    static func parse(_ key: String) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }
    static func label(_ date: Date) -> String {
        "\(weekdays[weekday(date)]) \(number(date)) \(months[month(date) - 1].lowercased())"
    }
    static func clock(_ date: Date) -> String {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour!, parts.minute!)
    }
    static func minutes(_ clock: String) -> Int {
        let parts = clock.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return 0 }
        return parts[0] * 60 + parts[1]
    }
    static func at(_ clock: String, on date: Date) -> Date {
        var parts = calendar.dateComponents([.year, .month, .day], from: date)
        let minute = minutes(clock)
        parts.hour = minute / 60
        parts.minute = minute % 60
        return calendar.date(from: parts)!
    }
    static func cells(_ anchor: Date, week: Bool) -> [Date] {
        let first = week ? day(anchor) : make(year(anchor), month(anchor))
        let offset = weekday(first)
        let days = calendar.range(of: .day, in: .month, for: first)!.count
        let count = week ? 7 : (offset + days + 6) / 7 * 7
        return (0..<count).map { add($0 - offset, to: first) }
    }
}

struct ShiftTimes: Codable, Equatable {
    var cutoff: String
    var weekdayEm: String
    var weekdayNa: String
    var weekendNa: String

    var valid: Bool {
        func offset(_ time: String) -> Int {
            (RosterDate.minutes(time) - RosterDate.minutes(cutoff) + 1440) % 1440
        }
        return offset(weekdayEm) > 0 && offset(weekdayNa) > offset(weekdayEm) && offset(weekendNa) > 0
    }
}

struct Vacation: Decodable, Identifiable {
    let id: String
    let label: String
    let periods: [[String]]

    var detail: String {
        periods.compactMap { period in
            guard period.count == 2, let first = RosterDate.parse(period[0]),
                  let last = RosterDate.parse(period[1]) else { return nil }
            return "\(RosterDate.label(first))–\(RosterDate.label(last))"
        }.joined(separator: ", ")
    }
}

struct Assignment: Decodable {
    let slots: [String: String]
    let closure: String?
    let note: String?
}

struct Roster: Decodable {
    let id: ScheduleKind
    let year: Int
    let times: ShiftTimes
    let vacation: [Vacation]
    let days: [String: Assignment]
}

struct Shift: Identifiable {
    let kind: ScheduleKind
    let day: Date
    let slot: Slot
    let crew: String
    var id: String { "\(kind.rawValue):\(RosterDate.key(day)):\(slot.rawValue)" }
}

struct Span: Codable, Equatable {
    var start: Date
    var end: Date
    var label: String {
        let suffix = RosterDate.day(start) == RosterDate.day(end) ? "" : " nästa dag"
        return "\(RosterDate.clock(start))–\(RosterDate.clock(end))\(suffix)"
    }

    func overlaps(_ other: Span) -> Bool { start < other.end && other.start < end }
}

struct Work {
    let shift: Shift
    let span: Span
    var overtime = false
}

struct Profile: Codable {
    var onboarded = false
    var kind = ScheduleKind.five
    var crews: [String: String] = [:]
    var vacations: [String: String] = [:]
    var overrides: [String: ShiftTimes] = [:]
    var overtime: [String: Span] = [:]
    var appearance = "system"
    var palette = "forest"
    var export = ExportSettings()

    var crew: String { crews[kind.rawValue] ?? "A" }
}

enum ExportCategory: String, CaseIterable, Identifiable {
    case regular, overtime, vacation
    var id: String { rawValue }
    var title: String {
        switch self {
        case .regular: return "Ordinarie pass"
        case .overtime: return "Övertid"
        case .vacation: return "Semester"
        }
    }
}

struct ExportSettings: Codable {
    var enabled = false
    var automatic = false
    var pending = false
    var categories: Set<String> = ["regular"]
    var reminder = 0
    var calendarID: String?
}

struct Schedule {
    private let rosters: [Roster]

    init(bundle: Bundle = .main) throws {
        guard let directory = bundle.resourceURL?.appendingPathComponent("data") else {
            throw CocoaError(.fileNoSuchFile)
        }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        rosters = try files.filter {
            $0.lastPathComponent.range(of: #"^(five|six)-[0-9]{4}\.json$"#, options: .regularExpression) != nil
        }.map { try JSONDecoder().decode(Roster.self, from: Data(contentsOf: $0)) }
        guard !rosters.isEmpty else { throw CocoaError(.fileNoSuchFile) }
    }

    func years(_ kind: ScheduleKind) -> [Int] {
        rosters.filter { $0.id == kind }.map(\.year).sorted()
    }
    func currentYear(_ kind: ScheduleKind, now: Date = .now) -> Int {
        let available = years(kind)
        let current = RosterDate.year(now)
        return available.contains(current) ? current : available.last ?? current
    }
    func roster(_ kind: ScheduleKind, _ year: Int) -> Roster? {
        rosters.first { $0.id == kind && $0.year == year }
    }
    func key(_ kind: ScheduleKind, _ year: Int) -> String { "\(kind.rawValue):\(year)" }
    func assignment(_ kind: ScheduleKind, _ day: Date) -> Assignment? {
        roster(kind, RosterDate.year(day))?.days[RosterDate.key(day)]
    }
    func shifts(_ kind: ScheduleKind, _ day: Date) -> [Shift] {
        guard let entry = assignment(kind, day) else { return [] }
        return Slot.allCases.compactMap { slot in
            entry.slots[slot.rawValue].map { Shift(kind: kind, day: day, slot: slot, crew: $0) }
        }
    }
    func times(_ profile: Profile, _ kind: ScheduleKind, _ year: Int) -> ShiftTimes {
        profile.overrides[key(kind, year)] ?? roster(kind, year)?.times ??
            ShiftTimes(cutoff: "06:00", weekdayEm: kind == .five ? "14:00" : "13:00",
                       weekdayNa: kind == .five ? "22:00" : "21:00", weekendNa: "18:00")
    }
    func activeDay(_ profile: Profile, now: Date) -> Date {
        let day = RosterDate.day(now)
        let cutoff = times(profile, profile.kind, RosterDate.year(day)).cutoff
        return now < RosterDate.at(cutoff, on: day) ? RosterDate.add(-1, to: day) : day
    }
    func span(_ profile: Profile, _ shift: Shift) -> Span {
        let day = shift.day
        let rule = times(profile, shift.kind, RosterDate.year(day))
        let weekend = RosterDate.weekend(day)
        let night = weekend ? rule.weekendNa : rule.weekdayNa
        func transition(_ time: String) -> Date {
            RosterDate.at(time, on: time < rule.cutoff ? RosterDate.add(1, to: day) : day)
        }
        switch shift.slot {
        case .fm: return Span(start: transition(rule.cutoff), end: transition(weekend ? night : rule.weekdayEm))
        case .em: return Span(start: transition(rule.weekdayEm), end: transition(night))
        case .na:
            let next = RosterDate.add(1, to: day)
            return Span(start: transition(night),
                        end: RosterDate.at(times(profile, shift.kind, RosterDate.year(next)).cutoff, on: next))
        }
    }
    func vacation(_ profile: Profile, _ day: Date) -> Vacation? {
        let year = RosterDate.year(day)
        let date = RosterDate.key(day)
        let group = profile.vacations[key(profile.kind, year)]
        return roster(profile.kind, year)?.vacation.first {
            $0.id == group && $0.periods.contains { $0.count == 2 && $0[0] <= date && date <= $0[1] }
        }
    }
    func personal(_ profile: Profile, _ day: Date) -> [Work] {
        guard vacation(profile, day) == nil else { return [] }
        return shifts(profile.kind, day).filter { $0.crew == profile.crew }.map {
            Work(shift: $0, span: span(profile, $0))
        }
    }
    func days(_ kind: ScheduleKind, year: Int) -> [Date] {
        roster(kind, year)?.days.keys.sorted().compactMap(RosterDate.parse) ?? []
    }
    func overtime(_ profile: Profile) -> [Work] {
        profile.overtime.compactMap { key, span in
            let parts = key.split(separator: ":").map(String.init)
            guard parts.count == 3, let kind = ScheduleKind(rawValue: parts[0]), let day = RosterDate.parse(parts[1]),
                  let shift = shifts(kind, day).first(where: { $0.id == key }) else { return nil }
            return Work(shift: shift, span: span, overtime: true)
        }
    }
    func next(_ profile: Profile, now: Date) -> Work? {
        let extra = overtime(profile).filter { $0.span.start > now }.min { $0.span.start < $1.span.start }
        let active = activeDay(profile, now: now)
        for year in years(profile.kind) where year >= RosterDate.year(active) {
            for day in days(profile.kind, year: year) where day >= active {
                if let work = personal(profile, day).first(where: { $0.span.start > now }) {
                    if let extra, extra.span.start < work.span.start { return extra }
                    return work
                }
            }
        }
        return extra
    }
    func conflict(_ profile: Profile, shift: Shift, span: Span) -> Bool {
        if profile.overtime.contains(where: { $0.key != shift.id && $0.value.overlaps(span) }) { return true }
        return (-1...1).contains { offset in
            personal(profile, RosterDate.add(offset, to: shift.day)).contains { $0.span.overlaps(span) }
        }
    }

    func invalidDay(_ profile: Profile, year: Int) -> Date? {
        let boundary = RosterDate.add(-1, to: RosterDate.make(year, 1))
        return ([boundary] + days(profile.kind, year: year)).first { day in
            shifts(profile.kind, day).contains {
                let value = span(profile, $0)
                return value.start >= value.end
            }
        }
    }
}
