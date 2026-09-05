# Example output

The current Admin-2 review preview is `surveye_admin2_review.html`.
The country-outline-only companion is `surveye_review_dashboard.html`. It uses
240 synthetic interviews and 120 questionnaire items to exercise the original
single-page layout with the bug fixes. It includes the original header, filter
controls, highlights, profile table, map, and chart sections. No real survey
responses are included.

Rebuild the current preview from the repository root:

```bash
python3 tests/review_demo_generate.py
mkdir -p build
java -jar surveye.jar --config tests/review_demo_config.tsv
```

The fixture generator is deterministic. `tests/review_demo_expected.json`
contains independently calculated reference values, and
`tests/README_REVIEW_DEMO.md` explains the sample and assumptions. The fixed
exchange rate is a demonstration setting, not a current market rate.

The older `sample_dashboard.html`, `feature_preview_2_2_0.html`, and
`surveycto_demo_preview.html` are historical generated examples. Regenerate
old reports with the current engine to receive the data and calculation fixes.
The configuration `tests/sample_dashboard_config.tsv` now uses the included
synthetic Australia questionnaire and no longer needs a private input file.

The preview embeds Chart.js, Leaflet, its synthetic records and boundary
geometry. Google/OpenStreetMap background tiles require an internet connection;
the synthetic GPS points remain part of the local HTML file.

For a localized output, copy the review configuration and add `uilanguage` and
`direction` rows, or use the corresponding Stata options:

```stata
surveye using "questionnaire_ar.html", saving("dashboard_ar.html") ///
    uilanguage(ar) direction(auto) replace

surveye using "questionnaire_ur.html", saving("dashboard_ur.html") ///
    uilanguage(ur) direction(rtl) replace
```

Native Stata weights follow the `using` filename and precede the comma, for
example `[pw=pop]`. Confidence intervals are off by default; `ci level(90)` opts
in for eligible categorical/multiselect bars. Binary, completion, and donut
cards remain CI-free. Importance weights produce descriptive estimates and
suppress requested intervals.

## Supplied World Bank Admin-2 archive

From the repository root, regenerate the current district preview using the
actual archive path (standard-library Python helper plus Java):

```bash
python3 tests/generate_admin2_review.py "/path/World Bank Official Boundaries - Admin 2_mac.zip" --replace
```

`tests/review_admin2_data.csv` retains the original synthetic survey responses
and weights but uses new invented land coordinates in the corresponding four
Sri Lankan provinces. Locations were generated with seed 23220260905 and checked
against the supplied geometry. The original synthetic CSV was not overwritten.
`review_admin2_config.tsv.in` is a template, not a ready-to-run config: the helper
substitutes the explicit archive path and writes the temporary config under build/.
