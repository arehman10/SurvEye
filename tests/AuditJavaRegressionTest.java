import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small adversarial data fixtures for the September 2026 code audit. */
public final class AuditJavaRegressionTest {
    private static Path temporary;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected project root.");
        Path root = Paths.get(args[0]);
        temporary = Files.createTempDirectory("surveye-audit-java-");
        try {
            privacyAcrossRoles();
            explicitDataOnlyText();
            exactQuestionnaireCategoryCodes();
            negativeMeasurements();
            explicitComparisonLevels();
            xmlLabels(root);
            emptyCsvRecords();
            panelDependencies();
            printableTypeOverrides();
            System.out.println("PASS Java audit regressions: privacy, exact category codes, signed numeric values, compare levels, XML labels, CSV records, panel pruning, printable types");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void privacyAcrossRoles() throws Exception {
        CsvTable data = csv("privacy.csv", "a,b,notes,lat,lon\n1,0,Private contact 555-0199,-33.86,151.2\n0,1,,-33.86,151.2\n1,0,-9,-33.86,151.2\n");
        for (String role : Arrays.asList("chart", "bar", "hist", "donut", "filter", "table", "tableby", "highlight", "compare", "map")) {
            QuestionnaireSpec spec = binarySpec("a", "b");
            add(spec, question("notes", "Contact note", "text"));
            DashboardConfig c = config();
            c.variables = "a b";
            c.missingCodes = "-9";
            // The Stata wrapper supplies this automatically for selected and
            // auxiliary fields; it must not override an actual text question.
            c.dataTypes.put("notes", "single");
            Map<String, String> privateLabels = new LinkedHashMap<String, String>();
            privateLabels.put("Private contact 555-0199", "Private contact 555-0199");
            c.dataValueLabels.put("notes", privateLabels);
            if (Arrays.asList("chart", "bar", "hist", "donut").contains(role)) c.variables += " notes";
            if ("bar".equals(role)) c.bars = "notes";
            if ("hist".equals(role)) c.histograms = "notes";
            if ("donut".equals(role)) c.donuts = "notes";
            if ("filter".equals(role)) c.filters = "notes";
            if ("table".equals(role)) { c.tableBy = "a"; c.tableVariables = "notes"; }
            if ("tableby".equals(role)) { c.tableBy = "notes"; c.tableVariables = "a"; }
            if ("highlight".equals(role)) c.highlights = "notes";
            if ("compare".equals(role)) { c.compareVariables = "a b"; c.compareBy = "notes"; }
            if ("map".equals(role)) {
                c.latitude = "lat"; c.longitude = "lon"; c.country = "Australia"; c.mapBy = "notes";
            }
            DashboardBuilder.Result r = DashboardBuilder.build(spec, data, c);
            VariableMeta m = r.model.metadata.get("notes");
            check(m != null && "completion".equals(m.kind), role + " bypassed text completion metadata");
            check(!m.labels.toString().contains("Private") && m.labels.size() == 2,
                    role + " embedded private response labels: " + m.labels);
            check(Boolean.TRUE.equals(r.data.get(0).get("notes"))
                            && r.data.get(1).get("notes") == null && r.data.get(2).get("notes") == null,
                    role + " did not reduce notes to true/null/null");
            check(!Util.json(r.data).contains("Private"), role + " embedded raw private text");
            if ("filter".equals(role)) {
                check(r.model.filters.get(0).choices.size() == 2, "Text filter must offer only answered/missing");
                check("true".equals(r.model.filters.get(0).choices.get(0).value)
                                && "false".equals(r.model.filters.get(0).choices.get(1).value),
                        "Text filter levels do not match completion data");
            }
            if ("compare".equals(role)) check(r.model.panels.get(0).compareLevels.equals(Arrays.asList("true", "false")),
                    "Comparison of text completion exposed original levels");
            if ("map".equals(role)) check(r.model.mapGroups.equals(Arrays.asList("true")),
                    "Map legend exposed original text levels: " + r.model.mapGroups);
        }
        // Media and linked response types must obey the same metadata boundary.
        for (String type : Arrays.asList("other", "gps", "picture", "audio", "single", "multi")) {
            QuestionnaireSpec spec = binarySpec("a");
            Question q = question("notes", "Sensitive field", type);
            if ("single".equals(type) || "multi".equals(type)) q.rawType = "linked text list";
            add(spec, q);
            DashboardConfig c = config(); c.variables = "a"; c.filters = "notes";
            DashboardBuilder.Result r = DashboardBuilder.build(spec, data, c);
            check("completion".equals(r.model.metadata.get("notes").kind), type + " auxiliary metadata exposed content");
            check(!Util.json(r.data).contains("Private"), type + " auxiliary DATA exposed content");
        }
    }

    private static void negativeMeasurements() throws Exception {
        QuestionnaireSpec spec = binarySpec();
        Question q = question("employment_change", "Change in number of employees since last year", "numeric");
        q.options.add(new QuestionOption("-9", "Don't know", true));
        add(spec, q);
        CsvTable data = csv("signed.csv", "employment_change\n-5\n0\n5\n-9\n");
        for (String style : Arrays.asList("auto", "continuous", "discrete")) {
            DashboardConfig c = config();
            if ("continuous".equals(style)) c.continuousVariables = "employment_change";
            if ("discrete".equals(style)) c.discreteVariables = "employment_change";
            DashboardBuilder.Result r = DashboardBuilder.build(spec, data, c);
            VariableMeta m = r.model.metadata.get("employment_change");
            check(!m.nonnegative, style + " silently imposed a nonnegative domain");
            check(m.specialCodes.contains("-9"), style + " discarded declared special codes");
            check(((Number) r.data.get(0).get("employment_change")).doubleValue() == -5,
                    style + " lost the legitimate negative measurement");
            double sum = 0; int n = 0;
            for (Map<String, Object> row : r.data) {
                double value = ((Number) row.get("employment_change")).doubleValue();
                String code = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
                if (m.specialCodes.contains(code)) continue;
                sum += value; n++;
            }
            check(n == 3 && sum / n == 0, style + " measurement metadata yields wrong valid sample or mean");
        }
        DashboardConfig discrete = config(); discrete.discreteVariables = "employment_change";
        try {
            DashboardBuilder.build(spec, csv("signed-fraction.csv", "employment_change\n-1.5\n2\n"), discrete);
            throw new AssertionError("Forced discrete accepted a noninteger negative measurement");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("noninteger"), "Wrong negative noninteger error: " + expected);
        }
    }

