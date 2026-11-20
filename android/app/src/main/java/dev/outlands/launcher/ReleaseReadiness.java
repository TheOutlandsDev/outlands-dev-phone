package dev.outlands.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ReleaseReadiness {
    private static final String PREFERENCES = "outlands_release_checklists_v1";

    static final class Check {
        final String name;
        final ChangeBriefStore.Status status;

        private Check(String name, ChangeBriefStore.Status status) {
            this.name = name;
            this.status = status;
        }

        String outcome() {
            return status == null ? "Not recorded" : status.label;
        }
    }

    static final class Summary {
        final List<Check> checks;
        final int passed;
        final int failed;
        final int notRun;
        final int notRecorded;

        private Summary(List<Check> checks) {
            this.checks = Collections.unmodifiableList(checks);
            int passedCount = 0;
            int failedCount = 0;
            int unrunCount = 0;
            int missingCount = 0;
            for (Check check : checks) {
                if (check.status == ChangeBriefStore.Status.PASSED) passedCount++;
                else if (check.status == ChangeBriefStore.Status.FAILED) failedCount++;
                else if (check.status == ChangeBriefStore.Status.NOT_RUN) unrunCount++;
                else missingCount++;
            }
            passed = passedCount;
            failed = failedCount;
            notRun = unrunCount;
            notRecorded = missingCount;
        }

        boolean meetsChecklist() {
            return !checks.isEmpty() && passed == checks.size();
        }

        boolean needsReview() {
            return !checks.isEmpty() && !meetsChecklist();
        }

        String label() {
            if (checks.isEmpty()) return "No checklist configured";
            return meetsChecklist() ? "Meets local checklist" : "Needs review";
        }
    }

    private ReleaseReadiness() { }

    static List<String> parseChecklist(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : text.split("\\r\\n|\\r|\\n", -1)) {
            if (line.trim().isEmpty()) continue;
            if (!ChangeBriefStore.validText(line, ChangeBriefStore.MAX_CHECK_NAME)) {
                throw new IllegalArgumentException("Each check needs a displayable name of up to "
                        + ChangeBriefStore.MAX_CHECK_NAME + " characters.");
            }
            if (!names.add(line)) throw new IllegalArgumentException("Use each required check name once.");
            if (names.size() > ChangeBriefStore.MAX_CHECKS) {
                throw new IllegalArgumentException("A checklist can have up to " + ChangeBriefStore.MAX_CHECKS
                        + " required checks.");
            }
        }
        return new ArrayList<>(names);
    }

    static List<String> load(Context context, String repository) throws IOException {
        try {
            return parseChecklist(preferences(context).getString(key(repository), ""));
        } catch (IllegalArgumentException | ClassCastException error) {
            throw new IOException("Saved checklist is invalid", error);
        }
    }

    static void save(Context context, String repository, List<String> names) throws IOException {
        SharedPreferences.Editor editor = preferences(context).edit();
        if (names.isEmpty()) editor.remove(key(repository));
        else editor.putString(key(repository), String.join("\n", names));
        if (!editor.commit()) throw new IOException("Checklist could not be saved");
    }

    static Summary evaluate(List<String> required, ChangeBriefStore.Brief latest) {
        Map<String, ChangeBriefStore.Status> recorded = new HashMap<>();
        for (ChangeBriefStore.Check check : latest.checks) recorded.put(check.name, check.status);
        List<Check> result = new ArrayList<>();
        for (String name : required) result.add(new Check(name, recorded.get(name)));
        return new Summary(result);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(String repository) {
        return repository.toLowerCase(Locale.ROOT);
    }
}
