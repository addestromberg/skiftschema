import { createContext, useContext } from 'react';
import type { Accent } from './domain';

type Face = 'light' | 'dark';

const statusColors = {
  light: {
    ot: '#FCE6D7', otInk: '#8B421D', vac: '#FFF0C4', vacInk: '#634812',
    warn: '#FBE5E2', warnInk: '#98382F', blue: '#5489AF', red: '#CD6C61',
    yellow: '#D8AB4F', green: '#68A784',
  },
  dark: {
    ot: '#52382D', otInk: '#FFBD90', vac: '#473B22', vacInk: '#F5D88B',
    warn: '#4C302E', warnInk: '#FFB1A4', blue: '#83B6DA', red: '#ED958A',
    yellow: '#F9D881', green: '#92CAA7',
  },
};

const forest = {
  light: {
    canvas: '#F5F8F5', paper: '#FFFFFF', subtle: '#EEF3EF', line: '#DDE6DF',
    edge: '#C7D5CA', ink: '#17251F', muted: '#496157', quiet: '#586F63',
    weekend: '#EEF3EF', off: '#E8EEEA', scrim: '#0B1B146B',
    action: '#08735B', onAction: '#FFFFFF', mine: '#DDF2E6', mineInk: '#1C644D',
    hero: '#153C33', heroMain: '#F0FFF6', heroMuted: '#B8DEC8',
    heroBadge: '#C8EFDA', heroBadgeInk: '#174432',
  },
  dark: {
    canvas: '#101714', paper: '#19231E', subtle: '#222F28', line: '#34443B',
    edge: '#475B4E', ink: '#EFF8F2', muted: '#ACC0B3', quiet: '#93A99B',
    weekend: '#202C25', off: '#26342C', scrim: '#070D09A6',
    action: '#75DBB2', onAction: '#10251C', mine: '#273F34', mineInk: '#A3E9C7',
    hero: '#233B31', heroMain: '#F0FFF6', heroMuted: '#BCD8CA',
    heroBadge: '#B4EBCD', heroBadgeInk: '#183F30',
  },
};

type Palette = typeof forest.light;

// Havsblå and Plommon surfaces are fixed Material HCT tones generated from the
// palette's source hue. The stored palette choice is independent of appearance.
const ocean: Record<Face, Palette> = {
  light: {
    canvas: '#F0F3FF', paper: '#FDFBFF', subtle: '#DEE8FF', line: '#CBD8F2',
    edge: '#B8C4DE', ink: '#1D2430', muted: '#475369', quiet: '#535F75',
    weekend: '#E7EEFF', off: '#D6E3FE', scrim: '#1118246B',
    action: '#245FA9', onAction: '#FFFFFF', mine: '#E0EDFF', mineInk: '#214C83',
    hero: '#1B3659', heroMain: '#F2F8FF', heroMuted: '#BCCFEA',
    heroBadge: '#C6DFFD', heroBadgeInk: '#1D3B61',
  },
  dark: {
    canvas: '#0D1828', paper: '#172233', subtle: '#1C2C46', line: '#33435D',
    edge: '#4A5A76', ink: '#ECF1FF', muted: '#B4C4E5', quiet: '#99A9C9',
    weekend: '#1E2839', off: '#20314B', scrim: '#040E1FA6',
    action: '#98C4FF', onAction: '#101E31', mine: '#283C55', mineInk: '#B9D7FF',
    hero: '#233852', heroMain: '#F3F8FF', heroMuted: '#BDD2ED',
    heroBadge: '#B9D8FF', heroBadgeInk: '#1B385D',
  },
};

const plum: Record<Face, Palette> = {
  light: {
    canvas: '#FFEFFB', paper: '#FFFBFF', subtle: '#FDDFFB', line: '#EBCFEA',
    edge: '#D7BBD6', ink: '#2C202C', muted: '#614B62', quiet: '#6D576E',
    weekend: '#FDE8FA', off: '#F7DAF5', scrim: '#1F14206B',
    action: '#783C82', onAction: '#FFFFFF', mine: '#F1E1F5', mineInk: '#64366E',
    hero: '#452B52', heroMain: '#FEF5FF', heroMuted: '#E5CBE9',
    heroBadge: '#EDCEF4', heroBadgeInk: '#50305C',
  },
  dark: {
    canvas: '#211223', paper: '#2C1C2D', subtle: '#3B243E', line: '#533A55',
    edge: '#6B516D', ink: '#FFEBFC', muted: '#DBB9DB', quiet: '#BE9EBF',
    weekend: '#332234', off: '#3F2842', scrim: '#170919A6',
    action: '#DEB2F1', onAction: '#281730', mine: '#432F4C', mineInk: '#EBC9F6',
    hero: '#402E49', heroMain: '#FCF2FF', heroMuted: '#DDBFE6',
    heroBadge: '#E9C8F4', heroBadgeInk: '#48294F',
  },
};

