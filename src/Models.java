import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class QuestionnaireSpec {
    String title = "Survey";
    String language = "und";
    final List<QuestionSection> sections = new ArrayList<QuestionSection>();
    final List<Question> questions = new ArrayList<Question>();
    final List<String> warnings = new ArrayList<String>();

    Question findQuestion(String variable) {
        if (variable == null) return null;
        for (Question q : questions) {
            if (q.variable.equalsIgnoreCase(variable)) return q;
        }
        return null;
    }
}

final class QuestionSection {
    int number;
    String title;
    final List<Question> questions = new ArrayList<Question>();

    QuestionSection(int number, String title) {
        this.number = number;
        this.title = title;
    }
}

final class Question {
    String variable;
    String label;
    String type;
    String rawType;
    String scope = "";
    String familyPrompt = "";
    String condition = "";
    String section;
    int sectionNumber;
    String subsection = "";
    final List<QuestionOption> options = new ArrayList<QuestionOption>();

    Question copy() {
        Question q = new Question();
        q.variable = variable;
        q.label = label;
        q.type = type;
        q.rawType = rawType;
        q.scope = scope;
        q.familyPrompt = familyPrompt;
        q.condition = condition;
        q.section = section;
        q.sectionNumber = sectionNumber;
        q.subsection = subsection;
        q.options.addAll(options);
        return q;
    }
}

final class QuestionOption {
    final String code;
    final String label;
    final boolean special;

    QuestionOption(String code, String label, boolean special) {
        this.code = code;
        this.label = label;
        this.special = special;
    }
}

final class DashboardConfig {
    boolean stataPlugin;
    String configFile;
    String questionnaire;
    String data;
    String output;
    String status;
    String diagnostics;
    String title;
    String subtitle;
    String variables;
    String sections;
    String sectionMatch;
    String exclude;
    String filters;
    String highlights;
    String keyMessages;
    String customSections;
    String customVariables;
    String addToSections;
    String varGroups;
    boolean autoGroups = true;
    String ungroupVariables;
    final Map<String, String> dataLabels = new LinkedHashMap<String, String>();
    final Map<String, String> dataTypes = new LinkedHashMap<String, String>();
    final Map<String, String> dataFormats = new LinkedHashMap<String, String>();
    final Map<String, Map<String, String>> dataValueLabels = new LinkedHashMap<String, Map<String, String>>();
    String bars;
    String donuts;
    String histograms;
    String discreteVariables;
    String continuousVariables;
    boolean autoDiscrete = true;
    String compareVariables;
    String compareBy;
    String compareTitle;
    String compareLevels;
    String missingCodes;
    String latitude;
    String longitude;
    String country;
    String boundaries;
    String mapLevel = "auto";
    String mapType = "points";
    String baseMap = "google_hybrid";
    String mapBy;
    String mapTitle;
    String weight;
    String weightType;
    String usdVariables;
    double usdRate = Double.NaN;
    String currency = "Local currency";
    boolean currencySpecified;
    String tableBy;
    String tableVariables;
    String tableStats;
    String tableLabels;
    String tableTitle = "Summary table";
    String tableSubtitle;
    String tableTotal = "All filtered interviews";
    String tableWeightLabel = "Weighted total";
    boolean tableTitleSpecified;
    boolean tableSubtitleSpecified;
    boolean tableTotalSpecified;
    boolean tableWeightLabelSpecified;
    boolean showCI;
    double ciLevel = 95.0;
    String logo;
    String theme = "worldbank";
    String density = "compact";
    String uiLanguage = "auto";
    String direction = "auto";
    String note;
    String source;
    String disclaimer;
    boolean demo;
    int demoN = 240;
    long seed = 7L;
    boolean showEmpty;
    boolean strict;
    boolean replace;
    int maxCategories = 12;
    int maxPanels = 100;
    String mode = "build";
}

