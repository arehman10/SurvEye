# SurvEye changelog

All notable changes to SurvEye (Stata command `surveye`) and its former
development names are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use
semantic versioning.

## [Unreleased]

## [2.3.2] — local review candidate, 2026-09-05

Unpublished review build based on the supplied 2.3.1 revision 5 archive.

### Fixed
- Ignore macOS resource-fork entries when selecting shapefile ZIP components;
  normalize UTF-8-SIG and common UTF-8 CPG aliases.
- Keep boundary feature labels aligned with original DBF rows across deleted
  records/null shapes and preserve them through simplification.
- Binary highlights consistently use the affirmative category, not the mode.
- Filter-aware missing/outside map counters; empty sample resets to country extent.
- Remove surplus closing control markup and fix page overflow on narrow screens.

### Changed
- Preserve the original report structure and chart grids while improving text
  contrast, chips, card balance, numeric headers, and responsive spacing.
- Named district polygon hover, Fit interviews/Country extent controls, tile-failure
  notice, and bounding-box short-circuits for point-in-boundary checks.
- Add a real-archive synthetic preview, portable regeneration helper, boundary
  archive tests, and real-Chromium interaction/layout QA scripts.
- Rebuilds can reuse embedded runtime assets from the supplied JAR.

### Verification
- Portable gate passed; 77 boundary assertions; eight real-archive countries;
  73 real-Chromium assertions; 21 theme/RTL layout checks. Actual Stata,
  Windows network drives, live tile availability and other browsers remain
  unverified. Details and raw evidence: QA_REPORT.md.

## [2.3.1] — local review candidate, 2026-09-04

This candidate is prepared for local review and has not been pushed or published.
Licensed Stata and browser verification must be recorded separately from the
portable Java/Node checks; these notes do not claim either gate has passed.

### Local review revision 5 — 2026-09-05
- Withdraw the layout proposals from revisions 2–4 and restore the original
  shipped single-page report: header, compact controls, highlights, profile
  table, map, section navigation, and chart grids. The original stylesheet is
  preserved byte for byte; workspace scripts, styles, and navigation are removed.
- Retain all audit fixes and source recovery. Existing chart customization,
  numeric tabs, themes, and export controls were part of the shipped package
  and remain available in their original locations.
- Preserve section and map open/closed states after printing without changing
  the original print layout.
- Prevent questionnaire-defined codes such as `01` and `1` from merging during
  numeric normalization; preserve unambiguous padded-code aliases. A Java
  regression fails before the fix and passes after it.
- Replace redesign-specific tests with original-interface regression checks.

### Local review revision 4 — 2026-09-05
- Restore the graph-filled sheet as **Graphs**, the first primary tab and default
  opening view. Retain original chart grids and start all sections expanded;
  section jump and Expand all/Collapse all controls remain available.
- Keep explicit saved-view links, readable full filter wording, shared analytical
  state, question details, comparison tools, and all audit fixes.
- Add regression coverage for the restored primary/default view, original chart
  placement after visiting a question, filtered chart totals, section controls,
  and explicit saved Overview links. Browser rendering and licensed Stata
  execution remain unverified.

### Local review revision 3 — 2026-09-05
- Reorganize the whole report around four primary views: Overview, Questions,
  Compare, and Map. Replace the stacked masthead with a compact survey header
  and global scope bar. Move Report, Methodology, export, theme, and print actions
  into the secondary **Report & export** menu.
- Show all configured Overview indicators as compact result rows with full
  labels. Add local Indicators, Profile, and Sample & coverage tabs instead of
  first-three/Show all cards and vertically stacked secondary sections.
- Give Questions a full-width catalogue and a full-width reader with Back,
  Previous, Next, and Compare this question controls. Keep search, section, and
  sort local to the catalogue and preserve the return position. Categorical
  questions switch between Chart and Response table views.
- Keep complete filter wording, wrapping response choices, accessible group
  labels, and exact string-code selection. Bound the filter editor's height and
  start the comparison group chooser closed with selected groups in its summary.
  Disclose an active grouping filter and offer explicit removal.
