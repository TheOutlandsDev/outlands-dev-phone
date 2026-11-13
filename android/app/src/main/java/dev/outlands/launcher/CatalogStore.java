package dev.outlands.launcher;

import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Local, bounded configuration only. No credentials, networking, or remote configuration. */
final class CatalogStore {
    static final int MAX_FILE_BYTES = 256 * 1024;
    static final int MAX_SERVICES = 128;
    private static final String CATALOG_KEY = "service_catalog_v1";
    private static final String APP_PINS_KEY = "app_pins_v1";
    private static final String SERVICE_PINS_KEY = "service_pins_v1";
    private final SharedPreferences preferences;

    static final class Service {
        final String id;
        final String label;
        final String category;
        final String url;
        final String description;

        Service(String id, String label, String category, String url, String description) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.url = url;
            this.description = description;
        }

        Service withUrl(String value) {
            return new Service(id, label, category, value, description);
        }
    }

    CatalogStore(Context context) {
        preferences = context.getSharedPreferences("private_workspace", Context.MODE_PRIVATE);
    }

    List<Service> load() {
        String saved = preferences.getString(CATALOG_KEY, null);
        if (saved != null) {
            try {
                return parse(saved);
            } catch (IllegalArgumentException ignored) {
                // Keep a corrupted local configuration from making the Home activity unusable.
            }
        }
        return defaults();
    }

    void save(List<Service> services) {
        Set<String> availableIds = new HashSet<>();
        for (Service service : services) availableIds.add(service.id);
        List<String> kept = new ArrayList<>();
        for (String id : pinnedServiceIds()) if (availableIds.contains(id)) kept.add(id);
        preferences.edit().putString(CATALOG_KEY, serialize(services))
                .putString(SERVICE_PINS_KEY, encodePins(kept)).apply();
    }

    void reset() {
        preferences.edit().remove(CATALOG_KEY).remove(SERVICE_PINS_KEY).apply();
    }

    List<String> pinnedServiceIds() { return readPins(SERVICE_PINS_KEY, MAX_SERVICES, false); }
    List<String> pinnedApps() { return readPins(APP_PINS_KEY, 64, true); }
    boolean isServicePinned(String id) { return pinnedServiceIds().contains(id); }
    boolean isAppPinned(ComponentName component) { return pinnedApps().contains(component.flattenToString()); }

    void setServicePinned(String id, boolean pinned) {
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) return;
        updatePin(SERVICE_PINS_KEY, pinnedServiceIds(), id, pinned, MAX_SERVICES);
    }

    void setAppPinned(ComponentName component, boolean pinned) {
        updatePin(APP_PINS_KEY, pinnedApps(), component.flattenToString(), pinned, 64);
    }

    private void updatePin(String key, List<String> current, String value, boolean pinned, int maximum) {
        List<String> updated = new ArrayList<>(current);
        if (pinned && !updated.contains(value)) {
            if (updated.size() >= maximum) throw new IllegalArgumentException("Remove a pinned item before adding another.");
            updated.add(value);
        } else if (!pinned) updated.remove(value);
        preferences.edit().putString(key, encodePins(updated)).apply();
    }

    private List<String> readPins(String key, int maximum, boolean components) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String encoded = preferences.getString(key, "[]");
        if (encoded == null || encoded.length() > 32768) return new ArrayList<>();
        try {
            checkNesting(encoded);
            JSONArray entries = new JSONArray(encoded);
            for (int i = 0; i < Math.min(entries.length(), maximum); i++) {
                Object entry = entries.get(i);
                if (!(entry instanceof String)) continue;
                String value = (String) entry;
                boolean valid = components ? value.length() <= 512 && ComponentName.unflattenFromString(value) != null
                        : value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
                if (valid) result.add(value);
            }
        } catch (JSONException | IllegalArgumentException ignored) { }
        return new ArrayList<>(result);
    }

    private static String encodePins(List<String> pins) {
        JSONArray result = new JSONArray();
        for (String value : pins) result.put(value);
        return result.toString();
    }

    boolean hapticsEnabled() {
        return preferences.getBoolean("haptics_enabled", false);
    }

    void setHapticsEnabled(boolean enabled) {
        preferences.edit().putBoolean("haptics_enabled", enabled).apply();
    }

    static List<Service> defaults() {
        List<Service> result = new ArrayList<>();
        result.add(new Service("ai-workbench", "AI Workbench", "Workspace", "", "Your private AI workspace."));
        result.add(new Service("operations", "Operations", "Ops", "", "Your operations dashboard."));
        result.add(new Service("hub", "Hub", "Workspace", "", "Your service directory."));
        result.add(new Service("observability", "Observability", "Ops", "", "Your metrics and logs."));
        result.add(new Service("code", "Code", "Development", "", "Your development workspace."));
        result.add(new Service("chatgpt", "ChatGPT Web", "AI", "https://chatgpt.com", "Open ChatGPT in your browser."));
        return Collections.unmodifiableList(result);
    }

    static List<Service> parse(byte[] bytes) {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Choose a JSON file smaller than 256 KiB.");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return parse(text);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("The configuration must be UTF-8 JSON.");
        }
    }

    static List<Service> parse(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Choose a JSON file smaller than 256 KiB.");
        }
        checkNesting(text);
        try {
            JSONTokener reader = new JSONTokener(text);
            Object document = reader.nextValue();
            if (!(document instanceof JSONObject) || reader.nextClean() != 0) {
                throw new IllegalArgumentException("The file must contain one JSON object.");
            }
            JSONObject root = (JSONObject) document;
            Object version = root.get("version");
            if (!(version instanceof Number) || ((Number) version).doubleValue() != 1.0) {
                throw new IllegalArgumentException("Only configuration version 1 is supported.");
            }
            JSONArray entries = root.getJSONArray("services");
            if (entries.length() > MAX_SERVICES) {
                throw new IllegalArgumentException("A workspace can contain at most 128 services.");
            }
            Set<String> ids = new HashSet<>();
            List<Service> services = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String id = field(entry, "id", 64, false);
                if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || !ids.add(id)) {
                    throw new IllegalArgumentException("Each service needs a unique simple id (entry " + (i + 1) + ").");
                }
                String label = field(entry, "label", 80, false);
                String category = field(entry, "category", 40, false);
                String description = entry.has("description") ? field(entry, "description", 180, true) : "";
                String url = validateUrl(field(entry, "url", 2048, true));
                services.add(new Service(id, label, category, url, description));
            }
            return Collections.unmodifiableList(services);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Check the JSON structure and required service fields.");
        }
    }

    static String validateUrl(String input) {
        String value = input.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() > 2048 || containsControl(value)) {
            throw new IllegalArgumentException("Use an HTTPS address of at most 2048 characters.");
        }
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isEmpty() || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.isOpaque()) {
                throw new IllegalArgumentException("Use an HTTPS address with a hostname and no embedded login.");
            }
            // Fragments are valid service anchors. Common credential-bearing query keys are not.
            String query = uri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String key = URLDecoder.decode(part.split("=", 2)[0], "UTF-8")
                            .toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                    if (key.equals("token") || key.equals("accesstoken") || key.equals("refreshtoken")
                            || key.equals("apikey") || key.equals("password") || key.equals("passwd")
                            || key.equals("secret") || key.equals("clientsecret")
                            || key.equals("authorization") || key.equals("signature")
                            || key.startsWith("xamz")) {
                        throw new IllegalArgumentException("Use a service address without credentials or signed-link tokens.");
                    }
                }
            }
            return uri.toASCIIString();
        } catch (URISyntaxException | java.io.UnsupportedEncodingException error) {
            throw new IllegalArgumentException("Enter a valid HTTPS address.");
        }
    }

    static String serialize(List<Service> services) {
        try {
            JSONArray entries = new JSONArray();
            for (Service service : services) {
                JSONObject entry = new JSONObject();
                entry.put("id", service.id);
                entry.put("label", service.label);
                entry.put("category", service.category);
                entry.put("url", service.url);
                entry.put("description", service.description);
                entries.put(entry);
            }
            return new JSONObject().put("version", 1).put("services", entries).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException("Could not encode workspace configuration.", impossible);
        }
    }

    private static String field(JSONObject object, String key, int max, boolean allowEmpty) throws JSONException {
        Object raw = object.get(key);
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException("Service fields must be text.");
        }
        String value = ((String) raw).trim();
        if ((!allowEmpty && value.isEmpty()) || value.length() > max || containsControl(value)) {
            throw new IllegalArgumentException("Check service field lengths and remove control characters.");
        }
        return value;
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void checkNesting(String text) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
            } else if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                if (++depth > 12) {
                    throw new IllegalArgumentException("The JSON configuration is nested too deeply.");
                }
            } else if (current == '}' || current == ']') {
                depth--;
            }
        }
    }
}
