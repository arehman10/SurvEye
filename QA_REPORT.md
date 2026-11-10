# SurvEye 2.3.5 — what was tested, and what was not

Tested 9 November 2026. **Status: release candidate.** Everything that can be
tested without a Stata licence passed. Licensed Stata was not run for 2.3.5;
the short Stata check at the end of this page is still owed before the
release is called field-ready.

2.3.5 changes no calculation, chart, option or default. Compared with 2.3.4
it fixes the configurator's theme option, rounds the footer time stamp to
seconds, restores the help file's version line, and repairs the release
tooling. The full licensed-Stata evidence for the unchanged command logic is
the 2.3.4 report: [docs/history/QA_REPORT-2.3.4.md](docs/history/QA_REPORT-2.3.4.md).

## Starting point

The published repository was checked first, exactly as it stood.

| Check on the repository as published | Result |
| --- | --- |
| `bash build.sh` | Failed: `VERSION` said 2.3.2, the source said 2.3.4 |
| Repository's own test gate | 4 of 9 suites passed |
| What `net install` delivered | A consistent, tested 2.3.4 command (ado, help and both jars matched) |
| Configurator with theme Worldbank | Command box had no `theme()` option (bug SE-001), reproduced in Chromium |
| Automatic checks on GitHub | Not running: `ci.yml` sat at the repository root, where GitHub ignores it |

So users who installed 2.3.4 received a sound command. The repository
around it could not rebuild or test itself.

## Executed for 2.3.5

Environment: Ubuntu 24.04, OpenJDK 21.0.12 (build), Node 22, headless
Chromium via Playwright. Synthetic data only.

| Gate | Result |
| --- | --- |
| Version identical in all 15 places (`scripts/bump-version.js --check`) | Pass |
| Portable gate, `tests/run_all.sh`: 11 suites | Pass, 50 pass lines, 0 failures |
| of which: parser | 181 assertions |
| of which: boundary archives | 77 assertions |
| of which: release-readiness | 36 assertions |
| of which: configurator controller | 13 cases, 14 against generated HTML |
| JVM matrix, `tests/run_jvm_matrix.sh`: Java 8, 11, 17, 21, each with a Cp1252 default character set | Pass: 15 regression classes and 4 dashboards per runtime; every dashboard byte-identical to the build JDK's (time stamp aside) |
| jsdom DOM suite on the regenerated example | Pass |
| Chromium load pass: 13 dashboards (4 engine themes, default editorial finish, Arabic, Urdu) at 1366, 390 and 320 px | 39 loads: 0 console errors, 0 uncaught exceptions, 0 horizontal overflow, 0 duplicate ids, 0 unnamed buttons |
| Numbers on screen against values recomputed from the CSV | Agree: weighted and unweighted shares and medians, USD conversion, one- and two-filter subsets (below) |
| Hostile text in labels, title, notes and data (`</script>`, `<img onerror>`, U+2028) | Nothing executed; shown as text |
| Configurator theme in Chromium: 2.3.4 against 2.3.5 | 2.3.4 omits `theme(worldbank)`; 2.3.5 writes the selected theme for all five themes and after reload |
| Release archives, `release.sh` | Built; file lists verified by the script |

Numbers compared (review fixture, 240 synthetic interviews; the page value is
what Chromium displayed after clicking the control):

| View | Page | Recomputed |
| --- | --- | --- |
| Bank account, weighted, all interviews | 56.1% | 56.1% |
| Bank account, unweighted, all interviews | 59.8% | 59.8% |
| Woman among owners, weighted / unweighted | 45.4% / 45.7% | 45.4% / 45.7% |
| Annual sales, weighted median | LKR 2.76M | 2,757,000 |
| Same, in USD at 300 | USD 9,190 | 9,190 |
| Northern region: bank account / woman owner, weighted | 57.9% / 28.6% | 57.9% / 28.6% |
| Northern + Manufacturing: annual sales, weighted median | LKR 2.21M | 2,205,500 |

The last row is the instructive one. The cumulative weight reaches exactly
half at one observation, and SurvEye then averages that value with the next,
as Stata does. A check that skips that tie rule gets 2,014,000 instead.

## Not tested for 2.3.5

- **Licensed Stata, any version.** `surveye.ado` changed only in its version
  strings and five line endings, but nothing here executed it.
- Windows and macOS. The Windows scripts (`tests/*.ps1`) were edited and not
  run.
- The new GitHub workflow has not run on GitHub yet.
- Firefox and Safari. Live map tiles (no internet in the test browser);
  points and boundaries render without them.
- `examples/surveye_admin2_review.html` was not regenerated; it needs the
  World Bank Admin-2 boundary archive.
- Real survey data. Fixtures are synthetic.

## Known and unchanged

- Map base layers default to Google's keyless tile endpoints, fetched when a
  reader opens the map. This is documented in the help; `basemap(osm)` is the
  alternative. Review before distributing a dashboard outside your team.
- The CSV export writes category labels as they are. A label that begins
  with `=`, `+`, `-` or `@` would be read as a formula by spreadsheet
  software. Labels come from the questionnaire, not from respondents.

## Still owed: the Stata check

In a fresh Stata session, from the repository root:

```stata
do tests/stata_release_smoke.do
```

Expected last line: `PASS_RELEASE_SMOKE 2.3.5`. Then open
`build/release_smoke/configure.html`; with theme Worldbank the command box
must contain `theme(worldbank)`.

For the full gate on Windows: `./tests/run_windows_tests.ps1`, then
`./tests/run_stata_windows.ps1 -Suite all`. The 2.3.4 run of that gate
passed 289 option-matrix cases; 2.3.5 should match.

## Identity

- Engine: `surveye.jar` and `surveye_2_3_5.jar`, identical files.
- File sha256: `dda197d35155819a514e4405f808cfa45658abd478a21232e35c2c05c2f27846`
- Content fingerprint: `b7fa5d484df4859bcdcf0f10c9c62f89ba51a8d6596416483c56fd2122f01680`
  (`java scripts/JarFingerprint.java surveye.jar`; this value survives a
  rebuild on another machine, the file sha256 does not).
