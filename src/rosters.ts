import type { Schedule } from './domain';

declare const require: {
  context(path: string, recursive: boolean, pattern: RegExp): {
    keys(): string[];
    (path: string): Schedule;
  };
};

const files = require.context('../data', false, /^\.\/(?:five|six)-\d{4}\.json$/);
export const schedules = files.keys().map((path) => files(path)).sort((a, b) => a.year - b.year);