    private static void explicitDataOnlyText() throws Exception {
        CsvTable data = csv("data-only-text.csv", "a,notes,group\n1,Private contact 555-0199,01\n0,,1\n");
        for (String role : Arrays.asList("chart", "filter", "table", "highlight")) {
            DashboardConfig c = config(); c.variables = "a group";
            c.dataTypes.put("notes", "text");
            c.dataTypes.put("group", "string");
            if ("chart".equals(role)) c.variables += " notes";
            if ("filter".equals(role)) c.filters = "notes";
            if ("table".equals(role)) { c.tableBy = "a"; c.tableVariables = "notes"; }
            if ("highlight".equals(role)) c.highlights = "notes";
            DashboardBuilder.Result r = DashboardBuilder.build(binarySpec("a"), data, c);
            VariableMeta meta = r.model.metadata.get("notes");
            check(meta != null && "completion".equals(meta.kind)
                            && Boolean.TRUE.equals(r.data.get(0).get("notes")) && r.data.get(1).get("notes") == null,
                    "Explicit data-only text was not completion-reduced in " + role);
            check(!Util.json(r.data).contains("Private") && !meta.labels.toString().contains("Private"),
                    "Explicit data-only text exposed response content in " + role);
            check("01".equals(r.data.get(0).get("group")) && "1".equals(r.data.get(1).get("group"))
                            && !r.model.metadata.get("group").canonicalCodes,
                    "Explicit text support changed ordinary data-only string categories");
        }
    }

