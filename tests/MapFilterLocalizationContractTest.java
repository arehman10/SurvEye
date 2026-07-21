import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for map groups, typed filters, and localized binaries. */
public final class MapFilterLocalizationContractTest {
    private MapFilterLocalizationContractTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        require(Files.isRegularFile(root.resolve("surveye_2_1_3.jar")),
                "Release engine JAR is missing from project root: " + root);
        Path temporary = Files.createTempDirectory("surveye-map-filter-i18n-");
        try {
            testTypedFilters(temporary);
            testMissingMapGroup(temporary);
            testArabicUrduBinary(temporary);
        } finally {
            deleteTree(temporary);
        }
    }

    private static void testTypedFilters(Path temporary) throws Exception {
        Path questionnaire = temporary.resolve("filter-questionnaire.html");
        Files.write(questionnaire, questionnaire("en", "Filter shapes",
                question("single-select", "cat", "Category",
                        option("1", "One") + option("2", "Two") + option("-9", "Missing code"))
                        + question("numeric: integer", "num", "Numeric value",
                        option("-4", "Too many to count") + option("-9", "Don't know"))
                        + question("multi-select", "multi", "Services",
                        option("1", "Service one") + option("2", "Service two") + option("-9", "Missing code"))
                        + question("text", "note", "Field note", "")
                        + question("single-select", "emptycat", "All-missing category",
                        option("1", "Never observed one") + option("2", "Never observed two")
                                + option("-9", "Missing code"))).getBytes(StandardCharsets.UTF_8));
        Path data = temporary.resolve("filter-data.csv");
        Files.write(data, ("case_id,cat,num,multi,note,emptycat\n"
                + "1,1,1,1;2,hello,-9\n"
                + "2,2,1.0,2,,-9\n"
                + "3,-9,2,-9,-9,-9\n"
                + "4,1,,,world,-9\n"
                + "5,1,-4,1,hello,-9\n"
                + "6,2,-9,2,world,-9\n").getBytes(StandardCharsets.UTF_8));
        Path output = temporary.resolve("filter-output.html");
        Path status = temporary.resolve("filter-status.tsv");
        Path config = temporary.resolve("filter-config.tsv");
        Files.write(config, ("mode\tbuild\nquestionnaire\t" + questionnaire + "\ndata\t" + data
                + "\noutput\t" + output + "\nstatus\t" + status
                + "\nvariables\tcat num multi note\nfilters\tcat num multi note\nmissingcodes\t-9\nreplace\t1\n")
                .getBytes(StandardCharsets.UTF_8));
        int rc = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
        Map<String, String> values = readStatus(status);
        require(rc == 0 && "1".equals(values.get("success")), "Typed-filter build failed: " + values);
        require("4".equals(values.get("k_filters")), "Unexpected typed-filter count: " + values);
        String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        require(jsonObject(payload(html, "var META=", ";\nvar DATA="), "cat").contains("\"filterMode\":\"scalar\""),
                "Categorical filter mode is missing.");
        require(jsonObject(payload(html, "var META=", ";\nvar DATA="), "num").contains("\"filterMode\":\"numeric\""),
                "Numeric filter mode is missing.");
        require(jsonObject(payload(html, "var META=", ";\nvar DATA="), "multi").contains("\"filterMode\":\"multi\""),
                "Multiselect filter mode is missing.");
        require(jsonObject(payload(html, "var META=", ";\nvar DATA="), "note").contains("\"filterMode\":\"completion\""),
                "Completion filter mode is missing.");
        require(chip(html, "cat", "1") && chip(html, "cat", "2") && !chip(html, "cat", "-9"),
                "Categorical choices do not match observed nonmissing values.");
        require(chip(html, "num", "1") && chip(html, "num", "2")
                        && !chip(html, "num", "-4") && !chip(html, "num", "-9"),
                "Numeric choices did not exclude questionnaire special response codes.");
        require(chip(html, "multi", "1") && chip(html, "multi", "2") && !chip(html, "multi", "-9"),
                "Multiselect choices do not match observed nonmissing selections.");
        require(chip(html, "note", "true") && chip(html, "note", "false"),
                "Completion filter did not expose observed Answered/Missing states.");

        Path emptyStatus = temporary.resolve("empty-filter-status.tsv");
        Path emptyConfig = temporary.resolve("empty-filter-config.tsv");
        Files.write(emptyConfig, ("mode\tbuild\nquestionnaire\t" + questionnaire + "\ndata\t" + data
                + "\noutput\t" + temporary.resolve("empty-filter-output.html") + "\nstatus\t" + emptyStatus
                + "\nvariables\tcat\nfilters\temptycat\nmissingcodes\t-9\nreplace\t1\n")
                .getBytes(StandardCharsets.UTF_8));
        rc = org.worldbank.surveye.StataPlugin.stata(new String[]{emptyConfig.toString()});
        values = readStatus(emptyStatus);
        require(rc == 0 && "0".equals(values.get("success")), "All-missing filter must use error status: " + values);
        require(values.get("message") != null && values.get("message").contains("no observed valid values"),
                "All-missing filter error is not actionable: " + values);
        System.out.println("PASS typed filters and all-missing choice suppression");
    }

    private static void testMissingMapGroup(Path temporary) throws Exception {
        StringBuilder groups = new StringBuilder();
        for (int i = 1; i <= 10; i++) groups.append(option(Integer.toString(i), "Group " + i));
        groups.append(option("-9", "Missing group"));
        Path questionnaire = temporary.resolve("map-questionnaire.html");
        Files.write(questionnaire, questionnaire("en", "Map groups",
                question("single-select", "indicator", "Indicator",
                        option("1", "Yes") + option("2", "No"))
                        + question("single-select", "group", "Map group", groups.toString()))
                .getBytes(StandardCharsets.UTF_8));
        StringBuilder csv = new StringBuilder("case_id,indicator,group,lat,lon\n");
        for (int i = 1; i <= 11; i++) {
            csv.append(i).append(',').append(i % 2 + 1).append(',').append(i == 11 ? -9 : i)
                    .append(',').append(-33.86 + i * .001).append(',').append(151.20 + i * .001).append('\n');
        }
        Path data = temporary.resolve("map-data.csv");
        Files.write(data, csv.toString().getBytes(StandardCharsets.UTF_8));
        Path output = temporary.resolve("map-output.html");
        Path status = temporary.resolve("map-status.tsv");
        Path config = temporary.resolve("map-config.tsv");
        Files.write(config, ("mode\tbuild\nquestionnaire\t" + questionnaire + "\ndata\t" + data
                + "\noutput\t" + output + "\nstatus\t" + status
                + "\nvariables\tindicator\nlatitude\tlat\nlongitude\tlon\ncountry\tAustralia"
                + "\nmaptype\tpoints\nmapby\tgroup\nmissingcodes\t-9\nreplace\t1\n")
                .getBytes(StandardCharsets.UTF_8));
        int rc = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
        Map<String, String> values = readStatus(status);
        require(rc == 0 && "1".equals(values.get("success")), "Map-group build failed: " + values);
        require("11".equals(values.get("map_N")), "Configured missing group dropped a GPS point: " + values);
        String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        String configPayload = payload(html, "var CONFIG=", ";</script>");
        String groupArray = jsonArray(jsonObject(configPayload, "map"), "groups");
        for (int i = 1; i <= 10; i++) require(groupArray.contains("\"" + i + "\""),
                "Observed map group " + i + " is absent: " + groupArray);
        require(!groupArray.contains("\"-9\""), "Configured missing code leaked into the map legend: " + groupArray);
        String dataPayload = payload(html, "var DATA=", ";\nvar CONFIG=");
        require(count(dataPayload, "\"_mapby\":") == 11 && count(dataPayload, "\"_mapby\":null") == 1,
                "Missing map group was not retained as one ungrouped point: " + dataPayload);
        require(count(dataPayload, "\"_lat\":") == 11 && count(dataPayload, "\"_lon\":") == 11,
                "Not every valid GPS point remains in the map payload.");
        System.out.println("PASS missingcodes() map group exclusion with ungrouped point retention");
    }

    private static void testArabicUrduBinary(Path temporary) throws Exception {
        Path questionnaire = temporary.resolve("localized-binary.html");
        Files.write(questionnaire, questionnaire("ar", "Localized binary",
                question("single-select", "arabic_binary", "سؤال عربي",
                        option("1", "نعم") + option("2", "لا"))
                        + question("single-select", "urdu_binary", "اردو سوال",
                        option("1", "ہاں") + option("2", "نہیں"))).getBytes(StandardCharsets.UTF_8));
        Path data = temporary.resolve("localized-binary.csv");
        Files.write(data, ("case_id,arabic_binary,urdu_binary\n1,1,2\n2,2,1\n")
                .getBytes(StandardCharsets.UTF_8));
        Path output = temporary.resolve("localized-binary-output.html");
        Path status = temporary.resolve("localized-binary-status.tsv");
        Path config = temporary.resolve("localized-binary-config.tsv");
        Files.write(config, ("mode\tbuild\nquestionnaire\t" + questionnaire + "\ndata\t" + data
                + "\noutput\t" + output + "\nstatus\t" + status
                + "\nvariables\tarabic_binary urdu_binary\nreplace\t1\n").getBytes(StandardCharsets.UTF_8));
        int rc = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
        Map<String, String> values = readStatus(status);
        require(rc == 0 && "1".equals(values.get("success")), "Localized binary build failed: " + values);
        String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        require(html.contains("data-variable=\"arabic_binary\" data-kind=\"yesno\""),
                "Arabic نعم/لا question did not receive binary treatment.");
        require(html.contains("data-variable=\"urdu_binary\" data-kind=\"yesno\""),
                "Urdu ہاں/نہیں question did not receive binary treatment.");
        System.out.println("PASS Arabic and Urdu localized binary recognition");
    }

    private static String questionnaire(String language, String title, String questions) {
        return "<!doctype html><html lang=\"" + language + "\"><head><meta charset=\"utf-8\"><title>" + title
                + "</title></head><body><div class=\"questionnaire_title\"><h1>" + title
                + "</h1></div><section class=\"section\"><div class=\"section_header\"><h2>Section</h2></div>"
                + questions + "</section></body></html>";
    }

    private static String question(String type, String variable, String label, String options) {
        return "<div class=\"question-container\"><div class=\"question\"><div class=\"question-title\"><span>Q</span>"
                + label + "</div></div><div class=\"answer\"><div class=\"question-meta\"><div class=\"type\">"
                + type + "</div><div class=\"variable_name\">" + variable
                + "</div></div><div class=\"answer-editor\">" + options + "</div></div></div>";
    }

    private static String option(String value, String label) {
        return "<div class=\"option\"><span class=\"option-value\">" + value + "</span><label>" + label + "</label></div>";
    }

    private static boolean chip(String html, String variable, String value) {
        return html.contains("data-filter=\"" + variable + "\" data-value=\"" + value + "\"");
    }

    private static String payload(String html, String startToken, String endToken) {
        int start = html.indexOf(startToken);
        int end = start < 0 ? -1 : html.indexOf(endToken, start + startToken.length());
        require(start >= 0 && end > start, "Generated HTML has no readable payload for " + startToken);
        return html.substring(start + startToken.length(), end);
    }

    private static String jsonObject(String json, String key) {
        int start = json.indexOf("\"" + key + "\":{");
        require(start >= 0, "JSON has no object for " + key + ": " + json);
        int open = json.indexOf('{', start);
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(open, i + 1);
        }
        throw new AssertionError("Unterminated JSON object for " + key + ".");
    }

    private static String jsonArray(String json, String key) {
        int start = json.indexOf("\"" + key + "\":[");
        require(start >= 0, "JSON has no array for " + key + ": " + json);
        int open = json.indexOf('[', start);
        int close = json.indexOf(']', open);
        require(close > open, "Unterminated JSON array for " + key + ".");
        return json.substring(open, close + 1);
    }

    private static int count(String value, String token) {
        int found = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) found++;
        return found;
    }

    private static Map<String, String> readStatus(Path filename) throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        List<String> lines = Files.readAllLines(filename, StandardCharsets.UTF_8);
        for (String line : lines) {
            int tab = line.indexOf('\t');
            require(tab > 0, "Malformed status record: " + line);
            values.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return values;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            Path[] paths = stream.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }
}
