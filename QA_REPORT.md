# QA report — SurvEye 2.1.2

Release-candidate review date: 2026-07-20

## Outcome

The Java engine, questionnaire parser, generated HTML, browser interactions,
privacy transformations, GPS rendering, package structure, and the public
Stata plugin/status-file contract pass the available portable tests. Two
real-Stata traces exposed wrapper defects before Java invocation: an incorrectly
preallocated analysis-sample marker (`r(111)`) and a bare `tab` token in
`file write` (`r(198)`). The ado now uses the documented
`marksample touse, novarlist` and `_tab` contracts, and CI rejects either old
pattern. A subsequent full wrapper audit also corrected weight abbreviations,
all-missing-row retention, macro-safe status text, `showempty`, logical
exclusions, path aliases, and configuration-error status reporting. No
release-blocking engine or rendering defect is currently known.

Version 2.0.0 establishes the SurvEye brand and `surveye` public command; the
command, help, package manifest, generic JAR, and release JAR consistently use
that name. It also adds complete Arabic and Urdu interface
translations, automatic language detection, and right-to-left layout through
`uilanguage()` and `direction()`. Native Stata bracket weights remain the only
weight interface and are now documented and exercised explicitly as
`[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, and `[pw=pop]`; no
`weight()` option exists.

The 1.2 feature set remains intact: `customvars()` and `addtosections()`,
configurable Wilson intervals, the numeric Stats/outlier view, and Leaflet maps
with Google Hybrid, Satellite, Roads, and OpenStreetMap layers. `maptype(points)`
and `basemap(google_hybrid)` remain the defaults; every valid GPS row is drawn
separately, coincident points are spidered for selection, and individual point
markers are keyboard focusable with Enter/Space popup activation. Cluster and
heat displays remain explicit alternatives.

The v1.1 compact chart grammar remains: directly labeled horizontal bars,
split binary/completion bars, short numeric and date figures, and responsive
three-, two-, or one-column compact layouts. A compact GPS map stays collapsed
until requested; comfortable mode uses a roomier grid and opens it initially.

A third trace returned `r(5100)` before any status file was written.  The JAR
resolved from `PERSONAL` was the original 34 KB command-line engine, which has
`main()` but no Stata `stata(String[])` entry method; it shadowed the corrected
826 KB JAR.  Since version 1.0.3 the command calls a fully qualified bridge from a
release-specific JAR, preserves Stata's JVM/class-loader
diagnostic, and reports the resolved JAR path.  This removes the unversioned
shadowing path while keeping `surveye.jar` for command-line use.

A fourth trace completed the Java call but failed while reading an intentionally
absent `k_questions` field.  The wrapper changed the absence to `.` and then
called `confirm number .`, which the licensed Stata runtime rejected with
`r(498)`.  Version 1.0.4 returns numeric missing directly for absent or explicit
`.` fields, validates only present values, and retains `r(498)` for genuinely
malformed status data.  Sparse and malformed status records are now exercised
by the licensed-Stata smoke file and guarded statically.

Two environment-dependent release checks remain. A licensed Stata executable
was not available, so Stata 16 and current-Stata must run the smoke tests below
to verify `net install`, `javacall`, the ado wrapper, status-file parsing, and
returned `r()` values end to end. The deterministic browser run intercepted
the expected Google tile requests; a connected browser must also confirm live
imagery and base-layer switching in the intended deployment environment.

## Supplied questionnaire coverage

| Questionnaire | Sections | Questions | Options | Parser warnings | Default panels |
|---|---:|---:|---:|---:|---:|
| Australia ES B-READY 2025 | 21 | 399 | 975 | 0 | 100 |
| Global informal 2026 — English | 3 | 212 | 574 | 0 | 100 |
| Global informal 2026 — Sinhala | 3 | 212 | 574 | 0 | 100 |
| TRG 2025 — English | 8 | 87 | 90 | 0 | 45 |

The parser suite completed 4,994 assertions. The English and Sinhala informal
questionnaires have identical structural metadata, and Sinhala Unicode and
zero-width joiners survive parsing and rendering. The Australia questionnaire
produces all 351 chartable panels with `maxpanels(0)`; the default 100-panel cap
keeps large dashboards responsive and reports that later panels were omitted.

## Engine regression checks

| Area | Result |
|---|---|
| Survey Solutions variants | Current, legacy, English, and translated printable HTML fixtures parsed successfully. Arbitrary or empty HTML is rejected. |
| CSV | UTF-8 BOM, quoted commas, multiline quoted fields, raw codes, case-insensitive columns, and ragged-row warnings are handled. `strict` rejects a ragged CSV with exit code 2 and creates no output. |
| Category codes | Numeric exports match zero-padded questionnaire codes such as `01`, `0004`, and `-09`; genuine string codes remain distinct. |
| High-cardinality charts | With `maxcategories(5)`, a 16-level item renders four leading levels plus `Other (12 categories)` while retaining the full denominator. An expanded-multiselect respondent is counted once in the combined Other group even when several grouped responses were selected. |
| Compact chart grammar | Automatic categorical, binary, completion, numeric, date, and explicit-donut paths render the intended Chart.js types. Every card exposes a direct or screen-reader summary, and no compact chart exceeds its type-specific height limit. |
| Palette and symmetry | Focused JavaScript checks lock the 12 nonrepeating chart accents, code-based binary roles, muted special values, 800 ms normal animation, zero reduced-motion animation, and balanced 3/2/1 row partitions. Browser QA proves an offscreen chart stays unbuilt, becomes animated only after entering the viewport, changes geometry during the 800 ms transition, and then completes; it also measures equal card widths and heights in every row, full-width singleton search results, and complete use of each grid in LTR and RTL layouts. |
| Density option | Compact is the engine and Stata default; `density(comfortable)` reaches the generated HTML; any other value returns a friendly status-file error. |
| Multiselect denominator | Expanded rows `1/0`, `0/1`, `0/0`, and missing become `["1"]`, `["2"]`, `[]`, and `null`; the chart correctly reports three answered respondents. |
| Privacy transformations | Text, media, GPS-completion, and linked text-list questions embed only Boolean completion. Focused outputs contain zero occurrences of the test name or phone numbers; linked multiselect output is `true`, `true`, `null`. |
| Interface language | `uilanguage(auto)` respects declared `ar`/`ur`, detects predominantly Arabic-script questionnaire text and Urdu-specific characters when no declaration is available, ignores incidental Arabic text in otherwise non-Arabic instruments, and otherwise resolves to English. Explicit `english`/`arabic`/`urdu` and `en`/`ar`/`ur` inputs validate and propagate. Generated HTML exposes the resolved language to interface strings and accessibility text. |
| Text direction | `direction(auto)` follows the resolved UI language (Arabic/Urdu RTL; English LTR), while `ltr` and `rtl` override layout independently of translation. The root document and responsive controls, navigation, tables, charts, and Leaflet UI preserve logical reading order without horizontal overflow. Horizontal bar scales, category axes, interval/direct-label placement, and map controls are mirrored rather than merely text-aligned. |
| Custom variables | Data-only variables are additive to questionnaire selection, use their Stata variable label by default, and infer numeric, date, string-category, or value-labelled category behavior. Generic Survey Solutions calculated-variable labels fall back to the canonical variable name across panels, filters, highlights, and metadata; meaningful labels remain unchanged. Placement accepts exact titles, original section numbers, and visible selected-dashboard positions; unplaced variables remain in Additional indicators. |
| Confidence intervals | Default output contains no CI text or whiskers. `ci` enables pointwise Wilson intervals only on ordinary categorical and multiselect horizontal bars; `level()` requires `ci` and defaults to 95 when requested. Binary yes/no, answered/missing completion, and donut cards remain CI-free even with `ci`. Frequency weights use weighted counts; analytic and probability weights use a labelled Kish effective-sample-size approximation. Importance weights automatically suppress requested CIs with an explanatory note. |
| Weights | The wrapper accepts only native Stata specifications after the `using` filename and before the comma: `[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, or `[pw=pop]`; there is no `weight()` option. One numeric variable is required. Negatives fail; fweights must be integers; zero and missing weights are excluded from the analysis sample for every type. Weighted shares, histograms, means, medians, quantiles, standard deviations, and Stats tables use the supplied weights. When `ci` is requested, confidence-interval footers identify non-design-based approximations. |
| Numeric outliers | Distribution and Stats tabs are keyboard operable. The distribution uses Tukey's 1.5-IQR whisker range and annotates outside values; the Stats table retains all valid values and reports n, missing, mean, standard deviation, range, quartiles, median, fences, and outlier count. Weighted cards additionally report outlier weight mass and its share of valid weight. Percentage/share questions in demo mode are generated within 0–100 (except labels that explicitly describe change); the final sample contains `b2a` values from 0 through 100. |
| Special and missing codes | Questionnaire-defined special responses remain visible and muted in categorical figures, while numeric figures, Stats, outlier detection, and numeric filters automatically exclude declared negative special codes. Focused JavaScript and Java/HTML contracts verify that ordinary `50` and the nonnegative substantive shortcut `100` remain in numeric results, declared `-4` and `-9` do not, explicitly configured missing codes remain excluded everywhere, and missing/special arrays propagate independently into `META`. A privacy-reduced completion fixture also maps valid/`-9`/blank to `true`/`null`/`null`, so sentinels cannot inflate completion rates. |
| Dates | ISO/displayed dates and numeric Stata `%tc`, `%td`, `%tw`, `%tm`, `%tq`, `%th`, and `%ty` values render through the date path. The metadata contract verifies every format, including negative weekly/monthly/quarterly/half-year serials in JavaScript; business-calendar `%tb` values remain explicitly unsupported and documented. |
| Filters and highlights | A filter-only variable that is also a highlight keeps its questionnaire labels plus explicit missing/special metadata. Choices are observed-value-only and type-aware: scalar, normalized numeric, any-selected multiselect, and answered/missing completion behavior all pass focused contracts. The 12-valid-plus-`-9` boundary renders all 12 valid choices while excluding `-9`; an all-missing requested filter fails clearly. Values such as `__proto__`, `constructor`, and `toString` remain ordinary safe categories. |
| GPS | Bundled country outlines and the supplied World Bank Admin-2 ZIP render. The Admin-2 fixture loads 566 features and reports 8 valid points, 1 invalid pair, and 0 outside points. Point mode creates one Leaflet SVG circle marker per valid row; duplicate coordinates remain individually selectable. Every point exposes a localized accessible name, visible focus, and Enter/Space popup activation. Configured-missing `mapby()` values stay visible as ungrouped points but do not enter the legend or group cap. Valid latitude/longitude values are embedded at six decimals for points, four for cluster, and three for heat, and the disclosure notice says so. True WGS84 rings align the boundary with points; the browser fixture verifies all 43 Australia rings. |
| Base maps | Leaflet 1.9.4, points, and boundary geometry are embedded. Google Hybrid is selected by default; Google Satellite, Google Roads, and OpenStreetMap are available from the layer control. Expected tile traffic is separated from unexpected external requests in browser QA. |
| Output security and notices | Labels and data are escaped. The CSP permits only the documented Google/OpenStreetMap tile images for map-enabled output while continuing to block other external resources; non-map dashboards retain no runtime network dependency. Each standalone output embeds the applicable full Chart.js MIT, Leaflet BSD, and Noto Sans Arabic OFL notices in a nonvisual template. |
| Stata sample marker | The supplied real-Stata trace was reproduced by source inspection: preallocating `touse` and passing its expansion to `marksample` left `__000000` uncreated. The command now lets `marksample` create and bind the marker, and a dedicated source-contract test passes. |
| Stata config writer | The second real-Stata trace reached the config helper and showed that bare `tab` is invalid. The writer now uses `_tab`, protects values with `macval()`, and CI requires `_tab` plus `_n`. |
| Stata Java loader | The third trace returned Stata's general Java exception `r(5100)` before the engine entry point ran. The original unversioned JAR lacked that entry point and shadowed the corrected binary in `PERSONAL`. Stata now calls a fully qualified bridge from a release-specific JAR name and retains loader diagnostics. |
| Stata status numbers | The fourth trace showed that a build status legitimately omits describe-only `k_questions`, but the reader sent its missing placeholder `.` through `confirm number` and raised `r(498)`. Absent/`.` values now return numeric missing directly; present values remain strictly validated. Schema tests cover build, demo, describe, engine-error, and bridge-error records. |
| Stata option parser | Optional `level()` is captured as a one-value numlist. This avoids Stata's invalid optional `real` descriptor without losing the distinction between an omitted level and an explicitly supplied one. A licensed-Stata regression reproduces the reported Sri Lanka GPS command—including spaced paths, coordinate-option whitespace, `country(Sri Lanka)`, `sections(3)`, and `maxpanels(400)`—and verifies that parsing reaches ordinary boundary-file validation instead of returning `r(197)`. |
| Missing-row denominator | The internal sample marker is retained as a noncharted CSV sentinel, so a respondent missing on every selected field is not mistaken for an empty CSV line. The bundled multiselect fixture retains all four rows. |
| Wrapper selections | Weight abbreviations are resolved to canonical data names; `showempty` works without a matching response column; logical multiselect names are accepted by `exclude()`; and chart/exclude contradictions fail early. |
| Path safety | Java compares normalized/real paths before writing. The status-contract test verifies that an aliased output path cannot overwrite the questionnaire and that source bytes remain unchanged. |
| Stata Java boundary | The portable smoke test calls `org.worldbank.surveye.StataPlugin.stata(String[])` with bundled and supplied fixtures, verifies successful output, bridge/linkage fallback status, missing-questionnaire errors, malformed-config fallback status, and path-collision protection. |

## Browser and visual QA

The browser harness tests 1440×1000 desktop, a short 1536×640 desktop viewport,
1024×1366 tablet, and 390×844 mobile layouts. Each run checks the collapsed and
expanded search/filter toolbar, search, filters, reset, collapsible sections,
chart type, chart height, exact-value summaries, screen-reader descriptions,
responsive column count, legends, maps and hover tooltips when present,
horizontal overflow, console/page errors, and external requests.

The 20 July controls-disclosure regression passed 12 of 12 viewport runs across
the release sample, a three-filter Sri Lanka stress dashboard, and a dark Arabic
RTL map dashboard. The collapsed toolbar
measured 54–56 pixels in English and 56–80 pixels in Arabic, including the short
desktop case that reproduces the reported obstruction. Enter and Space toggled
the disclosure, focus returned safely when hidden, filter selections and matched
counts survived collapse/reopen, Reset all worked while collapsed, and the
active-choice badge returned to zero. The three-filter dashboard also verified
bounded, scrollable expanded controls. Both expanded and collapsed states had
zero horizontal overflow; all charts and all 300/120 point markers remained
rendered without browser, page, or unexpected-network errors.

Nine final 2.1.2 executable suites passed after the palette, symmetry,
controls-disclosure, and numeric-axis refresh: 29 of 29 viewport runs and 29
saved screenshots.
Seven baseline suites retain the original three-viewport matrix; the regenerated
release sample and smart-numeric dashboard also pass the short-desktop regression,
for four viewports each. Reports are under `tests/design-output/palette-qa-*` for
the established suites and `build/qa-numeric-results-final` for the numeric-axis
regression.

| Final suite | Exact browser result |
|---|---|
| Urdu RTL | Passed 3/3. The document resolved to `lang="ur" dir="rtl"`, embedded both 400 and 700 Noto Sans Arabic font resources, rendered its multiselect chart in every viewport, used responsive 3/2/1 columns, and had no horizontal overflow, browser errors, or external requests. Focused JavaScript contracts separately verify reversed horizontal scales and mirrored value-label placement. |
| Arabic RTL map | Passed 3/3. The document resolved to `lang="ar" dir="rtl"` with embedded Noto Sans Arabic. All 3/3 charts rendered; the numeric Stats tab populated five rows and retained the Tukey-outlier field. Google Hybrid point mode rendered 8/8 expected accessible markers over all 43 Australia boundary rings, filtering/refitting passed 8 to 2 to 8, and keyboard QA opened a point popup with both Enter and Space. |
| Weighted numeric | Passed 3/3. The weighted histogram rendered in every viewport and reported weighted median 7, weighted mean 18.56, raw n=8, and zero Tukey outliers. Its six-row Stats table opened, populated, exposed raw and weighted outlier fields, and restored the Distribution tab correctly. |
| Expanded multiselect | Passed 3/3. QA caught and the implementation fixed an empty-array denominator bug: an answered all-zero expansion now contributes to the respondent denominator but to no option numerator. Browser QA verified respondent n=3 against expected n=3 in all three viewports; the default output contained no CI text or whiskers. |
| Dark Arabic RTL | Passed 3/3. All 3/3 charts rendered in every viewport with readable dark-theme labels, axes, summaries, and Stats content. Responsive 3/2/1 columns, RTL order, search/filter/reset interactions, and horizontal-overflow checks passed without browser errors or external requests. |
| Explicit CI | Passed 3/3. Eligible ordinary bars use the opt-in Wilson whiskers; binary and numeric cards remain free of ambiguous CI overlays. The long custom-variable card occupies a deliberate full-width singleton row. |
| Dark donut | Passed 3/3. The eight-color composition palette, card-colored segment separators, neutral binary bar, purple distribution, and coral median/outlier guide remain distinct on the dark card background. |
| Release sample | Passed 4/4, including 1536×640. All 4/4 charts rendered in every viewport. The collapsed control toolbar measured 54–56 pixels; expanded state, selection preservation, collapsed Reset all, focus return, and zero-overflow checks passed. The compact Google Hybrid point map rendered 300/300 individual accessible markers and refit/restored 300 to 164 to 300 after filtering. |
| Smart numeric distributions | Passed 4/4, including 1536×640. Age and years-in-city cards used linear numeric axes with regular round-number ticks, bounded integer-aligned bars, visible median guides, and explicit low/high Tukey-tail counts. An age value of 506 remained in Stats without stretching the plotted axis; `maxcategories(12)` remained unchanged. |

Across the nine final reports, every search, filter/reset, details, chart,
responsive-density, accessibility-summary, Stats, map, and overflow assertion
passed. There were zero browser console errors, page errors, unexpected local
requests, or unexpected external requests. The three map suites intercepted 173
expected basemap-tile attempts so the runs remained deterministic under the
network policy.

A paired CI regression also passed all six viewport runs. The clean-default
report at `tests/qa-output/surveye-ci-default-final/qa-report.json` verified
zero CI text and zero populated intervals on every card. The explicit-CI report
at `tests/qa-output/surveye-ci-enabled-final/qa-report.json` verified populated
Wilson whiskers on both eligible ordinary bars while its binary card retained
zero interval text, interval data, or CI plugin.

The generated Arabic and Urdu HTML was also inspected for the embedded Noto
font declarations and root RTL attributes. Before public release, a connected
browser should still confirm live Google imagery and base-layer switching in
the intended deployment environment; deterministic QA validates the tile URL
pattern and Leaflet behavior but intentionally intercepts tile responses.

## Package checks

- Engine version: 2.1.2
- Generic and release-specific JARs are verified byte-for-byte identical. A fixed SHA is not published because ZIP entry timestamps change on a clean rebuild.
- Every Java class in the current release build has Java 8 major version 52
- Runtime dependencies: `java.base` only
- Public Stata entry point: `org.worldbank.surveye.StataPlugin.stata(java.lang.String[])`
- The active JAR contains only the canonical `org/worldbank/surveye`
  bridge; package QA rejects retired command, class, and package namespaces
- Embedded resources: Chart.js, Leaflet, CSS, JavaScript, country geometry,
  aliases, Noto Sans Arabic 400/700 WOFF2 files, and the Noto OFL license are
  present; executable web resources byte-match their source files
- JAR archive integrity: passed
- Ado structure: 8 programs and 8 matching `end` statements
- Stata source contracts: sample marker, `_tab`/`_n`, `macval()`, canonical weight, row sentinel, optional numeric status fields, and balanced program/end checks pass
- JavaScript statistics regression: `tests/test_statistics.js` verifies
  Stata-compatible percentile boundaries, weighted means and standard
  deviations, aweight scale invariance, effective sample sizes, a/f/i-weight
  confidence-interval behavior, and the fixed multiselect empty-array
  denominator contract, plus special-versus-explicit-missing behavior for
  categorical, numeric, and date values; it also covers type-aware filters,
  reserved category keys, mirrored RTL scales/direct labels, weighted outlier
  share, the accessible Leaflet point-marker source contract, smart integer
  bins, numeric linear axes, regular tick intervals, Tukey-tail clipping, and
  visible-plus-tail mass conservation
- Numeric-distribution contract: `tests/NumericDistributionContractTest.java`
  verifies that 81 distinct integer ages plus an extreme positive tail build
  successfully with `maxcategories(12)` and retain nonnegative/special-code
  metadata
- Java weight contract: `tests/WeightContractTest.java` verifies all four
  weight types, zero/missing exclusion, paired `weight`/`weighttype`, negative,
  fractional-fweight, all-zero, and nonnumeric failures, and iweight CI
  suppression through the real Java/status-file boundary
- Java metadata contract: `tests/DateFormatMetadataContractTest.java` verifies
  all supported Stata date formats, independent special/missing arrays, and the
  12-valid-plus-`-9` filter/highlight boundary at `maxcategories(12)`
- Completion missing-code contract: `tests/CompletionMissingContractTest.java`
  verifies that privacy-safe Boolean reduction still applies `missingcodes()`
  before the original value is discarded
- Map/filter/localization contract: `tests/MapFilterLocalizationContractTest.java`
  verifies observed-only typed choices, all-missing-filter rejection,
  Arabic/Urdu binary recognition, and configured-missing `mapby()` exclusion
  with point retention
- Licensed-Stata regression harness: `tests/stata_smoke.do` covers sparse/malformed status records, main, describe, demo, `if`, native a/f/i/p weights, zero/missing weights, missing rows, `showempty`, special-character Unicode text, explicit `build`, custom-variable placement, UI language/direction, default CI absence, explicit `ci level(#)`, output protection, and `r()` results
- SMCL help braces: balanced
- SSC manifest: every listed file exists; the JAR correctly uses uppercase `F`
- Release builder: the full-source ZIP excludes every retired installable
  filename, the SSC ZIP is flat and matches the package manifest exactly, and
  SHA-256 checksums are printed after validation
- Shell and JavaScript syntax checks: passed
- GitHub Actions builds and validates the `2.1.2` release-specific JAR, runs
  the package-integrity checks, and exercises the clean flat-SSC release builder

## Required Stata release smoke test

Run from a clean package staging directory under Stata 16 and the newest Stata
version you intend to support:

```stata
net install surveye, from("LOCAL_OR_RELEASE_URL") replace
which surveye
findfile surveye_2_1_2.jar
help surveye

surveye describe using "English TRG_2025.html", detail
return list

use "survey_data.dta", clear
surveye selected_var1 selected_var2 if completed == 1 ///
    using "questionnaire.html", saving("smoke.html") ///
    filters(region) highlights(selected_var1) ///
    keymessages("QA::Release smoke test") diagnostics("smoke.txt") ///
    replace open
return list

surveye selected_var1 using "questionnaire.html" [aw=wmedian], ///
    saving("smoke_aw.html") replace
surveye selected_var1 using "questionnaire.html" [fw=frequency], ///
    saving("smoke_fw.html") replace
surveye selected_var1 using "questionnaire.html" [iw=importance], ///
    saving("smoke_iw.html") replace
surveye selected_var1 using "questionnaire.html" [pw=pop], ///
    saving("smoke_pw.html") replace

surveye using "questionnaire_ar.html", saving("smoke_ar.html") ///
    uilanguage(ar) direction(auto) replace
surveye using "questionnaire_ur.html", saving("smoke_ur.html") ///
    uilanguage(ur) direction(rtl) replace
```

Run the bundled deterministic wrapper regression after installation:

```stata
do "tests/stata_smoke.do" "C:/path/to/extracted/surveye-2.1.2"
```

Also test:

1. an expanded multiselect through `questions()`;
2. native `[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, and `[pw=pop]`
   syntax, including zero/missing exclusion, integer fweights, negative-weight
   diagnostics, and suppression of requested CIs for iweights;
3. real `%td` and `%tc` variables;
4. bundled-country and Admin-2 maps with missing coordinates, exact duplicate
   points, `mapby()`, all four `basemap()` choices, and deliberate cluster/heat modes;
5. an existing output without `replace`, malformed HTML, a missing JAR, and a
   ragged CSV under `strict`; and
6. `customvars()` label/type/value-label inference, `addtosections()` by title,
   original section number, and visible position, default CI absence,
   `ci level(90)` on eligible bars, CI-free binary/completion/donut cards, and
   the numeric Stats tab; and
7. Arabic and Urdu `uilanguage(auto)` detection, explicit `ar`/`ur` aliases,
   `direction(auto|ltr|rtl)`, RTL keyboard navigation, and mobile overflow; and
8. installation from an SSC-like index, confirming both uppercase `F` JARs are
   installed and `findfile surveye_2_1_2.jar` resolves the release-specific binary.

## Publication notes

Before publishing, add the permanent GitHub path plus a maintained support
email or issue URL to the README, help, and package metadata, following
`PUBLISHING.md`. Treat generated HTML like the source analysis data: selected
row-level values are embedded for interactivity, and even reduced-precision
latitude/longitude values can still be identifying. Translated questionnaires
should use explicit `filters()` when variable names do not reveal safe filter
candidates.
