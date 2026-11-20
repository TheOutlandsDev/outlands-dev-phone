package dev.outlands.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class ReleaseReview {
    static final int MAX_NOTE = 160;
    private static final String PREFERENCES = "outlands_release_reviews_v1";

    final Instant capturedAt;
    final Instant reviewedAt;
    final String targetCommit;
    final String note;
    private final String checklist;

    private ReleaseReview(Instant capturedAt, Instant reviewedAt, String targetCommit, String checklist, String note) {
        this.capturedAt = capturedAt;
        this.reviewedAt = reviewedAt;
        this.targetCommit = targetCommit;
        this.checklist = checklist;
        this.note = note;
    }

    boolean isCurrent(ChangeBriefStore.Brief capture, List<String> required) {
        return capturedAt.equals(capture.capturedAt) && targetCommit.equals(capture.targetCommit)
                && checklist.equals(checklistKey(required));
    }

    static String validateNote(String raw) {
        if (raw.length() > MAX_NOTE) throw new IllegalArgumentException("Keep the review note to 160 characters.");
        for (int index = 0; index < raw.length();) {
            int character = raw.codePointAt(index);
            int type = Character.getType(character);
            if (Character.isISOControl(character) || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Use a single line for the review note.");
            }
            index += Character.charCount(character);
        }
        return raw.trim();
    }

    static ReleaseReview load(Context context, String pullRequest) throws IOException {
        try {
            String stored = preferences(context).getString(key(pullRequest), null);
            if (stored == null) return null;
            JSONObject record = new JSONObject(stored);
            String target = record.getString("targetCommit");
            if (!target.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IOException("Invalid review commit");
            String checklist = record.getString("checklist");
            if (!checklistKey(ReleaseReadiness.parseChecklist(checklist)).equals(checklist)) {
                throw new IOException("Invalid review checklist");
            }
            return new ReleaseReview(Instant.parse(record.getString("capturedAt")),
                    Instant.parse(record.getString("reviewedAt")), target, checklist,
                    validateNote(record.getString("note")));
        } catch (JSONException | IllegalArgumentException | ClassCastException | DateTimeParseException error) {
            throw new IOException("Saved release review is invalid", error);
        }
    }

    static synchronized ReleaseReview save(Context context, ChangeBriefStore.SavedBriefs displayed,
            List<String> required, String note) throws IOException {
        ChangeBriefStore.Brief capture = displayed.current;
        String pullRequest = capture.pullRequestUrl;
        ReleaseReview review = new ReleaseReview(capture.capturedAt, Instant.now(), capture.targetCommit,
                checklistKey(required), validateNote(note));
        ChangeBriefStore.SavedBriefs latest = ChangeBriefStore.find(context, pullRequest);
        if (latest == null || !review.isCurrent(latest.current, ReleaseReadiness.load(context, latest.repository()))) {
            throw new IllegalStateException("The capture or checklist changed. Reopen it before saving a review.");
        }
        try {
            JSONObject record = new JSONObject()
                    .put("capturedAt", review.capturedAt.toString())
                    .put("reviewedAt", review.reviewedAt.toString())
                    .put("targetCommit", review.targetCommit)
                    .put("checklist", review.checklist)
                    .put("note", review.note);
            if (!preferences(context).edit().putString(key(pullRequest), record.toString()).commit()) {
                throw new IOException("Review could not be saved");
            }
        } catch (JSONException error) {
            throw new IOException("Review could not be written", error);
        }
        return review;
    }

    private static String checklistKey(List<String> required) {
        List<String> sorted = new ArrayList<>(required);
        Collections.sort(sorted);
        return String.join("\n", sorted);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(String pullRequest) {
        return pullRequest.toLowerCase(Locale.ROOT);
    }
}
