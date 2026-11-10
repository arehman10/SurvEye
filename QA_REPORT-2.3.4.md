# SurvEye 2.3.4 — Windows/Stata and Chrome validation

Tested 19 September 2026. Ready within the tested **Windows, StataNow/MP 19.5, and Chrome scope**, with the limits below. The supplied ZIP remains unchanged; the prior report is preserved in [QA_REPORT-2.3.3.md](docs/history/QA_REPORT-2.3.3.md).

## Environment and scope

Windows/OneDrive paths, including spaces/Unicode; StataNow/MP 19.5; Zulu JDK 21.0.12; Java 8 compilation target; Node 24.19.0. Synthetic data/boundaries. Live Chrome used five local HTTP theme previews, each with two indicators, at desktop 1366 × 768 and mobile 390 × 844.

## Corrections

Earlier fixes covered relative paths, literal text, option declarations, quoting/pipe lists, table semantics, logos, configurator validation, and package consistency; details remain in the archived report.

The 2.3.4 browser review added these fixes:

- Stats now updates its headline, hover title, and accessible description with the visible table. Returning to Distribution restores grouped-chart metadata through the normal lazy rebuild.
- Done commits an edited chart title before closing. Clipboard fallback reports success only when copying succeeds and always removes its temporary field.
- Customization popups receive a viewport height cap and position clamp, retaining internal scrolling.
- Editorial UI blue/green retain readable contrast while the bright chart palette stays unchanged. The obsolete 13.75px title override was removed; shared 15px/comfortable 16px sizing applies. Dark customization text uses the light ink color.

## Final executed gates

All results below are fresh 2.3.4 results. Logs are under `build/qa_windows/`; test scripts are under `tests/`.

| Gate | Result | Evidence |
| --- | --- | --- |
| Java engine, package, appearance, reader actions | PASS, 28 groups | `engine-20260919-013533/` |
| Licensed Stata smoke and full option matrix | PASS, 19 smoke scenarios and 289 matrix cases | `run-smoke.log`, `stata_matrix_results.tsv` |
| Edge/state/reproducibility, files, GPS, SurveyCTO | PASS, including 18 focused edge cases | `stata_extended.log`, `stata_files.log` |
| Generated semantic contracts | PASS, 1,773 checks across 157 groups; zero failures | `stata_semantic_report.json` |
| Embedded payload contracts | PASS, 162 dashboards, 813 summaries, 2,681 exact-category filters | `payload_qa_report.json` |
| Configurator and literal quoting | PASS, 11 controller groups and three quoting contracts | `tests/test_configurator.js`, `stata_quote_review.log` |
| Isolated installed package | PASS; installed commands describe, simulate, and build | `stata_install.log`, closed 01:41:54 |

Every public option has successful coverage: 71 build, 26 demo, four describe, three configure. [OPTION_COVERAGE.md](tests/OPTION_COVERAGE.md) maps options and rejection cases. Numerical/output contracts supplement return codes and checks of Stata dataset/session preservation.

The dashboard count changed from 164 to 162 after stale 2.3.3 manual artifacts `literal.html` and `malformed_logo.html` were preserved in `historical-2.3.3-scratch/`; neither belongs to the final generated fixtures.

## Actual Chrome acceptance

All five final themes—Worldbank, Editorial, Clean, Forest, Dark—were checked on desktop and mobile with no horizontal page overflow and 15px chart titles. Screenshots are in [browser_live](build/qa_windows/browser_live/), named `<theme>-desktop.jpg` and `<theme>-mobile.jpg`.

Stats counts 80 → 160 → 229 agreed between table/headline. Search, collapse/expand, customization, Done title persistence, and reset passed. Dark dialog text was `rgb(238,244,251)` on `rgb(21,31,46)`. At 844 × 390, popup bottom 379.56px fit the viewport. Visible numeric reset showed 22.32M with readable dark ticks; earlier offscreen captures showed cached canvas content, not a display defect. Chrome console checks were empty.

**Final 2.3.4 copy-view passed:** clipboard read and actual Ctrl+V paste confirmed the exact URL with selected sector `1`. The view and normal viewport were reset afterward; Chrome warnings/errors remained empty.

Actual CSV/PNG downloads passed on **2.3.3 before the UI patches**: `browser_live/downloaded-data.csv` (240 records) and `exported-sector.png` (1120 × 338). Weight/currency tests were analytical/controller only; no live browser weight/currency pass is claimed.

## Limits and optional refinements

Actual Stata 16–18, an actual Java 8 VM, macOS/Linux, network shares, and other browser engines were not tested. Java 8 bytecode compatibility is distinct from Java 8 execution. Live map-tile availability, current official boundary data, map/configurator visual flows, and exhaustive keyboard/screen-reader/contrast accessibility are outside this browser pass. Optional jsdom dependencies were unavailable. No finite suite proves absence of every possible bug.

Optional refinements: reduce editorial hero height on phones; consider Worldbank or Clean for dense analytical views; explore content-aware card heights and less blank export space without clipping labels or legends. These are polish suggestions, not additional implemented changes.

## Reproduce and package identity

Run `tests/build_windows.ps1`, `tests/run_windows_tests.ps1`, and `tests/run_stata_windows.ps1`, followed by `node tests/verify_stata_outputs.js`, `node tests/test_generated_payloads.js`, `node tests/test_configurator.js build/qa_windows/configure_default.html`, and `node tests/verify_stata_quotes.js`. Reader regressions also run with `node tests/test_reader_actions.js`. Repeat the live browser scenarios above against newly generated output.

- ZIP: `surveye-2.3.4-tested.zip`. Its final checksum is recorded outside the archive in `build/qa_windows/FINAL_QA_SUMMARY.json` after packaging this report.
- Runtime JARs: SHA-256 `26e168313615433af66ace6f6717b85cddbb7fba780cbd2d633a06b41fd41275`.

Restart Stata after replacing an installed release. No GitHub push, SSC submission, or external publication was performed.
