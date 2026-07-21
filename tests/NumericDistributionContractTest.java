import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Contracts for numeric chart selection that must not depend on categorical limits. */
public final class NumericDistributionContractTest {
    private NumericDistributionContractTest() {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("surveye-numeric-distribution-");
        try {
            Path csv = temporary.resolve("numeric-distributions.csv");
            Files.write(csv, fixtureRows(), StandardCharsets.UTF_8);

            DashboardConfig config = new DashboardConfig();
            config.questionnaire = "numeric-distribution-contract.html";
            config.output = temporary.resolve("dashboard.html").toString();
            config.variables = "age years_city workers";
            config.discreteVariables = "age years_city workers";
            // maxcategories() controls categorical charts and filters. It must
            // not force users to raise the limit merely because a numeric
            // distribution has many distinct integer observations.
            config.maxCategories = 12;
            config.replace = true;

            DashboardBuilder.Result built = DashboardBuilder.build(
                    questionnaire(), CsvTable.read(csv.toString()), config);

            check("discrete".equals(built.model.metadata.get("age").kind),
                    "Forced integer age distribution was not retained as discrete.");
            check("discrete".equals(built.model.metadata.get("years_city").kind),
                    "Sparse integer years-in-city distribution was not retained as discrete.");
            check("discrete".equals(built.model.metadata.get("workers").kind),
                    "Short worker-count support was not retained as discrete.");
            check(built.model.metadata.get("age").nonnegative
                            && built.model.metadata.get("years_city").nonnegative
                            && built.model.metadata.get("workers").nonnegative,
                    "Forced discrete metadata must exclude negative response codes.");
            check(built.model.metadata.get("age").specialCodes.contains("-9")
                            && built.model.metadata.get("age").specialCodes.contains("-4"),
                    "Questionnaire numeric special codes were not preserved in metadata.");
            check(built.model.panels.size() == 3,
                    "Numeric distributions were incorrectly capped by maxcategories().");

            System.out.println("PASS maxcategories-independent numeric distribution contracts");
        } finally {
            deleteTree(temporary);
        }
    }

    private static List<String> fixtureRows() {
        List<String> rows = new ArrayList<String>();
        rows.add("age,years_city,workers");
        // Eighty distinct ordinary ages deliberately exceed the categorical
        // default, while one implausibly high age exercises the robust display
        // domain in the browser regression test.
        for (int age = 18; age <= 97; age++) {
            int index = age - 18;
            int years = index < 25 ? index + 1
                    : index < 50 ? 10 + (index % 16)
                    : Arrays.asList(33, 41, 49, 57, 69).get(index % 5);
            int workers = 1 + (index % 11);
            rows.add(age + "," + years + "," + workers);
        }
        rows.add("506,69,11");
        rows.add("-4,-4,-4");
        rows.add("-9,-9,-9");
        return rows;
    }

    private static QuestionnaireSpec questionnaire() {
        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.title = "Numeric distribution contract";
        QuestionSection section = new QuestionSection(1, "Owner and workforce");
        spec.sections.add(section);
        add(spec, section, numeric("age", "Owner's age"));
        add(spec, section, numeric("years_city", "Years the owner has lived in this city"));
        add(spec, section, numeric("workers", "Number of workers"));
        return spec;
    }

    private static Question numeric(String variable, String label) {
        Question question = new Question();
        question.variable = variable;
        question.label = label;
        question.type = "numeric";
        question.rawType = "numeric: integer";
        question.section = "Owner and workforce";
        question.sectionNumber = 1;
        question.options.add(new QuestionOption("-4", "Refused", true));
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
        List<Path> paths = new ArrayList<Path>();
        Files.walk(root).forEach(paths::add);
        java.util.Collections.sort(paths, java.util.Collections.reverseOrder());
        for (Path path : paths) Files.deleteIfExists(path);
    }
}
