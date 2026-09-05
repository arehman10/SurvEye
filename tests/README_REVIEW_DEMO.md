# Public synthetic review dashboard

Every response, weight, firm, and map point in this fixture is invented. The
figures are software illustrations, not Enterprise Survey findings, fieldwork
results, or estimates about Sri Lanka. The LKR 300 per USD conversion is a fixed
demonstration assumption, not a current exchange rate.

From the repository root, after building the review engine:

```sh
python3 tests/review_demo_generate.py
mkdir -p build
java -jar surveye.jar --config tests/review_demo_config.tsv
```

Open `examples/surveye_review_dashboard.html` locally. The Python generator uses
only the standard library. Its fixed seed is `23010904`; rerunning it writes
identical fixture files. The HTML build itself includes its generation time.

The fixture includes 240 rows and 120 meaningful questions across six sections.
The comparison combines four indicators, leaving 117 panels. `maxpanels=0`
deliberately keeps all panels so navigation and search can be checked with a
questionnaire longer than the default 100-panel limit. All 240 synthetic points
are inside the bundled Sri Lanka outline. Detailed internal boundaries are not
included. The outline and locations are embedded; background map tiles require
internet access.

Useful review actions:

- Inspect the original report, jump between chart sections, and expand or
  collapse them. Select a region and sector and confirm that the sample size,
  highlights, profile table, comparison charts, and map agree.
- Switch weighted estimates off and compare with
  `tests/review_demo_expected.json`. Sales and employees should use a named
  numeric statistic, and bank-account and female-owner indicators a percentage.
  Change to USD and confirm sales divide by 300 while headcounts do not change.
- Search for `employment_change`. Its 240 unweighted values are exactly 80 each
  of −5, 0, and 5: the mean is 0 and valid n is 240. Its question wording includes
  “number of employees” to exercise the legitimate-negative-value regression.
- Review the region comparison. Raw region codes `01` (Western) and `1`
  (Central) are deliberately distinct, with different bank-account rates.
- Open a multiselect, an ordinal obstacle question, a count distribution, and a
  text-completion question. Missing answers are present by design. A text note
  is provided in exactly one-third of rows; the other two-thirds are blank.

The auxiliary privacy fixture adds unselected text fields used only in a filter
or profile table:

```sh
java -jar surveye.jar --config tests/review_demo_privacy_config.tsv
```

Its output is `build/review-demo-privacy.html`. Raw notes start with
`SYNTHETIC_PRIVATE_NOTE_`, `SYNTHETIC_FILTER_NOTE_`, or
`SYNTHETIC_TABLE_NOTE_`. None of those prefixes should appear in either generated
dashboard. The table-only note has unweighted completion 33.333…%, not 100%.
The original CSV contains these deliberately non-sensitive sentinel strings so
privacy reduction can be verified against a known source.

`review_demo_expected.json` records unweighted reference counts, means, medians,
the signed-change reference, and sentinel prefixes. The generator and fixture
were exercised with the shipped engine to verify parser compatibility before
the review build. Final layout behavior and fixed-engine results should be
verified against the rebuilt review engine; generation alone does not establish
that browser interactions are correct.
