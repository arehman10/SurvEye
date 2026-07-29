import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for Survey Solutions' generic calculated-variable labels. */
public final class CalculatedVariableLabelContractTest {
    private CalculatedVariableLabelContractTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path temporary = Files.createTempDirectory("surveye-calculated-label-");
        try {
            Path questionnaire = temporary.resolve("questionnaire.html");
            Files.write(questionnaire, questionnaire().getBytes(StandardCharsets.UTF_8));
            Path data = temporary.resolve("data.csv");
            Files.write(data, ("case_id,q_placeholder,q_meaningful,calc_string,calc_long,quality\n"
                    + "1,1,1,3,1,10\n"
                    + "2,2,2,4,2,20\n"
                    + "3,1,1,3,3,30\n").getBytes(StandardCharsets.UTF_8));

            Path output = temporary.resolve("dashboard.html");
            Path status = temporary.resolve("status.tsv");
            Path config = temporary.resolve("config.tsv");
            Files.write(config, ("mode\tbuild\nquestionnaire\t" + questionnaire
                    + "\ndata\t" + data + "\noutput\t" + output + "\nstatus\t" + status
                    + "\nvariables\tq_placeholder q_meaningful"
                    + "\ncustomvars\tcalc_string quality"
                    + "\nfilters\tcalc_string\nhighlights\tcalc_long"
                    + "\ndatalabel.calc_string\tCalculated variable of type String"
                    + "\ndatatype.calc_string\tsingle"
                    + "\ndatalabel.calc_long\t  CALCULATED   VARIABLE OF TYPE: LONG  "
                    + "\ndatatype.calc_long\tnumeric"
                    + "\ndatalabel.quality\tField quality score"
                    + "\ndatatype.quality\tnumeric\nreplace\t1\n")
                    .getBytes(StandardCharsets.UTF_8));

            int rc = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
            Map<String, String> result = readStatus(status);
            require(rc == 0 && "1".equals(result.get("success")),
                    "Calculated-variable label build failed: " + result);

            String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            require(!html.toLowerCase(java.util.Locale.ROOT).contains("calculated variable of type"),
                    "A Survey Solutions placeholder label leaked into the dashboard.");
            String metadata = payload(html, "var META=", ";\nvar DATA=");
            assertMetadataLabel(metadata, "q_placeholder", "q_placeholder");
            assertMetadataLabel(metadata, "q_meaningful", "Meaningful questionnaire label");
            assertMetadataLabel(metadata, "calc_string", "calc_string");
            assertMetadataLabel(metadata, "quality", "Field quality score");
            assertMetadataLabel(metadata, "calc_long", "calc_long");
            require(html.contains("<span class=\"filter-label\">calc_string</span>"),
                    "The data-only filter did not fall back to its variable name.");
            require(html.contains("<div class=\"highlight-label\">calc_long</div>"),
                    "The data-only highlight did not fall back to its variable name.");

            require("Calculated variable of type importance score".equals(
                            Util.displayLabel("Calculated variable of type importance score", "score")),
                    "A meaningful non-SuSo label was mistaken for a generated placeholder.");
            require("Field quality score".equals(Util.displayLabel(" Field quality score ", "quality")),
                    "A meaningful Stata label was not preserved.");
            System.out.println("PASS calculated-variable display-label fallback contract");
        } finally {
            deleteTree(temporary);
        }
    }

    private static String questionnaire() {
        return "<!doctype html><html lang=\"en\"><body>"
                + "<div class=\"questionnaire_title\"><h1>Label contract</h1></div>"
                + "<section class=\"section\"><div class=\"section_header\"><h2>Section</h2></div>"
                + question("q_placeholder", "Calculated variable of type Boolean")
                + question("q_meaningful", "Meaningful questionnaire label")
                + "</section></body></html>";
    }

    private static String question(String variable, String label) {
        return "<div class=\"question-container\"><div class=\"question\">"
                + "<div class=\"question-title\"><span>C</span>" + label + "</div></div>"
                + "<div class=\"answer\"><div class=\"question-meta\">"
                + "<div class=\"type\">single-select</div><div class=\"variable_name\">"
                + variable + "</div></div><div class=\"answer-editor\">"
                + "<div class=\"option\"><span class=\"option-value\">1</span><label>Yes</label></div>"
                + "<div class=\"option\"><span class=\"option-value\">2</span><label>No</label></div>"
                + "</div></div></div>";
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

    private static String payload(String html, String startToken, String endToken) {
        int start = html.indexOf(startToken);
        int end = start < 0 ? -1 : html.indexOf(endToken, start + startToken.length());
        require(start >= 0 && end > start, "Generated HTML has no readable payload for " + startToken);
        return html.substring(start + startToken.length(), end);
    }

    private static void assertMetadataLabel(String metadata, String variable, String label) {
        String object = jsonObject(metadata, variable);
        require(object.contains("\"label\":\"" + label + "\""),
                "Unexpected metadata label for " + variable + ": " + object);
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
