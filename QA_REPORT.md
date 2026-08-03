# QA report — SurvEye

## 2.2.0 delta review — 2026-07-27

Scope: reader-facing feature release (runtime theme switch, shareable view
links, filtered-data CSV export, per-chart PNG export, expand/collapse-all,
back-to-top with reading-progress bar, `/` and `Esc` keyboard shortcuts) plus
editorial CSS polish and the 2.2.0 version-contract update. No parser, weight,
currency, numeric-statistics, or map calculation paths were changed.

Checks completed against the 2.2.0 candidate:

| Check | Result |
|---|---|
| `node tests/test_statistics.js` | **PASS** — statistics runtime unchanged; the engine still loads cleanly in the DOM-free test sandbox with the new features present but inert before `init()`. |
| Clean Java 8-compatible build | **PASS** — `--release 8` build; released class files remain major version 52. |
| `tests/check_package.sh` | **PASS** — manifest, 2.2.0 version contract, generic/release JAR identity (`surveye_2_2_0.jar`), resources, class level, namespace. |
| `tests/check_stata_source.sh` | **PASS** — ado syntax, weight grammar, option handoff, status parsing, 2.2.0 release JAR name, help/package contracts. |
| `tests/run_engine_smoke.sh` | **PASS — 14/14** against the bundled fixture questionnaire, including CLI path-collision protection. |
| DOM runtime smoke (jsdom) | **PASS** — generated dashboard initializes with zero page errors; theme toggle flips `data-theme` both ways with correct `aria-pressed`; filter clicks, search input, and reset round-trip through the `#view=` hash; a preset `#view=` URL restores chips, search, matched-`n`, and reset state on load; CSV export emits header + one row per filtered interview with labelled categories and a `.csv` filename; expand/collapse-all toggles every section. |
| Pooled-scale robustness (GLOBAL shape) | **PASS** — full 308k×101 replica: legacy `0x96`/`0xE9` bytes build cleanly (was `Input length = 1`); `-Xmx1g` OOM reproduced pre-fix and cell interning now fits 150k×30 inside a capped 160 MB heap (smoke check); compare-by on a 40-level country grouper renders the weighted-largest 20 at 636px with the truncation disclosed in the stat line, heaviest economy ranked first, zero page errors. |
| Configurator (2.3.0, browser e2e) | **PASS — 18/18 + engine round trip** — on the full-coverage SurveyCTO form: the browser lists 16 items with repeat fields disabled and the correct source badge; the group picker refuses incompatible option sets; a clicked-together setup (10 variables, donut override, two filters, a labelled group, a two-member comparison by gender, pweight, USD at 300 LKR) composes a syntactically correct `surveye` command in questionnaire order with a green Ready state, survives reload, and its Engine-config TSV drives the real engine to an 8-kind weighted USD dashboard. Emission verified for all three questionnaire sources (4-check jar suite). |
| Cross-source parity: Survey Solutions vs SurveyCTO (browser e2e) | **PASS — 53/53, run twice** — the full customization matrix now also runs on a SurveyCTO XForm-built dashboard carrying all ten panel kinds (bar, multibar from genuine split `services_1..3` columns, yesno, completion, donut, hist, discrete, date, family, comparison) under pweights with gender and region filters: accent and chart-size verified per kind, a yes/no card split by Gender sized and accent-matched on the affirmative series, and a customized chart exported. The real deployed ES printable (118 fields) builds end-to-end in demo mode: 64 panels, popover with all six controls, clean export, zero page errors. |
| Full customization matrix (browser e2e) | **PASS — 29/29** — with all nine chart kinds live on one dashboard (bar, multibar, yesno, completion, donut, hist, discrete, date, family, comparison): the accent recolors every kind (pixel-verified) and size=tall grows every chart area to 260px; the two production regressions reproduce and pass fixed — a yes/no card switched to a donut grows to 205px with a real ring, and a yes/no split by City gets 258px with one readable row per city, the affirmative series taking the accent, and a clean export (six city bands, 6,750 accent pixels). |
| Compare-by splits (browser e2e) | **PASS — 8/8** — on the informal replica with city and activity filter chips: the popover offers the filter variables; splitting the profit/loss card by City rebuilds it as six stacked composition bars (multiple category hues, donut hole gone) with the stat line naming the group; the preference persists and the grouped chart exports (six vivid row bands verified); a numeric indicator splits to medians by City and clearing the split restores the original histogram statistics. jsdom suite now 36 assertions incl. the compare-by round trip. |
| Label completeness + editable titles (browser e2e) | **PASS — 4/4 + pixels** — an a4s replica (180-char question, six categories incl. a 149-char label): the donut download wraps the full title over four lines and draws a complete six-dot legend below the chart; switching to bars via the popover, editing the title in place, and re-exporting works; the edited title survives a full reload; the bars download appends the still-truncated label in full (four appendix text rows). |
| Chart customization + export (browser e2e) | **PASS — 9/9** — headless-Chromium run on the feature preview: popover opens; accent change rebuilds bars in the chosen color (pixel-verified 216,27,96); bars→donut renders with a transparent center; preferences persist to localStorage; three panel PNGs download with substance; and the true yes/no export from the informal replica composes title, split bar, headline percentage, legend, and footer (zone-by-zone pixel fingerprints). jsdom suite grew to 30 assertions covering the popover round trip. |
| SurveyCTO rosters variant + printable auto-typing | **PASS — 9/9 suite** — SurveyCTO's rosters sample (JSI variant: group depth encoded by colored spacer cells, roster headers on `#8C8C8C`, repeat markers on breadcrumb rows) parses to ground truth (5 questions, 2 sections, consent chartable, roster fields repeat-scoped); with data, the roster-count open field promotes to a discrete chart via Stata metadata with a “typed numeric” caption, browser-verified (compare-by split by consent, clean export, zero page errors); XForm and Survey Solutions dashboards rebuilt unchanged (10 and 9 kinds). |
| SurveyCTO printable certification | **PASS** — a real 222 KB production printable parses to its full 118 fields across 3 sections (64 choice fields chartable), with breadcrumb subsections (e.g. Consented · Non-Covid Health), repeat groups flagged, relevance captured, hints excluded from labels, and a simulated demo dashboard built from it with 64 panels and clean type captions. |
| SurveyCTO support | **PASS — 7/7** — XForm describe (itext sections, counts, GPS candidate), end-to-end build from a split-column export (multi reassembly, itemset and inline choice labels, calculate field, select_one filter chips), demo simulation from the XML alone, printable-HTML heuristic describe with its best-effort flag, both negative paths (neutral file and unrecognized SurveyCTO page), and Survey Solutions regression. |
| Stale-jar guard | **PASS** — planting a `surveye_2_1_3.jar` makes `tests/check_package.sh` fail with the named diagnosis and web-UI removal steps; removing it restores PASS. CI now runs this check before the raw `cmp` so upload mistakes are self-explanatory. |
| SMCL help contracts | **PASS** — no physical line over 244 characters and no directive spanning a line break remain in `surveye.sthlp`; both conditions are now enforced by `tests/check_stata_source.sh`. |
| Theme-toggle matrix | **PASS** — jsdom round-trips on a forest build (forest → dark → forest), a dark build (dark → light base → dark, with correct `aria-pressed`), and an editorial-finish build (dark toggle preserves `data-surveye-finish` while the scoped finish palette yields to the dark variables); zero page errors in all three. |
| ISES-device adoption | **PASS** — byline block, `*emphasis*` title/subtitle rendering with a plain browser-tab title, profile-table lens bars (present after load and after `#view=` restore, percentage widths, benchmark row included), and the navy header row all assert green in the DOM smoke; the Arabic demo build renders an Arabic byline label under `dir="rtl"`. |
| Visual identity build | **PASS** — embedded Public Sans @font-face present in output with `font-src data:` allowed by the CSP; hero cover markup (`.hero-inner`) renders; no stale `.hero:before` rules; RTL build mirrors the cover pattern and spectrum rule; print block neutralizes the cover. |
| Localization | **PASS** — all 14 new interface strings supplied in English, Arabic, and Urdu; an Arabic `uilanguage(arabic)` build renders the translated controls under `dir="rtl"`. |

