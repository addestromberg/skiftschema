import { useCallback, useEffect, useState } from 'react';
import { BackHandler, Pressable, ScrollView, View } from 'react-native';
import { civilDate, date, findRoster, months, unitLabel, yearOf, type Unit, type VacationColor, type VacationGroup } from './domain';
import { schedules } from './rosters';
import { useTheme } from './theme';
import { Button, Divider, Icon, Option, Panel, T, TopBar, shared } from './ui';

const shortDate = (stamp: string) => {
  const local = date(stamp);
  return `${local.day} ${months[local.month - 1].toLowerCase()}`;
};
export function groupDetail(group: VacationGroup) {
  return group.periods.map(([a, b]) => `${shortDate(a)}–${shortDate(b)}`).join(', ');
}
type Complete = { unit: Unit; crew: string; year?: number; color?: VacationColor };
export function Onboarding({ now, complete }: { now: number; complete: (setup: Complete) => void }) {
  const [unit, setUnit] = useState<Unit>('five');
  const [step, setStep] = useState(1);
  const [crew, setCrew] = useState<string>();
  const [group, setGroup] = useState<VacationColor>();
  const canGoBack = step > 1;
  const previousStep = useCallback(() => setStep((current) => Math.max(1, current - 1)), []);
  useEffect(() => {
    if (!canGoBack) return;
    const back = BackHandler.addEventListener('hardwareBackPress', () => { previousStep(); return true; });
    return () => back.remove();
  }, [canGoBack, previousStep]);
  const c = useTheme();
  const currentYear = yearOf(civilDate(now));
  const roster = findRoster(schedules, unit, currentYear) ?? schedules.filter((r) => r.id === unit).at(-1);
  const teams = roster?.teams ?? (unit === 'five' ? [...'ABCDE'] : [...'ABCDEF']);
  const steps = unit === 'five' ? 4 : 3;
  const showingGuide = step === steps;
  const choosingVacation = unit === 'five' && step === 3;
  const heading = step === 1 ? 'Vilket schema jobbar du på?' : step === 2 ? 'Vilket skiftlag tillhör du?' :
    choosingVacation ? 'Vilken semestergrupp tillhör du?' : 'Så använder du schemat';
  const next = () => {
    if (!showingGuide) setStep(step + 1);
    else if (crew) complete({ unit, crew, year: roster?.year, color: unit === 'five' ? group : undefined });
  };
  return <>
    <TopBar back={canGoBack ? previousStep : undefined} />
    <ScrollView contentContainerStyle={[shared.page, { gap: 18, flexGrow: 1 }]}>
      <View style={[shared.row, { gap: 5, marginTop: 10 }]}>{Array.from({ length: steps }, (_, i) => <View key={i}
        style={{ flex: 1, height: 4, borderRadius: 2, backgroundColor: i < step ? c.action : c.edge }} />)}</View>
      <T size={12} weight="bold" color={c.muted}>Steg {step} av {steps}</T>
      <T size={37} weight="bold">{heading}</T>
      {step === 1 ? <>
        <T color={c.muted}>Välj den skiftgång som gäller för dig.</T>
        <Option title="5-skift" detail="Fem skiftlag · A–E" icon="layers" chosen={unit === 'five'} onPress={() => { setUnit('five'); setCrew(undefined); }} />
        <Option title="6-skift" detail="Sex skiftlag · A–F" icon="layers" chosen={unit === 'six'} onPress={() => { setUnit('six'); setCrew(undefined); }} />
        <Panel color={c.subtle} outlined={false} style={{ padding: 15, gap: 9, marginTop: 8 }}>
          <View style={[shared.row, { gap: 8 }]}><Icon name="info" color={c.mineInk} size={18} /><T weight="bold">Du kan ändra dig senare</T></View>
          <T color={c.muted} size={13}>Byt schema, skiftlag och semestergrupp i Preferenser när du behöver.</T>
        </Panel>
      </> : step === 2 ? <>
        <T color={c.muted}>Dina arbetspass markeras i kalendern.</T>
        <CrewGrid teams={teams} selected={crew} choose={setCrew} />
        {crew && <RosterPreview crew={crew} unit={unit} />}
      </> : showingGuide ? <CalendarGestureGuide /> : <>
        <T color={c.muted}>Välj färgen som gäller för dig{roster ? ` ${roster.year}` : ''}. Du kan också välja gruppen senare.</T>
        {roster?.vacation.map((choice) => <Option key={choice.id} title={choice.label} detail={groupDetail(choice)}
          swatch={c[choice.id]} chosen={group === choice.id} onPress={() => setGroup(choice.id)} />)}
      </>}
      <View style={{ flex: 1, minHeight: 4 }} />
      <Divider />
      <Button disabled={step === 2 && !crew} onPress={next}>{step === steps ? 'Öppna schemat' : 'Fortsätt'}</Button>
      {choosingVacation && <Button kind="quiet" onPress={() => { setGroup(undefined); next(); }}>Hoppa över</Button>}
    </ScrollView>
  </>;
}

