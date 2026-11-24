import type { ComponentRef, ReactNode } from 'react';
import { useEffect, useRef } from 'react';
import {
  Modal, Pressable, ScrollView, StyleSheet, Switch, Text, View, Platform,
  type NativeScrollEvent, type NativeSyntheticEvent, type StyleProp, type TextStyle, type ViewStyle,
} from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { useTheme } from './theme';

const icons = {
  calendar: 'M4 6H20V21H4Z M8 3V8 M16 3V8 M4 11H20 M8 15H10 M14 15H16',
  clock: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 7v5l4 2',
  settings: 'M4 7H20 M4 12H20 M4 17H20 M8 5V9 M16 10V14 M10 15V19',
  left: 'M15 5L8 12L15 19', right: 'M9 5l7 7-7 7', down: 'M5 9l7 7 7-7',
  back: 'M19 12H5m6-6-6 6 6 6', close: 'M6 6l12 12M18 6 6 18',
  sun: 'M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2',
  moon: 'M19 15C13 16 8 12 9 5 4 7 3 13 6 17c3 4 10 4 13-2Z',
  edit: 'M4 15 15 4 20 9 9 20H4Z M12 7l5 5',
  plus: 'M12 5v14m-7-7h14', more: 'M4 12h1m6.5 0h1m7 0h1',
  trash: 'M4 6h16m-11 0V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7',
  sync: 'M5 9a8 8 0 0 1 14-1l2 3m0-5v5h-5M19 15a8 8 0 0 1-14 1l-2-3m0 5v-5h5',
  alert: 'M12 3 22 20H2Z M12 9v4m0 3v1',
  info: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18m0 8v5m0-9v1',
  phone: 'M7 3h10q2 0 2 2v14q0 2-2 2H7q-2 0-2-2V5q0-2 2-2Zm3 14h4',
  layers: 'M12 4l9 5-9 5-9-5Zm-8 9 8 5 8-5m-16 4 8 5 8-5',
  bell: 'M6 11c0-4 2-6 6-6s6 2 6 6v5l2 2H4l2-2Zm4 10h4',
  check: 'M4 12l5 5L20 6',
};
export type IconName = keyof typeof icons;
export function Icon({ name, size = 20, color }: { name: IconName; size?: number; color?: string }) {
  const theme = useTheme();
  return <Svg width={size} height={size} viewBox="0 0 24 24">
    <Path d={icons[name]} fill="none" stroke={color || theme.muted} strokeWidth={1.65} strokeLinecap="round" strokeLinejoin="round" />
  </Svg>;
}

type Weight = 'regular' | 'medium' | 'bold';
export function T({ children, size = 14, color, weight = 'regular', mono = false, style, numberOfLines }:
  { children?: ReactNode; size?: number; color?: string; weight?: Weight; mono?: boolean; style?: StyleProp<TextStyle>; numberOfLines?: number }) {
  const theme = useTheme();
  return <Text numberOfLines={numberOfLines} style={[
    { color: color ?? theme.ink, fontSize: size, lineHeight: Math.ceil(size * 1.32),
      fontWeight: weight === 'bold' ? '700' : weight === 'medium' ? '500' : '400',
      letterSpacing: size >= 26 ? -size * .04 : 0,
      ...(mono ? { fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontVariant: ['tabular-nums'] } : {}) },
    style,
  ]}>{children}</Text>;
}
export function Panel({ children, style, color, outlined = true }: {
  children: ReactNode; style?: StyleProp<ViewStyle>; color?: string; outlined?: boolean;
}) {
  const theme = useTheme();
  return <View style={[{ backgroundColor: color || theme.paper, borderRadius: 17,
    borderWidth: outlined ? 1 : 0, borderColor: theme.line }, style]}>{children}</View>;
}
export function Divider() {
  const theme = useTheme();
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: theme.line }} />;
}

export function IconButton({ icon, label, onPress, small = false }: { icon: IconName; label: string; onPress: () => void; small?: boolean }) {
  const theme = useTheme();
  const size = small ? 36 : 43;
  return <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress}
    style={({ pressed }) => [shared.center, { width: size, height: size, borderRadius: 12, borderWidth: 1, borderColor: theme.line, backgroundColor: pressed ? theme.mine : theme.paper }]}>
    <Icon name={icon} size={small ? 17 : 20} color={theme.ink} />
  </Pressable>;
}

export function Button({ children, onPress, kind = 'primary', disabled = false, style }: {
  children: string; onPress: () => void; kind?: 'primary' | 'secondary' | 'quiet' | 'danger'; disabled?: boolean; style?: StyleProp<ViewStyle>;
}) {
  const c = useTheme();
  const bg = ({ primary: c.action, danger: c.warnInk, secondary: c.paper, quiet: 'transparent' })[kind];
  const fg = ({ primary: c.onAction, danger: c.canvas, secondary: c.ink, quiet: c.muted })[kind];
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress}
    style={({ pressed }) => [shared.center, { marginTop: 0, minHeight: 48, paddingHorizontal: 12, paddingVertical: 8,
      backgroundColor: bg, borderRadius: 15, borderWidth: kind === 'secondary' ? 1 : 0, borderColor: c.edge,
      opacity: disabled ? .5 : pressed ? .8 : 1 }, style]}>
    <T size={14} color={fg} weight="bold" style={{ textAlign: 'center' }}>{children}</T>
  </Pressable>;
}