Environment-dependent checks not repeated in this pass (unchanged code paths;
last completed for 2.1.3): the four-questionnaire parser regression (external
fixture previews), the connected-browser tile check, the licensed-Stata
installation test, and the full `tests/visual_qa.js` viewport matrix. The
visual QA harness should be re-run before SSC submission because this release
adds interface elements it can begin asserting on (`#theme-toggle`,
`#copy-view`, `#download-data`, `.panel-export`, `#back-to-top`).

---

# QA report — SurvEye 2.1.3

Release-candidate review date: 2026-07-21

## Outcome

All portable release checks completed for this candidate passed. The parser,
Java engine, JavaScript statistics runtime, deterministic browser QA, Stata
source contracts, package manifest, and rebuilt JAR integrity are internally
consistent with SurvEye 2.1.3. No release-blocking defect is known from those
checks.

This report records only checks that were completed against the current
candidate. A licensed-Stata installation test and a connected-browser check of
live Google/OpenStreetMap tiles remain environment-dependent release checks;
they are listed separately below and are not represented as completed. The
portable browser run intentionally blocked tile traffic while exercising every
local dashboard interaction.

## Change-specific acceptance

| Area | Verified 2.1.3 behavior |
|---|---|
| Filtered numeric Stats | Distribution and Stats views use the same current filtered rows. A visible Stats tab refreshes immediately after a filter changes, including weighted summaries. |
| Numeric distributions | Every valid measurement remains in the plotted range and summary. No outlier classification, range clipping, outlier count/mass, whisker overlay, or edge annotation is produced. A single dotted **Mean + 3 SD** guide is drawn only when the standard deviation is positive and the reference lies strictly inside the observed plotted range. |
| Runtime weights | Supplying native Stata `[aw=]`, `[fw=]`, `[iw=]`, or `[pw=]` adds a **Weighted estimates** switch. It starts on. Turning it off recalculates charts, comparisons, numeric summaries, and the profile table with one unit per retained row; raw sample counts remain raw. Both modes use the analysis sample established when the dashboard was built. |
| Runtime currency | `usdvars()`, `usdrate()`, and optional `currency()` add a local-currency/USD switch. Local currency is the initial display. `usdrate()` is interpreted as local-currency units per USD, and conversion occurs at render time without changing embedded source values or the analysis sample. |
| Profile table | `tableby()` and `tablevars()` create a responsive, live summary table. `tablestats()` accepts aligned `auto`, `share`, `share:<code>`, `mean`, `median`, and `sum` entries; variable labels are the default headings and `tablelabels()` can override them. Raw `n` is always shown; the weighted-total column appears only in weighted mode. The all-group reference row is always present and `tabletotal()` customizes its label. |
| Profile-table filters | The table deliberately ignores only its own `tableby()` dashboard filter so all comparison rows and the all-group benchmark remain visible; selected grouping rows may be highlighted. Every other active filter, plus the current weight and currency settings, is honored. |
| Packaging | The ado, help, package metadata, manifest, generic JAR, and release-specific `surveye_2_1_3.jar` use the 2.1.3 product/version contract. The generic and release-specific JARs are byte-identical after a clean build. |

