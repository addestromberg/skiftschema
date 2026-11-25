package se.skiftschema.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.function.IntConsumer;

final class PreferencesPage {
    private static final String[] MODES = {"system", "light", "dark"};
    private static final String[] MODES_LABELS = {"System", "Ljust", "Mörkt"};
    private static final int[] MODE_ICONS = {R.drawable.ic_phone, R.drawable.ic_sun, R.drawable.ic_moon};
    private static final Integer[] REMINDER_MINUTES = {null, 15, 30, 60, 1440};
    private static final String[] REMINDERS = {"Ingen påminnelse", "15 minuter före", "30 minuter före",
            "1 timme före", "1 dag före"};
    private static final int[] CATEGORY_ICONS = {R.drawable.ic_calendar, R.drawable.ic_clock, R.drawable.ic_sun};
    private static final String[] TRANSITION_NAMES = {"Bryttid för schemadag", "Skiftbyte FM till EM",
            "Skiftbyte EM till NA", "Skiftbyte FM till NA"};
    private final MainActivity app;
    private Ui ui;
    private Palette c;
    private Profile profile;
    private int year;
    private String vacationChoice;
    private Schedule.Times times;
    private String timingError;
    private String crewChoice;

    PreferencesPage(MainActivity app) { this.app = app; }

    void enter(String route) {
        if (app.profile == null || app.profile.unit == null) return;
        String unit = app.profile.unit;
        if ("timing".equals(route) || "vacation".equals(route)) {
            year = app.schedule.latestYear(unit, app.now());
            times = app.schedule.times(app.profile, unit, year);
            vacationChoice = app.profile.vacation.get(Schedule.yearKey(unit, year));
            timingError = null;
        }
    }

    void build(Ui.Stack screen) {
        ui = app.ui;
        c = ui.c;
        profile = app.profile;
        String route = app.route;
        String title = switch (route) {
            case "appearance", "accent" -> "Utseende";
            case "timing" -> "Avancerat";
            case "export" -> "Kalenderexport";
            case "vacation" -> "Semestergrupp";
            case "calendar-help" -> "Kalendergester";
            default -> null;
        };
        String parent = "accent".equals(route) ? "appearance" : "prefs";
        screen.addView(ui.top(title, title == null ? null : () -> app.go(parent)));
        Ui.Stack page = ui.column(15);
        ui.pad(page, 24, 0, 24, 24);
        switch (route) {
            case "appearance" -> appearance(page);
            case "accent" -> accent(page);
            case "timing" -> timing(page);
            case "export" -> export(page);
            case "vacation" -> vacation(page);
            case "calendar-help" -> {
                page.addView(ui.headline("Så använder du schemat", 36));
                page.addView(ui.guide());
            }
            default -> summary(page);
        }
        ScrollView scroll = ui.scroll(page);
        app.pageScroll = scroll;
        ui.stretch(screen, scroll);
    }

    private void summary(Ui.Stack page) {
        String unit = profile.unit;
        int current = app.schedule.latestYear(unit, app.now());
        page.addView(ui.headline("Preferenser", 37));
        page.addView(ui.section("Ditt schema"));
        Ui.Stack mySchedule = ui.panel(0, c.paper, true);
        mySchedule.addView(setting("Schema", Schedule.label(unit), R.drawable.ic_layers, this::chooseScheme));
        line(mySchedule, setting("Skiftlag", profile.crews.get(unit), R.drawable.ic_calendar,
                () -> chooseCrew(unit, false, true)));
        if ("five".equals(unit)) {
            Schedule.Roster roster = app.schedule.roster(unit, current);
            Schedule.Vacation group = roster == null ? null
                    : roster.group(profile.vacation.get(Schedule.yearKey(unit, current)));
            line(mySchedule, setting("Semestergrupp", group == null ? "Ingen" : group.label() + " · " + current,
                    R.drawable.ic_sun, () -> app.go("vacation")));
        }
        page.addView(mySchedule);
        page.addView(ui.section("App och kalender"));
        Profile.Export out = profile.export;
        Ui.Stack application = ui.panel(0, c.paper, true);
        application.addView(setting("Utseende", modeLabel(profile.appearance),
                R.drawable.ic_phone, () -> app.go("appearance")));
        line(application, setting("Kalenderexport", out.enabled ? out.pending ? "Ändringar väntar" : "På" : "Av",
                R.drawable.ic_sync, () -> app.go("export")));
        line(application, setting("Avancerat", null, R.drawable.ic_clock, () -> app.go("timing")));
        line(application, setting("Kalendergester", null, R.drawable.ic_info, () -> app.go("calendar-help")));
        page.addView(application);
    }

