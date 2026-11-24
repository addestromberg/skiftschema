import { openDatabaseAsync } from 'expo-sqlite';
import type { State } from './domain';

let opening: ReturnType<typeof openDatabaseAsync> | undefined;
async function database() {
  opening ??= openDatabaseAsync('skiftschema.db').then(async (db) => {
    await db.execAsync('PRAGMA journal_mode = WAL; CREATE TABLE IF NOT EXISTS state (id TEXT PRIMARY KEY, value TEXT NOT NULL);');
    return db;
  }).catch((error) => { opening = undefined; throw error; });
  return opening;
}

export async function loadState(): Promise<State | undefined> {
  const row = await (await database()).getFirstAsync<{ value: string }>('SELECT value FROM state WHERE id = ?', 'profile');
  return row ? JSON.parse(row.value) as State : undefined;
}

export async function saveState(state: State) {
  await (await database()).runAsync(
    'INSERT INTO state (id, value) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET value = excluded.value;',
    'profile', JSON.stringify(state),
  );
}
