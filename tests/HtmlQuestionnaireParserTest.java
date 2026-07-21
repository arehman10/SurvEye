import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HtmlQuestionnaireParserTest {
    private static int assertions;

    private HtmlQuestionnaireParserTest() {}

    public static void main(String[] args) throws Exception {
        Path expected = args.length > 0 ? Paths.get(args[0])
                : Paths.get("tests", "parser_expected.tsv");
        Path upload = args.length > 1 ? Paths.get(args[1]) : Paths.get("upload");

        Map<String, QuestionnaireSpec> parsed = new LinkedHashMap<String, QuestionnaireSpec>();
        List<String> lines = Files.readAllLines(expected, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            check(fields.length == 4, "Malformed expected-count row " + (i + 1));
            String file = fields[0];
            int expectedSections = Integer.parseInt(fields[1]);
            int expectedQuestions = Integer.parseInt(fields[2]);
            int expectedOptions = Integer.parseInt(fields[3]);
            QuestionnaireSpec spec = HtmlQuestionnaireParser.parseFile(upload.resolve(file).toString());
            parsed.put(file, spec);

            check(spec.sections.size() == expectedSections,
                    file + ": expected " + expectedSections + " sections, got " + spec.sections.size());
            check(spec.questions.size() == expectedQuestions,
                    file + ": expected " + expectedQuestions + " questions, got " + spec.questions.size());
            int sectionQuestions = 0;
            int options = 0;
            Set<String> names = new LinkedHashSet<String>();
            for (QuestionSection section : spec.sections) sectionQuestions += section.questions.size();
            for (Question question : spec.questions) {
                options += question.options.size();
                check(question.variable != null && !question.variable.isEmpty(), file + ": empty variable name");
                check(question.label != null && !question.label.isEmpty(), file + ": empty question label");
                check(names.add(question.variable.toLowerCase(java.util.Locale.ROOT)),
                        file + ": duplicate output variable " + question.variable);
            }
            check(sectionQuestions == spec.questions.size(), file + ": section/global question lists disagree");
            check(options == expectedOptions,
                    file + ": expected " + expectedOptions + " options, got " + options);
            System.out.println("PASS " + file + "  sections=" + spec.sections.size()
                    + " questions=" + spec.questions.size() + " options=" + options
                    + " warnings=" + spec.warnings.size());
        }

        QuestionnaireSpec australia = parsed.get("English ES_B_READY_2025_Australia(4).html");
        QuestionnaireSpec english = parsed.get("English Global_informal2026(4).html");
        QuestionnaireSpec sinhala = parsed.get("siNhl Global_informal2026.html");
        QuestionnaireSpec trg = parsed.get("English TRG_2025.html");
        check(australia != null && english != null && sinhala != null && trg != null,
                "All four named regression fixtures must be present");

        assertOption(australia, "Desk_Complete", "01", false);
        assertOption(australia, "d31x", "0004", false);
        assertOption(australia, "a7a", "-09", true);
        check(australia.findQuestion("country") == null,
                "Computed variable country must not be emitted as an actual question");
        Question rosterQuestion = australia.findQuestion("s_mode");
        check(rosterQuestion != null && rosterQuestion.subsection.contains("SCREENING ATTEMPT"),
                "Roster context was not assigned to its child question");
        Question afterRoster = australia.findQuestion("name_confirm_PS");
        check(afterRoster != null && afterRoster.subsection.isEmpty(),
                "Roster context leaked past its group footer: "
                        + (afterRoster == null ? "<missing>" : afterRoster.subsection));
        Question groupedQuestion = australia.findQuestion("interview_type");
        check(groupedQuestion != null && groupedQuestion.subsection.contains("Appointment Information"),
                "Group context was not assigned to its child question");
        Question hidden = english.findQuestion("frac_assignment");
        check(hidden != null && "hidden".equals(hidden.scope), "Hidden scope was not parsed");

        Question critical = trg.findQuestion("IT_1_1_1_2A_3");
        check(critical != null, "TRG critical question fixture is missing");
        check(!critical.label.startsWith("C ") && !critical.label.startsWith("C1"),
                "Critical marker C leaked into the visible label: " + critical.label);
        check(critical.label.indexOf("](") < 0,
                "Markdown destination leaked into the cleaned question label: " + critical.label);
        check(critical.label.contains("legal framework"), "Markdown link text itself was lost");

        compareLocalizedStructure(english, sinhala);
        check(sinhala.title.indexOf('\ufffd') < 0, "Sinhala title contains a replacement character");
        boolean sawJoiner = sinhala.title.indexOf('\u200d') >= 0;
        for (Question question : sinhala.questions) sawJoiner |= question.label.indexOf('\u200d') >= 0;
        check(sawJoiner, "Sinhala zero-width joiners were not preserved");

        testTolerantSyntheticHtml();
        testStrictRejection();
        System.out.println("PASS parser suite  assertions=" + assertions);
    }

    private static void compareLocalizedStructure(QuestionnaireSpec english, QuestionnaireSpec sinhala) {
        check(english.sections.size() == sinhala.sections.size(), "Localized section counts differ");
        check(english.questions.size() == sinhala.questions.size(), "Localized question counts differ");
        for (int i = 0; i < english.questions.size(); i++) {
            Question left = english.questions.get(i);
            Question right = sinhala.questions.get(i);
            check(left.variable.equals(right.variable), "Localized variable mismatch at index " + i);
            check(left.type.equals(right.type), "Localized normalized type mismatch for " + left.variable);
            check(left.rawType.equals(right.rawType), "Localized raw type mismatch for " + left.variable);
            check(left.scope.equals(right.scope), "Localized scope mismatch for " + left.variable);
            check(left.options.size() == right.options.size(), "Localized option count mismatch for " + left.variable);
            for (int j = 0; j < left.options.size(); j++) {
                QuestionOption a = left.options.get(j);
                QuestionOption b = right.options.get(j);
                check(a.code.equals(b.code), "Localized option code mismatch for " + left.variable);
                check(a.special == b.special, "Localized special-value mismatch for " + left.variable);
            }
        }
    }

    private static void testTolerantSyntheticHtml() {
        String html = "  <!doctype html><html lang='si-LK'><head>"
                + "<style>.section{content:'<div class=question-container>'}</style>"
                + "<script>var fake=\"<section class='section'>\";</script></head><body>"
                + "<div class='extra questionnaire_title'><h1>Entity &amp; parser test</h1></div>"
                + "<section data-x='1' class='extra section future'>"
                + "<div class='x section_header'><h2>Alpha</h2></div>"
                + "<div class='group extra'><div class='sub_section'>Sub A</div></div>"
                + question("x", "A [linked term](destination)", "single-select<br><span>Scope: hidden</span>",
                    "<div class='option extra'><div class='option-value'><span>0004</span></div>"
                    + "<div class='option-text'><label>A &amp; B</label></div></div>"
                    + "<div class='option'><div class='option-value'><span>-009</span></div>"
                    + "<div class='option-text'><label>DON'T KNOW</label></div></div>")
                + "<div class='group_footer extra'></div>"
                + variable("computed", "1")
                + "<div class='static-text'><div class='static_text'>Ignore me</div></div>"
                + question("X", "Duplicate", "text", "")
                + "</section></body></html>";
        QuestionnaireSpec spec = HtmlQuestionnaireParser.parseHtml(html);
        check("Entity & parser test".equals(spec.title), "Title entity decoding failed");
        check("si-LK".equals(spec.language), "Document language parsing failed: " + spec.language);
        check(spec.sections.size() == 1 && spec.questions.size() == 1,
                "Synthetic classification/deduplication failed");
        Question q = spec.questions.get(0);
        check("x".equals(q.variable), "First duplicate occurrence was not retained");
        check("A linked term".equals(q.label), "Markdown destination cleaning failed: " + q.label);
        check("single".equals(q.type) && "single-select".equals(q.rawType), "Type parsing failed");
        check("hidden".equals(q.scope), "Scope parsing failed");
        check("Sub A".equals(q.subsection), "Subsection association failed");
        check(q.options.size() == 2, "Synthetic options were not parsed");
        check("0004".equals(q.options.get(0).code), "Leading-zero option code was changed");
        check("A & B".equals(q.options.get(0).label), "Option entity decoding failed");
        check("-009".equals(q.options.get(1).code) && q.options.get(1).special,
                "Negative special code was not preserved/classified");
        check(!spec.warnings.isEmpty(), "Duplicate variable did not generate a warning");
    }

    private static String question(String variable, String label, String type, String options) {
        return "<div class='future question-container extra'><div class='question'>"
                + "<div class='question-title'><span>C</span>" + label + "</div></div>"
                + "<div class='answer extra'><div class='question-meta future'>"
                + "<div class='type'>" + type + "</div><div class='variable_name'>" + variable + "</div>"
                + "</div><div class='answer-editor'>" + options + "</div></div></div>";
    }

    private static String variable(String variable, String expression) {
        return "<div id='var-id' class='question-container'><div class='question'>"
                + "<div class='question-title'><div class='type'>Variable</div></div>"
                + "<div class='common-info'><div class='variable-expression'>" + expression + "</div></div>"
                + "</div><div class='answer'><div class='question-meta'><div class='type'>long</div>"
                + "<div class='variable_name'>" + variable + "</div></div></div></div>";
    }

    private static void testStrictRejection() {
        QuestionnaireSpec unspecified = HtmlQuestionnaireParser.parseHtml(
                "<html lang='invalid language'><body><section class='section'>"
                + "<div class='section_header'><h2>One</h2></div>"
                + question("x", "X", "text", "") + "</section></body></html>");
        check("und".equals(unspecified.language), "Invalid document language was not normalized to und");

        boolean rejected = false;
        try {
            HtmlQuestionnaireParser.parseHtml("<html><body><section><h2>Ordinary page</h2></section></body></html>");
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().contains("Survey Solutions");
        }
        check(rejected, "Arbitrary HTML was not rejected with a clear error");

        rejected = false;
        try {
            HtmlQuestionnaireParser.parseHtml("<section class='section'><div class='section_header'><h2>Empty</h2></div></section>");
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().contains("no recognizable questions");
        }
        check(rejected, "A section-shaped but question-free page was not rejected");
    }

    private static void assertOption(QuestionnaireSpec spec, String variable, String code, boolean special) {
        Question question = spec.findQuestion(variable);
        check(question != null, "Question not found: " + variable);
        for (QuestionOption option : question.options) {
            if (code.equals(option.code)) {
                check(option.special == special, "Unexpected special flag for " + variable + "=" + code);
                return;
            }
        }
        throw new AssertionError("Option not found: " + variable + "=" + code);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
