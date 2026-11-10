# SurvEye 2.3.3 — Windows/Stata release validation

Tested 19 September 2026 from the supplied `SurvEye-main.zip`. The original ZIP remains unchanged. This report supersedes the prior 2.3.2 report, preserved in `docs/history/QA_REPORT-2.3.2-original.md`.

## Release assessment

All executed gates pass for the corrected 2.3.3 candidate: the full licensed-Stata option matrix, independent Java regressions, generated-output statistics, and isolated local package installation. Every public Stata option was exercised successfully. This establishes readiness within the tested command/engine scope; complete production sign-off still requires fresh visual/browser acceptance. The managed browser refused local-file navigation and optional browser dependencies were unavailable.

## Environment

- Windows, working from the supplied OneDrive folder, including paths with spaces and Unicode.
- Licensed StataNow/MP 19.5, 64 bit, revision 12 August 2026; Java heap 4 GB.
- Stata-bundled Zulu JDK 21.0.12; source compiled with Java 8 target compatibility.
- Node.js 24.19.0.
- Synthetic bundled survey data only. No external survey dataset or real administrative-boundary archive was supplied.

## Defects corrected

1. **Relative filenames:** the JVM resolved relative inputs/outputs against its installation directory. The Stata bridge now resolves questionnaire, output, diagnostics, boundary, logo, and temporary-file paths against Stata's current directory.
2. **Literal text and filenames:** dispatch, status parsing, appearance processing, and selected display/option paths re-expanded macro-looking text. Literal backticks, apostrophes, dollar signs, and Unicode are preserved through HTML, returned results, and file handling.
3. **Unusable documented options:** noncontiguous uppercase letters in the syntax declarations caused `addtosections()` and `tableweightlabel()` to be rejected. Both full lowercase options now execute successfully.
4. **Compound quoting:** `questions()`, `exclude()`, `tablestats()`, and `tablelabels()` now use Stata's string parsing to accept ordinary and compound quotes. Unquoted lists remain supported.
5. **Malformed pipe lists:** trailing empty entries in variable groups, table statistics, and table labels now fail consistently with leading/interior empty entries.
6. **Automatic table statistics:** a questionnaire binary field used only in a table incorrectly became a histogram/numeric field, making `auto` report a median. The table now preserves the questionnaire's categorical meaning and agrees with the same field when charted.
7. **Logo validation:** nonimages and corrupt/mislabeled files were accepted and emitted as broken images. PNG, JPEG, GIF, and SVG logos now require matching valid content.
8. **Configurator:** fixed missing theme in TSV, comparison-level code serialization, hidden selections left behind after deselection, invalid USD-rate readiness, crashes from stale saved variable names, cross-section groups, and comparisons with invalid counts, chart overrides, or nonbinary fields.
9. **Release consistency:** synchronized metadata/help, documented configure and comparison limits, removed stale versioned JARs from the package root while preserving backups, and introduced a distinct 2.3.3 engine JAR.

## Executed gates

| Gate | Result | Evidence |
| --- | --- | --- |
| Licensed Stata smoke | PASS, 19 scenarios | `build/qa_windows/run-smoke.log` |
| Full option matrix | PASS, 289 cases | `build/qa_windows/stata_matrix_results.tsv`, `stata_matrix.log` |
| Edge, state, and reproducibility | PASS, including 18 focused edge cases | `build/qa_windows/stata_extended.log` |
| File protection, SurveyCTO, GPS | PASS | `build/qa_windows/stata_files.log` |
| Generated-output semantic checks | PASS, 1,773 checks across 157 fixture groups | `build/qa_windows/stata_semantic_report.json` |
| Embedded dashboard payload checks | PASS, 164 dashboards, 822 summaries, 2,707 exact-category filters | `build/qa_windows/payload_qa_report.json` |
| Configurator controller | PASS, 11 case groups including actual generated controller | `tests/test_configurator.js` |
| Java engine, package and appearance | PASS, 27 groups; parser 181, boundary 77, and new release 35 assertions | `build/qa_windows/engine-20260919-001959/` |
| Local package installation | PASS; installed copy describes, simulates, and builds successfully | `build/qa_windows/stata_install.log` |
| Literal quoting follow-up | PASS, 3 Stata cases and 3 output contracts | `build/qa_windows/stata_quote_review.log`, `tests/verify_stata_quotes.js` |

The option inventory contains **71 build options, 26 demo options, four describe options, and three configure options**. Every option has successful coverage, with `showempty` using a real questionnaire item absent from the data. All finite appearance/language/direction values run in both build and demo. Detailed mappings and rejection cases are in `tests/OPTION_COVERAGE.md` and the semantic JSON report.

The matrix covers native analytic/probability/frequency/importance weights, zero/missing/negative/fractional weights, if/in, exact string codes, custom variables and labels, date formats, chart overrides, grouping/comparisons, table statistics, currency conversion, CI controls, maps and a synthetic WGS84 Admin-2 ZIP, diagnostics, source protection, overwrite behavior, browser-open code paths, and simulated datasets from one to 100,000 observations. Tests check dataset fingerprints after successful and rejected calls. Separate checks preserve observation order, variable/value labels, estimation results, and Stata's random-number state.

Independent output checks compare embedded results with the source CSV, calculate means/medians/categorical denominators and Wilson/Kish intervals independently, test filter intersections and currency/weight switches, inspect maps and privacy reductions, and compare same/different demo seeds. Return-code tests alone are not treated as proof of correct output.

## Limits

- Actual Stata 16–18, a Java 8 VM, macOS/Linux, network shares, other browser engines, and live map tiles were not executed. Java 8 bytecode compatibility is distinct from running Java 8.
- The managed browser blocked local HTML navigation. No fresh screenshots, real rendered chart layout, browser clipboard/download, or browser accessibility pass is claimed. Dependency-free controller/embedded-runtime tests are not a substitute for these checks.
- Stata's `open` options executed successfully and saved their files; this does not establish rendered-browser correctness.
- Boundary tests use synthetic fixtures and existing regression archives, not a current official boundary dataset.
- No finite test suite proves absence of every possible bug. The evidence above applies to the tested candidate and inputs.

## Reproduce

From the extracted source root in PowerShell:

```powershell
.\tests\build_windows.ps1
.\tests\run_windows_tests.ps1
.\tests\run_stata_windows.ps1
node tests/verify_stata_outputs.js
node tests/test_generated_payloads.js
node tests/test_configurator.js build/qa_windows/configure_default.html
node tests/verify_stata_quotes.js
.\tests\package_windows.ps1
```

The Stata runner automatically builds its synthetic boundary fixture and checks completion markers instead of trusting Stata's process exit code. Stata must be installed and licensed. The Java runner requires a JDK and Node; its optional `-Dom` suite additionally requires jsdom and is not counted unless explicitly run.

The delivered ZIP contains only the declared runtime files and a SHA-256 manifest. Both runtime JAR names have SHA-256 `b455aa4e2f7fee6b1aeb28132cc3173a671552319010e545e85f8a53cec18e46`. Source and reusable tests remain in the extracted `SurvEye-main` directory. Install from the extracted local package, then restart Stata after replacing an installed release. No GitHub push, SSC submission, or external publication was performed.