- Label categorical response counts as raw **Interview n**, separate from
  **Weighted share** when weighting is active; disclose the weighted denominator.
- Add separate current-question/current-comparison and full-filtered-report print
  actions. Include printed sample, filter, weight, and currency scope. Full-report
  printing restores all configured charts and Overview views and includes the
  current comparison after Compare has been used. Catalogue search no longer
  hides complete-report charts. Restore the interactive view after printing.
- Revision 3 passed the complete portable gate and the review-fixture and
  readability-fixture DOM suites. Both examples regenerated with 240 rows,
  120 questions, and 117 chart panels. Real-browser rendering remains unavailable
  and unverified because cloud-browser policy blocked the preview; DOM checks
  use chart/map stubs and do not establish visual rendering. Licensed Stata
  execution remains unverified.

### Local review revision 2 — 2026-09-05
- Retain complete filter questions, response choices, and highlight labels instead
  of truncating them during HTML generation. Keep filter values and calculations
  unchanged, including distinct string codes such as `01` and `1`.
- Organize filters into separately labelled groups with wrapping response buttons
  and clearer selected states. Restore spacing around highlight content and use
  a balanced card layout, removing the green strip that overlapped text.
- Reduce the initial Overview to the first three configured highlights, with
  Show all for the rest. Keep the interview count once in the control bar and
  start sample composition, response coverage, and the profile table collapsed.
  This intermediate layout is superseded by revision 3's result rows and local
  Overview tabs.
- Add a generated-HTML regression fixture for long visible wording, accessible
  filter-group labels, and the existing exact-code toggle-button contract.
  Browser rendering remains unverified; the original cloud preview was blocked.

### Fixed
- Apply privacy-safe completion reduction consistently to text and media fields
  used only in filters or profile tables, as well as charted fields.
- Retain legitimate signed measurements and separate numeric inclusion rules
  from chart presentation. Discrete numeric highlights retain numeric units.
- Use the applicable sample denominator for completion percentages.
- Preserve distinct string group codes such as `01` and `1`, and allow explicit
  comparison groups beyond the initial category-discovery window.
- Preserve SurveyCTO question wording, underscore-prefixed user columns in CSV
  exports, and all-empty records in direct CSV input.
- Restore omitted configurator sources and assets so the current source can
  build the full engine, including the Stata bridge's heap guidance.
- Derive build/archive/test versions from `VERSION`, validate installed metadata,
  include every maintained workspace/configurator resource in package checks,
  and exclude Git history from source archives.
- Replace missing private default fixtures with clearly identified public
  synthetic fixtures exercising all existing structural contracts. Historical
  private-form count checks remain available as an explicit additional run.

### Changed
- Add compact Overview, Questions, Compare, and Map views, with shared filters,
  weighting and currency controls. Questions can be searched and inspected on
  demand, while the report remains available for printing.
- Keep data/statistical rules independent of the selected chart presentation.

## [2.3.0] — in development

### Fixed
- Pooled-scale datasets exhausted the Java heap while loading the CSV —
  31 million cells stored as 31 million separate string objects — so
  builds died with `OutOfMemoryError: Java heap space` inside Stata's
  default ceiling. The CSV reader now interns repeated cell values into
  shared strings: a 150,000-row regression fixture builds inside a
  deliberately capped 160 MB heap. When memory does run out, the bridge
  error now states the remedy (`set java_heapmax 4g, permanently`, then
  restart Stata), and the help file gained a Troubleshooting entry.
- Per-chart compare-by splits truncated high-cardinality groupers
  silently and arbitrarily: the first 14 levels in dataset order, which
  for a country-sorted pooled file meant Afghanistan through Belize.
  Splits now rank groups by weighted frequency, show the largest 20, and
  disclose the truncation in the stat line ("largest 20 of 40 groups",
  localized). For all-groups views, `tableby()` and country filters
  remain the right tools.
