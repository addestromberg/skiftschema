package se.skiftschema.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final String TAG = "Skiftschema";
    private static final int PERMISSION_CALENDAR = 42;
    private static final String[] CALENDAR_PERMISSIONS = {
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final ExecutorService provider = Executors.newSingleThreadExecutor();
    private final CalendarPage calendar = new CalendarPage(this);
    private final PreferencesPage preferences = new PreferencesPage(this);
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (profile != null) calendar.clockChanged();
            main.postDelayed(this, 15000);
        }
    };

    Schedule schedule;
    Profile profile;
    Ui ui;
    String route = "calendar";
    ScrollView pageScroll;
    boolean calendarBusy;
    private boolean retryAutomatic;
    private boolean resumed;
    private boolean destroyed;
    private boolean loadFailed;
    private long exportRevision;
    private Profile.Store store;
    private PhoneCalendar phoneCalendar;
    private FrameLayout root;
    private int calendarScrollY;
    private String notice;
    private TextView noticeView;
    private final Runnable clearNotice = () -> {
        notice = null;
        if (noticeView != null) root.removeView(noticeView);
        noticeView = null;
    };
    private Dialog dialog;
    private Runnable sheetBack;
    private Consumer<Boolean> afterPermission;
    private OnBackInvokedCallback backCallback;
    private boolean backRegistered;
    private String onboardingUnit = "five";
    private String onboardingCrew;
    private String onboardingGroup;
    private int onboardingStep = 1;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) restore(saved);
        if (Build.VERSION.SDK_INT >= 33) backCallback = this::navigateBack;
        store = new Profile.Store(getApplicationContext());
        phoneCalendar = new PhoneCalendar(getContentResolver());
        root = new FrameLayout(this);
        configureWindow(getWindow(), root);
        setContentView(root);
        render();
        disk.execute(() -> {
            try {
                Schedule loadedSchedule = new Schedule(getAssets());
                Profile loaded;
                try {
                    loaded = store.load();
                } catch (Exception error) {
                    Log.e(TAG, "Could not read profile", error);
                    loaded = new Profile();
                }
                Profile loadedProfile = loaded;
                main.post(() -> {
                    if (destroyed) return;
                    schedule = loadedSchedule;
                    profile = loadedProfile;
                    preferences.enter(route);
                    render("calendar".equals(route) ? calendarScrollY : 0);
                    if (resumed) sync(false);
                });
            } catch (Exception error) {
                Log.e(TAG, "Could not read bundled rosters", error);
                main.post(() -> {
                    if (destroyed) return;
                    loadFailed = true;
                    render();
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        main.removeCallbacks(heartbeat);
        heartbeat.run();
        if (profile != null) sync(false);
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(heartbeat);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        dismissSheet();
        disk.execute(store::close);
        disk.shutdown();
        provider.shutdown();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (profile == null || "system".equals(profile.appearance)) {
            dismissSheet();
            refresh();
        }
    }

    private void restore(Bundle saved) {
        route = saved.getString("route", "calendar");
        calendarScrollY = saved.getInt("calendarScrollY");
        onboardingUnit = saved.getString("onboardingUnit", "five");
        onboardingCrew = saved.getString("onboardingCrew");
        onboardingGroup = saved.getString("onboardingGroup");
        onboardingStep = saved.getInt("onboardingStep", 1);
        calendar.anchor = date(saved, "anchor");
        calendar.selected = date(saved, "selected");
        calendar.previousAnchor = date(saved, "previousAnchor");
        calendar.previousSelected = date(saved, "previousSelected");
        calendar.week = saved.getBoolean("week");
    }

    private LocalDate date(Bundle saved, String key) {
        String value = saved.getString(key);
        return value == null ? null : LocalDate.parse(value);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("route", route);
        out.putInt("calendarScrollY", "calendar".equals(route) && pageScroll != null
                ? pageScroll.getScrollY() : calendarScrollY);
        out.putString("onboardingUnit", onboardingUnit);
        out.putString("onboardingCrew", onboardingCrew);
        out.putString("onboardingGroup", onboardingGroup);
        out.putInt("onboardingStep", onboardingStep);
        if (calendar.anchor != null) out.putString("anchor", calendar.anchor.toString());
        if (calendar.selected != null) out.putString("selected", calendar.selected.toString());
        if (calendar.previousAnchor != null) out.putString("previousAnchor", calendar.previousAnchor.toString());
        if (calendar.previousSelected != null) out.putString("previousSelected", calendar.previousSelected.toString());
        out.putBoolean("week", calendar.week);
    }

    long now() { return System.currentTimeMillis(); }

    boolean dark() {
        String appearance = profile == null ? "system" : profile.appearance;
        return "dark".equals(appearance) || ("system".equals(appearance)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }

    void go(String destination) {
        if (route.equals(destination)) return;
        dismissSheet();
        if ("calendar".equals(route) && pageScroll != null) calendarScrollY = pageScroll.getScrollY();
        route = destination;
        preferences.enter(destination);
        render("calendar".equals(destination) ? calendarScrollY : 0);
    }

    void render() { render(0); }
    void refresh() { render(pageScroll == null ? 0 : pageScroll.getScrollY()); }

    private void render(int scrollY) {
        if (destroyed) return;
        ui = new Ui(this, Palette.get(profile == null ? "forest" : profile.accent, dark()));
        root.removeAllViews();
        root.setBackgroundColor(ui.c.canvas);
        setBarAppearance(getWindow(), dark());
        Ui.Stack screen = ui.column(0);
        root.addView(screen, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        pageScroll = null;
        if (profile == null) {
            ui.stretch(screen, ui.centered(loadFailed ? "Schemat kunde inte läsas." : "Läser schemat…",
                    14, ui.c.muted, false));
        } else if (profile.unit == null) {
            onboarding(screen);
        } else {
            if ("calendar".equals(route)) calendar.build(screen);
            else preferences.build(screen);
            if ("calendar".equals(route) || "prefs".equals(route)) screen.addView(ui.dock());
        }
        if (pageScroll != null && scrollY != 0) {
            ScrollView scroll = pageScroll;
            scroll.post(() -> scroll.scrollTo(0, scrollY));
        }
        showNotice();
        updateBack();
    }

    private void onboarding(Ui.Stack screen) {
        boolean five = "five".equals(onboardingUnit);
        int steps = five ? 4 : 3;
        int step = onboardingStep;
        int year = schedule.latestYear(onboardingUnit, now());
        Schedule.Roster roster = schedule.roster(onboardingUnit, year);
        screen.addView(ui.top(null, step > 1 ? this::navigateBack : null));
        Ui.Stack page = ui.column(18);
        ui.pad(page, 24, 10, 24, 24);
        Ui.Stack progress = ui.row(5);
        for (int i = 0; i < steps; i++) {
            progress.addView(ui.block(i < step ? ui.c.action : ui.c.edge, 2),
                    new LinearLayout.LayoutParams(0, ui.dp(4), 1));
        }
        page.addView(progress);
        page.addView(ui.text("Steg " + step + " av " + steps, 12, ui.c.muted, true));
        if (step == 1) {
            page.addView(ui.headline("Vilket schema jobbar du på?", 37));
            page.addView(ui.muted("Välj den skiftgång som gäller för dig."));
            for (String unit : new String[]{"five", "six"}) {
                boolean selected = onboardingUnit.equals(unit);
                page.addView(ui.option(Schedule.label(unit), unitDetail(unit), selected,
                        ui.optionIcon(R.drawable.ic_layers, selected), () -> {
                            if (onboardingUnit.equals(unit)) return;
                            onboardingUnit = unit;
                            onboardingCrew = null;
                            onboardingGroup = null;
                            refresh();
                        }));
            }
            Ui.Stack info = ui.panel(9, ui.c.subtle, false);
            ui.pad(info, 15);
            Ui.Stack line = ui.row(8);
            line.addView(ui.icon(R.drawable.ic_info, ui.c.mineInk), ui.size(18, 18));
            line.addView(ui.headline("Du kan ändra dig senare", 14), ui.size(Ui.WRAP, Ui.WRAP));
            info.addView(line);
            info.addView(ui.text("Byt schema, skiftlag och semestergrupp i Preferenser när du behöver.",
                    13, ui.c.muted, false));
            page.addView(info);
        } else if (step == 2) {
            page.addView(ui.headline("Vilket skiftlag tillhör du?", 37));
            page.addView(ui.muted("Dina arbetspass markeras i kalendern."));
            page.addView(ui.crewGrid(onboardingUnit, onboardingCrew, crew -> {
                onboardingCrew = crew;
                refresh();
            }));
            if (onboardingCrew != null) page.addView(ui.rosterPreview(onboardingUnit, onboardingCrew));
        } else if (step == steps) {
            page.addView(ui.headline("Så använder du schemat", 37));
            page.addView(ui.guide());
        } else {
            page.addView(ui.headline("Vilken semestergrupp tillhör du?", 37));
            page.addView(ui.muted("Välj färgen som gäller för dig " + year + ". Du kan också välja gruppen senare."));
            if (roster != null) {
                for (Schedule.Vacation vacation : roster.vacations()) {
                    page.addView(ui.option(vacation.label(), vacation.detail(),
                            vacation.id().equals(onboardingGroup), ui.swatch(ui.c.vacationColor(vacation.id())), () -> {
                                onboardingGroup = vacation.id();
                                refresh();
                            }));
                }
            }
        }
        View padding = new View(this);
        padding.setMinimumHeight(ui.dp(4));
        ui.stretch(page, padding);
        page.addView(ui.divider());
        page.addView(ui.button(step == steps ? "Öppna schemat" : "Fortsätt", Ui.PRIMARY,
                step != 2 || onboardingCrew != null, 48, () -> {
                    if (step < steps) {
                        onboardingStep++;
                        render();
                    } else {
                        profile.unit = onboardingUnit;
                        profile.crews.put(onboardingUnit, onboardingCrew);
                        if (five && onboardingGroup != null) {
                            profile.vacation.put(Schedule.yearKey(onboardingUnit, year), onboardingGroup);
                        }
                        save();
                        render();
                    }
                }));
        if (five && step == 3) page.addView(ui.button("Hoppa över", Ui.QUIET, () -> {
            onboardingGroup = null;
            onboardingStep++;
            render();
        }));
        pageScroll = ui.scroll(page);
        ui.stretch(screen, pageScroll);
    }

    static String unitDetail(String unit) {
        return "five".equals(unit) ? "Fem skiftlag · A–E" : "Sex skiftlag · A–F";
    }

    void sheet(String title, String subtitle, boolean center, Runnable back, Consumer<Ui.Stack> content) {
        if (destroyed) return;
        if (dialog == null) {
            dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar) {
                @Override public void onBackPressed() { closeSheet(); }
            };
            dialog.setCancelable(true);
        }
        sheetBack = back;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(ui.c.scrim);
        overlay.setOnClickListener(view -> closeSheet());
        overlay.setContentDescription("Stäng");
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Ui.Stack sheet = ui.column(0);
        ui.surface(sheet, ui.c.paper, 23, ui.c.edge, center ? 1 : 0);
        if (!center) {
            var field = ui.fill(ui.c.paper, 0, 0, 0);
            float r = ui.dp(23);
            field.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            sheet.setBackground(field);
            LinearLayout.LayoutParams handle = ui.size(42, 4);
            handle.gravity = Gravity.CENTER_HORIZONTAL;
            handle.topMargin = ui.dp(8);
            sheet.addView(ui.block(ui.c.edge, 2), handle);
        }
        sheet.setClickable(true);
        if (Build.VERSION.SDK_INT >= 28) sheet.setAccessibilityPaneTitle(title);
        Ui.Stack body = ui.column(14);
        ui.pad(body, 23, center ? 19 : 12, 23, 24);
        Ui.Stack heading = ui.row(9);
        ui.weight(heading, ui.headline(title, 21));
        if (!center) heading.addView(ui.iconButton(R.drawable.ic_close, "Stäng", true, this::closeSheet));
        body.addView(heading);
        if (subtitle != null) body.addView(ui.text(subtitle, 13, ui.c.muted, false));
        content.accept(body);
        sheet.addView(ui.scroll(body));
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = center ? Math.min((int) (screenWidth * .9f), ui.dp(400))
                : Math.min(screenWidth, ui.dp(480));
        overlay.addView(sheet, new FrameLayout.LayoutParams(width, Ui.WRAP,
                center ? Gravity.CENTER : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        configureWindow(window, overlay);
        setBarAppearance(window, dark());
        dialog.setContentView(overlay);
        if (!dialog.isShowing()) {
            dialog.show();
            window.setLayout(Ui.MATCH, Ui.MATCH);
            if (Build.VERSION.SDK_INT >= 33) {
                dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::closeSheet);
            }
        }
    }

    private void closeSheet() {
        Runnable back = sheetBack;
        if (back == null) dismissSheet();
        else back.run();
    }

    void dismissSheet() {
        if (dialog == null) return;
        dialog.dismiss();
        dialog = null;
        sheetBack = null;
    }

    void confirm(String title, String detail, String cancel, String accept, int kind,
                 Runnable onCancel, Runnable onAccept) {
        sheet(title, detail, true, onCancel, panel -> {
            Ui.Stack buttons = ui.row(8);
            ui.weight(buttons, ui.button(cancel, Ui.SECONDARY, onCancel));
            ui.weight(buttons, ui.button(accept, kind, onAccept));
            panel.addView(buttons);
        });
    }

    @SuppressWarnings("deprecation")
    private void configureWindow(Window window, FrameLayout container) {
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : Color.BLACK);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        container.requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    private void setBarAppearance(Window window, boolean dark) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;
            int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(dark ? 0 : flags, flags);
        } else {
            View decor = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(dark ? decor.getSystemUiVisibility() & ~flags
                    : decor.getSystemUiVisibility() | flags);
        }
    }

    private boolean handlesBack() {
        if (profile == null) return false;
        if (profile.unit == null) return onboardingStep > 1;
        return !"calendar".equals(route) || calendar.week;
    }

    private void updateBack() {
        if (Build.VERSION.SDK_INT < 33 || handlesBack() == backRegistered) return;
        backRegistered = handlesBack();
        if (backRegistered) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        else getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (handlesBack()) navigateBack();
        else super.onBackPressed();
    }

    private void navigateBack() {
        if (profile.unit == null) {
            onboardingStep--;
            render();
        } else {
            switch (route) {
                case "calendar" -> calendar.back();
                case "prefs" -> go("calendar");
                case "accent" -> go("appearance");
                default -> go("prefs");
            }
        }
    }

    void save() {
        String data = profile.json();
        disk.execute(() -> {
            try {
                store.save(data);
            } catch (Exception error) {
                Log.e(TAG, "Could not save profile", error);
                main.post(() -> notice("Ändringen sparades inte på enheten. Försök igen."));
            }
        });
    }

    void preferencesChanged(boolean calendarChange) {
        boolean approval = calendarChange && profile.export.enabled && profile.export.sync;
        if (calendarChange) exportRevision++;
        if (approval) profile.export.pending = true;
        save();
        refresh();
        if (approval) confirmSync();
    }

    void overtimeChanged() {
        save();
        refresh();
        sync(false);
    }

    void changeUnit(String unit, String crew) {
        boolean newUnit = !unit.equals(profile.unit);
        boolean changed = newUnit || !crew.equals(profile.crews.get(unit));
        profile.unit = unit;
        profile.crews.put(unit, crew);
        if (newUnit) {
            calendar.reset();
            calendarScrollY = 0;
        }
        dismissSheet();
        preferencesChanged(changed);
    }

    void notice(String message) {
        if (destroyed) return;
        notice = message;
        showNotice();
        main.removeCallbacks(clearNotice);
        main.postDelayed(clearNotice, 5500);
    }

    private void showNotice() {
        if (noticeView != null) root.removeView(noticeView);
        noticeView = null;
        if (notice == null) return;
        noticeView = ui.text(notice, 13, ui.c.heroMain, false);
        noticeView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.surface(noticeView, ui.c.hero, 17, 0, 0);
        ui.pad(noticeView, 13);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        position.leftMargin = ui.dp(16);
        position.rightMargin = ui.dp(16);
        position.bottomMargin = ui.dp(62);
        root.addView(noticeView, position);
    }

    private boolean permitted() {
        for (String permission : CALENDAR_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void calendarPermission(Consumer<Boolean> callback) {
        if (permitted()) callback.accept(true);
        else if (afterPermission == null) {
            afterPermission = callback;
            requestPermissions(CALENDAR_PERMISSIONS, PERMISSION_CALENDAR);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_CALENDAR || afterPermission == null) return;
        Consumer<Boolean> callback = afterPermission;
        afterPermission = null;
        callback.accept(permitted());
    }

    void systemSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    void setExport(boolean enabled) {
        if (!enabled) {
            profile.export.enabled = false;
            save();
            refresh();
            return;
        }
        calendarPermission(granted -> {
            profile.export.enabled = granted;
            profile.export.rejected = !granted;
            save();
            refresh();
            if (granted) sync(false);
        });
    }

    void requestSync() {
        if (profile.export.pending) confirmSync();
        else sync(true);
    }

    private void confirmSync() {
        confirm("Uppdatera kalendern?",
                "Inställningen är sparad i appen. Vill du uppdatera telefonens kalender?",
                "Inte nu", "Uppdatera", Ui.PRIMARY, this::dismissSheet, () -> {
                    dismissSheet();
                    sync(true);
                });
    }

    void sync(boolean manual) {
        if (!profile.export.enabled || (!manual && (!profile.export.sync || profile.export.pending))) return;
        if (calendarBusy) {
            retryAutomatic |= !manual;
            if (manual) notice("Kalendern uppdateras redan. Försök igen om en stund.");
            return;
        }
        if (manual) calendarPermission(granted -> {
            if (granted) exportCalendar(true);
            else denyCalendar(true);
        });
        else if (permitted()) exportCalendar(false);
        else denyCalendar(false);
    }

    private void denyCalendar(boolean manual) {
        profile.export.enabled = false;
        profile.export.rejected = true;
        save();
        if ("export".equals(route)) refresh();
        if (manual) notice("Kalenderåtkomst saknas.");
    }

    private void exportCalendar(boolean manual) {
        String snapshot = profile.json();
        long revision = exportRevision;
        long now = now();
        calendarBusy = true;
        if ("export".equals(route)) refresh();
        provider.execute(() -> {
            Long calendarId = null;
            boolean success = false;
            try {
                Profile pending = Profile.parse(snapshot);
                calendarId = phoneCalendar.open(pending.export.calendarId, true);
                if (calendarId == null) throw new IllegalStateException("Calendar unavailable");
                phoneCalendar.reconcile(calendarId, pending, schedule, now);
                success = true;
            } catch (Exception error) {
                Log.e(TAG, "Could not update calendar", error);
            }
            Long id = calendarId;
            boolean completed = success;
            main.post(() -> {
                if (destroyed) return;
                if (id != null) profile.export.calendarId = id;
                if (completed && manual && revision == exportRevision) profile.export.pending = false;
                save();
                calendarFinished();
                if (!completed) notice("Telefonens kalender kunde inte uppdateras. Försök igen i Kalenderexport.");
                else if (manual) notice("Kalendern är uppdaterad.");
            });
        });
    }

    void deleteEvents(int category, PhoneCalendar.Range range) {
        profile.export.categories[category] = false;
        exportRevision++;
        save();
        refresh();
        if (range == PhoneCalendar.Range.KEEP) return;
        calendarPermission(granted -> {
            if (!granted) {
                notice("Kalenderåtkomst krävs för att ta bort händelser.");
                return;
            }
            Long id = profile.export.calendarId;
            long now = now();
            calendarBusy = true;
            if ("export".equals(route)) refresh();
            provider.execute(() -> {
                boolean success = false;
                try {
                    Long calendarId = phoneCalendar.open(id, false);
                    if (calendarId != null) phoneCalendar.clear(calendarId, category, range, now);
                    success = true;
                } catch (Exception error) {
                    Log.e(TAG, "Could not remove calendar events", error);
                }
                boolean completed = success;
                main.post(() -> {
                    if (destroyed) return;
                    calendarFinished();
                    notice(completed ? "Valda kalenderhändelser har tagits bort."
                            : "Telefonens kalender kunde inte uppdateras. Försök igen i Kalenderexport.");
                });
            });
        });
    }

    private void calendarFinished() {
        calendarBusy = false;
        if ("export".equals(route)) refresh();
        if (!retryAutomatic) return;
        retryAutomatic = false;
        sync(false);
    }
}
