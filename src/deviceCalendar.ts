import { Platform } from 'react-native';
import {
  CalendarAccessLevel, createCalendar, EntityTypes, getCalendarPermissions, getCalendars, getDefaultCalendarSync,
  requestCalendarPermissions, type ExpoCalendarEvent,
} from 'expo-calendar';
import { calendarName, eventZone, marker, ownedKey, type CalendarEvent, type DeviceCalendar } from './calendarEvents';

const localOwner = 'se.skiftschema.app';
export async function calendarPermission(request: boolean) {
  return (await (request ? requestCalendarPermissions() : getCalendarPermissions())).granted;
}

function details(event: CalendarEvent) {
  return {
    title: event.title, notes: marker + event.key, startDate: new Date(event.start), endDate: new Date(event.end),
    allDay: event.allDay, timeZone: eventZone(event),
    alarms: event.reminder === null ? [] : [{ relativeOffset: -event.reminder }],
  };
}

export async function openCalendar(id: string | null, create: boolean): Promise<DeviceCalendar | undefined> {
  const calendars = await getCalendars(EntityTypes.EVENT);
  let calendar = calendars.find((item) => item.id === id && item.title === calendarName)
    ?? calendars.find((item) => item.title === calendarName && item.name === localOwner && item.ownerAccount === localOwner);
  if (!calendar && create) {
    const base = { title: calendarName, color: '#08735B', entityType: EntityTypes.EVENT, name: localOwner };
    if (Platform.OS === 'android') {
      calendar = await createCalendar({ ...base, source: { type: 'LOCAL', name: localOwner, isLocalAccount: true },
        ownerAccount: localOwner, accessLevel: CalendarAccessLevel.OWNER });
    } else {
      const source = getDefaultCalendarSync().source;
      calendar = await createCalendar({ ...base, source, sourceId: source?.id });
    }
  }
  if (!calendar) return undefined;
  const target = calendar;
  const events = new Map<string, ExpoCalendarEvent>();
  return {
    id: target.id,
    async list(from, until) {
      const fetched = await target.listEvents(new Date(from), new Date(until));
      return fetched.flatMap((event) => {
        const key = ownedKey(event.notes);
        if (!key) return [];
        events.set(event.id, event);
        return [{ key, id: event.id, title: event.title, start: new Date(event.startDate).getTime(),
          end: new Date(event.endDate).getTime(), allDay: event.allDay,
          reminder: event.alarms?.[0]?.relativeOffset === undefined ? null : -event.alarms[0].relativeOffset }];
      });
    },
    async create(event) { await target.createEvent(details(event)); },
    async update(eventId, event) { await events.get(eventId)?.update(details(event)); },
    async remove(eventId) { await events.get(eventId)?.delete(); },
  };
}