- Pooled legacy datasets (surveys aggregated from pre-Stata 14 files) can
  carry extended-ASCII bytes in value labels and string cells; the
  engine's strict UTF-8 readers died on the first such byte with the
  opaque `surveye: Input length = 1` (a Java MalformedInputException)
  before any status was written. Config and CSV reads now decode
  leniently (invalid bytes become U+FFFD), and the Stata wrapper
  additionally repairs label text with `ustrfix()` at the emission
  source, so a stray en dash from 2006 can never kill a 2026 build.
  Regression-tested with raw `0x96`/`0xE9` bytes in both files.

### Added
- `surveye configure using "questionnaire"` writes a self-contained,
  offline HTML configurator: the parsed questionnaire (Survey Solutions
  preview, SurveyCTO form definition, or SurveyCTO printable) opens as a
  searchable, section-grouped item list with type badges and repeat
  flags, and point-and-click builders assemble the full command —
  variable selection, chart-type overrides, variable groups offered only
  across compatible single-selects, comparisons with the 2–5 level rule
  enforced as you click, filters, weights, USD conversion, titles,
  byline, language, and theme — composing a ready-to-run `surveye`
  command line (copy or download as a .do file) plus an engine config TSV
  for advanced pipelines. Selections persist locally per questionnaire.

## [2.2.0] — 2026-07-29

Reader-facing feature release. Every addition works inside the existing
self-contained HTML output; no new Stata options and no runtime network
dependency are introduced. The release-specific engine is now
`surveye_2_2_0.jar`.

### Added — SurveyCTO questionnaires
- `surveye`, `surveye describe`, and `surveye demo` now accept SurveyCTO
  questionnaires alongside Survey Solutions previews, detected by content
  rather than file extension. The ODK XForm form definition (XML) is parsed
  precisely: titles, top-level groups as sections, nested groups as
  subsections, repeats (flagged), binds and types, inline choice items,
  itemsets over secondary instances, jr:itext translations (default
  language), notes (skipped), and calculate fields (charted when typed).
- SurveyCTO split select_multiple exports (`field_1`, `field_2`, ...) are
  reassembled into multiselect charts automatically; single_one filters,
  demo simulation, GPS candidates, and the profile table all work unchanged.
- The printable SurveyCTO HTML form is read directly, certified against a
  real production export: the printed Field/Question/Answer table, dark
  top-level group rows as sections, breadcrumb subgroup rows (including
  "(Repeated group)") as subsections with repeat scope, spacer-based
  nesting depth, nested choice tables with codes and labels, relevance
  captured as conditions, and hints kept out of labels. Printables do not
  encode field types, so choice fields import as select_one and open,
  note, or calculated fields as text; display-only notes drop
  automatically when no data column matches, and one warning explains all
  of it. Detection also recognizes the table layout structurally, so
  exports that never mention SurveyCTO by name still parse. Panel
  subtitles now show reader-facing type captions ("single-select",
  "numeric decimal", "choice list") instead of internal tokens.
  Unrecognized layouts fail with directions to the fully supported XML
  definition. `tests/run_surveycto_tests.sh` covers describe, build, demo,
  the printed table layout end to end, both negative paths, and Survey
  Solutions regression, and runs in CI.

### Documentation
- The README opens with a screenshot gallery (`docs/screenshots/`): the
  Sri Lanka 2026 informal-sector fieldwork dashboard masthead (forest
  theme, byline, live filter count), `surveye demo` on the full B-READY
  2025 Australia instrument with a simulated section of results, and an
  informal-sector module band whose chart values are simulated so no
  preliminary fieldwork results circulate.

### Added — in-place chart customization and presentation-ready exports
- Every chart carries a Customize popover (⚙): switch between bars and a
  donut where the two are statistically equivalent (single-select and
  yes/no cards), pick an accent color, scale chart fonts, and toggle value
  labels, rewrite the chart title in place — the edited title shows on
  the card, feeds the PNG download, and survives reloads — and split any
  eligible chart by a filter variable with "Compare by": a categorical
  indicator becomes one 100% composition bar per group (city, gender, ...)
  with a shared legend, and a numeric indicator becomes the median per
  group, honoring weights and the currency toggle. The split respects
  every active filter except the grouping variable itself, so filtering to
  one city never collapses a by-city view, and clearing the split restores
  the original chart.
