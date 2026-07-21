import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DashboardRenderer {
    private DashboardRenderer() {}

    static String render(DashboardModel model, List<Map<String, Object>> data) throws IOException {
        String css = Util.readResource("/resources/dashboard.css");
        String chart = safeScript(Util.readResource("/resources/chart.umd.js"));
        String leafletCss = model.mapGeometry == null ? "" : Util.readResource("/resources/leaflet.css");
        String leaflet = model.mapGeometry == null ? "" : safeScript(Util.readResource("/resources/leaflet.js"));
        String engine = safeScript(Util.readResource("/resources/dashboard.js"));
        boolean embedsArabicFont = needsArabicFont(model);
        String arabicFonts = embedsArabicFont ? arabicFontCss() : "";
        String thirdPartyNotices = thirdPartyNotices(model.mapGeometry != null, embedsArabicFont);
        String csp = model.mapGeometry == null
                ? "default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
                : "default-src 'none'; img-src data: https://*.google.com https://*.tile.openstreetmap.org; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
        StringBuilder html = new StringBuilder(1024 * 1024);
        html.append("<!doctype html>\n<html lang=\"").append(Util.html(model.language)).append("\" dir=\"")
                .append(Util.html(model.direction)).append("\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(csp).append("\">\n")
                .append("<title>").append(Util.html(model.title)).append("</title>\n<style>").append(arabicFonts)
                .append(leafletCss).append("\n").append(css).append("</style>\n</head>\n")
                .append("<body data-theme=\"").append(Util.html(model.theme)).append("\" data-density=\"")
                .append(Util.html(model.density)).append("\" data-ui-language=\"").append(Util.html(model.uiLanguage))
                .append("\" data-direction=\"").append(Util.html(model.direction)).append("\">\n")
                .append("<div class=\"topline\" aria-hidden=\"true\"></div>\n")
                .append("<div class=\"brandbar\"><div class=\"wrap\">");
        if (model.logoDataUri != null) {
            html.append("<img src=\"").append(Util.html(model.logoDataUri)).append("\" alt=\"")
                    .append(Util.html(t(model, "logoAlt"))).append("\">");
        } else {
            html.append("<div class=\"brandtext\">Survey Solutions <span>")
                    .append(Util.html(t(model, "interactiveDashboard"))).append("</span></div>");
        }
        html.append("<div class=\"privacy\">").append(model.mapGeometry == null
                        ? t(model, "selfContainedPrivacy") : t(model, "mapPrivacy"))
                .append("</div></div></div>\n")
                .append("<header class=\"topbar\"><div class=\"wrap\"><div class=\"product\"><small>")
                .append(Util.html(t(model, "questionnaire"))).append("</small>")
                .append(Util.html(model.product)).append("</div><div class=\"top-spacer\"></div>")
                .append("<div class=\"badge").append(model.simulated ? " sim" : "").append("\"")
                .append(model.weighted && !model.simulated ? " data-weight-mode-label" : "").append(">")
                .append(Util.html(model.simulated ? t(model, "simulatedBadge")
                        : model.weighted ? t(model, "weightedBadge") : t(model, "fieldworkBadge")))
                .append("</div></div></header>\n")
                .append(navigation(model))
                .append("<main class=\"wrap\"><section class=\"hero\" id=\"overview\"><div class=\"eyebrow\">")
                .append(Util.html(model.simulated ? t(model, "previewEyebrow") : t(model, "resultsEyebrow")))
                .append("</div><h1>").append(Util.html(model.title)).append("</h1><p class=\"subtitle\">")
                .append(Util.html(model.subtitle)).append("</p>");
        if (!blank(model.disclaimer)) {
            html.append("<div class=\"notice\"><b>").append(Util.html(t(model, "sharingNote")))
                    .append("</b><span>").append(Util.html(model.disclaimer)).append("</span></div>");
        }
        html.append("</section>\n").append(controls(model)).append(kpis(model));
        if (!model.highlights.isEmpty()) html.append(highlights(model));
        if (!model.keyMessages.isEmpty()) html.append(messages(model));
        if (!model.warnings.isEmpty()) html.append(warnings(model));
        if (model.summaryTable != null) html.append(summaryTable(model));
        if (model.mapGeometry != null) html.append(map(model));
        html.append("<div id=\"no-results\" class=\"no-results\"><b>").append(Util.html(t(model, "noSearchResults")))
                .append("</b><br>").append(Util.html(t(model, "searchHint"))).append("</div>\n");
        int panelIndex = 1;
        for (int i = 0; i < model.sections.size(); i++) {
            DashboardSection section = model.sections.get(i);
            html.append(section(model, section, i + 1, panelIndex));
            panelIndex += section.panels.size();
        }
        html.append(footer(model)).append("</main>\n")
                .append("<script>var META=").append(Util.json(metadataJson(model))).append(";\n")
                .append("var DATA=").append(Util.json(data)).append(";\n")
                .append("var CONFIG=").append(Util.json(configJson(model))).append(";</script>\n")
                .append("<script>").append(chart).append("</script>\n")
                .append(model.mapGeometry == null ? "" : "<script>" + leaflet + "</script>\n")
                .append("<script>").append(engine).append("</script>\n")
                .append("<template id=\"surveye-third-party-notices\" aria-hidden=\"true\"><pre>")
                .append(Util.html(thirdPartyNotices)).append("</pre></template>\n")
                .append("</body></html>\n");
        return html.toString();
    }

    private static String thirdPartyNotices(boolean embedsLeaflet, boolean embedsArabicFont) throws IOException {
        StringBuilder notices = new StringBuilder();
        notices.append("Chart.js 4.4.9\n")
                .append(Util.readResource("/resources/CHARTJS-LICENSE.md"));
        if (embedsLeaflet) {
            notices.append("\n\nLeaflet 1.9.4\n")
                    .append(Util.readResource("/resources/LEAFLET-LICENSE.txt"));
        }
        if (embedsArabicFont) {
            notices.append("\n\nNoto Sans Arabic\n")
                    .append(Util.readResource("/resources/NOTO-SANS-ARABIC-LICENSE.txt"));
        }
        return notices.toString();
    }

    private static String navigation(DashboardModel model) {
        StringBuilder out = new StringBuilder();
        out.append("<nav class=\"section-nav\" id=\"section-nav\" aria-label=\"")
                .append(Util.html(t(model, "dashboardSections"))).append("\"><div class=\"wrap\">")
                .append("<a href=\"#overview\" class=\"active\">").append(Util.html(t(model, "overview"))).append("</a>");
        for (int i = 0; i < model.sections.size(); i++) {
            DashboardSection section = model.sections.get(i);
            out.append("<a href=\"#").append(Util.slug(section.title, i + 1)).append("\">")
                    .append(Util.html(Util.shortLabel(section.title, t(model, "section") + " " + (i + 1), 29))).append("</a>");
        }
        return out.append("</div></nav>\n").toString();
    }

    private static String controls(DashboardModel model) {
        StringBuilder out = new StringBuilder();
        out.append("<section class=\"controls\" id=\"dashboard-controls\" data-expanded=\"false\" aria-label=\"")
                .append(Util.html(t(model, "dashboardControls"))).append("\">")
                .append("<div class=\"controls-toolbar\">")
                .append("<button type=\"button\" class=\"controls-toggle\" id=\"controls-toggle\" aria-expanded=\"false\" aria-controls=\"controls-body\">")
                .append("<span class=\"controls-toggle-label\">").append(Util.html(t(model, "showFilters")))
                .append("</span><span class=\"controls-chevron\" aria-hidden=\"true\">⌄</span></button>")
                .append("<span class=\"active-filter-count\" id=\"active-filter-count\" data-active-count=\"0\" role=\"status\" aria-live=\"polite\" hidden><b>0</b> <span data-active-filter-label>")
                .append(Util.html(t(model, "activeFilters"))).append("</span></span>");
        if (model.weighted || model.usdEnabled) {
            out.append("<div class=\"estimate-toggles\" role=\"group\" aria-label=\"")
                    .append(Util.html(t(model, "displaySettings"))).append("\">");
            if (model.weighted) {
                out.append("<label class=\"mode-toggle\" for=\"weight-toggle\"><input id=\"weight-toggle\" type=\"checkbox\" checked>")
                        .append("<span>").append(Util.html(t(model, "weightedEstimates"))).append("</span></label>");
            }
            if (model.usdEnabled) {
                out.append("<label class=\"mode-toggle\" for=\"usd-toggle\"><input id=\"usd-toggle\" type=\"checkbox\">")
                        .append("<span>").append(Util.html(t(model, "valuesInUsd"))).append("</span></label>");
            }
            out.append("</div>");
        }
        out.append("<div class=\"controls-summary\"><button type=\"button\" class=\"reset\" id=\"reset\" disabled>")
                .append(Util.html(t(model, "resetAll"))).append("</button>")
                .append("<span class=\"matched\" role=\"status\" aria-live=\"polite\"><b data-matched>0</b> ")
                .append(Util.html(t(model, "interviewsShown"))).append("</span></div></div>")
                .append("<div class=\"controls-body\" id=\"controls-body\" hidden><div class=\"control-row\">")
                .append("<label class=\"searchbox\"><span class=\"sr-only\"></span><input id=\"indicator-search\" type=\"search\" placeholder=\"")
                .append(Util.html(t(model, "searchPlaceholder"))).append("\" aria-label=\"")
                .append(Util.html(t(model, "searchIndicators"))).append("\"><span class=\"icon\" aria-hidden=\"true\">⌕</span></label>")
                .append("<div class=\"filters\">");
        for (DashboardFilter filter : model.filters) {
            out.append("<div class=\"filter\"><span class=\"filter-label\">").append(Util.html(filter.label)).append("</span><span class=\"chips\">");
            for (FilterChoice choice : filter.choices) {
                out.append("<button type=\"button\" class=\"chip\" aria-pressed=\"false\" data-filter=\"")
                        .append(Util.html(filter.variable)).append("\" data-value=\"").append(Util.html(choice.value)).append("\" title=\"")
                        .append(Util.html(choice.label)).append("\">").append(Util.html(Util.shortLabel(choice.label, choice.value, 28))).append("</button>");
            }
            out.append("</span></div>");
        }
        out.append("</div>");
        out.append("</div></div></div></section>\n");
        return out.toString();
    }

    private static String summaryTable(DashboardModel model) {
        DashboardTable table = model.summaryTable;
        StringBuilder out = new StringBuilder("<section class=\"profile-card\" id=\"summary-profile\" aria-labelledby=\"summary-profile-title\">")
                .append("<div class=\"profile-head\"><div><h2 id=\"summary-profile-title\">")
                .append(Util.html(table.title)).append("</h2>");
        if (!blank(table.subtitle)) out.append("<p>").append(Util.html(table.subtitle)).append("</p>");
        out.append("</div><span class=\"profile-status\" data-profile-status role=\"status\" aria-live=\"polite\"></span></div>")
                .append("<div class=\"profile-table-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"")
                .append(Util.html(table.title)).append("\"><table class=\"profile-table\" id=\"summary-profile-table\"><thead><tr>")
                .append("<th scope=\"col\" data-table-by-heading>").append(Util.html(table.byLabel)).append("</th>");
        for (int i = 0; i < table.variables.size(); i++) {
            out.append("<th scope=\"col\" data-table-variable=\"")
                    .append(Util.html(table.variables.get(i))).append("\">")
                    .append(Util.html(table.labels.get(i))).append("</th>");
        }
        out.append("</tr></thead><tbody id=\"summary-profile-body\" aria-live=\"polite\"></tbody></table></div></section>\n");
        return out.toString();
    }

    private static String kpis(DashboardModel model) {
        return "<section class=\"kpis\" aria-label=\"" + Util.html(t(model, "dashboardSummary")) + "\">"
                + kpi("0", t(model, "interviewsMatching"), "data-matched")
                + kpi(Integer.toString(indicatorCount(model)), t(model, "indicatorsVisualized"), null)
                + kpi(Integer.toString(model.sections.size()), t(model, "sectionsShown"), null)
                + kpi(Integer.toString(model.sourceQuestions), t(model, "questionsInstrument"), null)
                + "</section>\n";
    }

    private static String kpi(String value, String label, String attribute) {
        return "<div class=\"kpi\"><div class=\"kpi-value\"" + (attribute == null ? "" : " " + attribute)
                + ">" + Util.html(value) + "</div><div class=\"kpi-label\">" + Util.html(label) + "</div></div>";
    }

    private static String highlights(DashboardModel model) {
        StringBuilder out = new StringBuilder("<section class=\"highlight-grid\" aria-label=\"")
                .append(Util.html(t(model, "highlights"))).append("\">");
        for (HighlightCard card : model.highlights) {
            out.append("<div class=\"highlight\" data-variable=\"").append(Util.html(card.variable)).append("\">")
                    .append("<div class=\"highlight-value\">—</div><div class=\"highlight-label\">").append(Util.html(card.label))
                    .append("</div><div class=\"highlight-detail\">").append(Util.html(t(model, "calculating"))).append("</div></div>");
        }
        return out.append("</section>\n").toString();
    }

    private static String messages(DashboardModel model) {
        StringBuilder out = new StringBuilder("<section class=\"messages\"><h2>")
                .append(Util.html(t(model, "keyMessages"))).append("</h2><div class=\"message-grid\">");
        for (KeyMessage message : model.keyMessages) {
            out.append("<article class=\"message\"><h3>").append(Util.html(message.title)).append("</h3><p>")
                    .append(Util.html(message.text)).append("</p></article>");
        }
        return out.append("</div></section>\n").toString();
    }

    private static String warnings(DashboardModel model) {
        StringBuilder out = new StringBuilder("<details class=\"flagbox\"><summary>")
                .append(model.warnings.size()).append(' ').append(Util.html(model.warnings.size() == 1
                        ? t(model, "qualityNote") : t(model, "qualityNotes")))
                .append("</summary><ul>");
        for (String warning : model.warnings) out.append("<li>").append(Util.html(warning)).append("</li>");
        return out.append("</ul></details>\n").toString();
    }

    private static String map(DashboardModel model) {
        BoundaryMap.MapGeometry geometry = model.mapGeometry;
        StringBuilder out = new StringBuilder("<details class=\"map-card\" id=\"spatial-map\"");
        if ("comfortable".equals(model.density)) out.append(" open");
        out.append("><summary class=\"map-head\"><div><h2>")
                .append(Util.html(model.mapTitle)).append("</h2><div class=\"map-meta\">")
                .append(Util.html(geometry.displayName)).append(" · ").append(Util.html(
                        "admin2".equals(model.mapLevel) ? t(model, "admin2Boundaries") : t(model, "countryOutline")))
                .append(" · ").append(Util.html(mapBaseLabel(model, model.baseMap))).append(" · ")
                .append(Util.html(t(model, "mapDisplay").replace("{type}", t(model, model.mapType))))
                .append("</div></div><div class=\"map-count\"><b id=\"map-count\">0</b><span>")
                .append(Util.html(t(model, "mappedInterviewsShown"))).append("</span></div>")
                .append("<span class=\"map-action\"><span class=\"map-show\">").append(Util.html(t(model, "showMap")))
                .append("</span><span class=\"map-hide\">").append(Util.html(t(model, "hideMap")))
                .append("</span><span class=\"map-chevron\" aria-hidden=\"true\">⌄</span></span></summary><div class=\"map-body\">")
                .append("<div class=\"map-stage\" id=\"map-stage\"><div id=\"leaflet-map\" class=\"leaflet-map\" role=\"application\" aria-label=\"")
                .append(Util.html(t(model, "interactiveMapOf"))).append(' ').append(Util.html(geometry.displayName)).append("\"></div>")
                .append("<div class=\"map-loading\" id=\"map-loading\">").append(Util.html(t(model, "preparingMap"))).append("</div></div>");
        if (!blank(model.mapBy)) {
            out.append("<div class=\"map-legend\" id=\"map-legend\" aria-label=\"")
                    .append(Util.html(t(model, "mapGroups"))).append("\"></div>");
        }
        out.append("<div class=\"map-foot\"><span>").append(model.mapMissing).append(' ')
                .append(Util.html(t(model, "missingInvalid"))).append(" · ").append(model.mapOutside).append(' ')
                .append(Util.html(t(model, "outsideBoundary"))).append("</span><span>")
                .append(Util.html(t(model, "boundary"))).append(": ").append(Util.html(geometry.sourceLabel))
                .append(" · ").append(Util.html(t(model, "coincidentPoints"))).append("</span></div></div></details>\n");
        return out.toString();
    }

    private static String mapBaseLabel(DashboardModel model, String value) {
        if ("google_sat".equals(value)) return t(model, "googleSatellite");
        if ("google_road".equals(value)) return t(model, "googleRoads");
        if ("osm".equals(value)) return t(model, "openStreetMap");
        return t(model, "googleHybrid");
    }

    private static String section(DashboardModel model, DashboardSection section, int displayNumber, int startPanelIndex) {
        String id = Util.slug(section.title, displayNumber);
        boolean open = model.sections.size() <= 4 || displayNumber == 1;
        StringBuilder out = new StringBuilder("<details class=\"story\" id=\"").append(id).append("\"");
        if (open) out.append(" open");
        out.append("><summary><span class=\"sec-number\">").append(String.format(Locale.ROOT, "%02d", displayNumber))
                .append("</span><span><span class=\"sec-title\">").append(Util.html(section.title)).append("</span><span class=\"sec-meta\">")
                .append(indicatorCount(section.panels)).append(' ').append(Util.html(indicatorCount(section.panels) == 1
                        ? t(model, "indicator") : t(model, "indicators"))).append("</span></span><span class=\"chevron\" aria-hidden=\"true\">⌄</span></summary>")
                .append("<div class=\"grid\">");
        for (int i = 0; i < section.panels.size(); i++) out.append(panel(model, section.panels.get(i), startPanelIndex + i));
        return out.append("</div></details>\n").toString();
    }

    private static String panel(DashboardModel model, ChartPanel panel, int index) {
        String canvasId = "chart-" + index;
        String panelId = blank(panel.id) ? panel.variable : panel.id;
        String statId = "stat-" + safeId(panelId);
        String summaryId = "summary-" + safeId(panelId);
        String chartViewId = "distribution-" + index;
        String statsViewId = "stats-view-" + index;
        String chartTabId = "distribution-tab-" + index;
        String statsTabId = "stats-tab-" + index;
        StringBuilder searchText = new StringBuilder(panel.fullLabel).append(' ').append(panel.section).append(' ').append(panel.subsection);
        for (String member : panel.memberVariables()) {
            VariableMeta memberMeta = model.metadata.get(member);
            searchText.append(' ').append(member);
            if (memberMeta != null) searchText.append(' ').append(memberMeta.label);
        }
        String search = searchText.toString().toLowerCase(Locale.ROOT);
        String subLabel = blank(panel.subsection) ? panel.rawType : panel.subsection + " · " + panel.rawType;
        boolean numeric = "hist".equals(panel.kind) || "discrete".equals(panel.kind);
        boolean composite = "family".equals(panel.kind) || "comparison".equals(panel.kind);
        String memberNames = String.join(" ", panel.memberVariables());
        StringBuilder out = new StringBuilder("<article class=\"panel panel--").append(Util.html(panel.kind)).append("\" data-search=\"")
                .append(Util.html(search)).append("\"><div class=\"panel-head\"><h3 class=\"panel-title\" title=\"")
                .append(Util.html(panel.fullLabel)).append("\">").append(Util.html(panel.title)).append("</h3><span class=\"varbadge\" title=\"")
                .append(Util.html(composite ? t(model, "variableName") + "s" : t(model, "variableName"))).append("\">")
                .append(Util.html(composite ? Integer.toString(panel.memberVariables().size()) + " variables" : panel.variable))
                .append("</span></div>");
        if (composite) {
            out.append("<div class=\"group-vars\" aria-label=\"").append(Util.html(t(model, "variableName"))).append("s\">");
            for (String member : panel.memberVariables()) out.append("<code>").append(Util.html(member)).append("</code>");
            out.append("</div>");
        }
        out.append("<div class=\"panel-meta\"><span class=\"panel-sub\" title=\"")
                .append(Util.html(subLabel)).append("\">").append(Util.html(subLabel))
                .append("</span><span class=\"panel-stat\" id=\"").append(statId).append("\"></span>");
        if (numeric) {
            out.append("<span class=\"panel-tabs\" role=\"tablist\" aria-label=\"").append(Util.html(t(model, "numericDisplay"))).append("\">")
                    .append("<button id=\"").append(chartTabId).append("\" type=\"button\" role=\"tab\" aria-selected=\"true\" tabindex=\"0\" data-panel-view=\"distribution\" aria-controls=\"").append(chartViewId).append("\">").append(Util.html(t(model, "distribution"))).append("</button>")
                    .append("<button id=\"").append(statsTabId).append("\" type=\"button\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-panel-view=\"stats\" aria-controls=\"").append(statsViewId).append("\">").append(Util.html(t(model, "stats"))).append("</button></span>");
        }
        out.append("</div><div class=\"chart-summary\" id=\"").append(summaryId).append("\"></div>");
        if (numeric) out.append("<div class=\"panel-view-stack\">")
                .append("<div class=\"panel-view is-active\" id=\"").append(chartViewId).append("\" role=\"tabpanel\" aria-labelledby=\"").append(chartTabId).append("\" data-panel-view-pane=\"distribution\" aria-hidden=\"false\">");
        out.append("<div class=\"chartwrap chartwrap--").append(Util.html(panel.kind)).append("\"><canvas class=\"chart\" id=\"")
                .append(canvasId).append("\" data-variable=\"").append(Util.html(panelId)).append("\" data-kind=\"")
                .append(Util.html(panel.kind)).append("\" data-members=\"").append(Util.html(memberNames))
                .append("\" role=\"img\" aria-label=\"").append(Util.html(panel.fullLabel)).append("\" aria-describedby=\"")
                .append(statId).append(' ').append(summaryId).append("\"></canvas><div class=\"empty\" id=\"empty-").append(canvasId)
                .append("\"><strong>□</strong><span>").append(Util.html(t(model, "noDataSelection"))).append("</span></div></div>");
        if (numeric) out.append("</div><div class=\"panel-view panel-view--stats\" id=\"").append(statsViewId)
                .append("\" role=\"tabpanel\" aria-labelledby=\"").append(statsTabId).append("\" data-panel-view-pane=\"stats\" aria-hidden=\"true\"><div class=\"numeric-stats\" id=\"stats-")
                .append(safeId(panelId)).append("\" aria-live=\"polite\"></div></div></div>");
        return out.append("</article>").toString();
    }

    private static String footer(DashboardModel model) {
        StringBuilder out = new StringBuilder("<footer class=\"footer\"><div><b>").append(Util.html(model.product)).append("</b><br>")
                .append("<span").append(model.weighted ? " data-weight-footer-label" : "").append(">")
                .append(Util.html(model.weighted ? t(model, "footerWeighted") : t(model, "footerUnweighted")))
                .append("</span> ").append(Util.html(t(model, "footerSpecial")));
        if (model.showCI) {
            out.append(' ').append(Util.html(t(model, "footerCI").replace("{level}", formatLevel(model.ciLevel))));
            if (model.weighted && !"fweight".equalsIgnoreCase(model.weightType)) {
                out.append(" <span data-weighted-ci-note>")
                        .append(Util.html(t(model, "footerWeightedCI"))).append("</span>");
            }
        }
        if (!blank(model.note)) out.append("<br><b>").append(Util.html(t(model, "note"))).append(":</b> ").append(Util.html(model.note));
        if (!blank(model.source)) out.append("<br><b>").append(Util.html(t(model, "source"))).append(":</b> ").append(Util.html(model.source));
        out.append("</div><div class=\"footer-right\">").append(Util.html(t(model, "generated"))).append(' ')
                .append(Util.html(Util.utcTimestamp())).append("<br>SurvEye 2.1.3 · ")
                .append(Util.html(model.mapGeometry == null ? t(model, "offlineHtml") : t(model, "onlineMapHtml")))
                .append(model.simulated ? "<br><b>" + Util.html(t(model, "simulatedWarning")) + "</b>" : "")
                .append("</div></footer>\n");
        return out.toString();
    }

    private static String formatLevel(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private static Map<String, Object> metadataJson(DashboardModel model) {
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, VariableMeta> entry : model.metadata.entrySet()) {
            VariableMeta meta = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("label", meta.label); value.put("kind", meta.kind); value.put("filterMode", meta.filterMode);
            value.put("distributionMode", meta.distributionMode); value.put("nonnegative", meta.nonnegative);
            value.put("format", meta.stataFormat); value.put("order", meta.order);
            value.put("labels", meta.labels); value.put("special", new ArrayList<String>(meta.specialCodes));
            value.put("missing", new ArrayList<String>(meta.missingCodes)); value.put("multi", meta.multi);
            value.put("affirmative", new ArrayList<String>(meta.affirmativeCodes));
            value.put("negative", new ArrayList<String>(meta.negativeCodes));
            output.put(entry.getKey(), value);
        }
        return output;
    }

    private static Map<String, Object> configJson(DashboardModel model) {
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        output.put("weighted", model.weighted); output.put("simulated", model.simulated);
        output.put("weightType", model.weightType); output.put("showCI", model.showCI); output.put("ciLevel", model.ciLevel);
        output.put("weightToggle", model.weighted);
        if (model.usdEnabled) {
            Map<String, Object> usd = new LinkedHashMap<String, Object>();
            usd.put("enabled", true); usd.put("variables", model.usdVariables);
            usd.put("rate", model.usdRate); usd.put("currency", model.currency);
            output.put("usd", usd);
        } else output.put("usd", null);
        if (model.summaryTable != null) {
            DashboardTable definition = model.summaryTable;
            Map<String, Object> table = new LinkedHashMap<String, Object>();
            table.put("enabled", true); table.put("by", definition.by); table.put("byLabel", definition.byLabel);
            table.put("variables", definition.variables); table.put("stats", definition.statistics);
            table.put("labels", definition.labels); table.put("title", definition.title);
            table.put("subtitle", definition.subtitle); table.put("totalLabel", definition.totalLabel);
            table.put("weightLabel", definition.weightLabel);
            table.put("ignoreOwnFilter", true);
            output.put("table", table);
        } else output.put("table", null);
        output.put("maxCategories", model.maxCategories);
        output.put("density", model.density);
        output.put("uiLanguage", model.uiLanguage);
        output.put("locale", model.language);
        output.put("direction", model.direction);
        output.put("strings", DashboardI18n.strings(model.uiLanguage));
        Map<String, Object> families = new LinkedHashMap<String, Object>();
        Map<String, Object> comparisons = new LinkedHashMap<String, Object>();
        for (ChartPanel panel : model.panels) {
            if (!("family".equals(panel.kind) || "comparison".equals(panel.kind))) continue;
            String panelId = blank(panel.id) ? panel.variable : panel.id;
            Map<String, Object> definition = new LinkedHashMap<String, Object>();
            definition.put("members", panel.memberVariables());
            definition.put("title", panel.fullLabel);
            Map<String, String> labels = new LinkedHashMap<String, String>();
            for (String member : panel.memberVariables()) {
                VariableMeta meta = model.metadata.get(member);
                labels.put(member, meta == null ? member : meta.label);
            }
            definition.put("labels", labels);
            if ("family".equals(panel.kind)) {
                definition.put("automatic", panel.automaticGroup);
                families.put(panelId, definition);
            } else {
                definition.put("by", panel.compareBy);
                definition.put("levels", panel.compareLevels);
                VariableMeta byMeta = model.metadata.get(panel.compareBy);
                definition.put("groupLabels", byMeta == null ? new LinkedHashMap<String, String>() : byMeta.labels);
                comparisons.put(panelId, definition);
            }
        }
        output.put("families", families);
        output.put("comparisons", comparisons);
        if (model.mapGeometry != null) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("width", model.mapGeometry.viewWidth); map.put("height", model.mapGeometry.viewHeight); map.put("type", model.mapType);
            map.put("basemap", model.baseMap);
            map.put("minLon", model.mapGeometry.minLon); map.put("maxLon", model.mapGeometry.maxLon);
            map.put("minLat", model.mapGeometry.minLat); map.put("maxLat", model.mapGeometry.maxLat);
            map.put("lonCenter", model.mapGeometry.lonCenter);
            map.put("boundaryAdmin2", model.mapGeometry.admin2);
            map.put("boundary", model.mapGeometry.encodedFeatures);
            map.put("by", model.mapBy);
            if (!blank(model.mapBy) && model.metadata.containsKey(model.mapBy)) {
                VariableMeta by = model.metadata.get(model.mapBy);
                map.put("groups", model.mapGroups); map.put("groupLabels", by.labels);
            }
            output.put("map", map);
        } else output.put("map", null);
        return output;
    }

    private static int indicatorCount(DashboardModel model) {
        return indicatorCount(model.panels);
    }

    private static int indicatorCount(List<ChartPanel> panels) {
        java.util.LinkedHashSet<String> variables = new java.util.LinkedHashSet<String>();
        for (ChartPanel panel : panels) variables.addAll(panel.memberVariables());
        return variables.size();
    }

    private static String safeScript(String value) {
        return value.replace("</script", "<\\/script").replace("</SCRIPT", "<\\/SCRIPT");
    }

    private static boolean needsArabicFont(DashboardModel model) {
        if ("arabic".equals(model.uiLanguage) || "urdu".equals(model.uiLanguage) || "rtl".equals(model.direction)) return true;
        if (model.questionnaireLanguage != null && model.questionnaireLanguage.matches("(?i)^(ar|ur|fa|ps|sd)(?:[-_].*)?$")) return true;
        StringBuilder visibleText = new StringBuilder().append(model.title).append(' ').append(model.product);
        for (DashboardSection section : model.sections) visibleText.append(' ').append(section.title);
        for (ChartPanel panel : model.panels) visibleText.append(' ').append(panel.fullLabel);
        return DashboardI18n.containsArabicScript(visibleText.toString());
    }

    private static String arabicFontCss() throws IOException {
        String range = "U+0600-06FF,U+0750-077F,U+0870-089F,U+08A0-08FF,U+FB50-FDFF,U+FE70-FEFF;";
        return "@font-face{font-family:'Noto Sans Arabic';font-style:normal;font-display:swap;font-weight:400;"
                + "src:url(data:font/woff2;base64," + Util.resourceBase64("/resources/noto-sans-arabic-arabic-400-normal.woff2")
                + ") format('woff2');unicode-range:" + range + "}\n"
                + "@font-face{font-family:'Noto Sans Arabic';font-style:normal;font-display:swap;font-weight:700;"
                + "src:url(data:font/woff2;base64," + Util.resourceBase64("/resources/noto-sans-arabic-arabic-700-normal.woff2")
                + ") format('woff2');unicode-range:" + range + "}\n";
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String t(DashboardModel model, String key) {
        return DashboardI18n.text(model.uiLanguage, key);
    }
}
