import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.LocalDate;

final class DashboardBuilder {
    private static final Pattern FILTER_VARIABLE_SIGNAL = Pattern.compile(
            "(^|_)(region|province|district|state|sector|industry|isic|size|gender|sex|female|urban|rural|strat(?:um|a|ification)?|zone|city|town|locality|ownership|legal_?status)(_|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILTER_PRIMARY_SIGNAL = Pattern.compile(
            "\\b(?:region|province|district|stratum|strata|zone)\\b"
                    + "|\\bstate\\s*(?:/|or)\\s*province\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILTER_SECTOR_SIGNAL = Pattern.compile(
            "\\b(?:business|firm|establishment|enterprise)\\b.{0,45}\\b(?:sector|industry|isic|main activity)\\b"
                    + "|\\b(?:sector|industry|isic|main activity)\\b.{0,45}\\b(?:business|firm|establishment|enterprise)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILTER_SIZE_SIGNAL = Pattern.compile(
            "\\bsize\\s+of\\s+(?:the\\s+)?(?:business|firm|establishment|enterprise|city|town|locality)\\b"
                    + "|\\b(?:business|firm|establishment|enterprise)\\s+size\\b|\\b(?:urban|rural)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILTER_PROFILE_SIGNAL = Pattern.compile(
            "\\blegal\\s*status\\b|\\b(?:gender|sex)\\s+of\\b"
                    + "|\\b(?:respondent|owner)(?:'s|’s)?\\s+(?:gender|sex)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ID_NAME = Pattern.compile(
            "(^|_)(id|uuid|guid|barcode|serial)(_|$)|^interview__|^assignment__|^sssys|^ss_|^responsible$|^squ_id0$|^gotolocation$|^rand(om)?(_|$)|password",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERED_LETTER_FAMILY = Pattern.compile("^(.+?\\d)_?([a-z])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDERSCORE_LETTER_FAMILY = Pattern.compile("^(.+)_([a-z])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT_SIGNAL = Pattern.compile(
            "\\bhow many\\b|\\bnumber of\\b|\\bcount of\\b|\\b(?:workers?|employees?|persons?|people|members?|children|rooms?|vehicles?|locations?|visits?|competitors?|owners?|managers?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CONTINUOUS_SIGNAL = Pattern.compile(
            "\\b(?:percent(?:age)?|share|rate|ratio|amount|revenue|sales|income|wage|price|cost|profit|duration|distance|area|weight|height|index|average|mean)\\b|%",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> YES = new HashSet<String>(Arrays.asList(
            "yes", "y", "true", "si", "sí", "oui", "ja", "sim", "evet", "да", "是", "はい", "ඔව්",
            "نعم", "أجل", "اجل", "ہاں", "جی", "جی ہاں", "ہاں جی"));
    private static final Set<String> NO = new HashSet<String>(Arrays.asList(
            "no", "n", "false", "non", "nein", "não", "nao", "hayır", "нет", "否", "いいえ", "නැත",
            "لا", "كلا", "نہیں", "نہ", "جی نہیں"));

    static final class Result {
        final DashboardModel model;
        final List<Map<String, Object>> data;

        Result(DashboardModel model, List<Map<String, Object>> data) {
            this.model = model;
            this.data = data;
        }
    }

    private DashboardBuilder() {}

    static Result build(QuestionnaireSpec spec, CsvTable table, DashboardConfig config) throws Exception {
        DashboardModel model = new DashboardModel();
        DashboardI18n.Resolution locale = DashboardI18n.resolve(spec, config);
        model.uiLanguage = locale.uiLanguage;
        model.language = locale.languageCode;
        model.direction = locale.direction;
        model.questionnaireLanguage = spec.language;
        model.title = blank(config.title) ? spec.title : config.title.trim();
        model.subtitle = blank(config.subtitle) ? DashboardI18n.text(model.uiLanguage, "defaultSubtitle") : config.subtitle.trim();
        model.product = spec.title;
        model.theme = normalizeTheme(config.theme);
        model.density = blank(config.density) ? "compact" : config.density.trim().toLowerCase(Locale.ROOT);
        model.note = config.note;
        model.source = config.source;
        model.disclaimer = blank(config.disclaimer) ? DashboardI18n.text(model.uiLanguage, "defaultDisclaimer") : config.disclaimer;
        model.questionnairePath = Util.absolute(config.questionnaire);
        model.sourceQuestions = spec.questions.size();
        model.sourceSections = spec.sections.size();
        model.simulated = config.demo;
        model.weighted = !blank(config.weight);
        model.weightVariable = config.weight;
        model.weightType = config.weightType;
        model.latitude = config.latitude;
        model.longitude = config.longitude;
        model.mapType = blank(config.mapType) ? "points" : config.mapType.toLowerCase(Locale.ROOT);
        model.baseMap = blank(config.baseMap) ? "google_hybrid" : config.baseMap.toLowerCase(Locale.ROOT);
        model.mapBy = config.mapBy;
        model.mapTitle = blank(config.mapTitle) ? (blank(config.country)
                ? DashboardI18n.text(model.uiLanguage, "spatialDistribution")
                : DashboardI18n.text(model.uiLanguage, "spatialDistribution") + " — " + config.country) : config.mapTitle;
        model.showCI = config.showCI;
        if (model.weighted && "iweight".equalsIgnoreCase(model.weightType) && model.showCI) {
            model.showCI = false;
            model.warnings.add("Confidence intervals are disabled for iweights because their sampling interpretation is undefined.");
        }
        model.ciLevel = config.ciLevel;
        model.maxCategories = config.maxCategories;
        model.warnings.addAll(spec.warnings);
        if (table != null) model.warnings.addAll(table.warnings);
        if (!blank(config.logo)) model.logoDataUri = Util.dataUri(config.logo);

        if (!blank(config.latitude) || !blank(config.longitude)) {
            if (blank(config.latitude) || blank(config.longitude)) {
                throw new IllegalArgumentException("latitude() and longitude() must be specified together.");
            }
            if (blank(config.country)) {
                throw new IllegalArgumentException("country() is required when latitude() and longitude() request a map.");
            }
            if (!config.demo && (table == null || !table.has(config.latitude) || !table.has(config.longitude))) {
                throw new IllegalArgumentException("The latitude or longitude variable is not present in the data.");
            }
            String level = blank(config.mapLevel) || "auto".equalsIgnoreCase(config.mapLevel)
                    ? (blank(config.boundaries) ? "country" : "admin2") : config.mapLevel.trim().toLowerCase(Locale.ROOT);
            if (!level.equals("country") && !level.equals("admin2")) {
                throw new IllegalArgumentException("maplevel() must be country or admin2.");
            }
            if (!level.equals("country") && blank(config.boundaries)) {
                throw new IllegalArgumentException("boundaries() is required for maplevel(" + level + ").");
            }
            model.mapLevel = level;
            model.mapGeometry = BoundaryMap.load(config.country,
                    level.equals("country") ? null : config.boundaries, model.warnings);
            if (level.equals("admin2") && !model.mapGeometry.admin2) {
                throw new IllegalArgumentException("The supplied boundaries() layer does not contain recognizable Admin-2 fields.");
            }
        }

        List<String> explicitVariables = Util.splitWords(config.variables);
        List<String> customVariables = Util.splitWords(config.customVariables);
        Set<String> customNames = lowerSet(customVariables);
        Set<String> explicitNames = lowerSet(explicitVariables);
        explicitNames.addAll(customNames);
        Set<String> excluded = lowerSet(Util.splitWords(config.exclude));
        Set<String> bars = lowerSet(Util.splitWords(config.bars));
        Set<String> donuts = lowerSet(Util.splitWords(config.donuts));
        Set<String> histograms = lowerSet(Util.splitWords(config.histograms));
        Set<String> discrete = lowerSet(Util.splitWords(config.discreteVariables));
        Set<String> continuous = lowerSet(Util.splitWords(config.continuousVariables));
        checkChartOverrides(bars, donuts, histograms, discrete, continuous);
        Set<Integer> sectionNumbers = parseSectionNumbers(config.sections, spec.sections.size());
        List<String> sectionMatches = Util.splitPipe(config.sectionMatch);

        List<Question> questionnaireSelected = new ArrayList<Question>();
        if (!explicitVariables.isEmpty()) {
            for (String requested : explicitVariables) {
                if (excluded.contains(requested.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Variable " + requested + " is both selected and excluded.");
                }
                Question question = spec.findQuestion(requested);
                if (question == null) {
                    if (!config.demo && table != null && table.has(requested) && !config.strict) {
                        question = inferredQuestion(table, requested, config, false);
                        model.warnings.add("Variable " + requested + " is not in the questionnaire; its chart type was inferred from the data.");
                    } else {
                        throw new IllegalArgumentException("Selected variable is not in the questionnaire: " + requested
                                + ". Run surveye describe to inspect available variables.");
                    }
                }
                if (!sectionAllowed(question, sectionNumbers, sectionMatches)) continue;
                if (!config.demo && table != null && !table.hasQuestion(question) && !config.showEmpty) {
                    throw new IllegalArgumentException("Selected variable " + requested + " is not present in the current data.");
                }
                question = question.copy();
                questionnaireSelected.add(question);
            }
        } else {
            for (Question source : spec.questions) {
                if (excluded.contains(source.variable.toLowerCase(Locale.ROOT))) continue;
                if (!sectionAllowed(source, sectionNumbers, sectionMatches)) continue;
                if (unstableLinkedQuestion(source)) {
                    if (config.demo || table == null || table.hasQuestion(source)) {
                        model.warnings.add("Linked question " + source.variable
                                + " has no fixed answer labels and was skipped to avoid exposing linked text-list values; select it explicitly for an answered/missing chart.");
                    }
                    continue;
                }
                if (!isChartable(source, false)) continue;
                if (!config.demo && table != null && !table.hasQuestion(source) && !config.showEmpty) continue;
                questionnaireSelected.add(source.copy());
            }
        }

        List<Question> customSelected = new ArrayList<Question>();
        for (String requested : customVariables) {
            if (excluded.contains(requested.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Custom variable " + requested + " is both selected and excluded.");
            }
            Question question = spec.findQuestion(requested);
            if (question == null) {
                if (config.demo || table == null || !table.has(requested)) {
                    throw new IllegalArgumentException("Custom variable is not present in the current data: " + requested + ".");
                }
                question = inferredQuestion(table, requested, config, true);
            } else {
                question = question.copy();
                model.warnings.add("customvars() includes questionnaire variable " + requested
                        + "; questionnaire metadata was kept.");
            }
            customSelected.add(question);
        }

        List<Question> selected = new ArrayList<Question>();
        // Declared custom variables are explicit user requests.  Process them
        // first so maxpanels() never silently drops them behind a large
        // questionnaire; section ordering is normalized after placement.
        selected.addAll(customSelected);
        selected.addAll(questionnaireSelected);
        String additionalIndicators = DashboardI18n.text(model.uiLanguage, "additionalIndicators");
        for (Question question : selected) {
            if (question.sectionNumber == 10000 && "Additional indicators".equals(question.section)) {
                question.section = additionalIndicators;
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No chartable questionnaire variables matched the requested data and section selection.");
        }
        if (config.maxPanels > 0 && customSelected.size() > config.maxPanels) {
            throw new IllegalArgumentException("customvars() contains more variables than maxpanels(). Raise maxpanels() or select fewer custom variables.");
        }

        Map<Integer, DashboardSection> sectionMap = new LinkedHashMap<Integer, DashboardSection>();
        Map<String, Question> questionsByVariable = new LinkedHashMap<String, Question>();
        Set<String> usedVariables = new LinkedHashSet<String>();
        for (Question q : selected) {
            if (!usedVariables.add(q.variable.toLowerCase(Locale.ROOT))) {
                model.warnings.add("Duplicate selected variable was ignored: " + q.variable);
                continue;
            }
            boolean explicit = explicitNames.contains(q.variable.toLowerCase(Locale.ROOT));
            if (!isChartable(q, explicit)) {
                model.skipped++;
                model.skippedVariables.add(q.variable);
                model.warnings.add("Variable " + q.variable + " has type " + q.rawType + " and was not charted.");
                continue;
            }
            String kind = chartKind(q, table, bars, donuts, histograms, discrete, continuous, config, explicit);
            if (kind == null) {
                model.skipped++;
                model.skippedVariables.add(q.variable);
                continue;
            }
            // Normalize at the model boundary as a final guard for Questions
            // constructed outside the HTML parser (for example, data-only
            // custom variables and focused integration tests).
            q.label = Util.displayLabel(q.label, q.variable);
            VariableMeta meta = metadata(q, kind, table, config);
            model.metadata.put(q.variable, meta);
            ChartPanel panel = new ChartPanel();
            panel.id = q.variable;
            panel.variable = q.variable;
            panel.members.add(q.variable);
            panel.fullLabel = q.label;
            panel.title = Util.shortLabel(q.label, q.variable, 100);
            panel.kind = kind;
            panel.rawType = q.rawType;
            panel.subsection = q.subsection;
            panel.section = q.section;
            panel.sectionNumber = q.sectionNumber;
            panel.explicit = explicit;
            model.panels.add(panel);
            questionsByVariable.put(q.variable.toLowerCase(Locale.ROOT), q);
            DashboardSection dashboardSection = sectionMap.get(q.sectionNumber);
            if (dashboardSection == null) {
                dashboardSection = new DashboardSection();
                dashboardSection.number = q.sectionNumber;
                dashboardSection.title = q.section;
                sectionMap.put(q.sectionNumber, dashboardSection);
            }
            dashboardSection.panels.add(panel);
            addRequired(model, q, meta, table);
        }
        model.sections.addAll(sectionMap.values());

        if (!blank(config.customSections) && !blank(config.addToSections)) {
            throw new IllegalArgumentException("customsections() and addtosections() may not be combined.");
        }
        applyAddToSections(model, config.addToSections, customNames);
        applyCustomSections(model, config.customSections);
        rebuildPanels(model);
        applyComparison(model, spec, table, config, questionsByVariable, customNames);
        applyManualGroups(model, config.varGroups, questionsByVariable);
        if (config.autoGroups) applyAutomaticGroups(model, questionsByVariable,
                lowerSet(Util.splitWords(config.ungroupVariables)));
        limitPanels(model, config.maxPanels);
        sortSections(model);
        addFilters(model, spec, table, config);
        addHighlights(model, spec, table, config);
        addKeyMessages(model, config.keyMessages);
        if (model.weighted) model.requiredColumns.add(requireDataColumn(table, config.weight, config.demo, "weight"));
        if (!blank(config.latitude)) {
            model.requiredColumns.add(requireDataColumn(table, config.latitude, config.demo, "latitude"));
            model.requiredColumns.add(requireDataColumn(table, config.longitude, config.demo, "longitude"));
            if (!blank(config.mapBy)) {
                String mapByColumn = requireDataColumn(table, config.mapBy, config.demo, "mapby");
                model.requiredColumns.add(mapByColumn);
                Question mapQuestion = spec.findQuestion(config.mapBy);
                if (!model.metadata.containsKey(mapByColumn)) {
                    if (mapQuestion == null && table != null) mapQuestion = inferredQuestion(table, config.mapBy, config,
                            customNames.contains(config.mapBy.toLowerCase(Locale.ROOT)));
                    if (mapQuestion == null) throw new IllegalArgumentException("mapby() variable is not in the questionnaire: " + config.mapBy);
                    model.metadata.put(mapByColumn, metadata(mapQuestion, "filter", table, config));
                }
                model.mapBy = mapByColumn;
                VariableMeta mapMeta = model.metadata.get(mapByColumn);
                List<String> groups = observedMapGroups(table, config.mapBy, mapMeta);
                if (groups.size() > 10) {
                    throw new IllegalArgumentException("mapby() supports at most 10 categories after missingcodes() exclusions; "
                            + config.mapBy + " has more than 10.");
                }
                model.mapGroups.addAll(groups);
            }
        }

        if (model.panels.isEmpty()) throw new IllegalArgumentException("No indicators could be charted from the selected variables.");

        List<Map<String, Object>> data = config.demo ? demoData(model, config) : realData(model, table, config);
        model.observations = data.size();
        if (model.observations == 0) throw new IllegalArgumentException("The analysis sample contains no observations.");
        return new Result(model, data);
    }

    private static List<String> observedMapGroups(CsvTable table, String variable, VariableMeta meta) {
        LinkedHashSet<String> groups = new LinkedHashSet<String>();
        if (table == null) {
            for (String value : positiveCodes(meta)) {
                String group = normalizeFilterValue(value, meta);
                if (!blank(group) && !meta.missingCodes.contains(group)) groups.add(group);
                if (groups.size() > 10) break;
            }
        } else {
            for (String[] row : table.rows) {
                String raw = table.get(row, variable);
                if (CsvTable.isMissing(raw)) continue;
                String group = normalizeFilterValue(raw, meta);
                if (blank(group) || meta.missingCodes.contains(group)) continue;
                groups.add(group);
                if (groups.size() > 10) break;
            }
        }
        return new ArrayList<String>(groups);
    }

    private static String normalizeTheme(String theme) {
        String value = blank(theme) ? "worldbank" : theme.toLowerCase(Locale.ROOT).trim();
        if (!value.equals("worldbank") && !value.equals("clean") && !value.equals("forest") && !value.equals("dark")) {
            throw new IllegalArgumentException("theme() must be worldbank, clean, forest, or dark.");
        }
        return value;
    }

    private static void checkChartOverrides(Set<String> bars, Set<String> donuts, Set<String> histograms,
                                            Set<String> discrete, Set<String> continuous) {
        Set<String> seen = new HashSet<String>();
        for (String value : bars) seen.add(value);
        for (String value : donuts) if (!seen.add(value)) throw new IllegalArgumentException("A variable appears in more than one chart override: " + value);
        for (String value : histograms) if (!seen.add(value)) throw new IllegalArgumentException("A variable appears in more than one chart override: " + value);
        for (String value : discrete) {
            if (continuous.contains(value) || histograms.contains(value)) {
                throw new IllegalArgumentException("Variable " + value + " is requested as both discrete and continuous.");
            }
        }
        for (String value : continuous) {
            if (bars.contains(value) || donuts.contains(value)) {
                throw new IllegalArgumentException("Variable " + value + " is requested as both continuous and categorical.");
            }
        }
    }

    private static Set<Integer> parseSectionNumbers(String value, int maximum) {
        Set<Integer> out = new LinkedHashSet<Integer>();
        if (blank(value)) return out;
        for (String token : value.trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            String[] range = token.split("[/\\-]", 2);
            try {
                int start = Integer.parseInt(range[0]);
                int end = range.length == 1 ? start : Integer.parseInt(range[1]);
                if (start < 1 || end < start || end > maximum) throw new NumberFormatException();
                for (int i = start; i <= end; i++) out.add(i);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid sections() value: " + token + ". Use section numbers shown by describe.");
            }
        }
        return out;
    }

    private static boolean sectionAllowed(Question q, Set<Integer> numbers, List<String> matches) {
        if (!numbers.isEmpty() && !numbers.contains(q.sectionNumber)) return false;
        if (!matches.isEmpty()) {
            String title = q.section == null ? "" : q.section.toLowerCase(Locale.ROOT);
            boolean found = false;
            for (String match : matches) if (title.contains(match.toLowerCase(Locale.ROOT))) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    private static Question inferredQuestion(CsvTable table, String variable, DashboardConfig config, boolean declaredCustom) {
        Question q = new Question();
        q.variable = table.canonical(variable);
        String key = q.variable.toLowerCase(Locale.ROOT);
        String suppliedLabel = config == null ? null : config.dataLabels.get(key);
        q.label = Util.displayLabel(suppliedLabel, q.variable);
        String suppliedType = config == null ? null : config.dataTypes.get(key);
        if ("date".equals(suppliedType)) q.type = "date";
        else if ("single".equals(suppliedType) || "categorical".equals(suppliedType) || "string".equals(suppliedType)) q.type = "single";
        else if ("numeric".equals(suppliedType)) q.type = "numeric";
        else q.type = table.mostlyNumeric(variable) ? "numeric" : "single";
        q.rawType = declaredCustom ? "custom " + q.type : "inferred from data";
        q.section = "Additional indicators";
        q.sectionNumber = 10000;
        if (q.type.equals("single")) {
            Map<String, String> suppliedValues = config == null ? null : config.dataValueLabels.get(key);
            if (suppliedValues != null) {
                for (Map.Entry<String, String> value : suppliedValues.entrySet()) {
                    q.options.add(new QuestionOption(value.getKey(), blank(value.getValue()) ? value.getKey() : value.getValue(), false));
                }
            }
            if (q.options.isEmpty()) {
                for (String value : table.distinct(variable, 100)) q.options.add(new QuestionOption(value, value, false));
            }
        }
        return q;
    }

    private static boolean isChartable(Question q, boolean explicit) {
        if (q == null || blank(q.variable)) return false;
        if (!explicit && ("hidden".equalsIgnoreCase(q.scope) || ID_NAME.matcher(q.variable).find())) return false;
        if (unstableLinkedQuestion(q)) return explicit;
        if ("gps".equals(q.type) || "picture".equals(q.type) || "audio".equals(q.type)) return explicit;
        if ("text".equals(q.type) || "other".equals(q.type)) return explicit;
        return "single".equals(q.type) || "multi".equals(q.type) || "numeric".equals(q.type) || "date".equals(q.type);
    }

    static boolean isDefaultChartable(Question question) {
        return isChartable(question, false)
                && chartKind(question, null, Collections.<String>emptySet(), Collections.<String>emptySet(),
                Collections.<String>emptySet(), false) != null;
    }

    private static String chartKind(Question q, CsvTable table, Set<String> bars, Set<String> donuts,
                                    Set<String> histograms, boolean explicit) {
        return chartKind(q, table, bars, donuts, histograms, Collections.<String>emptySet(),
                Collections.<String>emptySet(), null, explicit);
    }

    private static String chartKind(Question q, CsvTable table, Set<String> bars, Set<String> donuts,
                                    Set<String> histograms, Set<String> discrete, Set<String> continuous,
                                    DashboardConfig config, boolean explicit) {
        String key = q.variable.toLowerCase(Locale.ROOT);
        if (unstableLinkedQuestion(q)) return explicit ? "completion" : null;
        if ("multi".equals(q.type)) {
            if (histograms.contains(key) || donuts.contains(key)) {
                throw new IllegalArgumentException("Multi-select variable " + q.variable
                        + " supports a horizontal bar chart only; remove it from histograms() or donuts().");
            }
            return "multibar";
        }
        if (histograms.contains(key) || continuous.contains(key)) return "hist";
        if (bars.contains(key)) return "bar";
        if (donuts.contains(key)) return "donut";
        if (discrete.contains(key)) {
            validateForcedDiscrete(q, table, config);
            return "discrete";
        }
        if ("numeric".equals(q.type)) {
            return config != null && config.autoDiscrete && inferredDiscrete(q, table, config) ? "discrete" : "hist";
        }
        if ("date".equals(q.type)) return "date";
        if ("text".equals(q.type) || "other".equals(q.type) || "gps".equals(q.type)
                || "picture".equals(q.type) || "audio".equals(q.type)) return explicit ? "completion" : null;
        int categories = positiveOptions(q).size();
        if (looksYesNo(q)) return "yesno";
        if (categories >= 2) return "bar";
        if (table != null) {
            int distinct = table.distinctCount(q.variable, 100);
            if (distinct > 1) return "bar";
        }
        return explicit ? "completion" : null;
    }

    private static List<QuestionOption> positiveOptions(Question q) {
        List<QuestionOption> out = new ArrayList<QuestionOption>();
        for (QuestionOption option : q.options) if (!option.special) out.add(option);
        return out;
    }

    private static boolean looksYesNo(Question q) {
        List<QuestionOption> options = positiveOptions(q);
        if (options.size() != 2) return false;
        boolean yes = false, no = false;
        for (QuestionOption option : options) {
            String label = option.label == null ? "" : option.label.trim().toLowerCase(Locale.ROOT);
            if (YES.contains(label)) yes = true;
            if (NO.contains(label)) no = true;
        }
        return yes && no;
    }

    private static VariableMeta metadata(Question q, String kind, CsvTable table, DashboardConfig config) {
        VariableMeta meta = new VariableMeta();
        meta.variable = q.variable;
        meta.label = Util.displayLabel(q.label, q.variable);
        meta.kind = kind;
        meta.distributionMode = "discrete".equals(kind) ? "discrete" : "hist".equals(kind) ? "continuous" : "auto";
        meta.nonnegative = "discrete".equals(kind) || isStrongCount(q);
        meta.rawType = q.rawType;
        meta.stataFormat = config.dataFormats.get(q.variable.toLowerCase(Locale.ROOT));
        meta.multi = "multi".equals(q.type);
        meta.canonicalCodes = hasIntegerCodes(q) && !"inferred from data".equals(q.rawType);
        for (QuestionOption option : q.options) {
            String code = meta.canonicalCodes ? Util.canonicalCode(option.code) : option.code.trim();
            if (!meta.labels.containsKey(code)) meta.order.add(code);
            meta.labels.put(code, blank(option.label) ? code : option.label);
            if (option.special) meta.specialCodes.add(code);
        }
        for (String code : Util.splitWords(config.missingCodes)) {
            String missingCode = meta.canonicalCodes ? Util.canonicalCode(code) : code.trim();
            meta.missingCodes.add(missingCode);
            meta.specialCodes.add(missingCode);
        }
        if (table != null && meta.multi) meta.expandedColumns.putAll(table.expandedColumns(q));
        if (table != null && meta.labels.isEmpty()
                && (kind.equals("donut") || kind.equals("bar") || kind.equals("yesno") || kind.equals("filter"))) {
            for (String value : table.distinct(q.variable, Math.max(100, config.maxCategories + 1))) {
                meta.order.add(value);
                meta.labels.put(value, value);
            }
        }
        assignBinaryRoles(meta);
        return meta;
    }

    private static boolean isStrongCount(Question q) {
        if (q == null) return false;
        String text = (q.label == null ? "" : q.label) + " " + (q.rawType == null ? "" : q.rawType);
        return COUNT_SIGNAL.matcher(text).find() && !CONTINUOUS_SIGNAL.matcher(text).find();
    }

    private static void validateForcedDiscrete(Question q, CsvTable table, DashboardConfig config) {
        if (!"numeric".equals(q.type) && (table == null || !table.mostlyNumeric(q.variable))) {
            throw new IllegalArgumentException("discrete() requires a numeric variable: " + q.variable + ".");
        }
        if (table == null || config == null) return;
        NumericProfile profile = numericProfile(q, table, config, true);
        if (profile.nonInteger) {
            throw new IllegalArgumentException("discrete(" + q.variable + ") contains noninteger values; use continuous(" + q.variable + ") instead.");
        }
        if (profile.count == 0) {
            throw new IllegalArgumentException("discrete(" + q.variable + ") has no valid nonnegative values after exclusions.");
        }
        // maxcategories() governs categorical displays and filter controls.
        // A numeric integer distribution has a different rendering contract:
        // the browser keeps exact integer bars while they remain readable and
        // switches to integer-aligned bins for a genuinely wide support.  Do
        // not force users to weaken every categorical limit merely because an
        // age, duration, or count variable has many distinct integer values.
    }

    private static boolean inferredDiscrete(Question q, CsvTable table, DashboardConfig config) {
        if (!"numeric".equals(q.type)) return false;
        String format = config.dataFormats.get(q.variable.toLowerCase(Locale.ROOT));
        if (format != null && format.matches("(?i)^%t[cdwmqhyb].*")) return false;
        String text = (q.label == null ? "" : q.label) + " " + (q.rawType == null ? "" : q.rawType);
        if (CONTINUOUS_SIGNAL.matcher(text).find()) return false;
        boolean strong = isStrongCount(q);
        if (table == null) return strong;
        NumericProfile profile = numericProfile(q, table, config, strong);
        if (profile.count < 2 || profile.nonInteger || profile.min < 0) return false;
        double span = profile.max - profile.min;
        if (strong) return profile.distinct <= 40 && span <= 60;
        double support = span + 1;
        return profile.count >= 20 && profile.distinct >= 2 && profile.distinct <= 12
                && span <= 12 && support > 0 && profile.distinct / support >= 0.50;
    }

    private static NumericProfile numericProfile(Question q, CsvTable table, DashboardConfig config,
                                                  boolean excludeAllNegative) {
        NumericProfile profile = new NumericProfile();
        Set<String> configuredMissing = lowerSet(Util.splitWords(config.missingCodes));
        Set<String> special = new HashSet<String>();
        for (QuestionOption option : q.options) if (option.special) special.add(Util.canonicalCode(option.code));
        Set<Long> distinct = new HashSet<Long>();
        for (String[] row : table.rows) {
            String raw = table.get(row, q.variable);
            if (CsvTable.isMissing(raw)) continue;
            String token = Util.canonicalCode(raw).toLowerCase(Locale.ROOT);
            if (configuredMissing.contains(token)) continue;
            Double value = CsvTable.parseNumber(raw);
            if (value == null) continue;
            if (value.doubleValue() < 0 && (excludeAllNegative || special.contains(token))) continue;
            profile.count++;
            if (value.doubleValue() != Math.rint(value.doubleValue())) profile.nonInteger = true;
            profile.min = Math.min(profile.min, value.doubleValue());
            profile.max = Math.max(profile.max, value.doubleValue());
            if (value.doubleValue() == Math.rint(value.doubleValue())) distinct.add(Long.valueOf(Math.round(value.doubleValue())));
        }
        profile.distinct = distinct.size();
        return profile;
    }

    private static final class NumericProfile {
        int count;
        int distinct;
        boolean nonInteger;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
    }

    private static void assignBinaryRoles(VariableMeta meta) {
        if (!"yesno".equals(meta.kind)) return;
        for (Map.Entry<String, String> entry : meta.labels.entrySet()) {
            if (meta.specialCodes.contains(entry.getKey())) continue;
            String label = entry.getValue() == null ? "" : entry.getValue().trim().toLowerCase(Locale.ROOT);
            if (YES.contains(label)) meta.affirmativeCodes.add(entry.getKey());
            if (NO.contains(label)) meta.negativeCodes.add(entry.getKey());
        }
    }

    private static void addRequired(DashboardModel model, Question q, VariableMeta meta, CsvTable table) {
        if (meta.multi && table != null && !meta.expandedColumns.isEmpty()) {
            model.requiredColumns.addAll(meta.expandedColumns.values());
        } else model.requiredColumns.add(q.variable);
    }

    private static void addFilters(DashboardModel model, QuestionnaireSpec spec, CsvTable table, DashboardConfig config) {
        List<String> requested = Util.splitWords(config.filters);
        boolean automatic = requested.isEmpty();
        if (automatic) {
            List<Question> candidates = new ArrayList<Question>();
            for (Question q : spec.questions) {
                if (!autoFilterCandidate(q, table, config, 2)) continue;
                int distinct = filterCategoryCount(q, table, config);
                if (distinct >= 2 && distinct <= Math.min(12, config.maxCategories) && isFilterSignal(q)) {
                    candidates.add(q);
                }
            }
            Collections.sort(candidates, new Comparator<Question>() {
                public int compare(Question left, Question right) {
                    return Integer.compare(filterSignalScore(right), filterSignalScore(left));
                }
            });
            for (Question candidate : candidates) requested.add(candidate.variable);
        }
        for (String variable : requested) {
            if (automatic && model.filters.size() >= 3) break;
            Question q = spec.findQuestion(variable);
            if (!config.demo && (table == null || (q == null ? !table.has(variable) : !table.hasQuestion(q)))) {
                throw new IllegalArgumentException("Filter variable is not present in the data: " + variable);
            }
            String canonical = table == null ? variable : table.canonical(variable);
            if (canonical == null) canonical = q == null ? variable : q.variable;
            DashboardFilter filter = new DashboardFilter();
            filter.variable = canonical;
            String dataLabel = config.dataLabels.get(canonical.toLowerCase(Locale.ROOT));
            String sourceLabel = q == null ? dataLabel : q.label;
            filter.label = Util.shortLabel(Util.displayLabel(sourceLabel, canonical), canonical, 42);
            Map<String, String> labels = new LinkedHashMap<String, String>();
            boolean canonicalCodes = q != null && hasIntegerCodes(q) && !"inferred from data".equals(q.rawType);
            if (q != null) for (QuestionOption option : q.options) {
                String code = canonicalCodes ? Util.canonicalCode(option.code) : option.code.trim();
                labels.put(code, blank(option.label) ? code : option.label);
            }
            if (q == null) {
                Map<String, String> supplied = config.dataValueLabels.get(canonical.toLowerCase(Locale.ROOT));
                if (supplied != null) labels.putAll(supplied);
            }

            VariableMeta meta = model.metadata.get(canonical);
            if (meta == null) {
                Question inferred = q != null ? q : inferredQuestion(table, variable, config,
                        lowerSet(Util.splitWords(config.customVariables)).contains(variable.toLowerCase(Locale.ROOT)));
                meta = metadata(inferred, "filter", table, config);
                // The data table is case-insensitive; keep one canonical key
                // and variable name everywhere in the rendered dashboard.
                meta.variable = canonical;
                meta.label = filter.label;
            }
            meta.filterMode = filterMode(q, meta, table, variable);
            labels.putAll(meta.labels);
            List<String> values = observedFilterValues(table, canonical, meta, config.maxCategories + 1);
            if (automatic && values.size() < 2) continue;
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Filter " + variable
                        + " has no observed valid values after missing/special-code exclusions.");
            }
            if (values.size() > config.maxCategories) {
                throw new IllegalArgumentException("Filter " + variable + " has more than " + config.maxCategories
                        + " categories after missing/special-code exclusions. Choose a lower-cardinality filter or raise maxcategories().");
            }
            for (String value : values) {
                String label;
                if ("completion".equals(meta.filterMode)) {
                    label = DashboardI18n.text(model.uiLanguage, "true".equals(value) ? "answered" : "missing");
                } else label = filterChoiceLabel(value, labels, meta.filterMode);
                filter.choices.add(new FilterChoice(value, label));
            }
            model.filters.add(filter);
            if (meta.multi && !meta.expandedColumns.isEmpty()) {
                model.requiredColumns.addAll(meta.expandedColumns.values());
            } else model.requiredColumns.add(canonical);
            if (!model.metadata.containsKey(canonical)) {
                for (FilterChoice choice : filter.choices) {
                    if (!meta.order.contains(choice.value)) meta.order.add(choice.value);
                    if (!meta.labels.containsKey(choice.value)) meta.labels.put(choice.value, choice.label);
                }
                model.metadata.put(canonical, meta);
            }
        }
    }

    private static String filterMode(Question question, VariableMeta meta, CsvTable table, String variable) {
        if (meta != null && meta.multi) return "multi";
        if (meta != null && "completion".equals(meta.kind)) return "completion";
        if ((meta != null && ("hist".equals(meta.kind) || "discrete".equals(meta.kind)))
                || (question != null && "numeric".equals(question.type))
                || (question == null && table != null && table.mostlyNumeric(variable))) return "numeric";
        return "scalar";
    }

    private static List<String> observedFilterValues(CsvTable table, String variable, VariableMeta meta, int stopAfter) {
        LinkedHashSet<String> observed = new LinkedHashSet<String>();
        if (table == null) {
            if ("completion".equals(meta.filterMode)) {
                observed.add("true");
                observed.add("false");
            } else {
                for (String value : meta.order) {
                    String normalized = normalizeFilterValue(value, meta);
                    Double numeric = "numeric".equals(meta.filterMode) ? CsvTable.parseNumber(normalized) : null;
                    boolean excludedSpecial = numeric != null && numeric.doubleValue() < 0
                            && meta.specialCodes.contains(normalized);
                    if (!blank(normalized) && !meta.missingCodes.contains(normalized) && !excludedSpecial) {
                        observed.add(normalized);
                    }
                    if (observed.size() >= stopAfter) break;
                }
            }
            return new ArrayList<String>(observed);
        }
        for (String[] row : table.rows) {
            if ("completion".equals(meta.filterMode)) {
                Object value = completionValue(row, table, variable, meta);
                observed.add(value == null ? "false" : "true");
            } else if ("multi".equals(meta.filterMode)) {
                Object raw = multiValue(row, table, variable, meta);
                if (raw instanceof List<?>) {
                    for (Object item : (List<?>) raw) {
                        String value = normalizeFilterValue(String.valueOf(item), meta);
                        if (!blank(value) && !meta.missingCodes.contains(value)) observed.add(value);
                    }
                }
            } else {
                String raw = table.get(row, variable);
                if (CsvTable.isMissing(raw)) continue;
                String normalized = normalizeFilterValue(raw, meta);
                if (blank(normalized) || meta.missingCodes.contains(normalized)) continue;
                if ("numeric".equals(meta.filterMode)) {
                    Double number = CsvTable.parseNumber(raw);
                    if (number == null) continue;
                    if (number.doubleValue() < 0 && meta.specialCodes.contains(normalized)) continue;
                    normalized = numericFilterToken(number.doubleValue());
                }
                observed.add(normalized);
            }
            if (observed.size() >= stopAfter) break;
        }
        return new ArrayList<String>(observed);
    }

    private static String filterChoiceLabel(String value, Map<String, String> labels, String mode) {
        if (labels.containsKey(value)) return labels.get(value);
        if ("numeric".equals(mode)) {
            Double number = CsvTable.parseNumber(value);
            if (number != null) {
                for (Map.Entry<String, String> label : labels.entrySet()) {
                    Double candidate = CsvTable.parseNumber(label.getKey());
                    if (candidate != null && Double.compare(number.doubleValue(), candidate.doubleValue()) == 0) {
                        return label.getValue();
                    }
                }
            }
        }
        return value;
    }

    private static String normalizeFilterValue(String value, VariableMeta meta) {
        if (value == null) return "";
        return meta != null && meta.canonicalCodes ? Util.canonicalCode(value) : value.trim();
    }

    private static String numericFilterToken(double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static boolean autoFilterCandidate(Question q, CsvTable table, DashboardConfig config, int minimum) {
        if (!"single".equals(q.type) || "hidden".equalsIgnoreCase(q.scope) || ID_NAME.matcher(q.variable).find()
                || (table != null && !table.has(q.variable))) return false;
        int distinct = filterCategoryCount(q, table, config);
        return distinct >= minimum && distinct <= Math.min(12, config.maxCategories);
    }

    static boolean isDefaultFilterCandidate(Question q) {
        if (q == null || !"single".equals(q.type) || "hidden".equalsIgnoreCase(q.scope)
                || ID_NAME.matcher(q.variable).find() || !isFilterSignal(q)) return false;
        int categories = positiveOptions(q).size();
        return categories >= 2 && categories <= 12;
    }

    static boolean isFilterSignal(Question q) {
        return filterSignalScore(q) > 0;
    }

    private static int filterSignalScore(Question q) {
        if (q == null) return 0;
        if (FILTER_VARIABLE_SIGNAL.matcher(q.variable).find()) return 120;
        if (FILTER_PRIMARY_SIGNAL.matcher(q.label).find()) return 100;
        if (FILTER_SECTOR_SIGNAL.matcher(q.label).find()) return 95;
        if (FILTER_SIZE_SIGNAL.matcher(q.label).find()) return 85;
        if (FILTER_PROFILE_SIGNAL.matcher(q.label).find()) return 70;
        return 0;
    }

    private static int filterCategoryCount(Question q, CsvTable table, DashboardConfig config) {
        return table == null ? positiveOptions(q).size() : table.distinctCount(q.variable, config.maxCategories + 1);
    }

    private static void addHighlights(DashboardModel model, QuestionnaireSpec spec, CsvTable table, DashboardConfig config) {
        List<String> variables = Util.splitWords(config.highlights);
        if (variables.size() > 6) throw new IllegalArgumentException("highlights() accepts at most six variables.");
        for (String variable : variables) {
            if (!config.demo && (table == null || !table.has(variable))) {
                throw new IllegalArgumentException("Highlight variable is not present in the data: " + variable);
            }
            Question q = spec.findQuestion(variable);
            String canonical = table == null ? variable : table.canonical(variable);
            HighlightCard card = new HighlightCard();
            card.variable = canonical == null ? variable : canonical;
            String dataLabel = config.dataLabels.get(card.variable.toLowerCase(Locale.ROOT));
            String sourceLabel = q == null ? dataLabel : q.label;
            card.label = Util.shortLabel(Util.displayLabel(sourceLabel, card.variable), card.variable, 54);
            model.highlights.add(card);
            model.requiredColumns.add(card.variable);
            if (!model.metadata.containsKey(card.variable)) {
                Question inferred = q != null ? q : inferredQuestion(table, variable, config,
                        lowerSet(Util.splitWords(config.customVariables)).contains(variable.toLowerCase(Locale.ROOT)));
                String kind = chartKind(inferred, table, Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(), true);
                model.metadata.put(card.variable, metadata(inferred, kind, table, config));
            }
        }
    }

    private static void addKeyMessages(DashboardModel model, String raw) {
        List<String> messages = Util.splitPipe(raw);
        if (messages.size() > 6) throw new IllegalArgumentException("keymessages() accepts at most six messages.");
        int index = 1;
        for (String item : messages) {
            String title = DashboardI18n.text(model.uiLanguage, "keyMessage") + " " + index;
            String text = item;
            int split = item.indexOf("::");
            if (split < 0) split = item.indexOf(':');
            if (split > 0) {
                title = item.substring(0, split).trim();
                text = item.substring(split + (item.startsWith("::", split) ? 2 : 1)).trim();
            }
            model.keyMessages.add(new KeyMessage(title, text));
            index++;
        }
    }

    private static void applyAddToSections(DashboardModel model, String raw, Set<String> customNames) {
        if (blank(raw)) return;
        Map<String, ChartPanel> byVariable = new LinkedHashMap<String, ChartPanel>();
        for (ChartPanel panel : model.panels) byVariable.put(panel.variable.toLowerCase(Locale.ROOT), panel);
        Set<String> assigned = new HashSet<String>();
        int nextNumber = 20000;
        for (DashboardSection section : model.sections) nextNumber = Math.max(nextNumber, section.number + 1);
        for (String group : Util.splitPipe(raw)) {
            int colon = group.indexOf(':');
            if (colon <= 0 || colon == group.length() - 1) {
                throw new IllegalArgumentException("addtosections() groups must use Section title or number: custom_varlist.");
            }
            String target = group.substring(0, colon).trim();
            DashboardSection destination = findSection(model.sections, target);
            if (destination == null) {
                if (target.matches("\\d+")) {
                    throw new IllegalArgumentException("addtosections() section " + target
                            + " is not present in the selected dashboard.");
                }
                destination = new DashboardSection();
                destination.number = nextNumber++;
                destination.title = target;
                model.sections.add(destination);
            }
            for (String variable : Util.splitWords(group.substring(colon + 1))) {
                String key = variable.toLowerCase(Locale.ROOT);
                if (!customNames.contains(key)) {
                    throw new IllegalArgumentException("addtosections() may move only variables declared in customvars(): " + variable);
                }
                ChartPanel panel = byVariable.get(key);
                if (panel == null) {
                    throw new IllegalArgumentException("addtosections() names a custom variable that was not charted: " + variable);
                }
                if (!assigned.add(key)) {
                    throw new IllegalArgumentException("addtosections() assigns a custom variable more than once: " + variable);
                }
                for (DashboardSection section : model.sections) section.panels.remove(panel);
                panel.section = destination.title;
                panel.sectionNumber = destination.number;
                destination.panels.add(panel);
            }
        }
        for (int i = model.sections.size() - 1; i >= 0; i--) {
            if (model.sections.get(i).panels.isEmpty()) model.sections.remove(i);
        }
    }

    private static DashboardSection findSection(List<DashboardSection> sections, String target) {
        Integer requestedNumber = null;
        try { requestedNumber = Integer.valueOf(target); } catch (NumberFormatException ignored) { /* title target */ }
        for (DashboardSection section : sections) {
            if (requestedNumber != null && section.number == requestedNumber.intValue()) return section;
            if (section.title != null && section.title.trim().equalsIgnoreCase(target)) return section;
        }
        // The dashboard renumbers the selected sections 01, 02, ... for display.
        // Accept that visible position as a convenience when it does not match an
        // original questionnaire section number.
        if (requestedNumber != null && requestedNumber.intValue() >= 1
                && requestedNumber.intValue() <= sections.size()) {
            return sections.get(requestedNumber.intValue() - 1);
        }
        return null;
    }

    private static void sortSections(DashboardModel model) {
        Collections.sort(model.sections, new Comparator<DashboardSection>() {
            public int compare(DashboardSection left, DashboardSection right) {
                return Integer.compare(left.number, right.number);
            }
        });
    }

    private static void applyComparison(DashboardModel model, QuestionnaireSpec spec, CsvTable table,
                                        DashboardConfig config, Map<String, Question> questionsByVariable,
                                        Set<String> customNames) {
        boolean hasVariables = !blank(config.compareVariables);
        boolean hasBy = !blank(config.compareBy);
        if (!hasVariables && !hasBy) {
            if (!blank(config.compareTitle) || !blank(config.compareLevels)) {
                throw new IllegalArgumentException("comparetitle() and comparelevels() require compare() and compareby().");
            }
            return;
        }
        if (!hasVariables || !hasBy) {
            throw new IllegalArgumentException("compare() and compareby() must be specified together.");
        }
        List<String> requested = Util.splitWords(config.compareVariables);
        if (requested.size() < 2 || requested.size() > 12) {
            throw new IllegalArgumentException("compare() accepts 2 to 12 selected binary variables.");
        }
        Map<String, ChartPanel> byVariable = singletonPanels(model);
        List<ChartPanel> sources = new ArrayList<ChartPanel>();
        Set<String> seen = new HashSet<String>();
        for (String name : requested) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) throw new IllegalArgumentException("compare() repeats variable " + name + ".");
            ChartPanel source = byVariable.get(key);
            if (source == null) throw new IllegalArgumentException("compare() names a variable that is not selected: " + name + ".");
            if (!"yesno".equals(source.kind)) {
                throw new IllegalArgumentException("compare() currently supports binary yes/no indicators; " + name
                        + " is " + source.kind + ".");
            }
            sources.add(source);
        }

        String compareColumn = requireDataColumn(table, config.compareBy, config.demo, "compareby");
        Question compareQuestion = spec.findQuestion(config.compareBy);
        if (compareQuestion == null && table != null) {
            compareQuestion = inferredQuestion(table, config.compareBy, config,
                    customNames.contains(config.compareBy.toLowerCase(Locale.ROOT)));
        }
        if (compareQuestion == null) {
            throw new IllegalArgumentException("compareby() variable is not in the questionnaire or exported data: " + config.compareBy + ".");
        }
        VariableMeta compareMeta = model.metadata.get(compareColumn);
        if (compareMeta == null) {
            compareMeta = metadata(compareQuestion, "filter", table, config);
            compareMeta.variable = compareColumn;
            model.metadata.put(compareColumn, compareMeta);
        }
        List<String> levels = observedFilterValues(table, compareColumn, compareMeta, 7);
        if (!blank(config.compareLevels)) {
            levels = selectedComparisonLevels(config.compareLevels, levels, compareMeta);
        }
        if (levels.size() < 2 || levels.size() > 5) {
            throw new IllegalArgumentException("compareby(" + config.compareBy
                    + ") must have 2 to 5 valid levels; use comparelevels() to select and order them.");
        }
        model.requiredColumns.add(compareColumn);

        ChartPanel first = sources.get(0);
        DashboardSection destination = sectionContaining(model, first);
        int insertion = destination == null ? 0 : destination.panels.indexOf(first);
        ChartPanel panel = new ChartPanel();
        panel.id = compositeId("comparison", first.variable);
        panel.variable = first.variable;
        panel.members.addAll(canonicalMembers(sources));
        panel.kind = "comparison";
        panel.compareBy = compareColumn;
        panel.compareLevels.addAll(levels);
        panel.title = blank(config.compareTitle) ? "Comparison by " + compareMeta.label : config.compareTitle.trim();
        panel.fullLabel = panel.title;
        panel.rawType = "binary comparison by " + compareMeta.label;
        panel.subsection = first.subsection;
        panel.section = first.section;
        panel.sectionNumber = first.sectionNumber;
        panel.explicit = true;
        removePanels(model, sources);
        if (destination == null) throw new IllegalArgumentException("Could not place the comparison panel.");
        destination.panels.add(Math.max(0, Math.min(insertion, destination.panels.size())), panel);
        rebuildPanels(model);
    }

    private static List<String> selectedComparisonLevels(String raw, List<String> observed, VariableMeta meta) {
        Set<String> observedSet = new LinkedHashSet<String>(observed);
        List<String> selected = new ArrayList<String>();
        // Defensively accept values written by older wrappers or hand-authored
        // config files with their delimiting quotes still attached.
        String cleaned = stripOuterQuotes(raw);
        String[] tokens = cleaned.indexOf('|') >= 0 ? cleaned.split("\\|") : cleaned.trim().split("[\\s,]+" );
        for (String token : tokens) {
            token = stripOuterQuotes(token);
            if (token.isEmpty()) continue;
            String value = meta.canonicalCodes ? Util.canonicalCode(token) : token;
            if (!observedSet.contains(value)) {
                for (String candidate : observed) {
                    String label = meta.labels.containsKey(candidate) ? meta.labels.get(candidate) : candidate;
                    if (label != null && label.trim().equalsIgnoreCase(token)) { value = candidate; break; }
                }
            }
            if (!observedSet.contains(value)) {
                throw new IllegalArgumentException("comparelevels() value " + token + " is not observed in compareby().");
            }
            if (!selected.contains(value)) selected.add(value);
        }
        return selected;
    }

    private static String stripOuterQuotes(String value) {
        String cleaned = value == null ? "" : value.trim();
        while (cleaned.length() >= 2) {
            char first = cleaned.charAt(0);
            char last = cleaned.charAt(cleaned.length() - 1);
            if (!((first == '"' && last == '"') || (first == '\'' && last == '\''))) break;
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static void applyManualGroups(DashboardModel model, String raw,
                                          Map<String, Question> questionsByVariable) {
        if (blank(raw)) return;
        Set<String> assigned = new HashSet<String>();
        for (String specification : Util.splitPipe(stripOuterQuotes(raw))) {
            int split = specification.indexOf("::");
            int width = 2;
            if (split < 1) { split = specification.indexOf(':'); width = 1; }
            if (split < 1 || split + width >= specification.length()) {
                throw new IllegalArgumentException("vargroups() entries must use Title:: varlist.");
            }
            String title = specification.substring(0, split).trim();
            List<String> variables = Util.splitWords(specification.substring(split + width));
            if (variables.size() < 2 || variables.size() > 20) {
                throw new IllegalArgumentException("Each vargroups() entry must contain 2 to 20 selected variables.");
            }
            Map<String, ChartPanel> byVariable = singletonPanels(model);
            List<ChartPanel> sources = new ArrayList<ChartPanel>();
            for (String variable : variables) {
                String key = variable.toLowerCase(Locale.ROOT);
                if (!assigned.add(key)) throw new IllegalArgumentException("vargroups() assigns a variable more than once: " + variable + ".");
                ChartPanel panel = byVariable.get(key);
                if (panel == null) {
                    throw new IllegalArgumentException("vargroups() names an unselected or already grouped variable: " + variable + ".");
                }
                sources.add(panel);
            }
            ensureFamilyCompatibility(model, sources, true);
            ChartPanel first = sources.get(0);
            DashboardSection destination = sectionContaining(model, first);
            for (ChartPanel source : sources) {
                if (sectionContaining(model, source) != destination) {
                    throw new IllegalArgumentException("All variables in a vargroups() entry must be in the same final section.");
                }
            }
            int insertion = destination.panels.indexOf(first);
            ChartPanel family = familyPanel(title, sources, false);
            removePanels(model, sources);
            destination.panels.add(Math.max(0, Math.min(insertion, destination.panels.size())), family);
            rebuildPanels(model);
        }
    }

    private static void applyAutomaticGroups(DashboardModel model, Map<String, Question> questionsByVariable,
                                             Set<String> ungrouped) {
        for (DashboardSection section : model.sections) {
            List<ChartPanel> grouped = new ArrayList<ChartPanel>();
            for (int i = 0; i < section.panels.size();) {
                ChartPanel first = section.panels.get(i);
                FamilyName name = familyName(first.variable);
                Question firstQuestion = questionsByVariable.get(lower(first.variable));
                if (!automaticCandidate(first, firstQuestion, name, ungrouped)) {
                    grouped.add(first); i++; continue;
                }
                List<ChartPanel> run = new ArrayList<ChartPanel>();
                run.add(first);
                char expected = (char) (name.suffix + 1);
                int cursor = i + 1;
                while (cursor < section.panels.size()) {
                    ChartPanel candidate = section.panels.get(cursor);
                    FamilyName candidateName = familyName(candidate.variable);
                    Question candidateQuestion = questionsByVariable.get(lower(candidate.variable));
                    if (!automaticCandidate(candidate, candidateQuestion, candidateName, ungrouped)
                            || !name.stem.equalsIgnoreCase(candidateName.stem) || candidateName.suffix != expected
                            || !same(first.subsection, candidate.subsection)
                            || !same(firstQuestion.condition, candidateQuestion.condition)
                            || !same(firstQuestion.familyPrompt, candidateQuestion.familyPrompt)
                            || !familyCompatible(model, first, candidate)) break;
                    run.add(candidate);
                    expected++;
                    cursor++;
                }
                if (run.size() >= 3) {
                    String title = blank(firstQuestion.familyPrompt)
                            ? "Related indicators — " + name.stem : firstQuestion.familyPrompt;
                    grouped.add(familyPanel(title, run, true));
                    i = cursor;
                } else {
                    grouped.add(first);
                    i++;
                }
            }
            section.panels.clear();
            section.panels.addAll(grouped);
        }
        rebuildPanels(model);
    }

    private static boolean automaticCandidate(ChartPanel panel, Question question, FamilyName name,
                                              Set<String> ungrouped) {
        if (panel == null || question == null || name == null || !"yesno".equals(panel.kind)
                || panel.memberVariables().size() != 1 || ungrouped.contains(lower(panel.variable))) return false;
        String raw = question.rawType == null ? "" : question.rawType.toLowerCase(Locale.ROOT);
        return !raw.startsWith("custom") && !raw.startsWith("inferred");
    }

    private static FamilyName familyName(String variable) {
        if (variable == null) return null;
        Matcher matcher = NUMBERED_LETTER_FAMILY.matcher(variable);
        if (!matcher.matches()) matcher = UNDERSCORE_LETTER_FAMILY.matcher(variable);
        if (!matcher.matches()) return null;
        return new FamilyName(matcher.group(1), Character.toLowerCase(matcher.group(2).charAt(0)));
    }

    private static final class FamilyName {
        final String stem;
        final char suffix;
        FamilyName(String stem, char suffix) { this.stem = stem; this.suffix = suffix; }
    }

    private static ChartPanel familyPanel(String title, List<ChartPanel> sources, boolean automatic) {
        ChartPanel first = sources.get(0);
        ChartPanel panel = new ChartPanel();
        panel.id = compositeId("family", first.variable);
        panel.variable = first.variable;
        panel.members.addAll(canonicalMembers(sources));
        panel.title = Util.shortLabel(title, first.variable, 140);
        panel.fullLabel = blank(title) ? panel.title : title.trim();
        panel.kind = "family";
        panel.rawType = "grouped " + first.rawType;
        panel.subsection = first.subsection;
        panel.section = first.section;
        panel.sectionNumber = first.sectionNumber;
        panel.explicit = !automatic;
        panel.automaticGroup = automatic;
        return panel;
    }

    private static void ensureFamilyCompatibility(DashboardModel model, List<ChartPanel> sources, boolean manual) {
        ChartPanel first = sources.get(0);
        if (!("yesno".equals(first.kind) || "bar".equals(first.kind) || "donut".equals(first.kind))) {
            throw new IllegalArgumentException("vargroups() supports compatible single-select variables; "
                    + first.variable + " is " + first.kind + ".");
        }
        for (int i = 1; i < sources.size(); i++) {
            if (!familyCompatible(model, first, sources.get(i))) {
                throw new IllegalArgumentException("Variables " + first.variable + " and " + sources.get(i).variable
                        + " do not have compatible response categories for one vargroups() chart.");
            }
        }
    }

    private static boolean familyCompatible(DashboardModel model, ChartPanel left, ChartPanel right) {
        if (!same(left.kind, right.kind)) return false;
        VariableMeta a = model.metadata.get(left.variable);
        VariableMeta b = model.metadata.get(right.variable);
        if (a == null || b == null || a.multi || b.multi) return false;
        return a.order.equals(b.order) && a.labels.equals(b.labels)
                && a.missingCodes.equals(b.missingCodes) && a.specialCodes.equals(b.specialCodes)
                && a.affirmativeCodes.equals(b.affirmativeCodes) && a.negativeCodes.equals(b.negativeCodes);
    }

    private static Map<String, ChartPanel> singletonPanels(DashboardModel model) {
        Map<String, ChartPanel> output = new LinkedHashMap<String, ChartPanel>();
        for (ChartPanel panel : model.panels) {
            List<String> members = panel.memberVariables();
            if (members.size() == 1 && !"comparison".equals(panel.kind) && !"family".equals(panel.kind)) {
                output.put(lower(members.get(0)), panel);
            }
        }
        return output;
    }

    private static DashboardSection sectionContaining(DashboardModel model, ChartPanel panel) {
        for (DashboardSection section : model.sections) if (section.panels.contains(panel)) return section;
        return null;
    }

    private static void removePanels(DashboardModel model, List<ChartPanel> panels) {
        for (DashboardSection section : model.sections) section.panels.removeAll(panels);
    }

    private static List<String> canonicalMembers(List<ChartPanel> sources) {
        List<String> members = new ArrayList<String>();
        for (ChartPanel panel : sources) members.addAll(panel.memberVariables());
        return members;
    }

    private static String compositeId(String prefix, String firstVariable) {
        return prefix + "-" + (firstVariable == null ? "panel" : firstVariable.replaceAll("[^A-Za-z0-9_-]", "_"));
    }

    private static void limitPanels(DashboardModel model, int maximum) {
        if (maximum <= 0 || model.panels.size() <= maximum) return;
        int remaining = maximum;
        for (DashboardSection section : model.sections) {
            if (remaining <= 0) section.panels.clear();
            else if (section.panels.size() > remaining) {
                section.panels.subList(remaining, section.panels.size()).clear();
                remaining = 0;
            } else remaining -= section.panels.size();
        }
        for (int i = model.sections.size() - 1; i >= 0; i--) {
            if (model.sections.get(i).panels.isEmpty()) model.sections.remove(i);
        }
        model.warnings.add("The dashboard was limited to " + maximum + " chart panels by maxpanels(); grouped panels were kept intact.");
        rebuildPanels(model);
    }

    private static void rebuildPanels(DashboardModel model) {
        model.panels.clear();
        model.familyPanels = 0;
        model.comparisonPanels = 0;
        for (DashboardSection section : model.sections) {
            for (ChartPanel panel : section.panels) {
                model.panels.add(panel);
                if ("family".equals(panel.kind)) model.familyPanels++;
                if ("comparison".equals(panel.kind)) model.comparisonPanels++;
            }
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean same(String left, String right) {
        return safe(left).trim().equalsIgnoreCase(safe(right).trim());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void applyCustomSections(DashboardModel model, String raw) {
        if (blank(raw)) return;
        String otherIndicators = DashboardI18n.text(model.uiLanguage, "otherIndicators");
        List<String> groups = Util.splitPipe(raw);
        Map<String, ChartPanel> byVariable = new LinkedHashMap<String, ChartPanel>();
        for (ChartPanel panel : model.panels) byVariable.put(panel.variable.toLowerCase(Locale.ROOT), panel);
        Set<String> assigned = new HashSet<String>();
        List<DashboardSection> custom = new ArrayList<DashboardSection>();
        int number = 1;
        for (String group : groups) {
            int colon = group.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("customsections() groups must use Title: varlist.");
            DashboardSection section = new DashboardSection();
            section.number = number++;
            section.title = group.substring(0, colon).trim();
            for (String variable : Util.splitWords(group.substring(colon + 1))) {
                String key = variable.toLowerCase(Locale.ROOT);
                ChartPanel panel = byVariable.get(key);
                if (panel == null) throw new IllegalArgumentException("customsections() names a variable that is not selected: " + variable);
                if (!assigned.add(key)) throw new IllegalArgumentException("customsections() assigns a variable more than once: " + variable);
                panel.section = section.title;
                panel.sectionNumber = section.number;
                section.panels.add(panel);
            }
            if (!section.panels.isEmpty()) custom.add(section);
        }
        for (ChartPanel panel : model.panels) {
            if (!assigned.contains(panel.variable.toLowerCase(Locale.ROOT))) {
                DashboardSection remainder = null;
                for (DashboardSection section : custom) if (section.title.equals(otherIndicators)) remainder = section;
                if (remainder == null) {
                    remainder = new DashboardSection(); remainder.number = number; remainder.title = otherIndicators; custom.add(remainder);
                }
                panel.section = remainder.title;
                panel.sectionNumber = remainder.number;
                remainder.panels.add(panel);
            }
        }
        model.sections.clear();
        model.sections.addAll(custom);
    }

    private static List<Map<String, Object>> realData(DashboardModel model, CsvTable table, DashboardConfig config) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(table.rows.size());
        int excludedWeights = 0;
        int zeroCoords = 0;
        int sourceIndex = 0;
        for (String[] source : table.rows) {
            sourceIndex++;
            Double parsedWeight = null;
            if (model.weighted) {
                String rawWeight = table.get(source, config.weight);
                if (CsvTable.isMissing(rawWeight)) { excludedWeights++; continue; }
                parsedWeight = CsvTable.parseNumber(rawWeight);
                if (parsedWeight == null) {
                    throw new IllegalArgumentException("The weight variable contains a nonnumeric value in observation " + sourceIndex + ".");
                }
                if (parsedWeight.doubleValue() < 0) {
                    throw new IllegalArgumentException("The weight variable contains a negative value in observation " + sourceIndex + ".");
                }
                if (parsedWeight.doubleValue() == 0) { excludedWeights++; continue; }
                if ("fweight".equalsIgnoreCase(model.weightType)
                        && parsedWeight.doubleValue() != Math.rint(parsedWeight.doubleValue())) {
                    throw new IllegalArgumentException("Frequency weights must be integers; observation " + sourceIndex + " is not.");
                }
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, VariableMeta> entry : model.metadata.entrySet()) {
                String variable = entry.getKey();
                VariableMeta meta = entry.getValue();
                if ("completion".equals(meta.kind)) row.put(variable, completionValue(source, table, variable, meta));
                else if (meta.multi) row.put(variable, multiValue(source, table, variable, meta));
                else row.put(variable, typedValue(table.get(source, variable), meta));
            }
            for (DashboardFilter filter : model.filters) if (!row.containsKey(filter.variable)) {
                row.put(filter.variable, typedValue(table.get(source, filter.variable), model.metadata.get(filter.variable)));
            }
            if (model.weighted) row.put("_w", parsedWeight);
            if (model.mapGeometry != null) {
                Double lat = CsvTable.parseNumber(table.get(source, config.latitude));
                Double lon = CsvTable.parseNumber(table.get(source, config.longitude));
                if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    model.mapMissing++;
                    row.put("_x", null); row.put("_y", null); row.put("_lat", null); row.put("_lon", null);
                } else {
                    if (lat == 0 && lon == 0) zeroCoords++;
                    model.mapValid++;
                    if (!model.mapGeometry.contains(lon, lat)) model.mapOutside++;
                    double x = model.mapGeometry.projectX(lon, lat);
                    double y = model.mapGeometry.projectY(lon, lat);
                    int decimals = "heat".equals(model.mapType) ? 1 : "points".equals(model.mapType) ? 3 : 2;
                    row.put("_x", round(x, decimals)); row.put("_y", round(y, decimals));
                    int geoDecimals = "heat".equals(model.mapType) ? 3 : "cluster".equals(model.mapType) ? 4 : 6;
                    row.put("_lat", round(lat, geoDecimals)); row.put("_lon", round(lon, geoDecimals));
                    if (!blank(config.mapBy)) {
                        Object group = row.get(model.mapBy);
                        VariableMeta mapMeta = model.metadata.get(model.mapBy);
                        row.put("_mapby", configuredMissing(mapMeta, group) ? null : group);
                    }
                }
            }
            out.add(row);
        }
        if (excludedWeights > 0) model.warnings.add(excludedWeights
                + " observations with missing or zero weights were excluded from all dashboard calculations.");
        if (model.mapGeometry != null && model.mapValid == 0 && !out.isEmpty()) throw new IllegalArgumentException("No valid latitude/longitude pairs remain for the requested map.");
        if (zeroCoords >= Math.max(3, out.size() / 20)) model.warnings.add(zeroCoords + " observations are located at (0,0); verify that these are real coordinates.");
        if (model.mapMissing > 0) model.warnings.add(model.mapMissing + " observations have missing or out-of-range coordinates and are omitted from the map.");
        if (model.mapOutside > 0) model.warnings.add(model.mapOutside + " valid points fall outside the selected country boundary.");
        return out;
    }

    private static Object multiValue(String[] row, CsvTable table, String variable, VariableMeta meta) {
        List<String> selected = new ArrayList<String>();
        if (!meta.expandedColumns.isEmpty()) {
            boolean answered = false;
            for (Map.Entry<String, String> expanded : meta.expandedColumns.entrySet()) {
                String value = table.get(row, expanded.getValue());
                if (!CsvTable.isMissing(value)) answered = true;
                if (CsvTable.truthy(value)) selected.add(expanded.getKey());
            }
            return answered ? selected : null;
        } else {
            String value = table.get(row, variable);
            if (CsvTable.isMissing(value)) return null;
            for (String token : value.trim().split("[;,|\\s]+")) if (!token.isEmpty()) {
                selected.add(meta.canonicalCodes ? Util.canonicalCode(token) : token);
            }
        }
        return selected;
    }

    private static Object completionValue(String[] row, CsvTable table, String variable, VariableMeta meta) {
        if (!meta.expandedColumns.isEmpty()) {
            for (String column : meta.expandedColumns.values()) {
                if (CsvTable.truthy(table.get(row, column))) return Boolean.TRUE;
            }
            return null;
        }
        String value = table.get(row, variable);
        if (CsvTable.isMissing(value)) return null;
        String code = meta.canonicalCodes ? Util.canonicalCode(value) : value.trim();
        return meta.missingCodes.contains(code) ? null : Boolean.TRUE;
    }

    private static Object typedValue(String value, VariableMeta meta) {
        if (CsvTable.isMissing(value)) return null;
        String kind = meta == null ? "" : meta.kind;
        if ("completion".equals(kind)) return Boolean.TRUE;
        if ("hist".equals(kind) || "discrete".equals(kind)) {
            Double number = CsvTable.parseNumber(value);
            return number == null ? null : number;
        }
        return meta != null && meta.canonicalCodes ? Util.canonicalCode(value) : value.trim();
    }

    private static boolean configuredMissing(VariableMeta meta, Object value) {
        if (meta == null || value == null) return false;
        String normalized = normalizeFilterValue(String.valueOf(value), meta);
        return meta.missingCodes.contains(normalized);
    }

    private static boolean hasIntegerCodes(Question question) {
        if (question == null || question.options.isEmpty()) return false;
        for (QuestionOption option : question.options) {
            if (option.code == null || !option.code.trim().matches("[+-]?\\d+")) return false;
        }
        return true;
    }

    private static boolean unstableLinkedQuestion(Question question) {
        return question != null && question.options.isEmpty() && question.rawType != null
                && question.rawType.toLowerCase(Locale.ROOT).contains("linked");
    }

    private static List<Map<String, Object>> demoData(DashboardModel model, DashboardConfig config) {
        Random random = new Random(config.seed);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(config.demoN);
        for (int i = 0; i < config.demoN; i++) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (VariableMeta meta : model.metadata.values()) {
                Object value;
                if (random.nextDouble() < 0.06) value = null;
                else if ("completion".equals(meta.kind)) value = Boolean.TRUE;
                else if ("hist".equals(meta.kind) || "discrete".equals(meta.kind)) {
                    value = demoNumericValue(meta, random);
                } else if ("date".equals(meta.kind)) {
                    int day = 1 + random.nextInt(365);
                    value = LocalDate.ofYearDay(2025, day).toString();
                } else if (meta.multi) {
                    List<String> selected = new ArrayList<String>();
                    for (String code : positiveCodes(meta)) if (random.nextDouble() < 0.28) selected.add(code);
                    value = selected;
                } else {
                    List<String> choices = positiveCodes(meta);
                    value = choices.isEmpty() ? (random.nextBoolean() ? "1" : "2") : choices.get(Math.min(choices.size() - 1, (int) Math.abs(random.nextGaussian() * 1.2)));
                }
                row.put(meta.variable, value);
            }
            if (model.weighted) row.put("_w", 0.5 + random.nextDouble() * 2.5);
            if (model.mapGeometry != null) {
                double lon = 0, lat = 0; boolean found = false;
                for (int tries = 0; tries < 100; tries++) {
                    lon = model.mapGeometry.minLon + random.nextDouble() * (model.mapGeometry.maxLon - model.mapGeometry.minLon);
                    lat = model.mapGeometry.minLat + random.nextDouble() * (model.mapGeometry.maxLat - model.mapGeometry.minLat);
                    if (model.mapGeometry.contains(lon, lat)) { found = true; break; }
                }
                if (found) {
                    row.put("_x", round(model.mapGeometry.projectX(lon, lat), 2));
                    row.put("_y", round(model.mapGeometry.projectY(lon, lat), 2));
                    row.put("_lat", round(lat, 6));
                    row.put("_lon", round(lon, 6));
                    model.mapValid++;
                } else { row.put("_x", null); row.put("_y", null); row.put("_lat", null); row.put("_lon", null); model.mapMissing++; }
                if (!blank(config.mapBy)) {
                    Object group = row.get(model.mapBy);
                    VariableMeta mapMeta = model.metadata.get(model.mapBy);
                    row.put("_mapby", configuredMissing(mapMeta, group) ? null : group);
                }
            }
            out.add(row);
        }
        return out;
    }

    private static long demoNumericValue(VariableMeta meta, Random random) {
        String label = meta == null || meta.label == null ? "" : meta.label.toLowerCase(Locale.ROOT);
        // A generic log-normal preview is visibly wrong for percentage
        // questions because it routinely produces values above 100.  Keep
        // percentage/share previews in their natural range while retaining a
        // small fully-owned/100% tail so the outlier-focused display remains
        // demonstrable.  Questions about percentage *change* are left
        // unconstrained because negative values may be meaningful there.
        boolean percentage = !label.contains("change")
                && (label.contains("percentage") || label.contains("percent")
                || label.contains("proportion") || label.contains("share") || label.contains("%"));
        if (percentage) {
            if (random.nextDouble() < 0.08) return 100L;
            return Math.round(Math.max(0, Math.min(100, 28 + random.nextGaussian() * 13)));
        }
        if (meta != null && "discrete".equals(meta.kind)) {
            return Math.max(0L, Math.round(4 + random.nextGaussian() * 2.1));
        }
        double base = Math.exp(random.nextGaussian() * 1.15 + 4.4);
        // High positive values exercise Tukey outlier handling without
        // inventing arbitrary negative responses that violate many surveys'
        // validation rules.
        if (random.nextDouble() < 0.07) base *= 6 + random.nextDouble() * 4;
        return Math.round(base);
    }

    private static List<String> positiveCodes(VariableMeta meta) {
        List<String> out = new ArrayList<String>();
        for (String code : meta.order) if (!meta.specialCodes.contains(code)) out.add(code);
        return out;
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private static String requireDataColumn(CsvTable table, String name, boolean demo, String role) {
        if (demo) return name;
        String canonical = table == null ? null : table.canonical(name);
        if (canonical == null) throw new IllegalArgumentException("The " + role + " variable is not present in the data: " + name);
        return canonical;
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> out = new LinkedHashSet<String>();
        for (String value : values) out.add(value.toLowerCase(Locale.ROOT));
        return out;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
