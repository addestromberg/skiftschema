import type { State } from './domain';

export const previewDemo = typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('demo');
const key = previewDemo ? 'skiftschema:preview:demo' : 'skiftschema:preview';

export async function loadState(): Promise<State | undefined> {
  const json = window.localStorage.getItem(key);
  return json ? JSON.parse(json) as State : undefined;
}

export async function saveState(state: State) {
  window.localStorage.setItem(key, JSON.stringify(state));
}