final class DashboardModel {
    String title;
    String subtitle;
    String product;
    String language = "en";
    String questionnaireLanguage = "und";
    String uiLanguage = "english";
    String direction = "ltr";
    String theme;
    String density = "compact";
    String note;
    String source;
    String disclaimer;
    String logoDataUri;
    String questionnairePath;
    boolean simulated;
    boolean weighted;
    String weightType;
    String weightVariable;
    boolean usdEnabled;
    double usdRate = Double.NaN;
    String currency = "Local currency";
    final List<String> usdVariables = new ArrayList<String>();
    DashboardTable summaryTable;
    int sourceQuestions;
    int sourceSections;
    int observations;
    int skipped;
    int familyPanels;
    int comparisonPanels;
    int mapValid;
    int mapMissing;
    int mapOutside;
    BoundaryMap.MapGeometry mapGeometry;
    String latitude;
    String longitude;
    String mapType;
    String baseMap = "google_hybrid";
    String mapBy;
    String mapLevel = "country";
    String mapTitle;
    boolean showCI;
    double ciLevel = 95.0;
    int maxCategories = 12;
    final List<String> mapGroups = new ArrayList<String>();
    final List<DashboardSection> sections = new ArrayList<DashboardSection>();
    final List<ChartPanel> panels = new ArrayList<ChartPanel>();
    final List<DashboardFilter> filters = new ArrayList<DashboardFilter>();
    final List<HighlightCard> highlights = new ArrayList<HighlightCard>();
    final List<KeyMessage> keyMessages = new ArrayList<KeyMessage>();
    final List<String> warnings = new ArrayList<String>();
    final List<String> skippedVariables = new ArrayList<String>();
    final Set<String> requiredColumns = new LinkedHashSet<String>();
    final Map<String, VariableMeta> metadata = new LinkedHashMap<String, VariableMeta>();
}

/** Declarative, filter-aware profile table rendered by the dashboard runtime. */
final class DashboardTable {
    String by;
    String byLabel;
    String title = "Summary table";
    String subtitle;
    String totalLabel = "All filtered interviews";
    String weightLabel = "Weighted total";
    final List<String> variables = new ArrayList<String>();
    final List<String> statistics = new ArrayList<String>();
    final List<String> labels = new ArrayList<String>();
}

final class DashboardSection {
    int number;
    String title;
    String note;
    final List<ChartPanel> panels = new ArrayList<ChartPanel>();
}

final class ChartPanel {
    String id;
    String variable;
    String title;
    String fullLabel;
    String kind;
    String rawType;
    String subsection;
    String section;
    int sectionNumber;
    boolean explicit;
    final List<String> members = new ArrayList<String>();
    boolean automaticGroup;
    String compareBy;
    final List<String> compareLevels = new ArrayList<String>();

    /** Returns member variables while preserving legacy singleton panels. */
    List<String> memberVariables() {
        List<String> variables = new ArrayList<String>();
        if (!members.isEmpty()) variables.addAll(members);
        else if (variable != null && !variable.trim().isEmpty()) variables.add(variable);
        return variables;
    }
}

final class VariableMeta {
    String variable;
    String label;
    String kind;
    String distributionMode = "auto";
    boolean nonnegative;
    String filterMode = "scalar";
    String rawType;
    String stataFormat;
    boolean multi;
    boolean canonicalCodes;
    final List<String> order = new ArrayList<String>();
    final Map<String, String> labels = new LinkedHashMap<String, String>();
    final Set<String> specialCodes = new LinkedHashSet<String>();
    final Set<String> missingCodes = new LinkedHashSet<String>();
    final Set<String> affirmativeCodes = new LinkedHashSet<String>();
    final Set<String> negativeCodes = new LinkedHashSet<String>();
    final Map<String, String> expandedColumns = new LinkedHashMap<String, String>();
}

final class DashboardFilter {
    String variable;
    String label;
    final List<FilterChoice> choices = new ArrayList<FilterChoice>();
}

final class FilterChoice {
    final String value;
    final String label;

    FilterChoice(String value, String label) {
        this.value = value;
        this.label = label;
    }
}

final class HighlightCard {
    String variable;
    String label;
    String value;
    String detail;
}

final class KeyMessage {
    String title;
    String text;

    KeyMessage(String title, String text) {
        this.title = title;
        this.text = text;
    }
}
