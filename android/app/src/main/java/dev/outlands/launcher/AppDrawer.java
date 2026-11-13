package dev.outlands.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** A real, searchable list of launchable activities, loaded away from the UI thread. */
final class AppDrawer {
    private AppDrawer() { }

    static Dialog show(Activity activity, ExecutorService worker, CatalogStore store, Runnable pinsChanged) {
        Dialog dialog = new Dialog(activity, R.style.Theme_OutlandsHome);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(activity, "All apps", 28, Ui.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button close = Ui.button(activity, "Close", false);
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.setMargins(0, Ui.dp(activity, 20), 0, Ui.dp(activity, 16));
        root.addView(header, headerParams);

        EditText search = Ui.input(activity, activity.getString(R.string.search_apps));
        search.setContentDescription(activity.getString(R.string.search_apps));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView pinHint = Ui.text(activity, "Long press an app to pin it to Home.", 12, Ui.MUTED, false);
        pinHint.setPadding(0, Ui.dp(activity, 10), 0, 0);
        root.addView(pinHint);
        TextView status = Ui.eyebrow(activity, "LOADING INSTALLED APPS");
        status.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 10));
        root.addView(status);

        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(activity, 12));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        AppAdapter adapter = new AppAdapter(activity, store);
        Dialog[] appMenu = new Dialog[1];
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = adapter.visible.get(position);
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(entry.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                activity.startActivity(launch);
                dialog.dismiss();
            } catch (ActivityNotFoundException | SecurityException error) {
                Toast.makeText(activity, "This app is no longer available to open.", Toast.LENGTH_SHORT).show();
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Entry entry = adapter.visible.get(position);
            boolean pinned = store.isAppPinned(entry.component);
            if (appMenu[0] != null) appMenu[0].dismiss();
            appMenu[0] = new AlertDialog.Builder(activity).setTitle(entry.label)
                    .setItems(new String[]{pinned ? "Remove from Home" : "Pin to Home", "App settings"}, (menu, which) -> {
                        if (which == 0) {
                            try {
                                store.setAppPinned(entry.component, !pinned);
                                adapter.notifyDataSetChanged();
                                pinsChanged.run();
                                Toast.makeText(activity, pinned ? "Removed from Home." : "Pinned to Home.", Toast.LENGTH_SHORT).show();
                            } catch (IllegalArgumentException limit) {
                                Toast.makeText(activity, limit.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            try {
                                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + entry.component.getPackageName())));
                                dialog.dismiss();
                            } catch (ActivityNotFoundException | SecurityException unavailable) {
                                Toast.makeText(activity, "App settings are unavailable.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).setNegativeButton("Cancel", null).show();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
                status.setText(adapter.visible.size() + " APPS");
            }
            public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> { if (appMenu[0] != null) appMenu[0].dismiss(); });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            Ui.edgeToEdge(window, root, Ui.dp(activity, 20));
        }
        worker.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            Set<ComponentName> seen = new HashSet<>();
            try {
                Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(query, 0)) {
                    if (info.activityInfo == null || activity.getPackageName().equals(info.activityInfo.packageName)) {
                        continue;
                    }
                    ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                    if (seen.add(component)) {
                        try {
                            entries.add(new Entry(info.loadLabel(activity.getPackageManager()).toString(),
                                    component, info.loadIcon(activity.getPackageManager())));
                        } catch (RuntimeException disappearedDuringQuery) {
                            // An uninstall/update during enumeration should not break Home.
                        }
                    }
                }
                Collator collator = Collator.getInstance();
                entries.sort((a, b) -> collator.compare(a.label, b.label));
            } catch (RuntimeException queryFailure) {
                activity.runOnUiThread(() -> {
                    if (dialog.isShowing()) status.setText("COULD NOT READ INSTALLED APPS");
                });
                return;
            }
            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed() && dialog.isShowing()) {
                    adapter.all.addAll(entries);
                    adapter.filter(search.getText().toString());
                    status.setText(adapter.visible.size() + " APPS");
                }
            });
        });
        return dialog;
    }

    private static final class Entry {
        final String label;
        final ComponentName component;
        final Drawable icon;
        Entry(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.icon = icon;
        }
    }

    private static final class AppAdapter extends BaseAdapter {
        final Activity activity;
        final List<Entry> all = new ArrayList<>();
        final List<Entry> visible = new ArrayList<>();
        final CatalogStore store;
        AppAdapter(Activity activity, CatalogStore store) { this.activity = activity; this.store = store; }

        void filter(String query) {
            String term = query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (Entry entry : all) {
                if (term.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(term)
                        || entry.component.getPackageName().toLowerCase(Locale.ROOT).contains(term)) {
                    visible.add(entry);
                }
            }
            notifyDataSetChanged();
        }

        public int getCount() { return visible.size(); }
        public Object getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            Holder holder;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                holder = (Holder) row.getTag();
            } else {
                row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(activity, 76));
                row.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12));
                row.setBackground(Ui.clickable(activity, false));
                FrameLayout icon = new FrameLayout(activity);
                row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 46), Ui.dp(activity, 46)));
                LinearLayout words = new LinearLayout(activity);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView label = Ui.text(activity, "", 16, Ui.WHITE, true);
                TextView packageName = Ui.text(activity, "", 11, Ui.MUTED, false);
                words.addView(label);
                words.addView(packageName);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
                textParams.setMargins(Ui.dp(activity, 14), 0, 0, 0);
                row.addView(words, textParams);
                holder = new Holder(icon, label, packageName);
                row.setTag(holder);
            }
            Entry entry = visible.get(position);
            holder.icon.removeAllViews();
            holder.icon.addView(new OutlandsBadge(activity, OutlandsBadge.kindFor(entry.label + " " + entry.component.getPackageName()), entry.icon),
                    new FrameLayout.LayoutParams(-1, -1));
            boolean pinned = store.isAppPinned(entry.component);
            holder.label.setText(entry.label + (pinned ? "  ★" : ""));
            holder.packageName.setText(entry.component.getPackageName());
            row.setContentDescription(entry.label + ". " + entry.component.getPackageName()
                    + (pinned ? ". Pinned to Home." : "") + ". Long press for Home pin and app settings.");
            return row;
        }
    }

    private static final class Holder {
        final FrameLayout icon;
        final TextView label;
        final TextView packageName;
        Holder(FrameLayout icon, TextView label, TextView packageName) {
            this.icon = icon;
            this.label = label;
            this.packageName = packageName;
        }
    }
}
