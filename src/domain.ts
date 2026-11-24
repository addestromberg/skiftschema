import { Temporal } from '@js-temporal/polyfill';

export const zone = 'Europe/Stockholm';
export const slots = ['FM', 'EM', 'NA'] as const;
export const slotLabel = { FM: 'Förmiddag', EM: 'Eftermiddag', NA: 'Natt' };
export const months = ['Januari', 'Februari', 'Mars', 'April', 'Maj', 'Juni', 'Juli', 'Augusti', 'September', 'Oktober', 'November', 'December'];
export const weekdays = ['Mån', 'Tis', 'Ons', 'Tor', 'Fre', 'Lör', 'Sön'];
export type Slot = typeof slots[number];
export type Unit = 'five' | 'six';
export type VacationColor = 'blue' | 'red' | 'yellow' | 'green';
export type Appearance = 'system' | 'light' | 'dark';
export type Accent = 'forest' | 'ocean' | 'plum' | 'amber' | 'nord' | 'deep-one';
export type Category = 'regular' | 'overtime' | 'vacation';
export type Period = readonly [string, string];
export type Times = { cutoff: string; weekdayEm: string; weekdayNa: string; weekendNa: string };
export type Assignment = { slots: Partial<Record<Slot, string>>; closure?: string; note?: string };
export type VacationGroup = { id: VacationColor; label: string; periods: readonly Period[] };
export type Schedule = {
  schemaVersion: 1;
  id: Unit;
  year: number;
  label: string;
  teams: string[];
  times: Times;
  vacation: VacationGroup[];
  days: Record<string, Assignment>;
};
export type ShiftRef = { unit: Unit; date: string; slot: Slot; crew: string };
export type Span = { start: number; end: number };
export type Overtime = ShiftRef & Span;
export type ExportSettings = {
  enabled: boolean;
  sync: boolean;
  pending: boolean;
  categories: Record<Category, boolean>;
  reminder: number | null;
  calendarId: string | null;
  rejected: boolean;
};
export type Preferences = {
  unit: Unit | null;
  crews: Partial<Record<Unit, string>>;
  vacation: Record<string, VacationColor | null>;
  overrides: Record<string, Times>;
  appearance: Appearance;
  accent: Accent;
  export: ExportSettings;
};
export type State = { prefs: Preferences; overtime: Record<string, Overtime> };
export const initialState: State = {
  prefs: {
    unit: null, crews: {}, vacation: {}, overrides: {}, appearance: 'system', accent: 'forest',
    export: { enabled: false, sync: false, pending: false, categories: { regular: true, overtime: true, vacation: false },
      reminder: null, calendarId: null, rejected: false },
  },
  overtime: {},
};
export const unitLabel = { five: '5-skift', six: '6-skift' };
export const categoryLabel = { regular: 'Ordinarie pass', overtime: 'Övertid', vacation: 'Semester' };
export const categoryKeys = ['regular', 'overtime', 'vacation'] as const;

export const date = (value: string) => Temporal.PlainDate.from(value);
export const yearOf = (value: string) => date(value).year;
export const addDays = (value: string, days: number) => date(value).add({ days }).toString();
export const addMonths = (value: string, count: number) => date(value).add({ months: count }).toString();
export const weekStart = (value: string) => addDays(value, 1 - date(value).dayOfWeek);
export const monthStart = (value: string) => date(value).with({ day: 1 }).toString();
export const daysBetween = (start: string, end: string) => date(start).until(date(end), { largestUnit: 'day' }).days;
export const yearKey = (unit: Unit, year: number) => `${unit}:${year}`;
export const shiftKey = ({ unit, date: day, slot }: ShiftRef) => `${unit}:${day}:${slot}`;
export const spanOverlaps = (a: Span, b: Span) => a.start < b.end && b.start < a.end;
const two = (value: number) => String(value).padStart(2, '0');
const clockPattern = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const defaultTimes: Record<Unit, Times> = {
  five: { cutoff: '06:00', weekdayEm: '14:00', weekdayNa: '22:00', weekendNa: '18:00' },
  six: { cutoff: '06:00', weekdayEm: '13:00', weekdayNa: '21:00', weekendNa: '18:00' },
};

