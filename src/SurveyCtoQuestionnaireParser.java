import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free reader for SurveyCTO questionnaires.
 *
 * <p>Two inputs are supported. The ODK XForm form definition (the XML that
 * SurveyCTO's designer produces and that Collect consumes) is parsed
 * precisely: the specification is stable, so titles, groups, repeats, field
 * types, choice lists (inline items, itemsets over secondary instances, and
 * jr:itext translations), and calculate fields are all read from the model
 * and body. The printable HTML form is parsed heuristically as a
 * convenience; when its layout is not recognized the error points at the
 * fully supported XML definition instead of guessing.</p>
 */
final class SurveyCtoQuestionnaireParser {
    private static final Pattern ITEXT_REF = Pattern.compile("jr:itext\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern INSTANCE_REF = Pattern.compile("instance\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    /** Unquoted jr:itext(node) form used inside itemsets: the argument names a child node. */
    private static final Pattern ITEXT_NODE_REF = Pattern.compile("jr:itext\\(\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*\\)");
    private static final Pattern NAME_TOKEN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private SurveyCtoQuestionnaireParser() {}

    static boolean looksLikeXform(String content) {
        if (content == null) return false;
        String head = content.substring(0, Math.min(content.length(), 4000)).toLowerCase(Locale.ROOT);
        return head.contains("www.w3.org/2002/xforms")
                || (head.contains("<h:html") && head.contains("<h:head"));
    }

    static boolean looksLikeSurveyCtoHtml(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("surveycto") && !looksLikeXform(content);
    }

    /** The printed table layout itself, for exports that never name SurveyCTO. */
    static boolean looksLikePrintableTable(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("\"fieldcell\"") && lower.contains("\"questioncell\"");
    }

    // ------------------------------------------------------------------
    // XForm form definition
    // ------------------------------------------------------------------

    static QuestionnaireSpec parseXform(String xml) {
        HtmlQuestionnaireParser.Node document = HtmlQuestionnaireParser.MiniHtml.parse(xml);
        HtmlQuestionnaireParser.Node model = firstByLocalName(document, "model");
        HtmlQuestionnaireParser.Node body = firstByLocalName(document, "body");
        if (model == null || body == null) {
            throw new IllegalArgumentException(
                    "Not a SurveyCTO form definition: the XForm model or body element is missing.");
        }

        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.sourceFormat = "SurveyCTO form definition";
        HtmlQuestionnaireParser.Node title = firstByLocalName(document, "title");
        String titleText = text(title);
        if (!blank(titleText)) spec.title = titleText;

        Itext itext = readItext(model);
        if (!blank(itext.language)) spec.language = normalizeLanguage(itext.language);
        Map<String, Bind> binds = readBinds(model);
        Map<String, List<HtmlQuestionnaireParser.Node>> instances = readSecondaryInstances(model);

        Walk walk = new Walk(spec, itext, binds, instances);
        for (HtmlQuestionnaireParser.Node child : body.children) walk.visit(child, null, "", false);
        walk.appendBodylessBinds();

        if (spec.questions.isEmpty()) {
            throw new IllegalArgumentException(
                    "The SurveyCTO form definition contains no readable fields.");
        }
        if (walk.repeatGroups > 0) {
            spec.warnings.add("Repeat-group fields are exported to wide or long companion columns; "
                    + walk.repeatGroups + " repeat group(s) were read and will chart only where a matching data column exists.");
        }
        return spec;
    }

    /** Per-question walk state over the XForm body. */
    private static final class Walk {
        final QuestionnaireSpec spec;
        final Itext itext;
        final Map<String, Bind> binds;
        final Map<String, List<HtmlQuestionnaireParser.Node>> instances;
        final Map<String, QuestionSection> sectionsByPath = new LinkedHashMap<String, QuestionSection>();
        QuestionSection currentSection;
        int repeatGroups;

        Walk(QuestionnaireSpec spec, Itext itext, Map<String, Bind> binds,
                Map<String, List<HtmlQuestionnaireParser.Node>> instances) {
            this.spec = spec;
            this.itext = itext;
            this.binds = binds;
            this.instances = instances;
        }

        void visit(HtmlQuestionnaireParser.Node node, String sectionPath, String subsection, boolean inRepeat) {
            if (!node.isElement()) return;
            String tag = localName(node.tag);
            if ("group".equals(tag)) {
                String label = resolveLabel(node, itext);
                String ref = ref(node);
                boolean topLevel = sectionPath == null;
                String nextSection = sectionPath;
                String nextSub = subsection;
                if (topLevel) {
                    nextSection = ref != null ? ref : ("group-" + (spec.sections.size() + 1));
                    section(nextSection, blank(label) ? "Section " + (spec.sections.size() + 1) : label);
                } else if (!blank(label)) {
                    nextSub = blank(subsection) ? label : subsection + " \u00b7 " + label;
                }
                for (HtmlQuestionnaireParser.Node child : node.children) {
                    visit(child, nextSection, nextSub, inRepeat);
                }
                return;
            }
            if ("repeat".equals(tag)) {
                repeatGroups++;
                for (HtmlQuestionnaireParser.Node child : node.children) {
                    visit(child, sectionPath == null ? ensureDefaultSectionPath() : sectionPath, subsection, true);
                }
                return;
            }
            if ("input".equals(tag) || "select1".equals(tag) || "select".equals(tag)
                    || "upload".equals(tag) || "trigger".equals(tag) || "range".equals(tag)) {
                addControl(node, tag, sectionPath, subsection, inRepeat);
                return;
            }
            for (HtmlQuestionnaireParser.Node child : node.children) {
                visit(child, sectionPath, subsection, inRepeat);
            }
        }

        String ensureDefaultSectionPath() {
            if (currentSection == null) section("__form__", spec.title);
            for (Map.Entry<String, QuestionSection> entry : sectionsByPath.entrySet()) {
                if (entry.getValue() == currentSection) return entry.getKey();
            }
            return "__form__";
        }

        void section(String path, String titleText) {
            QuestionSection existing = sectionsByPath.get(path);
            if (existing != null) { currentSection = existing; return; }
            QuestionSection section = new QuestionSection(spec.sections.size() + 1, titleText);
            spec.sections.add(section);
            sectionsByPath.put(path, section);
            currentSection = section;
        }

        void addControl(HtmlQuestionnaireParser.Node node, String tag,
                String sectionPath, String subsection, boolean inRepeat) {
            String ref = ref(node);
            if (blank(ref)) return;
            if (sectionPath == null) sectionPath = ensureDefaultSectionPath();
            QuestionSection section = sectionsByPath.get(sectionPath);
            if (section == null) { section("__form__", spec.title); section = currentSection; }

            Bind bind = binds.get(ref);
            Question question = new Question();
            question.variable = lastSegment(ref);
            question.label = resolveLabel(node, itext);
            if (blank(question.label)) question.label = question.variable;
            question.subsection = subsection == null ? "" : subsection;
            question.section = section.title;
            question.sectionNumber = section.number;
            if (inRepeat) question.scope = "repeat";
            if (bind != null && !blank(bind.relevant)) question.condition = bind.relevant;

            String bindType = bind == null ? "" : lower(bind.type);
            if ("select1".equals(tag) || "select_one".equals(bindType) || "select1".equals(bindType)) {
                question.rawType = "single-select";
                question.type = "single";
            } else if ("select".equals(tag) || "select".equals(bindType) || "select_multiple".equals(bindType)) {
                question.rawType = "multi-select";
                question.type = "multi";
            } else if ("upload".equals(tag)) {
                String media = lower(attr(node, "mediatype"));
                question.rawType = media.startsWith("audio") ? "audio" : "picture";
                question.type = media.startsWith("audio") ? "audio" : "picture";
            } else if ("trigger".equals(tag)) {
                question.rawType = "acknowledge";
                question.type = "single";
                question.options.add(new QuestionOption("OK", "OK", false));
            } else {
                question.rawType = displayBindType(bindType);
                question.type = mapBindType(bindType);
            }
            if (bind != null && !blank(bind.calculate)) question.rawType = "calculated " + question.rawType;

            if ("single".equals(question.type) || "multi".equals(question.type)) {
                collectChoices(node, question);
                if (question.options.isEmpty() && !"trigger".equals(tag)) {
                    spec.warnings.add("Field " + question.variable
                            + " is a choice question but its list could not be resolved.");
                }
            }
            if (isNote(bind, question)) return;
            section.questions.add(question);
            spec.questions.add(question);
        }

        boolean isNote(Bind bind, Question question) {
            // SurveyCTO notes are readonly string fields that store no answer.
            return bind != null && bind.readonly && "text".equals(question.type)
                    && blank(bind.calculate) && question.options.isEmpty();
        }

        void collectChoices(HtmlQuestionnaireParser.Node control, Question question) {
            for (HtmlQuestionnaireParser.Node child : control.children) {
                if (!child.isElement()) continue;
                String tag = localName(child.tag);
                if ("item".equals(tag)) {
                    String code = text(firstChildByLocalName(child, "value"));
                    String label = resolveLabel(child, itext);
                    if (!blank(code)) {
                        question.options.add(new QuestionOption(code, blank(label) ? code : label, isSpecialCode(code)));
                    }
                } else if ("itemset".equals(tag)) {
                    resolveItemset(child, question);
                }
            }
        }

        void resolveItemset(HtmlQuestionnaireParser.Node itemset, Question question) {
            String nodeset = attr(itemset, "nodeset");
            if (blank(nodeset)) return;
            Matcher matcher = INSTANCE_REF.matcher(nodeset);
            if (!matcher.find()) return;
            List<HtmlQuestionnaireParser.Node> items = instances.get(matcher.group(1));
            if (items == null) return;
            String valueRef = childRef(itemset, "value", "name");
            String labelRef = childRef(itemset, "label", "label");
            for (HtmlQuestionnaireParser.Node item : items) {
                String code = text(firstChildByLocalName(item, valueRef));
                String label = null;
                Matcher quoted = ITEXT_REF.matcher(labelRef);
                Matcher nodeRef = ITEXT_NODE_REF.matcher(labelRef);
                if (quoted.find()) {
                    label = itext.resolve(quoted.group(1));
                } else if (nodeRef.find()) {
                    label = itext.resolve(text(firstChildByLocalName(item, nodeRef.group(1))));
                } else {
                    label = text(firstChildByLocalName(item, labelRef));
                }
                if (blank(label)) label = itext.resolve(text(firstChildByLocalName(item, "itextid")));
                if (!blank(code)) {
                    question.options.add(new QuestionOption(code, blank(label) ? code : label, isSpecialCode(code)));
                }
            }
        }

        void appendBodylessBinds() {
            for (Map.Entry<String, Bind> entry : binds.entrySet()) {
                Bind bind = entry.getValue();
                if (blank(bind.calculate) || bind.seen) continue;
                Question question = new Question();
                question.variable = lastSegment(entry.getKey());
                question.label = question.variable;
                question.rawType = "calculated " + displayBindType(lower(bind.type));
                question.type = blank(bind.type) ? "text" : mapBindType(lower(bind.type));
                QuestionSection section = sectionFor(entry.getKey());
                question.section = section.title;
                question.sectionNumber = section.number;
                section.questions.add(question);
                spec.questions.add(question);
            }
        }

        QuestionSection sectionFor(String nodeset) {
            for (Map.Entry<String, QuestionSection> entry : sectionsByPath.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith("/") ) continue;
                if (nodeset.startsWith(path.endsWith("/") ? path : path + "/")) return entry.getValue();
            }
            if (currentSection == null) section("__form__", spec.title);
            return spec.sections.get(spec.sections.size() - 1);
        }
    }

    private static String displayBindType(String bindType) {
        String value = bindType == null ? "" : bindType;
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        if (value.equals("int") || value.equals("integer")) return "numeric integer";
        if (value.equals("decimal")) return "numeric decimal";
        if (value.equals("datetime")) return "date and time";
        if (value.isEmpty() || value.equals("string")) return "text";
        return value;
    }

    private static String mapBindType(String bindType) {
        String value = bindType == null ? "" : bindType;
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        if (value.equals("int") || value.equals("integer") || value.equals("decimal")) return "numeric";
        if (value.equals("date") || value.equals("datetime") || value.equals("time")) return "date";
        if (value.equals("geopoint") || value.equals("geotrace") || value.equals("geoshape")) return "gps";
        if (value.equals("binary")) return "picture";
        if (value.equals("barcode") || value.equals("string") || value.isEmpty()) return "text";
        if (value.equals("boolean")) return "single";
        return "text";
    }

    private static final class Bind {
        String type;
        String calculate;
        String relevant;
        boolean readonly;
        boolean seen;
    }

    private static Map<String, Bind> readBinds(HtmlQuestionnaireParser.Node model) {
        Map<String, Bind> binds = new LinkedHashMap<String, Bind>();
        List<HtmlQuestionnaireParser.Node> nodes = new ArrayList<HtmlQuestionnaireParser.Node>();
        collectByLocalName(model, "bind", nodes);
        for (HtmlQuestionnaireParser.Node node : nodes) {
            String nodeset = attr(node, "nodeset");
            if (blank(nodeset)) nodeset = attr(node, "ref");
            if (blank(nodeset)) continue;
            Bind bind = new Bind();
            bind.type = attr(node, "type");
            bind.calculate = attr(node, "calculate");
            bind.relevant = attr(node, "relevant");
            bind.readonly = "true()".equalsIgnoreCase(trim(attr(node, "readonly")));
            binds.put(nodeset, bind);
        }
        return binds;
    }

    private static Map<String, List<HtmlQuestionnaireParser.Node>> readSecondaryInstances(
            HtmlQuestionnaireParser.Node model) {
        Map<String, List<HtmlQuestionnaireParser.Node>> out =
                new HashMap<String, List<HtmlQuestionnaireParser.Node>>();
        List<HtmlQuestionnaireParser.Node> instances = new ArrayList<HtmlQuestionnaireParser.Node>();
        collectByLocalName(model, "instance", instances);
        for (HtmlQuestionnaireParser.Node instance : instances) {
            String id = attr(instance, "id");
            if (blank(id)) continue;
            List<HtmlQuestionnaireParser.Node> items = new ArrayList<HtmlQuestionnaireParser.Node>();
            collectByLocalName(instance, "item", items);
            out.put(id, items);
        }
        return out;
    }

    /** Default-language jr:itext lookup. */
    private static final class Itext {
        String language;
        final Map<String, String> values = new HashMap<String, String>();

        String resolve(String key) {
            return key == null ? null : values.get(key);
        }
    }

    private static Itext readItext(HtmlQuestionnaireParser.Node model) {
        Itext itext = new Itext();
        HtmlQuestionnaireParser.Node itextNode = firstByLocalName(model, "itext");
        if (itextNode == null) return itext;
        HtmlQuestionnaireParser.Node chosen = null;
        List<HtmlQuestionnaireParser.Node> translations = new ArrayList<HtmlQuestionnaireParser.Node>();
        collectByLocalName(itextNode, "translation", translations);
        for (HtmlQuestionnaireParser.Node translation : translations) {
            if ("true()".equalsIgnoreCase(trim(attr(translation, "default")))) { chosen = translation; break; }
        }
        if (chosen == null && !translations.isEmpty()) chosen = translations.get(0);
        if (chosen == null) return itext;
        itext.language = attr(chosen, "lang");
        List<HtmlQuestionnaireParser.Node> texts = new ArrayList<HtmlQuestionnaireParser.Node>();
        collectByLocalName(chosen, "text", texts);
        for (HtmlQuestionnaireParser.Node textNode : texts) {
            String id = attr(textNode, "id");
            HtmlQuestionnaireParser.Node value = firstChildByLocalName(textNode, "value");
            if (!blank(id) && value != null) itext.values.put(id, text(value));
        }
        return itext;
    }

    private static String resolveLabel(HtmlQuestionnaireParser.Node node, Itext itext) {
        HtmlQuestionnaireParser.Node label = firstChildByLocalName(node, "label");
        if (label == null) return null;
        String ref = attr(label, "ref");
        if (!blank(ref)) {
            Matcher matcher = ITEXT_REF.matcher(ref);
            if (matcher.find()) {
                String resolved = itext.resolve(matcher.group(1));
                if (!blank(resolved)) return clean(resolved);
            }
        }
        return clean(text(label));
    }

    // ------------------------------------------------------------------
    // Printable HTML form (best effort)
    // ------------------------------------------------------------------

    static QuestionnaireSpec parsePrintHtml(String html) {
        HtmlQuestionnaireParser.Node document = HtmlQuestionnaireParser.MiniHtml.parse(html);
        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.sourceFormat = "SurveyCTO printable form";
        HtmlQuestionnaireParser.Node title = HtmlQuestionnaireParser.firstByTag(document, "h4");
        if (title == null) title = HtmlQuestionnaireParser.firstByTag(document, "h1");
        if (title == null) title = HtmlQuestionnaireParser.firstByTag(document, "title");
        String titleText = clean(text(title));
        if (!blank(titleText)) spec.title = titleText;

        TableWalk walk = new TableWalk(spec);
        walk.run(document);
        if (spec.questions.size() < 1) {
            throw new IllegalArgumentException(
                    "This looks like a SurveyCTO page, but the printable layout was not recognized "
                    + "(found " + spec.questions.size() + " field(s)). Use the form definition XML instead: "
                    + "in SurveyCTO, open Design, download the form files, and pass the .xml file to surveye \u2014 "
                    + "that format is fully supported.");
        }
        if (walk.choiceFields > 0 || walk.freeFields > 0) {
            spec.warnings.add("Printable forms do not encode field types: "
                    + walk.choiceFields + " choice field(s) were read as select_one, and "
                    + walk.freeFields + " open, note, or calculated field(s) as text (display-only notes are "
                    + "dropped automatically when no data column matches). Build from the form-definition "
                    + "XML for exact types.");
        }
        return spec;
    }

    /**
     * Certified reader for the table layout SurveyCTO prints: one outer
     * Field/Question/Answer table whose rows are fields, dark colspan rows
     * are top-level groups, breadcrumb rows with spacer indentation are
     * nested groups (optionally "(Repeated group)"), choice lists are
     * nested three-column tables, and notes carry a response-note answer
     * cell.
     */
    private static final class TableWalk {
        final QuestionnaireSpec spec;
        final List<Sub> stack = new ArrayList<Sub>();
        QuestionSection current;
        int choiceFields;
        int freeFields;

        static final class Sub {
            final int depth;
            final String label;
            final boolean repeat;
            Sub(int depth, String label, boolean repeat) {
                this.depth = depth; this.label = label; this.repeat = repeat;
            }
        }

        TableWalk(QuestionnaireSpec spec) { this.spec = spec; }

        void run(HtmlQuestionnaireParser.Node document) {
            HtmlQuestionnaireParser.Node outer = outerTable(document);
            if (outer == null) return;
            List<HtmlQuestionnaireParser.Node> rows = new ArrayList<HtmlQuestionnaireParser.Node>();
            directRows(outer, rows);
            for (HtmlQuestionnaireParser.Node row : rows) visitRow(row);
        }

        HtmlQuestionnaireParser.Node outerTable(HtmlQuestionnaireParser.Node node) {
            if (node.isElement() && "table".equals(node.tag)) {
                List<HtmlQuestionnaireParser.Node> fieldCells = new ArrayList<HtmlQuestionnaireParser.Node>();
                collectWithAnyClassStatic(node, new String[]{"fieldCell"}, fieldCells);
                if (!fieldCells.isEmpty()) return node;
            }
            for (HtmlQuestionnaireParser.Node child : node.children) {
                if (!child.isElement()) continue;
                HtmlQuestionnaireParser.Node found = outerTable(child);
                if (found != null) return found;
            }
            return null;
        }

        /** Rows of this table only: recursion stops at nested tables. */
        void directRows(HtmlQuestionnaireParser.Node node, List<HtmlQuestionnaireParser.Node> out) {
            for (HtmlQuestionnaireParser.Node child : node.children) {
                if (!child.isElement()) continue;
                if ("tr".equals(child.tag)) { out.add(child); continue; }
                if ("table".equals(child.tag)) continue;
                if ("thead".equals(child.tag)) continue;
                directRows(child, out);
            }
        }

        void visitRow(HtmlQuestionnaireParser.Node row) {
            List<HtmlQuestionnaireParser.Node> cells = directCells(row);
            if (cells.isEmpty()) return;

            HtmlQuestionnaireParser.Node fieldCell = findWithin(row, "fieldCell");
            String relevance = relevanceText(row);
            boolean groupRelevance = relevance != null && lower(relevance).startsWith("group relevant when");
            boolean repeatMark = rowText(row).contains("(Repeated group)");

            if (fieldCell == null || groupRelevance || repeatMark) {
                String headerLabel = headerLabel(row, cells);
                if (blank(headerLabel)) return;
                int depth = spacerDepth(row);
                boolean topLevel = hasColspanHeader(cells);
                if (topLevel || depth == 0) {
                    stack.clear();
                    section(headerLabel, relevance);
                } else {
                    while (!stack.isEmpty() && stack.get(stack.size() - 1).depth >= depth) {
                        stack.remove(stack.size() - 1);
                    }
                    stack.add(new Sub(depth, headerLabel, repeatMark));
                }
                return;
            }

            String name = fieldName(fieldCell);
            if (blank(name)) return;
            int depth = spacerDepth(row);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).depth > depth) {
                stack.remove(stack.size() - 1);
            }
            if (current == null) section(spec.title, null);

            Question question = new Question();
            question.variable = name;
            HtmlQuestionnaireParser.Node questionCell = findWithin(row, "questionCell");
            String label = questionCell == null ? null : labelWithoutRelevance(questionCell);
            question.label = blank(label) ? name : label;
            String fieldRelevance = questionCell == null ? null : relevanceText(questionCell);
            if (fieldRelevance != null) {
                question.condition = fieldRelevance.replaceFirst("(?i)^question relevant when:\\s*", "");
            }
            if (!stack.isEmpty()) {
                StringBuilder sub = new StringBuilder();
                for (Sub frame : stack) {
                    if (sub.length() > 0) sub.append(" \u00b7 ");
                    sub.append(frame.label);
                    if (frame.repeat) question.scope = "repeat";
                }
                question.subsection = sub.toString();
            }
            question.section = current.title;
            question.sectionNumber = current.number;

            HtmlQuestionnaireParser.Node answerCell = cells.get(cells.size() - 1);
            HtmlQuestionnaireParser.Node choices = firstChildTable(answerCell);
            if (choices != null) readChoices(choices, question);
            if (!question.options.isEmpty()) {
                question.rawType = "choice list";
                question.type = "single";
                choiceFields++;
            } else {
                // Printables render open questions and display-only notes with
                // the same empty answer cell; keep both and let data matching
                // discard the notes, which never have a data column.
                question.rawType = "open or note";
                question.type = "text";
                freeFields++;
            }
            current.questions.add(question);
            spec.questions.add(question);
        }

        void section(String label, String relevance) {
            current = new QuestionSection(spec.sections.size() + 1, label);
            spec.sections.add(current);
        }

        String headerLabel(HtmlQuestionnaireParser.Node row, List<HtmlQuestionnaireParser.Node> cells) {
            for (HtmlQuestionnaireParser.Node cell : cells) {
                HtmlQuestionnaireParser.Node span = HtmlQuestionnaireParser.firstByTag(cell, "span");
                String value = clean(text(span));
                if (blank(value) || "(Repeated group)".equals(value)) continue;
                int gt = value.lastIndexOf(" > ");
                if (gt >= 0) value = value.substring(gt + 3);
                value = value.replaceAll("\\s*\\(\\d+\\)$", "").trim();
                if (!blank(value)) return value;
            }
            return null;
        }

        boolean hasColspanHeader(List<HtmlQuestionnaireParser.Node> cells) {
            for (HtmlQuestionnaireParser.Node cell : cells) {
                String style = attr(cell, "style");
                if (style != null && style.replace(" ", "").contains("#707070")) return true;
            }
            return false;
        }

        String fieldName(HtmlQuestionnaireParser.Node fieldCell) {
            String value = clean(text(fieldCell));
            if (blank(value)) return null;
            int space = value.indexOf(' ');
            String token = space < 0 ? value : value.substring(0, space);
            return NAME_TOKEN.matcher(token).matches() ? token : null;
        }

        String labelWithoutRelevance(HtmlQuestionnaireParser.Node questionCell) {
            StringBuilder out = new StringBuilder();
            textSkippingRelevance(questionCell, out);
            return clean(out.toString());
        }

        void textSkippingRelevance(HtmlQuestionnaireParser.Node node, StringBuilder out) {
            if (node.isElement() && (node.hasClass("relevance") || node.hasClass("hint"))) return;
            if (!node.isElement()) {
                if (node.text != null) out.append(node.text).append(' ');
                return;
            }
            for (HtmlQuestionnaireParser.Node child : node.children) textSkippingRelevance(child, out);
        }

        String relevanceText(HtmlQuestionnaireParser.Node node) {
            HtmlQuestionnaireParser.Node relevance = findWithin(node, "relevance");
            return relevance == null ? null : clean(text(relevance));
        }

        String rowText(HtmlQuestionnaireParser.Node row) {
            String value = text(row);
            return value == null ? "" : value;
        }

        int spacerDepth(HtmlQuestionnaireParser.Node row) {
            List<HtmlQuestionnaireParser.Node> spacers = new ArrayList<HtmlQuestionnaireParser.Node>();
            collectWithAnyClassStatic(row, new String[]{"spacer"}, spacers);
            return spacers.size();
        }

        List<HtmlQuestionnaireParser.Node> directCells(HtmlQuestionnaireParser.Node row) {
            List<HtmlQuestionnaireParser.Node> out = new ArrayList<HtmlQuestionnaireParser.Node>();
            for (HtmlQuestionnaireParser.Node child : row.children) {
                if (child.isElement() && "td".equals(child.tag)) out.add(child);
            }
            return out;
        }

        HtmlQuestionnaireParser.Node firstChildTable(HtmlQuestionnaireParser.Node cell) {
            if (cell == null) return null;
            List<HtmlQuestionnaireParser.Node> tables = new ArrayList<HtmlQuestionnaireParser.Node>();
            collectTables(cell, tables);
            for (HtmlQuestionnaireParser.Node table : tables) {
                if (findWithin(table, "fieldCell") == null) return table;
            }
            return null;
        }

        void collectTables(HtmlQuestionnaireParser.Node node, List<HtmlQuestionnaireParser.Node> out) {
            for (HtmlQuestionnaireParser.Node child : node.children) {
                if (!child.isElement()) continue;
                if ("table".equals(child.tag)) { out.add(child); continue; }
                collectTables(child, out);
            }
        }

        void readChoices(HtmlQuestionnaireParser.Node table, Question question) {
            List<HtmlQuestionnaireParser.Node> rows = new ArrayList<HtmlQuestionnaireParser.Node>();
            directRows(table, rows);
            for (HtmlQuestionnaireParser.Node row : rows) {
                List<HtmlQuestionnaireParser.Node> cells = directCells(row);
                if (cells.size() < 2) continue;
                String code = null;
                String label = null;
                for (HtmlQuestionnaireParser.Node cell : cells) {
                    String value = clean(text(cell));
                    if (blank(value)) continue;
                    if (code == null) code = value;
                    else { label = value; break; }
                }
                if (!blank(code) && code.length() <= 40) {
                    question.options.add(new QuestionOption(code, blank(label) ? code : label, isSpecialCode(code)));
                }
            }
        }

        HtmlQuestionnaireParser.Node findWithin(HtmlQuestionnaireParser.Node node, String className) {
            if (node.isElement() && node.hasClass(className)) return node;
            for (HtmlQuestionnaireParser.Node child : node.children) {
                if (!child.isElement()) continue;
                HtmlQuestionnaireParser.Node found = findWithin(child, className);
                if (found != null) return found;
            }
            return null;
        }
    }

    private static void collectWithAnyClassStatic(HtmlQuestionnaireParser.Node node, String[] names,
            List<HtmlQuestionnaireParser.Node> out) {
        for (HtmlQuestionnaireParser.Node child : node.children) {
            if (!child.isElement()) continue;
            boolean hit = false;
            for (String name : names) if (child.hasClass(name)) { hit = true; break; }
            if (hit) out.add(child);
            collectWithAnyClassStatic(child, names, out);
        }
    }


    // ------------------------------------------------------------------
    // Shared small helpers
    // ------------------------------------------------------------------

    private static boolean isSpecialCode(String code) {
        String value = trim(code);
        if (value.isEmpty()) return false;
        if (value.startsWith("-")) {
            try { Long.parseLong(value); return true; } catch (NumberFormatException ignored) { return false; }
        }
        return false;
    }

    private static String childRef(HtmlQuestionnaireParser.Node itemset, String childName, String fallback) {
        HtmlQuestionnaireParser.Node child = firstChildByLocalName(itemset, childName);
        String ref = child == null ? null : attr(child, "ref");
        return blank(ref) ? fallback : trim(ref);
    }

    private static String ref(HtmlQuestionnaireParser.Node node) {
        String ref = attr(node, "ref");
        if (blank(ref)) ref = attr(node, "nodeset");
        return blank(ref) ? null : trim(ref);
    }

    private static String lastSegment(String path) {
        String trimmed = trim(path);
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    private static String localName(String tag) {
        if (tag == null) return "";
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private static HtmlQuestionnaireParser.Node firstByLocalName(HtmlQuestionnaireParser.Node node, String name) {
        if (node.isElement() && localName(node.tag).equals(name)) return node;
        for (HtmlQuestionnaireParser.Node child : node.children) {
            if (!child.isElement()) continue;
            HtmlQuestionnaireParser.Node found = firstByLocalName(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static HtmlQuestionnaireParser.Node firstChildByLocalName(HtmlQuestionnaireParser.Node node, String name) {
        if (node == null) return null;
        String wanted = lower(name);
        for (HtmlQuestionnaireParser.Node child : node.children) {
            if (child.isElement() && localName(child.tag).equals(wanted)) return child;
        }
        return null;
    }

    private static void collectByLocalName(HtmlQuestionnaireParser.Node node, String name,
            List<HtmlQuestionnaireParser.Node> output) {
        if (node.isElement() && localName(node.tag).equals(name)) output.add(node);
        for (HtmlQuestionnaireParser.Node child : node.children) {
            if (child.isElement()) collectByLocalName(child, name, output);
        }
    }






    private static String attr(HtmlQuestionnaireParser.Node node, String name) {
        return node == null || node.attributes == null ? null : node.attributes.get(name);
    }

    private static String text(HtmlQuestionnaireParser.Node node) {
        return node == null ? null : HtmlQuestionnaireParser.cleanNodeText(node, null);
    }

    private static String clean(String value) {
        if (value == null) return null;
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeLanguage(String value) {
        String lower = lower(trim(value));
        if (lower.isEmpty()) return "und";
        if (lower.startsWith("english")) return "en";
        if (lower.startsWith("arabic")) return "ar";
        if (lower.startsWith("urdu")) return "ur";
        if (lower.length() <= 3) return lower;
        int paren = lower.indexOf('(');
        if (paren > 0) {
            String inside = lower.substring(paren + 1).replace(")", "").trim();
            if (inside.length() >= 2 && inside.length() <= 3) return inside;
        }
        return lower.substring(0, 2);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
