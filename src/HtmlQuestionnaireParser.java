import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free, tolerant reader for Survey Solutions questionnaire HTML exports.
 *
 * <p>The exporter has used more than one presentation template over time. This
 * parser deliberately ignores the stylesheet and matches semantic class tokens
 * rather than exact tags or serialized attribute order.</p>
 */
final class HtmlQuestionnaireParser {
    private static final Pattern SCOPE = Pattern.compile("(?i)\\bscope\\s*:\\s*([\\p{L}\\p{N}_-]+)");
    private static final Pattern MARKER = Pattern.compile("(?i)^(?:C|E|I|F|[VWM]\\d+)$");
    private static final Set<String> VOID_TAGS = new HashSet<String>();
    private static final Set<String> KEPT_ATTRIBUTES = new HashSet<String>();

    static {
        String[] voidTags = new String[]{
                "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
                "param", "source", "track", "wbr"
        };
        for (String tag : voidTags) VOID_TAGS.add(tag);
        String[] attributes = new String[]{"class", "id", "href", "type", "name", "for", "alt", "lang"};
        for (String attribute : attributes) KEPT_ATTRIBUTES.add(attribute);
    }

    private HtmlQuestionnaireParser() {}

    /**
     * Parses either an HTML string or a filename. HTML is recognized by its first
     * non-whitespace character; all other strings are treated as paths.
     */
    static QuestionnaireSpec parse(String input) throws IOException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Questionnaire HTML or filename is required.");
        }
        String trimmed = input.trim();
        return trimmed.startsWith("<") ? parseHtml(input) : parseFile(input);
    }

    static QuestionnaireSpec parse(Path filename) throws IOException {
        if (filename == null) throw new IllegalArgumentException("Questionnaire filename is required.");
        return parseFile(filename.toString());
    }

    static QuestionnaireSpec parseFile(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Questionnaire filename is required.");
        }
        return parseHtml(Util.readUtf8(filename));
    }

    static QuestionnaireSpec parseHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalArgumentException("Questionnaire HTML is empty.");
        }

        Node document = MiniHtml.parse(html);
        List<Node> sectionNodes = new ArrayList<Node>();
        collectByTagAndClass(document, "section", "section", sectionNodes);
        if (sectionNodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Not a Survey Solutions questionnaire: no recognizable questionnaire sections were found.");
        }

        QuestionnaireSpec spec = new QuestionnaireSpec();
        Node htmlNode = firstByTag(document, "html");
        if (htmlNode != null && htmlNode.attributes != null) {
            spec.language = normalizeLanguage(htmlNode.attributes.get("lang"));
        }
        Node titleContainer = firstByClass(document, "questionnaire_title");
        Node titleNode = titleContainer == null ? null : firstByTag(titleContainer, "h1");
        if (titleNode == null) titleNode = firstByTag(document, "h1");
        String title = cleanNodeText(titleNode, null);
        if (!title.isEmpty()) spec.title = title;

        LinkedHashMap<String, Question> seenVariables = new LinkedHashMap<String, Question>();
        int sectionNumber = 0;
        for (Node sectionNode : sectionNodes) {
            sectionNumber++;
            Node header = firstDirectByClass(sectionNode, "section_header");
            if (header == null) header = firstByClass(sectionNode, "section_header");
            Node h2 = header == null ? null : firstByTag(header, "h2");
            String sectionTitle = cleanNodeText(h2, null);
            if (sectionTitle.isEmpty()) {
                sectionTitle = "Section " + sectionNumber;
                spec.warnings.add("Section " + sectionNumber + " has no readable title; a fallback title was used.");
            }

            QuestionSection section = new QuestionSection(sectionNumber, sectionTitle);
            spec.sections.add(section);
            SectionState state = new SectionState(spec, section, seenVariables);
            for (Node child : sectionNode.children) {
                if (!child.isElement()) continue;
                if (child.hasClass("section_header")) continue;
                processSectionNode(child, state);
            }
            if (!state.context.isEmpty()) {
                spec.warnings.add("Section '" + sectionTitle + "' ended before " + state.context.size()
                        + " subsection/roster footer(s); the remaining context was closed automatically.");
            }
        }

        if (spec.questions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Not a Survey Solutions questionnaire: sections were present but no recognizable questions were found.");
        }
        return spec;
    }

    private static String normalizeLanguage(String value) {
        if (value == null) return "und";
        String language = value.trim().replace('_', '-');
        if (language.matches("(?i)^[a-z]{2,8}(?:-[a-z0-9]{1,8})*$")) return language;
        return "und";
    }

    private static void processSectionNode(Node node, SectionState state) {
        if (!node.isElement()) return;

        if (node.hasClass("question-container")) {
            Question question = parseQuestion(node, state.section, state.currentSubsection(),
                    state.pendingFamilyPrompt, state.spec);
            if (question != null) addQuestion(question, state);
            return;
        }
        if (node.hasClass("static-text")) {
            Node promptNode = firstByClass(node, "static_text");
            state.pendingFamilyPrompt = cleanNodeText(promptNode == null ? node : promptNode, null);
            return;
        }
        if (node.hasClass("group_footer")) {
            state.pendingFamilyPrompt = "";
            if (!state.context.isEmpty()) state.context.remove(state.context.size() - 1);
            else state.spec.warnings.add("An unmatched subsection/roster footer was ignored in section '"
                    + state.section.title + "'.");
            return;
        }
        if (node.hasClass("group") || node.hasClass("roster")) {
            state.pendingFamilyPrompt = "";
            String markerClass = node.hasClass("roster") ? "roster-title" : "sub_section";
            Node marker = firstByClass(node, markerClass);
            String name = cleanNodeText(marker, "C");
            if (name.isEmpty()) name = node.hasClass("roster") ? "Roster" : "Subsection";
            state.context.add(name);
            return;
        }

        // Tolerate an additional wrapper introduced by an exporter revision. Do
        // not recurse into already recognized records, where nested option divs
        // could otherwise be mistaken for section contents.
        for (Node child : node.children) {
            if (child.isElement()) processSectionNode(child, state);
        }
    }

    private static void addQuestion(Question question, SectionState state) {
        String key = question.variable.toLowerCase(Locale.ROOT);
        Question existing = state.seenVariables.get(key);
        if (existing != null) {
            state.spec.warnings.add("Duplicate variable name '" + question.variable + "' in section '"
                    + state.section.title + "' was ignored; the first occurrence in section '"
                    + existing.section + "' was retained.");
            return;
        }
        state.seenVariables.put(key, question);
        state.section.questions.add(question);
        state.spec.questions.add(question);
    }

    private static Question parseQuestion(Node container, QuestionSection section, String subsection,
                                          String familyPrompt, QuestionnaireSpec spec) {
        Node questionBlock = firstDirectByClass(container, "question");
        if (questionBlock == null) questionBlock = firstByClass(container, "question");
        Node answerBlock = firstDirectByClass(container, "answer");
        if (answerBlock == null) answerBlock = firstByClass(container, "answer");
        if (questionBlock == null || answerBlock == null) {
            spec.warnings.add("A malformed question container in section '" + section.title + "' was ignored.");
            return null;
        }

        Node titleNode = firstDirectByClass(questionBlock, "question-title");
        if (titleNode == null) titleNode = firstByClass(questionBlock, "question-title");
        if (isComputedVariable(titleNode)) return null;

        Node primaryMeta = firstPrimaryMetadata(answerBlock);
        if (primaryMeta == null) {
            spec.warnings.add("A question without primary answer metadata in section '" + section.title
                    + "' was ignored.");
            return null;
        }
        Node variableNode = firstDirectByClass(primaryMeta, "variable_name");
        if (variableNode == null) variableNode = firstByClass(primaryMeta, "variable_name");
        String variable = cleanNodeText(variableNode, null);
        if (variable.isEmpty()) {
            spec.warnings.add("A question without a variable name in section '" + section.title + "' was ignored.");
            return null;
        }

        Node typeNode = firstDirectByClass(primaryMeta, "type");
        if (typeNode == null) typeNode = firstByClass(primaryMeta, "type");
        String rawType = cleanDirectText(typeNode);
        if (rawType.isEmpty()) rawType = baseTypeFallback(cleanNodeText(typeNode, null));
        String scope = parseScope(typeNode);
        Node conditionNode = firstByClass(questionBlock, "condition");
        String condition = cleanNodeText(conditionNode, "E");

        Question question = new Question();
        question.variable = variable;
        String parsedLabel = cleanNodeText(titleNode, "C");
        question.label = Util.displayLabel(parsedLabel, variable);
        if (parsedLabel.isEmpty()) {
            spec.warnings.add("Question '" + variable + "' has no readable label; its variable name was used.");
        }
        question.rawType = rawType;
        question.type = normalizeType(rawType);
        question.scope = scope;
        question.familyPrompt = familyPrompt == null ? "" : familyPrompt;
        question.condition = condition;
        question.section = section.title;
        question.sectionNumber = section.number;
        question.subsection = subsection == null ? "" : subsection;
        collectOptions(answerBlock, question.options);
        return question;
    }

    private static boolean isComputedVariable(Node titleNode) {
        if (titleNode == null) return false;
        for (Node child : titleNode.children) {
            if (child.isElement() && child.hasClass("type")
                    && "variable".equalsIgnoreCase(cleanNodeText(child, null))) return true;
        }
        return false;
    }

    private static Node firstPrimaryMetadata(Node answerBlock) {
        List<Node> candidates = new ArrayList<Node>();
        collectByClass(answerBlock, "question-meta", candidates);
        for (Node candidate : candidates) {
            Node variable = firstDirectByClass(candidate, "variable_name");
            if (variable == null) variable = firstByClass(candidate, "variable_name");
            if (variable != null && !cleanNodeText(variable, null).isEmpty()) return candidate;
        }
        return null;
    }

    private static String parseScope(Node typeNode) {
        if (typeNode == null) return "";
        String full = cleanNodeText(typeNode, null);
        Matcher matcher = SCOPE.matcher(full);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "";
    }

    private static String baseTypeFallback(String full) {
        if (full == null) return "";
        String value = full.replaceFirst("(?i)\\s+scope\\s*:.*$", "");
        value = value.replaceFirst("(?i)\\s+source of categories\\s*:.*$", "");
        return value.trim();
    }

    private static String normalizeType(String rawType) {
        String value = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT).trim();
        if (value.startsWith("multi-select")) return "multi";
        if (value.startsWith("single-select")) return "single";
        if (value.startsWith("numeric") || value.equals("integer") || value.equals("decimal")
                || value.equals("double") || value.equals("long")) return "numeric";
        if (value.startsWith("gps")) return "gps";
        if (value.startsWith("date") || value.contains("date:")) return "date";
        if (value.startsWith("picture") || value.equals("image")) return "picture";
        if (value.startsWith("audio")) return "audio";
        if (value.equals("text") || value.equals("string")) return "text";
        if (value.equals("boolean") || value.equals("bool")) return "single";
        return "other";
    }

    private static void collectOptions(Node answerBlock, List<QuestionOption> output) {
        OptionState state = new OptionState();
        collectOptionsDepthFirst(answerBlock, state, output);
    }

    private static void collectOptionsDepthFirst(Node node, OptionState state, List<QuestionOption> output) {
        if (!node.isElement()) return;
        if (node.hasClass("type") && "special values".equalsIgnoreCase(cleanNodeText(node, null))) {
            state.special = true;
        }
        if (node.hasClass("option")) {
            Node codeNode = firstByClass(node, "option-value");
            Node labelNode = firstByTag(node, "label");
            if (labelNode == null) labelNode = firstByClass(node, "option-text");
            String code = cleanNodeText(codeNode, null);
            String label = cleanNodeText(labelNode, null);
            if (!code.isEmpty()) {
                // Survey Solutions special values are represented either by an
                // explicit "Special values" block or a negative code. Do not
                // infer this from an English label (for example, "Refusal"),
                // which would classify localized copies differently and can
                // hide legitimate positive response categories.
                boolean special = state.special || code.startsWith("-");
                output.add(new QuestionOption(code, label.isEmpty() ? code : label, special));
            }
            return;
        }
        for (Node child : node.children) {
            if (child.isElement()) collectOptionsDepthFirst(child, state, output);
        }
    }

    private static String cleanDirectText(Node node) {
        if (node == null) return "";
        StringBuilder value = new StringBuilder();
        for (Node child : node.children) {
            if (!child.isElement()) appendSeparated(value, child.text);
        }
        return Util.cleanText(value.toString());
    }

    private static String cleanNodeText(Node node, String excludedDirectMarker) {
        if (node == null) return "";
        StringBuilder value = new StringBuilder();
        appendText(node, value, node, excludedDirectMarker);
        return Util.cleanText(value.toString());
    }

    private static void appendText(Node node, StringBuilder output, Node root, String excludedDirectMarker) {
        if (!node.isElement()) {
            appendSeparated(output, node.text);
            return;
        }
        if (node != root && node.parent == root && "span".equals(node.tag)) {
            String marker = Util.cleanText(rawText(node));
            if ((excludedDirectMarker != null && excludedDirectMarker.equalsIgnoreCase(marker))
                    || (excludedDirectMarker != null && MARKER.matcher(marker).matches())) return;
        }
        boolean boundary = isTextBoundary(node.tag);
        if (boundary) appendSeparated(output, " ");
        for (Node child : node.children) appendText(child, output, root, excludedDirectMarker);
        if (boundary) appendSeparated(output, " ");
    }

    private static boolean isTextBoundary(String tag) {
        return "br".equals(tag) || "p".equals(tag) || "div".equals(tag) || "li".equals(tag)
                || "dt".equals(tag) || "dd".equals(tag) || "pre".equals(tag)
                || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag);
    }

    private static String rawText(Node node) {
        StringBuilder value = new StringBuilder();
        appendRawText(node, value);
        return value.toString();
    }

    private static void appendRawText(Node node, StringBuilder output) {
        if (!node.isElement()) {
            output.append(node.text);
            return;
        }
        for (Node child : node.children) appendRawText(child, output);
    }

    private static void appendSeparated(StringBuilder output, String value) {
        if (value == null || value.isEmpty()) return;
        if (output.length() > 0) {
            char last = output.charAt(output.length() - 1);
            char first = value.charAt(0);
            if (!Character.isWhitespace(last) && !Character.isWhitespace(first)) output.append(' ');
        }
        output.append(value);
    }

    private static void collectByTagAndClass(Node node, String tag, String className, List<Node> output) {
        if (node.isElement() && tag.equals(node.tag) && node.hasClass(className)) output.add(node);
        for (Node child : node.children) collectByTagAndClass(child, tag, className, output);
    }

    private static void collectByClass(Node node, String className, List<Node> output) {
        for (Node child : node.children) {
            if (child.isElement() && child.hasClass(className)) output.add(child);
            collectByClass(child, className, output);
        }
    }

    private static Node firstByClass(Node node, String className) {
        for (Node child : node.children) {
            if (child.isElement() && child.hasClass(className)) return child;
            Node nested = firstByClass(child, className);
            if (nested != null) return nested;
        }
        return null;
    }

    private static Node firstDirectByClass(Node node, String className) {
        if (node == null) return null;
        for (Node child : node.children) {
            if (child.isElement() && child.hasClass(className)) return child;
        }
        return null;
    }

    private static Node firstByTag(Node node, String tag) {
        if (node == null) return null;
        for (Node child : node.children) {
            if (child.isElement() && tag.equals(child.tag)) return child;
            Node nested = firstByTag(child, tag);
            if (nested != null) return nested;
        }
        return null;
    }

    private static final class SectionState {
        final QuestionnaireSpec spec;
        final QuestionSection section;
        final LinkedHashMap<String, Question> seenVariables;
        final List<String> context = new ArrayList<String>();
        String pendingFamilyPrompt = "";

        SectionState(QuestionnaireSpec spec, QuestionSection section,
                     LinkedHashMap<String, Question> seenVariables) {
            this.spec = spec;
            this.section = section;
            this.seenVariables = seenVariables;
        }

        String currentSubsection() {
            return context.isEmpty() ? "" : context.get(context.size() - 1);
        }
    }

    private static final class OptionState {
        boolean special;
    }

    private static final class Node {
        final String tag;
        final String text;
        final Map<String, String> attributes;
        final List<Node> children = new ArrayList<Node>();
        Node parent;

        Node(String tag, String text, Map<String, String> attributes) {
            this.tag = tag;
            this.text = text;
            this.attributes = attributes;
        }

        static Node element(String tag, Map<String, String> attributes) {
            return new Node(tag, null, attributes);
        }

        static Node text(String text) {
            return new Node(null, text, null);
        }

        boolean isElement() {
            return tag != null;
        }

        boolean hasClass(String className) {
            if (attributes == null) return false;
            String value = attributes.get("class");
            if (value == null || value.isEmpty()) return false;
            int start = 0;
            while (start < value.length()) {
                while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
                int end = start;
                while (end < value.length() && !Character.isWhitespace(value.charAt(end))) end++;
                if (end > start && value.regionMatches(true, start, className, 0, className.length())
                        && end - start == className.length()) return true;
                start = end + 1;
            }
            return false;
        }

        void add(Node child) {
            child.parent = this;
            children.add(child);
        }
    }

    /** Small forgiving tokenizer/tree builder for the subset of HTML we need. */
    private static final class MiniHtml {
        private MiniHtml() {}

        static Node parse(String html) {
            Node document = Node.element("#document", new LinkedHashMap<String, String>());
            Deque<Node> stack = new ArrayDeque<Node>();
            stack.push(document);
            int length = html.length();
            int index = 0;
            while (index < length) {
                int open = html.indexOf('<', index);
                if (open < 0) {
                    addText(stack.peek(), html.substring(index));
                    break;
                }
                if (open > index) addText(stack.peek(), html.substring(index, open));

                if (startsWith(html, open, "<!--", false)) {
                    int close = html.indexOf("-->", open + 4);
                    index = close < 0 ? length : close + 3;
                    continue;
                }
                if (startsWith(html, open, "<![CDATA[", false)) {
                    int close = html.indexOf("]]>", open + 9);
                    if (close < 0) {
                        addText(stack.peek(), html.substring(open + 9));
                        break;
                    }
                    addText(stack.peek(), html.substring(open + 9, close));
                    index = close + 3;
                    continue;
                }

                int end = findTagEnd(html, open + 1);
                if (end < 0) {
                    addText(stack.peek(), html.substring(open));
                    break;
                }
                String inside = html.substring(open + 1, end);
                String trimmed = inside.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '!' || trimmed.charAt(0) == '?') {
                    index = end + 1;
                    continue;
                }

                boolean closing = trimmed.charAt(0) == '/';
                int nameStart = closing ? 1 : 0;
                while (nameStart < trimmed.length() && Character.isWhitespace(trimmed.charAt(nameStart))) nameStart++;
                int nameEnd = nameStart;
                while (nameEnd < trimmed.length() && isTagNameChar(trimmed.charAt(nameEnd))) nameEnd++;
                if (nameEnd == nameStart) {
                    addText(stack.peek(), "<");
                    index = open + 1;
                    continue;
                }
                String tag = trimmed.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);

                if (closing) {
                    closeTag(stack, tag);
                    index = end + 1;
                    continue;
                }

                boolean selfClosing = isSelfClosing(trimmed) || VOID_TAGS.contains(tag);
                if ("style".equals(tag) || "script".equals(tag)) {
                    int rawClose = indexOfClosingTag(html, tag, end + 1);
                    if (rawClose < 0) break;
                    int rawEnd = findTagEnd(html, rawClose + 2 + tag.length());
                    index = rawEnd < 0 ? length : rawEnd + 1;
                    continue;
                }

                Map<String, String> attributes = parseAttributes(trimmed, nameEnd);
                Node element = Node.element(tag, attributes);
                stack.peek().add(element);
                if (!selfClosing) stack.push(element);
                index = end + 1;
            }
            return document;
        }

        private static void addText(Node parent, String value) {
            if (value == null || value.isEmpty()) return;
            parent.add(Node.text(value));
        }

        private static int findTagEnd(String html, int start) {
            char quote = 0;
            for (int i = start; i < html.length(); i++) {
                char c = html.charAt(i);
                if (quote != 0) {
                    if (c == quote) quote = 0;
                } else if (c == '\'' || c == '"') quote = c;
                else if (c == '>') return i;
            }
            return -1;
        }

        private static boolean startsWith(String value, int offset, String target, boolean ignoreCase) {
            return offset >= 0 && offset + target.length() <= value.length()
                    && value.regionMatches(ignoreCase, offset, target, 0, target.length());
        }

        private static boolean isTagNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == ':' || c == '-' || c == '_';
        }

        private static boolean isSelfClosing(String tagText) {
            int i = tagText.length() - 1;
            while (i >= 0 && Character.isWhitespace(tagText.charAt(i))) i--;
            return i >= 0 && tagText.charAt(i) == '/';
        }

        private static void closeTag(Deque<Node> stack, String tag) {
            boolean found = false;
            for (Node node : stack) {
                if (tag.equals(node.tag)) {
                    found = true;
                    break;
                }
            }
            if (!found) return;
            while (stack.size() > 1) {
                Node node = stack.pop();
                if (tag.equals(node.tag)) return;
            }
        }

        private static Map<String, String> parseAttributes(String tagText, int start) {
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            int length = tagText.length();
            int index = start;
            while (index < length) {
                while (index < length && (Character.isWhitespace(tagText.charAt(index))
                        || tagText.charAt(index) == '/')) index++;
                if (index >= length) break;
                int nameStart = index;
                while (index < length && isAttributeNameChar(tagText.charAt(index))) index++;
                if (index == nameStart) {
                    index++;
                    continue;
                }
                String name = tagText.substring(nameStart, index).toLowerCase(Locale.ROOT);
                while (index < length && Character.isWhitespace(tagText.charAt(index))) index++;
                String value = "";
                if (index < length && tagText.charAt(index) == '=') {
                    index++;
                    while (index < length && Character.isWhitespace(tagText.charAt(index))) index++;
                    if (index < length && (tagText.charAt(index) == '\'' || tagText.charAt(index) == '"')) {
                        char quote = tagText.charAt(index++);
                        int valueStart = index;
                        while (index < length && tagText.charAt(index) != quote) index++;
                        value = tagText.substring(valueStart, Math.min(index, length));
                        if (index < length) index++;
                    } else {
                        int valueStart = index;
                        while (index < length && !Character.isWhitespace(tagText.charAt(index))
                                && tagText.charAt(index) != '/') index++;
                        value = tagText.substring(valueStart, index);
                    }
                }
                if (KEPT_ATTRIBUTES.contains(name)) attributes.put(name, Util.unescape(value));
            }
            return attributes;
        }

        private static boolean isAttributeNameChar(char c) {
            return !Character.isWhitespace(c) && c != '=' && c != '>' && c != '/' && c != '\'' && c != '"';
        }

        private static int indexOfClosingTag(String html, String tag, int start) {
            String target = "</" + tag;
            int limit = html.length() - target.length();
            for (int i = Math.max(0, start); i <= limit; i++) {
                if (html.regionMatches(true, i, target, 0, target.length())) return i;
            }
            return -1;
        }
    }
}
