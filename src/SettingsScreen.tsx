import { useState } from 'react';
import { Linking, ScrollView, View } from 'react-native';
import {
  addDays, categoryKeys, categoryLabel, civilDate, findRoster, rosterTimes,
  shiftsOn, slotTimes, unitLabel, validTimes, yearKey, yearOf,
  type Appearance, type Category, type Preferences, type State, type Times, type Unit, type VacationColor,
} from './domain';
import { schedules } from './rosters';
import { CalendarGestureGuide, CrewGrid, groupDetail, RosterPreview } from './Onboarding';
import { isPreview } from './preview';
import { accentChoices, accentLabel, themeFor, useTheme, type Theme } from './theme';
import { Button, Divider, Icon, Option, Panel, Setting, Sheet, T, Tag, TimeWheel, TopBar, shared } from './ui';

export type SettingsPage = 'prefs' | 'appearance' | 'accent' | 'vacation' | 'timing' | 'export' | 'calendar-help';
type Props = {
  page: SettingsPage; go: (page: SettingsPage) => void; showCalendar: () => void; state: State; now: number;
  update: (update: (prefs: Preferences) => Preferences, calendarChange?: boolean) => void;
  setExport: (enabled: boolean) => void; sync: () => void;
  deleteEvents: (category: Category, range: 'keep' | 'future' | 'all') => void;
  busy: boolean; face: 'light' | 'dark';
};
const appearanceLabel: Record<Appearance, string> = { system: 'System', light: 'Ljust', dark: 'Mörkt' };
const reminders: Array<{ label: string; value: number | null }> = [
  { label: 'Ingen påminnelse', value: null }, { label: '15 minuter före', value: 15 },
  { label: '30 minuter före', value: 30 }, { label: '1 timme före', value: 60 },
  { label: '1 dag före', value: 24 * 60 },
];
function latestYear(unit: Unit, now: number) {
  const year = yearOf(civilDate(now));
  return findRoster(schedules, unit, year)?.year ?? schedules.filter((r) => r.id === unit).at(-1)?.year ?? year;
}

