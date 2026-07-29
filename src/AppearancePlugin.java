package org.worldbank.surveye;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the optional SurvEye finish layer after the dashboard engine writes
 * its self-contained HTML.  Keeping this as a small named-package plugin lets
 * Stata expose presentation controls without coupling them to survey content,
 * chart calculations, filters, maps, or the questionnaire parser.
 */
public final class AppearancePlugin {
    private static final String STYLE_ID = "surveye-appearance-overrides";
    private static final String SCRIPT_ID = "surveye-appearance-motion";
    private static final Pattern SURVEYE_ATTRIBUTE = Pattern.compile(
            "\\sdata-surveye-(?:finish|background|typography|corners|shadow|motion|page-width)=\\\"[^\\\"]*\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> THEMES = set("editorial", "worldbank", "clean", "forest", "dark");
    private static final Set<String> BACKGROUNDS = set("auto", "glow", "paper", "plain");
    private static final Set<String> TYPOGRAPHY = set("auto", "editorial", "modern", "system");
    private static final Set<String> CORNERS = set("auto", "rounded", "soft", "square");
    private static final Set<String> SHADOWS = set("auto", "soft", "lifted", "none");
    private static final Set<String> MOTIONS = set("none", "subtle", "reveal");

    private AppearancePlugin() {}

    /** Stata javacall entry point: public static int method(String[]). */
    public static int apply(String[] args) {
        try {
            applyInternal(args);
            return 0;
        } catch (Exception error) {
            String detail = error.getMessage();
            System.err.println("surveye: could not apply dashboard appearance"
                    + (detail == null || detail.trim().isEmpty() ? "" : " (" + detail.trim() + ")"));
            return 499;
        }
    }

    /** Command-line entry point used by portable release tests and previews. */
    public static void main(String[] args) throws Exception {
        applyInternal(args);
    }

    private static void applyInternal(String[] args) throws Exception {
        if (args == null || args.length != 8) {
            throw new IllegalArgumentException(
                    "expected output, theme, background, typography, corners, shadow, motion, and page width");
        }
        Path output = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(output)) throw new IOException("dashboard HTML not found: " + output);

        String theme = choice(args[1], THEMES, "theme");
        String background = choice(args[2], BACKGROUNDS, "background");
        String typography = choice(args[3], TYPOGRAPHY, "typography");
        String corners = choice(args[4], CORNERS, "corners");
        String shadow = choice(args[5], SHADOWS, "shadow");
        String motion = choice(args[6], MOTIONS, "motion");
        int pageWidth = parsePageWidth(args[7]);

        String html = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        html = removeTaggedBlock(html, "style", STYLE_ID);
        html = removeTaggedBlock(html, "script", SCRIPT_ID);
        html = addBodyAttributes(html, theme, background, typography, corners, shadow, motion, pageWidth);

        String css = buildCss(theme, pageWidth);
        int headClose = indexOfIgnoreCase(html, "</head>");
        if (headClose < 0) throw new IllegalArgumentException("dashboard HTML has no </head> tag");
        String style = "\n<style id=\"" + STYLE_ID + "\">\n" + css + "\n</style>\n";
        html = html.substring(0, headClose) + style + html.substring(headClose);

        if (!"none".equals(motion)) {
            int bodyClose = indexOfIgnoreCase(html, "</body>");
            if (bodyClose < 0) throw new IllegalArgumentException("dashboard HTML has no </body> tag");
            String script = "\n<script id=\"" + SCRIPT_ID + "\">\n" + motionScript() + "\n</script>\n";
            html = html.substring(0, bodyClose) + script + html.substring(bodyClose);
        }

        writeAtomic(output, html);
    }

