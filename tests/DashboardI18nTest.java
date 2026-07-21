/** Focused tests for automatic language and direction resolution. */
public final class DashboardI18nTest {
    private DashboardI18nTest() {}

    public static void main(String[] args) {
        QuestionnaireSpec mixed = questionnaire("English survey", "Customer comments مرحبا", "Is the business open?");
        DashboardI18n.Resolution result = DashboardI18n.resolve(mixed, new DashboardConfig());
        check("english".equals(result.uiLanguage) && "ltr".equals(result.direction),
                "Incidental Arabic text must not flip an English questionnaire to RTL.");

        QuestionnaireSpec arabic = questionnaire("مسح المنشآت", "معلومات المنشأة", "هل المنشأة مفتوحة اليوم؟");
        arabic.language = "und";
        result = DashboardI18n.resolve(arabic, new DashboardConfig());
        check("arabic".equals(result.uiLanguage) && "ar".equals(result.languageCode) && "rtl".equals(result.direction),
                "Arabic-script predominance was not detected.");

        QuestionnaireSpec urdu = questionnaire("کاروباری سروے", "کاروبار کی معلومات", "کیا ادارہ آج کھلا ہے؟");
        urdu.language = "und";
        result = DashboardI18n.resolve(urdu, new DashboardConfig());
        check("urdu".equals(result.uiLanguage) && "ur".equals(result.languageCode) && "rtl".equals(result.direction),
                "Urdu-script signals were not detected.");

        DashboardConfig explicit = new DashboardConfig();
        explicit.uiLanguage = "en";
        explicit.direction = "rtl";
        result = DashboardI18n.resolve(arabic, explicit);
        check("english".equals(result.uiLanguage) && "en".equals(result.languageCode) && "rtl".equals(result.direction),
                "Explicit UI-language alias/direction did not override auto detection.");

        explicit.direction = "auto";
        result = DashboardI18n.resolve(arabic, explicit);
        check("english".equals(result.uiLanguage) && "ltr".equals(result.direction),
                "Auto direction must follow an explicitly selected English UI language.");

        explicit.uiLanguage = "ar";
        explicit.direction = "ltr";
        result = DashboardI18n.resolve(mixed, explicit);
        check("arabic".equals(result.uiLanguage) && "ltr".equals(result.direction),
                "Arabic alias or explicit LTR override was not honored.");

        check("مؤشرات إضافية".equals(DashboardI18n.text("arabic", "additionalIndicators"))
                        && "مؤشرات أخرى".equals(DashboardI18n.text("arabic", "otherIndicators"))
                        && "رسالة رئيسية".equals(DashboardI18n.text("arabic", "keyMessage")),
                "Arabic default section/message headings are not localized.");
        check("اضافی اشاریے".equals(DashboardI18n.text("urdu", "additionalIndicators"))
                        && "دیگر اشاریے".equals(DashboardI18n.text("urdu", "otherIndicators"))
                        && "اہم پیغام".equals(DashboardI18n.text("urdu", "keyMessage")),
                "Urdu default section/message headings are not localized.");
        check("Show filters".equals(DashboardI18n.text("english", "showFilters"))
                        && "Hide filters".equals(DashboardI18n.text("english", "hideFilters"))
                        && "active filter".equals(DashboardI18n.text("english", "activeFilter"))
                        && "active filters".equals(DashboardI18n.text("english", "activeFilters")),
                "English filter-disclosure strings are incomplete.");
        check("إظهار عوامل التصفية".equals(DashboardI18n.text("arabic", "showFilters"))
                        && "إخفاء عوامل التصفية".equals(DashboardI18n.text("arabic", "hideFilters"))
                        && "عوامل تصفية نشطة".equals(DashboardI18n.text("arabic", "activeFilters")),
                "Arabic filter-disclosure strings are incomplete.");
        check("فلٹرز دکھائیں".equals(DashboardI18n.text("urdu", "showFilters"))
                        && "فلٹرز چھپائیں".equals(DashboardI18n.text("urdu", "hideFilters"))
                        && "فعال فلٹرز".equals(DashboardI18n.text("urdu", "activeFilters")),
                "Urdu filter-disclosure strings are incomplete.");
        check("تقديرات غير مرجّحة".equals(DashboardI18n.text("arabic", "unweightedEstimates"))
                        && "العملة المحلية".equals(DashboardI18n.text("arabic", "localCurrency"))
                        && "جدول ملخص".equals(DashboardI18n.text("arabic", "summaryTable"))
                        && "حجم العينة".equals(DashboardI18n.text("arabic", "sampleN"))
                        && "المجموع المرجّح".equals(DashboardI18n.text("arabic", "weightedTotal"))
                        && "جميع المقابلات بعد التصفية".equals(DashboardI18n.text("arabic", "allFilteredInterviews"))
                        && "المتوسط + 3 انحرافات معيارية".equals(DashboardI18n.text("arabic", "meanPlusThreeSd")),
                "Arabic estimate-toggle, profile-table, or three-SD strings are incomplete.");
        check("غیر وزنی تخمینے".equals(DashboardI18n.text("urdu", "unweightedEstimates"))
                        && "مقامی کرنسی".equals(DashboardI18n.text("urdu", "localCurrency"))
                        && "خلاصہ جدول".equals(DashboardI18n.text("urdu", "summaryTable"))
                        && "نمونے کی تعداد".equals(DashboardI18n.text("urdu", "sampleN"))
                        && "وزنی مجموعہ".equals(DashboardI18n.text("urdu", "weightedTotal"))
                        && "تمام فلٹر شدہ انٹرویوز".equals(DashboardI18n.text("urdu", "allFilteredInterviews"))
                        && "اوسط + 3 معیاری انحراف".equals(DashboardI18n.text("urdu", "meanPlusThreeSd")),
                "Urdu estimate-toggle, profile-table, or three-SD strings are incomplete.");
        check(!"integerValueBins".equals(DashboardI18n.text("arabic", "integerValueBins"))
                        && !"integerValueBins".equals(DashboardI18n.text("urdu", "integerValueBins")),
                "Smart integer-distribution labels are missing Arabic or Urdu translations.");
        check("tukeyOutliers".equals(DashboardI18n.text("english", "tukeyOutliers"))
                        && "weightedOutlierMass".equals(DashboardI18n.text("arabic", "weightedOutlierMass"))
                        && "valueWhisker".equals(DashboardI18n.text("urdu", "valueWhisker")),
                "Retired Tukey/outlier translation keys should not remain in the runtime dictionary.");
        System.out.println("PASS dashboard UI language and direction resolution");
    }

    private static QuestionnaireSpec questionnaire(String title, String sectionTitle, String questionLabel) {
        QuestionnaireSpec spec = new QuestionnaireSpec();
        spec.language = "en";
        spec.title = title;
        QuestionSection section = new QuestionSection(1, sectionTitle);
        Question question = new Question();
        question.variable = "q1";
        question.label = questionLabel;
        question.section = sectionTitle;
        question.sectionNumber = 1;
        section.questions.add(question);
        spec.sections.add(section);
        spec.questions.add(question);
        return spec;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