export function findRoster(schedules: Schedule[], unit: Unit, year: number) {
  return schedules.find((item) => item.id === unit && item.year === year);
}
export function rosterTimes(schedules: Schedule[], prefs: Preferences, unit: Unit, day: string): Times {
  const year = yearOf(day);
  return prefs.overrides[yearKey(unit, year)] || findRoster(schedules, unit, year)?.times || defaultTimes[unit];
}
export function localDateTime(instant: number) {
  return Temporal.Instant.fromEpochMilliseconds(instant).toZonedDateTimeISO(zone);
}
export const civilDate = (instant: number) => localDateTime(instant).toPlainDate().toString();
export const clock = (instant: number) => {
  const local = localDateTime(instant);
  return `${two(local.hour)}:${two(local.minute)}`;
};
export function atTime(day: string, time: string) {
  return date(day).toPlainDateTime(Temporal.PlainTime.from(time)).toZonedDateTime(zone).epochMilliseconds;
}
export const minutes = (time: string) => {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
};
export const shiftedMinute = (time: string, cutoff: string) => (minutes(time) - minutes(cutoff) + 1440) % 1440;
export function validTimes(t: Times) {
  if (!Object.values(t).every((value) => clockPattern.test(value))) return false;
  const em = shiftedMinute(t.weekdayEm, t.cutoff);
  const na = shiftedMinute(t.weekdayNa, t.cutoff);
  return em > 0 && na > em && shiftedMinute(t.weekendNa, t.cutoff) > 0;
}
export function activeDate(now: number, unit: Unit, prefs: Preferences, schedules: Schedule[]) {
  const day = civilDate(now);
  return now < atTime(day, rosterTimes(schedules, prefs, unit, day).cutoff) ? addDays(day, -1) : day;
}
export function slotTimes(ref: ShiftRef, prefs: Preferences, schedules: Schedule[]): Span {
  const { date: day, unit, slot } = ref;
  const rule = rosterTimes(schedules, prefs, unit, day);
  const weekend = date(day).dayOfWeek >= 6;
  const night = weekend ? rule.weekendNa : rule.weekdayNa;
  const starts = { FM: rule.cutoff, EM: rule.weekdayEm, NA: night };
  const startClock = starts[slot];
  const start = atTime(minutes(startClock) < minutes(rule.cutoff) ? addDays(day, 1) : day, startClock);
  if (slot === 'NA') {
    const next = addDays(day, 1);
    return { start, end: atTime(next, rosterTimes(schedules, prefs, unit, next).cutoff) };
  }
  const endClock = slot === 'EM' || weekend ? night : rule.weekdayEm;
  return { start, end: atTime(minutes(endClock) < minutes(rule.cutoff) ? addDays(day, 1) : day, endClock) };
}
export function vacationOn(roster: Schedule | undefined, prefs: Preferences, day: string) {
  if (!roster || roster.id !== 'five') return undefined;
  const id = prefs.vacation[yearKey(roster.id, roster.year)];
  return roster.vacation.find((group) => group.id === id && group.periods.some(([start, end]) => day >= start && day <= end));
}
export function shiftsOn(day: string, unit: Unit, schedules: Schedule[]): ShiftRef[] {
  const assigned = findRoster(schedules, unit, yearOf(day))?.days[day]?.slots;
  return slots.flatMap((slot) => assigned?.[slot] ? [{ unit, date: day, slot, crew: assigned[slot] }] : []);
}
export function isOwnShift(shift: ShiftRef, prefs: Preferences) {
  return shift.crew === prefs.crews[shift.unit];
}
export function personalShifts(roster: Schedule, day: string, state: State, schedules: Schedule[]): Overtime[] {
  if (vacationOn(roster, state.prefs, day)) return [];
  return shiftsOn(day, roster.id, schedules)
    .filter((ref) => isOwnShift(ref, state.prefs))
    .map((ref) => ({ ...ref, ...slotTimes(ref, state.prefs, schedules) }));
}
export function nextWork(now: number, state: State, schedules: Schedule[]) {
  const extra = Object.values(state.overtime).filter(({ start }) => start > now)
    .map((shift) => ({ shift, overtime: true }));
  const regular: typeof extra = [];
  if (state.prefs.unit) {
    const today = activeDate(now, state.prefs.unit, state.prefs, schedules);
    const possible = schedules.filter((s) => s.id === state.prefs.unit && s.year >= yearOf(today)).sort((a, b) => a.year - b.year);
    for (const roster of possible) {
      const shifts = Object.keys(roster.days).filter((day) => day >= today).sort()
        .flatMap((day) => personalShifts(roster, day, state, schedules));
      const next = shifts.find((pass) => pass.start > now);
      if (next) {
        regular.push({ shift: next, overtime: false });
        break;
      }
    }
  }
  return [...regular, ...extra].sort((a, b) => a.shift.start - b.shift.start || Number(b.overtime) - Number(a.overtime))[0];
}
export function conflictingShift(candidate: Overtime, state: State, schedules: Schedule[]) {
  const other = Object.values(state.overtime).filter((item) => shiftKey(item) !== shiftKey(candidate));
  const unit = state.prefs.unit;
  if (unit) {
    for (const day of [addDays(candidate.date, -1), candidate.date, addDays(candidate.date, 1)]) {
      const roster = findRoster(schedules, unit, yearOf(day));
      if (roster) other.push(...personalShifts(roster, day, state, schedules));
    }
  }
  return other.find((item) => spanOverlaps(item, candidate));
}
export function monthDays(anchor: string) {
  const month = date(anchor).with({ day: 1 });
  const start = month.subtract({ days: month.dayOfWeek - 1 });
  const size = Math.ceil((month.dayOfWeek - 1 + month.daysInMonth) / 7) * 7;
  return Array.from({ length: size }, (_, index) => start.add({ days: index }).toString());
}
export const weekdayName = (day: string) => weekdays[date(day).dayOfWeek - 1];
export const dateLabel = (day: string, showMonth = true) =>
  `${weekdayName(day)} ${date(day).day}${showMonth ? ` ${months[date(day).month - 1].toLowerCase()}` : ''}`;
export const spanLabel = (span: Span) =>
  `${clock(span.start)}–${clock(span.end)}${civilDate(span.start) !== civilDate(span.end) ? ' nästa dag' : ''}`;