    private void line(Ui.Stack panel, View view) {
        if (panel.getChildCount() != 0) panel.addView(ui.divider());
        panel.addView(view);
    }

    private View setting(String title, String value, int res, Runnable action) {
        return ui.setting(title, null, value, res, null, action);
    }

    private View singleSetting(View setting) {
        Ui.Stack panel = ui.panel(0, c.paper, true);
        panel.addView(setting);
        return panel;
    }

    private void chooseScheme() {
        app.sheet("Välj schema", null, false, null, panel -> {
            for (String unit : new String[]{"five", "six"}) {
                boolean current = unit.equals(profile.unit);
                panel.addView(ui.option(Schedule.label(unit), MainActivity.unitDetail(unit), current,
                        ui.optionIcon(R.drawable.ic_layers, current), () -> {
                            if (current) app.dismissSheet();
                            else if (profile.crews.containsKey(unit)) app.changeUnit(unit, profile.crews.get(unit));
                            else chooseCrew(unit, true, true);
                        }));
            }
        });
    }

    private void chooseCrew(String unit, boolean fromScheme, boolean first) {
        if (first) crewChoice = profile.crews.get(unit);
        app.sheet("Välj skiftlag", Schedule.label(unit), false, fromScheme ? this::chooseScheme : null,
                panel -> {
                    panel.addView(ui.crewGrid(unit, crewChoice, crew -> {
                        crewChoice = crew;
                        chooseCrew(unit, fromScheme, false);
                    }));
                    panel.addView(ui.button("Spara skiftlag", Ui.PRIMARY, crewChoice != null, 48,
                            () -> app.changeUnit(unit, crewChoice)));
                });
    }

    private String modeLabel(String mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return MODES_LABELS[i];
        return MODES_LABELS[0];
    }

    private void appearance(Ui.Stack page) {
        page.addView(ui.headline("Välj tema", 36));
        Ui.Stack themes = ui.row(12);
        ui.surface(themes, c.paper, 17, c.line, 1);
        ui.pad(themes, 12);
        for (int i = 1; i < MODES.length; i++) {
            Palette preview = Palette.get(profile.accent, i == 2);
            Ui.Stack card = ui.column(6);
            Ui.Stack thumbnail = ui.column(8);
            ui.surface(thumbnail, preview.canvas, 13, preview.line, 1);
            ui.pad(thumbnail, 9);
            thumbnail.setMinimumHeight(ui.dp(92));
            Ui.Stack top = ui.row(6);
            top.addView(ui.block(preview.action, 2), ui.size(7, 7));
            top.addView(ui.block(preview.ink, 2), ui.size(51, 5));
            thumbnail.addView(top);
            thumbnail.addView(ui.block(preview.mine, 5), ui.size(Ui.MATCH, 19));
            Ui.Stack days = ui.row(3);
            for (int j = 0; j < 5; j++) {
                days.addView(ui.block(j == 1 ? preview.action : preview.line, 3),
                        new LinearLayout.LayoutParams(0, ui.dp(18), 1));
            }
            thumbnail.addView(days);
            card.addView(thumbnail);
            card.addView(ui.centered(MODES_LABELS[i], 12, c.ink, true));
            ui.weight(themes, card);
        }
        page.addView(themes);
        for (int i = 0; i < MODES.length; i++) {
            String mode = MODES[i];
            boolean chosen = profile.appearance.equals(mode);
            page.addView(ui.option(MODES_LABELS[i], i == 0 ? "Följer telefonens tema" : null,
                    chosen, ui.optionIcon(MODE_ICONS[i], chosen), () -> {
                        profile.appearance = mode;
                        app.preferencesChanged(false);
                    }));
        }
        page.addView(singleSetting(setting("Färgpalett", Palette.LABELS[Palette.index(profile.accent)],
                0, () -> app.go("accent"))));
    }

