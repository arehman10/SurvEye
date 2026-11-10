# SurvEye 2.3.1 — original layout, bug-fix review candidate (revision 5)

The original shipped single-page layout is restored. The proposed workspace,
new navigation tabs, question reader, and alternate overview layout are removed.
The header, compact controls, highlights, profile table, map, section navigation,
chart grids, numeric Stats tabs, themes, customization, and export controls are
in their original locations. The stylesheet matches the original 2.3.0 JAR
byte for byte. Existing options still control your report's theme and content.

This is an unpublished local candidate. No commit, GitHub push, deployment, or
SSC submission has been made. The original repository remains unchanged.

## Review the original report with the fixes

Open `examples/surveye_review_dashboard.html` in your browser. It uses 240
invented interviews, 120 questions, and 117 chart panels across six sections.
Every response, weight, and location is synthetic. These are software examples,
not survey findings about Sri Lanka. LKR 300 per USD is an assumed demonstration
rate, not a current exchange rate.

Use the original filters, question search, section navigation, Stats tabs,
weight/currency toggles, and Download data control. The comparison charts
configured by the existing command options remain in the original chart grid.
Search again filters the visible chart report. Printing keeps the original
layout and restores each section and the map to its prior open/closed state.

Regenerate an existing dashboard with this engine to apply the bug fixes;
previous HTML files retain their embedded code.

## Test with your Stata data

Extract the complete package first. Use the absolute folder containing
`surveye.pkg`; do not install from inside the ZIP or from GitHub for this review.
For example:

```stata
net install surveye, from("C:/SurvEye-review") replace
```

Restart Stata if an earlier SurvEye Java engine has already been loaded. The
starter do-file takes questionnaire, data, and output paths:

```stata
do "C:/SurvEye-review/example.do" ///
    "C:/my_project/questionnaire.html" ///
    "C:/my_project/survey_data.dta" ///
    "C:/my_project/dashboard_review.html"
```

Use a new output filename while reviewing. Your existing do-file can also use
the new installation; the layout does not require new command options. The
questionnaire may be Survey Solutions HTML, SurveyCTO XML, or SurveyCTO printable
HTML. `example.do` includes recipes for adding your own filters, highlights,
profile table, weights, currency conversion, and map.

## Audit fixes included

All eleven numbered audit findings have corresponding changes and regression
coverage:

1. Text/media privacy reduction applies to filters and tables as well as charts;
   using a field in a different view cannot expose its raw text.
2. Legitimate negative measurements remain included. Question wording and chart
   selection no longer silently exclude them.
3. Missing configurator source, resources, translations, and shipped behavior
   were recovered so the full release can be rebuilt from source.
4. Text-completion percentages use the full applicable sample denominator,
   including when displayed in profile tables.
5. Distinct string group codes such as `01` and `1` remain distinct in comparisons
   and table selection. This also covers questionnaire-defined options that
   would collide under numeric normalization, while retaining unambiguous
   padded-code/numeric-data compatibility.
6. Discrete numeric highlights use numeric summaries with a named statistic.
7. Explicit comparison levels can be selected beyond the first seven observed
   categories.
8. SurveyCTO XML input questions retain their wording.
9. CSV exports retain legitimate underscore-prefixed user variables.
10. Direct CSV input retains delimited records whose fields are all missing;
    genuinely blank physical lines remain ignored.
11. Builds, package filenames, and validation scripts derive the release version
    from `VERSION`, replacing obsolete release assumptions.

The three smaller reproduced defects are fixed: Stats retains a valid Mean +
3 SD value outside the drawing range; a single row with frequency weight greater
than one yields the same SD as its expanded observations; and large discrete
distributions avoid JavaScript argument-limit failures.

Additional corrections prune data from panels removed by `maxpanels()` when no
remaining filter, table, map, highlight, or comparison needs it. Grouped text
completion no longer uses an undefined chart color, and grouped monetary
medians convert to USD exactly once. Restored shipped behavior includes the
configurator, tolerant text decoding, pooled CSV values, printable-form type
inference, and Stata heap guidance. Configurator output/input collision
protection prevents overwriting source inputs.

## Verification and remaining limits

| Check | Result |
|---|---|
| Original layout | Source inspection passed: original shipped CSS is byte-identical; original renderer structure restored; no workspace resources or shell remain. |
| Portable Java/Node release gate | Passed in the release gate for revision 5: package/source integrity, statistics, parser assertions, Java audit contracts, source recovery, SurveyCTO, and appearance contracts. |
| Original-interface DOM suite | Passed for the rebuilt review fixture: original structure, exact category filtering, search/reset, Stats, weights, USD, shared views, CSV export, and repeated print preparation/restoration. Chart.js and Leaflet are stubbed. |
| Browser rendering | Unverified. Browser access was blocked by policy; DOM checks do not establish actual chart rendering, map tiles, responsive geometry, or print appearance. |
| Licensed Stata execution | Unverified: licensed Stata is unavailable. Portable checks cover Stata source and Java bridge contracts. |
| Production survey data | Unverified: the demonstrations use synthetic data. |

## Reproduce the build and checks

From the extracted repository root, with a JDK, Node.js, and Python:

```sh
bash build.sh
sh tests/run_all.sh
python3 tests/review_demo_generate.py
mkdir -p build
java -jar surveye.jar --config tests/review_demo_config.tsv
java -jar surveye.jar --config tests/review_demo_privacy_config.tsv
npm --prefix tests install
npm --prefix tests run test:dashboard
```

With dependencies installed elsewhere:

```sh
SURVEYE_DOM_NODE_MODULES="/absolute/path/to/node_modules" \
    node tests/test_dashboard_dom.js
```

The privacy example is written to `build/review-demo-privacy.html`; raw sentinel
prefixes in `tests/review_demo_expected.json` must not appear in either output.
The synthetic employment change has 80 values each of -5, 0, and 5, so its
unweighted mean is 0 and valid n is 240. Notes are answered in 80 of 240 rows,
so unweighted completion is 33.3%. The independently calculated reference file
also includes group counts and numeric summaries.

The optional DOM suite and licensed-Stata/browser checks are separate from
`run_all.sh`. A licensed Stata smoke run after local installation is:

```stata
do "C:/SurvEye-review/tests/stata_smoke.do" "C:/SurvEye-review"
```
