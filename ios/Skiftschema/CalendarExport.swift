import EventKit
import Foundation

@MainActor
final class CalendarExport {
    private let store = EKEventStore()
    private let marker = "skiftschema"
    private let calendarName = "Skiftschema"

    func sync(schedule: Schedule, profile: Profile, calendarReady: (String) -> Void) async throws {
        try await authorize()
        let existing = profile.export.calendarID.flatMap(store.calendar(withIdentifier:))
        let calendar: EKCalendar
        if let existing, existing.allowsContentModifications {
            calendar = existing
        } else {
            guard let source = store.sources.first(where: { $0.sourceType == .local })
                    ?? store.defaultCalendarForNewEvents?.source else {
                throw ExportError.noCalendar
            }
            calendar = EKCalendar(for: .event, eventStore: store)
            calendar.title = calendarName
            calendar.source = source
            try store.saveCalendar(calendar, commit: true)
        }

        calendarReady(calendar.calendarIdentifier)
        let now = Date.now
        let active = ExportCategory.allCases.filter { profile.export.categories.contains($0.rawValue) }
        let desired = makeEvents(schedule: schedule, profile: profile, categories: active)
            .filter { $0.span.end > ($0.allDay ? Calendar.current.startOfDay(for: now) : now) }
        var existingEvents = events(in: calendar, schedule: schedule).filter {
            guard let category = category(for: $0) else { return false }
            return active.contains(category) && $0.endDate > ($0.isAllDay ? Calendar.current.startOfDay(for: now) : now)
        }
        do {
            for model in desired {
                let url = URL(string: "\(marker)://export/\(model.category.rawValue)/\(model.id)")!
                let event: EKEvent
                if let index = existingEvents.firstIndex(where: { $0.url == url }) {
                    event = existingEvents.remove(at: index)
                } else {
                    event = EKEvent(eventStore: store)
                    event.calendar = calendar
                    event.url = url
                }
                event.title = model.title
                event.timeZone = model.allDay ? nil : RosterDate.zone
                event.isAllDay = model.allDay
                event.startDate = model.span.start
                event.endDate = model.span.end
                let reminder = profile.export.reminder
                event.alarms = reminder > 0 && !model.allDay
                    ? [EKAlarm(relativeOffset: -Double(reminder * 60))] : nil
                try store.save(event, span: .thisEvent, commit: false)
            }
            for stale in existingEvents {
                try store.remove(stale, span: .thisEvent, commit: false)
            }
            try store.commit()
        } catch {
            store.reset()
            throw error
        }
    }

    func remove(category: ExportCategory, futureOnly: Bool, schedule: Schedule, profile: Profile) async throws {
        try await authorize()
        guard let id = profile.export.calendarID, let calendar = store.calendar(withIdentifier: id) else { return }
        let now = Date.now
        do {
            for event in events(in: calendar, schedule: schedule)
                where self.category(for: event) == category && (!futureOnly || isFuture(event, now: now)) {
                try store.remove(event, span: .thisEvent, commit: false)
            }
            try store.commit()
        } catch {
            store.reset()
            throw error
        }
    }

    private func isFuture(_ event: EKEvent, now: Date) -> Bool {
        if event.isAllDay {
            return Calendar.current.startOfDay(for: event.startDate) > Calendar.current.startOfDay(for: now)
        }
        return event.startDate > now
    }

    private func authorize() async throws {
        guard try await store.requestFullAccessToEvents() else { throw ExportError.permission }
    }

    private func category(for event: EKEvent) -> ExportCategory? {
        guard let url = event.url, url.scheme == marker, url.host == "export",
              url.pathComponents.count == 3 else { return nil }
        return ExportCategory(rawValue: url.pathComponents[1])
    }

    private func events(in calendar: EKCalendar, schedule: Schedule) -> [EKEvent] {
        let years = Set(ScheduleKind.allCases.flatMap { schedule.years($0) }).sorted()
        guard let first = years.first, let last = years.last else { return [] }
        return (first - 1...last + 1).flatMap { year in
            let start = RosterDate.make(year, 1)
            let end = RosterDate.make(year + 1, 1)
            let query = store.predicateForEvents(withStart: start, end: end, calendars: [calendar])
            // A night shift can overlap two queries. Use start dates to assign each event once.
            return store.events(matching: query).filter { $0.startDate >= start && $0.startDate < end }
        }
    }

    private struct Event {
        let id: String
        let category: ExportCategory
        let title: String
        let span: Span
        var allDay = false
    }

    private func makeEvents(schedule: Schedule, profile: Profile, categories: [ExportCategory]) -> [Event] {
        var output: [Event] = []
        if categories.contains(.regular) {
            for year in schedule.years(profile.kind) {
                for day in schedule.days(profile.kind, year: year) {
                    output += schedule.personal(profile, day).map {
                        Event(id: $0.shift.id, category: .regular,
                              title: "\($0.shift.slot.title) · Skift \($0.shift.crew)", span: $0.span)
                    }
                }
            }
        }
        if categories.contains(.overtime) {
            output += schedule.overtime(profile).map {
                Event(id: $0.shift.id, category: .overtime,
                      title: "Övertid · \($0.shift.slot.rawValue)", span: $0.span)
            }
        }
        if categories.contains(.vacation) {
            for year in schedule.years(.five) {
                guard profile.kind == .five,
                      let id = profile.vacations[schedule.key(.five, year)],
                      let group = schedule.roster(.five, year)?.vacation.first(where: { $0.id == id }) else { continue }
                for period in group.periods {
                    guard period.count == 2, let first = RosterDate.parse(period[0]),
                          let last = RosterDate.parse(period[1]) else { continue }
                    var local = Calendar(identifier: .gregorian)
                    local.timeZone = .current
                    func localDay(_ date: Date) -> Date {
                        local.date(from: RosterDate.calendar.dateComponents([.year, .month, .day], from: date))!
                    }
                    output.append(Event(id: period[0], category: .vacation, title: "Semester",
                                        span: Span(start: localDay(first), end: localDay(RosterDate.add(1, to: last))),
                                        allDay: true))
                }
            }
        }
        return output
    }

    private enum ExportError: LocalizedError {
        case permission, noCalendar
        var errorDescription: String? {
            switch self {
            case .permission: return "Tillåt kalenderåtkomst i telefonens inställningar."
            case .noCalendar: return "Ingen skrivbar kalenderkälla är tillgänglig."
            }
        }
    }
}