    private static String buildCss(String theme, int pageWidth) {
        StringBuilder css = new StringBuilder(12000);
        css.append("/* SurvEye finish layer: presentation only; data and calculations are unchanged. */\n")
                .append(".brandbar,.topline{display:none!important}\n");
        if ("editorial".equals(theme)) {
            css.append("body[data-surveye-finish=\"editorial\"]:not([data-theme=\"dark\"]){\n")
                    .append("  --navy:#002244;--blue:#009fda;--cyan:#009fda;--gold:#fdb714;--green:#00a887;\n")
                    .append("  --coral:#e5552b;--purple:#7c5ba6;--paper:#fbfaf6;--card:#fff;--ink:#16202e;\n")
                    .append("  --muted:#5a6473;--line:#e6e1d5;--soft:#f3f0e8;\n")
                    .append("  --shadow:0 1px 2px rgba(0,34,68,.04),0 10px 30px rgba(0,34,68,.07);--radius:16px;\n")
                    .append("}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .topline{display:none}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .topbar{border-bottom:4px solid var(--gold);box-shadow:0 2px 12px rgba(0,34,68,.18)}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .section-nav{background:color-mix(in srgb,var(--paper) 96%,transparent);border-bottom-color:rgba(0,34,68,.12)}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .hero{padding:36px 0 0}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .hero h1{font-size:clamp(36px,4.6vw,54px);line-height:1.08;max-width:24ch}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .hero .subtitle{font-size:17px;line-height:1.55;max-width:74ch}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .controls{border-color:rgba(0,34,68,.10);box-shadow:0 8px 28px rgba(0,34,68,.07)}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .kpi,body[data-surveye-finish=\"editorial\"] .highlight{padding:18px 18px 17px}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .kpi-value,body[data-surveye-finish=\"editorial\"] .highlight-value{letter-spacing:-.015em}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .profile-card,body[data-surveye-finish=\"editorial\"] .map-card{padding:20px 22px}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .message{border-inline-start:4px solid var(--gold);border-left-width:1px}\n")
                    .append("html[dir=\"ltr\"] body[data-surveye-finish=\"editorial\"] .message{border-left-width:4px}\n")
                    .append("html[dir=\"rtl\"] body[data-surveye-finish=\"editorial\"] .message{border-right-width:4px}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .story{margin-top:34px;border-top:4px solid var(--navy)}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .story>summary{padding:14px 3px 12px}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .panel{padding:17px 18px 15px;border-color:rgba(0,34,68,.10);box-shadow:0 1px 2px rgba(0,34,68,.035),0 8px 24px rgba(0,34,68,.055)}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .panel-title{font-size:13.75px}\n")
                    .append("body[data-surveye-finish=\"editorial\"] .footer{margin-top:62px}\n");
        }

        css.append("body[data-surveye-background=\"glow\"]{background-color:var(--paper);background-image:radial-gradient(circle at 12% -8%,rgba(0,159,218,.065),transparent 42%),radial-gradient(circle at 92% 0%,rgba(253,183,20,.065),transparent 38%);background-attachment:fixed}\n")
                .append("body[data-surveye-background=\"paper\"]{background:var(--paper)}\n")
                .append("body[data-surveye-background=\"plain\"]{background:var(--card)}\n")
                .append("body[data-surveye-typography=\"editorial\"] .hero h1,body[data-surveye-typography=\"editorial\"] .sec-title,body[data-surveye-typography=\"editorial\"] .sec-number,body[data-surveye-typography=\"editorial\"] .kpi-value,body[data-surveye-typography=\"editorial\"] .highlight-value,body[data-surveye-typography=\"editorial\"] .profile-head h2,body[data-surveye-typography=\"editorial\"] .messages h2,body[data-surveye-typography=\"editorial\"] .map-head h2,body[data-surveye-typography=\"editorial\"] .map-count b,body[data-surveye-typography=\"editorial\"] .summary-lead strong{font-family:Georgia,\"Noto Serif\",serif}\n")
                .append("body[data-surveye-typography=\"modern\"] .hero h1,body[data-surveye-typography=\"modern\"] .sec-title,body[data-surveye-typography=\"modern\"] .sec-number,body[data-surveye-typography=\"modern\"] .kpi-value,body[data-surveye-typography=\"modern\"] .highlight-value,body[data-surveye-typography=\"modern\"] .profile-head h2,body[data-surveye-typography=\"modern\"] .messages h2,body[data-surveye-typography=\"modern\"] .map-head h2,body[data-surveye-typography=\"modern\"] .map-count b,body[data-surveye-typography=\"modern\"] .summary-lead strong{font-family:Inter,\"Segoe UI\",Roboto,\"Noto Sans Arabic\",Tahoma,Arial,sans-serif}\n")
                .append("body[data-surveye-typography=\"system\"],body[data-surveye-typography=\"system\"] button,body[data-surveye-typography=\"system\"] input,body[data-surveye-typography=\"system\"] select,body[data-surveye-typography=\"system\"] .hero h1,body[data-surveye-typography=\"system\"] .sec-title,body[data-surveye-typography=\"system\"] .sec-number,body[data-surveye-typography=\"system\"] .kpi-value,body[data-surveye-typography=\"system\"] .highlight-value{font-family:system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif}\n")
                .append("body[data-surveye-corners=\"rounded\"] .controls,body[data-surveye-corners=\"rounded\"] .kpi,body[data-surveye-corners=\"rounded\"] .highlight,body[data-surveye-corners=\"rounded\"] .profile-card,body[data-surveye-corners=\"rounded\"] .messages,body[data-surveye-corners=\"rounded\"] .message,body[data-surveye-corners=\"rounded\"] .flagbox,body[data-surveye-corners=\"rounded\"] .map-card,body[data-surveye-corners=\"rounded\"] .panel,body[data-surveye-corners=\"rounded\"] .no-results{border-radius:16px}\n")
                .append("body[data-surveye-corners=\"soft\"] .controls,body[data-surveye-corners=\"soft\"] .kpi,body[data-surveye-corners=\"soft\"] .highlight,body[data-surveye-corners=\"soft\"] .profile-card,body[data-surveye-corners=\"soft\"] .messages,body[data-surveye-corners=\"soft\"] .message,body[data-surveye-corners=\"soft\"] .flagbox,body[data-surveye-corners=\"soft\"] .map-card,body[data-surveye-corners=\"soft\"] .panel,body[data-surveye-corners=\"soft\"] .no-results{border-radius:10px}\n")
                .append("body[data-surveye-corners=\"square\"] .controls,body[data-surveye-corners=\"square\"] .kpi,body[data-surveye-corners=\"square\"] .highlight,body[data-surveye-corners=\"square\"] .profile-card,body[data-surveye-corners=\"square\"] .messages,body[data-surveye-corners=\"square\"] .message,body[data-surveye-corners=\"square\"] .flagbox,body[data-surveye-corners=\"square\"] .map-card,body[data-surveye-corners=\"square\"] .panel,body[data-surveye-corners=\"square\"] .no-results{border-radius:2px}\n")
                .append("body[data-surveye-shadow=\"soft\"] .controls,body[data-surveye-shadow=\"soft\"] .kpi,body[data-surveye-shadow=\"soft\"] .highlight,body[data-surveye-shadow=\"soft\"] .profile-card,body[data-surveye-shadow=\"soft\"] .map-card{box-shadow:0 1px 2px rgba(0,34,68,.04),0 10px 30px rgba(0,34,68,.07)}\n")
                .append("body[data-surveye-shadow=\"soft\"] .panel{box-shadow:0 1px 2px rgba(0,34,68,.035),0 8px 24px rgba(0,34,68,.055)}\n")
                .append("body[data-surveye-shadow=\"lifted\"] .controls,body[data-surveye-shadow=\"lifted\"] .kpi,body[data-surveye-shadow=\"lifted\"] .highlight,body[data-surveye-shadow=\"lifted\"] .profile-card,body[data-surveye-shadow=\"lifted\"] .map-card{box-shadow:0 2px 5px rgba(0,34,68,.06),0 18px 48px rgba(0,34,68,.13)}\n")
                .append("body[data-surveye-shadow=\"lifted\"] .panel{box-shadow:0 2px 4px rgba(0,34,68,.05),0 14px 38px rgba(0,34,68,.10)}\n")
                .append("body[data-surveye-shadow=\"none\"] .controls,body[data-surveye-shadow=\"none\"] .kpi,body[data-surveye-shadow=\"none\"] .highlight,body[data-surveye-shadow=\"none\"] .profile-card,body[data-surveye-shadow=\"none\"] .map-card,body[data-surveye-shadow=\"none\"] .panel{box-shadow:none}\n");

        if (pageWidth > 0) {
            css.append("body[data-surveye-page-width=\"").append(pageWidth)
                    .append("\"] .wrap{max-width:").append(pageWidth).append("px}\n");
        }
        css.append("body[data-surveye-motion=\"subtle\"] .surveye-reveal,body[data-surveye-motion=\"reveal\"] .surveye-reveal{opacity:0;transform:translateY(var(--surveye-reveal-distance,8px));transition:opacity var(--surveye-reveal-duration,.45s) ease,transform var(--surveye-reveal-duration,.45s) ease}\n")
                .append("body[data-surveye-motion=\"reveal\"]{--surveye-reveal-distance:16px;--surveye-reveal-duration:.6s}\n")
                .append("body[data-surveye-motion] .surveye-reveal.in{opacity:1;transform:none}\n")
                .append("@media (hover:hover){body[data-surveye-finish=\"editorial\"]:not([data-surveye-motion=\"none\"]) .panel{transition:transform .2s ease,box-shadow .2s ease}body[data-surveye-finish=\"editorial\"]:not([data-surveye-motion=\"none\"]) .panel:hover{transform:translateY(-2px);box-shadow:0 2px 5px rgba(0,34,68,.05),0 14px 34px rgba(0,34,68,.09)}}\n")
                .append("@media (prefers-reduced-motion:reduce){body .surveye-reveal{opacity:1!important;transform:none!important;transition:none!important}body .panel{transition:none!important}}\n")
                .append("@media(max-width:600px){body[data-surveye-finish=\"editorial\"] .hero{padding-top:34px}body[data-surveye-finish=\"editorial\"] .panel{padding:14px}body[data-surveye-page-width] .wrap{padding-inline:16px}}\n");
        return css.toString();
    }

