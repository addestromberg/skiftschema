package se.skiftschema.app;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

final class OvertimeSheet {
    private final MainActivity app;
    private final LocalDate day;
    private final String unit;
    private final Schedule.Day assignment;
    private final Ui ui;
    private final Palette c;
    private Schedule.Shift shift;
    private Schedule.Span official;
    private boolean partial;
    private OvertimeTime start;
    private OvertimeTime end;

    OvertimeSheet(MainActivity app, LocalDate day) {
        this.app = app;
        this.day = day;
        unit = app.profile.unit;
        assignment = app.schedule.assignment(unit, day);
        ui = app.ui;
        c = ui.c;
    }

    void showDay() {
        List<Schedule.Shift> shifts = assignment == null ? Collections.emptyList() : assignment.shifts();
        Schedule.Vacation vacation = app.schedule.vacation(app.profile, unit, day);
        String subtitle = Schedule.label(unit) + (shifts.isEmpty() ? "" : " · " + ui.copy(UiCopy.PICK_OVERTIME));
        app.sheet(Schedule.dateLabel(day), subtitle, false, null, panel -> {
            if (vacation != null) {
                Ui.Stack note = ui.panel(0, c.vac, false);
                ui.pad(note, 11);
                note.addView(ui.text("Semester", 14, c.vacInk, true));
                panel.addView(note);
            }
            for (Schedule.Shift shift : shifts) panel.addView(shiftRow(shift));
            if (vacation == null && shifts.isEmpty()) {
                String label = assignment != null && assignment.closure() != null ? assignment.closure()
                        : app.schedule.roster(unit, day.getYear()) != null ? "Inga pass" : ui.copy(UiCopy.NO_SCHEDULE);
                panel.addView(ui.headline(label, 14));
            }
            note(panel);
        });
    }

    private View shiftRow(Schedule.Shift item) {
        Schedule.Work extra = app.profile.overtime.get(item.key());
        boolean own = app.profile.owns(item);
        boolean unavailable = own && extra == null;
        Ui.Stack row = ui.row(11);
        ui.pad(row, 12);
        row.setMinimumHeight(ui.dp(64));
        ui.surface(row, extra != null ? c.ot : own ? c.mine : c.paper, 14, c.line, 1);
        TextView slot = ui.centered(Schedule.SLOTS[item.slot()], 12, extra != null ? c.otInk : c.muted, true);
        ui.surface(slot, extra != null ? c.paper : c.subtle, 8, 0, 0);
        row.addView(slot, ui.size(36, 34));
        String title = item.title() + " · Skiftlag " + item.crew();
        String time = app.schedule.span(app.profile, item).label();
        Ui.Stack labels = ui.column(2);
        labels.addView(ui.headline(title, 14));
        labels.addView(ui.text(time, 12, c.muted, false));
        if (own) labels.addView(ui.text(ui.copy(UiCopy.YOUR_CREW), 12, c.mineInk, true));
        if (extra != null) labels.addView(ui.text(ui.copy(UiCopy.OVERTIME) + " · " + extra.span().label(),
                12, c.otInk, true));
        ui.weight(row, labels);
        if (!unavailable) {
            row.addView(ui.icon(R.drawable.ic_right, c.muted), ui.size(17, 17));
            ui.click(row, () -> choose(item));
        }
        row.setEnabled(!unavailable);
        String action = unavailable ? ui.copy(UiCopy.YOUR_CREW)
                : extra != null ? ui.copy(UiCopy.MANAGE_OVERTIME) : "Välj";
        row.setContentDescription(action + ", " + title + ", " + time);
        ui.buttonRole(row);
        return row;
    }

    private void choose(Schedule.Shift item) {
        shift = item;
        Schedule.Work extra = existing();
        official = app.schedule.span(app.profile, shift);
        partial = extra != null && !extra.span().equals(official);
        start = OvertimeTime.from(extra == null ? official.start() : extra.span().start());
        end = OvertimeTime.from(extra == null ? official.end() : extra.span().end());
        if (extra == null && !app.profile.owns(item)) edit();
        else menu();
    }

