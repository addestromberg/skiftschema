import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BackHandler, PanResponder, Platform, Pressable, ScrollView, View } from 'react-native';
import {
  activeDate, addDays, addMonths, civilDate, clock, date, dateLabel, daysBetween, findRoster, isOwnShift,
  monthDays, months, monthStart, shiftKey, shiftsOn, slotLabel, slotTimes, slots,
  unitLabel, vacationOn, weekdays, weekStart, yearOf, type State, type Unit,
} from './domain';
import { nextWork } from './domain';
import { schedules } from './rosters';
import { useTheme } from './theme';
import { Button, Icon, IconButton, Panel, Sheet, T, Tag, TopBar, shared } from './ui';

type Props = {
  state: State; now: number; active: boolean; openPrefs: () => void;
  openDay: (day: string, unit: Unit) => void;
};
type CalendarView = 'month' | 'week';
type CalendarDate = { anchor: string; selected?: string };
type Position = CalendarDate & ({ view: 'month' } | { view: 'week'; previousMonth: CalendarDate });
const doubleTapWindow = 350;

export function CalendarScreen({ state, now, active, openPrefs, openDay }: Props) {
  const unit = state.prefs.unit!;
  const today = activeDate(now, unit, state.prefs, schedules);
  const [{ anchor, view, selected }, setPosition] = useState<Position>({ anchor: today, view: 'month' });
  const [picker, setPicker] = useState(false);
  const [pickerYear, setPickerYear] = useState(yearOf(anchor));
  const c = useTheme();
  const start = view === 'month' ? monthStart(anchor) : weekStart(anchor);
  const cells = view === 'month' ? monthDays(anchor) : Array.from({ length: 7 }, (_, i) => addDays(start, i));
  const covered = view === 'month' ? !!findRoster(schedules, unit, yearOf(start)) :
    cells.some((cell) => !!findRoster(schedules, unit, yearOf(cell)));
  const upcoming = useMemo(() => nextWork(now, state, schedules), [now, state]);
  const output = upcoming?.shift;
  const diff = output ? daysBetween(civilDate(now), civilDate(output.start)) : 0;
  const lastTap = useRef<{ day: string; time: number }>(undefined);
  const multipleTouches = useRef(false);

  useEffect(() => {
    if (!active || view !== 'week') return;
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      lastTap.current = undefined;
      setPosition((position) => {
        if (position.view !== 'week') return position;
        const { selected, previousMonth } = position;
        return { view: 'month', ...(selected ? { anchor: selected, selected } : previousMonth) };
      });
      return true;
    });
    return () => back.remove();
  }, [active, view]);

  const swipe = useCallback((direction: number) => {
    lastTap.current = undefined;
    setPosition((position) => ({
      ...position,
      anchor: position.view === 'month' ? addMonths(position.anchor, direction) : addDays(position.anchor, direction * 7),
      selected: undefined,
    }));
  }, []);
  const gesture = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponderCapture: (event, pan) => {
      if (multipleTouches.current || (event.nativeEvent.touches?.length ?? 0) > 1) {
        multipleTouches.current = true;
        return true;
      }
      return Math.abs(pan.dx) > 22 && Math.abs(pan.dx) > Math.abs(pan.dy) * 1.5;
    },
    onPanResponderRelease: (_event, pan) => {
      if (!multipleTouches.current && Math.abs(pan.dx) > 44 && Math.abs(pan.dx) > Math.abs(pan.dy)) {
        swipe(pan.dx < 0 ? 1 : -1);
      }
      multipleTouches.current = false;
    },
    onPanResponderTerminate: () => { multipleTouches.current = false; lastTap.current = undefined; },
  }), [swipe]);

  const moveTo = (view: CalendarView, anchor: string, selected?: string) => {
    lastTap.current = undefined;
    setPosition((position) => {
      if (view === 'month') return { view, anchor, selected };
      const previousMonth = position.view === 'week' ? position.previousMonth : {
        anchor: position.anchor, selected: position.selected,
      };
      return { view, anchor, selected, previousMonth };
    });
  };
  const tapDay = (day: string) => {
    if (multipleTouches.current) return;
    if (view === 'month') {
      const time = Date.now();
      const previous = lastTap.current;
      if (previous?.day === day && time - previous.time <= doubleTapWindow) {
        moveTo('week', day, day);
        return;
      }
      lastTap.current = { day, time };
    }
    setPosition((position) => ({ ...position, selected: day }));
  };
  const showDay = (day: string) => {
    if (multipleTouches.current) return;
    lastTap.current = undefined;
    setPosition((position) => ({ ...position, selected: day }));
    openDay(day, unit);
  };
  const setMode = (option: CalendarView) => {
    if (option !== view) moveTo(option, selected ?? (cells.includes(today) ? today : anchor), selected);
  };
  const renderDay = (day: string) => <DayCell key={day} day={day} unit={unit} today={today}
    included={view === 'week' || date(day).month === date(anchor).month}
    week={view === 'week'} selected={selected === day} state={state}
    select={() => tapDay(day)} hold={() => showDay(day)} />;
  return <>
    <TopBar />
    <ScrollView contentContainerStyle={{ paddingHorizontal: 18, paddingBottom: 13, gap: 13 }}>
      <Panel color={c.hero} outlined={false} style={{ minHeight: 96, padding: 15, marginHorizontal: 4, gap: 3 }}>
        <View style={[shared.row, { justifyContent: 'space-between' }]}>
          <T size={12} weight="bold" color={c.heroMuted}>Nästa pass</T>
          {output && <Tag background={c.heroBadge} color={c.heroBadgeInk}>{diff === 0 ? 'Idag' : diff === 1 ? 'Imorgon' : `Om ${diff} dagar`}</Tag>}
        </View>
        <View style={[shared.row, { justifyContent: 'space-between', gap: 6 }]}>
          <T size={25} weight="bold" color={c.heroMain} style={{ flex: 1 }}>
            {output ? `${dateLabel(civilDate(output.start))}, ${clock(output.start)}` : '–'}
          </T>
          <Icon name="clock" color={c.heroMuted} size={20} />
        </View>
        {output && <T color={c.heroMuted} size={11}>{upcoming.overtime ? 'Övertid' : slotLabel[output.slot]} · Skift {output.crew}</T>}
      </Panel>

      <View style={[shared.row, { justifyContent: 'space-between', gap: 8, paddingHorizontal: 3 }]}>
        <Pressable onPress={() => { lastTap.current = undefined; setPickerYear(yearOf(start)); setPicker(true); }} accessibilityLabel="Gå till månad"
          style={[shared.row, { flex: 1, gap: 4, minHeight: 42 }]}>
          <T size={29} weight="bold" numberOfLines={1} style={{ flexShrink: 1 }}>
            {view === 'month' ? `${months[date(start).month - 1]} ${date(start).year}` : `Vecka ${date(start).weekOfYear}`}
          </T>
          <Icon name="down" size={15} />
        </Pressable>
        <View style={[shared.row, { gap: 4 }]}>
          <IconButton small icon="left" label={view === 'month' ? 'Föregående månad' : 'Föregående vecka'} onPress={() => swipe(-1)} />
          <Tag onPress={() => moveTo(view, today)} color={c.action} background={c.paper} style={{ paddingHorizontal: 8 }}>Idag</Tag>
          <IconButton small icon="right" label={view === 'month' ? 'Nästa månad' : 'Nästa vecka'} onPress={() => swipe(1)} />
        </View>
      </View>
      <View style={[shared.row, { justifyContent: 'space-between' }]}>
        <View style={[shared.row, { backgroundColor: c.subtle, padding: 4, borderRadius: 12 }]}>
          {(['month', 'week'] as const).map((option) => <Pressable key={option} accessibilityRole="tab"
            accessibilityState={{ selected: view === option }} onPress={() => setMode(option)}
            style={[shared.center, { minWidth: 75, minHeight: 30, backgroundColor: view === option ? c.paper : c.subtle, borderRadius: 9 }]}>
            <T size={12} weight="bold" color={view === option ? c.ink : c.muted}>{option === 'month' ? 'Månad' : 'Vecka'}</T>
          </Pressable>)}
        </View>
        <Tag onPress={openPrefs}>{`${unitLabel[unit]} · ${state.prefs.crews[unit]}`}</Tag>
      </View>
      <View {...gesture.panHandlers} onTouchStart={(event) => {
        const count = event.nativeEvent.touches?.length ?? 0;
        if (count === 1) multipleTouches.current = false;
        if (count > 1) { multipleTouches.current = true; lastTap.current = undefined; }
      }} style={{ gap: 5, position: 'relative', ...(Platform.OS === 'web' && { touchAction: 'pan-y' }) }}>
        {view === 'week' && <T size={12} color={c.muted}>{dateLabel(start)} – {dateLabel(addDays(start, 6))} {yearOf(start)}</T>}
        {view === 'month' && <View style={[shared.row, { gap: 3, marginBottom: 1 }]}>
          {weekdays.map((day) => <T key={day} size={11} color={c.muted} weight="bold" style={{ flex: 1, textAlign: 'center' }}>{day}</T>)}
        </View>}
        {view === 'month' ? Array.from({ length: cells.length / 7 }, (_, i) => <View key={i}
          style={{ flexDirection: 'row', gap: 3, alignItems: 'stretch' }}>
          {cells.slice(i * 7, i * 7 + 7).map(renderDay)}
        </View>) : cells.map(renderDay)}
        {!covered && <View pointerEvents="none" style={{ position: 'absolute', left: 12, right: 12, top: '37%' }}>
          <Panel style={[shared.center, { padding: 18, gap: 8 }]}><Icon name="calendar" size={24} />
            <T weight="bold" size={17}>Inget schema tillgängligt</T></Panel>
        </View>}
      </View>
      {selected ? <Panel color={c.mine} style={[shared.row, { padding: 8, paddingLeft: 12, justifyContent: 'space-between', gap: 4 }]}>
        <T weight="bold" size={13} color={c.mineInk} style={{ flex: 1 }}>Vald dag · {dateLabel(selected)}</T>
        <Button kind="secondary" onPress={() => showDay(selected)} style={{ minHeight: 40 }}>Visa pass</Button>
      </Panel> : covered ? <View style={[shared.row, { gap: 13, paddingHorizontal: 3, minHeight: 24, flexWrap: 'wrap' }]}>
        <Legend text="Ditt pass" color={c.mine} edge={c.mineInk} />
        {unit === 'five' && <Legend text="Semester" color={c.vac} edge={c.vacInk} />}
        <Legend text="Övertid" color={c.ot} edge={c.otInk} />
      </View> : null}
    </ScrollView>
    {picker && <Sheet title="Gå till månad" close={() => setPicker(false)}>
      <View style={[shared.row, { justifyContent: 'space-between' }]}>
        <IconButton small icon="left" label="Föregående år" onPress={() => setPickerYear((y) => Math.max(1900, y - 1))} />
        <T weight="bold" size={20}>{pickerYear}</T>
        <IconButton small icon="right" label="Nästa år" onPress={() => setPickerYear((y) => Math.min(9999, y + 1))} />
      </View>
      {Array.from({ length: 3 }, (_, row) => <View key={row} style={[shared.row, { gap: 6 }]}>
        {months.slice(4 * row, 4 * row + 4).map((month, index) => <Button key={month}
          style={{ flex: 1, paddingHorizontal: 4 }} kind="secondary" onPress={() => {
            moveTo('month', `${pickerYear}-${String(row * 4 + index + 1).padStart(2, '0')}-01`);
            setPicker(false);
          }}>{month.slice(0, 3)}</Button>)}
      </View>)}
    </Sheet>}
  </>;
}