const gestures = [
  { icon: 'calendar', title: 'Tryck en gång', detail: 'Markerar hela dagen.' },
  { icon: 'layers', title: 'Tryck två gånger', detail: 'Öppnar dagens vecka från månadsvyn. Gå tillbaka med Månad eller Androids bakåtknapp.' },
  { icon: 'clock', title: 'Håll ned', detail: 'Visar dagens pass. Välj ett pass från ett annat skiftlag för att lägga till övertid.' },
  { icon: 'right', title: 'Svep åt sidan', detail: 'Går till föregående eller nästa månad eller vecka.' },
] as const;

export function CalendarGestureGuide() {
  const c = useTheme();
  return <Panel>
    {gestures.map(({ icon, title, detail }, index) => <View key={title}>
      {index > 0 && <Divider />}
      <View style={[shared.row, { padding: 14, gap: 12 }]}>
        <View style={[shared.center, { width: 36, height: 36, borderRadius: 10, backgroundColor: c.mine }]}>
          <Icon name={icon} color={c.mineInk} size={18} />
        </View>
        <View style={{ flex: 1, gap: 3 }}>
          <T weight="bold">{title}</T>
          <T color={c.muted} size={13}>{detail}</T>
        </View>
      </View>
    </View>)}
  </Panel>;
}

export function CrewGrid({ teams, selected, choose }: { teams: readonly string[]; selected?: string; choose: (crew: string) => void }) {
  const c = useTheme();
  return <View style={{ gap: 8 }}>
    {[0, 3].map((start) => <View key={start} style={[shared.row, { gap: 8 }]}>
      {teams.slice(start, start + 3).map((crew) => <Pressable key={crew} accessibilityRole="radio"
        accessibilityState={{ checked: crew === selected }} accessibilityLabel={`Skiftlag ${crew}`} onPress={() => choose(crew)}
        style={[shared.center, { flex: 1, height: 61, borderWidth: crew === selected ? 1.5 : 1,
          borderColor: crew === selected ? c.action : c.line, backgroundColor: crew === selected ? c.mine : c.paper, borderRadius: 14 }]}>
        <T size={25} weight="bold" color={crew === selected ? c.mineInk : c.ink}>{crew}</T>
      </Pressable>)}
      {teams.length === 5 && start === 3 && <View style={{ flex: 1 }} />}
    </View>)}
  </View>;
}
export function RosterPreview({ crew, unit = 'five' }: { crew: string; unit?: Unit }) {
  const c = useTheme();
  const others = [...(unit === 'five' ? 'ABCDE' : 'ABCDEF')].filter((x) => x !== crew);
  const hours = unit === 'five' ? ['06:00', '14:00', '22:00'] : ['06:00', '13:00', '21:00'];
  return <Panel style={{ padding: 14, gap: 11 }}>
    <View style={[shared.row, { justifyContent: 'space-between' }]}><T weight="bold">Så visas ditt pass</T><T size={11} color={c.muted}>{unitLabel[unit]}</T></View>
    <View style={[shared.row, { gap: 7 }]}>{['FM', 'EM', 'NA'].map((shift, i) => <View key={shift}
      style={{ flex: 1, padding: 8, backgroundColor: i === 1 ? c.mine : c.subtle, borderRadius: 10 }}>
      <T size={11} weight="bold">{shift}</T>
      <T size={17} weight="bold" color={i === 1 ? c.mineInk : c.ink}>{[others[0], crew, others[1]][i]}</T>
      <T size={11} color={c.muted} mono>{hours[i]}</T>
    </View>)}</View>
  </Panel>;
}
