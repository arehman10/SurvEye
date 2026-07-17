import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Smoke-tests the public javacall entry point and its status-file contract. */
public final class StataEntryPointSmokeTest {
    private StataEntryPointSmokeTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected project root and fixture directory.");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Path fixtures = Paths.get(args[1]).toAbsolutePath().normalize();
        Path questionnaire = fixtures.resolve("English ES_B_READY_2025_Australia(4).html");
        Path data;
        boolean australiaFixture;
        String variables;
        String filters;
        String expectedN;
        String expectedCharts;
        if (Files.isRegularFile(questionnaire)) {
            australiaFixture = true;
            data = root.resolve("tests/synthetic_australia.csv");
            variables = "b1 b4_sp b2a";
            filters = "region";
            expectedN = "9";
            expectedCharts = "4";
        } else {
            australiaFixture = false;
            questionnaire = root.resolve("tests/fixed_multi_questionnaire.html");
            data = root.resolve("tests/fixed_multi.csv");
            variables = "services_used";
            filters = "";
            expectedN = "4";
            expectedCharts = "1";
        }
        check(Files.isRegularFile(questionnaire), "Questionnaire fixture is missing: " + questionnaire);
        check(Files.isRegularFile(data), "Synthetic CSV is missing: " + data);

        Path temporary = Files.createTempDirectory("surveye-stata-entry-");
        try {
            Path output = temporary.resolve("dashboard.html");
            Path status = temporary.resolve("status.tsv");
            Path diagnostics = temporary.resolve("diagnostics.txt");
            Path config = temporary.resolve("config.tsv");
            String settings = "mode\tbuild\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "data\t" + data + "\n"
                    + "output\t" + output + "\n"
                    + "status\t" + status + "\n"
                    + "diagnostics\t" + diagnostics + "\n"
                    + "variables\t" + variables + "\n"
                    + "filters\t" + filters + "\n"
                    + "replace\t1\n";
            if (australiaFixture) {
                settings += "customvars\tcomment\n"
                        + "datalabel.comment\tField quality note\n"
                        + "datatype.comment\tsingle\n"
                        + "addtosections\tFirm profile: comment\n"
                        + "latitude\tlat\n"
                        + "longitude\tlon\n"
                        + "country\tAustralia\n"
                        + "maptype\tpoints\n"
                        + "basemap\tgoogle_hybrid\n";
            }
            Files.write(config, settings.getBytes(StandardCharsets.UTF_8));

            int returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{config.toString()});
            Map<String, String> values = readStatus(status);
            check(returnCode == 0, "stata() returned " + returnCode);
            assertDashboardSuccessSchema(values, "build");
            check(expectedN.equals(values.get("N")), "Unexpected observation count: " + values.get("N"));
            check(expectedCharts.equals(values.get("k_charted")), "Unexpected chart count: " + values.get("k_charted"));
            check(Files.isRegularFile(output), "Dashboard output was not created.");
            String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            check(html.contains("var META=") && html.contains("var DATA="), "Dashboard payload is incomplete.");
            check(html.contains("data-density=\"compact\""), "Compact density is not the default.");
            check(html.contains("class=\"chart-summary\""), "Accessible/direct chart summaries are missing.");
            check(html.contains("id=\"surveye-third-party-notices\"")
                            && html.contains("Chart.js 4.4.9") && html.contains("The MIT License"),
                    "Generated HTML does not retain the embedded Chart.js license notice.");
            if (variables.contains("b1")) {
                check(html.contains("data-variable=\"b1\" data-kind=\"bar\""),
                        "Automatic categorical question b1 is not a horizontal bar.");
                check(html.contains("data-variable=\"b4_sp\" data-kind=\"yesno\""),
                        "Binary question b4_sp is not using the compact split-bar path.");
                check(html.contains("\"affirmative\":[\"1\"]")
                                && html.contains("\"negative\":[\"2\"]"),
                        "Binary response roles were not serialized by code.");
            }
            if (australiaFixture) {
                check("1".equals(values.get("has_map")), "GPS map was not reported in status: " + values);
                check("8".equals(values.get("map_N")), "Unexpected valid GPS count: " + values.get("map_N"));
                check(html.contains("Field quality note"), "Custom variable did not use its supplied Stata label.");
                check(html.contains("data-variable=\"comment\" data-kind=\"bar\""),
                        "Custom categorical variable was not rendered as a bar chart.");
                check(html.contains("Firm profile"), "Custom variable section placement is missing.");
                check(html.contains("data-panel-view=\"stats\""), "Numeric Stats tab is missing.");
                check(html.contains("Tukey outliers"), "Numeric outlier summary is missing.");
                check(html.contains("id=\"leaflet-map\""), "Leaflet map container is missing.");
                check(html.contains("Google Hybrid"), "Google Hybrid basemap label is missing.");
                check(html.contains("Leaflet 1.9.4") && html.contains("BSD 2-Clause License"),
                        "Map-enabled HTML does not retain the embedded Leaflet license notice.");
                check(html.contains("\"type\":\"points\""), "GPS points are not the configured default display.");
                check(html.contains("\"basemap\":\"google_hybrid\""), "Google Hybrid is not in the map config.");
                check(html.contains("\"showCI\":false") && html.contains("\"ciLevel\":95.0"),
                        "Confidence intervals must be off by default.");
                check(countOccurrences(html, "\"_lat\":") - countOccurrences(html, "\"_lat\":null") == 8 &&
                                countOccurrences(html, "\"_lon\":") - countOccurrences(html, "\"_lon\":null") == 8,
                        "The embedded map payload must retain every valid point coordinate.");
            }

