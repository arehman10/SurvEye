import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contract tests for USD conversion metadata, weight toggle, and profile tables. */
public final class TableCurrencyContractTest {
    private TableCurrencyContractTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path questionnaire = root.resolve("tests/fixed_multi_questionnaire.html");
        Path temporary = Files.createTempDirectory("surveye-table-currency-");
        try {
            Path data = write(temporary.resolve("profile.csv"),
                    "case_id,services_used__1,services_used__2,stratum,sales,banked,w\n"
                            + "1,1,0,CBD,75000,1,10\n"
                            + "2,0,1,Other,50000,0,20\n"
                            + "3,1,1,CBD,100000,1,30\n");
            Result valid = run(questionnaire, data, temporary, "valid",
                    "weight\tw\nweighttype\tpweight\n"
                            + "usdvars\tsales\nusdrate\t300\ncurrency\tSri Lankan rupees\n"
                            + "tableby\tstratum\ntablevars\tsales banked\n"
                            + "tablestats\tmedian|share:1\n"
                            + "tablelabels\tMedian sales|Banked\n"
                            + "tabletitle\tStratum profile\n"
                            + "tablesubtitle\tKey indicators side by side\n"
                            + "tabletotal\tSri Lanka (all locations)\n"
                            + "tableweightlabel\tEstimated firms\n");
            require("1".equals(valid.status.get("success")), "Valid profile build failed: " + valid.status);
            String html = read(valid.output);
            require(html.contains("id=\"weight-toggle\" type=\"checkbox\" checked"), "Weight toggle scaffold is missing.");
            require(html.contains("id=\"usd-toggle\" type=\"checkbox\""), "USD toggle scaffold is missing.");
            require(html.contains("class=\"estimate-toggles\"") && html.contains("class=\"mode-toggle\""),
                    "Toggle class contract is missing.");
            require(html.indexOf("class=\"estimate-toggles\"") < html.indexOf("id=\"controls-body\""),
                    "Estimate toggles must remain in the always-visible toolbar.");
            require(html.contains("id=\"summary-profile-table\""), "Summary table scaffold is missing.");
            require(html.contains("class=\"profile-card\" id=\"summary-profile\"")
                            && html.contains("class=\"profile-status\" data-profile-status")
                            && html.contains("class=\"profile-table\" id=\"summary-profile-table\""),
                    "Profile-table class/status contract is incomplete.");
            require(html.contains("data-weight-mode-label") && html.contains("data-weight-footer-label"),
                    "Dynamic weighted badge/footer hooks are missing.");
            require(html.contains("\"weightToggle\":true"), "Weight-toggle config is missing.");
            require(html.contains("\"usd\":{\"enabled\":true,\"variables\":[\"sales\"],\"rate\":300.0,\"currency\":\"Sri Lankan rupees\"}"),
                    "USD config contract is incomplete.");
            require(html.contains("\"table\":{\"enabled\":true,\"by\":\"stratum\""), "Table config is missing.");
            require(html.contains("\"stats\":[\"median\",\"share:1\"]"), "Table statistics were not preserved.");
            require(html.contains("\"labels\":[\"Median sales\",\"Banked\"]"), "Table labels were not preserved.");
            require(html.contains("\"ignoreOwnFilter\":true"), "Table-own-filter behavior is not declared.");
            require(count(html, "\"sales\":") >= 3 && count(html, "\"banked\":") >= 3
                            && count(html, "\"stratum\":") >= 3,
                    "Table/USD columns were not embedded for every analysis row.");

            Result defaults = run(questionnaire, data, temporary, "defaults",
                    "usdvars\tsales\nusdrate\t300\n"
                            + "tableby\tstratum\ntablevars\tsales banked\n");
            require("1".equals(defaults.status.get("success")), "Default profile build failed: " + defaults.status);
            String defaultHtml = read(defaults.output);
            require(defaultHtml.contains("\"currency\":\"Local currency\""), "Default local-currency label is missing.");
            require(defaultHtml.contains("\"stats\":[\"auto\",\"auto\"]"), "Default auto table statistics are missing.");
            require(defaultHtml.contains("\"title\":\"Summary table\""), "Default table title is missing.");
            require(defaultHtml.contains("\"totalLabel\":\"All filtered interviews\""), "Default total label is missing.");
            require(defaultHtml.contains("\"weightLabel\":\"Weighted total\""), "Default weight label is missing.");

            Result forcedDistribution = run(questionnaire, data, temporary, "usd-distribution",
                    "variables\tservices_used sales\ndiscrete\tsales\n"
                            + "usdvars\tsales\nusdrate\t300\n");
            require("1".equals(forcedDistribution.status.get("success")),
                    "USD distribution normalization failed: " + forcedDistribution.status);
            String forcedHtml = read(forcedDistribution.output);
            require(forcedHtml.contains("data-variable=\"sales\" data-kind=\"hist\""),
                    "usdvars() did not normalize a forced discrete panel to a numeric histogram.");
            require(forcedHtml.contains("\"sales\":{\"label\":\"sales\",\"kind\":\"hist\""),
                    "usdvars() did not normalize numeric metadata to hist.");
            require(forcedHtml.contains("\"distributionMode\":\"continuous\""),
                    "USD metadata does not declare a continuous distribution.");

            assertError(run(questionnaire, data, temporary, "rate-missing", "usdvars\tsales\n"),
                    "usdvars and usdrate must be specified together");
            assertError(run(questionnaire, data, temporary, "vars-missing", "usdrate\t300\n"),
                    "usdvars and usdrate must be specified together");
            assertError(run(questionnaire, data, temporary, "rate-zero", "usdvars\tsales\nusdrate\t0\n"),
                    "greater than zero");
            assertError(run(questionnaire, data, temporary, "currency-only", "currency\tSri Lankan rupees\n"),
                    "currency requires usdvars");
            assertError(run(questionnaire, data, temporary, "usd-string", "usdvars\tstratum\nusdrate\t300\n"),
                    "requires numeric data");
            assertError(run(questionnaire, data, temporary, "table-pair", "tableby\tstratum\n"),
                    "tableby and tablevars must be specified together");
            assertError(run(questionnaire, data, temporary, "table-option-only", "tabletitle\tProfile\n"),
                    "table options require tableby");
            assertError(run(questionnaire, data, temporary, "stats-count",
                    "tableby\tstratum\ntablevars\tsales banked\ntablestats\tmedian\n"),
                    "one pipe-delimited entry");
            assertError(run(questionnaire, data, temporary, "invalid-stat",
                    "tableby\tstratum\ntablevars\tsales\ntablestats\tmode\n"),
                    "invalid tablestats entry");
            assertError(run(questionnaire, data, temporary, "empty-stat",
                    "tableby\tstratum\ntablevars\tsales banked\ntablestats\tmedian|\n"),
                    "empty pipe-delimited entry");
            assertError(run(questionnaire, data, temporary, "mean-string",
                    "tableby\tbanked\ntablevars\tstratum\ntablestats\tmean\n"),
                    "requires numeric data");

            System.out.println("PASS USD, weight-toggle, and summary-table backend contract");
        } finally {
            deleteTree(temporary);
        }
    }

    private static Result run(Path questionnaire, Path data, Path directory, String name, String extras) throws Exception {
        Path output = directory.resolve(name + ".html");
        Path status = directory.resolve(name + ".status.tsv");
        Path config = directory.resolve(name + ".config.tsv");
        String settings = "mode\tbuild\nquestionnaire\t" + questionnaire
                + "\ndata\t" + data + "\noutput\t" + output + "\nstatus\t" + status
                + "\nvariables\tservices_used\nreplace\t1\n" + extras;
        write(config, settings);
        int returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
        require(returnCode == 0, "stata() did not use the status-file contract: " + returnCode);
        return new Result(readStatus(status), output);
    }

    private static Path write(Path path, String text) throws Exception {
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Map<String, String> readStatus(Path path) throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines) {
            int tab = line.indexOf('\t');
            require(tab > 0, "Malformed status record: " + line);
            values.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return values;
    }

    private static void assertError(Result result, String expected) throws Exception {
        require("0".equals(result.status.get("success")), "Expected failure: " + result.status);
        String message = result.status.get("message");
        require(message != null && message.toLowerCase().contains(expected.toLowerCase()),
                "Expected error containing '" + expected + "': " + result.status);
        require(!Files.exists(result.output), "A failed build created output: " + result.output);
    }

    private static int count(String value, String needle) {
        int count = 0, offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); }
        return count;
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

    private static final class Result {
        final Map<String, String> status;
        final Path output;
        Result(Map<String, String> status, Path output) { this.status = status; this.output = output; }
    }
}