    private Schedule.Work existing() { return app.profile.overtime.get(shift.key()); }

    private String title() {
        return shift.title() + " · Skiftlag " + shift.crew() + " · " + official.label();
    }

    private void note(Ui.Stack panel) {
        if (assignment != null && assignment.note() != null) {
            panel.addView(ui.text("Schemaanteckning · " + assignment.note(), 12, c.muted, false));
        }
    }

    private void menu() {
        Schedule.Work extra = existing();
        app.sheet(Schedule.dateLabel(day), title(), false, this::showDay, panel -> {
            Ui.Stack saved = ui.panel(0, extra == null ? c.subtle : c.ot, false);
            ui.pad(saved, 12);
            saved.addView(ui.text(extra == null ? "Ordinarie skiftlag " + shift.crew()
                    : ui.copy(UiCopy.LOGGED_OVERTIME) + " · " + extra.span().label(),
                    14, extra == null ? c.ink : c.otInk, true));
            panel.addView(saved);
            note(panel);
            if (!app.profile.owns(shift)) {
                panel.addView(ui.button(ui.copy(extra == null ? UiCopy.ADD_OVERTIME : UiCopy.EDIT_OVERTIME),
                        Ui.SECONDARY, this::edit));
            }
            if (extra != null) {
                panel.addView(ui.divider());
                String removeLabel = ui.copy(UiCopy.REMOVE_OVERTIME);
                panel.addView(ui.button(removeLabel, Ui.QUIET, () -> app.confirm(removeLabel + "?",
                        extra.span().label(), "Avbryt", "Ta bort", Ui.DANGER, this::menu, () -> {
                            app.profile.overtime.remove(shift.key());
                            updated();
                        })));
            }
        });
    }

    private void edit() {
        String heading = ui.copy(existing() == null ? UiCopy.ADD_OVERTIME : UiCopy.EDIT_OVERTIME);
        app.sheet(heading, Schedule.dateLabel(day) + " · " + title(),
                false, null, panel -> {
                    Ui.Stack mode = ui.row(7);
                    ui.weight(mode, ui.button("Hela passet", partial ? Ui.SECONDARY : Ui.PRIMARY, () -> {
                        if (!partial) return;
                        partial = false;
                        edit();
                    }));
                    ui.weight(mode, ui.button("Del av passet", partial ? Ui.PRIMARY : Ui.SECONDARY, () -> {
                        if (partial) return;
                        partial = true;
                        edit();
                    }));
                    panel.addView(mode);
                    Ui.Stack status = ui.row(7);
                    ui.pad(status, 12);
                    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
                    TextView save = ui.button(ui.copy(UiCopy.SAVE_OVERTIME), Ui.PRIMARY, valid(), 48, this::commit);
                    Runnable refreshStatus = () -> status(status, save);
                    if (partial) {
                        int width = app.getResources().getDisplayMetrics().widthPixels;
                        boolean sideBySide = width >= ui.dp(332);
                        Ui.Stack pickers = sideBySide ? ui.row(12) : ui.column(12);
                        if (sideBySide) pickers.setGravity(Gravity.TOP);
                        View from = timePicker(true, refreshStatus);
                        View until = timePicker(false, refreshStatus);
                        if (sideBySide) {
                            ui.weight(pickers, from);
                            ui.weight(pickers, until);
                        } else {
                            pickers.addView(from);
                            pickers.addView(until);
                        }
                        panel.addView(pickers);
                    } else {
                        Ui.Stack span = ui.panel(0, c.subtle, false);
                        ui.pad(span, 12);
                        span.addView(ui.headline(official.label(), 14));
                        panel.addView(span);
                    }
                    refreshStatus.run();
                    panel.addView(status);
                    panel.addView(save);
                });
    }

