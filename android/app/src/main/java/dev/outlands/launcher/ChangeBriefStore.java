package dev.outlands.launcher;

import android.content.Context;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ChangeBriefStore {
    private static final int MAX_BYTES = 32 * 1024;
    private static final int MAX_PATHS = 200;
    static final int MAX_CHECKS = 50;
    static final int MAX_CHECK_NAME = 100;
    private static final String LEGACY_FILE = "change-brief-v1.json";
    private static final String LEGACY_PAIR = "change-brief-pair-v1";
    private static final String DIRECTORY = "change-library-v1";
    private static final Pattern RECORD_NAME = Pattern.compile("(?:standalone|pr-[a-f0-9]{64})\\.bin");
    private static final Pattern COMMIT = Pattern.compile("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})");
    private static final Pattern UTC = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,9})?Z");
    private static final Pattern GITHUB_PR = Pattern.compile(
            "https://github\\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}/pull/[1-9][0-9]{0,11}");
    private static final Object LOCK = new Object();

    private ChangeBriefStore() { }

    enum Status {
        PASSED("passed", "Passed"),
        FAILED("failed", "Failed"),
        NOT_RUN("not_run", "Not run");

        final String value;
        final String label;

        Status(String value, String label) {
            this.value = value;
            this.label = label;
        }

        static Status parse(String value) throws InvalidBrief {
            for (Status status : values()) if (status.value.equals(value)) return status;
            throw invalid();
        }
    }

    static final class Check {
        final String name;
        final Status status;

        private Check(String name, Status status) {
            this.name = name;
            this.status = status;
        }
    }

    static final class Brief {
        final String title;
        final Instant capturedAt;
        final String baseCommit;
        final String targetCommit;
        final List<String> changedPaths;
        final List<Check> checks;
        final String pullRequestUrl;
        private final byte[] document;

        private Brief(String title, Instant capturedAt, String baseCommit, String targetCommit,
                List<String> changedPaths, List<Check> checks, String pullRequestUrl, byte[] document) {
            this.title = title;
            this.capturedAt = capturedAt;
            this.baseCommit = baseCommit;
            this.targetCommit = targetCommit;
            this.changedPaths = Collections.unmodifiableList(changedPaths);
            this.checks = Collections.unmodifiableList(checks);
            this.pullRequestUrl = pullRequestUrl;
            this.document = document;
        }
    }

    static final class InvalidBrief extends IOException {
        private InvalidBrief(String message) {
            super(message);
        }
    }

    static final class SavedBriefs {
        final Brief current;
        final Brief previous;

        private SavedBriefs(Brief current, Brief previous) {
            this.current = current;
            this.previous = previous;
        }

        String pullRequestNumber() {
            String url = current.pullRequestUrl;
            return url == null ? "" : url.substring(url.lastIndexOf('/') + 1);
        }

        String repository() {
            String url = current.pullRequestUrl;
            return url == null ? "" : url.substring("https://github.com/".length(), url.lastIndexOf("/pull/"));
        }
    }

    static boolean samePullRequest(Brief first, Brief second) {
        return first != null && second != null && first.pullRequestUrl != null
                && first.pullRequestUrl.equalsIgnoreCase(second.pullRequestUrl);
    }

    static List<SavedBriefs> load(Context context) throws IOException {
        synchronized (LOCK) {
            return sorted(loadLibrary(context));
        }
    }

    static SavedBriefs find(Context context, String pullRequestUrl) throws IOException {
        if (pullRequestUrl == null) return null;
        for (SavedBriefs entry : load(context)) {
            if (pullRequestUrl.equalsIgnoreCase(entry.current.pullRequestUrl)) return entry;
        }
        return null;
    }

    private static Map<String, SavedBriefs> loadLibrary(Context context) throws IOException {
        Map<String, SavedBriefs> entries = new HashMap<>();
        File directory = new File(privateDirectory(context), DIRECTORY);
        if (directory.exists()) {
            String[] files = directory.list();
            if (files == null) throw new IOException("Saved change library is unavailable");
            for (String filename : files) {
                String name = filename.endsWith(".bak") ? filename.substring(0, filename.length() - 4) : filename;
                if (!RECORD_NAME.matcher(name).matches() || entries.containsKey(name)) continue;
                SavedBriefs entry = loadPair(new AtomicFile(new File(directory, name)));
                if (entry == null || !recordName(entry.current).equals(name)) {
                    throw new IOException("Saved change library record is invalid");
                }
                entries.put(name, entry);
            }
        }
        SavedBriefs legacy = loadLegacy(context);
        if (legacy != null) entries.putIfAbsent(recordName(legacy.current), legacy);
        return entries;
    }

    private static List<SavedBriefs> sorted(Map<String, SavedBriefs> entries) {
        List<SavedBriefs> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparing((SavedBriefs entry) -> entry.current.capturedAt).reversed()
                .thenComparing(entry -> entry.current.title)
                .thenComparing(entry -> entry.current.pullRequestUrl, Comparator.nullsFirst(Comparator.naturalOrder())));
        return result;
    }

    private static SavedBriefs loadLegacy(Context context) throws IOException {
        File directory = privateDirectory(context);
        SavedBriefs oldPair = loadPair(new AtomicFile(new File(directory, LEGACY_PAIR)));
        if (oldPair != null) return oldPair;
        try (FileInputStream legacy = openIfPresent(new AtomicFile(new File(directory, LEGACY_FILE)))) {
            return legacy == null ? null : new SavedBriefs(read(legacy), null);
        }
    }

    private static SavedBriefs loadPair(AtomicFile file) throws IOException {
        try (FileInputStream input = openIfPresent(file)) {
            if (input != null) {
                DataInputStream stream = new DataInputStream(input);
                Brief current = readRecord(stream, false);
                Brief previous = readRecord(stream, true);
                if (stream.read() != -1 || (previous != null && (!samePullRequest(current, previous)
                        || !current.capturedAt.isAfter(previous.capturedAt)))) {
                    throw new IOException("Saved change brief pair is invalid");
                }
                return new SavedBriefs(current, previous);
            }
        }
        return null;
    }

    private static FileInputStream openIfPresent(AtomicFile file) throws IOException {
        try {
            return file.openRead();
        } catch (FileNotFoundException absent) {
            File base = file.getBaseFile();
            if (!base.exists() && !new File(base.getPath() + ".bak").exists()) return null;
            throw absent;
        }
    }

    private static Brief readRecord(DataInputStream input, boolean optional) throws IOException {
        int length = input.readInt();
        if (length == 0 && optional) return null;
        if (length <= 0 || length > MAX_BYTES) throw new IOException("Saved change brief length is invalid");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return read(new ByteArrayInputStream(bytes));
    }

    static List<SavedBriefs> save(Context context, Brief brief) throws IOException {
        synchronized (LOCK) {
            Map<String, SavedBriefs> entries = loadLibrary(context);
            String name = recordName(brief);
            SavedBriefs before = entries.get(name);
            SavedBriefs after = addBrief(before, brief);
            if (after == before) return sorted(entries);
            File directory = new File(privateDirectory(context), DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdir() && !directory.isDirectory()) {
                throw new IOException("Saved change library is unavailable");
            }
            AtomicFile saved = new AtomicFile(new File(directory, name));
            FileOutputStream output = saved.startWrite();
            try {
                DataOutputStream stream = new DataOutputStream(output);
                writeRecord(stream, after.current);
                writeRecord(stream, after.previous);
                saved.finishWrite(output);
            } catch (IOException failure) {
                saved.failWrite(output);
                throw failure;
            }
            entries.put(name, after);
            return sorted(entries);
        }
    }

    private static String recordName(Brief brief) {
        if (brief.pullRequestUrl == null) return "standalone.bin";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(brief.pullRequestUrl.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder("pr-");
            for (byte value : digest) {
                name.append(Character.forDigit((value & 0xff) >>> 4, 16));
                name.append(Character.forDigit(value & 0x0f, 16));
            }
            return name.append(".bin").toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static SavedBriefs addBrief(SavedBriefs saved, Brief brief) throws InvalidBrief {
        if (saved == null || !samePullRequest(saved.current, brief)) return new SavedBriefs(brief, null);

        if (sameCapture(brief, saved.current) || sameCapture(brief, saved.previous)) return saved;
        if (brief.capturedAt.isAfter(saved.current.capturedAt)) return new SavedBriefs(brief, saved.current);
        if (saved.previous == null || brief.capturedAt.isAfter(saved.previous.capturedAt)) {
            return new SavedBriefs(saved.current, brief);
        }
        throw new InvalidBrief("This capture is older than both saved briefs.");
    }

    private static boolean sameCapture(Brief incoming, Brief saved) throws InvalidBrief {
        if (saved == null || !incoming.capturedAt.equals(saved.capturedAt)) return false;
        if (Arrays.equals(incoming.document, saved.document)) return true;
        throw new InvalidBrief("A different brief for this pull request already has that capture time.");
    }

    private static void writeRecord(DataOutputStream stream, Brief brief) throws IOException {
        stream.writeInt(brief == null ? 0 : brief.document.length);
        if (brief != null) stream.write(brief.document);
    }

    private static File privateDirectory(Context context) throws IOException {
        File directory = context.getNoBackupFilesDir();
        if (directory == null || !directory.isDirectory()) throw new IOException("Brief storage unavailable");
        return directory;
    }

    static Brief read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - output.size()) throw new InvalidBrief("The brief exceeds 32 KiB.");
            output.write(buffer, 0, count);
        }
        byte[] document = output.toByteArray();
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(document)).toString();
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                Brief brief = parse(reader, document);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
                return brief;
            }
        } catch (InvalidBrief error) {
            throw error;
        } catch (IOException | IllegalStateException | DateTimeParseException error) {
            throw invalid();
        }
    }

    private static Brief parse(JsonReader reader, byte[] document) throws IOException {
        Set<String> seen = new HashSet<>();
        String title = null;
        Instant capturedAt = null;
        String base = null;
        String target = null;
        String link = null;
        List<String> paths = null;
        List<Check> checks = null;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (uniqueName(reader, seen)) {
                case "version":
                    if (reader.peek() != JsonToken.NUMBER || !"1".equals(reader.nextString())) {
                        throw new InvalidBrief("This change brief version is unsupported.");
                    }
                    break;
                case "title": title = text(reader, 120); break;
                case "capturedAtUtc":
                    String time = text(reader, 30);
                    if (!UTC.matcher(time).matches()) throw invalid();
                    capturedAt = Instant.parse(time);
                    break;
                case "baseCommit": base = commit(reader); break;
                case "targetCommit": target = commit(reader); break;
                case "changedPaths": paths = paths(reader); break;
                case "checks": checks = checks(reader); break;
                case "pullRequestUrl":
                    link = text(reader, 220);
                    if (!GITHUB_PR.matcher(link).matches() || link.contains("/./") || link.contains("/../")) {
                        throw new InvalidBrief("Use a GitHub pull request permalink.");
                    }
                    break;
                default: throw invalid();
            }
        }
        reader.endObject();
        if (!seen.contains("version") || title == null || capturedAt == null || base == null || target == null
                || base.length() != target.length() || paths == null || checks == null) throw invalid();
        return new Brief(title, capturedAt, base, target, paths, checks, link, document);
    }

    private static List<String> paths(JsonReader reader) throws IOException {
        List<String> paths = new ArrayList<>();
        Set<String> names = new HashSet<>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (paths.size() >= MAX_PATHS) throw invalid();
            String value = text(reader, 240);
            if (value.startsWith("/") || value.indexOf('\\') >= 0 || value.matches("^[A-Za-z]:.*")
                    || !names.add(value)) throw invalid();
            for (String part : value.split("/", -1)) {
                if (part.isEmpty() || ".".equals(part) || "..".equals(part)) throw invalid();
            }
            paths.add(value);
        }
        reader.endArray();
        return paths;
    }

    private static List<Check> checks(JsonReader reader) throws IOException {
        List<Check> checks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (checks.size() >= MAX_CHECKS) throw invalid();
            Set<String> fields = new HashSet<>();
            String name = null;
            Status status = null;
            reader.beginObject();
            while (reader.hasNext()) {
                switch (uniqueName(reader, fields)) {
                    case "name": name = text(reader, MAX_CHECK_NAME); break;
                    case "status": status = Status.parse(text(reader, 12)); break;
                    default: throw invalid();
                }
            }
            reader.endObject();
            if (name == null || status == null || !names.add(name)) throw invalid();
            checks.add(new Check(name, status));
        }
        reader.endArray();
        return checks;
    }

    private static String uniqueName(JsonReader reader, Set<String> seen) throws IOException {
        String name = reader.nextName();
        if (!seen.add(name)) throw invalid();
        return name;
    }

    private static String commit(JsonReader reader) throws IOException {
        String value = text(reader, 64);
        if (!COMMIT.matcher(value).matches()) throw invalid();
        return value.toLowerCase(Locale.ROOT);
    }

    private static String text(JsonReader reader, int max) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw invalid();
        String value = reader.nextString();
        if (!validText(value, max)) throw invalid();
        return value;
    }

    static boolean validText(String value, int max) {
        return value.length() <= max && !value.trim().isEmpty()
                && value.codePoints().noneMatch(ch -> Character.isISOControl(ch)
                        || Character.getType(ch) == Character.FORMAT
                        || Character.getType(ch) == Character.SURROGATE
                        || Character.getType(ch) == Character.LINE_SEPARATOR
                        || Character.getType(ch) == Character.PARAGRAPH_SEPARATOR);
    }

    private static InvalidBrief invalid() {
        return new InvalidBrief("This file does not match the Change Briefing format.");
    }
}
