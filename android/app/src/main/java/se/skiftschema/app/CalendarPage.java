package se.skiftschema.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CalendarPage {
    private final MainActivity app;
    LocalDate anchor;
    LocalDate selected;
    LocalDate previousAnchor;
    LocalDate previousSelected;
    boolean week;
    private LocalDate lastTap;
    private long lastTapAt;
    private LocalDate today;
    private int pickerYear;
    private Ui ui;
    private Palette c;
    private Ui.Stack hero;
    private Schedule.Work displayedWork;
    private long daysUntilWork;
    private Ui.Stack footer;
    private boolean covered;
    private final Map<LocalDate, Cell> dayViews = new LinkedHashMap<>();

    CalendarPage(MainActivity app) { this.app = app; }

    void reset() {
        anchor = null;
        selected = null;
        previousAnchor = null;
        previousSelected = null;
        lastTap = null;
        week = false;
    }

    void build(Ui.Stack screen) {
        ui = app.ui;
        c = ui.c;
        Profile profile = app.profile;
        String unit = profile.unit;
        today = app.schedule.activeDay(profile, app.now());
        if (anchor == null) anchor = today;
        LocalDate start = week ? Schedule.weekStart(anchor) : YearMonth.from(anchor).atDay(1);
        List<LocalDate> days = Schedule.cells(anchor, week);
        covered = week ? days.stream().anyMatch(day -> app.schedule.roster(unit, day.getYear()) != null)
                : app.schedule.roster(unit, start.getYear()) != null;

        screen.addView(ui.top(null, null));
        Ui.Stack body = ui.column(13);
        ui.pad(body, 18, 0, 18, 13);
        hero = ui.panel(3, c.hero, false);
        hero.setMinimumHeight(ui.dp(96));
        ui.pad(hero, 15);
        LinearLayout.LayoutParams heroParams = ui.size(Ui.MATCH, Ui.WRAP);
        heroParams.leftMargin = ui.dp(4);
        heroParams.rightMargin = ui.dp(4);
        body.addView(hero, heroParams);
        updateHero();

        Ui.Stack navigation = ui.row(4);
        ui.pad(navigation, 3, 0, 3, 0);
        Ui.Stack date = ui.row(4);
        date.setMinimumHeight(ui.dp(42));
        TextView title = ui.headline(week ? "Vecka " + start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                : Schedule.month(start) + " " + start.getYear(), 29);
        ui.oneLine(title);
        date.addView(title, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP, 1));
        date.addView(ui.icon(R.drawable.ic_down, c.muted), ui.size(15, 15));
        // Keep the chevron beside the title while letting the controls keep their full width.
        FrameLayout titleSlot = new FrameLayout(app);
        titleSlot.addView(date, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        date.setContentDescription("Gå till månad, " + title.getText());
        ui.buttonRole(date);
        ui.click(date, () -> {
            lastTap = null;
            pickerYear = start.getYear();
            monthPicker();
        });
        ui.weight(navigation, titleSlot);
        navigation.addView(ui.iconButton(R.drawable.ic_left, week ? "Föregående vecka" : "Föregående månad", true,
                () -> move(-1)));
        navigation.addView(ui.tag("Idag", c.action, c.paper, 8, () -> moveTo(week, today, null)));
        navigation.addView(ui.iconButton(R.drawable.ic_right, week ? "Nästa vecka" : "Nästa månad", true,
                () -> move(1)));
        body.addView(navigation);

        Ui.Stack mode = ui.row(4);
        Ui.Stack buttons = ui.row(0);
        ui.pad(buttons, 4);
        ui.surface(buttons, c.subtle, 12, 0, 0);
        for (boolean toWeek : new boolean[]{false, true}) {
            boolean active = toWeek == week;
            TextView tab = ui.centered(toWeek ? "Vecka" : "Månad", 12, active ? c.ink : c.muted, true);
            tab.setMinimumWidth(ui.dp(75));
            tab.setMinimumHeight(ui.dp(30));
            tab.setSelected(active);
            ui.surface(tab, active ? c.paper : c.subtle, 9, 0, 0);
            ui.buttonRole(tab);
            ui.click(tab, () -> {
                if (toWeek != week) moveTo(toWeek, selected != null ? selected : days.contains(today) ? today : anchor, selected);
            });
            buttons.addView(tab, ui.size(Ui.WRAP, Ui.WRAP));
        }
        mode.addView(buttons, ui.size(Ui.WRAP, Ui.WRAP));
        ui.space(mode);
        mode.addView(ui.tag(Schedule.label(unit) + " · " + profile.crews.get(unit), () -> app.go("prefs")),
                ui.size(Ui.WRAP, Ui.WRAP));
        body.addView(mode);

        SwipeGrid wrapper = new SwipeGrid();
        Ui.Stack grid = ui.column(5);
        wrapper.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        dayViews.clear();
        if (week) {
            grid.addView(ui.text(Schedule.dateLabel(start) + " – " + Schedule.dateLabel(start.plusDays(6))
                    + " " + start.getYear(), 12, c.muted, false));
            for (LocalDate day : days) grid.addView(day(day));
        } else {
            Ui.Stack weekdays = ui.row(3);
            for (String name : Schedule.WEEKDAYS) ui.weight(weekdays, ui.centered(name, 11, c.muted, true));
            grid.addView(weekdays);
            for (int rowIndex = 0; rowIndex < days.size(); rowIndex += 7) {
                Ui.Stack row = ui.row(3);
                for (LocalDate day : days.subList(rowIndex, rowIndex + 7)) {
                    row.addView(day(day), new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
                }
                grid.addView(row);
            }
        }
        if (!covered) {
            Ui.Stack empty = ui.panel(8, c.paper, true);
            empty.setGravity(Gravity.CENTER);
            ui.pad(empty, 18);
            empty.addView(ui.icon(R.drawable.ic_calendar, c.muted), ui.size(24, 24));
            empty.addView(ui.centered("Inget schema tillgängligt", 17, c.ink, true));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
            params.leftMargin = ui.dp(12);
            params.rightMargin = ui.dp(12);
            wrapper.addView(empty, params);
        }
        body.addView(wrapper);
        footer = ui.column(0);
        body.addView(footer);
        updateFooter();
        ScrollView scroll = ui.scroll(body);
        app.pageScroll = scroll;
        ui.stretch(screen, scroll);
    }

    void clockChanged() {
        if (hero == null || !app.route.equals("calendar") || app.profile.unit == null) return;
        if (!app.schedule.activeDay(app.profile, app.now()).equals(today)) app.refresh();
        else updateHero();
    }

    private void updateHero() {
        long now = app.now();
        Schedule.Work work = app.schedule.next(app.profile, now);
        long diff = work == null ? 0 : ChronoUnit.DAYS.between(Schedule.civilDate(now),
                Schedule.civilDate(work.span().start()));
        if (hero.getChildCount() != 0 && diff == daysUntilWork && Objects.equals(displayedWork, work)) return;
        displayedWork = work;
        daysUntilWork = diff;
        hero.removeAllViews();
        Ui.Stack header = ui.row(4);
        ui.weight(header, ui.text("Nästa pass", 12, c.heroMuted, true));
        if (work != null) {
            String when = diff == 0 ? "Idag" : diff == 1 ? "Imorgon" : "Om " + diff + " dagar";
            header.addView(ui.tag(when, c.heroBadgeInk, c.heroBadge, 12, null), ui.size(Ui.WRAP, Ui.WRAP));
        }
        hero.addView(header);
        Ui.Stack main = ui.row(6);
        String label = work == null ? "–" : Schedule.dateLabel(Schedule.civilDate(work.span().start()))
                + ", " + Schedule.clock(work.span().start());
        ui.weight(main, ui.text(label, 25, c.heroMain, true));
        main.addView(ui.icon(R.drawable.ic_clock, c.heroMuted), ui.size(20, 20));
        hero.addView(main);
        if (work != null) hero.addView(ui.text((work.overtime() ? "Övertid" : work.shift().title())
                + " · Skift " + work.shift().crew(), 11, c.heroMuted, false));
    }

    private void updateFooter() {
        footer.removeAllViews();
        if (selected != null) {
            Ui.Stack selection = ui.row(4);
            ui.surface(selection, c.mine, 17, c.line, 1);
            ui.pad(selection, 12, 8, 8, 8);
            ui.weight(selection, ui.text("Vald dag · " + Schedule.dateLabel(selected), 13, c.mineInk, true));
            selection.addView(ui.button("Visa pass", Ui.SECONDARY, true, 40, () -> openDay(selected)), ui.size(Ui.WRAP, Ui.WRAP));
            footer.addView(selection);
        } else if (covered) {
            Ui.Stack legend = ui.row(13);
            ui.pad(legend, 3, 0, 3, 0);
            legend.setMinimumHeight(ui.dp(24));
            legend.addView(legend("Ditt pass", c.mine, c.mineInk));
            if ("five".equals(app.profile.unit)) legend.addView(legend("Semester", c.vac, c.vacInk));
            legend.addView(legend("Övertid", c.ot, c.otInk));
            footer.addView(legend);
        }
    }

    private View legend(String label, int fill, int edge) {
        Ui.Stack item = ui.row(5);
        View swatch = ui.block(fill, 4);
        ui.surface(swatch, fill, 4, edge, 1);
        item.addView(swatch, ui.size(12, 12));
        item.addView(ui.text(label, 10, c.muted, true), ui.size(Ui.WRAP, Ui.WRAP));
        return item;
    }

    private record Cell(View view, int fill, boolean active, boolean vacation) {}

    private void tint(LocalDate day) {
        Cell cell = dayViews.get(day);
        if (cell == null) return;
        boolean chosen = day.equals(selected);
        int color = (chosen || cell.active) && !cell.vacation ? c.mine : cell.fill;
        ui.surface(cell.view, color, week ? 12 : 8, chosen || cell.active ? c.action : c.line,
                chosen ? 2 : cell.active ? 1.3f : 1);
        cell.view.setSelected(chosen);
    }

    private View day(LocalDate day) {
        Profile profile = app.profile;
        Schedule.Day entry = app.schedule.assignment(profile.unit, day);
        Schedule.Vacation vacation = app.schedule.vacation(profile, profile.unit, day);
        List<Schedule.Shift> shifts = entry == null ? Collections.emptyList() : entry.shifts();
        boolean active = day.equals(today);
        boolean included = week || day.getMonthValue() == anchor.getMonthValue();
        boolean overtime = shifts.stream().anyMatch(shift -> profile.overtime.containsKey(shift.key()));
        int fill = vacation != null ? c.vac : !included ? c.subtle : Schedule.weekend(day) ? c.weekend : c.paper;
        Ui.Stack cell = week ? ui.row(6) : ui.column(2);
        ui.pad(cell, week ? 6 : 3);
        cell.setMinimumHeight(ui.dp(week ? 58 : 73));
        Ui.Stack label = week ? ui.column(0) : ui.row(0);
        label.setGravity(week ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        if (week) label.addView(ui.centered(Schedule.weekday(day), 10, c.muted, true));
        TextView number = ui.centered(Integer.toString(day.getDayOfMonth()), week ? 14 : 11,
                active ? c.onAction : included ? c.ink : c.quiet, true);
        number.setMinimumWidth(ui.dp(17));
        number.setMinimumHeight(ui.dp(17));
        if (active) ui.surface(number, c.action, 10, 0, 0);
        label.addView(number, ui.size(Ui.WRAP, Ui.WRAP));
        if (!week) ui.space(label);
        if (vacation != null) {
            TextView vacationTag = ui.centered("S", 8, 0xFF0B1812, true);
            ui.pad(vacationTag, 3, 0, 3, 0);
            ui.surface(vacationTag, c.vacationColor(vacation.id()), 3, 0, 0);
            label.addView(vacationTag, ui.size(Ui.WRAP, Ui.WRAP));
        } else if (!week && overtime) {
            label.addView(ui.text("ÖT", 8, c.otInk, true), ui.size(Ui.WRAP, Ui.WRAP));
        }
        cell.addView(label, ui.size(week ? 36 : Ui.MATCH, Ui.WRAP));
        if (entry != null && entry.closure() != null) {
            TextView closure = ui.centered(vacation != null ? "Semester" : entry.closure(),
                    week ? 12 : 8, vacation != null ? c.vacInk : c.muted, true);
            closure.setMaxLines(2);
            if (week) ui.weight(cell, closure);
            else ui.stretch(cell, closure);
        } else if (week) {
            for (int slot = 0; slot < Schedule.SLOTS.length; slot++) {
                Schedule.Shift shift = null;
                for (Schedule.Shift ref : shifts) if (ref.slot() == slot) shift = ref;
                ui.weight(cell, shift == null ? ui.centered("–", 11, c.quiet, false) : shift(shift, vacation));
            }
        } else {
            for (Schedule.Shift shift : shifts) cell.addView(shift(shift, vacation));
        }
        StringBuilder description = new StringBuilder(Schedule.dateLabel(day));
        if (vacation != null) description.append(", Semester");
        else if (entry != null && entry.closure() != null) description.append(", ").append(entry.closure());
        for (Schedule.Shift shift : shifts) {
            description.append(", ").append(Schedule.SLOTS[shift.slot()]).append(" skift ").append(shift.crew());
        }
        if (overtime) description.append(", Övertid");
        cell.setContentDescription(description);
        ui.buttonRole(cell);
        ui.click(cell, () -> tap(day));
        cell.setOnLongClickListener(view -> {
            openDay(day);
            return true;
        });
        dayViews.put(day, new Cell(cell, fill, active, vacation != null));
        tint(day);
        return cell;
    }

    private View shift(Schedule.Shift shift, Schedule.Vacation vacation) {
        Schedule.Work extra = app.profile.overtime.get(shift.key());
        boolean mine = vacation == null && app.profile.owns(shift);
        int fill = extra != null ? c.ot : mine ? c.mine : week ? c.subtle : Color.TRANSPARENT;
        int color = extra != null ? c.otInk : mine ? c.mineInk : c.ink;
        Ui.Stack slot = ui.column(0);
        ui.surface(slot, fill, week ? 7 : 4, 0, 0);
        ui.pad(slot, week ? 6 : 2, week ? 4 : 0, week ? 4 : 2, week ? 4 : 0);
        slot.setMinimumHeight(ui.dp(week ? 44 : 14));
        Ui.Stack row = ui.row(week ? 5 : 2);
        row.addView(ui.text(Schedule.SLOTS[shift.slot()], week ? 12 : 8,
                extra != null || mine ? color : c.muted, true), ui.size(Ui.WRAP, Ui.WRAP));
        if (!week) ui.space(row);
        row.addView(ui.text(shift.crew(), week ? 13 : 10, color, true), ui.size(Ui.WRAP, Ui.WRAP));
        if (week && extra != null) row.addView(ui.text("ÖT", 9, c.otInk, true), ui.size(Ui.WRAP, Ui.WRAP));
        slot.addView(row);
        if (week) {
            long start = extra != null ? extra.span().start() : app.schedule.span(app.profile, shift).start();
            slot.addView(ui.mono(Schedule.clock(start), 10, c.muted));
        }
        return slot;
    }

    private void tap(LocalDate day) {
        if (!week) {
            long time = SystemClock.uptimeMillis();
            if (day.equals(lastTap) && time - lastTapAt <= 350) {
                moveTo(true, day, day);
                return;
            }
            lastTap = day;
            lastTapAt = time;
        }
        select(day);
    }

    private void select(LocalDate day) {
        LocalDate old = selected;
        selected = day;
        tint(old);
        tint(selected);
        updateFooter();
    }

    private void openDay(LocalDate day) {
        lastTap = null;
        select(day);
        new OvertimeSheet(app, day).showDay();
    }

    private void move(int direction) {
        lastTap = null;
        anchor = week ? anchor.plusWeeks(direction) : anchor.plusMonths(direction);
        selected = null;
        app.refresh();
    }

    private void moveTo(boolean showWeek, LocalDate day, LocalDate selection) {
        lastTap = null;
        if (showWeek && !week) {
            previousAnchor = anchor;
            previousSelected = selected;
        }
        week = showWeek;
        anchor = day;
        selected = selection;
        app.refresh();
    }

    void back() {
        moveTo(false, selected != null ? selected : previousAnchor, selected != null ? selected : previousSelected);
    }

    private void monthPicker() {
        app.sheet("Gå till månad", null, false, null, panel -> {
            Ui.Stack header = ui.row(6);
            header.addView(ui.iconButton(R.drawable.ic_left, "Föregående år", true, () -> {
                pickerYear = Math.max(1900, pickerYear - 1);
                monthPicker();
            }));
            ui.weight(header, ui.centered(Integer.toString(pickerYear), 20, c.ink, true));
            header.addView(ui.iconButton(R.drawable.ic_right, "Nästa år", true, () -> {
                pickerYear = Math.min(9999, pickerYear + 1);
                monthPicker();
            }));
            panel.addView(header);
            for (int row = 0; row < 3; row++) {
                Ui.Stack buttons = ui.row(6);
                for (int column = 0; column < 4; column++) {
                    int month = row * 4 + column + 1;
                    TextView button = ui.button(Schedule.MONTHS[month - 1].substring(0, 3), Ui.SECONDARY,
                            true, 48, () -> {
                        app.dismissSheet();
                        moveTo(false, LocalDate.of(pickerYear, month, 1), null);
                    });
                    ui.pad(button, 4, 8, 4, 8);
                    ui.weight(buttons, button);
                }
                panel.addView(buttons);
            }
        });
    }

    private final class SwipeGrid extends FrameLayout {
        private float x, y;
        private boolean multiple;

        SwipeGrid() { super(app); }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    x = event.getX();
                    y = event.getY();
                    multiple = false;
                }
                case MotionEvent.ACTION_POINTER_DOWN -> {
                    multiple = true;
                    lastTap = null;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float horizontal = Math.abs(event.getX() - x);
                    if (horizontal > ui.dp(22) && horizontal > Math.abs(event.getY() - y) * 1.5f) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
                default -> { }
            }
            return false;
        }

        // Day cells handle clicks. The arrow buttons expose the same navigation as this swipe.
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() > 1) {
                multiple = true;
                lastTap = null;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !multiple) {
                float horizontal = event.getX() - x;
                if (Math.abs(horizontal) > ui.dp(44) && Math.abs(horizontal) > Math.abs(event.getY() - y)) {
                    move(horizontal < 0 ? 1 : -1);
                }
            }
            return true;
        }
    }
}
