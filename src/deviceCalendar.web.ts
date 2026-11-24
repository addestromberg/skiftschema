import type { DeviceCalendar, NativeEvent } from './calendarEvents';

const events = new Map<string, NativeEvent>();
let counter = 0;
export async function calendarPermission(_request: boolean) { return true; }
export async function openCalendar(id: string | null, create: boolean): Promise<DeviceCalendar | undefined> {
  if (!id && !create) return undefined;
  return {
    id: 'preview',
    async list() { return [...events.values()]; },
    async create(event) { const key = String(++counter); events.set(key, { ...event, id: key }); },
    async update(key, event) { events.set(key, { ...event, id: key }); },
    async remove(key) { events.delete(key); },
  };
}
