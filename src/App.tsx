import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, BackHandler, Platform, useColorScheme, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { atTime, initialState, isOwnShift, shiftKey, yearKey, type Category, type Preferences, type ShiftRef, type State, type Unit } from './domain';
import { schedules } from './rosters';
import { loadState, saveState } from './storage';
import { demo, isPreview } from './preview';
import { calendarPermission, openCalendar } from './deviceCalendar';
import { clearCategory, reconcile } from './calendarEvents';
import { CalendarScreen } from './CalendarScreen';
import { Onboarding } from './Onboarding';
import { DayActions, ShiftActions } from './OvertimeEditor';
import { SettingsScreen, type SettingsPage } from './SettingsScreen';
import { themeFor, ThemeContext } from './theme';
import { Button, Dock, Panel, Sheet, T, shared } from './ui';

const docket = atTime('2027-07-13', '16:42');
const readClock = () => demo ? docket : Date.now();
const demoState: State = {
  ...initialState,
  prefs: { ...initialState.prefs, unit: 'five', crews: { five: 'B' }, vacation: { [yearKey('five', 2027)]: 'yellow' } },
};
type Route = 'calendar' | SettingsPage;
const preferenceParent = (page: Route): Route => page === 'accent' ? 'appearance' : page === 'prefs' ? 'calendar' : 'prefs';

