package dev.outlands.launcher;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReadinessQueueActivity extends Activity {
    private static final String REVIEW_ONLY = "reviewOnly";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Entry> entries = Collections.emptyList();
    private QueueAdapter rows;
    private CheckBox reviewOnly;
    private TextView message;
    private Button retry;
    private int refreshNumber;
    private boolean loading;
    private boolean failed;

    private static final class Entry {
        final ChangeBriefStore.SavedBriefs capture;
        final ReleaseReadiness.Summary summary;
        final String reviewStatus;

        Entry(ChangeBriefStore.SavedBriefs capture, ReleaseReadiness.Summary summary, String reviewStatus) {
            this.capture = capture;
            this.summary = summary;
            this.reviewStatus = reviewStatus;
        }

        boolean needsReview() {
            return summary != null && summary.needsReview();
        }

        String issues() {
            if (!needsReview()) return "";
            List<String> lines = new ArrayList<>();
            for (ReleaseReadiness.Check check : summary.checks) {
                if (check.status != ChangeBriefStore.Status.PASSED) {
                    lines.add(check.name + " · " + check.outcome());
                }
            }
            return String.join("\n", lines);
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BACKGROUND);

        TextView brand = Ui.eyebrow(this, "OUTLANDS");
        brand.setTextColor(Ui.YELLOW);
        add(page, brand);
        add(page, Ui.text(this, "Readiness Queue", 28, Ui.WHITE, true));
        Button back = Ui.button(this, "Back", false);
        back.setOnClickListener(view -> finish());
        add(page, back);

        reviewOnly = new CheckBox(this);
        reviewOnly.setText("Needs review only");
        reviewOnly.setTextColor(Ui.WHITE);
        reviewOnly.setButtonTintList(ColorStateList.valueOf(Ui.CYAN));
        reviewOnly.setMinHeight(Ui.dp(this, 48));
        reviewOnly.setChecked(state != null && state.getBoolean(REVIEW_ONLY));
        reviewOnly.setOnCheckedChangeListener((view, checked) -> render());
        add(page, reviewOnly);
        message = Ui.text(this, "", 14, Ui.MUTED, false);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(page, message);
        retry = Ui.button(this, "Retry", false);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(view -> refresh());
        add(page, retry);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Ui.BACKGROUND));
        list.setDividerHeight(Ui.dp(this, 8));
        rows = new QueueAdapter();
        list.setAdapter(rows);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(ReleaseReadinessActivity.open(this, rows.getItem(position).capture)));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(page);
        Ui.edgeToEdge(getWindow(), page, Ui.dp(this, 16));
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(REVIEW_ONLY, reviewOnly.isChecked());
    }

    private void refresh() {
        int request = ++refreshNumber;
        loading = true;
        failed = false;
        render();
        worker.execute(() -> {
            try {
                loaded(request, loadEntries(), false);
            } catch (IOException error) {
                loaded(request, Collections.emptyList(), true);
            }
        });
    }

    private List<Entry> loadEntries() throws IOException {
        List<Entry> result = new ArrayList<>();
        Map<String, List<String>> checklists = new HashMap<>();
        for (ChangeBriefStore.SavedBriefs capture : ChangeBriefStore.load(getApplicationContext())) {
            if (capture.current.pullRequestUrl == null) continue;
            String repository = capture.repository();
            String key = repository.toLowerCase(Locale.ROOT);
            if (!checklists.containsKey(key)) {
                checklists.put(key, loadChecklist(repository));
            }
            List<String> required = checklists.get(key);
            ReleaseReadiness.Summary readiness = required == null ? null
                    : ReleaseReadiness.evaluate(required, capture.current);
            result.add(new Entry(capture, readiness, localReviewStatus(capture.current, required)));
        }
        return result;
    }

    private String localReviewStatus(ChangeBriefStore.Brief capture, List<String> required) {
        try {
            ReleaseReview review = ReleaseReview.load(getApplicationContext(), capture.pullRequestUrl);
            if (review == null) return "";
            if (required == null) return "Review status unavailable";
            String status = review.isCurrent(capture, required) ? "Reviewed" : "Outdated review";
            return status + " (UTC) · " + review.reviewedAt;
        } catch (IOException error) {
            return "Review notes unavailable";
        }
    }

    private List<String> loadChecklist(String repository) {
        try {
            return ReleaseReadiness.load(getApplicationContext(), repository);
        } catch (IOException error) {
            return null;
        }
    }

    private void loaded(int request, List<Entry> result, boolean unavailable) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || request != refreshNumber) return;
            entries = result;
            loading = false;
            failed = unavailable;
            render();
        });
    }

    private void render() {
        boolean showReady = !loading && !failed;
        reviewOnly.setEnabled(showReady);
        retry.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (!showReady) {
            rows.submit(Collections.emptyList());
            message.setText(loading ? "Reading saved captures…" : "The saved change library could not be read.");
            return;
        }
        List<Entry> visible = new ArrayList<>();
        for (Entry item : entries) {
            if (!reviewOnly.isChecked() || item.needsReview()) visible.add(item);
        }
        rows.submit(visible);
        int count = visible.size();
        if (reviewOnly.isChecked()) {
            String reviewCount = count == 1 ? "1 PR needs review" : count + " PRs need review";
            message.setText(count == 0 ? "No PRs need review" : reviewCount);
        } else {
            message.setText(count == 0 ? "No saved PR captures" : count + (count == 1 ? " saved PR" : " saved PRs"));
        }
    }

    private void add(LinearLayout page, View child) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = Ui.dp(this, 8);
        page.addView(child, layout);
    }

    private final class QueueAdapter extends BaseAdapter {
        private List<Entry> items = Collections.emptyList();

        void submit(List<Entry> visible) {
            items = visible;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Entry getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            QueueRow row = recycled == null ? new QueueRow() : (QueueRow) recycled;
            row.bind(getItem(position));
            return row;
        }
    }

    private final class QueueRow extends LinearLayout {
        private final TextView title;
        private final TextView identity;
        private final TextView status;
        private final TextView time;
        private final TextView issues;
        private final TextView review;

        QueueRow() {
            super(ReadinessQueueActivity.this);
            setOrientation(VERTICAL);
            int pad = Ui.dp(ReadinessQueueActivity.this, 12);
            setPadding(pad, pad, pad, pad);
            setBackground(Ui.panel(ReadinessQueueActivity.this, Ui.PANEL, Ui.BORDER));
            title = line(17, Ui.WHITE, true);
            identity = line(13, Ui.CYAN, false);
            status = line(15, Ui.WHITE, true);
            time = line(13, Ui.MUTED, false);
            issues = line(13, Ui.MUTED, false);
            issues.setMaxLines(3);
            issues.setEllipsize(TextUtils.TruncateAt.END);
            review = line(13, Ui.MUTED, false);
        }

        private TextView line(int size, int color, boolean bold) {
            TextView view = Ui.text(ReadinessQueueActivity.this, "", size, color, bold);
            addView(view, new LinearLayout.LayoutParams(-1, -2));
            return view;
        }

        void bind(Entry item) {
            ChangeBriefStore.SavedBriefs capture = item.capture;
            title.setText(capture.current.title);
            identity.setText(capture.repository() + " · PR #" + capture.pullRequestNumber());
            status.setText(item.summary == null ? "Checklist unavailable" : item.summary.label());
            int tone = item.needsReview() ? Ui.YELLOW : Ui.MUTED;
            if (item.summary != null && item.summary.meetsChecklist()) tone = Ui.CYAN;
            status.setTextColor(tone);
            time.setText("Captured (UTC) · " + capture.current.capturedAt);
            String detail = item.issues();
            issues.setText(detail);
            issues.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
            review.setText(item.reviewStatus);
            review.setVisibility(item.reviewStatus.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