function Legend({ text, color, edge }: { text: string; color: string; edge: string }) {
  const c = useTheme();
  return <View style={[shared.row, { gap: 5 }]}><View style={{ backgroundColor: color, borderColor: edge,
    width: 12, height: 12, borderWidth: 1, borderRadius: 4 }} /><T size={10} weight="bold" color={c.muted}>{text}</T></View>;
}

function DayCell({ day, unit, today, included, week, selected, state, select, hold }: {
  day: string; unit: Unit; today: string; included: boolean; week: boolean; selected: boolean;
  state: State; select: () => void; hold: () => void;
}) {
  const c = useTheme();
  const held = useRef(false);
  const web = Platform.OS === 'web';
  const roster = findRoster(schedules, unit, yearOf(day));
  const assignment = roster?.days[day];
  const vacation = vacationOn(roster, state.prefs, day);
  const refs = shiftsOn(day, unit, schedules);
  const active = day === today;
  const weekend = date(day).dayOfWeek >= 6;
  const overtime = refs.some((ref) => !!state.overtime[shiftKey(ref)]);
  const background = vacation ? c.vac : active || selected ? c.mine : !included ? c.subtle : weekend ? c.weekend : c.paper;
  const passes = refs.map((ref) => `${ref.slot} skift ${ref.crew}`).join(', ');
  const description = [dateLabel(day), vacation ? 'Semester' : assignment?.closure, passes].filter(Boolean).join(', ');
  return <Pressable accessibilityRole="button" aria-selected={selected} accessibilityLabel={description}
    onPress={select} delayLongPress={380}
    onLongPress={web ? () => { held.current = true; } : hold}
    // On web, opening the sheet before release lets that release click its backdrop.
    {...(web && { delayPressOut: 35 })}
    onPressOut={web ? ({ nativeEvent }) => {
      const type = 'type' in nativeEvent ? nativeEvent.type : undefined;
      if (held.current && (type === 'touchend' || type === 'mouseup')) hold();
      held.current = false;
    } : undefined}
    style={[{ borderWidth: selected ? 2 : active ? 1.3 : 1, borderColor: active || selected ? c.action : c.line,
      borderRadius: week ? 12 : 8, backgroundColor: background, padding: week ? 6 : 3, minHeight: week ? 58 : 73, gap: 2 },
      week ? [shared.row, { gap: 6 }] : { flex: 1 }]}>
    <View style={[shared.row, week ? { flexDirection: 'column', width: 36 } : { justifyContent: 'space-between' }]}>
      {week && <T color={c.muted} size={10} weight="bold">{weekdays[date(day).dayOfWeek - 1]}</T>}
      <View style={[shared.center, { minWidth: 17, minHeight: 17, backgroundColor: active ? c.action : 'transparent', borderRadius: 10 }]}>
        <T size={week ? 14 : 11} weight="bold" color={active ? c.onAction : included ? c.ink : c.quiet}>{date(day).day}</T>
      </View>
      {vacation && <View style={{ borderRadius: 3, backgroundColor: c[vacation.id], paddingHorizontal: 3 }}>
        <T color="#0B1812" weight="bold" size={8}>S</T></View>}
      {!week && !vacation && overtime && <T size={8} weight="bold" color={c.otInk}>ÖT</T>}
    </View>
    {assignment?.closure ? <View style={[shared.center, { flex: 1, paddingVertical: 8 }]}>
      <T size={week ? 12 : 8} color={vacation ? c.vacInk : c.muted} weight="bold" style={{ textAlign: 'center' }} numberOfLines={2}>
        {vacation ? 'Semester' : assignment.closure}
      </T>
    </View> : (week ? slots.map((slot) => refs.find((shift) => shift.slot === slot)) : refs).map((ref, index) => {
      if (!ref) return <View key={slots[index]} style={[shared.center, { flex: 1 }]}><T size={11} color={c.quiet}>–</T></View>;
      const extra = state.overtime[shiftKey(ref)];
      const isMine = !vacation && isOwnShift(ref, state.prefs);
      const fill = extra ? c.ot : isMine ? c.mine : week ? c.subtle : 'transparent';
      const text = extra ? c.otInk : isMine ? c.mineInk : c.ink;
      return <View key={ref.slot} style={week
        ? { flex: 1, minHeight: 44, padding: 4, paddingLeft: 6, borderRadius: 7, backgroundColor: fill }
        : { minHeight: 14, paddingHorizontal: 2, borderRadius: 4, backgroundColor: fill }}>
        <View style={[shared.row, { gap: week ? 5 : 2, justifyContent: week ? 'flex-start' : 'space-between' }]}>
          <T size={week ? 12 : 8} weight="bold" color={extra || isMine ? text : c.muted}>{ref.slot}</T>
          <T size={week ? 13 : 10} color={text} weight="bold">{ref.crew}</T>
          {week && extra && <T size={9} color={c.otInk} weight="bold">ÖT</T>}
        </View>
        {week && <T size={10} color={c.muted} mono>{clock((extra ?? slotTimes(ref, state.prefs, schedules)).start)}</T>}
      </View>;
    })}
  </Pressable>;
}