            Path demoOutput = temporary.resolve("demo-dashboard.html");
            Path demoStatus = temporary.resolve("demo-status.tsv");
            Path demoConfig = temporary.resolve("demo-config.tsv");
            String demoSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + demoOutput + "\n"
                    + "status\t" + demoStatus + "\n"
                    + "demon\t7\n"
                    + "seed\t42\n"
                    + "maxpanels\t3\n"
                    + "density\tcomfortable\n"
                    + "replace\t1\n";
            if (australiaFixture) demoSettings += "variables\tb2a\n";
            Files.write(demoConfig, demoSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{demoConfig.toString()});
            values = readStatus(demoStatus);
            check(returnCode == 0, "Demo stata() returned " + returnCode);
            assertDashboardSuccessSchema(values, "demo");
            check("7".equals(values.get("N")), "Unexpected demo observation count: " + values.get("N"));
            check("0".equals(values.get("weighted")), "Unweighted demo reported weighted status: " + values);
            check(Files.isRegularFile(demoOutput), "Demo dashboard output was not created.");
            String demoHtml = new String(Files.readAllBytes(demoOutput), StandardCharsets.UTF_8);
            check(demoHtml.contains("data-density=\"comfortable\""), "Comfortable density was not rendered.");
            check(demoHtml.contains("\"showCI\":false"), "Demo confidence intervals must be off by default.");
            if (australiaFixture) {
                Matcher percentage = Pattern.compile("\\\"b2a\\\":(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
                        .matcher(demoHtml);
                int numericValues = 0;
                while (percentage.find()) {
                    double value = Double.parseDouble(percentage.group(1));
                    check(value >= 0.0 && value <= 100.0,
                            "Percentage demo value fell outside 0-100: " + value);
                    numericValues++;
                }
                check(numericValues > 0, "Percentage demo did not produce any numeric b2a values.");
            }

            Path rtlOutput = temporary.resolve("rtl-dashboard.html");
            Path rtlStatus = temporary.resolve("rtl-status.tsv");
            Path rtlConfig = temporary.resolve("rtl-config.tsv");
            String rtlSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + rtlOutput + "\n"
                    + "status\t" + rtlStatus + "\n"
                    + "demon\t5\n"
                    + "uilanguage\tar\n"
                    + "direction\tauto\n"
                    + "replace\t1\n";
            Files.write(rtlConfig, rtlSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{rtlConfig.toString()});
            values = readStatus(rtlStatus);
            check(returnCode == 0, "RTL demo stata() returned " + returnCode);
            assertDashboardSuccessSchema(values, "demo RTL");
            String rtlHtml = new String(Files.readAllBytes(rtlOutput), StandardCharsets.UTF_8);
            check(rtlHtml.contains("<html lang=\"ar\" dir=\"rtl\">"),
                    "Arabic language/direction attributes are missing.");
            check(rtlHtml.contains("data-ui-language=\"arabic\"")
                            && rtlHtml.contains("data-direction=\"rtl\""),
                    "Resolved Arabic/RTL body attributes are missing.");
            check(rtlHtml.contains("نظرة عامة"), "Arabic interface translation is missing.");
            check(rtlHtml.contains("\"direction\":\"rtl\""),
                    "Resolved RTL direction is missing from the dashboard configuration.");
            check(rtlHtml.contains("Noto Sans Arabic") && rtlHtml.contains("SIL Open Font License"),
                    "Arabic HTML does not retain the embedded font license notice.");

            Path badLanguageStatus = temporary.resolve("bad-language-status.tsv");
            Path badLanguageConfig = temporary.resolve("bad-language-config.tsv");
            String badLanguageSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("bad-language.html") + "\n"
                    + "status\t" + badLanguageStatus + "\n"
                    + "uilanguage\tklingon\n"
                    + "replace\t1\n";
            Files.write(badLanguageConfig, badLanguageSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{badLanguageConfig.toString()});
            values = readStatus(badLanguageStatus);
            check(returnCode == 0, "Invalid UI language must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("uilanguage()"),
                    "Invalid UI-language message is missing: " + values.get("message"));

            Path badDirectionStatus = temporary.resolve("bad-direction-status.tsv");
            Path badDirectionConfig = temporary.resolve("bad-direction-config.tsv");
            String badDirectionSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("bad-direction.html") + "\n"
                    + "status\t" + badDirectionStatus + "\n"
                    + "direction\tvertical\n"
                    + "replace\t1\n";
            Files.write(badDirectionConfig, badDirectionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{badDirectionConfig.toString()});
            values = readStatus(badDirectionStatus);
            check(returnCode == 0, "Invalid direction must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("direction()"),
                    "Invalid direction message is missing: " + values.get("message"));

            Path badDensityStatus = temporary.resolve("bad-density-status.tsv");
            Path badDensityConfig = temporary.resolve("bad-density-config.tsv");
            String badDensitySettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("bad-density.html") + "\n"
                    + "status\t" + badDensityStatus + "\n"
                    + "density\tgiant\n"
                    + "replace\t1\n";
            Files.write(badDensityConfig, badDensitySettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{badDensityConfig.toString()});
            values = readStatus(badDensityStatus);
            check(returnCode == 0, "Invalid density must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("density()"),
                    "Invalid density message is missing: " + values.get("message"));

            Path badBasemapStatus = temporary.resolve("bad-basemap-status.tsv");
            Path badBasemapConfig = temporary.resolve("bad-basemap-config.tsv");
            String badBasemapSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("bad-basemap.html") + "\n"
                    + "status\t" + badBasemapStatus + "\n"
                    + "basemap\tgoogle_terrain\n"
                    + "replace\t1\n";
            Files.write(badBasemapConfig, badBasemapSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{badBasemapConfig.toString()});
            values = readStatus(badBasemapStatus);
            check(returnCode == 0, "Invalid basemap must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("basemap()"),
                    "Invalid basemap message is missing: " + values.get("message"));

            Path badLevelStatus = temporary.resolve("bad-level-status.tsv");
            Path badLevelConfig = temporary.resolve("bad-level-config.tsv");
            String badLevelSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("bad-level.html") + "\n"
                    + "status\t" + badLevelStatus + "\n"
                    + "cilevel\t100\n"
                    + "replace\t1\n";
            Files.write(badLevelConfig, badLevelSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{badLevelConfig.toString()});
            values = readStatus(badLevelStatus);
            check(returnCode == 0, "Invalid CI level must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("level()"),
                    "Invalid confidence-level message is missing: " + values.get("message"));

            Path describeStatus = temporary.resolve("describe-status.tsv");
            Path describeConfig = temporary.resolve("describe-config.tsv");
            String describeSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "status\t" + describeStatus + "\n";
            Files.write(describeConfig, describeSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{describeConfig.toString()});
            values = readStatus(describeStatus);
            check(returnCode == 0, "Describe stata() returned " + returnCode);
            assertDescribeSuccessSchema(values);

            Path errorStatus = temporary.resolve("error-status.tsv");
            Path errorConfig = temporary.resolve("error-config.tsv");
            String badSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + temporary.resolve("missing.html") + "\n"
                    + "status\t" + errorStatus + "\n";
            Files.write(errorConfig, badSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{errorConfig.toString()});
            values = readStatus(errorStatus);
            check(returnCode == 0, "Expected errors must be reported through the status file.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("not found"),
                    "Friendly error message is missing: " + values.get("message"));

            Path parseErrorStatus = temporary.resolve("parse-error-status.tsv");
            Path parseErrorConfig = temporary.resolve("parse-error-config.tsv");
            String malformedSettings = "mode\tbuild\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "maxpanels\tnot-an-integer\n";
            Files.write(parseErrorConfig, malformedSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    parseErrorConfig.toString(), parseErrorStatus.toString()
            });
            values = readStatus(parseErrorStatus);
            check(returnCode == 0, "Configuration errors must use the status-file contract.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("integer"),
                    "Config parse error message is missing: " + values.get("message"));