## Attached questionnaire coverage

The parser regression completed **4,994 assertions** across the four supplied
Survey Solutions questionnaire previews.

| Questionnaire | Sections | Questions | Options | Parser warnings | Default panels |
|---|---:|---:|---:|---:|---:|
| Australia ES B-READY 2025 — English | 21 | 399 | 975 | 0 | 100 |
| Global informal 2026 — English | 3 | 212 | 574 | 0 | 100 |
| Global informal 2026 — Sinhala | 3 | 212 | 574 | 0 | 100 |
| TRG 2025 — English | 8 | 87 | 90 | 0 | 45 |

The English and Sinhala informal previews retain matching structural metadata.
Sinhala Unicode content survives parsing and rendering. The Australia preview
can expose all chartable panels with `maxpanels(0)`; the ordinary 100-panel cap
remains a presentation limit rather than a parser limit.

## Completed checks

| Check | Result |
|---|---|
| Questionnaire parser suite | **PASS** — 4,994 assertions across the four attached questionnaires. |
| `node tests/test_statistics.js` | **PASS** — JavaScript statistics, filters, weighting state, full-range numeric plans, conditional 3-SD guide, runtime currency conversion, and profile-table behavior. |
| Clean Java 8-compatible build | **PASS** — current Java sources and maintained dashboard resources rebuilt successfully. |
| Build/JAR integrity | **PASS** — archive integrity, manifest identity, Java bridge, embedded resources, and source/JAR resource byte matching. |
| `tests/check_stata_source.sh` | **PASS** — Stata syntax, native weight grammar, option handoff, metadata export, sample marker, status parsing, release JAR name, help/package contracts, and retired-name guards. |
| `tests/check_package.sh` | **PASS** — package manifest, release version, generic/release JAR identity, archive/resources, Java 8 class level, canonical namespace, and package safety checks. |
| `tests/run_engine_smoke.sh` | **PASS** — full engine and status-file smoke, including `StataEntryPointSmokeTest`, `StataBridgeLinkageTest`, `DashboardI18nTest`, `WeightContractTest`, `TableCurrencyContractTest`, `DateFormatMetadataContractTest`, `CompletionMissingContractTest`, `MapFilterLocalizationContractTest`, `CalculatedVariableLabelContractTest`, `CompositePanelContractTest`, `NumericDistributionContractTest`, and CLI path-collision protection. |
| Browser/visual QA | **PASS — 8/8 viewport runs** across the targeted control/table dashboard and regenerated public sample at 1440×1000, 1536×640, 1024×1366, and 390×844. The 800 ms animation lifecycle, responsive layout, collapsed controls, weight and USD recalculation, profile-table columns/reference rows, table-own-filter behavior, live Stats filtering, search, filter/reset, 300 individual map points, chart rendering, and overflow checks all passed with no page/console errors or unexpected network requests. |

The package JAR SHA-256 is intentionally not fixed in this report. It must be
recorded from the final post-edit rebuild because ZIP entry timestamps can
change the digest even when source content is unchanged.

