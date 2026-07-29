import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Known-contract tests for direct Java/config weight handling. */
public final class WeightContractTest {
    private WeightContractTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path questionnaire = root.resolve("tests/fixed_multi_questionnaire.html");
        Path temporary = Files.createTempDirectory("surveye-weight-contract-");
        try {
            Path valid = writeCsv(temporary, "valid.csv",
                    "case_id,services_used__1,services_used__2,w\n"
                            + "1,1,0,1\n2,0,1,0\n3,0,0,\n4,,,2\n");
            for (String type : new String[]{"aweight", "fweight", "pweight", "iweight"}) {
                Result result = run(questionnaire, valid, temporary, type, true, true);
                require("1".equals(result.status.get("success")), type + " build failed: " + result.status);
                require("2".equals(result.status.get("N")), type + " did not exclude zero/missing rows: " + result.status);
                require("1".equals(result.status.get("weighted")), type + " did not report weighted output");
                String html = new String(Files.readAllBytes(result.output), StandardCharsets.UTF_8);
                require(count(html, "\"_w\":") == 2, type + " embedded an excluded weight row");
                if ("iweight".equals(type)) {
                    require(html.contains("\"showCI\":false"), "iweight did not suppress confidence intervals");
                    require(html.contains("Confidence intervals are disabled for iweights"),
                            "iweight warning is missing");
                } else {
                    require(html.contains("\"showCI\":true"), type + " unexpectedly suppressed confidence intervals");
                }
            }

            Result missingType = run(questionnaire, valid, temporary, null, true, false);
            assertError(missingType, "weight and weighttype must be specified together");
            Result missingVariable = run(questionnaire, valid, temporary, "aweight", false, true);
            assertError(missingVariable, "weight and weighttype must be specified together");

            Path negative = writeCsv(temporary, "negative.csv",
                    "case_id,services_used__1,services_used__2,w\n1,1,0,1\n2,0,1,-1\n");
            assertError(run(questionnaire, negative, temporary, "aweight", true, true), "negative value");

            Path fractional = writeCsv(temporary, "fractional.csv",
                    "case_id,services_used__1,services_used__2,w\n1,1,0,1\n2,0,1,1.5\n");
            assertError(run(questionnaire, fractional, temporary, "fweight", true, true), "must be integers");

            Path allZero = writeCsv(temporary, "all-zero.csv",
                    "case_id,services_used__1,services_used__2,w\n1,1,0,0\n2,0,1,0\n");
            assertError(run(questionnaire, allZero, temporary, "pweight", true, true), "no observations");

            Path nonnumeric = writeCsv(temporary, "nonnumeric.csv",
                    "case_id,services_used__1,services_used__2,w\n1,1,0,1\n2,0,1,oops\n");
            assertError(run(questionnaire, nonnumeric, temporary, "aweight", true, true), "nonnumeric value");

            System.out.println("PASS Java weight validation and analysis-sample contract");
        } finally {
            deleteTree(temporary);
        }
    }

    private static Result run(Path questionnaire, Path data, Path directory, String type,
                              boolean includeWeight, boolean includeType) throws Exception {
        String suffix = (type == null ? "none" : type) + "-" + System.nanoTime();
        Path output = directory.resolve(suffix + ".html");
        Path status = directory.resolve(suffix + ".status.tsv");
        Path config = directory.resolve(suffix + ".config.tsv");
        StringBuilder settings = new StringBuilder()
                .append("mode\tbuild\nquestionnaire\t").append(questionnaire)
                .append("\ndata\t").append(data)
                .append("\noutput\t").append(output)
                .append("\nstatus\t").append(status)
                .append("\nvariables\tservices_used\nshowci\t1\nreplace\t1\n");
        if (includeWeight) settings.append("weight\tw\n");
        if (includeType) settings.append("weighttype\t").append(type).append('\n');
        Files.write(config, settings.toString().getBytes(StandardCharsets.UTF_8));
        int returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
        require(returnCode == 0, "stata() did not use the status-file contract: " + returnCode);
        return new Result(readStatus(status), output);
    }

    private static Path writeCsv(Path directory, String name, String value) throws Exception {
        Path output = directory.resolve(name);
        Files.write(output, value.getBytes(StandardCharsets.UTF_8));
        return output;
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

    private static void assertError(Result result, String expected) {
        require("0".equals(result.status.get("success")), "Expected failure: " + result.status);
        String message = result.status.get("message");
        require(message != null && message.toLowerCase().contains(expected.toLowerCase()),
                "Expected error containing '" + expected + "': " + result.status);
        require(!Files.exists(result.output), "A failed weight build created output: " + result.output);
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