    private static void exactQuestionnaireCategoryCodes() throws Exception {
        QuestionnaireSpec spec = binarySpec("a", "b");
        Question region = question("region", "Region", "single");
        String[] codes = {"01", "1", "03", "04"};
        String[] labels = {"Western", "Central", "Northern", "Southern"};
        StringBuilder contents = new StringBuilder("a,b,region,lat,lon\n");
        for (int i = 0; i < codes.length; i++) {
            region.options.add(new QuestionOption(codes[i], labels[i], false));
            contents.append("1,0,").append(codes[i]).append(",-33.86,151.2\n");
        }
        add(spec, region);
        DashboardConfig c = config(); c.variables = "a b region"; c.filters = "region";
        c.tableBy = "region"; c.tableVariables = "a";
        c.compareVariables = "a b"; c.compareBy = "region"; c.compareLevels = "01|1|03|04";
        c.latitude = "lat"; c.longitude = "lon"; c.country = "Australia"; c.mapBy = "region";
        DashboardBuilder.Result r = DashboardBuilder.build(spec, csv("exact-codes.csv", contents.toString()), c);
        VariableMeta meta = r.model.metadata.get("region");
        check(!meta.canonicalCodes && meta.order.equals(Arrays.asList(codes)),
                "Questionnaire string codes were merged into numeric categories: " + meta.order);
        check(r.model.filters.get(0).choices.size() == codes.length,
                "Exact questionnaire codes were merged in filter choices");
        for (int i = 0; i < codes.length; i++) {
            check(codes[i].equals(r.data.get(i).get("region"))
                            && labels[i].equals(meta.labels.get(codes[i])),
                    "Questionnaire category identity or label lost for " + codes[i]);
            FilterChoice choice = r.model.filters.get(0).choices.get(i);
            check(codes[i].equals(choice.value) && labels[i].equals(choice.label),
                    "Filter category identity or label lost for " + codes[i]);
            check(codes[i].equals(r.data.get(i).get("_mapby")),
                    "Map grouping merged exact category " + codes[i]);
        }
        check(r.model.mapGroups.equals(Arrays.asList(codes)), "Map groups merged exact categories");
        check("region".equals(r.model.summaryTable.by), "Profile table lost exact-code grouping metadata");
        ChartPanel comparison = r.model.panels.get(0);
        check("comparison".equals(comparison.kind) && comparison.compareLevels.equals(Arrays.asList(codes)),
                "Comparison levels merged or reordered exact categories");

        DashboardBuilder.Result numeric = DashboardBuilder.build(binarySpec("a"),
                csv("integer-codes.csv", "a\n+1\n01\n0\n"), config());
        check(numeric.model.metadata.get("a").canonicalCodes
                        && "1".equals(numeric.data.get(0).get("a"))
                        && "1".equals(numeric.data.get(1).get("a")),
                "Canonical integer questionnaire options stopped normalizing numeric data tokens");
        QuestionnaireSpec paddedSpec = binarySpec();
        Question padded = question("region", "Region", "single");
        padded.options.add(new QuestionOption("01", "Western", false));
        padded.options.add(new QuestionOption("02", "Central", false));
        add(paddedSpec, padded);
        DashboardBuilder.Result aliases = DashboardBuilder.build(paddedSpec,
                csv("padded-integer-codes.csv", "region\n1\n2\n"), config());
        VariableMeta aliasMeta = aliases.model.metadata.get("region");
        check(aliasMeta.canonicalCodes && aliasMeta.order.equals(Arrays.asList("1", "2"))
                        && "Western".equals(aliasMeta.labels.get("1"))
                        && "Central".equals(aliasMeta.labels.get("2"))
                        && "1".equals(aliases.data.get(0).get("region")),
                "Unambiguous padded questionnaire codes stopped accepting numeric CSV aliases");
    }

    private static void explicitComparisonLevels() throws Exception {
        StringBuilder csv = new StringBuilder("a,b,region\n");
        for (int i = 1; i <= 100; i++) csv.append("1,0,").append(i).append('\n');
        DashboardConfig c = config(); c.compareVariables = "a b"; c.compareBy = "region"; c.compareLevels = "99 100";
        DashboardBuilder.Result r = DashboardBuilder.build(binarySpec("a", "b"), csv("regions.csv", csv.toString()), c);
        check(r.model.panels.get(0).compareLevels.equals(Arrays.asList("99", "100")),
                "Explicit levels beyond seven were lost or reordered");
        c.compareLevels = "99 101";
        try {
            DashboardBuilder.build(binarySpec("a", "b"), csv("regions.csv", csv.toString()), c);
            throw new AssertionError("Unobserved comparison level was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("101") && expected.getMessage().contains("not observed"),
                    "Unobserved level error is not specific");
        }
    }

    private static void xmlLabels(Path root) throws Exception {
        QuestionnaireSpec spec = QuestionnaireParser.parseFile(root.resolve("tests/surveycto_xform_questionnaire.xml").toString());
        String[][] expected = {{"resp_age", "How old is the respondent?"},
                {"monthly_sales", "Sales in the last calendar month"}, {"visit_date", "Date of visit"},
                {"worker_age", "Age of this worker"}, {"phone_number", "Best contact number"}};
        for (String[] pair : expected) check(pair[1].equals(spec.findQuestion(pair[0]).label),
                "XML input label lost for " + pair[0] + ": " + spec.findQuestion(pair[0]).label);
        check(spec.questions.size() == 11 && spec.sections.size() == 2, "XML nesting changed field or section counts");
    }

    private static void emptyCsvRecords() throws Exception {
        CsvTable data = csv("blank.csv", "a,b\r\n1,0\r\n,\r\n\r\n\"\",\"\"\r\n  \r\n0,1\r\n");
        check(data.rows.size() == 4, "Delimited/quoted empty records were dropped or blank lines added: " + data.rows.size());
        DashboardBuilder.Result r = DashboardBuilder.build(binarySpec("a", "b"), data, config());
        check(r.model.observations == 4 && r.data.get(1).get("a") == null && r.data.get(2).get("b") == null,
                "Empty records do not reach sample/missingness denominators");
        check(csv("one-column.csv", "a\n\"\"\n\n1\n").rows.size() == 2, "Single-column quoted empty record lost");
        check(csv("empty-eof.csv", "a,b\n1,0\n,").rows.size() == 2, "Final empty record without newline lost");
        check(csv("quoted-lines.csv", "a,b\n\"first\nsecond\",1\n").rows.size() == 1, "Quoted linebreak split an observation");
    }

