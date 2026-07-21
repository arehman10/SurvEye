import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Entry point for the Survey Solutions dashboard engine and Stata wrapper. */
public final class SurvEye {
    public static final String VERSION = "2.1.2";

    private SurvEye() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                usage();
                return;
            }
            if ("--version".equals(args[0])) {
                System.out.println("SurvEye " + VERSION);
                return;
            }
            if ("--inspect".equals(args[0]) || "--list-vars".equals(args[0])) {
                if (args.length < 2) throw new IllegalArgumentException("Questionnaire HTML is required after " + args[0] + ".");
                describe(args[1], null);
                return;
            }
            DashboardConfig config;
            if ("--config".equals(args[0])) {
                if (args.length < 2) throw new IllegalArgumentException("A configuration filename is required after --config.");
                config = readConfig(args[1]);
                config.configFile = args[1];
            } else config = legacyConfig(args);
            run(config);
        } catch (Exception error) {
            System.err.println("Error: " + friendly(error));
            if (Boolean.getBoolean("surveye.debug")) error.printStackTrace(System.err);
            System.exit(2);
        }
    }

    /**
     * Stata javacall entry point. The first argument is a UTF-8, tab-delimited
     * configuration file. An optional second argument supplies a fallback
     * status filename for configuration-read errors. Expected validation errors
     * are returned through the status file, keeping raw Java traces out of
     * Stata's Results window.
     */
    public static int stata(String[] args) {
        if (args == null || args.length < 1 || args[0] == null || args[0].trim().isEmpty()) {
            System.err.println("surveye: configuration filename is required");
            return 198;
        }
        String fallbackStatus = args.length > 1 && args[1] != null ? args[1] : "";
        DashboardConfig config = new DashboardConfig();
        config.stataPlugin = true;
        config.configFile = args[0];
        config.status = fallbackStatus;
        try {
            DashboardConfig parsed = readConfig(args[0]);
            parsed.stataPlugin = true;
            parsed.configFile = args[0];
            if (blank(parsed.status)) parsed.status = config.status;
            config = parsed;
            run(config);
            return 0;
        } catch (Exception error) {
            String message = friendly(error);
            Map<String, String> status = new LinkedHashMap<String, String>();
            status.put("success", "0");
            status.put("message", message);
            status.put("engine_version", VERSION);
            status.put("questionnaire", safe(config.questionnaire));
            status.put("output", safe(config.output));
            String statusTarget = safeErrorDestination(config.status, config, true)
                    ? config.status : (safeErrorDestination(fallbackStatus, config, true) ? fallbackStatus : "");
            if (!writeStatusQuietly(statusTarget, status)) {
                // The Stata wrapper normally reads the status file and prints
                // the friendly message.  Emit it here only when that handoff
                // itself failed, so capture noisily can still expose it.
                System.err.println("surveye: " + message);
            }
            if (safeErrorDestination(config.diagnostics, config, false)) {
                writeDiagnosticsQuietly(config.diagnostics, config, null, error);
            }
            return 0;
        }
    }

    private static void run(DashboardConfig config) throws Exception {
        validateConfig(config);
        if ("describe".equals(config.mode)) {
            describe(config.questionnaire, config);
            return;
        }
        QuestionnaireSpec spec = HtmlQuestionnaireParser.parseFile(config.questionnaire);
        if (config.strict && !spec.warnings.isEmpty()) {
            throw new IllegalArgumentException("Strict mode rejected " + spec.warnings.size() + " questionnaire parser warning(s). Run describe to inspect them.");
        }
        CsvTable table = config.demo ? null : CsvTable.read(config.data);
        if (config.strict && table != null && !table.warnings.isEmpty()) {
            throw new IllegalArgumentException("Strict mode rejected " + table.warnings.size()
                    + " CSV data warning(s). Correct the rectangular data file or run without strict.");
        }
        DashboardBuilder.Result result = DashboardBuilder.build(spec, table, config);
        String html = DashboardRenderer.render(result.model, result.data);
        writeAtomic(config.output, html, config.replace);
        Map<String, String> status = status(result.model, config);
        writeStatus(config.status, status);
        writeDiagnosticsQuietly(config.diagnostics, config, result.model, null);
        if (!config.stataPlugin) printSuccess(result.model, config);
    }

    private static void validateConfig(DashboardConfig config) throws IOException {
        if (blank(config.questionnaire)) throw new IllegalArgumentException("Questionnaire HTML was not specified.");
        Path questionnaire = Paths.get(config.questionnaire).toAbsolutePath().normalize();
        if (!Files.isRegularFile(questionnaire)) throw new IOException("Questionnaire HTML not found: " + questionnaire.toAbsolutePath());
        config.questionnaire = questionnaire.toString();

        if (!blank(config.status)) config.status = Paths.get(config.status).toAbsolutePath().normalize().toString();
        if (!blank(config.diagnostics)) config.diagnostics = Paths.get(config.diagnostics).toAbsolutePath().normalize().toString();
        Path questionnaireKey = pathKey(config.questionnaire);
        Path configKey = pathKey(config.configFile);
        Path statusKey = pathKey(config.status);
        Path diagnosticsKey = pathKey(config.diagnostics);
        requireDifferent(statusKey, "Status file", configKey, "configuration file");
        requireDifferent(diagnosticsKey, "Diagnostics file", configKey, "configuration file");
        requireDifferent(diagnosticsKey, "Diagnostics file", questionnaireKey, "questionnaire");
        requireDifferent(statusKey, "Status file", questionnaireKey, "questionnaire");
        requireDifferent(diagnosticsKey, "Diagnostics file", statusKey, "status file");

        if (!blank(config.data)) config.data = Paths.get(config.data).toAbsolutePath().normalize().toString();
        if (!blank(config.output)) config.output = Paths.get(config.output).toAbsolutePath().normalize().toString();
        if (!blank(config.boundaries)) config.boundaries = Paths.get(config.boundaries).toAbsolutePath().normalize().toString();
        if (!blank(config.logo)) config.logo = Paths.get(config.logo).toAbsolutePath().normalize().toString();
        Path dataKey = pathKey(config.data);
        Path outputKey = pathKey(config.output);
        Path boundariesKey = pathKey(config.boundaries);
        Path logoKey = pathKey(config.logo);
        requireDifferent(diagnosticsKey, "Diagnostics file", outputKey, "output file");
        requireDifferent(statusKey, "Status file", outputKey, "output file");
        requireDifferent(diagnosticsKey, "Diagnostics file", dataKey, "CSV data file");
        requireDifferent(statusKey, "Status file", dataKey, "CSV data file");
        requireDifferent(diagnosticsKey, "Diagnostics file", boundariesKey, "boundaries file");
        requireDifferent(diagnosticsKey, "Diagnostics file", logoKey, "logo file");
        requireDifferent(statusKey, "Status file", boundariesKey, "boundaries file");
        requireDifferent(statusKey, "Status file", logoKey, "logo file");

        if ("main".equals(config.mode)) config.mode = "build";
        if (!"build".equals(config.mode) && !"demo".equals(config.mode) && !"describe".equals(config.mode)) {
            throw new IllegalArgumentException("mode must be build, demo, or describe.");
        }
        config.demo = config.demo || "demo".equals(config.mode);
        if ("describe".equals(config.mode)) return;
        if (blank(config.output)) throw new IllegalArgumentException("Output HTML was not specified.");
        requireDifferent(outputKey, "Output file", questionnaireKey, "questionnaire");
        requireDifferent(outputKey, "Output file", statusKey, "status file");
        requireDifferent(outputKey, "Output file", diagnosticsKey, "diagnostics file");
        requireDifferent(outputKey, "Output file", configKey, "configuration file");

        if (!config.demo) {
            if (blank(config.data)) throw new IllegalArgumentException("A CSV data file is required outside demo mode.");
            Path data = Paths.get(config.data);
            if (!Files.isRegularFile(data)) throw new IOException("CSV data file not found: " + data.toAbsolutePath());
            config.data = data.toString();
            requireDifferent(outputKey, "Output file", dataKey, "CSV data file");
        }

        requireDifferent(outputKey, "Output file", boundariesKey, "boundaries file");
        requireDifferent(outputKey, "Output file", logoKey, "logo file");

        if (config.demoN < 1 || config.demoN > 100000) throw new IllegalArgumentException("Demo n() must be between 1 and 100,000.");
        if (config.maxCategories < 2 || config.maxCategories > 200) throw new IllegalArgumentException("maxcategories() must be between 2 and 200.");
        if (config.maxPanels < 0 || config.maxPanels > 5000) throw new IllegalArgumentException("maxpanels() must be between 0 and 5,000.");
        if (!blank(config.density) && !config.density.equalsIgnoreCase("compact")
                && !config.density.equalsIgnoreCase("comfortable")) {
            throw new IllegalArgumentException("density() must be compact or comfortable.");
        }
        if (!blank(config.uiLanguage) && !config.uiLanguage.equalsIgnoreCase("auto")
                && !config.uiLanguage.equalsIgnoreCase("english")
                && !config.uiLanguage.equalsIgnoreCase("arabic")
                && !config.uiLanguage.equalsIgnoreCase("urdu")
                && !config.uiLanguage.equalsIgnoreCase("en")
                && !config.uiLanguage.equalsIgnoreCase("ar")
                && !config.uiLanguage.equalsIgnoreCase("ur")) {
            throw new IllegalArgumentException("uilanguage() must be auto, english (en), arabic (ar), or urdu (ur)." );
        }
        if (!blank(config.direction) && !config.direction.equalsIgnoreCase("auto")
                && !config.direction.equalsIgnoreCase("ltr")
                && !config.direction.equalsIgnoreCase("rtl")) {
            throw new IllegalArgumentException("direction() must be auto, ltr, or rtl.");
        }
        if (!blank(config.mapType) && !config.mapType.equalsIgnoreCase("points")
                && !config.mapType.equalsIgnoreCase("cluster") && !config.mapType.equalsIgnoreCase("heat")) {
            throw new IllegalArgumentException("maptype() must be points, cluster, or heat.");
        }
        if (!blank(config.baseMap) && !config.baseMap.equalsIgnoreCase("google_hybrid")
                && !config.baseMap.equalsIgnoreCase("google_sat")
                && !config.baseMap.equalsIgnoreCase("google_road")
                && !config.baseMap.equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("basemap() must be google_hybrid, google_sat, google_road, or osm.");
        }
        if (!blank(config.mapLevel) && !config.mapLevel.equalsIgnoreCase("auto")
                && !config.mapLevel.equalsIgnoreCase("country") && !config.mapLevel.equalsIgnoreCase("admin2")) {
            throw new IllegalArgumentException("maplevel() must be country or admin2.");
        }
        if (!blank(config.mapBy) && (blank(config.latitude) || blank(config.longitude))) {
            throw new IllegalArgumentException("mapby() requires latitude() and longitude().");
        }
        if (!blank(config.weightType) && !config.weightType.equalsIgnoreCase("aweight")
                && !config.weightType.equalsIgnoreCase("fweight") && !config.weightType.equalsIgnoreCase("pweight")
                && !config.weightType.equalsIgnoreCase("iweight")) {
            throw new IllegalArgumentException("weighttype must be aweight, fweight, pweight, or iweight.");
        }
        if (blank(config.weight) != blank(config.weightType)) {
            throw new IllegalArgumentException("weight and weighttype must be specified together.");
        }
        if (!(config.ciLevel > 50.0 && config.ciLevel < 100.0)) {
            throw new IllegalArgumentException("level() must be greater than 50 and less than 100.");
        }
    }

    private static Path pathKey(String filename) throws IOException {
        if (blank(filename)) return null;
        Path path = Paths.get(filename).toAbsolutePath().normalize();
        if (Files.exists(path)) return path.toRealPath();
        Path parent = path.getParent();
        if (parent != null && Files.exists(parent)) {
            return parent.toRealPath().resolve(path.getFileName()).normalize();
        }
        return path;
    }

    private static void requireDifferent(Path first, String firstLabel, Path second, String secondLabel) {
        if (first == null || second == null) return;
        if (samePath(first, second)) {
            throw new IllegalArgumentException(firstLabel + " may not overwrite the " + secondLabel + ".");
        }
    }

    private static boolean samePath(Path first, Path second) {
        if (first.equals(second)) return true;
        try {
            if (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second)) return true;
        } catch (IOException ignored) {
            // Fall through to normalized provider-specific comparison.
        }
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && first.toString().equalsIgnoreCase(second.toString());
    }

    /** Never let error reporting overwrite an input or another output. */
    private static boolean safeErrorDestination(String target, DashboardConfig config, boolean statusTarget) {
        if (blank(target) || config == null) return false;
        try {
            Path targetKey = pathKey(target);
            String[] protectedPaths = statusTarget
                    ? new String[]{config.configFile, config.questionnaire, config.data, config.output, config.boundaries, config.logo, config.diagnostics}
                    : new String[]{config.configFile, config.questionnaire, config.data, config.output, config.boundaries, config.logo, config.status};
            for (String protectedPath : protectedPaths) {
                Path protectedKey = pathKey(protectedPath);
                if (protectedKey != null && samePath(targetKey, protectedKey)) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void describe(String questionnaire, DashboardConfig config) throws Exception {
        QuestionnaireSpec spec = HtmlQuestionnaireParser.parseFile(questionnaire);
        if (config != null && config.strict && !spec.warnings.isEmpty()) {
            throw new IllegalArgumentException("Strict mode rejected " + spec.warnings.size() + " questionnaire parser warning(s). Run describe without strict to inspect them.");
        }
        CsvTable table = null;
        if (config != null && !blank(config.data) && Files.isRegularFile(Paths.get(config.data))) table = CsvTable.read(config.data);
        int chartableTotal = 0;
        List<String> chartVars = new ArrayList<String>();
        List<String> filterCandidates = new ArrayList<String>();
        List<String> gpsCandidates = new ArrayList<String>();
        boolean print = config == null || !config.stataPlugin;
        if (print) {
            System.out.println();
            System.out.println("Survey Solutions questionnaire");
            System.out.println("  Title      " + spec.title);
            System.out.println("  Sections   " + spec.sections.size());
            System.out.println("  Questions  " + spec.questions.size());
            System.out.println();
            System.out.printf(Locale.ROOT, "  %-4s %-58s %9s %10s%n", "No.", "Section", "Questions", "In data");
            System.out.println("  " + repeat('-', 86));
        }
        for (QuestionSection section : spec.sections) {
            int inData = 0;
            for (Question q : section.questions) {
                if (basicChartable(q)) { chartableTotal++; chartVars.add(q.variable); }
                if (table != null && table.hasQuestion(q)) inData++;
                if ("gps".equals(q.type) || q.variable.toLowerCase(Locale.ROOT).matches(".*(latitude|longitude|lat|lon|gps).*")) gpsCandidates.add(q.variable);
                if (DashboardBuilder.isDefaultFilterCandidate(q)) filterCandidates.add(q.variable);
            }
            if (print) {
                System.out.printf(Locale.ROOT, "  %-4d %-58s %9d %10s%n", section.number,
                        Util.shortLabel(section.title, "Section " + section.number, 58), section.questions.size(), table == null ? "—" : Integer.toString(inData));
            }
        }
        if (print) {
            System.out.println();
            System.out.println("  Chartable  " + chartableTotal);
            if (!filterCandidates.isEmpty()) System.out.println("  Filters    " + joinLimited(filterCandidates, 12));
            if (!gpsCandidates.isEmpty()) System.out.println("  GPS        " + joinLimited(gpsCandidates, 12));
            if (!spec.warnings.isEmpty()) System.out.println("  Flags      " + spec.warnings.size());
            System.out.println();
        }

        if (config != null) {
            Map<String, String> status = new LinkedHashMap<String, String>();
            status.put("success", "1"); status.put("message", "Questionnaire inspected successfully.");
            status.put("title", spec.title); status.put("k_sections", Integer.toString(spec.sections.size()));
            status.put("k_questions", Integer.toString(spec.questions.size())); status.put("k_chartable", Integer.toString(chartableTotal));
            status.put("k_charted", Integer.toString(chartableTotal));
            status.put("chartvars", join(chartVars)); status.put("filter_candidates", join(filterCandidates));
            status.put("gps_candidates", join(gpsCandidates)); status.put("engine_version", VERSION);
            List<String> sections = new ArrayList<String>();
            for (QuestionSection section : spec.sections) {
                // ASCII | is the Stata-side record separator for r(sections).
                sections.add(section.number + ": " + safe(section.title).replace('|', '\uff5c'));
            }
            status.put("sections", String.join("|", sections));
            status.put("warnings", Integer.toString(spec.warnings.size()));
            status.put("questionnaire", Util.absolute(questionnaire));
            writeStatus(config.status, status);
            writeDiagnosticsQuietly(config.diagnostics, config, null, null);
        }
    }

    private static boolean basicChartable(Question q) {
        return DashboardBuilder.isDefaultChartable(q);
    }

    private static Map<String, String> status(DashboardModel model, DashboardConfig config) {
        Map<String, String> status = new LinkedHashMap<String, String>();
        status.put("success", "1"); status.put("message", "Dashboard created successfully."); status.put("title", model.title);
        int chartedVariables = chartedVariableCount(model);
        status.put("N", Integer.toString(model.observations)); status.put("k_requested", Integer.toString(chartedVariables + model.skipped));
        status.put("k_charted", Integer.toString(chartedVariables)); status.put("k_panels", Integer.toString(model.panels.size()));
        status.put("k_families", Integer.toString(model.familyPanels)); status.put("k_comparisons", Integer.toString(model.comparisonPanels));
        status.put("k_skipped", Integer.toString(model.skipped));
        status.put("k_sections", Integer.toString(model.sections.size())); status.put("k_filters", Integer.toString(model.filters.size()));
        List<String> chartVars = new ArrayList<String>(); for (ChartPanel panel : model.panels) chartVars.addAll(panel.memberVariables());
        List<String> filters = new ArrayList<String>(); for (DashboardFilter filter : model.filters) filters.add(filter.variable);
        List<String> sections = new ArrayList<String>(); for (DashboardSection section : model.sections) sections.add(Integer.toString(section.number));
        status.put("chartvars", join(chartVars)); status.put("skippedvars", join(model.skippedVariables)); status.put("filters", join(filters)); status.put("sections", join(sections));
        status.put("warnings", Integer.toString(model.warnings.size())); status.put("weighted", model.weighted ? "1" : "0");
        status.put("has_map", model.mapGeometry == null ? "0" : "1"); status.put("map_N", Integer.toString(model.mapValid));
        status.put("map_missing", Integer.toString(model.mapMissing)); status.put("map_outside", Integer.toString(model.mapOutside));
        status.put("engine_version", VERSION); status.put("output", config.output); status.put("questionnaire", config.questionnaire);
        return status;
    }

    private static int chartedVariableCount(DashboardModel model) {
        java.util.LinkedHashSet<String> variables = new java.util.LinkedHashSet<String>();
        for (ChartPanel panel : model.panels) variables.addAll(panel.memberVariables());
        return variables.size();
    }

    private static void printSuccess(DashboardModel model, DashboardConfig config) {
        System.out.println();
        System.out.println("OK  Survey Solutions dashboard created");
        System.out.println();
        System.out.println("    Questionnaire   " + model.product);
        System.out.println("    Observations    " + model.observations);
        System.out.println("    Sections        " + model.sections.size() + " of " + model.sourceSections);
        System.out.println("    Indicators      " + chartedVariableCount(model) + " in " + model.panels.size() + " panels");
        if (!model.filters.isEmpty()) {
            List<String> names = new ArrayList<String>(); for (DashboardFilter filter : model.filters) names.add(filter.variable);
            System.out.println("    Filters         " + join(names));
        }
        if (model.mapGeometry != null) System.out.println("    GPS map         " + model.mapValid + " valid; " + model.mapMissing + " omitted; " + model.mapOutside + " outside");
        System.out.println("    Saved           " + config.output);
        if (!model.warnings.isEmpty()) System.out.println("    Flags           " + model.warnings.size() + " (shown inside the dashboard)");
        System.out.println();
    }

    private static DashboardConfig readConfig(String filename) throws IOException {
        DashboardConfig config = new DashboardConfig();
        List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String key = line.substring(0, tab).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(tab + 1);
            set(config, key, value);
        }
        return config;
    }

    private static void set(DashboardConfig c, String key, String value) {
        if (key.equals("questionnaire")) c.questionnaire = value;
        else if (key.equals("data")) c.data = value;
        else if (key.equals("output") || key.equals("saving")) c.output = value;
        else if (key.equals("status")) c.status = value;
        else if (key.equals("diagnostics")) c.diagnostics = value;
        else if (key.equals("title")) c.title = value;
        else if (key.equals("subtitle")) c.subtitle = value;
        else if (key.equals("variables") || key.equals("selectedvars") || key.equals("varlist")) c.variables = value;
        else if (key.equals("sections")) c.sections = value;
        else if (key.equals("sectionmatch")) c.sectionMatch = value;
        else if (key.equals("exclude")) c.exclude = value;
        else if (key.equals("filters")) c.filters = value;
        else if (key.equals("highlights")) c.highlights = value;
        else if (key.equals("keymessages")) c.keyMessages = value;
        else if (key.equals("customsections")) c.customSections = value;
        else if (key.equals("customvars") || key.equals("customvariables")) c.customVariables = value;
        else if (key.equals("addtosections")) c.addToSections = value;
        else if (key.equals("vargroups")) c.varGroups = value;
        else if (key.equals("autogroups")) c.autoGroups = bool(value);
        else if (key.equals("noautogroups")) c.autoGroups = !bool(value);
        else if (key.equals("ungroupvars")) c.ungroupVariables = value;
        else if (key.startsWith("datalabel.")) c.dataLabels.put(key.substring("datalabel.".length()), value);
        else if (key.startsWith("datatype.")) c.dataTypes.put(key.substring("datatype.".length()), value.trim().toLowerCase(Locale.ROOT));
        else if (key.startsWith("dataformat.")) c.dataFormats.put(key.substring("dataformat.".length()), value.trim().toLowerCase(Locale.ROOT));
        else if (key.startsWith("datavalue.")) addDataValueLabel(c, key, value);
        else if (key.equals("bars")) c.bars = value;
        else if (key.equals("donuts")) c.donuts = value;
        else if (key.equals("histograms")) c.histograms = value;
        else if (key.equals("discrete")) c.discreteVariables = value;
        else if (key.equals("continuous")) c.continuousVariables = value;
        else if (key.equals("autodiscrete")) c.autoDiscrete = bool(value);
        else if (key.equals("noautodiscrete")) c.autoDiscrete = !bool(value);
        else if (key.equals("compare")) c.compareVariables = value;
        else if (key.equals("compareby")) c.compareBy = value;
        else if (key.equals("comparetitle")) c.compareTitle = value;
        else if (key.equals("comparelevels")) c.compareLevels = value;
        else if (key.equals("missingcodes")) c.missingCodes = value;
        else if (key.equals("latitude")) c.latitude = value;
        else if (key.equals("longitude")) c.longitude = value;
        else if (key.equals("country")) c.country = value;
        else if (key.equals("boundaries")) c.boundaries = value;
        else if (key.equals("maplevel") && !blank(value)) c.mapLevel = value;
        else if (key.equals("maptype") && !blank(value)) c.mapType = value;
        else if (key.equals("basemap") && !blank(value)) c.baseMap = value;
        else if (key.equals("mapby")) c.mapBy = value;
        else if (key.equals("maptitle")) c.mapTitle = value;
        else if (key.equals("weight") || key.equals("weightvar")) c.weight = value;
        else if (key.equals("weighttype")) c.weightType = value;
        else if (key.equals("showci") || key.equals("ci")) c.showCI = bool(value);
        else if (key.equals("cilevel") || key.equals("level")) c.ciLevel = number(value, "level");
        else if (key.equals("logo")) c.logo = value;
        else if (key.equals("theme") && !blank(value)) c.theme = value;
        else if (key.equals("density") && !blank(value)) c.density = value;
        else if (key.equals("uilanguage") && !blank(value)) c.uiLanguage = value;
        else if (key.equals("direction") && !blank(value)) c.direction = value;
        else if (key.equals("note")) c.note = value;
        else if (key.equals("source")) c.source = value;
        else if (key.equals("disclaimer") && !blank(value)) c.disclaimer = value;
        else if (key.equals("mode")) c.mode = value.trim().toLowerCase(Locale.ROOT);
        else if (key.equals("demo")) c.demo = bool(value);
        else if (key.equals("demon") || key.equals("n")) c.demoN = integer(value, "n");
        else if (key.equals("seed")) c.seed = longValue(value, "seed");
        else if (key.equals("showempty")) c.showEmpty = bool(value);
        else if (key.equals("strict")) c.strict = bool(value);
        else if (key.equals("replace")) c.replace = bool(value);
        else if (key.equals("maxcategories")) c.maxCategories = integer(value, "maxcategories");
        else if (key.equals("maxpanels")) c.maxPanels = integer(value, "maxpanels");
    }

    private static void addDataValueLabel(DashboardConfig config, String key, String value) {
        String rest = key.substring("datavalue.".length());
        int dot = rest.lastIndexOf('.');
        String variable = dot > 0 ? rest.substring(0, dot) : rest;
        int split = value.indexOf("::");
        if (split <= 0 || variable.isEmpty()) return;
        Map<String, String> labels = config.dataValueLabels.get(variable);
        if (labels == null) {
            labels = new LinkedHashMap<String, String>();
            config.dataValueLabels.put(variable, labels);
        }
        labels.put(value.substring(0, split).trim(), value.substring(split + 2));
    }

    private static DashboardConfig legacyConfig(String[] args) {
        DashboardConfig config = new DashboardConfig();
        List<String> positional = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--demo".equals(arg)) { config.demo = true; config.mode = "demo"; }
            else if ("--strict".equals(arg)) config.strict = true;
            else if ("--replace".equals(arg)) config.replace = true;
            else if ("--show-empty".equals(arg) || "--showempty".equals(arg)) config.showEmpty = true;
            else if ("--ci".equals(arg)) config.showCI = true;
            else if (arg.startsWith("--") && i + 1 < args.length) {
                String key = arg.substring(2).replace("-", ""); set(config, key, args[++i]);
            } else positional.add(arg);
        }
        if (config.demo) {
            if (positional.size() < 2) throw new IllegalArgumentException("Demo syntax: questionnaire.html --demo output.html");
            config.questionnaire = positional.get(0); config.output = positional.get(1);
        } else {
            if (positional.size() < 3) throw new IllegalArgumentException("Syntax: questionnaire.html data.csv output.html [options]");
            config.questionnaire = positional.get(0); config.data = positional.get(1); config.output = positional.get(2);
        }
        config.replace = true;
        return config;
    }

    private static void writeAtomic(String filename, String content, boolean replace) throws IOException {
        Path output = Paths.get(filename).toAbsolutePath().normalize();
        if (Files.exists(output) && !replace) throw new IOException("Output already exists: " + output + ". Specify replace to overwrite it.");
        Path parent = output.getParent();
        if (parent != null && !Files.isDirectory(parent)) throw new IOException("Output directory does not exist: " + parent);
        Path temporary = Files.createTempFile(parent, ".surveye-", ".tmp");
        try {
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            try {
                if (replace) Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                if (replace) Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeStatus(String filename, Map<String, String> values) throws IOException {
        if (blank(filename)) return;
        Path path = Paths.get(filename);
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        try {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey()); writer.write('\t'); writer.write(statusValue(entry.getValue())); writer.newLine();
            }
        } finally { writer.close(); }
    }

    private static boolean writeStatusQuietly(String filename, Map<String, String> values) {
        try {
            writeStatus(filename, values);
            return !blank(filename);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writeDiagnosticsQuietly(String filename, DashboardConfig config, DashboardModel model, Throwable error) {
        if (blank(filename)) return;
        try {
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), StandardCharsets.UTF_8);
            try {
                writer.write("SurvEye diagnostics\n"); writer.write("engine_version\t" + VERSION + "\n");
                writer.write("questionnaire\t" + safe(config.questionnaire) + "\n"); writer.write("data\t" + safe(config.data) + "\n");
                writer.write("output\t" + safe(config.output) + "\n"); writer.write("mode\t" + safe(config.mode) + "\n");
                if (model != null) {
                    writer.write("observations\t" + model.observations + "\n"); writer.write("panels\t" + model.panels.size() + "\n");
                    for (String warning : model.warnings) writer.write("warning\t" + statusValue(warning) + "\n");
                }
                if (error != null) {
                    writer.write("error\t" + statusValue(friendly(error)) + "\n");
                    StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace)); writer.write(trace.toString());
                }
            } finally { writer.close(); }
        } catch (Exception ignored) { /* diagnostics must not mask the main result */ }
    }

    private static String friendly(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().trim().isEmpty())) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? current.getClass().getSimpleName() : message.trim();
    }

    private static boolean bool(String value) {
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on"));
    }

    private static int integer(String value, String name) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { throw new IllegalArgumentException(name + " must be an integer."); }
    }

    private static long longValue(String value, String name) {
        try { return Long.parseLong(value.trim()); } catch (Exception e) { throw new IllegalArgumentException(name + " must be an integer."); }
    }

    private static double number(String value, String name) {
        try { return Double.parseDouble(value.trim()); } catch (Exception e) { throw new IllegalArgumentException(name + " must be numeric."); }
    }

    private static String statusValue(String value) { return safe(value).replace('\t', ' ').replace('\r', ' ').replace('\n', ' '); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String join(List<String> values) { return String.join(" ", values); }
    private static String joinLimited(List<String> values, int limit) {
        List<String> part = values.subList(0, Math.min(limit, values.size()));
        return join(part) + (values.size() > limit ? " …" : "");
    }
    private static String repeat(char c, int n) { StringBuilder out = new StringBuilder(n); for (int i = 0; i < n; i++) out.append(c); return out.toString(); }

    private static void usage() {
        System.out.println("SurvEye " + VERSION + "\n"
                + "  java -jar surveye.jar questionnaire.html data.csv output.html [options]\n"
                + "  java -jar surveye.jar questionnaire.html --demo output.html [options]\n"
                + "  java -jar surveye.jar --inspect questionnaire.html\n"
                + "  java -jar surveye.jar --config settings.tsv\n\n"
                + "Common options: --title TEXT --variables a,b,c --sections 3,5 --filters region,size\n"
                + "Map options: --latitude lat --longitude lon --country NAME [--boundaries layer.zip]\n"
                + "Appearance: --theme worldbank|clean|forest|dark --density compact|comfortable --logo image.png");
    }
}
