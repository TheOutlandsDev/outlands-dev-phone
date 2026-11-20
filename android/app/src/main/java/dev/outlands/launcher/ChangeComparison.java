package dev.outlands.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChangeComparison {
    private static final char BACKTICK = '\u0060';

    final ChangeBriefStore.Brief previous;
    final ChangeBriefStore.Brief current;
    final List<CheckChange> checks;
    final List<String> addedPaths;
    final List<String> removedPaths;

    static final class CheckChange {
        final String name;
        final ChangeBriefStore.Status before;
        final ChangeBriefStore.Status after;

        private CheckChange(String name, ChangeBriefStore.Status before, ChangeBriefStore.Status after) {
            this.name = name;
            this.before = before;
            this.after = after;
        }

        String transition() {
            return label(before) + " → " + label(after);
        }

        private String label(ChangeBriefStore.Status status) {
            return status == null ? "Not recorded" : status.label;
        }
    }

    ChangeComparison(ChangeBriefStore.SavedBriefs pair) {
        previous = pair.previous;
        current = pair.current;
        Map<String, ChangeBriefStore.Status> earlier = states(previous);
        Map<String, ChangeBriefStore.Status> later = states(current);
        Set<String> names = new LinkedHashSet<>(earlier.keySet());
        names.addAll(later.keySet());
        List<CheckChange> changes = new ArrayList<>();
        for (String name : names) {
            ChangeBriefStore.Status before = earlier.get(name);
            ChangeBriefStore.Status after = later.get(name);
            if (before != after) changes.add(new CheckChange(name, before, after));
        }
        checks = Collections.unmodifiableList(changes);
        addedPaths = difference(current.changedPaths, previous.changedPaths);
        removedPaths = difference(previous.changedPaths, current.changedPaths);
    }

    private static Map<String, ChangeBriefStore.Status> states(ChangeBriefStore.Brief brief) {
        Map<String, ChangeBriefStore.Status> state = new LinkedHashMap<>();
        for (ChangeBriefStore.Check check : brief.checks) state.put(check.name, check.status);
        return state;
    }

    private static List<String> difference(List<String> from, List<String> other) {
        List<String> result = new ArrayList<>(from);
        result.removeAll(other);
        return Collections.unmodifiableList(result);
    }

    String markdown(boolean includeNames, boolean includePaths, boolean includeCommits,
            ReleaseReadiness.Summary readiness) {
        int regressions = 0;
        int recoveries = 0;
        for (CheckChange change : checks) {
            if (change.before == ChangeBriefStore.Status.PASSED && change.after == ChangeBriefStore.Status.FAILED) {
                regressions++;
            }
            if (change.before == ChangeBriefStore.Status.FAILED && change.after == ChangeBriefStore.Status.PASSED) {
                recoveries++;
            }
        }
        int pending = 0;
        int failed = 0;
        for (ChangeBriefStore.Check check : current.checks) {
            if (check.status == ChangeBriefStore.Status.NOT_RUN) pending++;
            if (check.status == ChangeBriefStore.Status.FAILED) failed++;
        }
        StringBuilder report = new StringBuilder("# Release handoff\n\n");
        bullet(report, "Previous capture (UTC)", previous.capturedAt);
        bullet(report, "Current capture (UTC)", current.capturedAt);
        report.append("\n## Recorded checks\n\n");
        bullet(report, "Regressions (Passed → Failed)", regressions);
        bullet(report, "Recoveries (Failed → Passed)", recoveries);
        bullet(report, "Pending checks (current Not run)", pending);
        bullet(report, "Failed checks (current)", failed);
        if (includeNames && !checks.isEmpty()) {
            report.append("\n### Check changes\n\n");
            for (CheckChange check : checks) bullet(report, literal(check.name), check.transition());
        }
        if (readiness != null) {
            report.append("\n## Local release readiness\n\n");
            bullet(report, "Status", readiness.label());
            if (!readiness.checks.isEmpty()) {
                bullet(report, "Required checks", readiness.passed + " passed, " + readiness.failed + " failed, "
                        + readiness.notRun + " not run, " + readiness.notRecorded + " not recorded");
            }
        }

        report.append("\n## Changed-path lists\n\n");
        bullet(report, "Paths added to list", addedPaths.size());
        bullet(report, "Paths removed from list", removedPaths.size());
        if (includePaths) {
            appendPaths(report, "Added to list", addedPaths);
            appendPaths(report, "Removed from list", removedPaths);
        }
        if (includeCommits) {
            report.append("\n## Commit IDs\n\n");
            bullet(report, "Previous base", literal(previous.baseCommit));
            bullet(report, "Previous target", literal(previous.targetCommit));
            bullet(report, "Current base", literal(current.baseCommit));
            bullet(report, "Current target", literal(current.targetCommit));
        }
        return report.toString();
    }

    private static void appendPaths(StringBuilder report, String label, List<String> paths) {
        if (paths.isEmpty()) return;
        report.append("\n### ").append(label).append("\n\n");
        for (String path : paths) report.append("- ").append(literal(path)).append('\n');
    }

    private static void bullet(StringBuilder report, String label, Object value) {
        report.append("- ").append(label).append(" · ").append(value).append('\n');
    }

    private static String literal(String value) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            run = value.charAt(i) == BACKTICK ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        StringBuilder delimiter = new StringBuilder();
        for (int i = 0; i <= longest; i++) delimiter.append(BACKTICK);
        return delimiter + " " + value + " " + delimiter;
    }
}
