package dev.outlands.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReleaseHandoffActivity extends Activity {
    private static final int SAVE_MARKDOWN = 78;
    private static final String PULL_REQUEST = "pullRequest";
    private static final String PREVIOUS_CAPTURE = "previousCapture";
    private static final String CURRENT_CAPTURE = "currentCapture";
    private static final String INCLUDE_NAMES = "includeNames";
    private static final String INCLUDE_PATHS = "includePaths";
    private static final String INCLUDE_COMMITS = "includeCommits";
    private static final String INCLUDE_READINESS = "includeReadiness";
    private static final String APPROVED_MARKDOWN = "approvedMarkdown";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ChangeComparison comparison;
    private ReleaseReadiness.Summary readiness;
    private CheckBox includeReadiness;
    private CheckBox names;
    private CheckBox paths;
    private CheckBox commits;
    private TextView preview;
    private TextView status;
    private Button save;
    private Button back;
    private String approvedMarkdown;
    private boolean writing;

    static Intent open(Activity owner, ChangeBriefStore.SavedBriefs pair) {
        return new Intent(owner, ReleaseHandoffActivity.class)
                .putExtra(PULL_REQUEST, pair.current.pullRequestUrl)
                .putExtra(PREVIOUS_CAPTURE, pair.previous.capturedAt.toString())
                .putExtra(CURRENT_CAPTURE, pair.current.capturedAt.toString());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (state != null) approvedMarkdown = state.getString(APPROVED_MARKDOWN);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(this, 16);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        TextView brand = Ui.eyebrow(this, "OUTLANDS");
        brand.setTextColor(Ui.YELLOW);
        add(page, brand);
        add(page, Ui.text(this, "Release Handoff", 28, Ui.WHITE, true));
        back = Ui.button(this, "Back to comparison", false);
        back.setOnClickListener(view -> finish());
        add(page, back);

        includeReadiness = option(page, "Include readiness summary",
                state != null && state.getBoolean(INCLUDE_READINESS));
        names = option(page, "Include check names", state != null && state.getBoolean(INCLUDE_NAMES));
        paths = option(page, "Include changed paths", state != null && state.getBoolean(INCLUDE_PATHS));
        commits = option(page, "Include commit IDs", state != null && state.getBoolean(INCLUDE_COMMITS));
        save = Ui.button(this, "Choose Markdown destination", true);
        save.setOnClickListener(view -> chooseDestination());
        add(page, save);

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(page, status);
        preview = Ui.text(this, "", 14, Ui.WHITE, false);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextDirection(View.TEXT_DIRECTION_LTR);
        preview.setTextIsSelectable(true);
        preview.setPadding(padding, padding, padding, padding);
        preview.setBackground(Ui.panel(this, Ui.PANEL, Ui.BORDER));
        add(page, preview);
        setContentView(scroll);
        Ui.edgeToEdge(getWindow(), scroll, 0);

        updateControls();
        message("Reading selected comparison…");
        String url = getIntent().getStringExtra(PULL_REQUEST);
        String previous = getIntent().getStringExtra(PREVIOUS_CAPTURE);
        String current = getIntent().getStringExtra(CURRENT_CAPTURE);
        worker.execute(() -> loadComparison(url, previous, current));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(INCLUDE_NAMES, names.isChecked());
        state.putBoolean(INCLUDE_PATHS, paths.isChecked());
        state.putBoolean(INCLUDE_COMMITS, commits.isChecked());
        state.putBoolean(INCLUDE_READINESS, includeReadiness.isChecked());
        state.putString(APPROVED_MARKDOWN, approvedMarkdown);
    }

    private void loadComparison(String url, String previous, String current) {
        try {
            ChangeBriefStore.SavedBriefs pair = ChangeBriefStore.find(getApplicationContext(), url);
            if (pair != null && pair.previous != null && pair.previous.capturedAt.toString().equals(previous)
                    && pair.current.capturedAt.toString().equals(current)) {
                ChangeComparison selected = new ChangeComparison(pair);
                ReleaseReadiness.Summary summary = loadReadiness(pair);
                onUi(() -> {
                    comparison = selected;
                    readiness = summary;
                    if (summary == null) {
                        includeReadiness.setChecked(false);
                        includeReadiness.setText("Readiness summary unavailable");
                    }
                    updatePreview();
                    updateControls();
                    if (!writing) message("");
                });
                return;
            }
            onUi(() -> message("The saved comparison changed. Reopen it to prepare a handoff."));
        } catch (IOException error) {
            onUi(() -> message("The saved comparison could not be read."));
        }
    }

    private ReleaseReadiness.Summary loadReadiness(ChangeBriefStore.SavedBriefs pair) {
        try {
            return ReleaseReadiness.evaluate(ReleaseReadiness.load(getApplicationContext(), pair.repository()),
                    pair.current);
        } catch (IOException error) {
            return null;
        }
    }

    private CheckBox option(LinearLayout page, String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(Ui.WHITE);
        box.setButtonTintList(ColorStateList.valueOf(Ui.CYAN));
        box.setMinHeight(Ui.dp(this, 48));
        box.setChecked(checked);
        box.setOnCheckedChangeListener((view, value) -> {
            updatePreview();
            message("");
        });
        add(page, box);
        return box;
    }

    private void updatePreview() {
        if (comparison != null) {
            preview.setText(comparison.markdown(names.isChecked(), paths.isChecked(), commits.isChecked(),
                    includeReadiness.isChecked() ? readiness : null));
        }
    }

    private void updateControls() {
        boolean enabled = comparison != null && approvedMarkdown == null && !writing;
        includeReadiness.setEnabled(enabled && readiness != null);
        names.setEnabled(enabled);
        paths.setEnabled(enabled);
        commits.setEnabled(enabled);
        save.setEnabled(enabled);
        back.setEnabled(!writing);
    }

    private void chooseDestination() {
        approvedMarkdown = preview.getText().toString();
        updateControls();
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/markdown")
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .putExtra(Intent.EXTRA_TITLE, "Outlands-Release-Handoff.md");
        try {
            startActivityForResult(picker, SAVE_MARKDOWN);
        } catch (ActivityNotFoundException | SecurityException error) {
            approvedMarkdown = null;
            updateControls();
            message("Android’s file picker is unavailable.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_MARKDOWN) return;
        String approved = approvedMarkdown;
        approvedMarkdown = null;
        if (result != RESULT_OK) {
            updateControls();
            return;
        }
        Uri destination = data == null ? null : data.getData();
        if (approved == null || destination == null || !"content".equals(destination.getScheme())) {
            updateControls();
            message("Review the handoff again to save it.");
            return;
        }
        writing = true;
        updateControls();
        message("Saving Markdown…");
        worker.execute(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) throw new IOException("Document provider returned no output stream");
                stream.write(approved.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | SecurityException error) {
                onUi(() -> complete("The Markdown handoff could not be saved."));
                return;
            }
            onUi(() -> complete("Markdown handoff saved."));
        });
    }

    private void complete(String feedback) {
        writing = false;
        updateControls();
        message(feedback);
    }

    private void message(String text) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void add(LinearLayout page, View child) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = Ui.dp(this, 8);
        page.addView(child, layout);
    }

    private void onUi(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    @Override public void onBackPressed() {
        if (!writing) super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
