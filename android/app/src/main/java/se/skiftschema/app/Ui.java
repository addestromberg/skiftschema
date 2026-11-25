package se.skiftschema.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.Locale;
import java.util.function.Consumer;

final class Ui {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int PRIMARY = 0, SECONDARY = 1, QUIET = 2, DANGER = 3;
    final MainActivity activity;
    final Palette c;
    private final boolean unhinged;

    Ui(MainActivity activity, Palette palette) {
        this.activity = activity;
        c = palette;
        unhinged = activity.profile != null && activity.profile.unhinged;
    }

    String copy(UiCopy label) {
        return label.text(unhinged);
    }

    String warning(String message) {
        return unhinged ? "ASCHTUNG! " + message : message;
    }

    int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    int sp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                activity.getResources().getDisplayMetrics()));
    }

    final class Stack extends LinearLayout {
        private final int gap;

        Stack(boolean horizontal, int gap) {
            super(activity);
            this.gap = gap;
            setOrientation(horizontal ? HORIZONTAL : VERTICAL);
            if (horizontal) {
                setGravity(Gravity.CENTER_VERTICAL);
                setBaselineAligned(false);
            }
        }

        @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
            if (getChildCount() > 0 && index != 0 && gap != 0) {
                if (getOrientation() == HORIZONTAL) lp.leftMargin += dp(gap);
                else lp.topMargin += dp(gap);
            }
            super.addView(child, index, lp);
        }
    }

    Stack row(int gap) { return new Stack(true, gap); }
    Stack column(int gap) { return new Stack(false, gap); }

    LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    void weight(LinearLayout row, View child) {
        row.addView(child, new LinearLayout.LayoutParams(0, WRAP, 1));
    }

    void space(LinearLayout row) {
        row.addView(new View(activity), new LinearLayout.LayoutParams(0, 0, 1));
    }

    void stretch(LinearLayout column, View child) {
        column.addView(child, new LinearLayout.LayoutParams(MATCH, 0, 1));
    }

    void pad(View view, int all) { pad(view, all, all, all, all); }

    void pad(View view, int left, int top, int right, int bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    GradientDrawable fill(int color, int radius, int edge, float stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        if (stroke > 0) result.setStroke(Math.max(1, dp(stroke)), edge);
        return result;
    }

    void surface(View view, int color, int radius, int edge, float stroke) {
        view.setBackground(fill(color, radius, edge, stroke));
    }

    View block(int color, int radius) {
        View view = new View(activity);
        surface(view, color, radius, 0, 0);
        return view;
    }

    Stack panel(int gap, int color, boolean outlined) {
        Stack panel = column(gap);
        surface(panel, color, 17, c.line, outlined ? 1 : 0);
        return panel;
    }

    void click(View view, Runnable action) {
        view.setOnClickListener(v -> action.run());
        view.setFocusable(true);
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(0x2208735B), null,
                fill(Color.WHITE, 12, 0, 0)));
    }

    void buttonRole(View view) { role(view, "android.widget.Button", false); }

    void role(View view, String name, boolean checked) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                super.onInitializeAccessibilityNodeInfo(host, node);
                node.setClassName(name);
                if ("android.widget.RadioButton".equals(name)) {
                    node.setCheckable(true);
                    node.setChecked(checked);
                }
            }
        });
    }

    TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        text.setMinHeight(sp((float) Math.ceil(size * 1.32)));
        if (Build.VERSION.SDK_INT >= 28) text.setLineHeight(sp((float) Math.ceil(size * 1.32)));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (size >= 26) text.setLetterSpacing(-0.04f);
        return text;
    }

    TextView text(CharSequence value) { return text(value, 14, c.ink, false); }
    TextView headline(String value, int size) { return text(value, size, c.ink, true); }
    TextView muted(String value) { return text(value, 14, c.muted, false); }
    TextView section(String value) { return text(value, 13, c.muted, true); }

    TextView centered(CharSequence value, int size, int color, boolean bold) {
        TextView text = text(value, size, color, bold);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    void oneLine(TextView text) {
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
    }

    TextView mono(String value, int size, int color) {
        TextView view = text(value, size, color, false);
        view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    ImageView icon(int res, int color) {
        ImageView view = new ImageView(activity);
        view.setImageResource(res);
        view.setImageTintList(ColorStateList.valueOf(color));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    FrameLayout iconTile(int res, int size, int imageSize, int color, int background,
                         int radius, boolean outlined) {
        FrameLayout tile = new FrameLayout(activity);
        tile.setLayoutParams(size(size, size));
        surface(tile, background, radius, c.line, outlined ? 1 : 0);
        tile.addView(icon(res, color), new FrameLayout.LayoutParams(dp(imageSize), dp(imageSize), Gravity.CENTER));
        return tile;
    }

    View iconButton(int res, String label, boolean small, Runnable action) {
        int size = small ? 36 : 43;
        FrameLayout button = iconTile(res, size, small ? 17 : 20, c.ink, c.paper, 12, true);
        button.setContentDescription(label);
        buttonRole(button);
        click(button, action);
        return button;
    }

    View button(String label, Runnable action) { return button(label, PRIMARY, true, 48, action); }
    View button(String label, int kind, Runnable action) { return button(label, kind, true, 48, action); }

    TextView button(String label, int kind, boolean enabled, int height, Runnable action) {
        int background = switch (kind) {
            case PRIMARY -> c.action;
            case SECONDARY -> c.paper;
            case DANGER -> c.warnInk;
            default -> Color.TRANSPARENT;
        };
        int color = switch (kind) {
            case PRIMARY -> c.onAction;
            case SECONDARY -> c.ink;
            case DANGER -> c.canvas;
            default -> c.muted;
        };
        TextView view = centered(label, 14, color, true);
        pad(view, 12, 8, 12, 8);
        view.setMinimumHeight(dp(height));
        surface(view, background, 15, c.edge, kind == SECONDARY ? 1 : 0);
        click(view, action);
        buttonRole(view);
        enabled(view, enabled);
        return view;
    }

    void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : .5f);
    }

    View tag(String label, Runnable action) { return tag(label, c.muted, c.subtle, 12, action); }

    TextView tag(String label, int color, int fill, int padding, Runnable action) {
        TextView view = centered(label, 11, color, true);
        pad(view, padding, 3, padding, 3);
        view.setMinimumHeight(dp(29));
        surface(view, fill, 15, 0, 0);
        if (action != null) {
            buttonRole(view);
            click(view, action);
        }
        return view;
    }

    View top(String title, Runnable back) {
        Stack row = row(13);
        pad(row, 24, 12, 24, 12);
        if (back == null) row.addView(iconTile(R.drawable.ic_calendar, 35, 21, c.onAction, c.action, 10, false));
        else row.addView(iconButton(R.drawable.ic_back, "Tillbaka", false, back));
        row.addView(headline(title == null ? copy(UiCopy.APP_NAME) : title, 22));
        return row;
    }

    View divider() {
        View line = new View(activity);
        line.setBackgroundColor(c.line);
        line.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 1));
        return line;
    }

    View dock() {
        Stack wrapper = column(0);
        wrapper.setBackgroundColor(c.paper);
        wrapper.addView(divider());
        Stack tabs = row(16);
        tabs.setGravity(Gravity.CENTER);
        pad(tabs, 0, 7, 0, 7);
        String[] routes = {"calendar", "prefs"};
        String[] names = {copy(UiCopy.CALENDAR), "Preferenser"};
        int[] icons = {R.drawable.ic_calendar, R.drawable.ic_settings};
        for (int i = 0; i < routes.length; i++) {
            String route = routes[i];
            boolean active = route.equals(activity.route);
            Stack tab = row(8);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumWidth(dp(132));
            tab.setMinimumHeight(dp(44));
            surface(tab, active ? c.mine : c.paper, 13, 0, 0);
            int color = active ? c.mineInk : c.muted;
            tab.addView(icon(icons[i], color), size(20, 20));
            tab.addView(text(names[i], 13, color, true), size(WRAP, WRAP));
            tab.setSelected(active);
            buttonRole(tab);
            click(tab, () -> activity.go(route));
            tabs.addView(tab, size(WRAP, WRAP));
        }
        wrapper.addView(tabs);
        return wrapper;
    }

    ScrollView scroll(Stack body) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
        return scroll;
    }

    View option(String title, String detail, boolean chosen, View leading, Runnable action) {
        Stack row = row(13);
        row.setMinimumHeight(dp(62));
        pad(row, 14, 11, 14, 11);
        surface(row, chosen ? c.mine : c.paper, 16, chosen ? c.action : c.line, chosen ? 1.5f : 1);
        if (leading != null) row.addView(leading);
        Stack labels = column(3);
        labels.addView(headline(title, 15));
        if (detail != null) labels.addView(text(detail, 12, c.muted, false));
        weight(row, labels);
        FrameLayout indicator = new FrameLayout(activity);
        surface(indicator, Color.TRANSPARENT, 9, chosen ? c.action : c.edge, 1.5f);
        if (chosen) indicator.addView(block(c.action, 5),
                new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
        row.addView(indicator, size(18, 18));
        row.setContentDescription(detail == null ? title : title + ", " + detail);
        role(row, "android.widget.RadioButton", chosen);
        click(row, action);
        return row;
    }

    View optionIcon(int res, boolean chosen) {
        ImageView view = icon(res, chosen ? c.mineInk : c.muted);
        view.setLayoutParams(size(20, 20));
        return view;
    }

    View swatch(int color) {
        View view = block(color, 9);
        view.setLayoutParams(size(29, 29));
        return view;
    }

    View setting(String title, String detail, String value, int res, Boolean toggled, Runnable action) {
        Stack row = row(11);
        row.setMinimumHeight(dp(55));
        pad(row, 12, 8, 12, 8);
        if (res != 0) row.addView(iconTile(res, 33, 18, c.muted, c.subtle, 10, false));
        Stack labels = column(2);
        labels.addView(headline(title, 14));
        if (detail != null) labels.addView(text(detail, 11, c.muted, false));
        weight(row, labels);
        if (value != null) row.addView(text(value, 12, c.muted, true), size(WRAP, WRAP));
        if (toggled != null) {
            Switch control = new Switch(activity);
            control.setContentDescription(title);
            control.setChecked(toggled);
            control.setThumbTintList(new ColorStateList(new int[][]{
                    new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{c.onAction, c.paper}));
            control.setTrackTintList(new ColorStateList(new int[][]{
                    new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{c.action, c.edge}));
            control.setOnCheckedChangeListener((button, checked) -> action.run());
            row.addView(control, size(WRAP, WRAP));
        } else if (action != null) {
            row.addView(icon(R.drawable.ic_right, c.muted), size(15, 15));
            buttonRole(row);
            row.setContentDescription(value == null ? title : title + ", " + value);
            click(row, action);
        }
        return row;
    }

    View crewGrid(String unit, String selected, Consumer<String> choose) {
        String teams = Schedule.teams(unit);
        Stack grid = column(8);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            Stack row = row(8);
            for (int i = rowIndex * 3; i < (rowIndex + 1) * 3; i++) {
                if (i >= teams.length()) {
                    space(row);
                    continue;
                }
                String crew = teams.substring(i, i + 1);
                boolean active = crew.equals(selected);
                TextView view = centered(crew, 25, active ? c.mineInk : c.ink, true);
                surface(view, active ? c.mine : c.paper, 14, active ? c.action : c.line, active ? 1.5f : 1);
                view.setContentDescription("Skiftlag " + crew);
                role(view, "android.widget.RadioButton", active);
                click(view, () -> choose.accept(crew));
                row.addView(view, new LinearLayout.LayoutParams(0, dp(61), 1));
            }
            grid.addView(row);
        }
        return grid;
    }

    View rosterPreview(String unit, String crew) {
        Stack box = panel(11, c.paper, true);
        pad(box, 14);
        Stack heading = row(5);
        weight(heading, headline("Så visas ditt pass", 14));
        heading.addView(text(Schedule.label(unit), 11, c.muted, false), size(WRAP, WRAP));
        box.addView(heading);
        String others = Schedule.teams(unit).replace(crew, "");
        String[] crews = {others.substring(0, 1), crew, others.substring(1, 2)};
        String[] hours = {"06:00", "five".equals(unit) ? "14:00" : "13:00",
                "five".equals(unit) ? "22:00" : "21:00"};
        Stack row = row(7);
        for (int i = 0; i < crews.length; i++) {
            Stack cell = column(0);
            surface(cell, i == 1 ? c.mine : c.subtle, 10, 0, 0);
            pad(cell, 8);
            cell.addView(headline(Schedule.SLOTS[i], 11));
            cell.addView(text(crews[i], 17, i == 1 ? c.mineInk : c.ink, true));
            cell.addView(mono(hours[i], 11, c.muted));
            weight(row, cell);
        }
        box.addView(row);
        return box;
    }

    View guide() {
        int[] icons = {R.drawable.ic_calendar, R.drawable.ic_layers, R.drawable.ic_clock, R.drawable.ic_right};
        String[] titles = {"Tryck en gång", "Tryck två gånger", "Håll ned", "Svep åt sidan"};
        String[] details = {"Markerar hela dagen.",
                "Öppnar dagens vecka från månadsvyn. Gå tillbaka med Månad eller Androids bakåtknapp.",
                "Visar dagens pass. Välj ett pass från ett annat skiftlag för att lägga till övertid.",
                "Går till föregående eller nästa månad eller vecka."};
        Stack panel = panel(0, c.paper, true);
        for (int i = 0; i < titles.length; i++) {
            if (i != 0) panel.addView(divider());
            Stack line = row(12);
            pad(line, 14);
            line.addView(iconTile(icons[i], 36, 18, c.mineInk, c.mine, 10, false));
            Stack labels = column(3);
            labels.addView(headline(titles[i], 14));
            labels.addView(text(details[i], 13, c.muted, false));
            weight(line, labels);
            panel.addView(line);
        }
        return panel;
    }

    View timeWheel(LocalTime value, Consumer<LocalTime> change) {
        Stack row = row(5);
        NumberPicker hours = picker(23, value.getHour(), "Tim");
        NumberPicker minutes = picker(59, value.getMinute(), "Min");
        NumberPicker.OnValueChangeListener listener = (picker, old, selected) ->
                change.accept(LocalTime.of(hours.getValue(), minutes.getValue()));
        hours.setOnValueChangedListener(listener);
        minutes.setOnValueChangedListener(listener);
        weight(row, wheelPanel(hours, "Tim"));
        row.addView(text(":", 19, c.quiet, true), size(WRAP, WRAP));
        weight(row, wheelPanel(minutes, "Min"));
        return row;
    }

    private NumberPicker picker(int max, int value, String label) {
        Context context = new ContextThemeWrapper(activity, activity.dark()
                ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setFormatter(i -> String.format(Locale.ROOT, "%02d", i));
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setContentDescription(label);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            picker.setSelectionDividerHeight(0);
            picker.setTextColor(c.mineInk);
            picker.setTextSize(sp(19));
        }
        return picker;
    }

    private View wheelPanel(NumberPicker picker, String label) {
        Stack panel = column(0);
        surface(panel, c.subtle, 13, 0, 0);
        pad(panel, 4, 0, 4, 6);
        FrameLayout frame = new FrameLayout(activity);
        frame.addView(block(c.mine, 8), new FrameLayout.LayoutParams(MATCH, dp(34), Gravity.CENTER));
        frame.addView(picker, new FrameLayout.LayoutParams(MATCH, dp(102)));
        panel.addView(frame);
        panel.addView(centered(label, 10, c.muted, true));
        return panel;
    }
}
