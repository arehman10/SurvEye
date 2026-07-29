import java.io.IOException;
import java.util.Locale;

/**
 * Detects the questionnaire file format and routes to the right reader.
 *
 * <p>Supported inputs: Survey Solutions questionnaire preview HTML,
 * SurveyCTO/ODK form definition XML, and (best effort) SurveyCTO printable
 * form HTML. Detection reads content, never the file extension, so a form
 * definition saved with an .html suffix still parses.</p>
 */
final class QuestionnaireParser {
    private QuestionnaireParser() {}

    static QuestionnaireSpec parseFile(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Questionnaire filename is required.");
        }
        return parseContent(Util.readUtf8(filename));
    }

    static QuestionnaireSpec parseContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Questionnaire file is empty.");
        }
        if (SurveyCtoQuestionnaireParser.looksLikeXform(content)) {
            return SurveyCtoQuestionnaireParser.parseXform(content);
        }
        if (looksLikeSurveySolutions(content)) {
            return HtmlQuestionnaireParser.parseHtml(content);
        }
        if (SurveyCtoQuestionnaireParser.looksLikeSurveyCtoHtml(content)
                || SurveyCtoQuestionnaireParser.looksLikePrintableTable(content)) {
            return SurveyCtoQuestionnaireParser.parsePrintHtml(content);
        }
        try {
            return HtmlQuestionnaireParser.parseHtml(content);
        } catch (IllegalArgumentException notSuso) {
            throw new IllegalArgumentException(
                    "Unrecognized questionnaire file. Supported inputs: a Survey Solutions "
                    + "questionnaire preview (HTML), a SurveyCTO form definition (XML, downloaded "
                    + "from Design > form files), or a SurveyCTO printable form (HTML, best effort).");
        }
    }

    private static boolean looksLikeSurveySolutions(String content) {
        String lower = content.substring(0, Math.min(content.length(), 20000)).toLowerCase(Locale.ROOT);
        return lower.contains("questionnaire_title") || lower.contains("variable_name");
    }
}
