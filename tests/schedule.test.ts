import assert from 'node:assert/strict';
import { test } from 'node:test';
import five2026 from '../data/five-2026.json';
import six2026 from '../data/six-2026.json';
import five from '../data/five-2027.json';
import six from '../data/six-2027.json';
import {
  activeDate, atTime, clock, conflictingShift, initialState, nextWork, shiftKey, shiftsOn,
  slotTimes, validTimes, vacationOn, yearKey, type Overtime, type Schedule, type State,
} from '../src/domain';
import { clearCategory, desiredEvents, reconcile, type DeviceCalendar, type NativeEvent } from '../src/calendarEvents';

const schedules = [five, six] as Schedule[];
const [regular, energy] = schedules;
function sample(): State {
  return { ...initialState, prefs: { ...initialState.prefs, unit: 'five', crews: { five: 'B' },
    vacation: { [yearKey('five', 2027)]: 'yellow' } } };
}
const ref = (day: string, unit: 'five' | 'six' = 'five') => shiftsOn(day, unit, schedules).find((shift) => shift.slot === 'NA')!;

test('2027 rosters include every day, published closures and correct vacation group boundaries', () => {
  assert.equal(Object.keys(regular.days).length, 365);
  assert.equal(Object.keys(energy.days).length, 365);
  assert.equal(regular.days['2027-12-24'].closure, 'Jul');
  assert.equal(regular.days['2027-01-06'].slots.NA, 'E');
  assert.ok(energy.days['2027-12-24'].slots.NA);
  assert.equal(energy.vacation.length, 0);
  assert.equal(vacationOn(regular, sample().prefs, '2027-08-08')?.id, 'yellow');
  assert.equal(vacationOn(regular, sample().prefs, '2027-08-09'), undefined);
});

test('the 2026 rosters cover November 24 and use their own vacation periods', () => {
  const allYears = [five2026, six2026, ...schedules] as Schedule[];
  assert.equal(Object.keys(five2026.days).length, 365);
  assert.equal(Object.keys(six2026.days).length, 365);
  assert.deepEqual(shiftsOn('2026-11-24', 'five', allYears).map(({ crew }) => crew), ['E', 'B', 'D']);
  assert.deepEqual(shiftsOn('2026-11-24', 'six', allYears).map(({ crew }) => crew), ['F', 'A', 'D']);
  assert.equal(five2026.days['2026-12-27'].closure, 'Jul');
  assert.equal(six2026.vacation.length, 0);
  const state = sample();
  state.prefs.vacation[yearKey('five', 2026)] = 'yellow';
  assert.equal(vacationOn(allYears[0], state.prefs, '2026-08-09'), undefined);
  assert.equal(vacationOn(allYears[0], state.prefs, '2026-08-10')?.id, 'yellow');
  assert.equal(vacationOn(allYears[0], state.prefs, '2026-08-30')?.id, 'yellow');
  assert.equal(vacationOn(allYears[0], state.prefs, '2026-08-31'), undefined);
  assert.equal(nextWork(atTime('2026-11-24', '13:00'), state, allYears)?.shift.date, '2026-11-24');
});

test('the clock follows the roster date, including at year boundaries and daylight saving', () => {
  const { prefs } = sample();
  assert.equal(activeDate(atTime('2027-07-19', '05:59'), 'five', prefs, schedules), '2027-07-18');
  assert.equal(activeDate(atTime('2027-07-19', '06:00'), 'five', prefs, schedules), '2027-07-19');
  const longer = slotTimes(ref('2027-10-30', 'six'), prefs, schedules);
  const shorter = slotTimes(ref('2027-03-27', 'six'), prefs, schedules);
  assert.equal((longer.end - longer.start) / 3600000, 13);
  assert.equal((shorter.end - shorter.start) / 3600000, 11);

  const afterYear = { ...prefs, overrides: { [yearKey('six', 2028)]: { ...energy.times, cutoff: '07:00' } } };
  const newYearNight = slotTimes(ref('2027-12-31', 'six'), afterYear, schedules);
  assert.equal(clock(newYearNight.end), '07:00');
  assert.equal(activeDate(atTime('2028-01-01', '06:59'), 'six', afterYear, schedules), '2027-12-31');
  assert.equal(activeDate(atTime('2028-01-01', '07:00'), 'six', afterYear, schedules), '2028-01-01');
  assert.equal(validTimes({ ...regular.times, cutoff: '07:00' }), true);
  assert.equal(validTimes({ ...regular.times, weekdayEm: '22:00' }), false);
});

test('partial overtime belongs to its original roster slot and can overlap another unit’s shift', () => {
  const state = sample();
  const night = ref('2027-07-15');
  const entry: Overtime = { ...night, start: atTime('2027-07-16', '02:00'), end: atTime('2027-07-16', '06:00') };
  assert.ok(entry.start > slotTimes(night, state.prefs, schedules).start);
  state.overtime[shiftKey(entry)] = entry;
  const next = nextWork(atTime('2027-07-15', '23:00'), state, schedules)!;
  assert.equal(next.overtime, true);
  assert.deepEqual(next.shift, entry);
  assert.equal(conflictingShift(entry, state, schedules), undefined);
  const second = { ...entry, ...ref('2027-07-15', 'six') };
  assert.deepEqual(conflictingShift(second, state, schedules), entry);
});

test('calendar sync is repeatable, never schedules default alarms, and cleans one category at a time', async () => {
  const state = sample();
  state.prefs.export.categories.vacation = true;
  const now = atTime('2027-07-13', '16:42');
  const night = ref('2027-07-15');
  state.overtime[shiftKey(night)] = { ...night, ...slotTimes(night, state.prefs, schedules) };
  const wanted = desiredEvents(state, schedules, now);
  assert.ok(wanted.some((item) => item.key.startsWith('regular:')));
  assert.ok(wanted.some((item) => item.key.startsWith('overtime:')));
  assert.equal(wanted.filter((item) => item.allDay).length, 1);
  assert.ok(wanted.every((item) => item.reminder === null));
  assert.ok(!wanted.some((item) => item.key.startsWith('regular:five:2027-07-19')));
  const records = new Map<string, NativeEvent>();
  const deleted: string[] = [];
  let counter = 0;
  const calendar: DeviceCalendar = {
    id: 'local',
    async list() { return [...records.values()]; },
    async create(event) { const id = String(++counter); records.set(id, { ...event, id }); },
    async update(id, event) { records.set(id, { ...event, id }); },
    async remove(id) { deleted.push(records.get(id)!.key); records.delete(id); },
  };
  await reconcile(calendar, state, schedules, now);
  await reconcile(calendar, state, schedules, now);
  assert.equal(records.size, wanted.length);
  assert.equal(counter, wanted.length);

  state.prefs.export.reminder = 24 * 60;
  await reconcile(calendar, state, schedules, now);
  assert.equal(counter, wanted.length);
  assert.ok([...records.values()].every((event) => event.reminder === (event.allDay ? null : 1440)));

  state.prefs.export.categories.regular = false;
  await reconcile(calendar, state, schedules, now);
  assert.equal(records.size, wanted.length);
  await clearCategory(calendar, 'regular', true, schedules, now);
  assert.ok(deleted.length > 0 && deleted.every((key) => key.startsWith('regular:')));
  assert.equal(records.size, 2);
});