export function SettingsScreen(props: Props) {
  const { page, go, state, update, now, face } = props;
  const prefs = state.prefs;
  const c = useTheme();
  const unit = prefs.unit!;
  const year = latestYear(unit, now);
  const roster = findRoster(schedules, unit, year);
  const currentGroup = roster?.vacation.find((g) => g.id === prefs.vacation[yearKey(unit, year)]);
  const [selection, setSelection] = useState<'scheme' | { unit: Unit; fromScheme: boolean }>();
  const [crew, setCrew] = useState<string>();
  const close = () => setSelection(undefined);
  const newUnit = (target: Unit) => {
    setCrew(prefs.crews[target]);
    setSelection({ unit: target, fromScheme: selection === 'scheme' });
  };
  const parent = page === 'accent' ? 'appearance' : 'prefs';

  if (page === 'timing') return <TimingScreen key={unit} {...props} unit={unit} firstYear={year} />;
  if (page === 'export') return <ExportScreen {...props} />;
  if (page === 'vacation') return <VacationScreen {...props} firstYear={year} />;

  if (page === 'calendar-help') return <>
    <TopBar title="Kalendergester" back={() => go('prefs')} />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={36} weight="bold">Så använder du schemat</T>
      <CalendarGestureGuide />
    </ScrollView>
  </>;

  if (page === 'appearance' || page === 'accent') return <>
    <TopBar title="Utseende" back={() => go(parent)} />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={36} weight="bold">{page === 'appearance' ? 'Välj tema' : 'Färgpalett'}</T>
      {page === 'appearance' ? <>
        <Panel style={[shared.row, { padding: 12, gap: 12 }]}>
          {(['light', 'dark'] as const).map((mode) => {
            const preview = themeFor(mode, prefs.accent);
            return <View key={mode} style={{ flex: 1, gap: 6 }}>
              <View style={{ backgroundColor: preview.canvas, height: 92, borderWidth: 1, borderColor: preview.line, borderRadius: 13, padding: 9, gap: 8 }}>
                <View style={[shared.row, { gap: 6 }]}><View style={{ width: 7, height: 7, borderRadius: 2, backgroundColor: preview.action }} />
                  <View style={{ height: 5, width: 51, borderRadius: 2, backgroundColor: preview.ink }} /></View>
                <View style={{ height: 19, borderRadius: 5, backgroundColor: preview.mine }} />
                <View style={[shared.row, { gap: 3 }]}>{[0, 1, 2, 3, 4].map((i) => <View key={i} style={{ flex: 1, height: 18, borderRadius: 3, backgroundColor: i === 1 ? preview.action : preview.line }} />)}</View>
              </View>
              <T size={12} weight="bold" style={{ textAlign: 'center' }}>{appearanceLabel[mode]}</T>
            </View>;
          })}
        </Panel>
        {(['system', 'light', 'dark'] as const).map((mode) => <Option key={mode} title={appearanceLabel[mode]}
          detail={mode === 'system' ? 'Följer telefonens tema' : undefined} icon={mode === 'system' ? 'phone' : mode === 'light' ? 'sun' : 'moon'}
          chosen={prefs.appearance === mode} onPress={() => update((p) => ({ ...p, appearance: mode }))} />)}
        <Panel><Setting title="Färgpalett" value={accentLabel[prefs.accent]} onPress={() => go('accent')} /></Panel>
      </> : <>
        <T color={c.muted}>Välj en palett för hela appen.</T>
        <View style={{ gap: 8 }}>
          {accentChoices.map((accent) => <Option key={accent} title={accentLabel[accent]}
            detail={accent === 'forest' ? 'Standard' : undefined} leading={<PaletteSample palette={themeFor(face, accent)} />}
            chosen={prefs.accent === accent} onPress={() => update((p) => ({ ...p, accent }))} />)}
        </View>
        <RosterPreview unit={unit} crew={prefs.crews[unit] || 'B'} />
        <Button onPress={props.showCalendar}>Visa i schemat</Button>
      </>}
    </ScrollView>
  </>;

  return <>
    <TopBar />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={37} weight="bold">Preferenser</T>
      <T color={c.muted} weight="bold" size={13}>Ditt schema</T>
      <Panel>
        <Setting title="Schema" value={unitLabel[unit]} icon="layers" onPress={() => setSelection('scheme')} />
        <Divider />
        <Setting title="Skiftlag" value={prefs.crews[unit]} icon="calendar" onPress={() => newUnit(unit)} />
        {unit === 'five' && <><Divider /><Setting title="Semestergrupp" value={currentGroup ? `${currentGroup.label} · ${year}` : 'Ingen'}
          icon="sun" onPress={() => go('vacation')} /></>}
      </Panel>
      <T color={c.muted} weight="bold" size={13}>App och kalender</T>
      <Panel>
        <Setting title="Utseende" value={appearanceLabel[prefs.appearance]} icon="phone" onPress={() => go('appearance')} />
        <Divider />
        <Setting title="Kalenderexport" value={prefs.export.enabled ? prefs.export.pending ? 'Ändringar väntar' : 'På' : 'Av'} icon="sync" onPress={() => go('export')} />
        <Divider />
        <Setting title="Avancerat" icon="clock" onPress={() => go('timing')} />
        <Divider />
        <Setting title="Kalendergester" icon="info" onPress={() => go('calendar-help')} />
      </Panel>
    </ScrollView>
    {selection === 'scheme' && <Sheet title="Välj schema" close={close}>
      {(['five', 'six'] as const).map((choice) => <Option key={choice} title={unitLabel[choice]}
        detail={choice === 'five' ? 'Fem skiftlag · A–E' : 'Sex skiftlag · A–F'} icon="layers" chosen={choice === unit}
        onPress={() => {
          if (choice === unit) close();
          else if (prefs.crews[choice]) { close(); update((p) => ({ ...p, unit: choice }), true); }
          else newUnit(choice);
        }} />)}
    </Sheet>}
    {selection && selection !== 'scheme' && <Sheet title="Välj skiftlag" subtitle={unitLabel[selection.unit]}
      close={() => setSelection(selection.fromScheme ? 'scheme' : undefined)}>
      <CrewGrid teams={selection.unit === 'five' ? [...'ABCDE'] : [...'ABCDEF']} selected={crew} choose={setCrew} />
      <Button disabled={!crew} onPress={() => {
        if (!crew) return;
        const target = selection.unit;
        const changed = unit !== target || prefs.crews[target] !== crew;
        close(); update((p) => ({ ...p, unit: target, crews: { ...p.crews, [target]: crew } }), changed);
      }}>Spara skiftlag</Button>
    </Sheet>}
  </>;
}

function PaletteSample({ palette }: { palette: Theme }) {
  return <View style={[shared.row, { width: 44, height: 31, borderWidth: 1, borderColor: palette.line,
    borderRadius: 9, backgroundColor: palette.canvas, padding: 4, gap: 3 }]}>
    <View style={{ width: 16, height: '100%', borderRadius: 4, backgroundColor: palette.action }} />
    <View style={{ flex: 1, height: '100%', gap: 3 }}>
      <View style={{ flex: 1, borderRadius: 3, backgroundColor: palette.paper }} />
      <View style={{ flex: 1, borderRadius: 3, backgroundColor: palette.subtle }} />
    </View>
  </View>;
}

