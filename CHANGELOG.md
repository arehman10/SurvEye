# SurvEye changelog

All notable changes to SurvEye (Stata command `surveye`) and its former
development names are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use
semantic versioning.

## [2.0.0] — 2026-07-20

### Added

- Added complete English, Arabic, and Urdu dashboard-interface dictionaries, including controls, chart summaries, numeric statistics, map text, empty/error states, and footer explanations.
- Added `uilanguage(auto|english|arabic|urdu)`, with `en`, `ar`, and `ur` aliases. Automatic resolution gives priority to declared `ar`/`ur`, then detects Urdu-specific or other Arabic-script questionnaire text, and otherwise selects English.
- Added `direction(auto|ltr|rtl)`. Automatic direction follows the resolved interface language—Arabic and Urdu use right-to-left, while English uses left-to-right; an explicit direction overrides layout without changing the selected interface language.
- Added localized HTML `lang` and `dir` metadata and right-to-left component behavior for navigation, controls, cards, charts, tables, tooltips, and Leaflet controls.
- Expanded documentation and examples for all four native Stata weight forms: `[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, and `[pw=pop]`.
- Added keyboard-operable point markers to Leaflet maps. Every individual point exposes a localized accessible label and opens its popup with Enter or Space.
- Added weighted Tukey-outlier mass and share to the numeric Stats tab, alongside the raw outlier count.
- Added the `ci` flag for opt-in pointwise Wilson intervals on ordinary categorical and multiselect horizontal bars. `level(#)` requires `ci` and defaults to 95 when intervals are requested.

### Changed

- **Breaking:** adopted the SurvEye product name and renamed the Stata command
  and distribution package from the former `suso_dashboard` name (and interim
  `surveydash` preview) to `surveye`. The installed entry points are now
  `surveye.ado`, `surveye.sthlp`, `surveye.pkg`, `surveye.jar`, and
  `surveye_2_0_0.jar`. Existing do-files must replace the former command name.
- Clarified that weights use Stata's native bracket syntax after the `using` filename and before the comma; there is no `weight()` option.
- Added native importance-weight support for descriptive estimates. Because iweights have no general sampling interpretation, requested confidence intervals are suppressed automatically with an explanatory note.
- Standardized weight validation: one numeric variable is allowed, negatives are rejected, fweights must be integers, and zero or missing weights are excluded from the analysis sample for every weight type.
- Changed confidence intervals from automatic to opt-in so dense dashboards remain visually clear. Binary yes/no, answered/missing completion, and donut cards never display CI text or whiskers, even when `ci` is supplied.
- Refreshed the visual grammar with a restrained World Bank chart palette: blue for ordinary comparisons, purple for distributions, navy for time trends, coral for median/outlier guides, neutral binary alternatives, and muted special values. Category and map colors now remain stable when filters change.
- Balanced incomplete card rows into deliberate three-, two-, or one-card compartments, stretched card edges within each row, and retained the compact chart heights. Normal Chart.js transitions now use 800 milliseconds; reduced-motion users still receive no animation.
- Replaced the tall sticky search/filter block with a compact toolbar that starts collapsed. An accessible Show/Hide filters disclosure reveals the full controls, preserves selections when closed, and keeps Reset all, the live interview count, and any active-filter count visible in the toolbar.
- Delayed chart construction until a chart is genuinely visible, deferred it while the browser tab is hidden, and removed eager whole-section rendering so the 800 millisecond transition is seen instead of finishing offscreen.
- Retained the compact visuals, custom-variable placement, optional confidence intervals, outlier-aware Stats tab, and Leaflet/Google map behavior introduced in 1.2.0.
- Localized generated Additional indicators, Other indicators, and untitled Key message headings instead of leaving English fallback text in Arabic or Urdu dashboards.

### Fixed

- Corrected custom numeric value-label lookup in Stata: the attached value-label definition is now resolved by name, with a safe raw-code fallback for missing/orphan definitions, instead of being mistaken for a dataset variable and raising `r(111)`.
- Corrected the public and parser-facing weight order to Stata's `using questionnaire.html [weight], options` grammar and added a static regression guard for every shipped example.
- Replaced Survey Solutions' generic calculated-variable labels with the actual variable name across panels, filters, highlights, and metadata, while preserving meaningful Stata and questionnaire labels.
- Kept explicitly configured missing codes out of categorical, multiselect, numeric, date, filter, and privacy-reduced completion calculations. Questionnaire-declared negative special response codes remain visible in categorical figures but are now excluded automatically from numeric distributions, statistics, outlier detection, and numeric filter choices; nonnegative substantive shortcuts remain valid.
- Preserved date formats and missing/special metadata for custom, filter-only, and highlight variables, including the `maxcategories(12)` boundary with 12 valid filter levels plus an excluded code.
- Corrected dark-theme chart, brand, and numeric-tab contrast and kept percentage/share demo values in their natural 0–100 range.
- Serialized affirmative and negative response codes from the Java parser so Turkish, Russian, Chinese, Japanese, Arabic, Urdu, Sinhala, and other recognized binary labels receive the correct colors without relying on a narrower browser-side translation list.
- Made histogram outlier counts readable, used theme-aware donut separators and confidence-interval halos, darkened labels placed inside light blue bars, fixed the map legend text token, and forced dark dashboards to print on a light background.
- Made filters type-aware and observed-value-only: numeric comparisons are normalized, multiselect filters match any selected valid option, completion filters distinguish answered from missing, and configured missing codes never become filter choices.
- Kept GPS rows whose `mapby()` value is configured missing as visible ungrouped points while excluding those values from map legends and category limits.
- Fully mirrored horizontal bar axes and direct-value labels in RTL layouts, and hardened category, filter, navigation, and map dictionaries so legitimate values such as `__proto__`, `constructor`, and `toString` cannot corrupt dashboard state.
- Embedded the applicable Chart.js, Leaflet, and Noto Sans Arabic license notices in each standalone HTML output and documented that the keyless Google tile URLs are unofficial compatibility endpoints whose availability and terms may change.
- Made Reset all clear the indicator search as well as response-filter choices, and refreshed visible charts and Leaflet sizing after the controls panel changes height.

### Compatibility

- The questionnaire/data workflow and dashboard options remain otherwise compatible with 1.2.0. The command/package rename is the intentional major-version break.
- Requested weighted confidence intervals remain descriptive: frequency weights use weighted counts, analytic and probability weights use a labelled Kish effective-sample-size approximation rather than design-based survey variance estimation, and importance weights suppress intervals.

## [1.2.0] — 2026-07-15

### Added

- Added `customvars()` for variables present in the Stata data but absent from the questionnaire. Custom charts use the Stata variable label by default and infer categorical, numeric, or date behavior from storage type, supported `%tc`/`%td`/`%tw`/`%tm`/`%tq`/`%th`/`%ty` formats, and value-label metadata.
- Added `addtosections()` so declared custom variables can be placed in an existing selected section by exact title or number, or in a newly named section, without reorganizing the rest of the questionnaire.
- Added embedded Leaflet 1.9.4 map rendering with the `esqc_gps` base-layer choices: `google_hybrid`, `google_sat`, `google_road`, and `osm`.
- Added pointwise Wilson confidence intervals to categorical shares, with configurable confidence levels.
- Added a **Stats** tab to numeric cards with valid and missing counts, mean, standard deviation, extrema, quartiles, median, Tukey fences, and outlier counts.
- Added an outlier-aware distribution guide and histogram scale using Tukey's 1.5-IQR whisker range while retaining extreme values in numeric statistics.

### Changed

- Changed the GPS defaults to `maptype(points)` and `basemap(google_hybrid)`. Every valid observation now receives an individual circle marker; exact coordinate duplicates are separated slightly on screen so each remains selectable.
- Kept `maptype(cluster)` and `maptype(heat)` as explicit aggregated displays rather than silently combining points by default.
- Map-enabled dashboards now fetch the selected Google or OpenStreetMap tiles when opened. Leaflet, survey points, and boundary geometry remain embedded, and dashboards without maps retain no runtime network dependency.
- Updated the privacy notice to state that map-enabled files embed valid coordinates at six decimals for points, four for cluster, and three for heat. Reduced precision and visual aggregation are not presented as a substitute for formal disclosure control.
- Weighted confidence intervals now label analytic- and probability-weight results as effective-sample-size approximations and explicitly note that they are not adjusted for complex survey design.

### Fixed

- Replaced the stretched static SVG Leaflet overlay with true WGS84 boundary rings so country and Admin-2 lines align with GPS points; antimeridian countries are normalized onto one longitude branch.
- Counted expanded-multiselect respondents in the combined **Other** category once even when they selected more than one grouped response.
- Prevented extreme numeric values from flattening the main histogram while continuing to report them transparently as outliers.

## [1.1.0] — 2026-07-15

### Added

- Added `density(compact|comfortable)` so users can switch between the space-efficient default and a roomier presentation.
- Added concise, screen-reader-friendly chart summaries and reduced-motion support.

### Changed

- Redesigned the dashboard as a responsive three-, two-, or one-column grid for wide, medium, and mobile screens.
- Replaced binary and completion donuts with compact 100% split bars that display values directly.
- Made sorted horizontal bars the automatic categorical display; donut charts are now available only when explicitly requested.
- Compacted numeric histograms and date visuals, including a visible median marker for faster distribution reading.
- Collapsed optional GPS maps to a compact summary in compact mode while keeping the full interactive map one click away; comfortable mode opens maps initially.
- Strengthened responsive visual QA across desktop, tablet, mobile, overflow, accessibility-summary, and reduced-motion states.

## [1.0.4] — 2026-07-15

### Fixed

- Returned Stata numeric missing directly for mode-specific status fields that are absent or explicitly `.` instead of passing `.` to `confirm number` and raising `r(498)`.
- Guarded GPS summary formatting until all map counters are present.
- Added licensed-Stata regressions for sparse, explicit-missing, and genuinely malformed numeric status records.
- Added strict Java status-schema checks for build, demo, describe, engine-error, and bridge-linkage paths.

## [1.0.3] — 2026-07-15

### Added

- Added the fully qualified Stata plugin entry point `org.worldbank.suso.dashboard.StataPlugin` with a fallback status-file handoff for linkage failures.
- Added a release-specific `suso_dashboard_1_0_3.jar` for Stata while retaining `suso_dashboard.jar` for command-line use.

### Fixed

- Prevented an obsolete unversioned JAR earlier on the ado-path from shadowing the engine required by the current ado file.
- Replaced the absolute mixed-separator Windows JAR argument with Stata's documented `jars()` ado-path lookup.
- Preserved `javacall` loader and JVM diagnostics instead of suppressing them with `quietly`.
- Added a specific explanation for Stata `r(5100)` when the installed JAR is stale or incompatible.

## [1.0.2] — 2026-07-14

### Added

- Added an explicit `build` subcommand for datasets containing a leading variable named `describe` or `demo`.
- Added a licensed-Stata smoke do-file covering main, describe, demo, `if`, weights, all-missing rows, `showempty`, macro-safe Unicode text, output protection, and returned results.

### Fixed

- Replaced the invalid bare `tab` token in Stata `file write` with the required `_tab` directive and added a source-contract regression check.
- Protected configuration and status strings from unintended Stata macro expansion.
- Resolved abbreviated weight names before CSV export and Java configuration.
- Preserved observations missing on every selected field by exporting a noncharted analysis-sample sentinel.
- Allowed `showempty` to reach Java when no selected response column exists.
- Accepted logical questionnaire names in `exclude()` and rejected chart/exclude contradictions consistently.
- Prevented relative-path aliases and symlinks from allowing outputs or diagnostics to overwrite questionnaires, data, boundaries, logos, status files, or each other.
- Added fallback Java status reporting for configuration-read errors and clearer diagnostics for missing status files.

## [1.0.1] — 2026-07-14

### Fixed

- Corrected construction of the Stata analysis-sample marker so commands without `if`, `in`, or weights no longer stop with `__000000 not found` (`r(111)`).

## [1.0.0] — 2026-07-14

### Added

- Stata 16+ `rclass` command with main, `describe`, and `demo` modes.
- Direct, platform-independent Stata-to-Java bridge through `javacall`.
- Survey Solutions questionnaire parser designed for English, translated, legacy, and current printable HTML variants.
- Automatic and explicit variable selection, questionnaire section selection, section-title matching, exclusions, and custom sections.
- A responsive 100-panel default limit for large dashboards, with `maxpanels()` control and an unlimited opt-in.
- Logical `questions()` selection with exact and Survey Solutions multiselect-expansion export; it can be combined with the Stata `varlist`.
- Automatic chart choice plus bar, donut, and histogram overrides.
- Dashboard filters, highlight cards, key messages, titles, subtitles, notes, source lines, disclaimers, embedded logos, and four themes.
- `if`, `in`, analytic-weight, frequency-weight, and probability-weight support.
- Temporary UTF-8 raw-code CSV export containing only the variables required by the requested dashboard.
- Optional country GPS map with clustered, heat, and point displays; bundled country outlines; user-supplied WGS84 boundary ZIPs; Admin-2 support; map grouping; and coordinate QA counts.
- Self-contained offline HTML with embedded Chart.js, data, CSS, logo, and map geometry.
- Structured status exchange and documented `r()` results for automation and QA.
- Friendly input validation, strict mode, diagnostic logging, and refusal to create empty dashboards from unrecognized questionnaire files.
- Stata help, runnable example do-file, GitHub/SSC package metadata, license, and third-party notices.

### Fixed

- Matched zero-padded questionnaire option codes to Stata numeric exports while preserving genuine string categories.
- Reduced text, media, GPS-completion, and unlabeled linked text-list questions to answered/not-answered flags.
- Calculated weighted means, medians, shares, and histograms consistently, including safe handling of zero-total-weight selections.
- Distinguished unanswered expanded multiselect rows from answered all-zero rows so respondent denominators remain correct.
- Recognized ISO, Stata daily, Stata datetime, and common displayed-date forms without generating invalid demo dates.
- Omitted invalid GPS rows, kept map colors stable under filtering, and aligned map points at mobile sizes.
- Preserved negative numeric responses in histograms instead of treating every negative value as missing.
- Preserved a valid source-document language tag and used `lang="und"` when the questionnaire does not declare one.
- Removed Survey Solutions Markdown links and formatting artifacts from displayed labels.
- Added explicit `filters()` selection and metadata/cardinality checks; automatic suggestions remain conservative and language/name dependent.
- Tightened automatic filter matching to whole concepts, avoiding false matches such as `state` inside `statements` or `strat` inside `administrative`.
- Applied `maxcategories()` to bar, multiselect, and donut displays using a top-levels-plus-Other rule while preserving the full denominator.
- Replaced raw Java stack traces for common user errors with concise Stata and engine messages.
- Removed runtime dependencies on Google Fonts, CDNs, and other network services.
