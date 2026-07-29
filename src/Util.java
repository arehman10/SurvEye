import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigInteger;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Util {
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern SUBSTITUTION = Pattern.compile("%[^%\\s]{1,80}%");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^\\)]*\\)");
    private static final Pattern HTML_ENTITY = Pattern.compile("&(#x?[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]{1,31});");
    private static final Pattern CALCULATED_VARIABLE_LABEL = Pattern.compile(
            "^calculated\\s+variable\\s+of\\s+type\\s*:?\\s*"
                    + "(?:boolean|bool|date(?:time)?|decimal|double|integer|long|numeric|single|string|text)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPECIAL = Pattern.compile(
            "don't know|dont know|do not know|refus|not applicable|n/?a\\b|missing|unknown|no answer|not stated|не знаю|отказ|no sabe|rechaz|ne sait pas|refusé|不知道|拒绝",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Map<String, String> ENTITIES = new LinkedHashMap<String, String>();

    static {
        String[][] pairs = new String[][]{
                {"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"},
                {"nbsp", " "}, {"mdash", "—"}, {"ndash", "–"}, {"hellip", "…"},
                {"middot", "·"}, {"rsquo", "’"}, {"lsquo", "‘"}, {"ldquo", "“"},
                {"rdquo", "”"}, {"bull", "•"}, {"plusmn", "±"}, {"times", "×"},
                {"divide", "÷"}, {"deg", "°"}, {"copy", "©"}, {"reg", "®"},
                {"trade", "™"}, {"euro", "€"}, {"pound", "£"}, {"laquo", "«"},
                {"raquo", "»"}, {"frac12", "½"}, {"frac14", "¼"}, {"sup2", "²"}
        };
        for (String[] pair : pairs) ENTITIES.put(pair[0], pair[1]);
    }

    private Util() {}

    static String readUtf8(String filename) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(filename));
        int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    static String readResource(String name) throws IOException {
        InputStream in = Util.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Bundled resource is missing: " + name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    static String resourceBase64(String name) throws IOException {
        InputStream in = Util.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Bundled resource is missing: " + name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } finally {
            in.close();
        }
    }

    static String cleanText(String input) {
        if (input == null) return "";
        String value = unescape(input).replace('\u00a0', ' ');
        value = value.replace("%lastmonth%", "last month").replace("%thismonth%", "this month");
        value = SUBSTITUTION.matcher(value).replaceAll("…");
        value = MARKDOWN_LINK.matcher(value).replaceAll("$1");
        value = value.replace("***", "").replace("**", "").replace("__", "");
        return WS.matcher(value).replaceAll(" ").trim();
    }

    static String unescape(String input) {
        if (input == null || input.indexOf('&') < 0) return input == null ? "" : input;
        Matcher matcher = HTML_ENTITY.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = null;
            try {
                if (token.charAt(0) == '#') {
                    int value = token.length() > 2 && (token.charAt(1) == 'x' || token.charAt(1) == 'X')
                            ? Integer.parseInt(token.substring(2), 16) : Integer.parseInt(token.substring(1));
                    replacement = new String(Character.toChars(value));
                } else {
                    replacement = ENTITIES.get(token.toLowerCase(Locale.ROOT));
                }
            } catch (RuntimeException ignored) {
                replacement = null;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? matcher.group(0) : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static boolean isSpecialLabel(String label) {
        return label != null && SPECIAL.matcher(label).find();
    }

    static String normalizeKey(String value) {
        if (value == null) return "";
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    static String html(Object value) {
        String input = String.valueOf(value == null ? "" : value);
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    static String json(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value.toString()); return; }
        if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                if (!first) out.append(','); first = false;
                appendJsonString(out, String.valueOf(entry.getKey())); out.append(':'); appendJson(out, entry.getValue());
            }
            out.append('}'); return;
        }
        if (value instanceof Collection) {
            out.append('['); boolean first = true;
            for (Object item : (Collection<?>) value) {
                if (!first) out.append(','); first = false; appendJson(out, item);
            }
            out.append(']'); return;
        }
        appendJsonString(out, String.valueOf(value));
    }

    private static void appendJsonString(StringBuilder out, String input) {
        out.append('"');
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '<': out.append("\\u003c"); break;
                case '>': out.append("\\u003e"); break;
                case '&': out.append("\\u0026"); break;
                default:
                    if (c < 32 || c == '\u2028' || c == '\u2029') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    static List<String> splitWords(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String token : text.trim().split("[\\s,]+")) if (!token.isEmpty()) out.add(token);
        return out;
    }

    /**
     * Survey Solutions stores answer codes as integers, while questionnaire
     * HTML often pads them for display (for example 01 or -009).  Stata drops
     * that padding when it exports numeric variables.  Use one representation
     * on both sides so labels, ordering, missing-value rules, and filters keep
     * working regardless of the CSV's numeric display format.
     */
    static String canonicalCode(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (!trimmed.matches("[+-]?\\d+")) return trimmed;
        try {
            return new BigInteger(trimmed).toString();
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    static List<String> splitPipe(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String token : text.split("\\|")) if (!token.trim().isEmpty()) out.add(token.trim());
        return out;
    }

    static boolean containsIgnoreCase(Collection<String> values, String target) {
        if (target == null) return false;
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    static String shortLabel(String label, String fallback, int max) {
        String value = cleanText(label);
        value = value.replaceFirst("(?i)^\\s*(SRI\\.)?[A-Za-z]{1,5}\\.?\\s?\\d+[a-z]?\\.\\s*", "");
        value = value.replaceFirst("(?i)^\\s*(INTERVIEWER|ENUMERATOR|NOTE|CODE( THE)?|SELECT|INDICATE|RECORD)\\b[:\\s]*", "");
        value = value.replaceFirst("^[*•—\\-\\s]+", "").trim();
        if (value.isEmpty()) value = fallback == null ? "" : fallback;
        if (value.length() > max) value = value.substring(0, Math.max(1, max - 1)).replaceAll("\\s+$", "") + "…";
        return value;
    }

    /**
     * Returns a human-facing label, treating Survey Solutions' generated
     * calculated-variable description as metadata rather than as a title.
     * The Stata exports use labels such as "Calculated variable of type
     * String" for every calculated string, so showing that text makes several
     * different variables indistinguishable.  An intentional, meaningful
     * Stata or questionnaire label remains untouched.
     */
    static String displayLabel(String label, String variable) {
        String value = cleanText(label);
        if (value.isEmpty() || CALCULATED_VARIABLE_LABEL.matcher(value).matches()) {
            return cleanText(variable);
        }
        return value;
    }

    static String slug(String input, int index) {
        String slug = normalizeKey(input);
        if (slug.length() > 30) slug = slug.substring(0, 30);
        return "s" + index + "-" + (slug.isEmpty() ? "section" : slug);
    }

    static String dataUri(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty()) return null;
        Path path = Paths.get(filename);
        byte[] bytes = Files.readAllBytes(path);
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String mime = lower.endsWith(".svg") ? "image/svg+xml" : lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                ? "image/jpeg" : lower.endsWith(".gif") ? "image/gif" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static String utcTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
    }

    static String absolute(String filename) {
        if (filename == null || filename.isEmpty()) return filename;
        return new File(filename).getAbsoluteFile().toPath().normalize().toString();
    }
}