function VacationScreen({ state, update, go, firstYear }: Props & { firstYear: number }) {
  const c = useTheme();
  const prefs = state.prefs;
  const [year, setYear] = useState(firstYear);
  const [choice, setChoice] = useState<VacationColor | null>(prefs.vacation[yearKey('five', year)] ?? null);
  const [picker, open] = useState(false);
  const roster = findRoster(schedules, 'five', year);
  return <>
    <TopBar title="Semestergrupp" back={() => go('prefs')} />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={34} weight="bold">Din grupp för året</T>
      <T color={c.muted}>Välj en semestergrupp för varje schemaår.</T>
      <Panel><Setting title="Schemaår" value={String(year)} onPress={() => open(true)} /></Panel>
      {roster?.vacation.map((group) => <Option key={group.id} title={group.label} detail={groupDetail(group)}
        chosen={group.id === choice} swatch={c[group.id]} onPress={() => setChoice(group.id)} />)}
      <Option title="Ingen semestergrupp" chosen={choice === null} onPress={() => setChoice(null)} />
      <Button onPress={() => {
        go('prefs');
        const changed = (prefs.vacation[yearKey('five', year)] || null) !== choice;
        update((p) => ({ ...p, vacation: { ...p.vacation, [yearKey('five', year)]: choice } }), changed);
      }}>Spara semestergrupp</Button>
    </ScrollView>
    {picker && <Sheet title="Välj schemaår" close={() => open(false)}>
      {schedules.filter((item) => item.id === 'five').map((item) => <Option key={item.year} title={String(item.year)}
        chosen={item.year === year} onPress={() => { setYear(item.year); setChoice(prefs.vacation[yearKey('five', item.year)] ?? null); open(false); }} />)}
    </Sheet>}
  </>;
}

const transitionNames: Record<keyof Times, string> = {
  cutoff: 'Bryttid för schemadag', weekdayEm: 'Skiftbyte FM till EM', weekdayNa: 'Skiftbyte EM till NA', weekendNa: 'Skiftbyte FM till NA',
};