- A Chart size control (Compact / Default / Tall / Extra tall) resizes any
  chart's drawing area, and a sizing engine grows the area automatically
  when a preference demands room: switching a slim yes/no card to a donut,
  or splitting it by a filter variable, now gets a properly sized chart
  instead of a squeezed arc or overlapping labels crushed into the
  42-pixel split-bar strip. Custom sizes persist, reset cleanly, and get a
  sane fixed height in print.
- The accent color now recolors every chart family: the affirmative series
  of yes/no cards and their compare-by splits (matched by the
  questionnaire's affirmative codes, not data order), the leading donut
  slice, the Answered side of completion cards, the date trend line, the
  primary (affirmative) series of grouped families, and the first level of
  configured comparisons — verified pixel-by-pixel across all nine chart
  kinds in a headless browser. Changes are
  presentation-only — estimates never move — apply instantly, persist per
  dashboard in the reader's browser, and can be reset per chart. Localized
  in English, Arabic, and Urdu; hidden in print.
- The per-chart PNG download was redesigned. It previously exported the
  bare canvas with a single clipped title line, so yes/no cards lost their
  headline percentage and legend (rendered in the page, not the canvas)
  and long questions were cut off. Downloads now compose a
  presentation-ready image at 2× resolution: the wrapped question title,
  a meta line with the variable name and live filtered statistics, the
  chart, the yes/no summary figure and legend where applicable, and a
  source footer with the dashboard title and date — all following the
  active theme and any per-chart customization. Labels are complete:
  titles wrap up to eight lines instead of clipping, bar axis labels wrap
  to three wider lines on comfortable density, donut downloads replace the
  space-shortened in-canvas legend with a full legend below the chart
  (every category written out with its share), and bar downloads append
  any label the axis still had to truncate, written in full. Export
  failures now surface on the browser console instead of failing
  silently.

### Fixed
- SurveyCTO printable forms cannot encode field types, so their open
  fields import as text; the Stata side has always sent each selected
  variable's storage type, `%t` format, and value labels, but the engine
  only consulted that metadata for variables absent from the
  questionnaire. Questionnaire-present questions now take those overrides
  too — gated so they only fill gaps: a printable's text-typed open field
  promotes to numeric, date, or labelled categories (disclosed as "typed
  …" in the panel caption), while types declared by an XML form or a
  Survey Solutions questionnaire remain authoritative and are never
  re-typed (a labelled numeric would otherwise flip a histogram into
  bars). Verified against SurveyCTO's rosters sample printable (JSI
  variant), whose colored-spacer depth encoding, `#8C8C8C` roster
  headers, and breadcrumb repeat markers are now a permanent parser
  fixture; the SurveyCTO suite grew to 9 checks.
- `release.sh` verified its archives with `unzip -Z1 | grep -q` pipelines:
  under `pipefail`, `grep -q` exiting at the first match kills `unzip` with
  SIGPIPE, so a file that was present could nondeterministically report as
  missing. Each archive is now listed once to a file and checked from that
  list. The SSC archive composition was also corrected to the official
  submission rules: only `surveye.ado`, `surveye.sthlp`,
  `surveye_2_2_0.jar`, `example.do`, `LICENSE`, and
  `THIRDPARTY-LICENSES.md` -- never `surveye.pkg` or `stata.toc` (the
  archive generates both) and never the GitHub-only generic `surveye.jar`.
  `tests/stata_smoke.do` now runs under `set varabbrev off`, matching the
  SSC requirement, and `SURVEYE_KEEP_STAGING=1` preserves release staging
  for inspection.
- Refreshing the GitHub repository through the web uploader (which never
  deletes files) could leave the retired `surveye_2_1_3.jar` and the old
  workflow in place, failing CI with a cryptic `cmp` byte difference.
  `tests/check_package.sh` now fails any stale `surveye_*.jar` with an
  explicit message and the web-UI removal steps, the workflow runs that
  check before the raw `cmp`, and `GITHUB_UPLOAD.md` documents the removed
  files, the hidden-`.github` caveat, and an exact `git` mirror recipe.
- The help file contained three physical lines over 244 characters, which
  Stata's viewer corrupts by dropping 8-byte chunks at 256-byte boundaries
  (rendering, e.g., `digital_channel` as `digi nel`), plus one `{it:...}`
  directive spanning a line break, which SMCL forbids. All example commands
  are rewrapped with `{cmd:...}` closed and reopened per physical line, and
  `tests/check_stata_source.sh` now fails on any help line over 244
  characters or any line that leaves an SMCL directive open.
- Runtime dark mode now works under every theme and finish: the editorial
  finish palette is scoped `:not([data-theme="dark"])` so toggling dark on a
  default (editorial) build no longer mixes the cream palette into the dark
  one, and dark-specific overrides were added for the sticky top bar, active
  section-navigation pill, pressed filter chips, the mobile filter toggle,
  and the benchmark-row rule — all previously navy-on-near-navy. Toggling a
  dark-built dashboard switches to the light World Bank base and back.

### Added — devices adopted from the ISES house style
- `byline("Label|Name|Role|Email")` signs the masthead cover with a task-team
  attribution block (label pill, name, role, mailto link) in build and demo
  modes; all parts optional, Arabic-script aware, styled for print.
- Emphasis syntax in `title()` and `subtitle()`: a pair of asterisks renders
  as a cyan accent in the title and bold in the subtitle, while the browser
  tab title and language detection see plain text.
- The stratum profile table gains table-lens micro-bars under every share
  cell (absolute 0–100 scale; gold on the benchmark row) and a filled-navy
  uppercase header row; both restyle in dark mode and flatten for print with
  forced bar colors.
- The top bar carries a dim gold track that the cyan-to-gold reading-progress
  bar fills as the page scrolls, and section-navigation hover picks up the
  cyan tint.

### Changed — visual identity
- The dashboard typeface is now Public Sans (SIL OFL 1.1), embedded as a 34 KB
  Latin variable-weight WOFF2 so files stay fully offline; true tabular
  numerals now apply to every KPI, table, and axis. Arabic and Urdu interfaces
  keep their Noto stacks, and the `typography(editorial|modern|system)` finish
  options remain available as explicit overrides.
- The hero is a deep-navy cover card with a faint sampling-grid graticule and
  an animated cyan-to-gold base rule; the same spectrum now forms the topline
  and the reading-progress bar. The sticky controls card straddles the cover's
  bottom edge.
- Warm archival paper (the SurvEye/ISES house `#FBFAF6`) with warm hairlines
  and soft fills, under navy-keyed ink and shadows — the print-report ground
  the navy cover and gold accents sit on. Chart data colors are unchanged.
  Section numerals render as navy grid cells; serif display type is replaced
  by heavy-weight sans throughout.
- Dark theme gains layered blue-black surfaces and a dark-specific cover
  treatment. Print output neutralizes the cover to ink-on-white; reduced-motion
  disables the rule animation; RTL mirrors the cover pattern and rule.

### Added
- **Light/dark theme switch.** A toggle in the sticky top bar lets readers
  switch between the built theme and the dark theme at any time. Charts,
  the profile table, and map point colors re-skin immediately, and the
  choice persists across reopenings when browser storage is available.
  Dashboards built with `theme(dark)` start dark and can be switched to
  light. Interface strings are localized in English, Arabic, and Urdu.
- **Shareable view links.** The active filters, the weighted/unweighted
  switch, the currency switch, and the search query are mirrored into the
  page URL. A **Copy view link** button in the control bar copies a link
  that reopens the dashboard in exactly the same state — useful when
  circulating a specific subgroup view by email or chat.
- **Filtered-data CSV export.** A **Download data (CSV)** button exports
  the currently filtered interviews. Categorical codes are written using
  their questionnaire labels, multi-select answers are joined with
  semicolons, the weight column is appended when the dashboard is
  weighted, and a UTF-8 byte-order mark keeps Excel rendering correct.
  Only data already embedded in the file is exported, so the feature adds
  no new disclosure surface.
- **Per-chart PNG export.** Each panel gains a small download control that
  saves the chart — composited on the current card background with the
  panel title — as a PNG named after the variable.
- **Expand all / Collapse all.** Two buttons at the end of the sticky
  section navigation open or close every section at once; helpful on
  large instruments built with `maxpanels(0)`.
- **Back-to-top button and reading-progress bar.** A floating
  return-to-top control appears after scrolling, and a thin cyan-to-gold
  progress bar under the top bar shows position within the document. Both
  are RTL-aware and hidden in print.
- **Keyboard shortcuts.** `/` opens the controls and focuses the
  indicator search from anywhere on the page; `Escape` clears an active
  search.

### Changed
- Chart palette colors are re-read from CSS custom properties whenever the
  theme changes, instead of being captured once at load.
- Editorial finish refinements: a restrained cyan/gold hero glow, gentle
  hover elevation on panels and KPI cards, softer section-summary hover
  states, styled thin scrollbars on internal scroll regions, and a
  brand-tinted text-selection color. All motion respects
  `prefers-reduced-motion`, and the new controls are excluded from print
  output.

### Packaging
- Version contract updated throughout: `surveye.pkg`, `stata.toc` layout,
  `MANIFEST.MF`, the ado wrapper, the help file, and the release checks
  now reference 2.2.0 and `surveye_2_2_0.jar`.

### Added

- Added the survey-agnostic `editorial` theme, now the default, based on the
  warm paper canvas, World Bank role colors, serif hierarchy, softened cards,
  and restrained finishing of the generic informality dashboard.
- Added independent `background()`, `typography()`, `corners()`, `shadow()`,
  `motion()`, and `pagewidth()` presentation controls for build and demo mode.
- Added a reduced-motion-safe Java appearance post-processor, portable tests,
  and a refreshed bundled example dashboard.

### Changed

- Removed the separate Survey Solutions branding and embedded-data strip, including its top rule, from every generated dashboard theme. The dashboard now begins directly with its main header and navigation.

### Compatibility

- Explicit `theme(worldbank)`, `theme(clean)`, `theme(forest)`, and
  `theme(dark)` retain their previous appearance unless a new layer option is
  supplied.  Appearance processing does not alter data, chart calculations,
  filters, weights, currency conversion, tables, or maps.

## [2.1.3] — 2026-07-21

### Added

- Added a runtime **Weighted estimates** switch whenever a native Stata weight
  is supplied.  Weighted results remain the default; readers can recalculate
  charts, comparisons, numeric summaries, and profile tables without weights
  while raw sample counts remain unchanged.
- Added a local-currency/USD switch configured by `usdvars()`, `usdrate()`, and
  `currency()`.  Local currency remains the default, and one documented fixed
  rate updates declared monetary charts, Stats, and profile-table summaries.
- Added a responsive side-by-side profile table configured by `tableby()` and
  `tablevars()`, with `auto`, `share`, explicit-code share, mean, median, and
  sum columns; optional column labels, title, subtitle, all-group reference
  label, and weighted-total heading; and live filter, weight, and currency
  refresh.  The table ignores only its own grouping filter so all comparison
  rows and the reference benchmark remain visible.

### Changed

- Removed Tukey fences, outlier counts, weighted outlier mass/share, whisker
  boxes, tail annotations, and outlier-specific colors from numeric cards.
- Numeric distributions now retain every valid measurement across their full
  plotted range.  A single dotted `Mean + 3 SD` reference is drawn only when
  the standard deviation is positive and the reference lies strictly inside
  that range.
- Kept wide integer supports readable with automatic equal-width,
  integer-aligned bins and regular numeric ticks, independently of
  `maxcategories()`.

### Fixed

- Recalculated every numeric Stats table from the current filtered rows even
  while its Stats tab is open and the lazily rendered chart canvas is hidden.
- Applied the same live filter refresh to unweighted and weighted summaries,
  including valid/missing counts, moments, quantiles, and the 3-SD reference.

## [2.1.2] — 2026-07-20

### Changed

- Made integer distributions independent of `maxcategories()`.  Exact bars and
  zero-frequency gaps are retained while readable; wider supports now use
  equal-width, integer-aligned bins automatically.
- Replaced categorical histogram axes with numeric linear axes and regular
  round-number ticks for both discrete and continuous distributions.
- Kept complete summary statistics while preventing extreme Tukey tails from
  flattening the visible distribution; low/high tail counts are shown at the
  corresponding plot edges.

### Fixed

- Prevented isolated extreme positive values from expanding an integer chart
  into hundreds of empty bars and producing labels such as `18, 79, 140, ...`.
- Added Arabic and Urdu interface text for smart integer bins, display ranges,
  and outlier-tail annotations.

## [2.1.1] — 2026-07-20

### Fixed

- Fixed quoted `comparelevels()` values in Stata.  Options such as
  `comparelevels("Male|Female")` now reach the engine as the two display labels
  `Male` and `Female`, instead of the literal fragments `"Male` and `Female"`.
- Fixed the same outer-quote handling for documented `vargroups("Title:: ...")`
  specifications.
- Added engine-side defensive quote normalization and regression coverage for
  labeled numeric comparison groups and quoted manual families.

## [2.1.0] — 2026-07-20

### Added

- Added automatic related-variable families for compatible binary letter-suffix items such as `srib8a srib8b srib8c`. Added `vargroups()`, `ungroupvars()`, and `noautogroups` for explicit placement, opt-out, and manual grouping.
- Added `compare()` with required `compareby()`, optional `comparetitle()`, and `comparelevels()` for full-width grouped horizontal comparisons of up to 12 binary indicators across two to five subgroups.
- Added exact-value displays for small integer counts and `discrete()`, `continuous()`, and `noautodiscrete` controls. Discrete charts retain zero-frequency integer gaps, show a weighted median, flag Tukey outliers, and keep a complete Stats tab.

### Changed

- Gave grouped families and comparisons a symmetric full-width layout with direct percentage labels, stable colors, subgroup-specific valid denominators, and native Stata-weight support.
- Kept confidence intervals off binary family and subgroup-comparison panels, including when `ci` is requested; ordinary eligible categorical bars retain opt-in intervals.
- Balanced incomplete card rows into deliberate three-, two-, or one-card compartments and retained compact chart heights.
- Replaced the tall sticky filter block with a compact disclosure toolbar that starts collapsed and preserves active filters when closed.
- Set normal Chart.js transitions to 800 milliseconds and delayed chart construction until a panel is visible so animations do not finish offscreen. Reduced-motion users still receive no animation.

### Fixed

- Replaced Survey Solutions' generic calculated-variable labels with actual variable names when no meaningful label exists.
- Excluded questionnaire-declared negative special codes and configured missing codes from numeric distributions, medians, statistics, outlier detection, and numeric filter choices while retaining them in categorical figures.
- Corrected custom numeric value-label lookup so attached label definitions no longer raise Stata `r(111)`.
- Applied `maxpanels()` only after family construction so a grouped panel is never split, and made explicit comparisons take precedence over automatic grouping.

### Compatibility

- Existing 2.0 syntax remains valid. Automatic grouping is conservative and affects only compatible binary suffix families; use `noautogroups` to retain one panel per variable.

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
- Retained the compact visuals, custom-variable placement, optional confidence intervals, outlier-aware Stats tab, and Leaflet/Google map behavior introduced in 1.2.0.
- Localized generated Additional indicators, Other indicators, and untitled Key message headings instead of leaving English fallback text in Arabic or Urdu dashboards.

### Fixed

- Corrected the public and parser-facing weight order to Stata's `using questionnaire.html [weight], options` grammar and added a static regression guard for every shipped example.
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
