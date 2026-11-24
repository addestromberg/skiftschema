import assert from 'node:assert/strict';
import { test } from 'node:test';
import { accentChoices, themeFor, type Theme } from '../src/theme';

function luminance(hex: string) {
  return [0.2126, 0.7152, 0.0722].reduce((sum, factor, i) => {
    const channel = parseInt(hex.slice(1 + 2 * i, 3 + 2 * i), 16) / 255;
    return sum + factor * (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  }, 0);
}
function contrast(a: string, b: string) {
  const aa = luminance(a);
  const bb = luminance(b);
  return (Math.max(aa, bb) + 0.05) / (Math.min(aa, bb) + 0.05);
}

test('every named palette has its own application surfaces in Light and Dark', () => {
  const roles = ['canvas', 'paper', 'subtle', 'line', 'edge', 'ink', 'muted', 'quiet', 'weekend', 'off', 'scrim'] as const;
  for (const face of ['light', 'dark'] as const) {
    for (const role of roles) {
      assert.equal(new Set(accentChoices.map((name) => themeFor(face, name)[role])).size, accentChoices.length, `${face} ${role}`);
    }
  }
  for (const name of accentChoices) {
    assert.notEqual(themeFor('light', name).canvas, themeFor('dark', name).canvas);
  }
});

test('palette text and status labels have at least 4.5:1 contrast', () => {
  const textSurfaces = ['canvas', 'paper', 'subtle', 'weekend', 'mine'] as const;
  const statusPairs: [keyof Theme, keyof Theme][] = [
    ['action', 'onAction'], ['mine', 'mineInk'], ['hero', 'heroMain'], ['hero', 'heroMuted'],
    ['heroBadge', 'heroBadgeInk'], ['ot', 'otInk'], ['vac', 'vacInk'], ['warn', 'warnInk'],
  ];
  for (const face of ['light', 'dark'] as const) {
    for (const name of accentChoices) {
      const theme = themeFor(face, name);
      const pairs = [...statusPairs, ...textSurfaces.flatMap((surface): [keyof Theme, keyof Theme][] =>
        (['ink', 'muted', 'quiet'] as const).map((text) => [surface, text]))];
      for (const [background, text] of pairs) {
        assert.ok(contrast(theme[background], theme[text]) >= 4.5, `${face} ${name} ${background}/${text}`);
      }
    }
  }
});