function TimingScreen({ state, update, go, unit, firstYear }: Props & { unit: Unit; firstYear: number }) {
  const c = useTheme();
  const [year, setYear] = useState(firstYear);
  const [times, setTimes] = useState(() => rosterTimes(schedules, state.prefs, unit, `${firstYear}-01-01`));
  const [edit, setEdit] = useState<keyof Times | 'reset' | 'year'>();
  const [draft, setDraft] = useState('06:00');
  const [error, setError] = useState<string>();
  const key = yearKey(unit, year);
  const chooseTime = (key: keyof Times) => { setDraft(times[key]); setEdit(key); };
  const save = (reset = false) => {
    if (!reset && !validTimes(times)) { setError('Skiftbytena måste komma i ordning efter bryttiden.'); return; }
    const overrides = { ...state.prefs.overrides };
    if (reset) delete overrides[key]; else overrides[key] = times;
    const next = { ...state.prefs, overrides };
    const start = `${year}-01-01`;
    const finish = `${year}-12-31`;
    const days = [addDays(start, -1), ...Object.keys(findRoster(schedules, unit, year)?.days ?? {}), finish];
    for (const day of days) {
      const invalid = shiftsOn(day, unit, schedules).some((shift) => {
        const span = slotTimes(shift, next, schedules);
        return span.end <= span.start;
      });
      if (invalid) {
        setError(`Tiderna ger ett ogiltigt pass ${day}. Kontrollera bryttiden och skiftbytena.`);
        setEdit(undefined);
        return;
      }
    }
    setEdit(undefined); setError(undefined);
    if (reset) setTimes(rosterTimes(schedules, next, unit, start));
    const previousTimes = rosterTimes(schedules, state.prefs, unit, start);
    const changed = JSON.stringify(previousTimes) !== JSON.stringify(rosterTimes(schedules, next, unit, start));
    update(() => next, changed);
  };
  const timeTable = (weekend: boolean) => <>
    <T color={c.muted} size={13} weight="bold">{weekend ? 'Lördag–söndag' : 'Måndag–fredag'}</T>
    <Panel>
      {(weekend ? [
        ['FM', times.cutoff, times.weekendNa, 'weekendNa'], ['NA', times.weekendNa, times.cutoff, 'cutoff'],
      ] : [['FM', times.cutoff, times.weekdayEm, 'weekdayEm'],
        ['EM', times.weekdayEm, times.weekdayNa, 'weekdayNa'], ['NA', times.weekdayNa, times.cutoff, 'cutoff']]
      ).map(([shift, start, end, transition], index) => <View key={shift}>
        {index > 0 && <Divider />}
        <Setting title={shift} subtitle={shift === 'NA' ? 'Slutar när nästa schemadag börjar' : undefined}
          value={`${start} – ${end}`} onPress={() => chooseTime(transition as keyof Times)} />
      </View>)}
    </Panel>
  </>;
  return <>
    <TopBar title="Avancerat" back={() => go('prefs')} />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={35} weight="bold">Schematider</T>
      <T color={c.muted}>Anpassa tiderna för det valda schemaåret.</T>
      <View style={[shared.row, { gap: 8 }]}><Tag>{unitLabel[unit]}</Tag><Tag onPress={() => setEdit('year')}>{String(year)}</Tag></View>
      <Panel><Setting title="Bryttid för schemadag" subtitle="Första passet börjar vid bryttiden." value={times.cutoff}
        icon="clock" onPress={() => chooseTime('cutoff')} /></Panel>
      {timeTable(false)}
      {timeTable(true)}
      {error && <Panel color={c.warn} style={{ padding: 12 }}><T color={c.warnInk}>{error}</T></Panel>}
      <Button onPress={() => save()}>Spara schematider</Button>
      <Button kind="quiet" onPress={() => setEdit('reset')}>Återställ schematider</Button>
    </ScrollView>
    {edit === 'year' ? <Sheet title="Välj schemaår" close={() => setEdit(undefined)}>
      {schedules.filter((r) => r.id === unit).map((r) => <Option key={r.year} title={String(r.year)} chosen={year === r.year} onPress={() => {
        setYear(r.year); setTimes(rosterTimes(schedules, state.prefs, unit, `${r.year}-01-01`)); setEdit(undefined); setError(undefined);
      }} />)}
    </Sheet> : edit === 'reset' ? <Sheet center title="Återställ schematider?" subtitle={`Publicerade tider återställs för ${unitLabel[unit]} ${year}.`} close={() => setEdit(undefined)}>
      <View style={[shared.row, { gap: 8 }]}>
        <Button kind="secondary" style={{ flex: 1 }} onPress={() => setEdit(undefined)}>Avbryt</Button>
        <Button style={{ flex: 1 }} onPress={() => save(true)}>Återställ</Button>
      </View>
    </Sheet> : edit ? <Sheet title={transitionNames[edit]} subtitle={edit === 'cutoff' ? 'Bryttiden flyttar FM:s start och föregående nattpass slut.' : 'Tiden gäller både slutet på passet före och starten på nästa pass.'}
      close={() => setEdit(undefined)}>
      <TimeWheel value={draft} change={setDraft} />
      <Button onPress={() => { setTimes((value) => ({ ...value, [edit]: draft })); setEdit(undefined); setError(undefined); }}>Använd tid</Button>
    </Sheet> : null}
  </>;
}

