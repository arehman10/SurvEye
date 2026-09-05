import java.io.IOException;
import java.util.Map;

/** Renders the offline questionnaire configurator and its metadata payload. */
final class ConfiguratorRenderer {
    private ConfiguratorRenderer() {}

    static String render(QuestionnaireSpec spec, DashboardConfig config) throws IOException {
        String css = Util.readResource("/resources/configurator.css");
        String script = Util.readResource("/resources/configurator.js");
        String data = payload(spec, config);
        StringBuilder html = new StringBuilder(262144);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ")
                .append("img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; ")
                .append("font-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'\">\n")
                .append("<title>").append(Util.html(spec.title == null ? "SurvEye" : spec.title))
                .append(" · SurvEye configurator</title>\n")
                .append("<style>\n").append(css).append("\n</style>\n</head>\n<body>\n")
                .append("<script id=\"surveye-spec\" type=\"application/json\">")
                .append(data.replace("</", "<\\/")).append("</script>\n")
                .append("<div id=\"app\"></div>\n")
                .append("<script>\n").append(DashboardRenderer.safeScript(script)).append("\n</script>\n")
                .append("</body>\n</html>\n");
        return html.toString();
    }

    private static String payload(QuestionnaireSpec spec, DashboardConfig config) {
        StringBuilder json = new StringBuilder(65536);
        json.append('{')
                .append("\"title\":").append(Util.json(spec.title == null ? "Questionnaire" : spec.title))
                .append(",\"source\":").append(Util.json(spec.sourceFormat == null ? "questionnaire" : spec.sourceFormat))
                .append(",\"questionnairePath\":").append(Util.json(config.questionnaire == null ? "" : config.questionnaire))
                .append(",\"engineVersion\":").append(Util.json(SurvEye.VERSION))
                .append(",\"sections\":[");
        boolean first = true;
        for (QuestionSection section : spec.sections) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"n\":").append(section.number).append(",\"title\":")
                    .append(Util.json(section.title)).append('}');
        }
        json.append("],\"questions\":[");
        first = true;
        for (Question question : spec.questions) {
            if (!first) json.append(',');
            first = false;
            json.append('{').append("\"v\":").append(Util.json(question.variable))
                    .append(",\"label\":").append(Util.json(question.label == null ? question.variable : question.label))
                    .append(",\"type\":").append(Util.json(question.type == null ? "text" : question.type))
                    .append(",\"rawType\":").append(Util.json(question.rawType == null ? "" : question.rawType))
                    .append(",\"section\":").append(question.sectionNumber);
            if (question.subsection != null && !question.subsection.isEmpty()) {
                json.append(",\"sub\":").append(Util.json(question.subsection));
            }
            if ("repeat".equalsIgnoreCase(question.scope)) json.append(",\"repeat\":true");
            if (!question.options.isEmpty()) {
                json.append(",\"opts\":[");
                boolean firstOption = true;
                for (QuestionOption option : question.options) {
                    if (!firstOption) json.append(',');
                    firstOption = false;
                    json.append('[').append(Util.json(option.code)).append(',')
                            .append(Util.json(option.label)).append(']');
                }
                json.append(']');
            }
            json.append('}');
        }
        json.append("],\"datavars\":{");
        first = true;
        for (Map.Entry<String, String> variable : config.dataVars.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(Util.json(variable.getKey())).append(':').append(Util.json(variable.getValue()));
        }
        return json.append("}}").toString();
    }
}
