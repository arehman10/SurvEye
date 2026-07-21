*! SurvEye example 2.0.0 20jul2026
version 16.0
clear all
set more off

/*
Run this starter from Stata with three quoted arguments:

    do example.do "questionnaire.html" "survey_data.dta" "dashboard.html"

Paths may contain spaces.  The do-file inspects the questionnaire first, then
builds a dashboard from the data.  It will not silently reuse placeholder paths.
*/

args questionnaire datafile dashboard

if `"`questionnaire'"' == "" | `"`datafile'"' == "" | `"`dashboard'"' == "" {
    display as error "Three arguments are required.  Example:"
    display as error `"  do example.do "questionnaire.html" "survey_data.dta" "dashboard.html""'
    exit 198
}

confirm file `"`questionnaire'"'
confirm file `"`datafile'"'

display as text _newline "Step 1 of 2: inspect questionnaire"
surveye describe using `"`questionnaire'"', detail

display as text _newline "Step 2 of 2: build dashboard"
use `"`datafile'"', clear
surveye using `"`questionnaire'"', ///
    saving(`"`dashboard'"') replace open

return list

/*
Additional recipes
==================

Select indicators and observations:

    surveye legalstatus employment sales if consent == 1 ///
        using "questionnaire.html", saving("results.html") ///
        filters(region sector) highlights(employment sales) ///
        title("Enterprise survey results") replace

Choose dashboard density (compact is the default; comfortable is opt-in):

    // The sticky search/filter toolbar starts collapsed in either density.
    // Show filters reveals the controls without clearing existing selections.

    surveye legalstatus employment sales using "questionnaire.html", ///
        saving("compact.html") density(compact) replace open

    surveye legalstatus employment sales using "questionnaire.html", ///
        saving("comfortable.html") density(comfortable) replace open

Add a logical multiselect question whose Stata columns are services_used__1,
services_used__2, and so on:

    surveye sales employment using "questionnaire.html", ///
        saving("services.html") questions("services_used") ///
        filters(region) replace

Choose questionnaire sections by title and add messages:

    surveye using "questionnaire.html", saving("performance.html") ///
        sectionmatch("employment|performance") ///
        keymessages("Coverage::Completed interviews only|Caution::Weighted estimates") ///
        note("Percentages may not sum to 100 because of rounding.") replace

Include every chart in a very large questionnaire (the default cap is 100):

    surveye using "questionnaire.html", saving("complete.html") ///
        maxpanels(0) replace

Create custom sections and override chart types:

    surveye sector size sales employment using "questionnaire.html", ///
        saving("brief.html") ///
        customsections("Firm profile: sector size|Performance: sales employment") ///
        bars(sector) histograms(sales employment) replace

Add constructed variables that do not exist in the questionnaire.  Their Stata
variable labels become the chart titles.  Unplaced variables would appear in
"Additional indicators":

    label variable qc_score "Enumerator quality score"
    label variable risk_band "Review priority"

    surveye using "questionnaire.html", saving("quality.html") ///
        customvars(qc_score risk_band) ///
        addtosections("Interview quality: qc_score risk_band") ///
        histograms(qc_score) bars(risk_band) replace open

Move custom variables into an existing section by number and a new section by
title.  Only variables declared in customvars() may be moved:

    surveye sales using "questionnaire.html", ///
        saving("focused.html") customvars(qc_score risk_band) ///
        addtosections("3: qc_score|Quality checks: risk_band") replace

Confidence intervals are off by default.  Request 90% Wilson intervals for
ordinary categorical and multiselect bars with ci level(90).  Binary yes/no,
answered/missing completion, and donut cards never show CI text or whiskers:

    surveye sector ownership using "questionnaire.html", ///
        saving("shares_clean.html") replace

    surveye sector ownership using "questionnaire.html", ///
        saving("shares_90.html") ci level(90) replace

Numeric cards automatically include Distribution and Stats tabs.  The
distribution marks Tukey outliers while the table retains them in its summary:

    surveye sales employment using "questionnaire.html", ///
        saving("numeric_review.html") histograms(sales employment) replace

Apply Stata weights with the standard specification after the using filename
and before the comma.
There is no weight() option.  The weight must be one numeric variable; zero or
missing weights are excluded for all types, negatives are rejected, and
frequency weights must contain integers:

    surveye sector sales using "questionnaire.html" [aw=wmedian], ///
        saving("weighted_aw.html") replace

    surveye sector sales using "questionnaire.html" [fw=frequency], ///
        saving("weighted_fw.html") replace

    surveye sector sales using "questionnaire.html" [iw=importance], ///
        saving("weighted_iw.html") ///
        note("Descriptive importance weights.") replace

    surveye sector sales employment using "questionnaire.html" [pw=pop], ///
        saving("weighted.html") ///
        filters(region) note("Intervals use a Kish effective-n approximation.") replace

Build an Arabic dashboard.  uilanguage(ar) is short for uilanguage(arabic),
and direction(auto) selects right-to-left layout:

    surveye using "questionnaire_ar.html", saving("dashboard_ar.html") ///
        uilanguage(ar) direction(auto) replace open

Build an Urdu dashboard.  uilanguage(auto) normally recognizes a declared Urdu
questionnaire or Urdu-specific script; explicit settings are also available:

    surveye using "questionnaire_ur.html", saving("dashboard_ur.html") ///
        uilanguage(ur) direction(rtl) replace open

Add an Admin-2 Leaflet map.  maptype(points) is the default and keeps one marker
per valid observation; Google Hybrid is the default base map.  The browser can
switch among Google Hybrid, Satellite, Roads, and OpenStreetMap:

    surveye sector sales using "questionnaire.html", ///
        saving("mapped.html") ///
        latitude(gps_latitude) longitude(gps_longitude) country(KEN) ///
        boundaries("World Bank Official Boundaries - Admin 2.zip") ///
        maplevel(admin2) maptype(points) basemap(google_hybrid) mapby(sector) ///
        maptitle("Location of completed interviews") replace open

Use an aggregated display or OpenStreetMap only when deliberately requested:

    surveye sector using "questionnaire.html", saving("clustered.html") ///
        latitude(gps_latitude) longitude(gps_longitude) country(KEN) ///
        maptype(cluster) basemap(osm) replace

Preview a translated questionnaire with simulated data:

    surveye demo using "siNhl Global_informal2026.html", ///
        saving("sinhala_preview.html") n(250) seed(42) ///
        theme(clean) replace open
*/