    private static String motionScript() {
        return "(function(){\n"
                + "  var body=document.body;if(!body||body.getAttribute('data-surveye-motion')==='none')return;\n"
                + "  var nodes=[].slice.call(document.querySelectorAll('.hero,.controls,.kpis,.highlight-grid,.profile-card,.messages,.flagbox,.map-card,.story,.footer'));\n"
                + "  if(!nodes.length)return;\n"
                + "  var reduced=window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;\n"
                + "  nodes.forEach(function(node){node.classList.add('surveye-reveal');});\n"
                + "  if(reduced||!('IntersectionObserver' in window)){nodes.forEach(function(node){node.classList.add('in');});return;}\n"
                + "  var observer=new IntersectionObserver(function(entries){entries.forEach(function(entry){if(entry.isIntersecting){entry.target.classList.add('in');observer.unobserve(entry.target);}});},{threshold:.08,rootMargin:'0px 0px -24px 0px'});\n"
                + "  nodes.forEach(function(node,index){node.style.transitionDelay=Math.min(index%4,3)*45+'ms';observer.observe(node);});\n"
                + "})();";
    }

    private static String addBodyAttributes(String html, String theme, String background,
            String typography, String corners, String shadow, String motion, int pageWidth) {
        int bodyStart = indexOfIgnoreCase(html, "<body");
        if (bodyStart < 0) throw new IllegalArgumentException("dashboard HTML has no <body> tag");
        int bodyEnd = html.indexOf('>', bodyStart);
        if (bodyEnd < 0) throw new IllegalArgumentException("dashboard HTML has an incomplete <body> tag");
        String open = html.substring(bodyStart, bodyEnd);
        open = SURVEYE_ATTRIBUTE.matcher(open).replaceAll("");
        String attrs = " data-surveye-finish=\"" + attr(theme) + "\""
                + " data-surveye-background=\"" + attr(background) + "\""
                + " data-surveye-typography=\"" + attr(typography) + "\""
                + " data-surveye-corners=\"" + attr(corners) + "\""
                + " data-surveye-shadow=\"" + attr(shadow) + "\""
                + " data-surveye-motion=\"" + attr(motion) + "\""
                + " data-surveye-page-width=\"" + pageWidth + "\"";
        return html.substring(0, bodyStart) + open + attrs + html.substring(bodyEnd);
    }

    private static String removeTaggedBlock(String html, String tag, String id) {
        Pattern block = Pattern.compile("(?is)\\s*<" + tag + "\\s+id=\\\"" + Pattern.quote(id)
                + "\\\"[^>]*>.*?</" + tag + ">\\s*");
        return block.matcher(html).replaceAll("\n");
    }

    private static void writeAtomic(Path output, String html) throws IOException {
        Path parent = output.getParent();
        if (parent == null) parent = Paths.get(".").toAbsolutePath().normalize();
        Path temp = Files.createTempFile(parent, output.getFileName().toString(), ".appearance.tmp");
        try {
            Files.write(temp, html.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static int parsePageWidth(String value) {
        try {
            int width = Integer.parseInt(value == null ? "0" : value.trim());
            if (width != 0 && (width < 960 || width > 2000)) {
                throw new IllegalArgumentException("page width must be 0 or between 960 and 2,000 pixels");
            }
            return width;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("page width must be an integer");
        }
    }

    private static String choice(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(label + " has an unsupported value: " + normalized);
        }
        return normalized;
    }

    private static int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String attr(String value) {
        return Matcher.quoteReplacement(value).replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }
}
