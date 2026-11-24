import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { atTime, civilDate, clock, conflictingShift, dateLabel, findRoster, isOwnShift, shiftKey, shiftsOn, slotLabel, slotTimes, spanLabel, unitLabel, vacationOn, yearOf, type Overtime, type ShiftRef, type State, type Unit } from './domain';
import { schedules } from './rosters';
import { useTheme } from './theme';
import { Button, Divider, Icon, Panel, Sheet, T, TimeWheel, shared } from './ui';

export function DayActions({ day, unit, state, close, choose }: {
  day: string; unit: Unit; state: State; close: () => void; choose: (shift: ShiftRef) => void;
}) {
  const c = useTheme();
  const roster = findRoster(schedules, unit, yearOf(day));
  const assignment = roster?.days[day];
  const vacation = vacationOn(roster, state.prefs, day);
  const shifts = shiftsOn(day, unit, schedules);
  const subtitle = shifts.length ? `${unitLabel[unit]} · Välj pass för övertid` : unitLabel[unit];
  return <Sheet title={dateLabel(day)} subtitle={subtitle} close={close}>
    {vacation && <Panel color={c.vac} outlined={false} style={{ padding: 11 }}>
      <T color={c.vacInk} weight="bold">Semester</T>
    </Panel>}
    {shifts.map((shift) => {
      const extra = state.overtime[shiftKey(shift)];
      const own = isOwnShift(shift, state.prefs);
      const unavailable = own && !extra;
      const span = slotTimes(shift, state.prefs, schedules);
      const action = unavailable ? 'Ditt skiftlag ·' : extra ? 'Hantera övertid ·' : 'Välj';
      return <Pressable key={shift.slot} accessibilityRole="button" disabled={unavailable}
        aria-disabled={unavailable} accessibilityLabel={`${action} ${shift.slot} · Skiftlag ${shift.crew}`}
        onPress={() => choose(shift)} style={({ pressed }) => [shared.row, {
          minHeight: 64, padding: 12, gap: 11, borderRadius: 14, borderWidth: 1, borderColor: c.line,
          backgroundColor: pressed ? c.subtle : extra ? c.ot : own ? c.mine : c.paper,
        }]}>
        <View style={[shared.center, { width: 36, minHeight: 34, borderRadius: 8, backgroundColor: extra ? c.paper : c.subtle }]}>
          <T size={12} weight="bold" color={extra ? c.otInk : c.muted}>{shift.slot}</T>
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <T weight="bold">{slotLabel[shift.slot]} · Skiftlag {shift.crew}</T>
          <T size={12} color={c.muted}>{spanLabel(span)}</T>
          {own && <T size={12} color={c.mineInk} weight="bold">Ditt skiftlag</T>}
          {extra && <T size={12} color={c.otInk} weight="bold">Övertid · {spanLabel(extra)}</T>}
        </View>
        {!unavailable && <Icon name="right" size={17} />}
      </Pressable>;
    })}
    {!shifts.length && !vacation && <T weight="bold">{assignment?.closure ?? (roster ? 'Inga pass' : 'Inget schema tillgängligt')}</T>}
    {assignment?.note && <T color={c.muted} size={12}>Schemaanteckning · {assignment.note}</T>}
  </Sheet>;
}