export function Tag({ children, onPress, color, background, style }: {
  children: string; onPress?: () => void; color?: string; background?: string; style?: StyleProp<ViewStyle>;
}) {
  const c = useTheme();
  const text = <T size={11} color={color ?? c.muted} weight="bold" style={{ textAlign: 'center' }}>{children}</T>;
  const tagStyle = [shared.center, { minHeight: 29, borderRadius: 15, paddingHorizontal: 12, backgroundColor: background ?? c.subtle }, style];
  return onPress ? <Pressable accessibilityRole="button" onPress={onPress} style={tagStyle}>{text}</Pressable> : <View style={tagStyle}>{text}</View>;
}
export function Logo({ size = 35 }: { size?: number }) {
  const c = useTheme();
  return <View style={[shared.center, { width: size, height: size, borderRadius: Math.round(size * .29), backgroundColor: c.action }]}>
    <Icon name="calendar" size={Math.round(size * .6)} color={c.onAction} />
  </View>;
}
export function TopBar({ title, back }: { title?: string; back?: () => void }) {
  return <View style={[shared.row, { paddingHorizontal: 24, paddingVertical: 12, gap: 13 }]}>
    {back ? <IconButton icon="back" label="Tillbaka" onPress={back} /> : <Logo size={35} />}
    <T size={22} weight="bold">{title || 'Skiftschema'}</T>
  </View>;
}

export function Dock({ active, navigate }: { active: 'calendar' | 'prefs'; navigate: (page: 'calendar' | 'prefs') => void }) {
  const c = useTheme();
  return <View style={[shared.row, { justifyContent: 'center', gap: 16, paddingVertical: 7, borderTopWidth: 1,
    borderTopColor: c.line, backgroundColor: c.paper }]}>
    {([{ key: 'calendar', name: 'Schema', icon: 'calendar' }, { key: 'prefs', name: 'Preferenser', icon: 'settings' }] as const)
      .map(({ key, name, icon }) => <Pressable key={key} accessibilityRole="tab" accessibilityState={{ selected: key === active }} onPress={() => navigate(key)}
        style={[shared.row, { justifyContent: 'center', alignItems: 'center', minWidth: 132, minHeight: 44,
          gap: 8, borderRadius: 13, backgroundColor: key === active ? c.mine : c.paper }]}>
        <Icon name={icon} size={20} color={key === active ? c.mineInk : c.muted} />
        <T size={13} color={key === active ? c.mineInk : c.muted} weight="bold" style={{ includeFontPadding: false }}>{name}</T>
      </Pressable>)}
  </View>;
}

export function Option({ title, detail, chosen, onPress, icon, swatch, leading }: {
  title: string; detail?: string; chosen: boolean; onPress: () => void; icon?: IconName; swatch?: string; leading?: ReactNode;
}) {
  const c = useTheme();
  return <Pressable accessibilityRole="radio" accessibilityLabel={title} accessibilityState={{ checked: chosen }} onPress={onPress}
    style={({ pressed }) => [shared.row, { minHeight: 62, gap: 13, paddingHorizontal: 14, paddingVertical: 11, borderRadius: 16,
      borderWidth: chosen ? 1.5 : 1, borderColor: chosen ? c.action : c.line, backgroundColor: chosen || pressed ? c.mine : c.paper }]}>
    {leading ?? (icon ? <Icon name={icon} color={chosen ? c.mineInk : c.muted} /> : swatch ? <View style={{ width: 29, height: 29, borderRadius: 9, backgroundColor: swatch }} /> : null)}
    <View style={{ flex: 1, gap: 3 }}>
      <T size={15} weight="bold">{title}</T>
      {detail && <T size={12} color={c.muted}>{detail}</T>}
    </View>
    <View style={[shared.center, { width: 18, height: 18, borderRadius: 9, borderWidth: 1.5, borderColor: chosen ? c.action : c.edge }]}>
      {chosen ? <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: c.action }} /> : null}
    </View>
  </Pressable>;
}