export default function App() {
  const [state, setState] = useState<State>(demo ? demoState : initialState);
  const current = useRef(state);
  const [ready, setReady] = useState(false);
  const [route, setRoute] = useState<Route>('calendar');
  const [shift, setShift] = useState<ShiftRef>();
  const [openDay, setOpenDay] = useState<{ day: string; unit: Unit }>();
  const [confirmSync, setConfirmSync] = useState(false);
  const [now, setNow] = useState(readClock);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string>();
  const storageQueue = useRef(Promise.resolve());
  const calendarQueue = useRef(Promise.resolve());
  const osTheme = useColorScheme();
  const prefs = state.prefs;
  const face = prefs.appearance === 'system' ? osTheme === 'dark' ? 'dark' : 'light' : prefs.appearance;
  const c = themeFor(face, prefs.accent);
  const notify = useCallback((text: string) => setNotice(text), []);
  const patch = useCallback((apply: (previous: State) => State) => {
    const next = apply(current.current);
    current.current = next;
    setState(next);
    storageQueue.current = storageQueue.current.then(() => saveState(next)).catch(() => {
      notify('Ändringen sparades inte på enheten. Försök igen.');
    });
  }, [notify]);
  const updateExport = useCallback((fields: Partial<Preferences['export']>) => {
    patch((item) => ({ ...item, prefs: { ...item.prefs, export: { ...item.prefs.export, ...fields } } }));
  }, [patch]);
  const work = useCallback((operation: () => Promise<void>) => {
    calendarQueue.current = calendarQueue.current.then(async () => {
      setBusy(true);
      try {
        await operation();
      } catch {
        notify('Telefonens kalender kunde inte uppdateras. Försök igen i Kalenderexport.');
      } finally {
        setBusy(false);
      }
    });
  }, [notify]);
  const sync = useCallback((manual: boolean) => {
    work(async () => {
      const latest = current.current;
      const out = latest.prefs.export;
      if (!out.enabled || (!manual && (!out.sync || out.pending))) return;
      if (!(await calendarPermission(manual))) {
        updateExport({ enabled: false, rejected: true });
        if (manual) notify('Kalenderåtkomst saknas.');
        return;
      }
      const calendar = await openCalendar(out.calendarId, true);
      if (!calendar) throw new Error('Calendar unavailable');
      if (calendar.id !== out.calendarId) updateExport({ calendarId: calendar.id });
      await reconcile(calendar, latest, schedules, readClock());
      // A new edit made while the provider was writing still requires approval.
      if (manual && latest.prefs.export.pending &&
          current.current.prefs.unit === latest.prefs.unit &&
          current.current.prefs.crews === latest.prefs.crews &&
          current.current.prefs.vacation === latest.prefs.vacation &&
          current.current.prefs.overrides === latest.prefs.overrides &&
          current.current.prefs.export.categories === out.categories &&
          current.current.prefs.export.reminder === out.reminder) updateExport({ pending: false });
      if (manual) notify('Kalendern är uppdaterad.');
    });
  }, [notify, updateExport, work]);

  useEffect(() => {
    let mounted = true;
    loadState().then((saved) => {
      if (!mounted || !saved) return;
      current.current = saved;
      setState(saved);
    }).catch(() => notify('Lokala inställningar kunde inte läsas.')).finally(() => { if (mounted) setReady(true); });
    return () => { mounted = false; };
  }, [notify]);
  useEffect(() => {
    if (!ready) return;
    sync(false);
    const heartbeat = setInterval(() => setNow(readClock()), 15000);
    const app = AppState.addEventListener('change', (value) => {
      if (value === 'active') { setNow(readClock()); sync(false); }
    });
    return () => { clearInterval(heartbeat); app.remove(); };
  }, [ready, sync]);
  useEffect(() => {
    if (route === 'calendar') return;
    const navigation = BackHandler.addEventListener('hardwareBackPress', () => { setRoute(preferenceParent(route)); return true; });
    return () => navigation.remove();
  }, [route]);
  useEffect(() => {
    if (!notice) return;
    const timeout = setTimeout(() => setNotice(undefined), 5500);
    return () => clearTimeout(timeout);
  }, [notice]);

  const updatePrefs = (transform: (prefs: Preferences) => Preferences, calendarChange = false) => {
    const previous = current.current.prefs;
    const next = transform(previous);
    const needsApproval = calendarChange && next.export.enabled && next.export.sync;
    patch((item) => ({ ...item, prefs: needsApproval ? { ...next, export: { ...next.export, pending: true } } : next }));
    if (needsApproval) setConfirmSync(true);
    else if (!previous.export.sync && next.export.sync) sync(false);
  };
  const setExport = (enabled: boolean) => {
    if (!enabled) { updateExport({ enabled: false }); return; }
    work(async () => {
      const granted = await calendarPermission(true);
      updateExport({ enabled: granted, rejected: !granted });
      if (granted) sync(false);
    });
  };
  const deleteEvents = (category: Category, range: 'keep' | 'future' | 'all') => {
    const out = current.current.prefs.export;
    updateExport({ categories: { ...out.categories, [category]: false } });
    if (range === 'keep') return;
    work(async () => {
      if (!(await calendarPermission(true))) {
        notify('Kalenderåtkomst krävs för att ta bort händelser.');
        return;
      }
      const calendar = await openCalendar(out.calendarId, false);
      if (calendar) await clearCategory(calendar, category, range === 'all', schedules, readClock());
      notify('Valda kalenderhändelser har tagits bort.');
    });
  };
  const goCalendar = () => setRoute('calendar');
  const goPrefs = () => setRoute('prefs');
  const tabs = route === 'calendar' || route === 'prefs';
  return <SafeAreaProvider>
    <ThemeContext.Provider value={c}>
      <View style={{ flex: 1, backgroundColor: c.canvas }}>
        <StatusBar style={face === 'dark' ? 'light' : 'dark'} />
        <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor: c.canvas, width: '100%',
          maxWidth: isPreview ? 470 : undefined, alignSelf: 'center', borderColor: c.line, borderWidth: isPreview ? 1 : 0 }}>
          {Platform.OS === 'web' && <View style={[shared.row, { minHeight: 30, paddingHorizontal: 24, justifyContent: 'space-between' }]}>
            <T size={12} color={c.ink} weight="bold">{demo ? '16:42' : 'Skiftschema'}</T>
            <T size={10} color={c.quiet}>{demo ? 'Demo' : 'Förhandsvisning'}</T>
          </View>}
          {!ready ? <View style={[shared.center, { flex: 1 }]}><T color={c.muted}>Läser schemat…</T></View>
            : !prefs.unit ? <Onboarding now={now} complete={({ unit, crew, year, color }) => {
              patch((item) => ({ ...item, prefs: { ...item.prefs, unit, crews: { ...item.prefs.crews, [unit]: crew },
                vacation: year && color ? { ...item.prefs.vacation, [yearKey(unit, year)]: color } : item.prefs.vacation } }));
            }} /> : <>
              <View style={{ flex: 1, display: route === 'calendar' ? 'flex' : 'none' }}>
                <CalendarScreen key={prefs.unit} state={state} now={now} active={route === 'calendar'} openPrefs={goPrefs}
                  openDay={(day, unit) => setOpenDay({ day, unit })} />
              </View>
              {route !== 'calendar' && <SettingsScreen page={route} go={setRoute} showCalendar={goCalendar} state={state} now={now} update={updatePrefs} setExport={setExport}
                    sync={() => { if (prefs.export.pending) setConfirmSync(true); else sync(true); }}
                    deleteEvents={deleteEvents} busy={busy} face={face} />}
              {tabs && <Dock active={route} navigate={(page) => page === 'calendar' ? goCalendar() : goPrefs()} />}
            </>}
          {notice && <Panel color={c.hero} outlined={false} style={{ position: 'absolute', bottom: 62, left: 16, right: 16, padding: 13 }}>
            <View accessibilityLiveRegion="polite"><T size={13} color={c.heroMain}>{notice}</T></View>
          </Panel>}
          {openDay && !shift && <DayActions day={openDay.day} unit={openDay.unit} state={state}
            close={() => setOpenDay(undefined)} choose={setShift} />}
          {shift && <ShiftActions key={shiftKey(shift)} shift={shift} state={state} fromDay={!!openDay} close={() => setShift(undefined)}
            save={(entry) => {
              if (isOwnShift(entry, current.current.prefs)) return;
              patch((item) => ({ ...item, overtime: { ...item.overtime, [shiftKey(entry)]: entry } }));
              setShift(undefined); sync(false);
            }}
            remove={() => {
              patch((item) => {
                const overtime = { ...item.overtime };
                delete overtime[shiftKey(shift)];
                return { ...item, overtime };
              });
              setShift(undefined); sync(false);
            }} />}
          {confirmSync && <Sheet center title="Uppdatera kalendern?" subtitle="Inställningen är sparad i appen. Vill du uppdatera telefonens kalender?"
            close={() => setConfirmSync(false)}>
            <View style={[shared.row, { gap: 8 }]}>
              <Button style={{ flex: 1 }} kind="secondary" onPress={() => setConfirmSync(false)}>Inte nu</Button>
              <Button style={{ flex: 1 }} disabled={busy} onPress={() => { setConfirmSync(false); sync(true); }}>Uppdatera</Button>
            </View>
          </Sheet>}
        </SafeAreaView>
      </View>
    </ThemeContext.Provider>
  </SafeAreaProvider>;
}
