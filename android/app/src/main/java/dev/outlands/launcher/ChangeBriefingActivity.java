package dev.outlands.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChangeBriefingActivity extends Activity {
    private static final int PICK_BRIEF = 77;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ScrollView scroll;
    private LinearLayout page;
    private List<ChangeBriefStore.SavedBriefs> library = Collections.emptyList();
    private ChangeBriefStore.SavedBriefs selected;
    private ChangeBriefStore.Brief preview;
    private String message = "";
    private String query = "";
    private boolean busy;
    private boolean comparing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        page = column();
        int padding = Ui.dp(this, 16);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        Ui.edgeToEdge(getWindow(), scroll, 0);
        update(true, "Reading change library…");
        worker.execute(() -> {
            try {
                List<ChangeBriefStore.SavedBriefs> briefs = ChangeBriefStore.load(getApplicationContext());
                onUi(() -> {
                    library = briefs;
                    update(false, "");
                });
            } catch (IOException failure) {
                onUi(() -> update(false, "The saved change library could not be read."));
            }
        });
    }

    private void render() {
        page.removeAllViews();
        TextView brand = Ui.eyebrow(this, "OUTLANDS");
        brand.setTextColor(Ui.YELLOW);
        add(page, brand);
        if (comparing) {
            text(page, "Change Comparison", 28, Ui.WHITE, true);
            button(page, "Back to change brief", false, () -> showComparison(false));
            button(page, "Review release handoff", true,
                    () -> startActivity(ReleaseHandoffActivity.open(this, selected)));
            compareBriefs(new ChangeComparison(selected));
            return;
        }
        if (preview != null) {
            text(page, "Review import", 28, Ui.WHITE, true);
            String effect = preview.pullRequestUrl == null
                    ? "The library keeps one standalone brief without a PR link."
                    : "The library keeps the two latest captures for this pull request.";
            text(page, effect, 14, Ui.MUTED, false);
            button(page, "Save brief", true, this::savePreview).setEnabled(!busy);
            button(page, "Cancel import", false, this::openLibrary).setEnabled(!busy);
            showMessage();
            showBrief(preview);
        } else if (selected != null) {
            text(page, "Change Briefing", 28, Ui.WHITE, true);
            button(page, "Back to change library", false, this::openLibrary);
            if (selected.current.pullRequestUrl != null) {
                button(page, "Release readiness", false,
                        () -> startActivity(ReleaseReadinessActivity.open(this, selected)));
            }
            if (selected.previous != null) {
                button(page, "Compare briefs", false, () -> showComparison(true)).setEnabled(!busy);
            }
            showMessage();
            showBrief(selected.current);
        } else {
            showLibrary();
        }
    }

    private void showMessage() {
        if (!message.isEmpty()) {
            text(page, message, 14, Ui.YELLOW, false)
                    .setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
    }

    private void showLibrary() {
        text(page, "Change Library", 28, Ui.WHITE, true);
        button(page, "Back", false, this::finish).setEnabled(!busy);
        button(page, "Import change brief", true, this::pickFile).setEnabled(!busy);
        showMessage();
        if (library.isEmpty()) {
            if (!busy && message.isEmpty()) text(page, "No saved change briefs.", 15, Ui.MUTED, false);
            return;
        }
        EditText search = Ui.input(this, "Search by title or PR number");
        search.setContentDescription("Search by title or PR number");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setText(query);
        search.setEnabled(!busy);
        add(page, search);
        LinearLayout rows = column();
        add(page, rows);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                query = value.toString();
                showLibraryRows(rows);
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        showLibraryRows(rows);
    }

    private void showLibraryRows(LinearLayout rows) {
        rows.removeAllViews();
        String filter = query.trim().toLowerCase(Locale.ROOT);
        boolean byNumber = filter.matches("#[0-9]+");
        String number = byNumber ? filter.substring(1) : filter;
        for (ChangeBriefStore.SavedBriefs entry : library) {
            if (!entry.pullRequestNumber().equals(number) && (byNumber || !matchesTitle(entry, filter))) continue;
            LinearLayout card = panel(rows);
            String identity = entry.pullRequestNumber().isEmpty() ? "Standalone brief"
                    : entry.repository() + " · PR #" + entry.pullRequestNumber();
            text(card, identity, 14, Ui.CYAN, true);
            button(card, entry.current.title, false, () -> openEntry(entry)).setEnabled(!busy);
            text(card, "Captured (UTC) · " + entry.current.capturedAt, 14, Ui.MUTED, false);
            showLatestChecks(card, entry.current);
        }
        if (rows.getChildCount() == 0) text(rows, "No briefs match this search.", 15, Ui.MUTED, false);
    }

    private boolean matchesTitle(ChangeBriefStore.SavedBriefs entry, String filter) {
        return entry.current.title.toLowerCase(Locale.ROOT).contains(filter)
                || (entry.previous != null && entry.previous.title.toLowerCase(Locale.ROOT).contains(filter))
                || entry.repository().toLowerCase(Locale.ROOT).contains(filter);
    }

    private void showLatestChecks(LinearLayout card, ChangeBriefStore.Brief brief) {
        int failed = 0;
        int notRun = 0;
        for (ChangeBriefStore.Check check : brief.checks) {
            if (check.status == ChangeBriefStore.Status.FAILED) failed++;
            else if (check.status == ChangeBriefStore.Status.NOT_RUN) notRun++;
        }
        if (brief.checks.isEmpty()) text(card, "No check results recorded.", 14, Ui.MUTED, false);
        else if (failed == 0 && notRun == 0) text(card, "All recorded checks passed.", 14, Ui.CYAN, false);
        if (failed > 0) text(card, "Failed checks · " + failed, 14, Ui.YELLOW, true);
        if (notRun > 0) text(card, "Checks not run · " + notRun, 14, Ui.MUTED, false);
    }

    private void openEntry(ChangeBriefStore.SavedBriefs entry) {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(page.getWindowToken(), 0);
        selected = entry;
        update(false, "");
        scroll.scrollTo(0, 0);
    }

    private void openLibrary() {
        preview = null;
        selected = null;
        update(false, "");
        scroll.scrollTo(0, 0);
    }

    private void showComparison(boolean enabled) {
        comparing = enabled;
        update(false, "");
        scroll.scrollTo(0, 0);
    }

    @Override public void onBackPressed() {
        if (busy) return;
        if (comparing) showComparison(false);
        else if (preview != null || selected != null) openLibrary();
        else super.onBackPressed();
    }

    private void compareBriefs(ChangeComparison comparison) {
        ChangeBriefStore.Brief previous = comparison.previous;
        ChangeBriefStore.Brief current = comparison.current;
        LinearLayout summary = panel(page);
        text(summary, current.pullRequestUrl, 14, Ui.MUTED, false);
        text(summary, "Previous capture (UTC) · " + previous.capturedAt, 14, Ui.WHITE, false);
        text(summary, "Current capture (UTC) · " + current.capturedAt, 14, Ui.CYAN, true);

        LinearLayout checks = panel(page);
        text(checks, "Check changes", 18, Ui.WHITE, true);
        if (comparison.checks.isEmpty()) {
            text(checks, "No changes to recorded checks.", 14, Ui.MUTED, false);
        }
        for (ChangeComparison.CheckChange change : comparison.checks) {
            text(checks, change.name, 15, Ui.WHITE, true);
            text(checks, change.transition(), 15,
                    change.after == ChangeBriefStore.Status.FAILED ? Ui.YELLOW : Ui.MUTED, true);
        }

        LinearLayout paths = panel(page);
        text(paths, "Changed-path lists", 18, Ui.WHITE, true);
        if (!previous.baseCommit.equals(current.baseCommit)) {
            text(paths, "Base commit changed between captures.", 14, Ui.YELLOW, false);
        }
        if (comparison.addedPaths.isEmpty() && comparison.removedPaths.isEmpty()) {
            text(paths, "No paths entered or left the list.", 14, Ui.MUTED, false);
        } else {
            showPaths(paths, comparison.addedPaths, "Added to list");
            showPaths(paths, comparison.removedPaths, "Removed from list");
        }

        showCaptureCommits("Previous capture", previous);
        showCaptureCommits("Current capture", current);
    }

    private void showPaths(LinearLayout paths, List<String> entries, String label) {
        if (entries.isEmpty()) return;
        text(paths, label, 15, Ui.WHITE, true);
        for (String path : entries) code(paths, path);
    }

    private void showCaptureCommits(String label, ChangeBriefStore.Brief brief) {
        LinearLayout card = panel(page);
        text(card, label, 18, Ui.WHITE, true);
        text(card, brief.title, 15, Ui.MUTED, false);
        text(card, "Base commit", 15, Ui.WHITE, true);
        code(card, brief.baseCommit);
        text(card, "Target commit", 15, Ui.WHITE, true);
        code(card, brief.targetCommit);
    }

    private void showBrief(ChangeBriefStore.Brief brief) {
        LinearLayout card = panel(page);
        text(card, brief.title, 22, Ui.WHITE, true);
        text(card, "Captured (UTC) · " + brief.capturedAt, 14, Ui.MUTED, false);

        text(card, "Recorded checks", 17, Ui.WHITE, true);
        if (brief.checks.isEmpty()) text(card, "No check results recorded.", 14, Ui.MUTED, false);
        for (ChangeBriefStore.Check check : brief.checks) {
            int color = check.status == ChangeBriefStore.Status.PASSED ? Ui.CYAN
                    : check.status == ChangeBriefStore.Status.FAILED ? Ui.YELLOW : Ui.MUTED;
            text(card, check.name + " · " + check.status.label, 15, color, true);
        }

        text(card, "Changed paths", 17, Ui.WHITE, true);
        if (brief.changedPaths.isEmpty()) text(card, "No changed paths recorded.", 14, Ui.MUTED, false);
        for (String path : brief.changedPaths) code(card, path);
        text(card, "Base commit", 16, Ui.WHITE, true);
        code(card, brief.baseCommit);
        text(card, "Target commit", 16, Ui.WHITE, true);
        code(card, brief.targetCommit);
        if (brief.pullRequestUrl != null) {
            text(card, brief.pullRequestUrl, 14, Ui.MUTED, false);
            if (preview == null) button(card, "Open pull request", false,
                    () -> openPullRequest(brief.pullRequestUrl)).setEnabled(!busy);
        }
    }

    private void openPullRequest(String url) {
        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(browser);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            update(false, "No browser is available to open the pull request.");
        }
    }

    private void pickFile() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(picker, PICK_BRIEF);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            update(false, "Android’s file picker is unavailable.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_BRIEF || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) {
            update(false, "Choose a change brief through Android’s file picker.");
            return;
        }
        update(true, "Reading change brief…");
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Document unavailable");
                ChangeBriefStore.Brief candidate = ChangeBriefStore.read(input);
                onUi(() -> {
                    preview = candidate;
                    update(false, "");
                    scroll.scrollTo(0, 0);
                });
            } catch (ChangeBriefStore.InvalidBrief rejected) {
                onUi(() -> update(false, rejected.getMessage()));
            } catch (IOException | SecurityException failure) {
                onUi(() -> update(false, "The selected change brief could not be read."));
            }
        });
    }

    private void savePreview() {
        ChangeBriefStore.Brief candidate = preview;
        update(true, "Saving change brief…");
        worker.execute(() -> {
            try {
                List<ChangeBriefStore.SavedBriefs> briefs = ChangeBriefStore.save(getApplicationContext(), candidate);
                onUi(() -> {
                    library = briefs;
                    preview = null;
                    update(false, "Change brief saved.");
                    scroll.scrollTo(0, 0);
                });
            } catch (ChangeBriefStore.InvalidBrief rejected) {
                onUi(() -> update(false, rejected.getMessage()));
            } catch (IOException failure) {
                onUi(() -> update(false, "The change brief could not be saved."));
            }
        });
    }

    private void update(boolean working, String status) {
        busy = working;
        message = status;
        render();
    }

    private void onUi(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout panel(LinearLayout group) {
        LinearLayout card = column();
        int pad = Ui.dp(this, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.panel(this, Ui.PANEL, Ui.BORDER));
        add(group, card);
        return card;
    }

    private void code(LinearLayout group, String value) {
        TextView view = text(group, value, 14, Ui.MUTED, false);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextDirection(View.TEXT_DIRECTION_LTR);
        view.setTextIsSelectable(true);
    }

    private TextView text(LinearLayout group, String value, int size, int color, boolean bold) {
        TextView view = Ui.text(this, value, size, color, bold);
        add(group, view);
        return view;
    }

    private Button button(LinearLayout group, String label, boolean accent, Runnable action) {
        Button button = Ui.button(this, label, accent);
        button.setOnClickListener(view -> action.run());
        add(group, button);
        return button;
    }

    private void add(LinearLayout group, View child) {
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.bottomMargin = Ui.dp(this, 8);
        group.addView(child, position);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