const amber: Record<Face, Palette> = {
  light: {
    canvas: '#FFF2E2', paper: '#FFFCF4', subtle: '#FAE8CC', line: '#F6D29C',
    edge: '#D9B580', ink: '#2C2211', muted: '#674E24', quiet: '#6F552A',
    weekend: '#FFEBD1', off: '#FFDEAC', scrim: '#1F16076B',
    action: '#785200', onAction: '#FFFFFF', mine: '#F9D39A', mineInk: '#604100',
    hero: '#422C10', heroMain: '#FFF8E9', heroMuted: '#EBD2A6',
    heroBadge: '#F8CD85', heroBadgeInk: '#513600',
  },
  dark: {
    canvas: '#211502', paper: '#2D1F08', subtle: '#3D2802', line: '#553E15',
    edge: '#6F552A', ink: '#FFEEDA', muted: '#E1BF8A', quiet: '#CAA974',
    weekend: '#33250E', off: '#412D05', scrim: '#160C00A6',
    action: '#FFC563', onAction: '#322000', mine: '#52360E', mineInk: '#FFD797',
    hero: '#39270B', heroMain: '#FFF8E9', heroMuted: '#EBD2A6',
    heroBadge: '#FFD28B', heroBadgeInk: '#513600',
  },
};

// Nord's Polar Night, Snow Storm, and Frost colors mapped to app surfaces.
const nord: Record<Face, Palette> = {
  light: {
    canvas: '#ECEFF4', paper: '#F8FAFC', subtle: '#E5E9F0', line: '#D8DEE9',
    edge: '#B8C3D3', ink: '#2E3440', muted: '#434C5E', quiet: '#4C566A',
    weekend: '#E1E6EE', off: '#D6DEE9', scrim: '#2E34406B',
    action: '#37577C', onAction: '#FFFFFF', mine: '#D9ECF0', mineInk: '#2C5464',
    hero: '#3B4252', heroMain: '#ECEFF4', heroMuted: '#D8DEE9',
    heroBadge: '#8FBCBB', heroBadgeInk: '#213839',
  },
  dark: {
    canvas: '#2E3440', paper: '#3B4252', subtle: '#434C5E', line: '#4C566A',
    edge: '#6B7A92', ink: '#ECEFF4', muted: '#E5E9F0', quiet: '#D8DEE9',
    weekend: '#353D4B', off: '#414B5D', scrim: '#232732A6',
    action: '#88C0D0', onAction: '#202936', mine: '#405C67', mineInk: '#D5F2FA',
    hero: '#364854', heroMain: '#ECEFF4', heroMuted: '#D8DEE9',
    heroBadge: '#8FBCBB', heroBadgeInk: '#213839',
  },
};

// The dark palette uses the laptop theme's sea-green, witch-light and violet.
// The light palette carries those hues onto readable, pale surfaces.
const deepOne: Record<Face, Palette> = {
  light: {
    canvas: '#E8F7F1', paper: '#F7FFFB', subtle: '#D9EFE6', line: '#C6E1D4',
    edge: '#9BC4B2', ink: '#13261D', muted: '#345849', quiet: '#406355',
    weekend: '#DFF3EB', off: '#D2E7DB', scrim: '#060C0A6B',
    action: '#006C48', onAction: '#FFFFFF', mine: '#CDEEDC', mineInk: '#125940',
    hero: '#123A2A', heroMain: '#E6F2E8', heroMuted: '#B5C9B8',
    heroBadge: '#E7D5FC', heroBadgeInk: '#462566',
  },
  dark: {
    canvas: '#0A1210', paper: '#142019', subtle: '#1D3328', line: '#284638',
    edge: '#4A6657', ink: '#E6F2E8', muted: '#B5C9B8', quiet: '#A0B5A7',
    weekend: '#101B16', off: '#1D2B22', scrim: '#030706A6',
    action: '#43D68C', onAction: '#060C0A', mine: '#1F3B2E', mineInk: '#6EE8A8',
    hero: '#14251E', heroMain: '#E6F2E8', heroMuted: '#CFDFD2',
    heroBadge: '#BD91F5', heroBadgeInk: '#221534',
  },
};

export type Theme = Palette & typeof statusColors.light;

function withStatusColors(palette: Record<Face, Palette>): Record<Face, Theme> {
  return {
    light: { ...palette.light, ...statusColors.light },
    dark: { ...palette.dark, ...statusColors.dark },
  };
}
const palettes: Record<Accent, Record<Face, Theme>> = {
  forest: withStatusColors(forest),
  ocean: withStatusColors(ocean),
  plum: withStatusColors(plum),
  amber: withStatusColors(amber),
  nord: withStatusColors(nord),
  'deep-one': withStatusColors(deepOne),
};

export const themeFor = (face: Face, accent: Accent): Theme => palettes[accent][face];
export const accentLabel: Record<Accent, string> = {
  forest: 'Skogsgrön',
  ocean: 'Havsblå',
  plum: 'Plommon',
  amber: 'Bärnsten',
  nord: 'Nord',
  'deep-one': 'Cthulhu Deep One',
};
export const accentChoices: Accent[] = Object.keys(accentLabel) as Accent[];
export const ThemeContext = createContext<Theme>(themeFor('light', 'forest'));
export const useTheme = () => useContext(ThemeContext);
