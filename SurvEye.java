import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves the dashboard interface language/direction and supplies UI strings. */
final class DashboardI18n {
    static final class Resolution {
        final String uiLanguage;
        final String languageCode;
        final String direction;

        Resolution(String uiLanguage, String languageCode, String direction) {
            this.uiLanguage = uiLanguage;
            this.languageCode = languageCode;
            this.direction = direction;
        }
    }

    private static final Map<String, Map<String, String>> TRANSLATIONS = translations();

    private DashboardI18n() {}

    static Resolution resolve(QuestionnaireSpec spec, DashboardConfig config) {
        String requestedLanguage = normalizeLanguage(config.uiLanguage);
        String requestedDirection = normalize(config.direction, "auto");
        String sample = questionnaireText(spec);
        String declared = primaryLanguage(spec == null ? null : spec.language);

        String uiLanguage = requestedLanguage;
        if ("auto".equals(uiLanguage)) {
            if ("ur".equals(declared)) uiLanguage = "urdu";
            else if ("ar".equals(declared)) uiLanguage = "arabic";
            else if (isArabicPredominant(sample)) uiLanguage = containsUrduSignal(sample) ? "urdu" : "arabic";
            else uiLanguage = "english";
        }

        String direction = requestedDirection;
        if ("auto".equals(direction)) {
            direction = "arabic".equals(uiLanguage) || "urdu".equals(uiLanguage) ? "rtl" : "ltr";
        }
        String languageCode = "arabic".equals(uiLanguage) ? "ar" : "urdu".equals(uiLanguage) ? "ur" : "en";
        return new Resolution(uiLanguage, languageCode, direction);
    }

    static String text(String language, String key) {
        Map<String, String> selected = TRANSLATIONS.get(language);
        if (selected != null && selected.containsKey(key)) return selected.get(key);
        String fallback = TRANSLATIONS.get("english").get(key);
        return fallback == null ? key : fallback;
    }