    private void accent(Ui.Stack page) {
        page.addView(ui.headline("Färgpalett", 36));
        page.addView(ui.muted("Välj en palett för hela appen."));
        Ui.Stack choices = ui.column(8);
        for (int i = 0; i < Palette.IDS.length; i++) {
            String accent = Palette.IDS[i];
            choices.addView(ui.option(Palette.LABELS[i], i == 0 ? "Standard" : null,
                    accent.equals(profile.accent), sample(Palette.get(accent, app.dark())), () -> {
                        profile.accent = accent;
                        app.preferencesChanged(false);
                    }));
        }
        page.addView(choices);
        page.addView(ui.rosterPreview(profile.unit, profile.crews.getOrDefault(profile.unit, "B")));
        page.addView(ui.button("Visa i schemat", () -> app.go("calendar")));
    }

    private View sample(Palette p) {
        Ui.Stack sample = ui.row(3);
        sample.setLayoutParams(ui.size(44, 31));
        ui.surface(sample, p.canvas, 9, p.line, 1);
        ui.pad(sample, 4);
        sample.addView(ui.block(p.action, 4), ui.size(16, Ui.MATCH));
        Ui.Stack bars = ui.column(3);
        ui.stretch(bars, ui.block(p.paper, 3));
        ui.stretch(bars, ui.block(p.subtle, 3));
        sample.addView(bars, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        return sample;
    }

    private void vacation(Ui.Stack page) {
        page.addView(ui.headline("Din grupp för året", 34));
        page.addView(ui.muted("Välj en semestergrupp för varje schemaår."));
        page.addView(singleSetting(setting("Schemaår", Integer.toString(year), 0,
                () -> chooseYear("five", choice -> {
                    year = choice;
                    vacationChoice = profile.vacation.get(Schedule.yearKey("five", year));
                }))));
        Schedule.Roster roster = app.schedule.roster("five", year);
        if (roster != null) {
            for (Schedule.Vacation group : roster.vacations()) {
                page.addView(ui.option(group.label(), group.detail(), group.id().equals(vacationChoice),
                        ui.swatch(c.vacationColor(group.id())), () -> {
                            vacationChoice = group.id();
                            app.refresh();
                        }));
            }
        }
        page.addView(ui.option("Ingen semestergrupp", null, vacationChoice == null, null, () -> {
            vacationChoice = null;
            app.refresh();
        }));
        page.addView(ui.button("Spara semestergrupp", () -> {
            String key = Schedule.yearKey("five", year);
            boolean changed = !Objects.equals(profile.vacation.get(key), vacationChoice);
            profile.vacation.put(key, vacationChoice);
            app.go("prefs");
            app.preferencesChanged(changed);
        }));
    }

    private void chooseYear(String unit, IntConsumer select) {
        app.sheet("Välj schemaår", null, false, null, panel -> {
            for (int available : app.schedule.years(unit).keySet()) {
                panel.addView(ui.option(Integer.toString(available), null, year == available, null, () -> {
                    select.accept(available);
                    app.dismissSheet();
                    app.refresh();
                }));
            }
        });
    }

    private void timing(Ui.Stack page) {
        String unit = profile.unit;
        page.addView(ui.headline("Schematider", 35));
        page.addView(ui.muted("Anpassa tiderna för det valda schemaåret."));
        Ui.Stack tags = ui.row(8);
        tags.addView(ui.tag(Schedule.label(unit), null), ui.size(Ui.WRAP, Ui.WRAP));
        tags.addView(ui.tag(Integer.toString(year), () -> chooseYear(unit, choice -> {
            year = choice;
            times = app.schedule.times(profile, unit, year);
            timingError = null;
        })), ui.size(Ui.WRAP, Ui.WRAP));
        page.addView(tags);
        page.addView(singleSetting(ui.setting(TRANSITION_NAMES[0], "Första passet börjar vid bryttiden.",
                Schedule.clock(times.cutoff()), R.drawable.ic_clock, null, () -> editTime(0))));
        page.addView(ui.section("Måndag–fredag"));
        page.addView(timeTable(false));
        page.addView(ui.section("Lördag–söndag"));
        page.addView(timeTable(true));
        if (timingError != null) {
            Ui.Stack warning = ui.panel(0, c.warn, true);
            ui.pad(warning, 12);
            warning.addView(ui.text(timingError, 14, c.warnInk, false));
            page.addView(warning);
        }
        page.addView(ui.button("Spara schematider", () -> saveTiming(false)));
        page.addView(ui.button("Återställ schematider", Ui.QUIET, () -> app.confirm("Återställ schematider?",
                "Publicerade tider återställs för " + Schedule.label(unit) + " " + year + ".",
                "Avbryt", "Återställ", Ui.PRIMARY, app::dismissSheet, () -> saveTiming(true))));
    }

    private View timeTable(boolean weekend) {
        Ui.Stack panel = ui.panel(0, c.paper, true);
        if (weekend) {
            line(panel, timeRow(0, 0, 3));
            line(panel, timeRow(2, 3, 0));
        } else {
            for (int slot = 0; slot < 3; slot++) line(panel, timeRow(slot, slot, (slot + 1) % 3));
        }
        return panel;
    }

    private View timeRow(int slot, int start, int end) {
        return ui.setting(Schedule.SLOTS[slot], slot == 2 ? "Slutar när nästa schemadag börjar" : null,
                Schedule.clock(times.at(start)) + " – " + Schedule.clock(times.at(end)), 0, null, () -> editTime(end));
    }

    private void editTime(int index) {
        LocalTime[] draft = {times.at(index)};
        String detail = index == 0 ? "Bryttiden flyttar FM:s start och föregående nattpass slut."
                : "Tiden gäller både slutet på passet före och starten på nästa pass.";
        app.sheet(TRANSITION_NAMES[index], detail, false, null, panel -> {
            panel.addView(ui.timeWheel(draft[0], value -> draft[0] = value));
            panel.addView(ui.button("Använd tid", () -> {
                times = times.with(index, draft[0]);
                timingError = null;
                app.dismissSheet();
                app.refresh();
            }));
        });
    }

    private void saveTiming(boolean reset) {
        app.dismissSheet();
        if (!reset && !times.valid()) {
            timingError = "Skiftbytena måste komma i ordning efter bryttiden.";
            app.refresh();
            return;
        }
        String unit = profile.unit;
        String key = Schedule.yearKey(unit, year);
        Schedule.Times previous = app.schedule.times(profile, unit, year);
        Schedule.Times previousOverride = profile.overrides.get(key);
        if (reset) profile.overrides.remove(key);
        else profile.overrides.put(key, times);
        LocalDate last = LocalDate.of(year, 12, 31);
        for (LocalDate day = LocalDate.of(year - 1, 12, 31); !day.isAfter(last); day = day.plusDays(1)) {
            Schedule.Day entry = app.schedule.assignment(unit, day);
            if (entry == null) continue;
            for (Schedule.Shift shift : entry.shifts()) {
                Schedule.Span span = app.schedule.span(profile, shift);
                if (span.end() > span.start()) continue;
                if (previousOverride == null) profile.overrides.remove(key);
                else profile.overrides.put(key, previousOverride);
                timingError = "Tiderna ger ett ogiltigt pass " + day + ". Kontrollera bryttiden och skiftbytena.";
                app.refresh();
                return;
            }
        }
        timingError = null;
        times = app.schedule.times(profile, unit, year);
        app.preferencesChanged(!previous.equals(times));
    }

    private void export(Ui.Stack page) {
        Profile.Export out = profile.export;
        page.addView(ui.headline("Telefonens kalender", 33));
        page.addView(ui.muted("Visa valda pass i telefonens kalender Skiftschema."));
        page.addView(singleSetting(ui.setting("Kalenderexport", "Hantera kalendern Skiftschema",
                null, 0, out.enabled, () -> app.setExport(!out.enabled))));
        if (!out.enabled) {
            Ui.Stack info = ui.panel(9, c.subtle, false);
            ui.pad(info, 15);
            Ui.Stack title = ui.row(7);
            title.addView(ui.icon(out.rejected ? R.drawable.ic_alert : R.drawable.ic_calendar, c.muted), ui.size(20, 20));
            ui.weight(title, ui.headline(out.rejected ? "Kalenderåtkomst saknas" : "Exporten är av", 14));
            info.addView(title);
            info.addView(ui.text(out.rejected ? "Tillåt kalenderåtkomst i telefonens inställningar."
                    : "Slå på export för att välja vilka pass som ska visas.", 13, c.muted, false));
            page.addView(info);
            page.addView(ui.button(out.rejected ? "Öppna systeminställningar" : "Aktivera kalenderexport",
                    Ui.PRIMARY, !app.calendarBusy, 48, () -> {
                        if (out.rejected) app.systemSettings();
                        else app.setExport(true);
                    }));
            if (out.calendarId != null) page.addView(ui.button("Hantera redan exporterade händelser",
                    Ui.QUIET, this::manageEvents));
            return;
        }
        page.addView(singleSetting(ui.setting("Synk", "Uppdatera när schemat eller övertiden ändras", null,
                0, out.sync, () -> {
                    out.sync = !out.sync;
                    app.preferencesChanged(false);
                    if (out.sync) app.sync(false);
                })));
        if (out.pending) {
            Ui.Stack note = ui.panel(4, c.vac, false);
            ui.pad(note, 13);
            note.addView(ui.text("Ändringar väntar", 14, c.vacInk, true));
            note.addView(ui.text("Godkänn när du vill uppdatera kalendern.", 12, c.vacInk, false));
            page.addView(note);
        }
        page.addView(ui.section("Exportera"));
        Ui.Stack categories = ui.panel(0, c.paper, true);
        for (int i = 0; i < Schedule.CATEGORIES.length; i++) {
            int category = i;
            if (i == 2 && !"five".equals(profile.unit) && !out.categories[i]) continue;
            line(categories, ui.setting(Schedule.CATEGORY_NAMES[i], null, null, CATEGORY_ICONS[i],
                    out.categories[i], () -> {
                        if (out.categories[category]) {
                            app.refresh();
                            manageCategory(category, false);
                        } else {
                            out.categories[category] = true;
                            app.preferencesChanged(true);
                        }
                    }));
        }
        page.addView(categories);
        String reminder = "Av";
        if (out.reminder != null) {
            reminder = out.reminder + " min";
            for (int i = 0; i < REMINDERS.length; i++) {
                if (Objects.equals(REMINDER_MINUTES[i], out.reminder)) reminder = REMINDERS[i];
            }
        }
        page.addView(singleSetting(setting("Påminnelser", reminder, R.drawable.ic_bell, this::chooseReminder)));
        page.addView(ui.button(app.calendarBusy ? "Uppdaterar…" : "Uppdatera kalender", Ui.PRIMARY,
                !app.calendarBusy, 48, app::requestSync));
        if (out.calendarId != null) page.addView(ui.button("Hantera exporterade händelser",
                Ui.QUIET, this::manageEvents));
    }

    private void chooseReminder() {
        app.sheet("Påminnelser", "Gäller exporterade arbetspass och övertid.", false, null, panel -> {
            for (int i = 0; i < REMINDERS.length; i++) {
                Integer amount = REMINDER_MINUTES[i];
                boolean current = Objects.equals(profile.export.reminder, amount);
                panel.addView(ui.option(REMINDERS[i], null, current, null, () -> {
                    app.dismissSheet();
                    if (!current) {
                        profile.export.reminder = amount;
                        app.preferencesChanged(true);
                    }
                }));
            }
        });
    }

    private void manageEvents() {
        app.sheet("Hantera exporterade händelser", null, false, null, panel -> {
            for (int i = 0; i < Schedule.CATEGORY_NAMES.length; i++) {
                int category = i;
                panel.addView(ui.button(Schedule.CATEGORY_NAMES[i], Ui.SECONDARY,
                        () -> manageCategory(category, true)));
            }
        });
    }

    private void manageCategory(int category, boolean fromManage) {
        app.sheet(Schedule.CATEGORY_NAMES[category], "Välj vad som ska hända med exporterade händelser.",
                false, fromManage ? this::manageEvents : null, panel -> {
                    panel.addView(ui.button("Behåll befintliga händelser", Ui.SECONDARY, !app.calendarBusy, 48, () -> {
                        app.dismissSheet();
                        app.deleteEvents(category, PhoneCalendar.Range.KEEP);
                    }));
                    panel.addView(ui.button("Ta bort kommande händelser", Ui.QUIET, !app.calendarBusy, 48,
                            () -> confirmDelete(category, PhoneCalendar.Range.FUTURE, fromManage)));
                    panel.addView(ui.button("Ta bort alla exporterade", Ui.DANGER, !app.calendarBusy, 48,
                            () -> confirmDelete(category, PhoneCalendar.Range.ALL, fromManage)));
                });
    }

    private void confirmDelete(int category, PhoneCalendar.Range range, boolean fromManage) {
        app.confirm("Ta bort valda händelser?",
                "Exporterade händelser i " + Schedule.CATEGORY_NAMES[category].toLowerCase(Schedule.SWEDISH)
                        + " tas bort från telefonens kalender.",
                "Avbryt", "Ta bort", Ui.DANGER, () -> manageCategory(category, fromManage), () -> {
                    app.dismissSheet();
                    app.deleteEvents(category, range);
                });
    }
}