export function Setting({ title, subtitle, value, icon, onPress, toggled, labelColor }: {
  title: string; subtitle?: string; value?: string; icon?: IconName;
  onPress?: () => void; toggled?: boolean; labelColor?: string;
}) {
  const c = useTheme();
  const contents = <>
    {icon ? <View style={[shared.center, { width: 33, height: 33, borderRadius: 10, backgroundColor: c.subtle }]}><Icon name={icon} size={18} /></View> : null}
    <View style={{ flex: 1, gap: 2 }}>
      <T size={14} weight="bold" color={labelColor}>{title}</T>
      {subtitle ? <T size={11} color={c.muted}>{subtitle}</T> : null}
    </View>
    {value ? <T size={12} color={c.muted} weight="bold">{value}</T> : null}
    {toggled !== undefined ? <Switch accessibilityLabel={title} value={toggled} onValueChange={onPress}
      trackColor={{ false: c.edge, true: c.action }} thumbColor={toggled ? c.onAction : c.paper} /> : onPress ? <Icon name="right" size={15} /> : null}
  </>;
  const row = [shared.row, { minHeight: 55, paddingHorizontal: 12, paddingVertical: 8, gap: 11 }];
  if (!onPress || toggled !== undefined) return <View style={row}>{contents}</View>;
  return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityHint={value} onPress={onPress} style={row}>{contents}</Pressable>;
}

export function Sheet({ title, subtitle, close, children, center = false }: {
  title: string; subtitle?: string; close: () => void; children: ReactNode; center?: boolean;
}) {
  const c = useTheme();
  return <Modal visible transparent onRequestClose={close} animationType="fade">
    <View style={[StyleSheet.absoluteFill, { justifyContent: center ? 'center' : 'flex-end', alignItems: 'center' }]}>
      <Pressable accessibilityLabel="Stäng" onPress={close} style={[StyleSheet.absoluteFill, { backgroundColor: c.scrim }]} />
      <View accessibilityViewIsModal style={{ width: center ? '90%' : '100%', maxWidth: center ? 400 : 480, maxHeight: '95%',
        backgroundColor: c.paper, borderColor: c.edge, borderWidth: center ? 1 : 0,
        borderRadius: 23, borderBottomLeftRadius: center ? 23 : 0, borderBottomRightRadius: center ? 23 : 0, overflow: 'hidden' }}>
        {!center && <View style={{ alignSelf: 'center', marginTop: 8, width: 42, height: 4, backgroundColor: c.edge, borderRadius: 2 }} />}
        <ScrollView contentContainerStyle={{ paddingHorizontal: 23, paddingTop: center ? 19 : 12, paddingBottom: 24, gap: 14 }}>
          <View style={[shared.row, { justifyContent: 'space-between', gap: 9 }]}>
            <T size={21} style={{ flex: 1 }} weight="bold">{title}</T>
            {!center && <IconButton icon="close" label="Stäng" onPress={close} small />}
          </View>
          {subtitle ? <T color={c.muted} size={13}>{subtitle}</T> : null}
          {children}
        </ScrollView>
      </View>
    </View>
  </Modal>;
}

function Wheel({ value, length, label, change }: { value: number; length: number; label: string; change: (v: number) => void }) {
  const c = useTheme();
  const ref = useRef<ComponentRef<typeof ScrollView>>(null);
  const initial = useRef(value);
  const row = 34;
  useEffect(() => { ref.current?.scrollTo({ y: initial.current * row, animated: false }); }, []);
  const scroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const index = Math.max(0, Math.min(length - 1, Math.round(event.nativeEvent.contentOffset.y / row)));
    if (value !== index) change(index);
  };
  return <View style={[shared.center, { flex: 1, minWidth: 45, borderRadius: 13, backgroundColor: c.subtle, paddingHorizontal: 4, paddingBottom: 6 }]}>
    <View style={{ height: row * 3, alignSelf: 'stretch' }}>
      <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: row, height: row, backgroundColor: c.mine, borderRadius: 8 }} />
      <ScrollView ref={ref} nestedScrollEnabled showsVerticalScrollIndicator={false} snapToInterval={row}
        decelerationRate="fast" onScroll={scroll} scrollEventThrottle={35} contentContainerStyle={{ paddingVertical: row }}>
        {Array.from({ length }, (_, i) => <Pressable key={i} accessibilityLabel={`${i} ${label}`} onPress={() => {
          change(i); ref.current?.scrollTo({ y: i * row, animated: false });
        }} style={[shared.center, { height: row }]}>
          <T size={i === value ? 21 : 15} color={i === value ? c.mineInk : c.quiet} weight={i === value ? 'bold' : 'regular'} mono>{String(i).padStart(2, '0')}</T>
        </Pressable>)}
      </ScrollView>
    </View>
    <T size={10} color={c.muted} weight="bold">{label}</T>
  </View>;
}
export function TimeWheel({ value, change }: { value: string; change: (value: string) => void }) {
  const [h, m] = value.split(':');
  const c = useTheme();
  return <View style={[shared.row, { gap: 5 }]}>
    <Wheel length={24} value={Number(h)} label="Tim" change={(value) => change(`${String(value).padStart(2, '0')}:${m}`)} />
    <T weight="bold" size={19} color={c.quiet}>:</T>
    <Wheel length={60} value={Number(m)} label="Min" change={(value) => change(`${h}:${String(value).padStart(2, '0')}`)} />
  </View>;
}

export const shared = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  center: { alignItems: 'center', justifyContent: 'center' },
  page: { paddingHorizontal: 24, paddingBottom: 24, gap: 15 },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center' },
});