    static Map<String, String> strings(String language) {
        Map<String, String> output = new LinkedHashMap<String, String>(TRANSLATIONS.get("english"));
        Map<String, String> selected = TRANSLATIONS.get(language);
        if (selected != null) output.putAll(selected);
        return Collections.unmodifiableMap(output);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLanguage(String value) {
        String normalized = normalize(value, "auto");
        if ("en".equals(normalized)) return "english";
        if ("ar".equals(normalized)) return "arabic";
        if ("ur".equals(normalized)) return "urdu";
        return normalized;
    }

    private static String primaryLanguage(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int dash = normalized.indexOf('-');
        return dash < 0 ? normalized : normalized.substring(0, dash);
    }

    static boolean containsArabicScript(String value) {
        return scriptCounts(value)[0] > 0;
    }

    private static boolean isArabicPredominant(String value) {
        int[] counts = scriptCounts(value);
        int arabic = counts[0], allLetters = counts[1];
        // Ignore incidental foreign examples or response options in an
        // otherwise non-Arabic instrument.  Eight letters also makes very
        // short but genuinely Arabic questionnaires detectable.
        return arabic >= 8 && arabic * 2 >= allLetters;
    }

    private static int[] scriptCounts(String value) {
        if (value == null) return new int[]{0, 0};
        int arabic = 0, allLetters = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) continue;
            allLetters++;
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
            if (block == Character.UnicodeBlock.ARABIC
                    || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                    || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B) {
                arabic++;
            }
        }
        return new int[]{arabic, allLetters};
    }

    private static boolean containsUrduSignal(String value) {
        if (value == null) return false;
        // Letters whose repeated presence is a strong Urdu signal.  A language
        // declaration remains authoritative because Persian/Pashto share some.
        String urdu = "ٹڈڑںھہے";
        int matches = 0;
        for (int i = 0; i < value.length(); i++) {
            if (urdu.indexOf(value.charAt(i)) >= 0 && ++matches >= 2) return true;
        }
        return false;
    }

    private static String questionnaireText(QuestionnaireSpec spec) {
        if (spec == null) return "";
        StringBuilder out = new StringBuilder();
        append(out, spec.title);
        for (QuestionSection section : spec.sections) append(out, section.title);
        for (Question question : spec.questions) {
            append(out, question.label);
            append(out, question.subsection);
            for (QuestionOption option : question.options) append(out, option.label);
        }
        return out.toString();
    }

    private static void append(StringBuilder output, String value) {
        if (value != null && !value.isEmpty()) output.append(' ').append(value);
    }

    private static Map<String, Map<String, String>> translations() {
        Map<String, Map<String, String>> all = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> en = new LinkedHashMap<String, String>();
        put(en,
                "interactiveDashboard", "Interactive data dashboard",
                "logoAlt", "Organization logo",
                "selfContainedPrivacy", "Self-contained file · no network connection required",
                "mapPrivacy", "Dashboard data embedded · basemap tiles require internet",
                "questionnaire", "Questionnaire",
                "simulatedBadge", "Simulated data · preview",
                "weightedBadge", "Weighted dashboard",
                "fieldworkBadge", "Fieldwork dashboard",
                "previewEyebrow", "Preview generated from questionnaire structure",
                "resultsEyebrow", "Interactive survey results",
                "defaultSubtitle", "Interactive questionnaire dashboard",
                "defaultDisclaimer", "Internal working dashboard. Review disclosure risk before sharing.",
                "sharingNote", "Sharing note",
                "dashboardSections", "Dashboard sections",
                "overview", "Overview",
                "section", "Section",
                "dashboardControls", "Dashboard controls",
                "searchPlaceholder", "Search indicators or variable names",
                "searchIndicators", "Search indicators",
                "resetAll", "Reset all",
                "interviewsShown", "interviews shown",
                "dashboardSummary", "Dashboard summary",
                "interviewsMatching", "Interviews matching filters",
                "indicatorsVisualized", "Indicators visualized",
                "sectionsShown", "Sections shown",
                "questionsInstrument", "Questions in instrument",
                "highlights", "Highlights",
                "calculating", "Calculating…",
                "keyMessages", "Key messages",
                "keyMessage", "Key message",
                "additionalIndicators", "Additional indicators",
                "otherIndicators", "Other indicators",
                "qualityNote", "quality note",
                "qualityNotes", "quality notes",
                "admin2Boundaries", "Admin-2 boundaries",
                "countryOutline", "Country outline",
                "points", "points",
                "cluster", "clusters",
                "heat", "heat map",
                "display", "display",
                "mapDisplay", "{type} display",
                "mappedInterviewsShown", "mapped interviews shown",
                "showMap", "Show map",
                "hideMap", "Hide map",
                "interactiveMapOf", "Interactive map of",
                "preparingMap", "Preparing interactive map…",
                "mapGroups", "Map groups",
                "mapLayers", "Map layers",
                "zoomIn", "Zoom in",
                "zoomOut", "Zoom out",
                "missingInvalid", "missing/invalid",
                "outsideBoundary", "outside boundary",
                "boundary", "Boundary",
                "coincidentPoints", "coincident points are separated slightly on screen",
                "spatialDistribution", "Spatial distribution",
                "noSearchResults", "No indicators match this search.",
                "searchHint", "Try a variable name or a shorter phrase.",
                "indicator", "indicator",
                "indicators", "indicators",
                "variableName", "Variable name",
                "numericDisplay", "Numeric display",
                "distribution", "Distribution",
                "stats", "Stats",
                "noDataSelection", "No data for this selection",
                "footerWeighted", "Charts show weighted estimates with raw valid n for interviews matching the active filters.",
                "footerUnweighted", "Charts show unweighted frequencies and shares for interviews matching the active filters.",
                "footerSpecial", "Special-value categories are retained and visually muted; declared negative special codes and configured missing codes are excluded from numeric results.",
                "footerCI", "Ordinary categorical bars show pointwise Wilson {level}% confidence-interval whiskers. Binary, completion, and donut cards do not display confidence intervals.",
                "footerWeightedCI", "Weighted intervals use an effective-sample-size approximation and are not adjusted for clustering or stratification.",
                "note", "Note",
                "source", "Source",
                "generated", "Generated",
                "offlineHtml", "offline HTML",
                "onlineMapHtml", "interactive HTML; map tiles load online",
                "simulatedWarning", "SIMULATED DATA — PREVIEW ONLY",
                "chart", "Chart",
                "notAvailable", "n/a",
                "approx", "approx.",
                "confidenceInterval", "CI",
                "rawN", "raw n",
                "validN", "valid n",
                "respondentsN", "respondents n",
                "validResponses", "valid responses",
                "noPositiveWeight", "No positive weight for this selection",
                "noValidResponses", "No valid responses for this selection",
                "other", "Other",
                "anyOtherResponse", "Any other response",
                "categories", "categories",
                "options", "options",
                "summaryStatistics", "Summary statistics",
                "noNumericValues", "No numeric values for this selection",
                "validRawN", "Valid raw n",
                "missingExcluded", "Missing/excluded",
                "mean", "Mean",
                "stdDev", "Std. dev.",
                "minimum", "Minimum",
                "maximum", "Maximum",
                "median", "Median",
                "tukeyOutliers", "Tukey outliers",
                "weightedOutlierMass", "Weighted outlier mass",
                "weightedOutlierShare", "Weighted outlier share",
                "tukeyFences", "Tukey fences",
                "to", "to",
                "outliersNeedFour", "Outliers need at least four valid values",
                "weightedSum", "weighted sum",
                "descriptiveWeighted", "descriptive weighted statistics",
                "noValidNumeric", "No valid numeric values for this selection",
                "oneValidValue", "One valid value; open Stats for its summary",
                "value", "value",
                "weightedMedian", "weighted median",
                "weightedMean", "weighted mean",
                "outlier", "Tukey outlier",
                "outliers", "Tukey outliers",
                "outliersNotClassified", "outliers not classified",
                "valueWhisker", "value (Tukey whisker range)",
                "weightedFrequency", "weighted frequency",
                "observations", "observations",
                "around", "Around",
                "weighted", "weighted",
                "noRecognizableDates", "No recognizable dates for this selection",
                "validDatesN", "valid dates n",
                "noObservations", "No observations for this selection",
                "answered", "Answered",
                "missing", "Missing",
                "answeredN", "answered n",
                "missingN", "missing n",
                "chartFailed", "This chart could not be rendered",
                "of", "of",
                "interviewLocation", "Interview location",
                "coordinates", "Coordinates",
                "group", "Group",
                "coincidentPopup", "Coincident point separated slightly for visibility.",
                "interview", "interview",
                "interviews", "interviews",
                "surveyBoundary", "Survey boundary",
                "googleHybrid", "Google Hybrid",
                "googleSatellite", "Google Satellite",
                "googleRoads", "Google Roads",
                "openStreetMap", "OpenStreetMap");
        all.put("english", Collections.unmodifiableMap(en));

        Map<String, String> ar = new LinkedHashMap<String, String>();
        put(ar,
                "interactiveDashboard", "لوحة بيانات تفاعلية",
                "logoAlt", "شعار المؤسسة",
                "selfContainedPrivacy", "ملف مستقل · لا يلزم اتصال بالشبكة",
                "mapPrivacy", "بيانات اللوحة مضمنة · تتطلب خرائط الأساس اتصالاً بالإنترنت",
                "questionnaire", "الاستبيان",
                "simulatedBadge", "بيانات محاكاة · معاينة",
                "weightedBadge", "لوحة بيانات مرجّحة",
                "fieldworkBadge", "لوحة العمل الميداني",
                "previewEyebrow", "معاينة مولّدة من بنية الاستبيان",
                "resultsEyebrow", "نتائج المسح التفاعلية",
                "defaultSubtitle", "لوحة استبيان تفاعلية",
                "defaultDisclaimer", "لوحة عمل داخلية. راجع مخاطر الإفصاح قبل المشاركة.",
                "sharingNote", "ملاحظة المشاركة",
                "dashboardSections", "أقسام لوحة البيانات",
                "overview", "نظرة عامة",
                "section", "القسم",
                "dashboardControls", "عناصر تحكم لوحة البيانات",
                "searchPlaceholder", "ابحث في المؤشرات أو أسماء المتغيرات",
                "searchIndicators", "البحث في المؤشرات",
                "resetAll", "إعادة ضبط الكل",
                "interviewsShown", "مقابلات معروضة",
                "dashboardSummary", "ملخص لوحة البيانات",
                "interviewsMatching", "المقابلات المطابقة لعوامل التصفية",
                "indicatorsVisualized", "المؤشرات المعروضة",
                "sectionsShown", "الأقسام المعروضة",
                "questionsInstrument", "أسئلة أداة المسح",
                "highlights", "أبرز النتائج",
                "calculating", "جارٍ الحساب…",
                "keyMessages", "الرسائل الرئيسية",
                "keyMessage", "رسالة رئيسية",
                "additionalIndicators", "مؤشرات إضافية",
                "otherIndicators", "مؤشرات أخرى",
                "qualityNote", "ملاحظة جودة",
                "qualityNotes", "ملاحظات جودة",
                "admin2Boundaries", "حدود المستوى الإداري الثاني",
                "countryOutline", "حدود الدولة",
                "points", "نقاط",
                "cluster", "تجمعات",
                "heat", "خريطة حرارية",
                "display", "عرض",
                "mapDisplay", "عرض {type}",
                "mappedInterviewsShown", "مقابلات ظاهرة على الخريطة",
                "showMap", "إظهار الخريطة",
                "hideMap", "إخفاء الخريطة",
                "interactiveMapOf", "خريطة تفاعلية لـ",
                "preparingMap", "جارٍ إعداد الخريطة التفاعلية…",
                "mapGroups", "مجموعات الخريطة",
                "mapLayers", "طبقات الخريطة",
                "zoomIn", "تكبير",
                "zoomOut", "تصغير",
                "missingInvalid", "مفقودة/غير صالحة",
                "outsideBoundary", "خارج الحدود",
                "boundary", "الحدود",
                "coincidentPoints", "تُفصل النقاط المتطابقة قليلاً على الشاشة",
                "spatialDistribution", "التوزيع المكاني",
                "noSearchResults", "لا توجد مؤشرات تطابق هذا البحث.",
                "searchHint", "جرّب اسم متغير أو عبارة أقصر.",
                "indicator", "مؤشر",
                "indicators", "مؤشرات",
                "variableName", "اسم المتغير",
                "numericDisplay", "العرض الرقمي",
                "distribution", "التوزيع",
                "stats", "الإحصاءات",
                "noDataSelection", "لا توجد بيانات لهذا التحديد",
                "footerWeighted", "تعرض الرسوم تقديرات مرجّحة مع العدد الخام الصالح للمقابلات المطابقة لعوامل التصفية النشطة.",
                "footerUnweighted", "تعرض الرسوم التكرارات والنسب غير المرجّحة للمقابلات المطابقة لعوامل التصفية النشطة.",
                "footerSpecial", "تُحفظ فئات القيم الخاصة وتُعرض بلون خافت؛ وتُستبعد الرموز الخاصة السالبة المعلنة ورموز القيم المفقودة المحددة من النتائج الرقمية.",
                "footerCI", "تعرض الأشرطة الفئوية العادية علامات فاصل ثقة ويلسون النقطي بنسبة {level}٪. ولا تعرض البطاقات الثنائية أو بطاقات الاكتمال أو المخططات الحلقية فواصل ثقة.",
                "footerWeightedCI", "تستخدم الفواصل المرجّحة تقريب حجم العينة الفعال ولا تُعدّل للتجميع أو الطبقات.",
                "note", "ملاحظة",
                "source", "المصدر",
                "generated", "تم الإنشاء",
                "offlineHtml", "HTML دون اتصال",
                "onlineMapHtml", "HTML تفاعلي؛ تُحمّل مربعات الخريطة عبر الإنترنت",
                "simulatedWarning", "بيانات محاكاة — للمعاينة فقط",
                "chart", "رسم بياني",
                "notAvailable", "غير متاح",
                "approx", "تقريبًا",
                "confidenceInterval", "فاصل الثقة",
                "rawN", "العدد الخام",
                "validN", "العدد الصالح",
                "respondentsN", "عدد المستجيبين",
                "validResponses", "استجابات صالحة",
                "noPositiveWeight", "لا يوجد وزن موجب لهذا التحديد",
                "noValidResponses", "لا توجد استجابات صالحة لهذا التحديد",
                "other", "أخرى",
                "anyOtherResponse", "أي استجابة أخرى",
                "categories", "فئات",
                "options", "خيارات",
                "summaryStatistics", "الإحصاءات الموجزة",
                "noNumericValues", "لا توجد قيم رقمية لهذا التحديد",
                "validRawN", "العدد الخام الصالح",
                "missingExcluded", "مفقود/مستبعد",
                "mean", "المتوسط",
                "stdDev", "الانحراف المعياري",
                "minimum", "الحد الأدنى",
                "maximum", "الحد الأقصى",
                "median", "الوسيط",
                "tukeyOutliers", "قيم توكي المتطرفة",
                "weightedOutlierMass", "كتلة القيم المتطرفة المرجّحة",
                "weightedOutlierShare", "حصة القيم المتطرفة المرجّحة",
                "tukeyFences", "حدود توكي",
                "to", "إلى",
                "outliersNeedFour", "يتطلب تحديد القيم المتطرفة أربع قيم صالحة على الأقل",
                "weightedSum", "المجموع المرجّح",
                "descriptiveWeighted", "إحصاءات وصفية مرجّحة",
                "noValidNumeric", "لا توجد قيم رقمية صالحة لهذا التحديد",
                "oneValidValue", "قيمة صالحة واحدة؛ افتح الإحصاءات لعرض الملخص",
                "value", "القيمة",
                "weightedMedian", "الوسيط المرجّح",
                "weightedMean", "المتوسط المرجّح",
                "outlier", "قيمة توكي متطرفة",
                "outliers", "قيم توكي متطرفة",
                "outliersNotClassified", "لم تُصنّف القيم المتطرفة",
                "valueWhisker", "القيمة (نطاق شوارب توكي)",
                "weightedFrequency", "التكرار المرجّح",
                "observations", "المشاهدات",
                "around", "حول",
                "weighted", "مرجّح",
                "noRecognizableDates", "لا توجد تواريخ قابلة للتعرّف لهذا التحديد",
                "validDatesN", "عدد التواريخ الصالحة",
                "noObservations", "لا توجد مشاهدات لهذا التحديد",
                "answered", "تمت الإجابة",
                "missing", "مفقود",
                "answeredN", "عدد الحالات المُجاب عنها",
                "missingN", "عدد القيم المفقودة",
                "chartFailed", "تعذر عرض هذا الرسم البياني",
                "of", "من",
                "interviewLocation", "موقع المقابلة",
                "coordinates", "الإحداثيات",
                "group", "المجموعة",
                "coincidentPopup", "فُصلت النقطة المتطابقة قليلاً لتسهيل رؤيتها.",
                "interview", "مقابلة",
                "interviews", "مقابلات",
                "surveyBoundary", "حدود المسح",
                "googleHybrid", "خرائط Google الهجينة",
                "googleSatellite", "صور Google بالأقمار الصناعية",
                "googleRoads", "خرائط طرق Google",
                "openStreetMap", "OpenStreetMap");
        all.put("arabic", Collections.unmodifiableMap(ar));

        Map<String, String> ur = new LinkedHashMap<String, String>();
        put(ur,
                "interactiveDashboard", "انٹرایکٹو ڈیٹا ڈیش بورڈ",
                "logoAlt", "ادارے کا لوگو",
                "selfContainedPrivacy", "تمام مواد اسی فائل میں شامل ہیں · نیٹ ورک کنکشن درکار نہیں",
                "mapPrivacy", "ڈیش بورڈ ڈیٹا شامل ہے · بنیادی نقشوں کے لیے انٹرنیٹ درکار ہے",
                "questionnaire", "سوالنامہ",
                "simulatedBadge", "مصنوعی ڈیٹا · پیش منظر",
                "weightedBadge", "وزنی ڈیش بورڈ",
                "fieldworkBadge", "فیلڈ ورک ڈیش بورڈ",
                "previewEyebrow", "سوالنامے کی ساخت سے تیار کردہ پیش منظر",
                "resultsEyebrow", "انٹرایکٹو سروے نتائج",
                "defaultSubtitle", "انٹرایکٹو سوالنامہ ڈیش بورڈ",
                "defaultDisclaimer", "اندرونی ورکنگ ڈیش بورڈ۔ شیئر کرنے سے پہلے افشا کے خطرے کا جائزہ لیں۔",
                "sharingNote", "شیئرنگ نوٹ",
                "dashboardSections", "ڈیش بورڈ کے حصے",
                "overview", "جائزہ",
                "section", "حصہ",
                "dashboardControls", "ڈیش بورڈ کنٹرولز",
                "searchPlaceholder", "اشاریے یا متغیر کے نام تلاش کریں",
                "searchIndicators", "اشاریے تلاش کریں",
                "resetAll", "سب دوبارہ ترتیب دیں",
                "interviewsShown", "انٹرویوز دکھائے گئے",
                "dashboardSummary", "ڈیش بورڈ خلاصہ",
                "interviewsMatching", "فلٹرز سے مطابقت رکھنے والے انٹرویوز",
                "indicatorsVisualized", "دکھائے گئے اشاریے",
                "sectionsShown", "دکھائے گئے حصے",
                "questionsInstrument", "سروے آلے کے سوالات",
                "highlights", "اہم نکات",
                "calculating", "حساب جاری ہے…",
                "keyMessages", "اہم پیغامات",
                "keyMessage", "اہم پیغام",
                "additionalIndicators", "اضافی اشاریے",
                "otherIndicators", "دیگر اشاریے",
                "qualityNote", "معیار کا نوٹ",
                "qualityNotes", "معیار کے نوٹس",
                "admin2Boundaries", "دوسری انتظامی سطح کی حدود",
                "countryOutline", "ملک کی حد",
                "points", "نقاط",
                "cluster", "کلسٹرز",
                "heat", "حرارتی نقشہ",
                "display", "نمائش",
                "mapDisplay", "اندازِ نمائش: {type}",
                "mappedInterviewsShown", "نقشے پر دکھائے گئے انٹرویوز",
                "showMap", "نقشہ دکھائیں",
                "hideMap", "نقشہ چھپائیں",
                "interactiveMapOf", "انٹرایکٹو نقشہ:",
                "preparingMap", "انٹرایکٹو نقشہ تیار ہو رہا ہے…",
                "mapGroups", "نقشے کے گروپس",
                "mapLayers", "نقشے کی تہیں",
                "zoomIn", "بڑا کریں",
                "zoomOut", "چھوٹا کریں",
                "missingInvalid", "غائب/غلط",
                "outsideBoundary", "حد سے باہر",
                "boundary", "حد",
                "coincidentPoints", "ایک جگہ موجود نقاط کو اسکرین پر قدرے الگ دکھایا گیا ہے",
                "spatialDistribution", "مکانی تقسیم",
                "noSearchResults", "اس تلاش سے کوئی اشاریہ نہیں ملا۔",
                "searchHint", "متغیر کا نام یا مختصر عبارت آزمائیں۔",
                "indicator", "اشاریہ",
                "indicators", "اشاریے",
                "variableName", "متغیر کا نام",
                "numericDisplay", "عددی نمائش",
                "distribution", "تقسیم",
                "stats", "اعداد و شمار",
                "noDataSelection", "اس انتخاب کے لیے ڈیٹا موجود نہیں",
                "footerWeighted", "چارٹس فعال فلٹرز سے مطابقت رکھنے والے انٹرویوز کے لیے خام درست تعداد کے ساتھ وزنی تخمینے دکھاتے ہیں۔",
                "footerUnweighted", "چارٹس فعال فلٹرز سے مطابقت رکھنے والے انٹرویوز کے غیر وزنی تعدد اور حصے دکھاتے ہیں۔",
                "footerSpecial", "خصوصی قدروں کے زمرے برقرار اور مدھم دکھائے جاتے ہیں؛ منفی خصوصی کوڈ اور واضح طور پر غائب قرار دیے گئے کوڈ عددی نتائج سے خارج کیے جاتے ہیں۔",
                "footerCI", "عام زمرہ جاتی بارز {level}٪ پوائنٹ وائز ولسن اعتماد کے وقفے کی وِسکرز دکھاتی ہیں۔ ثنائی، تکمیل اور ڈونٹ کارڈز اعتماد کے وقفے نہیں دکھاتے۔",
                "footerWeightedCI", "وزنی وقفے مؤثر نمونے کے حجم کا تخمینہ استعمال کرتے ہیں اور کلسٹرنگ یا طبقات کے لیے ایڈجسٹ نہیں ہوتے۔",
                "note", "نوٹ",
                "source", "ماخذ",
                "generated", "تیار کیا گیا",
                "offlineHtml", "آف لائن HTML",
                "onlineMapHtml", "انٹرایکٹو HTML؛ نقشے کی ٹائلیں آن لائن لوڈ ہوتی ہیں",
                "simulatedWarning", "مصنوعی ڈیٹا — صرف پیش منظر",
                "chart", "چارٹ",
                "notAvailable", "دستیاب نہیں",
                "approx", "تقریباً",
                "confidenceInterval", "اعتماد کا وقفہ",
                "rawN", "خام تعداد",
                "validN", "درست تعداد",
                "respondentsN", "جواب دہندگان کی تعداد",
                "validResponses", "درست جوابات",
                "noPositiveWeight", "اس انتخاب کے لیے مثبت وزن موجود نہیں",
                "noValidResponses", "اس انتخاب کے لیے درست جوابات موجود نہیں",
                "other", "دیگر",
                "anyOtherResponse", "کوئی دیگر جواب",
                "categories", "زمرے",
                "options", "اختیارات",
                "summaryStatistics", "خلاصہ اعداد و شمار",
                "noNumericValues", "اس انتخاب کے لیے عددی قدریں موجود نہیں",
                "validRawN", "درست خام تعداد",
                "missingExcluded", "غائب/خارج",
                "mean", "اوسط",
                "stdDev", "معیاری انحراف",
                "minimum", "کم از کم",
                "maximum", "زیادہ سے زیادہ",
                "median", "میڈین",
                "tukeyOutliers", "ٹوکی بیرونی قدریں",
                "weightedOutlierMass", "بیرونی قدروں کا وزنی مجموعہ",
                "weightedOutlierShare", "بیرونی قدروں کا وزنی حصہ",
                "tukeyFences", "ٹوکی حدود",
                "to", "تا",
                "outliersNeedFour", "بیرونی قدروں کے لیے کم از کم چار درست قدریں درکار ہیں",
                "weightedSum", "وزنی مجموعہ",
                "descriptiveWeighted", "وضاحتی وزنی اعداد و شمار",
                "noValidNumeric", "اس انتخاب کے لیے درست عددی قدریں موجود نہیں",
                "oneValidValue", "ایک درست قدر؛ خلاصے کے لیے اعداد و شمار کھولیں",
                "value", "قدر",
                "weightedMedian", "وزنی میڈین",
                "weightedMean", "وزنی اوسط",
                "outlier", "ٹوکی بیرونی قدر",
                "outliers", "ٹوکی بیرونی قدریں",
                "outliersNotClassified", "بیرونی قدریں درجہ بند نہیں ہوئیں",
                "valueWhisker", "قدر (ٹوکی وسکر رینج)",
                "weightedFrequency", "وزنی تعدد",
                "observations", "مشاہدات",
                "around", "تقریباً",
                "weighted", "وزنی",
                "noRecognizableDates", "اس انتخاب کے لیے قابل شناخت تاریخیں موجود نہیں",
                "validDatesN", "درست تاریخوں کی تعداد",
                "noObservations", "اس انتخاب کے لیے مشاہدات موجود نہیں",
                "answered", "جواب دیا گیا",
                "missing", "غائب",
                "answeredN", "جواب شدہ تعداد",
                "missingN", "غائب تعداد",
                "chartFailed", "یہ چارٹ دکھایا نہیں جا سکا",
                "of", "میں سے",
                "interviewLocation", "انٹرویو کا مقام",
                "coordinates", "مختصات",
                "group", "گروپ",
                "coincidentPopup", "واضح دکھانے کے لیے ایک جگہ موجود نقطہ قدرے الگ کیا گیا ہے۔",
                "interview", "انٹرویو",
                "interviews", "انٹرویوز",
                "surveyBoundary", "سروے کی حد",
                "googleHybrid", "Google ہائبرڈ",
                "googleSatellite", "Google سیٹلائٹ",
                "googleRoads", "Google سڑکیں",
                "openStreetMap", "OpenStreetMap");
        all.put("urdu", Collections.unmodifiableMap(ur));
        return Collections.unmodifiableMap(all);
    }

    private static void put(Map<String, String> target, String... values) {
        if (values.length % 2 != 0) throw new IllegalArgumentException("Translation entries must be key/value pairs.");
        for (int i = 0; i < values.length; i += 2) target.put(values[i], values[i + 1]);
    }
}