            Path collisionStatus = temporary.resolve("collision-status.tsv");
            Path questionnaireAlias = questionnaire.getParent().resolve(".").resolve(questionnaire.getFileName());
            String collisionSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + questionnaireAlias + "\n"
                    + "status\t" + collisionStatus + "\n"
                    + "replace\t1\n";
            Path collisionConfig = temporary.resolve("collision-config.tsv");
            byte[] questionnaireBefore = Files.readAllBytes(questionnaire);
            Files.write(collisionConfig, collisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{collisionConfig.toString(), collisionStatus.toString()});
            values = readStatus(collisionStatus);
            check(returnCode == 0, "Path-collision errors must use the status-file contract.");
            assertErrorSchema(values);
            check(values.get("message") != null && values.get("message").contains("may not overwrite"),
                    "Path-collision message is missing: " + values.get("message"));
            check(java.util.Arrays.equals(questionnaireBefore, Files.readAllBytes(questionnaire)),
                    "Questionnaire changed during path-collision test.");

            Path diagnosticsCollisionStatus = temporary.resolve("diagnostics-collision-status.tsv");
            Path questionnaireDiagnosticsAlias = questionnaire.getParent().resolve(".").resolve(questionnaire.getFileName());
            String diagnosticsCollisionSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "status\t" + diagnosticsCollisionStatus + "\n"
                    + "diagnostics\t" + questionnaireDiagnosticsAlias + "\n"
                    + "replace\t1\n";
            Path diagnosticsCollisionConfig = temporary.resolve("diagnostics-collision-config.tsv");
            Files.write(diagnosticsCollisionConfig, diagnosticsCollisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    diagnosticsCollisionConfig.toString(), diagnosticsCollisionStatus.toString()
            });
            values = readStatus(diagnosticsCollisionStatus);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Diagnostics-collision status was not returned: " + values);
            check(java.util.Arrays.equals(questionnaireBefore, Files.readAllBytes(questionnaire)),
                    "Questionnaire was overwritten by error diagnostics.");

            Path statusCollisionFallback = temporary.resolve("status-collision-fallback.tsv");
            String statusCollisionSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "status\t" + questionnaireAlias + "\n";
            Path statusCollisionConfig = temporary.resolve("status-collision-config.tsv");
            Files.write(statusCollisionConfig, statusCollisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    statusCollisionConfig.toString(), statusCollisionFallback.toString()
            });
            values = readStatus(statusCollisionFallback);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Fallback status was not returned for an unsafe configured status path: " + values);
            check(java.util.Arrays.equals(questionnaireBefore, Files.readAllBytes(questionnaire)),
                    "Questionnaire was overwritten by error status reporting.");

            Path protectedLogo = temporary.resolve("protected-logo.png");
            byte[] logoBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
            Files.write(protectedLogo, logoBytes);
            Path logoCollisionFallback = temporary.resolve("logo-collision-fallback.tsv");
            String logoCollisionSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + temporary.resolve("logo-collision-output.html") + "\n"
                    + "status\t" + protectedLogo + "\n"
                    + "logo\t" + protectedLogo + "\n"
                    + "replace\t1\n";
            Path logoCollisionConfig = temporary.resolve("logo-collision-config.tsv");
            Files.write(logoCollisionConfig, logoCollisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    logoCollisionConfig.toString(), logoCollisionFallback.toString()
            });
            values = readStatus(logoCollisionFallback);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Unsafe status/logo collision did not use the fallback status: " + values);
            check(java.util.Arrays.equals(logoBytes, Files.readAllBytes(protectedLogo)),
                    "Logo was overwritten by status reporting.");

            Path protectedData = temporary.resolve("protected-data.csv");
            Files.copy(data, protectedData);
            byte[] protectedDataBefore = Files.readAllBytes(protectedData);
            Path describeDataFallback = temporary.resolve("describe-data-fallback.tsv");
            String describeDataCollisionSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "data\t" + protectedData + "\n"
                    + "status\t" + protectedData + "\n";
            Path describeDataCollisionConfig = temporary.resolve("describe-data-collision-config.tsv");
            Files.write(describeDataCollisionConfig, describeDataCollisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    describeDataCollisionConfig.toString(), describeDataFallback.toString()
            });
            values = readStatus(describeDataFallback);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Describe-mode data/status collision did not use fallback status: " + values);
            check(java.util.Arrays.equals(protectedDataBefore, Files.readAllBytes(protectedData)),
                    "Describe-mode data source was overwritten by status reporting.");

            Path protectedOutput = temporary.resolve("protected-output.html");
            byte[] protectedOutputBefore = "do-not-overwrite".getBytes(StandardCharsets.UTF_8);
            Files.write(protectedOutput, protectedOutputBefore);
            Path describeOutputFallback = temporary.resolve("describe-output-fallback.tsv");
            String describeOutputCollisionSettings = "mode\tdescribe\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + protectedOutput + "\n"
                    + "status\t" + protectedOutput + "\n";
            Path describeOutputCollisionConfig = temporary.resolve("describe-output-collision-config.tsv");
            Files.write(describeOutputCollisionConfig, describeOutputCollisionSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    describeOutputCollisionConfig.toString(), describeOutputFallback.toString()
            });
            values = readStatus(describeOutputFallback);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Describe-mode output/status collision did not use fallback status: " + values);
            check(java.util.Arrays.equals(protectedOutputBefore, Files.readAllBytes(protectedOutput)),
                    "Describe-mode output path was overwritten by status reporting.");

            Path protectedQuestionnaire = temporary.resolve("protected-questionnaire.html");
            Path hardLinkOutput = temporary.resolve("hard-link-output.html");
            Files.copy(questionnaire, protectedQuestionnaire);
            Files.createLink(hardLinkOutput, protectedQuestionnaire);
            byte[] protectedQuestionnaireBefore = Files.readAllBytes(protectedQuestionnaire);
            Path hardLinkStatus = temporary.resolve("hard-link-status.tsv");
            String hardLinkSettings = "mode\tdemo\n"
                    + "questionnaire\t" + protectedQuestionnaire + "\n"
                    + "output\t" + hardLinkOutput + "\n"
                    + "status\t" + hardLinkStatus + "\n"
                    + "replace\t1\n";
            Path hardLinkConfig = temporary.resolve("hard-link-config.tsv");
            Files.write(hardLinkConfig, hardLinkSettings.getBytes(StandardCharsets.UTF_8));
            returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{hardLinkConfig.toString(), hardLinkStatus.toString()});
            values = readStatus(hardLinkStatus);
            assertErrorSchema(values);
            check(returnCode == 0,
                    "Hard-link collision status was not returned: " + values);
            check(java.util.Arrays.equals(protectedQuestionnaireBefore, Files.readAllBytes(protectedQuestionnaire)),
                    "Questionnaire was overwritten through a hard-link alias.");

            Path cliConfig = temporary.resolve("cli-config.tsv");
            String cliCollisionSettings = "mode\tdemo\n"
                    + "questionnaire\t" + questionnaire + "\n"
                    + "output\t" + cliConfig + "\n"
                    + "replace\t1\n";
            byte[] cliConfigBefore = cliCollisionSettings.getBytes(StandardCharsets.UTF_8);
            Files.write(cliConfig, cliConfigBefore);
            Path javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
            Process process = new ProcessBuilder(javaExecutable.toString(), "-jar",
                    root.resolve("surveye.jar").toString(), "--config", cliConfig.toString())
                    .redirectErrorStream(true).start();
            String cliMessage = readProcessOutput(process.getInputStream());
            int cliReturnCode = process.waitFor();
            check(cliReturnCode != 0, "CLI --config collision unexpectedly succeeded.");
            check(cliMessage.contains("configuration file"),
                    "CLI --config collision message is missing: " + cliMessage);
            check(java.util.Arrays.equals(cliConfigBefore, Files.readAllBytes(cliConfig)),
                    "CLI --config input was overwritten by its output setting.");

            System.out.println("PASS Stata entry point and status-file contract");
        } finally {
            deleteTree(temporary);
        }
    }

    private static String readProcessOutput(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Map<String, String> readStatus(Path filename) throws Exception {
        check(Files.isRegularFile(filename), "Status file was not created: " + filename);
        Map<String, String> values = new LinkedHashMap<String, String>();
        List<String> lines = Files.readAllLines(filename, StandardCharsets.UTF_8);
        for (String line : lines) {
            int tab = line.indexOf('\t');
            check(tab > 0, "Malformed status record without key/tab: " + line);
            check(line.indexOf('\t', tab + 1) < 0, "Unescaped tab in status value: " + line);
            String key = line.substring(0, tab);
            check(!values.containsKey(key), "Duplicate status key: " + key);
            values.put(key, line.substring(tab + 1));
        }
        return values;
    }

    private static void assertDashboardSuccessSchema(Map<String, String> values, String mode) {
        requireExact(values, "success", "1", mode);
        requireNonblank(values, "message", mode);
        requireNonblank(values, "title", mode);
        requireKeys(values, mode, "chartvars", "skippedvars", "filters", "sections",
                "engine_version", "output", "questionnaire");
        requireExact(values, "engine_version", SurvEye.VERSION, mode);
        requireNonblank(values, "output", mode);
        requireNonblank(values, "questionnaire", mode);
        requireIntegers(values, mode, "N", "k_requested", "k_charted", "k_skipped",
                "k_sections", "k_filters", "warnings", "weighted", "has_map",
                "map_N", "map_missing", "map_outside");
        long charted = integerValue(values, "k_charted", mode);
        long skipped = integerValue(values, "k_skipped", mode);
        check(integerValue(values, "k_requested", mode) == charted + skipped,
                mode + " k_requested differs from k_charted + k_skipped: " + values);
        requireFlag(values, "weighted", mode);
        requireFlag(values, "has_map", mode);
        check(!values.containsKey("k_questions"),
                mode + " unexpectedly supplied describe-only k_questions: " + values);
        check(!values.containsKey("k_chartable"),
                mode + " unexpectedly supplied describe-only k_chartable alias: " + values);
        check(!values.containsKey("filter_candidates") && !values.containsKey("gps_candidates"),
                mode + " unexpectedly supplied describe-only candidate lists: " + values);
    }

    private static void assertDescribeSuccessSchema(Map<String, String> values) {
        String mode = "describe";
        requireExact(values, "success", "1", mode);
        requireNonblank(values, "message", mode);
        requireNonblank(values, "title", mode);
        requireKeys(values, mode, "chartvars", "filter_candidates", "gps_candidates",
                "engine_version", "sections", "questionnaire");
        requireExact(values, "engine_version", SurvEye.VERSION, mode);
        requireNonblank(values, "questionnaire", mode);
        requireIntegers(values, mode, "k_sections", "k_questions", "k_chartable",
                "k_charted", "warnings");
        check(values.get("k_chartable").equals(values.get("k_charted")),
                "describe compatibility alias k_chartable differs from k_charted: " + values);
        String[] intentionallyAbsent = {"N", "k_requested", "k_skipped", "k_filters",
                "weighted", "has_map", "map_N", "map_missing", "map_outside"};
        for (String key : intentionallyAbsent) {
            check(!values.containsKey(key),
                    "describe should leave noncomputed metric absent, not zero: " + key + " in " + values);
        }
        check(!values.containsKey("output") && !values.containsKey("skippedvars")
                        && !values.containsKey("filters"),
                "describe unexpectedly supplied dashboard-only strings: " + values);
    }

    private static void assertErrorSchema(Map<String, String> values) {
        String mode = "error";
        requireExact(values, "success", "0", mode);
        requireNonblank(values, "message", mode);
        requireExact(values, "engine_version", SurvEye.VERSION, mode);
        requireKeys(values, mode, "questionnaire", "output");
        String[] metrics = {"N", "k_requested", "k_chartable", "k_charted", "k_skipped",
                "k_sections", "k_filters", "k_questions", "warnings", "weighted",
                "has_map", "map_N", "map_missing", "map_outside"};
        for (String key : metrics) {
            check(!values.containsKey(key), "Error status contains a misleading metric " + key + ": " + values);
        }
    }

    private static void requireKeys(Map<String, String> values, String mode, String... keys) {
        for (String key : keys) check(values.containsKey(key), mode + " status is missing key " + key + ": " + values);
    }

    private static void requireNonblank(Map<String, String> values, String key, String mode) {
        requireKeys(values, mode, key);
        check(!values.get(key).trim().isEmpty(), mode + " status has blank " + key + ": " + values);
    }

    private static void requireExact(Map<String, String> values, String key, String expected, String mode) {
        requireKeys(values, mode, key);
        check(expected.equals(values.get(key)), mode + " status has " + key + "=" + values.get(key)
                + ", expected " + expected + ": " + values);
    }

    private static void requireIntegers(Map<String, String> values, String mode, String... keys) {
        for (String key : keys) {
            long value = integerValue(values, key, mode);
            check(value >= 0, mode + " status has negative " + key + ": " + values);
        }
    }

    private static long integerValue(Map<String, String> values, String key, String mode) {
        requireKeys(values, mode, key);
        try {
            return Long.parseLong(values.get(key));
        } catch (NumberFormatException error) {
            throw new AssertionError(mode + " status has malformed numeric " + key + "=" + values.get(key), error);
        }
    }

    private static void requireFlag(Map<String, String> values, String key, String mode) {
        String value = values.get(key);
        check("0".equals(value) || "1".equals(value),
                mode + " status has nonbinary " + key + "=" + value + ": " + values);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        java.util.List<Path> paths = new java.util.ArrayList<Path>();
        collect(root, paths);
        java.util.Collections.sort(paths, java.util.Collections.reverseOrder());
        for (Path path : paths) Files.deleteIfExists(path);
    }

    private static void collect(Path path, java.util.List<Path> output) throws Exception {
        output.add(path);
        if (!Files.isDirectory(path)) return;
        java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path);
        try {
            for (Path child : stream) collect(child, output);
        } finally {
            stream.close();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
