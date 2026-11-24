import { addDays, categoryKeys, categoryLabel, civilDate, personalShifts, Schedule, shiftKey, slotLabel, State, vacationOn, yearKey, zone, type Category } from './domain';

export type CalendarEvent = {
  key: string;
  title: string;
  start: number;
  end: number;
  allDay: boolean;
  reminder: number | null;
};
export type NativeEvent = CalendarEvent & { id: string };
export type DeviceCalendar = {
  id: string;
  list(from: number, until: number): Promise<NativeEvent[]>;
  create(event: CalendarEvent): Promise<void>;
  update(id: string, event: CalendarEvent): Promise<void>;
  remove(id: string): Promise<void>;
};
export const calendarName = 'Skiftschema';
export const marker = 'Skiftschema/v1:';
export function ownedKey(value: string | null) {
  if (!value?.startsWith(marker)) return undefined;
  const key = value.slice(marker.length);
  return categoryKeys.some((category) => key.startsWith(category + ':')) ? key : undefined;
}
const utcDay = (value: string) => Date.parse(`${value}T00:00:00Z`);
export const eventZone = (event: CalendarEvent) => event.allDay ? 'UTC' : zone;

export function desiredEvents(state: State, schedules: Schedule[], now: number) {
  const { prefs } = state;
  const { categories, reminder } = prefs.export;
  const result: CalendarEvent[] = [];
  if (categories.overtime) {
    for (const item of Object.values(state.overtime)) {
      if (item.end > now) result.push({ key: `overtime:${shiftKey(item)}`, title: `Övertid · ${item.slot}`, start: item.start,
        end: item.end, allDay: false, reminder });
    }
  }
  for (const roster of schedules.filter((s) => s.id === prefs.unit)) {
    if (categories.regular) {
      for (const day of Object.keys(roster.days)) {
        if (day < addDays(civilDate(now), -1)) continue;
        for (const item of personalShifts(roster, day, state, schedules)) {
          if (item.end > now) result.push({ key: `regular:${shiftKey(item)}`, title: `${slotLabel[item.slot]} · Skift ${item.crew}`,
            start: item.start, end: item.end, allDay: false, reminder });
        }
      }
    }
    if (categories.vacation && roster.id === 'five') {
      const group = roster.vacation.find((g) => g.id === prefs.vacation[yearKey(roster.id, roster.year)]);
      for (const [start, end] of group?.periods ?? []) {
        if (!vacationOn(roster, prefs, start) || utcDay(addDays(end, 1)) <= now) continue;
        result.push({ key: `vacation:${roster.id}:${start}`, title: categoryLabel.vacation, start: utcDay(start),
          end: utcDay(addDays(end, 1)), allDay: true, reminder: null });
      }
    }
  }
  return result;
}

function range(schedules: Schedule[], now: number): [number, number] {
  const currentYear = new Date(now).getUTCFullYear();
  const years = [currentYear, ...schedules.map((s) => s.year)];
  return [utcDay(`${Math.min(...years) - 1}-01-01`), utcDay(`${Math.max(...years) + 2}-01-01`)];
}

export async function reconcile(calendar: DeviceCalendar, state: State, schedules: Schedule[], now: number) {
  const wanted = new Map(desiredEvents(state, schedules, now).map((event) => [event.key, event]));
  const existing = await calendar.list(...range(schedules, now));
  for (const event of existing) {
    const family = categoryKeys.find((category) => event.key.startsWith(category + ':'));
    if (!family || !state.prefs.export.categories[family] || event.end <= now) continue;
    const desired = wanted.get(event.key);
    if (!desired) {
      await calendar.remove(event.id);
      continue;
    }
    wanted.delete(event.key);
    if (event.title !== desired.title || event.start !== desired.start || event.end !== desired.end ||
        event.allDay !== desired.allDay || event.reminder !== desired.reminder) await calendar.update(event.id, desired);
  }
  for (const event of wanted.values()) await calendar.create(event);
}

export async function clearCategory(calendar: DeviceCalendar, category: Category, all: boolean, schedules: Schedule[], now: number) {
  for (const event of await calendar.list(...range(schedules, now))) {
    if (event.key.startsWith(category + ':') && (all || event.start > now)) await calendar.remove(event.id);
  }
}