export function ShiftActions({ shift, state, fromDay = false, close, save, remove }: {
  shift: ShiftRef; state: State; fromDay?: boolean; close: () => void; save: (entry: Overtime) => void; remove: () => void;
}) {
  const c = useTheme();
  const key = shiftKey(shift);
  const existing = state.overtime[key];
  const own = isOwnShift(shift, state.prefs);
  const span = slotTimes(shift, state.prefs, schedules);
  const directEdit = fromDay && !existing && !own;
  const [page, setPage] = useState<'menu' | 'edit' | 'remove'>(directEdit ? 'edit' : 'menu');
  const [partial, setPartial] = useState(!!existing && (existing.start !== span.start || existing.end !== span.end));
  const [start, setStart] = useState(existing?.start ?? span.start);
  const [end, setEnd] = useState(existing?.end ?? span.end);
  const [overlap, setOverlap] = useState<Overtime>();
  const note = findRoster(schedules, shift.unit, yearOf(shift.date))?.days[shift.date]?.note;
  const title = `${slotLabel[shift.slot]} · Skiftlag ${shift.crew} · ${spanLabel(span)}`;
  const entry = { ...shift, start: partial ? start : span.start, end: partial ? end : span.end };
  const valid = entry.start < entry.end && entry.start >= span.start && entry.end <= span.end;
  const setTime = (side: 'start' | 'end', time: string) => {
    const current = side === 'start' ? start : end;
    const possible = [...new Set([civilDate(span.start), civilDate(span.end)])]
      .map((day) => atTime(day, time)).filter((value) => side === 'start' ? value >= span.start && value < span.end : value > span.start && value <= span.end);
    const picked = possible.find((value) => civilDate(value) === civilDate(current)) ?? possible[0] ?? atTime(civilDate(current), time);
    if (side === 'start') setStart(picked); else setEnd(picked);
  };
  const commit = () => {
    const found = conflictingShift(entry, state, schedules);
    if (found) setOverlap(found);
    else save(entry);
  };
  if (page === 'remove') return <Sheet center title="Ta bort övertid?" subtitle={existing && spanLabel(existing)} close={() => setPage('menu')}>
    <View style={[shared.row, { gap: 8 }]}>
      <Button style={{ flex: 1 }} kind="secondary" onPress={() => setPage('menu')}>Avbryt</Button>
      <Button style={{ flex: 1 }} kind="danger" onPress={remove}>Ta bort</Button>
    </View>
  </Sheet>;
  if (!own && overlap) return <Sheet center title="Passet överlappar" subtitle={`Ett annat pass överlappar ${dateLabel(civilDate(overlap.start))} kl. ${spanLabel(overlap)}.`}
    close={() => setOverlap(undefined)}>
    <View style={[shared.row, { gap: 8 }]}>
      <Button style={{ flex: 1 }} kind="secondary" onPress={() => setOverlap(undefined)}>Gå tillbaka</Button>
      <Button style={{ flex: 1 }} onPress={() => save(entry)}>Spara ändå</Button>
    </View>
  </Sheet>;
  if (!own && page === 'edit') return <Sheet title={existing ? 'Ändra övertid' : 'Markera övertid'} subtitle={`${dateLabel(shift.date)} · ${title}`} close={directEdit ? close : () => setPage('menu')}>
    <View style={[shared.row, { gap: 7 }]}>
      <Button kind={partial ? 'secondary' : 'primary'} style={{ flex: 1 }} onPress={() => setPartial(false)}>Hela passet</Button>
      <Button kind={partial ? 'primary' : 'secondary'} style={{ flex: 1 }} onPress={() => setPartial(true)}>Del av passet</Button>
    </View>
    {partial ? <View style={{ flexDirection: 'row', gap: 12, flexWrap: 'wrap' }}>
      <View style={{ flex: 1, minWidth: 137, gap: 7 }}>
        <T weight="bold">Från</T>
        <T size={12} color={c.muted}>{dateLabel(civilDate(start))}</T>
        <TimeWheel value={clock(start)} change={(value) => setTime('start', value)} />
      </View>
      <View style={{ flex: 1, minWidth: 137, gap: 7 }}>
        <T weight="bold">Till</T>
        <T size={12} color={c.muted}>{dateLabel(civilDate(end))}</T>
        <TimeWheel value={clock(end)} change={(value) => setTime('end', value)} />
      </View>
    </View> : <Panel color={c.subtle} outlined={false} style={{ padding: 12 }}><T weight="bold">{spanLabel(span)}</T></Panel>}
    <Panel color={valid ? c.ot : c.warn} outlined={false} style={{ padding: 12 }}>
      <View style={[shared.row, { gap: 7 }]}>
        <Icon name={valid ? 'clock' : 'alert'} size={18} color={valid ? c.otInk : c.warnInk} />
        <T color={valid ? c.otInk : c.warnInk} weight="bold" size={13} style={{ flex: 1 }}>
          {valid ? `${new Intl.NumberFormat('sv-SE', { maximumFractionDigits: 2 }).format((entry.end - entry.start) / 3600000)} timmar övertid`
            : 'Välj en sluttid efter starttiden, inom passets gränser.'}
        </T>
      </View>
    </Panel>
    <Button disabled={!valid} onPress={commit}>Spara övertid</Button>
  </Sheet>;
  return <Sheet title={dateLabel(shift.date)} subtitle={title} close={close}>
    <Panel color={existing ? c.ot : c.subtle} outlined={false} style={{ padding: 12 }}>
      <T weight="bold" color={existing ? c.otInk : c.ink}>
        {existing ? `Sparad övertid · ${spanLabel(existing)}` : `Ordinarie skiftlag ${shift.crew}`}
      </T>
    </Panel>
    {note && <T color={c.muted} size={12}>Schemaanteckning · {note}</T>}
    {!own && <Button kind="secondary" onPress={() => setPage('edit')}>{existing ? 'Ändra övertid' : 'Markera övertid'}</Button>}
    {existing && <><Divider /><Button kind="quiet" onPress={() => setPage('remove')}>Ta bort övertid</Button></>}
  </Sheet>;
}