    private static void panelDependencies() throws Exception {
        QuestionnaireSpec spec = binarySpec("a", "b", "c", "d", "e", "f", "g", "h", "dropped");
        CsvTable data = csv("cap.csv", "a,b,c,d,e,f,g,h,dropped,lat,lon,w\n1,0,1,0,1,0,1,0,1,-33.86,151.2,2\n0,1,0,1,0,1,0,1,0,-33.86,151.2,3\n");
        DashboardConfig c = config(); c.maxPanels = 1;
        DashboardBuilder.Result plain = DashboardBuilder.build(spec, data, c);
        check(plain.model.metadata.keySet().equals(new java.util.LinkedHashSet<String>(Arrays.asList("a")))
                        && plain.data.get(0).keySet().equals(plain.model.metadata.keySet()),
                "maxpanels left unused variable metadata or DATA embedded");

        c.compareVariables = "a b"; c.compareBy = "g";
        c.filters = "c"; c.highlights = "d"; c.tableBy = "e"; c.tableVariables = "f";
        c.usdVariables = "h"; c.usdRate = 300;
        c.latitude = "lat"; c.longitude = "lon"; c.country = "Australia"; c.mapBy = "c";
        c.weight = "w"; c.weightType = "fweight";
        DashboardBuilder.Result all = DashboardBuilder.build(spec, data, c);
        check(all.model.panels.size() == 1 && all.model.panels.get(0).memberVariables().size() == 2,
                "Cap broke a grouped/comparison panel");
        for (String name : Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h")) {
            check(all.model.metadata.containsKey(name) && all.data.get(0).containsKey(name), "Pruning lost dependency " + name);
        }
        check(!all.model.metadata.containsKey("dropped") && !all.data.get(0).containsKey("dropped")
                        && !all.model.requiredColumns.contains("dropped"), "Pruning retained a truly unused variable");
        check(all.model.requiredColumns.containsAll(Arrays.asList("w", "lat", "lon"))
                        && ((Number) all.data.get(0).get("_w")).doubleValue() == 2
                        && all.data.get(0).containsKey("_lat"), "Pruning damaged weight/map inputs");
    }

    private static void printableTypeOverrides() throws Exception {
        QuestionnaireSpec spec = binarySpec("a");
        Question unknown = question("sales", "Monthly sales", "text");
        unknown.rawType = "open or note"; add(spec, unknown);
        DashboardConfig c = config(); c.variables = "a sales"; c.dataTypes.put("sales", "numeric");
        DashboardBuilder.Result r = DashboardBuilder.build(spec, csv("printable.csv", "a,sales\n1,100\n0,200\n"), c);
        check("hist".equals(r.model.metadata.get("sales").kind)
                        && ((Number) r.data.get(0).get("sales")).doubleValue() == 100,
                "Missing printable-form type was not supplied from Stata metadata");
        check("text".equals(spec.findQuestion("sales").type) && "open or note".equals(spec.findQuestion("sales").rawType),
                "Type override mutated the user's original questionnaire specification");
    }

    private static DashboardConfig config() {
        DashboardConfig c = new DashboardConfig(); c.questionnaire = "audit-fixture.html"; c.autoGroups = false; return c;
    }

    private static CsvTable csv(String name, String contents) throws Exception {
        Path file = temporary.resolve(name); Files.write(file, contents.getBytes(StandardCharsets.UTF_8)); return CsvTable.read(file.toString());
    }

    private static QuestionnaireSpec binarySpec(String... variables) {
        QuestionnaireSpec spec = new QuestionnaireSpec(); spec.title = "Audit regression fixture";
        spec.sections.add(new QuestionSection(1, "Interview"));
        for (String variable : variables) {
            Question q = question(variable, "Question " + variable, "single");
            q.options.add(new QuestionOption("1", "Yes", false)); q.options.add(new QuestionOption("0", "No", false)); add(spec, q);
        }
        return spec;
    }

    private static Question question(String variable, String label, String type) {
        Question q = new Question(); q.variable = variable; q.label = label; q.type = type; q.rawType = type;
        q.section = "Interview"; q.sectionNumber = 1; return q;
    }

    private static void add(QuestionnaireSpec spec, Question q) { spec.questions.add(q); spec.sections.get(0).questions.add(q); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
