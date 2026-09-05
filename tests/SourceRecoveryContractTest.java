import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Contracts for shipped configurator/encoding features restored to source. */
public final class SourceRecoveryContractTest {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path temporary = Files.createTempDirectory("surveye-source-recovery-");
        try {
            Path questionnaire = temporary.resolve("questionnaire.html");
            Files.copy(root.resolve("tests/fixtures/synthetic-australia.html"), questionnaire);
            configureAndPreserveInputs(temporary, questionnaire);
            metadataPayloadIsCompleteAndEscaped();
            malformedUtf8RetainsSurroundingText(temporary);
            customizationTranslationsAreComplete();
            System.out.println("Source recovery contracts passed.");
        } finally {
            List<Path> paths = new ArrayList<Path>();
            try (Stream<Path> stream = Files.walk(temporary)) { stream.forEach(paths::add); }
            Collections.sort(paths, Collections.reverseOrder());
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }

    private static void configureAndPreserveInputs(Path directory, Path questionnaire) throws Exception {
        Path settings = directory.resolve("settings.tsv");
        Path status = directory.resolve("status.tsv");
        Path output = directory.resolve("configurator.html");
        String prefix = "mode\tconfigure\nquestionnaire\t" + questionnaire + "\n"
                + "status\t" + status + "\nreplace\t1\ndatavar.region\tSTRING\n";
        Files.write(settings, (prefix + "output\t" + output + "\n").getBytes(StandardCharsets.UTF_8));
        check(SurvEye.stata(new String[]{settings.toString()}) == 0, "Configure bridge failed.");
        Map<String, String> values = readStatus(status);
        check("1".equals(values.get("success")), "Configurator failed: " + values);
        check(SurvEye.VERSION.equals(values.get("engine_version")), "Configurator version disagrees with engine.");
        String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        check(html.contains("\"datavars\":{\"region\":\"string\"}"), "Stata data-variable metadata was lost.");
        check(html.contains("id=\"app\"") && html.contains("id=\"surveye-spec\""), "Configurator resources are incomplete.");
        check(html.contains("connect-src 'none'"), "Configurator must remain offline.");

        // The released binary returned from validation before these checks.
        // Rebuilding source must not reintroduce destructive configure writes.
        byte[] original = Files.readAllBytes(questionnaire);
        Files.write(settings, (prefix + "output\t" + questionnaire + "\n").getBytes(StandardCharsets.UTF_8));
        SurvEye.stata(new String[]{settings.toString()});
        values = readStatus(status);
        check("0".equals(values.get("success")), "Configurator accepted overwriting its questionnaire.");
        check(Arrays.equals(original, Files.readAllBytes(questionnaire)), "Questionnaire changed after rejected output.");
        Files.write(settings, (prefix + "output\t" + settings + "\n").getBytes(StandardCharsets.UTF_8));
        byte[] originalSettings = Files.readAllBytes(settings);
        SurvEye.stata(new String[]{settings.toString()});
        check("0".equals(readStatus(status).get("success")), "Configurator accepted overwriting its configuration.");
        check(Arrays.equals(originalSettings, Files.readAllBytes(settings)), "Settings changed after rejected output.");
    }

    private static void metadataPayloadIsCompleteAndEscaped() throws Exception {
        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.title = "Questionnaire </script><script>injected</script>";
        QuestionSection section = new QuestionSection(1, "Profile");
        Question question = new Question();
        question.variable = "channel";
        question.label = "Which channel?";
        question.type = "single";
        question.rawType = "single-select";
        question.sectionNumber = 1;
        question.subsection = "Business";
        question.scope = "repeat";
        question.options.add(new QuestionOption("01", "Shop", false));
        section.questions.add(question);
        spec.sections.add(section);
        spec.questions.add(question);
        DashboardConfig config = new DashboardConfig();
        config.questionnaire = "questionnaire.html";
        config.dataVars.put("channel", "string");
        String html = ConfiguratorRenderer.render(spec, config);
        check(!html.contains("</script><script>injected</script>"), "Questionnaire title can break out of embedded JSON.");
        check(html.contains("\"sub\":\"Business\"") && html.contains("\"repeat\":true"), "Question context was lost.");
        check(html.contains("\"opts\":[[\"01\",\"Shop\"]]"), "Question option codes or labels were lost.");
    }

    private static void malformedUtf8RetainsSurroundingText(Path directory) throws Exception {
        Path text = directory.resolve("older-export.txt");
        Files.write(text, new byte[]{'a', (byte) 0xc3, '(', '\n', 'b'});
        List<String> lines = Util.lenientReadLines(text);
        check(lines.equals(Arrays.asList("a\ufffd(", "b")), "Malformed UTF-8 no longer follows released replacement behavior.");
        try (BufferedReader reader = Util.lenientReader(text)) {
            check("a\ufffd(".equals(reader.readLine()), "CSV and configuration readers disagree about UTF-8 replacement.");
        }
    }

    private static void customizationTranslationsAreComplete() {
        String[] keys = {"customizeChart", "compareBy", "chartSize", "topGroupsNote", "sizeDefault",
                "sizeCompact", "sizeTall", "sizeXL", "noneOption", "chartTypeLabel", "chartBars",
                "chartDonut", "chartSplit", "accentColor", "themeColor", "useThemeColor", "fontSizeLabel",
                "fontSmaller", "fontNormal", "fontLarger", "fontLargest", "showValueLabels", "resetChart", "doneLabel"};
        for (String language : new String[]{"english", "arabic", "urdu"}) {
            for (String key : keys) {
                check(!key.equals(DashboardI18n.text(language, key)), language + " is missing " + key);
            }
        }
    }

    private static Map<String, String> readStatus(Path path) throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab >= 0) values.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return values;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