function ExportScreen({ go, state, update, setExport, sync, deleteEvents, busy }: Props) {
  const c = useTheme();
  const out = state.prefs.export;
  const reminderLabel = out.reminder === null ? 'Av' :
    (reminders.find(({ value }) => value === out.reminder)?.label ?? `${out.reminder} min`);
  const [modal, setModal] = useState<'reminders' | 'manage' | { category: Category; fromManage: boolean }>();
  const [confirm, setConfirm] = useState<{ category: Category; range: 'future' | 'all' }>();
  const change = (fields: Partial<Preferences['export']>, changesEvents = false) =>
    update((prefs) => ({ ...prefs, export: { ...prefs.export, ...fields } }), changesEvents);
  return <>
    <TopBar title="Kalenderexport" back={() => go('prefs')} />
    <ScrollView contentContainerStyle={shared.page}>
      <T size={33} weight="bold">Telefonens kalender</T>
      <T color={c.muted}>Visa valda pass i telefonens kalender Skiftschema.</T>
      {isPreview && <T size={12} color={c.muted}>Webbförhandsvisningen simulerar kalenderexport.</T>}
      <Panel><Setting title="Kalenderexport" subtitle="Hantera kalendern Skiftschema" toggled={out.enabled} onPress={() => setExport(!out.enabled)} /></Panel>
      {!out.enabled ? <>
        <Panel color={c.subtle} outlined={false} style={{ padding: 15, gap: 9 }}>
          <View style={[shared.row, { gap: 7 }]}><Icon name={out.rejected ? 'alert' : 'calendar'} />
            <T weight="bold">{out.rejected ? 'Kalenderåtkomst saknas' : 'Exporten är av'}</T></View>
          <T color={c.muted} size={13}>{out.rejected ? 'Tillåt kalenderåtkomst i telefonens inställningar.' : 'Slå på export för att välja vilka pass som ska visas.'}</T>
        </Panel>
        <Button disabled={busy} onPress={() => {
          if (out.rejected && !isPreview) void Linking.openSettings();
          else setExport(true);
        }}>{out.rejected ? 'Öppna systeminställningar' : 'Aktivera kalenderexport'}</Button>
        {out.calendarId && <Button kind="quiet" onPress={() => setModal('manage')}>Hantera redan exporterade händelser</Button>}
      </> : <>
        <Panel><Setting title="Synk" subtitle="Uppdatera när schemat eller övertiden ändras" toggled={out.sync} onPress={() => change({ sync: !out.sync })} /></Panel>
        {out.pending && <Panel color={c.vac} outlined={false} style={{ padding: 13, gap: 4 }}>
          <T color={c.vacInk} weight="bold">Ändringar väntar</T><T size={12} color={c.vacInk}>Godkänn när du vill uppdatera kalendern.</T>
        </Panel>}
        <T color={c.muted} weight="bold" size={13}>Exportera</T>
        <Panel>{categoryKeys.filter((category) => category !== 'vacation' || state.prefs.unit === 'five' || out.categories.vacation)
          .map((category, i) => <View key={category}>
          {i > 0 && <Divider />}
          <Setting title={categoryLabel[category]} icon={category === 'regular' ? 'calendar' : category === 'overtime' ? 'clock' : 'sun'}
            toggled={out.categories[category]} onPress={() => {
              if (out.categories[category]) setModal({ category, fromManage: false });
              else change({ categories: { ...out.categories, [category]: true } }, true);
            }} />
        </View>)}</Panel>
        <Panel><Setting title="Påminnelser" icon="bell" value={reminderLabel} onPress={() => setModal('reminders')} /></Panel>
        <Button disabled={busy} onPress={sync}>{busy ? 'Uppdaterar…' : 'Uppdatera kalender'}</Button>
        {out.calendarId && <Button kind="quiet" onPress={() => setModal('manage')}>Hantera exporterade händelser</Button>}
      </>}
    </ScrollView>
    {confirm ? <Sheet center title="Ta bort valda händelser?" subtitle={`Exporterade händelser i ${categoryLabel[confirm.category].toLowerCase()} tas bort från telefonens kalender.`} close={() => setConfirm(undefined)}>
      <View style={[shared.row, { gap: 8 }]}>
        <Button style={{ flex: 1 }} kind="secondary" onPress={() => setConfirm(undefined)}>Avbryt</Button>
        <Button style={{ flex: 1 }} kind="danger" disabled={busy} onPress={() => {
          deleteEvents(confirm.category, confirm.range); setConfirm(undefined); setModal(undefined);
        }}>Ta bort</Button>
      </View>
    </Sheet> : modal === 'reminders' ? <Sheet title="Påminnelser" subtitle="Gäller exporterade arbetspass och övertid." close={() => setModal(undefined)}>
      {reminders.map(({ label, value }) => <Option key={label} title={label} chosen={out.reminder === value}
        onPress={() => { setModal(undefined); if (out.reminder !== value) change({ reminder: value }, true); }} />)}
    </Sheet> : modal === 'manage' ? <Sheet title="Hantera exporterade händelser" close={() => setModal(undefined)}>
      {categoryKeys.map((category) => <Button key={category} kind="secondary" onPress={() => setModal({ category, fromManage: true })}>{categoryLabel[category]}</Button>)}
    </Sheet> : modal ? <Sheet title={categoryLabel[modal.category]} subtitle="Välj vad som ska hända med exporterade händelser."
      close={() => setModal(modal.fromManage ? 'manage' : undefined)}>
      <Button disabled={busy} kind="secondary" onPress={() => { deleteEvents(modal.category, 'keep'); setModal(undefined); }}>Behåll befintliga händelser</Button>
      <Button disabled={busy} kind="quiet" onPress={() => setConfirm({ category: modal.category, range: 'future' })}>Ta bort kommande händelser</Button>
      <Button disabled={busy} kind="danger" onPress={() => setConfirm({ category: modal.category, range: 'all' })}>Ta bort alla exporterade</Button>
    </Sheet> : null}
  </>;
}
