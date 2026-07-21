import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Contracts for grouped batteries, subgroup comparisons, and count plots. */
public final class CompositePanelContractTest {
    private CompositePanelContractTest() {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("surveye-composite-");
        try {
            Path csv = temporary.resolve("data.csv");
            Files.write(csv, Arrays.asList(
                    "srib8a,srib8b,srib8c,workers,gender",
                    "1,2,1,1,1",
                    "2,2,1,2,1",
                    "1,1,2,3,2",
                    "2,1,2,4,2",
                    "1,2,1,4,1",
                    "2,2,2,5,2",
                    "1,1,1,6,1",
                    "2,1,2,7,2",
                    "1,2,1,8,1",
                    "2,2,2,-9,2",
                    "1,1,1,11,1",
                    "2,1,2,4,2",
                    "1,2,1,3,1",
                    "2,2,2,5,2",
                    "1,1,1,2,1",
                    "2,1,2,4,2",
                    "1,2,1,6,1",
                    "2,2,2,4,2",
                    "1,1,1,3,1",
                    "2,1,2,5,2"), StandardCharsets.UTF_8);
            CsvTable table = CsvTable.read(csv.toString());
            QuestionnaireSpec spec = questionnaire();

            DashboardConfig automatic = config(temporary.resolve("automatic.html"));
            DashboardBuilder.Result built = DashboardBuilder.build(spec, table, automatic);
            check(built.model.panels.size() == 2, "Expected one family and one count panel.");
            check("family".equals(built.model.panels.get(0).kind), "Suffix battery was not grouped.");
            check(built.model.panels.get(0).memberVariables().equals(Arrays.asList("srib8a", "srib8b", "srib8c")),
                    "Family member order changed.");
            check("discrete".equals(built.model.panels.get(1).kind), "Worker count was not inferred as discrete.");
            check(built.model.metadata.get("workers").nonnegative, "Count metadata must exclude negative response codes.");
            check(built.model.familyPanels == 1 && built.model.comparisonPanels == 0, "Composite counters are wrong.");
            String html = DashboardRenderer.render(built.model, built.data);
            check(html.contains("data-kind=\"family\"") && html.contains("data-members=\"srib8a srib8b srib8c\""),
                    "Family renderer contract is missing.");
            check(html.contains("data-kind=\"discrete\"") && html.contains("\"nonnegative\":true"),
                    "Discrete renderer metadata is missing.");
            check(html.contains("\"families\":{\"family-srib8a\""), "Family runtime config is missing.");

            DashboardConfig comparison = config(temporary.resolve("comparison.html"));
            comparison.compareVariables = "srib8a srib8b srib8c";
            comparison.compareBy = "gender";
            // Older wrappers and hand-authored configs can retain the outer
            // quote pair. Labeled numeric groups must still resolve by label.
            comparison.compareLevels = "\"Male|Female\"";
            comparison.compareTitle = "The gender gap at a glance";
            DashboardBuilder.Result compared = DashboardBuilder.build(spec, table, comparison);
            check(compared.model.panels.size() == 2 && "comparison".equals(compared.model.panels.get(0).kind),
                    "Explicit comparison did not take precedence over auto grouping.");
            check(compared.model.comparisonPanels == 1 && compared.model.familyPanels == 0,
                    "Comparison counters are wrong.");
            check(compared.model.panels.get(0).compareLevels.equals(Arrays.asList("1", "2")),
                    "Comparison levels were not preserved in metadata order.");
            html = DashboardRenderer.render(compared.model, compared.data);
            check(html.contains("The gender gap at a glance")
                            && html.contains("\"comparisons\":{\"comparison-srib8a\"")
                            && html.contains("\"by\":\"gender\""),
                    "Comparison renderer config is incomplete.");

            DashboardConfig manual = config(temporary.resolve("manual.html"));
            manual.autoGroups = false;
            manual.varGroups = "\"Digital channels:: srib8a srib8b srib8c\"";
            DashboardBuilder.Result manuallyGrouped = DashboardBuilder.build(spec, table, manual);
            check("family".equals(manuallyGrouped.model.panels.get(0).kind)
                            && !manuallyGrouped.model.panels.get(0).automaticGroup,
                    "Manual group was not built when automatic grouping was disabled.");
            check("Digital channels".equals(manuallyGrouped.model.panels.get(0).fullLabel),
                    "Manual family title was not retained.");

            DashboardConfig limited = config(temporary.resolve("limited.html"));
            limited.maxPanels = 1;
            DashboardBuilder.Result capped = DashboardBuilder.build(spec, table, limited);
            check(capped.model.panels.size() == 1 && capped.model.panels.get(0).memberVariables().size() == 3,
                    "maxpanels() split or discarded the leading family.");

            DashboardConfig continuous = config(temporary.resolve("continuous.html"));
            continuous.continuousVariables = "workers";
            DashboardBuilder.Result histogram = DashboardBuilder.build(spec, table, continuous);
            check("hist".equals(histogram.model.metadata.get("workers").kind),
                    "continuous() did not override automatic count inference.");

            if (args.length > 0) verifySurveySolutionsFixture(Paths.get(args[0]), temporary);

            System.out.println("PASS grouped family, comparison, and discrete-count contracts");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void verifySurveySolutionsFixture(Path fixtures, Path temporary) throws Exception {
        Path questionnaire = fixtures.resolve("English Global_informal2026(4).html");
        if (!Files.isRegularFile(questionnaire)) return;
        QuestionnaireSpec parsed = HtmlQuestionnaireParser.parseFile(questionnaire.toString());
        Path csv = temporary.resolve("srib8.csv");
        Files.write(csv, Arrays.asList(
                "srib8a,srib8b,srib8c",
                "1,2,1",
                "2,2,1",
                "1,1,2"), StandardCharsets.UTF_8);
        DashboardConfig config = new DashboardConfig();
        config.questionnaire = questionnaire.toString();
        config.variables = "srib8a srib8b srib8c";
        DashboardBuilder.Result built = DashboardBuilder.build(parsed, CsvTable.read(csv.toString()), config);
        check(built.model.panels.size() == 1 && "family".equals(built.model.panels.get(0).kind),
                "Real Survey Solutions srib8a/b/c fixture was not grouped.");
        check(built.model.panels.get(0).fullLabel.toLowerCase().contains("following"),
                "Real Survey Solutions battery prompt was not used as the family title.");
    }

    private static DashboardConfig config(Path output) {
        DashboardConfig config = new DashboardConfig();
        config.questionnaire = "contract-questionnaire.html";
        config.output = output.toString();
        config.variables = "srib8a srib8b srib8c workers";
        config.replace = true;
        config.maxPanels = 100;
        return config;
    }

    private static QuestionnaireSpec questionnaire() {
        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.title = "Composite contract";
        QuestionSection section = new QuestionSection(1, "Digital practices");
        spec.sections.add(section);
        add(spec, section, binary("srib8a", "Uses social media"));
        add(spec, section, binary("srib8b", "Uses an online marketplace"));
        add(spec, section, binary("srib8c", "Uses a business website"));
        Question workers = new Question();
        workers.variable = "workers";
        workers.label = "How many workers does this business have?";
        workers.type = "numeric";
        workers.rawType = "numeric: integer";
        workers.section = section.title;
        workers.sectionNumber = 1;
        add(spec, section, workers);
        Question gender = binary("gender", "Gender of the principal owner");
        gender.options.clear();
        gender.options.add(new QuestionOption("1", "Male", false));
        gender.options.add(new QuestionOption("2", "Female", false));
        add(spec, section, gender);
        return spec;
    }

    private static Question binary(String variable, String label) {
        Question question = new Question();
        question.variable = variable;
        question.label = label;
        question.type = "single";
        question.rawType = "single-select";
        question.familyPrompt = "Does this business use any of the following to find customers?";
        question.condition = "eligible == 1";
        question.section = "Digital practices";
        question.sectionNumber = 1;
        question.options.add(new QuestionOption("1", "Yes", false));
        question.options.add(new QuestionOption("2", "No", false));
        question.options.add(new QuestionOption("-9", "Don't know", true));
        return question;
    }

    private static void add(QuestionnaireSpec spec, QuestionSection section, Question question) {
        section.questions.add(question);
        spec.questions.add(question);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        java.util.List<Path> paths = new java.util.ArrayList<Path>();
        Files.walk(root).forEach(paths::add);
        java.util.Collections.sort(paths, java.util.Collections.reverseOrder());
        for (Path path : paths) Files.deleteIfExists(path);
    }
}
