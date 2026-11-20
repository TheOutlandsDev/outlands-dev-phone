package dev.outlands.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReleaseReadinessActivity extends Activity {
    private static final String PULL_REQUEST = "pullRequest";
    private static final String CHECKLIST_DRAFT = "checklistDraft";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ScrollView scroll;
    private LinearLayout page;
    private ChangeBriefStore.SavedBriefs entry;
    private List<String> required = Collections.emptyList();
    private ReleaseReview review;
    private boolean reviewAvailable;
    private EditText editor;
    private TextView feedback;
    private String draft;
    private String message = "";
    private boolean busy = true;

    static Intent open(Activity owner, ChangeBriefStore.SavedBriefs entry) {
        return new Intent(owner, ReleaseReadinessActivity.class)
                .putExtra(PULL_REQUEST, entry.current.pullRequestUrl);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (state != null) draft = state.getString(CHECKLIST_DRAFT);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        Ui.edgeToEdge(getWindow(), scroll, 0);
        update(true, "Reading latest saved capture…");

        String pullRequest = getIntent().getStringExtra(PULL_REQUEST);
        worker.execute(() -> load(pullRequest));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (draft != null) state.putString(CHECKLIST_DRAFT, editor == null ? draft : editor.getText().toString());
    }

    private void load(String pullRequest) {
        try {
            ChangeBriefStore.SavedBriefs latest = ChangeBriefStore.find(getApplicationContext(), pullRequest);
            if (latest == null) {
                onUi(() -> update(false, "No saved capture for this pull request."));
                return;
            }
            List<String> checklist = ReleaseReadiness.load(getApplicationContext(), latest.repository());
            onUi(() -> {
                entry = latest;
                required = checklist;
            });
            loadReview(latest);
        } catch (IOException error) {
            onUi(() -> update(false, "The saved capture or checklist could not be read."));
        }
    }

    private void loadReview(ChangeBriefStore.SavedBriefs latest) {
        try {
            ReleaseReview saved = ReleaseReview.load(getApplicationContext(), latest.current.pullRequestUrl);
            onUi(() -> {
                review = saved;
                reviewAvailable = true;
                update(false, "");
            });
        } catch (IOException error) {
            onUi(() -> update(false, ""));
        }
    }

    private void render() {
        page.removeAllViews();
        editor = null;
        TextView brand = Ui.eyebrow(this, "OUTLANDS");
        brand.setTextColor(Ui.YELLOW);
        add(page, brand);
        text(page, "Release Readiness", 28, Ui.WHITE, true);
        button("Back", false, this::finish).setEnabled(!busy);
        if (entry != null) {
            text(page, entry.repository() + " · PR #" + entry.pullRequestNumber(), 15, Ui.CYAN, true);
            if (draft == null) {
                button("Edit checklist", false, () -> {
                    draft = String.join("\n", required);
                    update(false, "");
                    scroll.scrollTo(0, 0);
                }).setEnabled(!busy);
            }
        }
        feedback = text(page, message, 14, Ui.YELLOW, false);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        if (entry == null) return;
        if (draft != null) showEditor();
        else showReadiness();
    }

    private void showEditor() {
        text(page, "Required checks", 19, Ui.WHITE, true);
        text(page, "Enter one exact check name per line. Save an empty list to clear this repository’s checklist.",
                14, Ui.MUTED, false);
        editor = Ui.input(this, "Required check names");
        editor.setContentDescription("Required check names");
        editor.setSingleLine(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setMinLines(3);
        editor.setMaxLines(6);
        editor.setText(draft);
        editor.setEnabled(!busy);
        add(page, editor);
        button("Save checklist", true, this::save).setEnabled(!busy);
        button("Cancel edit", false, this::cancelEdit).setEnabled(!busy);
    }

    private void showReadiness() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(this, 14);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(Ui.panel(this, Ui.PANEL, Ui.BORDER));
        add(page, card);

        ChangeBriefStore.Brief capture = entry.current;
        ReleaseReadiness.Summary summary = ReleaseReadiness.evaluate(required, capture);
        text(card, summary.label(), 21, summary.meetsChecklist() ? Ui.CYAN : Ui.YELLOW, true);
        text(card, "Captured (UTC) · " + capture.capturedAt, 14, Ui.MUTED, false);
        text(card, "Target commit", 15, Ui.WHITE, true);
        TextView commit = text(card, capture.targetCommit, 14, Ui.MUTED, false);
        commit.setTypeface(Typeface.MONOSPACE);
        commit.setTextDirection(View.TEXT_DIRECTION_LTR);
        commit.setTextIsSelectable(true);
        if (!summary.checks.isEmpty()) text(card, "Required checks", 17, Ui.WHITE, true);
        for (ReleaseReadiness.Check check : summary.checks) {
            boolean passed = check.status == ChangeBriefStore.Status.PASSED;
            text(card, check.name + " · " + check.outcome(), 15, passed ? Ui.CYAN : Ui.YELLOW, true);
        }
        showReview(card, capture);
    }

    private void showReview(LinearLayout card, ChangeBriefStore.Brief capture) {
        text(card, "Local review", 17, Ui.WHITE, true);
        if (!reviewAvailable) {
            text(card, "Review notes unavailable", 14, Ui.YELLOW, false);
            return;
        }
        boolean current = review != null && review.isCurrent(capture, required);
        if (review == null) {
            text(card, "Unreviewed", 15, Ui.MUTED, false);
        } else {
            text(card, current ? "Reviewed" : "Outdated", 15, current ? Ui.CYAN : Ui.YELLOW, true);
            text(card, "Reviewed (UTC) · " + review.reviewedAt, 14, Ui.MUTED, false);
            if (!current) text(card, "Reviewed capture (UTC) · " + review.capturedAt, 14, Ui.MUTED, false);
            if (!review.note.isEmpty()) {
                text(card, current ? "Review note" : "Previous review note", 14, Ui.WHITE, true);
                text(card, review.note, 14, Ui.MUTED, false).setTextIsSelectable(true);
            }
        }
        String action = review == null ? "Mark reviewed" : current ? "Update review" : "Review latest capture";
        button(action, false, () -> editReview(current)).setEnabled(!busy);
    }

    private void editReview(boolean current) {
        EditText input = Ui.input(this, "Next action (optional)");
        input.setContentDescription("Next action (optional)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(ReleaseReview.MAX_NOTE)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSaveEnabled(false);
        if (current) input.setText(review.note);
        int pad = Ui.dp(this, 20);
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(pad, 0, pad, pad);
        fields.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Review saved capture")
                .setMessage(ReleaseReadiness.evaluate(required, entry.current).label()
                        + "\nCaptured (UTC) · " + entry.current.capturedAt)
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save review", null)
                .create();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String note;
            try {
                note = ReleaseReview.validateNote(input.getText().toString());
            } catch (IllegalArgumentException invalid) {
                input.setError(invalid.getMessage());
                return;
            }
            dialog.dismiss();
            saveReview(note);
        });
    }

    private void saveReview(String note) {
        ChangeBriefStore.SavedBriefs capture = entry;
        List<String> checklist = required;
        update(true, "Saving review…");
        worker.execute(() -> {
            try {
                ReleaseReview saved = ReleaseReview.save(getApplicationContext(), capture, checklist, note);
                onUi(() -> {
                    review = saved;
                    update(false, "Review saved.");
                });
            } catch (IllegalStateException changed) {
                onUi(() -> update(false, changed.getMessage()));
            } catch (IOException error) {
                onUi(() -> update(false, "The review could not be saved."));
            }
        });
    }

    private void save() {
        draft = editor.getText().toString();
        List<String> parsed;
        try {
            parsed = ReleaseReadiness.parseChecklist(draft);
        } catch (IllegalArgumentException rejected) {
            feedback.setText(rejected.getMessage());
            feedback.setVisibility(View.VISIBLE);
            return;
        }
        hideKeyboard();
        update(true, "Saving checklist…");
        String repository = entry.repository();
        worker.execute(() -> {
            try {
                ReleaseReadiness.save(getApplicationContext(), repository, parsed);
                onUi(() -> {
                    required = parsed;
                    draft = null;
                    update(false, "Checklist saved.");
                    scroll.scrollTo(0, 0);
                });
            } catch (IOException error) {
                onUi(() -> update(false, "The checklist could not be saved."));
            }
        });
    }

    private void cancelEdit() {
        hideKeyboard();
        draft = null;
        update(false, "");
        scroll.scrollTo(0, 0);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(page.getWindowToken(), 0);
    }

    private void update(boolean working, String text) {
        busy = working;
        message = text;
        render();
    }

    private TextView text(LinearLayout group, String value, int size, int color, boolean bold) {
        TextView view = Ui.text(this, value, size, color, bold);
        add(group, view);
        return view;
    }

    private Button button(String label, boolean accent, Runnable action) {
        Button button = Ui.button(this, label, accent);
        button.setOnClickListener(view -> action.run());
        add(page, button);
        return button;
    }

    private void add(LinearLayout group, View child) {
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.bottomMargin = Ui.dp(this, 8);
        group.addView(child, position);
    }

    private void onUi(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    @Override public void onBackPressed() {
        if (busy) return;
        if (draft != null) cancelEdit();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