    private View timePicker(boolean isStart, Runnable refreshStatus) {
        String label = isStart ? "Från" : "Till";
        Ui.Stack picker = ui.column(7);
        picker.addView(ui.headline(label, 14));
        TextView date = ui.text("", 12, c.muted, false);
        picker.addView(date);
        TextView occurrence = ui.centered("", 13, c.ink, true);
        ui.pad(occurrence, 8);
        occurrence.setMinimumHeight(ui.dp(48));
        ui.surface(occurrence, c.paper, 15, c.edge, 1);
        ui.buttonRole(occurrence);
        Runnable refreshPicker = () -> {
            OvertimeTime value = isStart ? start : end;
            date.setText(Schedule.dateLabel(value.wall().toLocalDate()));
            occurrence.setVisibility(value.isRepeated() ? View.VISIBLE : View.GONE);
            if (value.isRepeated()) {
                occurrence.setText(value.occurrenceLabel());
                occurrence.setContentDescription(label + ", " + value.occurrenceLabel()
                        + ". Byt till " + value.otherOccurrence().occurrenceLabel());
            }
        };
        ui.click(occurrence, () -> {
            if (!(isStart ? start : end).isRepeated()) return;
            OvertimeTime other = (isStart ? start : end).otherOccurrence();
            if (isStart) start = other;
            else end = other;
            refreshPicker.run();
            refreshStatus.run();
        });
        picker.addView(ui.timeWheel((isStart ? start : end).wall().toLocalTime(), time -> {
            OvertimeTime selected = (isStart ? start : end).withClock(time, official, isStart);
            if (isStart) start = selected;
            else end = selected;
            refreshPicker.run();
            refreshStatus.run();
        }));
        picker.addView(occurrence);
        refreshPicker.run();
        return picker;
    }

    private Schedule.Span chosenSpan() {
        return partial ? new Schedule.Span(start.instant(), end.instant()) : official;
    }

    private boolean hasGap() { return partial && (start.isGap() || end.isGap()); }

    private boolean valid() {
        if (hasGap()) return false;
        Schedule.Span picked = chosenSpan();
        return picked.start() < picked.end() && picked.start() >= official.start() && picked.end() <= official.end();
    }

    private void status(Ui.Stack row, View save) {
        boolean valid = valid();
        int color = valid ? c.otInk : c.warnInk;
        ui.surface(row, valid ? c.ot : c.warn, 17, 0, 0);
        row.removeAllViews();
        row.addView(ui.icon(valid ? R.drawable.ic_clock : R.drawable.ic_alert, color), ui.size(18, 18));
        NumberFormat format = NumberFormat.getNumberInstance(Schedule.SWEDISH);
        format.setMaximumFractionDigits(2);
        String text;
        if (valid) {
            Schedule.Span span = chosenSpan();
            text = format.format((span.end() - span.start()) / 3_600_000d) + " " + ui.copy(UiCopy.OVERTIME_HOURS);
        } else if (hasGap()) {
            text = ui.warning("Den valda tiden finns inte i Stockholm när klockan ställs fram. Välj en annan tid.");
        } else {
            text = ui.warning("Välj en sluttid efter starttiden, inom passets gränser.");
        }
        ui.weight(row, ui.text(text, 13, color, true));
        ui.enabled(save, valid);
    }

    private void commit() {
        if (!valid() || app.profile.owns(shift)) return;
        Schedule.Work work = new Schedule.Work(shift, chosenSpan(), true);
        Schedule.Work conflict = app.schedule.conflict(app.profile, work);
        if (conflict == null) save(work);
        else app.confirm(ui.warning("Passet överlappar"),
                "Ett annat pass överlappar " + Schedule.dateLabel(Schedule.civilDate(conflict.span().start()))
                        + " kl. " + conflict.span().label() + ".",
                "Gå tillbaka", "Spara ändå", Ui.PRIMARY, this::edit, () -> save(work));
    }

    private void save(Schedule.Work work) {
        app.profile.overtime.put(shift.key(), work);
        updated();
    }

    private void updated() {
        app.overtimeChanged();
        showDay();
    }
}
