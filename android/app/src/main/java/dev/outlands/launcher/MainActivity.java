package dev.outlands.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A permission-minimal Home replacement; every external action remains explicit. */
public final class MainActivity extends Activity {
    private static final int IMPORT_REQUEST = 100;
    private static final String TERMUX_PACKAGE = "com.termux";
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CatalogStore store;
    private List<CatalogStore.Service> services;
    private LinearLayout content;
    private LinearLayout categoryBar;
    private LinearLayout serviceGrid;
    private LinearLayout pinnedGrid;
    private TextView pinnedCaption;
    private int pinnedGeneration;
    private ScrollView page;
    private EditText serviceSearch;
    private TextView clock;
    private TextView date;
    private TextView battery;
    private TextView serviceCount;
    private Dialog activeDialog;
    private String selectedCategory = "";
    private boolean receiverRegistered;
    private boolean importing;

    private final Runnable clockTick = new Runnable() {
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 60000 - (System.currentTimeMillis() % 60000));
        }
    };

    private final BroadcastReceiver deviceSignals = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            updateClock();
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) updateBattery(intent);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new CatalogStore(this);
        services = store.load();
        if (savedInstanceState != null) selectedCategory = savedInstanceState.getString("category", "");
        buildHome();
        if (savedInstanceState != null) {
            serviceSearch.setText(savedInstanceState.getString("search", ""));
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter signals = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        signals.addAction(Intent.ACTION_TIME_CHANGED);
        signals.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        signals.addAction(Intent.ACTION_DATE_CHANGED);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = registerReceiver(deviceSignals, signals, Context.RECEIVER_NOT_EXPORTED);
        } else {
            sticky = registerReceiver(deviceSignals, signals);
        }
        receiverRegistered = true;
        if (sticky != null) updateBattery(sticky);
        clockHandler.removeCallbacks(clockTick);
        clockTick.run();
    }

    @Override protected void onStop() {
        clockHandler.removeCallbacks(clockTick);
        if (receiverRegistered) {
            unregisterReceiver(deviceSignals);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pinnedGrid != null) renderPinned();
    }

    @Override protected void onDestroy() {
        pinnedGeneration++;
        if (activeDialog != null) activeDialog.dismiss();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("search", serviceSearch.getText().toString());
        state.putString("category", selectedCategory);
        super.onSaveInstanceState(state);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (activeDialog != null) activeDialog.dismiss();
            hideKeyboard();
            serviceSearch.clearFocus();
            page.smoothScrollTo(0, 0);
        }
    }

    private void buildHome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        page = new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        page.setVerticalScrollBarEnabled(false);
        content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        page.addView(content, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL);
        root.addView(page, pageParams);
        root.addOnLayoutChangeListener((view, left, top, right, bottom, ol, ot, or, ob) -> {
            int available = right - left - root.getPaddingLeft() - root.getPaddingRight();
            int desired = Math.min(available, dp(960));
            if (desired > 0 && page.getLayoutParams().width != desired) {
                ViewGroup.LayoutParams params = page.getLayoutParams();
                params.width = desired;
                page.setLayoutParams(params);
            }
        });

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = column();
        TextView wordmark = Ui.text(this, "OUTLANDS", 27, Ui.WHITE, true);
        wordmark.setLetterSpacing(-0.04f);
        brand.addView(wordmark);
        TextView eyebrow = Ui.eyebrow(this, getString(R.string.field_workspace));
        eyebrow.setPadding(0, dp(5), 0, 0);
        brand.addView(eyebrow);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = Ui.button(this, getString(R.string.settings), false);
        settings.setOnClickListener(view -> { feedback(view); showSettings(); });
        brandRow.addView(settings);
        content.addView(brandRow, matchWrap());
        space(content, 22);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM);
        clock = Ui.text(this, "", 39, Ui.WHITE, true);
        boolean largeText = getResources().getConfiguration().fontScale > 1.35f;
        if (largeText) timeRow.setOrientation(LinearLayout.VERTICAL);
        timeRow.addView(clock, largeText ? matchWrap() : new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout localSignals = column();
        localSignals.setGravity(largeText ? Gravity.START : Gravity.END);
        date = Ui.text(this, "", 13, Ui.MUTED, false);
        battery = Ui.eyebrow(this, "BATTERY —");
        battery.setPadding(0, dp(5), 0, dp(3));
        localSignals.addView(date);
        localSignals.addView(battery);
        timeRow.addView(localSignals);
        content.addView(timeRow, matchWrap());
        space(content, 18);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.panel(this, Ui.PANEL, Ui.BORDER));
        ImageView artwork = new TopCropImage(this);
        artwork.setImageResource(R.drawable.outlands_wallpaper);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xee080b11, 0x85080b11, 0x00080b11}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroCopy = column();
        heroCopy.setPadding(dp(18), dp(18), dp(18), dp(20));
        TextView heroTag = Ui.eyebrow(this, "BUILD / OBSERVE / OPERATE");
        heroTag.setTextColor(Ui.YELLOW);
        heroCopy.addView(heroTag);
        TextView heroTitle = Ui.text(this, "MOBILE\nOPS CENTER.", 32, Ui.WHITE, true);
        heroTitle.setLetterSpacing(-0.035f);
        LinearLayout.LayoutParams heroTitleParams = matchWrap();
        heroTitleParams.topMargin = dp(22);
        heroCopy.addView(heroTitle, heroTitleParams);
        hero.addView(heroCopy, new FrameLayout.LayoutParams(-1, -1));
        int heroHeight = Math.max(216, Math.round(160 * getResources().getConfiguration().fontScale));
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(heroHeight)));
        space(content, 25);

        section("PINNED", "Your Home favorites");
        pinnedCaption = Ui.eyebrow(this, "");
        pinnedCaption.setPadding(0, 0, 0, dp(10));
        content.addView(pinnedCaption, matchWrap());
        pinnedGrid = column();
        content.addView(pinnedGrid, matchWrap());
        space(content, 20);

        section("QUICK ACTIONS", null);
        LinearLayout quickGrid = column();
        List<View> quick = new ArrayList<>();
        quick.add(actionCard("01 / TERMINAL", "Terminal", "Open Termux", () -> openPackage(TERMUX_PACKAGE, "Termux")));
        quick.add(actionCard("02 / NETWORK", "Tailscale", "Open Tailscale", () -> openPackage("com.tailscale.ipn", "Tailscale")));
        quick.add(actionCard("03 / CODEX", "Codex", "Copy command + open Termux", () -> copyAndOpenTermux("~/bin/codex-pc")));
        quick.add(actionCard("04 / TOOLKIT", "Local tools", "Languages and utilities", this::showLocalTools));
        addGrid(quickGrid, quick);
        content.addView(quickGrid, matchWrap());
        space(content, 25);

        section("SERVICE CATALOG", "Private shortcuts");
        serviceSearch = Ui.input(this, getString(R.string.search_services));
        serviceSearch.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        serviceSearch.setContentDescription(getString(R.string.search_services));
        content.addView(serviceSearch, matchWrap());
        space(content, 12);
        HorizontalScrollView categories = new HorizontalScrollView(this);
        categories.setHorizontalScrollBarEnabled(false);
        categoryBar = new LinearLayout(this);
        categories.addView(categoryBar, new HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(categories, matchWrap());
        serviceCount = Ui.eyebrow(this, "");
        serviceCount.setPadding(0, dp(14), 0, dp(12));
        content.addView(serviceCount, matchWrap());
        serviceGrid = column();
        content.addView(serviceGrid, matchWrap());
        serviceSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderServices(); }
            public void afterTextChanged(Editable s) { }
        });
        renderCategories();
        renderServices();
        space(content, 24);
        TextView footer = Ui.eyebrow(this, "OUTLANDS / FIELD WORKSPACE");
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, matchWrap());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(16), dp(10), dp(16), dp(10));
        navigation.setBackground(Ui.panel(this, Ui.PANEL, Ui.BORDER));
        addNavigationButton(navigation, "Home", false, () -> {
            hideKeyboard();
            serviceSearch.clearFocus();
            page.smoothScrollTo(0, 0);
        });
        addNavigationButton(navigation, "Services", false, () -> {
            hideKeyboard();
            serviceSearch.clearFocus();
            page.smoothScrollTo(0, Math.max(0, serviceSearch.getTop() - dp(22)));
        });
        addNavigationButton(navigation, "Apps", true, () -> {
            hideKeyboard();
            activeDialog = AppDrawer.show(this, worker, store, this::renderPinned);
        });
        root.addView(navigation, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        navigation.addOnLayoutChangeListener((view, left, top, right, bottom, ol, ot, or, ob) -> {
            int reserved = bottom - top + dp(8);
            if (page.getPaddingBottom() != reserved) page.setPadding(0, 0, 0, reserved);
        });
        setContentView(root);
        Ui.edgeToEdge(getWindow(), root, 0);
        content.setFocusableInTouchMode(true);
        content.requestFocus();
    }

    private void addNavigationButton(LinearLayout parent, String label, boolean accent, Runnable action) {
        Button button = Ui.button(this, label, accent);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(8));
        parent.addView(button, params);
        button.setOnClickListener(view -> { feedback(view); action.run(); });
    }

    private View actionCard(String tag, String title, String detail, Runnable action) {
        LinearLayout card = column();
        card.setMinimumHeight(dp(140));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.clickable(this, false));
        badgeHeader(card, tag, OutlandsBadge.kindFor(tag + " " + title), null);
        space(card, 14);
        card.addView(Ui.text(this, title, 21, Ui.WHITE, true));
        TextView subtitle = Ui.text(this, detail, 12, Ui.MUTED, false);
        subtitle.setPadding(0, dp(7), 0, 0);
        card.addView(subtitle);
        card.setFocusable(true);
        card.setContentDescription(title + ". " + detail);
        card.setOnClickListener(view -> { feedback(view); action.run(); });
        return card;
    }

    private void renderCategories() {
        categoryBar.removeAllViews();
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CatalogStore.Service service : services) categories.add(service.category);
        if (!selectedCategory.isEmpty() && !categories.contains(selectedCategory)) selectedCategory = "";
        addCategory("All", "");
        for (String category : categories) addCategory(category, category);
    }

    private void addCategory(String label, String value) {
        boolean selected = selectedCategory.equals(value);
        Button button = Ui.button(this, label, selected);
        button.setTextSize(12);
        button.setSelected(selected);
        button.setContentDescription(label + (selected ? ", selected" : ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(8), 0);
        categoryBar.addView(button, params);
        button.setOnClickListener(view -> {
            feedback(view);
            selectedCategory = value;
            renderCategories();
            renderServices();
        });
    }

    private void renderServices() {
        if (serviceGrid == null) return;
        String query = serviceSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<View> cards = new ArrayList<>();
        Set<String> pinnedIds = new HashSet<>(store.pinnedServiceIds());
        for (CatalogStore.Service service : services) {
            String searchable = (service.label + " " + service.category + " " + service.description + " " + service.url)
                    .toLowerCase(Locale.ROOT);
            if ((!selectedCategory.isEmpty() && !selectedCategory.equalsIgnoreCase(service.category))
                    || !searchable.contains(query)) continue;
            cards.add(serviceCard(service, false, pinnedIds.contains(service.id)));
        }
        serviceGrid.removeAllViews();
        serviceCount.setText(cards.size() + " OF " + services.size() + " SERVICES");
        if (cards.isEmpty()) {
            TextView empty = Ui.text(this, services.isEmpty() ? "No services yet. Import a catalog in Settings."
                    : "No matching services. Try another search or category.", 15, Ui.MUTED, false);
            empty.setPadding(dp(4), dp(20), dp(4), dp(20));
            serviceGrid.addView(empty, matchWrap());
        } else addGrid(serviceGrid, cards);
    }

    private View serviceCard(CatalogStore.Service service, boolean compact, boolean pinned) {
        LinearLayout card = column();
        card.setMinimumHeight(dp(compact ? 150 : 190));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.clickable(this, false));
        badgeHeader(card, service.category.toUpperCase(Locale.ROOT) + (pinned ? " / PINNED" : ""),
                OutlandsBadge.kindFor(service.label + " " + service.category), null);
        space(card, 13);
        TextView label = Ui.text(this, service.label, compact ? 18 : 19, Ui.WHITE, true);
        label.setMaxLines(3);
        label.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(label);
        if (!compact && !service.description.isEmpty()) {
            TextView description = Ui.text(this, service.description, 12, Ui.MUTED, false);
            description.setPadding(0, dp(7), 0, dp(12));
            description.setMaxLines(3);
            description.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(description);
        } else space(card, 12);
        card.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));
        card.addView(Ui.text(this, service.url.isEmpty() ? "SET ADDRESS  +" : "OPEN SERVICE  ↗", 10,
                service.url.isEmpty() ? Ui.MUTED : Ui.YELLOW, true));
        card.setFocusable(true);
        card.setContentDescription(service.label + ". " + service.description
                + (pinned ? ". Pinned to Home." : "")
                + (service.url.isEmpty() ? ". Set address." : ". Open in browser.")
                + " Long press to pin or edit address.");
        card.setOnClickListener(view -> {
            feedback(view);
            if (service.url.isEmpty()) showServiceEditor(service); else openUrl(service.url);
        });
        card.setOnLongClickListener(view -> { feedback(view); showServiceOptions(service); return true; });
        return card;
    }

    private void badgeHeader(LinearLayout card, String tag, String kind, Drawable icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new OutlandsBadge(this, kind, icon), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView text = Ui.eyebrow(this, tag);
        text.setTextColor(Ui.CYAN);
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams words = new LinearLayout.LayoutParams(0, -2, 1);
        words.setMarginStart(dp(10));
        row.addView(text, words);
        card.addView(row, matchWrap());
    }

    private void showServiceOptions(CatalogStore.Service service) {
        boolean pinned = store.isServicePinned(service.id);
        activeDialog = new AlertDialog.Builder(this).setTitle(service.label)
                .setItems(new String[]{pinned ? "Remove from Home" : "Pin to Home", "Edit address"}, (dialog, which) -> {
                    if (which == 1) { showServiceEditor(service); return; }
                    try {
                        store.setServicePinned(service.id, !pinned);
                        renderPinned();
                        renderServices();
                        toast(pinned ? "Removed from Home." : "Pinned to Home.");
                    } catch (IllegalArgumentException limit) { toast(limit.getMessage()); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void renderPinned() {
        if (pinnedGrid == null) return;
        final int generation = ++pinnedGeneration;
        List<CatalogStore.Service> favorites = new ArrayList<>();
        for (String id : store.pinnedServiceIds()) {
            for (CatalogStore.Service service : services) {
                if (id.equals(service.id)) { favorites.add(service); break; }
            }
        }
        List<String> appComponents = store.pinnedApps();
        if (appComponents.isEmpty()) {
            applyPinned(favorites, new ArrayList<>());
            return;
        }
        worker.execute(() -> {
            List<PinnedApp> apps = new ArrayList<>();
            PackageManager manager = getPackageManager();
            for (String flattened : appComponents) {
                if (Thread.currentThread().isInterrupted()) return;
                ComponentName component = ComponentName.unflattenFromString(flattened);
                if (component == null) continue;
                try {
                    ActivityInfo info = manager.getActivityInfo(component, 0);
                    apps.add(new PinnedApp(component, info.loadLabel(manager).toString(), info.loadIcon(manager), info.enabled));
                } catch (PackageManager.NameNotFoundException | RuntimeException removed) {
                    apps.add(new PinnedApp(component, component.getPackageName(), null, false));
                }
            }
            runOnUiThread(() -> {
                if (!isDestroyed() && generation == pinnedGeneration) applyPinned(favorites, apps);
            });
        });
    }

    private void applyPinned(List<CatalogStore.Service> favorites, List<PinnedApp> apps) {
        pinnedGrid.removeAllViews();
        pinnedCaption.setText((favorites.size() + apps.size()) + " HOME SHORTCUTS");
        List<View> cards = new ArrayList<>();
        for (CatalogStore.Service service : favorites) cards.add(serviceCard(service, true, true));
        for (PinnedApp app : apps) {
            LinearLayout card = column();
            card.setMinimumHeight(dp(150));
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(Ui.clickable(this, false));
            badgeHeader(card, "APP / PINNED", OutlandsBadge.kindFor(app.label), app.icon);
            space(card, 13);
            TextView label = Ui.text(this, app.label, 18, Ui.WHITE, true);
            label.setMaxLines(3);
            label.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(label);
            space(card, 12);
            card.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));
            card.addView(Ui.text(this, app.available ? "OPEN APP  ↗" : "APP UNAVAILABLE", 10,
                    app.available ? Ui.YELLOW : Ui.MUTED, true));
            card.setFocusable(true);
            card.setContentDescription(app.label + (app.available ? ". Open app." : ". App unavailable.")
                    + " Long press to remove from Home or open app settings.");
            card.setOnClickListener(view -> {
                feedback(view);
                if (!app.available) { toast("This app is unavailable. Long press to remove its shortcut."); return; }
                startSafely(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                        "This app is no longer available to open.");
            });
            card.setOnLongClickListener(view -> { feedback(view); showPinnedAppOptions(app); return true; });
            cards.add(card);
        }
        if (cards.isEmpty()) {
            TextView hint = Ui.text(this, "Long press an app in Apps or a service below to pin it here.", 14, Ui.MUTED, false);
            hint.setPadding(dp(4), dp(4), dp(4), dp(12));
            pinnedGrid.addView(hint, matchWrap());
        } else addGrid(pinnedGrid, cards);
    }

    private void showPinnedAppOptions(PinnedApp app) {
        activeDialog = new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(new String[]{"Remove from Home", "App settings"}, (dialog, which) -> {
                    if (which == 0) {
                        store.setAppPinned(app.component, false);
                        renderPinned();
                        toast("Removed from Home.");
                    } else startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.component.getPackageName())), "App settings are unavailable.");
                }).setNegativeButton("Cancel", null).show();
    }

    private static final class PinnedApp {
        final ComponentName component;
        final String label;
        final Drawable icon;
        final boolean available;
        PinnedApp(ComponentName component, String label, Drawable icon, boolean available) {
            this.component = component;
            this.label = label;
            this.icon = icon;
            this.available = available;
        }
    }

    private void addGrid(LinearLayout target, List<View> cards) {
        int columns = getResources().getConfiguration().screenWidthDp >= 360
                && getResources().getConfiguration().fontScale <= 1.35f ? 2 : 1;
        for (int i = 0; i < cards.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setBaselineAligned(false);
            for (int column = 0; column < columns; column++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                if (column > 0) params.setMarginStart(dp(10));
                View card = i + column < cards.size() ? cards.get(i + column) : new Space(this);
                row.addView(card, params);
            }
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(10);
            target.addView(row, rowParams);
        }
    }

    private void showSettings() {
        LinearLayout settings = dialogColumn();
        TextView privacy = Ui.text(this, "Service addresses stay in this app's private storage. Use service addresses without passwords, API keys, or other secrets.", 14, Ui.MUTED, false);
        settings.addView(privacy, matchWrap());
        space(settings, 18);
        addSettingsButton(settings, "Edit a service address", this::showServiceChooser);
        addSettingsButton(settings, "Import service catalog", this::chooseCatalog);
        addSettingsButton(settings, "Apply Outlands wallpaper", this::applyWallpaper);
        Switch haptics = new Switch(this);
        haptics.setText("Touch feedback");
        haptics.setTextColor(Ui.WHITE);
        haptics.setTextSize(15);
        haptics.setMinHeight(dp(56));
        haptics.setChecked(store.hapticsEnabled());
        haptics.setOnCheckedChangeListener((button, checked) -> store.setHapticsEnabled(checked));
        settings.addView(haptics, matchWrap());
        space(settings, 12);
        addSettingsButton(settings, "Choose Home app", () -> openSystemSettings(Settings.ACTION_HOME_SETTINGS));
        addSettingsButton(settings, "Android settings", () -> openSystemSettings(Settings.ACTION_SETTINGS));
        addSettingsButton(settings, "Outlands app settings", () -> startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())), "App settings are unavailable."));
        space(settings, 12);
        addSettingsButton(settings, "Reset service catalog", this::confirmReset);
        TextView rollback = Ui.text(this, "To return to your previous launcher, use Choose Home app. Clearing this app's storage removes its saved links.", 12, Ui.MUTED, false);
        rollback.setPadding(0, dp(14), 0, dp(8));
        settings.addView(rollback, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(settings);
        activeDialog = new AlertDialog.Builder(this).setTitle("Outlands settings")
                .setView(scroll).setNegativeButton("Close", null).show();
    }

    private void addSettingsButton(LinearLayout parent, String label, Runnable action) {
        Button button = Ui.button(this, label, false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(8);
        parent.addView(button, params);
        button.setOnClickListener(view -> {
            feedback(view);
            if (activeDialog != null) activeDialog.dismiss();
            action.run();
        });
    }

    private void showServiceChooser() {
        if (services.isEmpty()) { toast("Import a service catalog first."); return; }
        String[] labels = new String[services.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = services.get(i).label + " · " + services.get(i).category;
        activeDialog = new AlertDialog.Builder(this).setTitle("Choose a service")
                .setItems(labels, (dialog, which) -> showServiceEditor(services.get(which)))
                .setNegativeButton("Cancel", null).show();
    }

    private void showServiceEditor(CatalogStore.Service service) {
        LinearLayout fields = dialogColumn();
        TextView explanation = Ui.text(this, "HTTPS address. Leave blank to keep an unconfigured shortcut.", 14, Ui.MUTED, false);
        fields.addView(explanation, matchWrap());
        space(fields, 16);
        EditText address = Ui.input(this, getString(R.string.url_hint));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setText(service.url);
        address.setSelectAllOnFocus(true);
        address.setContentDescription("Service HTTPS address");
        fields.addView(address, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(service.label)
                .setView(scroll).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        activeDialog = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                String url = CatalogStore.validateUrl(address.getText().toString());
                List<CatalogStore.Service> updated = new ArrayList<>();
                for (CatalogStore.Service item : services) updated.add(item.id.equals(service.id) ? item.withUrl(url) : item);
                store.save(updated);
                services = updated;
                renderServices();
                renderPinned();
                hideKeyboard();
                dialog.dismiss();
                toast("Service address saved.");
            } catch (IllegalArgumentException error) {
                address.setError(error.getMessage());
            }
        }));
        dialog.show();
    }

    private void chooseCatalog() {
        if (importing) { toast("A catalog is already being imported."); return; }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(picker, IMPORT_REQUEST);
        } catch (ActivityNotFoundException error) {
            toast("No document picker is installed.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importing = true;
        Uri document = data.getData();
        toast("Reading service catalog…");
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(document)) {
                if (input == null) throw new IOException("Document could not be opened.");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (bytes.size() + count > CatalogStore.MAX_FILE_BYTES) {
                        throw new IllegalArgumentException("Choose a JSON file smaller than 256 KiB.");
                    }
                    bytes.write(buffer, 0, count);
                }
                List<CatalogStore.Service> imported = CatalogStore.parse(bytes.toByteArray());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    importing = false;
                    // A slow provider may finish after the user opens another launcher dialog.
                    if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
                    activeDialog = new AlertDialog.Builder(this).setTitle("Replace service catalog?")
                            .setMessage("Import " + imported.size() + " services? This replaces the current catalog and its saved addresses.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Import", (dialog, which) -> {
                                store.save(imported);
                                services = imported;
                                selectedCategory = "";
                                serviceSearch.setText("");
                                renderCategories();
                                renderServices();
                                renderPinned();
                                toast(imported.size() + " services imported.");
                            }).show();
                });
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                runOnUiThread(() -> {
                    importing = false;
                    if (!isDestroyed()) toast(error instanceof IllegalArgumentException
                            ? error.getMessage() : "Could not read that document. Current services were kept.");
                });
            }
        });
    }

    private void confirmReset() {
        activeDialog = new AlertDialog.Builder(this).setTitle("Reset service catalog?")
                .setMessage("Remove saved service addresses and restore the six generic shortcuts? Other apps and their data are unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    store.reset();
                    services = store.load();
                    selectedCategory = "";
                    serviceSearch.setText("");
                    renderCategories();
                    renderServices();
                    renderPinned();
                    toast("Service catalog reset.");
                }).show();
    }

    private void showLocalTools() {
        String[] names = {"Python", "Node.js", "Git help", "Go help", "Rust help", "Nmap help"};
        String[] commands = {"python", "node", "git --help", "go help", "rustc --help", "nmap --help"};
        activeDialog = new AlertDialog.Builder(this).setTitle("Copy command + open Termux")
                .setItems(names, (dialog, which) -> copyAndOpenTermux(commands[which]))
                .setNegativeButton("Cancel", null).show();
    }

    private void applyWallpaper() {
        WallpaperManager wallpaper = WallpaperManager.getInstance(this);
        if (!wallpaper.isWallpaperSupported() || !wallpaper.isSetWallpaperAllowed()) {
            toast("Wallpaper changes are unavailable on this device or restricted by its administrator.");
            return;
        }
        toast("Applying Outlands wallpaper…");
        worker.execute(() -> {
            try {
                wallpaper.setResource(R.drawable.outlands_wallpaper,
                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                runOnUiThread(() -> { if (!isDestroyed()) toast("Outlands wallpaper applied to Home and lock screen."); });
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> { if (!isDestroyed()) toast("Wallpaper could not be applied. Existing wallpaper was kept where possible."); });
            }
        });
    }

    private void copyAndOpenTermux(String command) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) { toast("Clipboard is unavailable."); return; }
        clipboard.setPrimaryClip(ClipData.newPlainText("Outlands command", command));
        if (openPackage(TERMUX_PACKAGE, "Termux")) toast(getString(R.string.command_copied));
    }

    private boolean openPackage(String packageName, String label) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) { toast(label + " is not installed or has no launchable activity."); return false; }
        return startSafely(intent, label + " could not be opened.");
    }

    private void openUrl(String url) {
        try {
            String validated = CatalogStore.validateUrl(url);
            if (validated.isEmpty()) return;
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(validated))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            Intent chrome = new Intent(web).setPackage("com.android.chrome");
            // Keep ChatGPT Web in a browser instead of a verified link opening its native app.
            Intent target = chrome.resolveActivity(getPackageManager()) != null ? chrome
                    : Intent.createChooser(web, "Open service in browser");
            startSafely(target, "No browser is available to open this address.");
        } catch (IllegalArgumentException error) {
            toast("That service address needs to be corrected in Settings.");
        }
    }

    private void openSystemSettings(String action) {
        if (!startSafely(new Intent(action), "This settings page is unavailable.")) {
            startSafely(new Intent(Settings.ACTION_SETTINGS), "Android settings are unavailable.");
        }
    }

    private boolean startSafely(Intent intent, String failure) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            toast(failure);
            return false;
        }
    }

    private void updateClock() {
        if (clock == null) return;
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now));
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE MMM d");
        date.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(now));
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) { battery.setText("BATTERY —"); return; }
        int percent = Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
        int state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String suffix = state == BatteryManager.BATTERY_STATUS_CHARGING ? " / CHARGING"
                : state == BatteryManager.BATTERY_STATUS_FULL ? " / FULL" : "";
        battery.setText("BATTERY " + percent + "%" + suffix);
    }

    private void feedback(View view) {
        if (store.hapticsEnabled()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && getCurrentFocus() != null) {
            keyboard.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(float value) { return Ui.dp(this, value); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout dialogColumn() { LinearLayout view = column(); view.setPadding(dp(24), dp(12), dp(24), dp(16)); return view; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private void space(LinearLayout parent, int amount) { parent.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(amount))); }

    private void section(String label, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.eyebrow(this, label);
        title.setTextColor(Ui.WHITE);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (detail != null) row.addView(Ui.text(this, detail, 11, Ui.MUTED, false));
        content.addView(row, matchWrap());
        space(content, 13);
    }

    /** Show the upper-middle architecture when using the portrait wallpaper in a wide hero. */
    private static final class TopCropImage extends ImageView {
        TopCropImage(Context context) { super(context); setScaleType(ScaleType.MATRIX); }
        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (getDrawable() == null || getDrawable().getIntrinsicWidth() <= 0 || getDrawable().getIntrinsicHeight() <= 0) return;
            float scale = Math.max(width / (float) getDrawable().getIntrinsicWidth(), height / (float) getDrawable().getIntrinsicHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((width - getDrawable().getIntrinsicWidth() * scale) / 2f,
                    (height - getDrawable().getIntrinsicHeight() * scale) * 0.30f);
            setImageMatrix(matrix);
        }
    }
}
