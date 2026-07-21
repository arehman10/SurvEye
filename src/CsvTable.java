import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CsvTable {
    final List<String> headers = new ArrayList<String>();
    final List<String[]> rows = new ArrayList<String[]>();
    final List<String> warnings = new ArrayList<String>();
    private final Map<String, Integer> columns = new LinkedHashMap<String, Integer>();
    char delimiter;

    static CsvTable read(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty()) throw new IOException("No data file was supplied.");
        Path path = Paths.get(filename);
        if (!Files.isRegularFile(path)) throw new IOException("Data file not found: " + path.toAbsolutePath());
        CsvTable table = new CsvTable();
        table.delimiter = detectDelimiter(path);
        Reader base = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            PushbackReader reader = new PushbackReader(new BufferedReader(base, 65536), 2);
            List<String> record;
            boolean first = true;
            int rowNumber = 0;
            while ((record = readRecord(reader, table.delimiter)) != null) {
                rowNumber++;
                if (first) {
                    first = false;
                    if (!record.isEmpty() && record.get(0).length() > 0 && record.get(0).charAt(0) == '\ufeff') {
                        record.set(0, record.get(0).substring(1));
                    }
                    for (String name : record) {
                        String cleaned = name == null ? "" : name.trim();
                        if (cleaned.isEmpty()) throw new IOException("CSV header contains an empty variable name.");
                        String key = cleaned.toLowerCase(Locale.ROOT);
                        if (table.columns.containsKey(key)) throw new IOException("Duplicate CSV variable name: " + cleaned);
                        table.columns.put(key, table.headers.size());
                        table.headers.add(cleaned);
                    }
                    continue;
                }
                if (isBlank(record)) continue;
                if (record.size() != table.headers.size()) {
                    if (table.warnings.size() < 20) {
                        table.warnings.add("CSV row " + rowNumber + " has " + record.size() + " fields; expected " + table.headers.size() + ".");
                    }
                }
                String[] row = new String[table.headers.size()];
                Arrays.fill(row, "");
                for (int i = 0; i < Math.min(row.length, record.size()); i++) row[i] = record.get(i);
                table.rows.add(row);
            }
        } finally {
            base.close();
        }
        if (table.headers.isEmpty()) throw new IOException("The CSV data file has no header row.");
        return table;
    }

    private static boolean isBlank(List<String> record) {
        if (record.isEmpty()) return true;
        for (String value : record) if (value != null && !value.trim().isEmpty()) return false;
        return true;
    }

    private static char detectDelimiter(Path path) throws IOException {
        BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            StringBuilder sample = new StringBuilder();
            boolean quoted = false;
            int c;
            while ((c = reader.read()) >= 0 && sample.length() < 65536) {
                char ch = (char) c;
                sample.append(ch);
                if (ch == '"') quoted = !quoted;
                if (!quoted && (ch == '\n' || ch == '\r')) break;
            }
            int comma = countOutsideQuotes(sample, ',');
            int tab = countOutsideQuotes(sample, '\t');
            int semicolon = countOutsideQuotes(sample, ';');
            if (tab > comma && tab >= semicolon) return '\t';
            if (semicolon > comma && semicolon > tab) return ';';
            return ',';
        } finally {
            reader.close();
        }
    }

    private static int countOutsideQuotes(CharSequence value, char delimiter) {
        boolean quoted = false;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted && c == delimiter) count++;
        }
        return count;
    }

    private static List<String> readRecord(PushbackReader reader, char delimiter) throws IOException {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        int c;
        while ((c = reader.read()) >= 0) {
            any = true;
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else {
                        inQuotes = false;
                        if (next >= 0) reader.unread(next);
                    }
                } else field.append(ch);
            } else if (ch == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (ch == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (ch == '\r') {
                int next = reader.read();
                if (next >= 0 && next != '\n') reader.unread(next);
                fields.add(field.toString());
                return fields;
            } else field.append(ch);
        }
        if (inQuotes) throw new IOException("CSV ended inside a quoted field.");
        if (!any && fields.isEmpty() && field.length() == 0) return null;
        fields.add(field.toString());
        return fields;
    }

    int column(String name) {
        if (name == null) return -1;
        Integer index = columns.get(name.trim().toLowerCase(Locale.ROOT));
        return index == null ? -1 : index;
    }

    boolean has(String name) {
        return column(name) >= 0;
    }

    String canonical(String name) {
        int index = column(name);
        return index < 0 ? null : headers.get(index);
    }

    String get(String[] row, String name) {
        int index = column(name);
        return index < 0 || index >= row.length ? "" : row[index];
    }

    boolean hasQuestion(Question question) {
        if (question == null) return false;
        if (has(question.variable)) return true;
        if (!"multi".equals(question.type)) return false;
        return !expandedColumns(question).isEmpty();
    }

    Map<String, String> expandedColumns(Question question) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (question == null || question.variable == null) return out;
        for (QuestionOption option : question.options) {
            String answerCode = Util.canonicalCode(option.code);
            String[] codeForms = answerCode.equals(option.code)
                    ? new String[]{option.code} : new String[]{option.code, answerCode};
            boolean found = false;
            for (String codeForm : codeForms) {
                String[] candidates = new String[]{
                        question.variable + "__" + codeForm,
                        question.variable + "_" + codeForm,
                        question.variable + "." + codeForm,
                        question.variable + "[" + codeForm + "]"
                };
                for (String candidate : candidates) {
                    String columnName = canonical(candidate);
                    if (columnName != null) {
                        out.put(answerCode, columnName);
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        }
        if (!out.isEmpty()) return out;
        String strongPrefix = question.variable.toLowerCase(Locale.ROOT) + "__";
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            if (lower.startsWith(strongPrefix) && lower.length() > strongPrefix.length()) {
                out.put(Util.canonicalCode(header.substring(strongPrefix.length())), header);
            }
        }
        return out;
    }

    boolean mostlyNumeric(String variable) {
        int index = column(variable);
        if (index < 0) return false;
        int seen = 0;
        int numeric = 0;
        for (String[] row : rows) {
            String value = row[index] == null ? "" : row[index].trim();
            if (isMissing(value)) continue;
            seen++;
            if (parseNumber(value) != null) numeric++;
            if (seen >= 2000) break;
        }
        return seen > 0 && numeric >= Math.max(1, (int) Math.ceil(seen * 0.9));
    }

    int distinctCount(String variable, int stopAfter) {
        int index = column(variable);
        if (index < 0) return 0;
        Set<String> values = new LinkedHashSet<String>();
        for (String[] row : rows) {
            String value = row[index] == null ? "" : row[index].trim();
            if (isMissing(value)) continue;
            values.add(value);
            if (values.size() >= stopAfter) break;
        }
        return values.size();
    }

    List<String> distinct(String variable, int limit) {
        List<String> out = new ArrayList<String>();
        int index = column(variable);
        if (index < 0) return out;
        Set<String> seen = new LinkedHashSet<String>();
        for (String[] row : rows) {
            String value = row[index] == null ? "" : row[index].trim();
            if (isMissing(value) || seen.contains(value)) continue;
            seen.add(value);
            out.add(value);
            if (out.size() >= limit) break;
        }
        return out;
    }

    static boolean isMissing(String value) {
        if (value == null) return true;
        String v = value.trim();
        return v.isEmpty() || v.equals(".") || v.matches("\\.[a-zA-Z]")
                || v.equalsIgnoreCase("NA") || v.equalsIgnoreCase("N/A") || v.equalsIgnoreCase("null");
    }

    static Double parseNumber(String value) {
        if (isMissing(value)) return null;
        try {
            double number = Double.parseDouble(value.trim());
            return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean truthy(String value) {
        if (isMissing(value)) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("0") || v.equals("false") || v.equals("no") || v.equals("n")) return false;
        Double number = parseNumber(v);
        return number == null || number.doubleValue() != 0.0;
    }
}
