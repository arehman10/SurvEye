import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Verifies that Stata date display formats reach the generated META payload. */
public final class DateFormatMetadataContractTest {
    private static final String[][] CASES = {
            {"monthly", "%tm"},
            {"quarterly", "%tq"},
            {"weekly", "%tw"},
            {"halfyearly", "%th"},
            {"yearly", "%ty"},
            {"daily", "%td"},
            {"clock", "%tc"}
    };

    private DateFormatMetadataContractTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        require(Files.isRegularFile(root.resolve("surveye_2_1_2.jar")),
                "Release engine JAR is missing from project root: " + root);
        Path temporary = Files.createTempDirectory("surveye-date-format-");
        try {
            Path questionnaire = temporary.resolve("questionnaire.html");
            Files.write(questionnaire, questionnaire().getBytes(StandardCharsets.UTF_8));
            Path data = temporary.resolve("dates.csv");
            Files.write(data, csv().getBytes(StandardCharsets.UTF_8));

            Path output = temporary.resolve("dates.html");
            Path status = temporary.resolve("status.tsv");
            Path config = temporary.resolve("config.tsv");
            Files.write(config, config(questionnaire, data, output, status).getBytes(StandardCharsets.UTF_8));

            int returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
            require(returnCode == 0, "stata() did not use the status-file contract: " + returnCode);
            Map<String, String> values = readStatus(status);
            require("1".equals(values.get("success")), "Date-format build failed: " + values);
            require("13".equals(values.get("N")), "Unexpected observation count: " + values);
            require("8".equals(values.get("k_charted")), "Unexpected chart count: " + values);
            require("1".equals(values.get("k_filters")), "Unexpected filter count: " + values);
            require(Files.isRegularFile(output), "Dashboard output was not created.");

            String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            String meta = payload(html, "var META=", ";\nvar DATA=");
            for (String[] dateCase : CASES) {
                String variable = dateCase[0];
                String format = dateCase[1];
                String object = jsonObject(meta, variable);
                require(object.contains("\"kind\":\"date\""),
                        variable + " did not retain its custom date type in META: " + object);
                require(object.contains("\"format\":\"" + format + "\""),
                        variable + " did not retain " + format + " in META: " + object);
                require(html.contains("data-variable=\"" + variable + "\" data-kind=\"date\""),
                        variable + " was not rendered through the date-chart path.");
            }

            String coded = jsonObject(meta, "special_case");
            String special = jsonArray(coded, "special");
            String missing = jsonArray(coded, "missing");
            require(special.contains("\"100\""),
                    "Questionnaire special code 100 is missing from META.special: " + coded);
            require(!missing.contains("\"100\""),
                    "Questionnaire special code 100 leaked into META.missing: " + coded);
            require(missing.contains("\"-9\""),
                    "Explicit missing code -9 is missing from META.missing: " + coded);
            require(special.contains("\"-9\""),
                    "Explicit missing code -9 must remain visually special/muted: " + coded);

            String filterHighlight = jsonObject(meta, "filter_highlight");
            require(filterHighlight.contains("\"kind\":\"filter\""),
                    "Filter-only highlight did not retain filter metadata: " + filterHighlight);
            require(jsonArray(filterHighlight, "missing").contains("\"-9\""),
                    "Filter-only highlight lost its explicit missing code: " + filterHighlight);
            require(jsonArray(filterHighlight, "special").contains("\"-9\""),
                    "Filter-only highlight lost special styling for its missing code: " + filterHighlight);
            require(html.contains("class=\"highlight\" data-variable=\"filter_highlight\""),
                    "Filter-only variable was not rendered as a highlight.");
            for (int category = 1; category <= 12; category++) {
                require(html.contains("data-filter=\"filter_highlight\" data-value=\"" + category + "\""),
                        "Filter-only variable did not offer valid category " + category + ".");
            }
            require(!html.contains("data-filter=\"filter_highlight\" data-value=\"-9\""),
                    "Explicit missing code -9 was incorrectly offered as a filter choice.");
            System.out.println("PASS Stata date-format and special/missing META propagation"
                    + " (%tm %tq %tw %th %ty %td %tc; filter-only highlight)");
        } finally {
            deleteTree(temporary);
        }
    }

    private static String csv() {
        StringBuilder csv = new StringBuilder(
                "case_id,special_case,filter_highlight,monthly,quarterly,weekly,halfyearly,yearly,daily,clock\n");
        for (int row = 1; row <= 13; row++) {
            int filter = row <= 12 ? row : -9;
            csv.append(row).append(',').append(row == 2 ? 100 : 1).append(',').append(filter)
                    .append(',').append(791 + row)
                    .append(',').append(263 + row)
                    .append(',').append(3444 + row)
                    .append(',').append(131 + row)
                    .append(',').append(2025 + row)
                    .append(',').append(24105 + row)
                    .append(',').append(2082758400000L + row * 86400000L).append('\n');
        }
        return csv.toString();
    }

    private static String questionnaire() {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>Date metadata contract</title></head><body>"
                + "<div class=\"questionnaire_title\"><h1>Date metadata contract</h1></div>"
                + "<section class=\"section\"><div class=\"section_header\"><h2>Codes</h2></div>"
                + "<div class=\"question-container\"><div class=\"question\"><div class=\"question-title\">"
                + "<span>C</span>Special status</div></div><div class=\"answer\">"
                + "<div class=\"question-meta\"><div class=\"type\">single-select</div>"
                + "<div class=\"variable_name\">special_case</div></div>"
                + "<div class=\"answer-editor single-option\">"
                + "<div class=\"option\"><div class=\"option-value\"><span>1</span></div>"
                + "<div class=\"option-text\"><label>Regular</label></div></div>"
                + "<div class=\"type\">Special values</div>"
                + "<div class=\"option\"><div class=\"option-value\"><span>100</span></div>"
                + "<div class=\"option-text\"><label>Questionnaire special</label></div></div>"
                + "</div></div></div>"
                + "<div class=\"question-container\"><div class=\"question\"><div class=\"question-title\">"
                + "<span>F</span>Filter and highlight status</div></div><div class=\"answer\">"
                + "<div class=\"question-meta\"><div class=\"type\">single-select</div>"
                + "<div class=\"variable_name\">filter_highlight</div></div>"
                + "<div class=\"answer-editor single-option\">"
                + filterOptions()
                + "<div class=\"type\">Special values</div>"
                + "<div class=\"option\"><div class=\"option-value\"><span>-009</span></div>"
                + "<div class=\"option-text\"><label>Nonresponse</label></div></div>"
                + "</div></div></div></section></body></html>";
    }

    private static String filterOptions() {
        StringBuilder options = new StringBuilder();
        for (int category = 1; category <= 12; category++) {
            options.append("<div class=\"option\"><div class=\"option-value\"><span>")
                    .append(category).append("</span></div><div class=\"option-text\"><label>Category ")
                    .append(category).append("</label></div></div>");
        }
        return options.toString();
    }

    private static String config(Path questionnaire, Path data, Path output, Path status) {
        StringBuilder value = new StringBuilder()
                .append("mode\tbuild\nquestionnaire\t").append(questionnaire)
                .append("\ndata\t").append(data)
                .append("\noutput\t").append(output)
                .append("\nstatus\t").append(status)
                .append("\nvariables\tspecial_case")
                .append("\nfilters\tfilter_highlight")
                .append("\nhighlights\tfilter_highlight")
                .append("\nmissingcodes\t-9")
                .append("\ncustomvars\t");
        for (int i = 0; i < CASES.length; i++) {
            if (i > 0) value.append(' ');
            value.append(CASES[i][0]);
        }
        value.append('\n');
        for (String[] dateCase : CASES) {
            value.append("datalabel.").append(dateCase[0]).append('\t')
                    .append("Custom ").append(dateCase[0]).append(" date\n")
                    .append("datatype.").append(dateCase[0]).append("\tdate\n")
                    .append("dataformat.").append(dateCase[0]).append('\t').append(dateCase[1]).append('\n');
        }
        return value.append("replace\t1\n").toString();
    }

    private static String payload(String html, String startToken, String endToken) {
        int start = html.indexOf(startToken);
        int end = start < 0 ? -1 : html.indexOf(endToken, start + startToken.length());
        require(start >= 0 && end > start, "Generated HTML has no readable META payload.");
        return html.substring(start + startToken.length(), end);
    }

    private static String jsonObject(String json, String key) {
        String token = "\"" + key + "\":";
        int keyOffset = json.indexOf(token);
        int start = keyOffset < 0 ? -1 : json.indexOf('{', keyOffset + token.length());
        require(start >= 0, "META has no object for " + key + ": " + json);
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start, i + 1);
        }
        throw new AssertionError("Unterminated META object for " + key + ".");
    }

    private static String jsonArray(String object, String key) {
        String token = "\"" + key + "\":";
        int keyOffset = object.indexOf(token);
        int start = keyOffset < 0 ? -1 : object.indexOf('[', keyOffset + token.length());
        int end = start < 0 ? -1 : object.indexOf(']', start + 1);
        require(start >= 0 && end > start, "META object has no " + key + " array: " + object);
        return object.substring(start, end + 1);
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