## Retained regression coverage

The full engine smoke continues to exercise the major established contracts:

- Survey Solutions current, legacy, English, and translated printable HTML;
- UTF-8 CSV parsing, raw codes, multiline fields, strict ragged-row rejection,
  zero-padded codes, multiselect denominators, and explicit missing codes;
- custom variables, calculated-variable label fallbacks, manual and automatic
  related-variable groups, subgroup comparisons, discrete and continuous
  numeric paths, Arabic/Urdu interface resolution, and right-to-left metadata;
- native Stata a/f/i/p weight validation, zero/missing-weight exclusion,
  integer fweights, negative-weight errors, and iweight CI suppression;
- numeric special-response exclusion without removing legitimate values;
- Stata date metadata, privacy-safe completion fields, typed filters,
  Admin-2 geometry, point-map metadata, and configured-missing map groups;
- the fully qualified `org.worldbank.surveye.StataPlugin.stata(String[])`
  bridge, fallback status files, protected paths, and CLI configuration safety.

## Environment-dependent checks still required

### Licensed Stata

A licensed Stata executable is not available in the portable build
environment. Before publication, run the bundled `tests/stata_smoke.do` under
Stata 16 and the newest Stata release intended for support. This is the final
check for `net install`, native option parsing, `javacall`, temporary files,
status-file parsing, returned `r()` values, and browser opening on Windows.

### Connected browser and live map tiles

Leaflet, base-layer definitions, point metadata, and boundary geometry are
covered by portable contracts. A connected browser must still confirm live
Google Hybrid/Satellite/Road and OpenStreetMap imagery, provider attribution,
layer switching, and any organization-specific network policy in the intended
deployment environment. Tile availability belongs to the external provider
and is not guaranteed by the embedded dashboard.

## Required Stata release smoke test

Run from a clean package staging directory:

```stata
net install surveye, from("LOCAL_OR_RELEASE_URL") replace
discard
which surveye
findfile surveye_2_1_3.jar
help surveye

surveye describe using "English TRG_2025.html", detail
return list

use "survey_data.dta", clear
surveye selected_var1 selected_var2 if completed == 1 ///
    using "questionnaire.html", ///
    saving("smoke.html") filters(region) ///
    highlights(selected_var1) ///
    keymessages("QA::Release smoke test") ///
    diagnostics("smoke.txt") replace open
return list

surveye selected_var1 using "questionnaire.html" [aw=wmedian], ///
    saving("smoke_aw.html") replace
surveye selected_var1 using "questionnaire.html" [fw=frequency], ///
    saving("smoke_fw.html") replace
surveye selected_var1 using "questionnaire.html" [iw=importance], ///
    saving("smoke_iw.html") replace
surveye selected_var1 using "questionnaire.html" [pw=pop], ///
    saving("smoke_pw.html") replace
```

Exercise the new weight, currency, and profile-table runtime together. Replace
the example variables and fixed exchange rate with fields appropriate to the
test dataset:

```stata
surveye women_led sales banked digital_channel ///
    using "questionnaire.html" [pw=pop], ///
    saving("smoke_table_usd.html") ///
    filters(stratum owner_type) ///
    usdvars(sales) usdrate(300) currency(LKR) ///
    tableby(stratum) ///
    tablevars(women_led sales banked digital_channel) ///
    tablestats("share:1|median|share:1|share:1") ///
    tablelabels("Women-led|Median sales|Banked|Digital channel") ///
    tabletitle("Stratum profile") ///
    tablesubtitle("Release smoke test") ///
    tabletotal("All surveyed locations") ///
    tableweightlabel("Est. firms") replace open
```

In the generated file verify that:

1. **Weighted estimates** starts on, changes the estimates when switched off,
   and does not change raw `n`;
2. the USD switch starts off, converts `sales` by `local value / 300`, and
   restores the local values exactly;
3. another dashboard filter refreshes charts, an already-open numeric Stats
   tab, and every table cell;
4. selecting a `stratum` filter highlights the relevant table row but does not
   remove the other strata or alter the all-location benchmark universe; and
5. the profile table's weighted-total column disappears in unweighted mode and
   returns in weighted mode.

Run the bundled deterministic wrapper regression after installation:

```stata
do "tests/stata_smoke.do" "C:/path/to/extracted/surveye-2.1.3"
```

Also inspect one Arabic or Urdu RTL dashboard at desktop and mobile widths and
one point map with duplicate coordinates and live base-layer switching.

## Publication note

Generated dashboards embed the selected row-level values required for
interactivity. Treat each HTML file like the source analysis data, particularly
when it contains GPS coordinates. Record the final package/JAR checksums only
after the last clean rebuild and verify that the GitHub package manifest points
to `surveye_2_1_3.jar` before release.
