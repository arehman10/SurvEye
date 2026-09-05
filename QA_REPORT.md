# QA report — SurvEye 2.3.2 local review

Run date: 5 September 2026. Inputs: the two ZIPs supplied in this conversation.
All reported passes below were executed for this candidate, not inherited from
historical QA. Historical review documents are retained under `docs/history/`.

## Environment and executed checks

| Area | Executed result | Scope and evidence |
|---|---|---|
| Build | PASS | OpenJDK 21.0.11, `--release 8`; Java 8 class target, not an actual Java 8 VM run. Rebuilt without loose runtime font assets using the supplied JAR fallback. |
| Portable gate | PASS | Java/Node parser, statistics, weights, currency, privacy, exact string codes, Stata source/bridge contracts, SurveyCTO, appearance, package integrity and resource parity. Node 22.16.0. |
| New archive regressions | PASS, 77 assertions | macOS forks, UTF-8-SIG/common aliases, nested/case-varied entries, Unicode names, deleted/null rows, polygon holes/islands, rejection of invalid projection/encoding/metadata-only input. |
| Actual supplied archive | PASS, eight countries | Counts and named-feature alignment checked against independently inspected DBF identifiers; antimeridian extent checked for Fiji. Details below. |
| Real browser interactions | PASS, 73 assertions | Chromium 144.0.7559.96, actual packaged Chart.js/Leaflet (not stubs), no uncaught page errors. Current HTML/source byte parity also checked. |
| Additional layouts | PASS, 21 checks | World Bank/dark/clean/forest styles; Arabic/Urdu document direction and expanded/collapsed-filter widths 390/768/1440. No page errors. |
| Privacy/resource checks | PASS | Both current previews and the privacy fixture embed current JS/CSS; synthetic raw-private-text sentinels are absent. |
| Visual inspection | Completed | Overview, district map, chart grid, dark mode, mobile and Urdu-interface screenshots inspected; numeric header crowding and binary-card vertical balance corrected before final capture. |

The main browser run checked collapsed and expanded filter layouts at 320, 390,
600, 768, 1024, 1440 and 1920 pixels with no page-level horizontal overflow.
Tables and section navigation may intentionally scroll inside their containers.
All 117 chart canvases instantiated real Chart.js charts and had positive finite
render dimensions. This is not a claim that a human inspected every one of the
117 charts individually. PNG and CSV were actual browser downloads, not mocks.

Browser checks included exact `01` versus `1` filters, stable map category colors,
keyboard activation of an interview point, district tooltips, map extent buttons,
empty-sample cleanup, responsive missing/outside counters, open Stats updates,
independently calculated median/mean-plus-three-SD values, USD display versus raw
CSV values, ordinary underscore-named survey columns, search/reset, serialized
share state, print disclosure restoration, title customization, and reduced motion.
The synthetic exchange rate is fixed at LKR 300/USD solely for the test.

## Supplied real-archive integration matrix

| Country request | Features loaded | Rings retained | Outcome |
|---|---:|---:|---|
| Sri Lanka | 25 | 92 | PASS |
| Nepal | 77 | 78 | PASS |
| Pakistan | 138 | 355 | PASS |
| Australia | 566 | 3,795 | PASS |
| Fiji | 15 | 248 | PASS; compact 176.91°–181.77° unwrapped longitude extent |
| France | 107 | 294 | PASS |
| Brazil | 5,506 | 7,642 | PASS |
| United States of America | 3,144 | 7,184 | PASS; antimeridian handling |

These counts describe the supplied archive, not current official administrative
counts. Australia includes one Ashmore and Cartier record sharing AUS/AU; France
includes a Clipperton record with WB_A3=FRA. The loader retains the existing
NAM_0/ISO_A3/ISO_A2/WB_A3 matching rule. Those additions are not label/geometry
alignment errors. All eight integration runs had nonempty feature names.

The source DBF contained 41,020 records and 246 distinct NAM_0 labels. The entire
archive was parsed to select the eight tested cases; output generation/rendering
for all 246 labels was **not** tested. Matrix loads took about 1.55–1.67 seconds
per request in this environment. These timings are not a Python/R benchmark.

The review HTML contains 240 invented points inside the 25 Sri Lankan features.
A separate generated edge-case fixture has two missing/invalid and two outside
coordinates. Its footer was checked after selecting each region separately.
The new dataset preserves the previous synthetic responses/weights and changes
only the GPS coordinates; the previous fixture is still present.

## What is not verified

Licensed Stata end-to-end execution, a Java 8 runtime itself, Windows/network-drive
behavior, other browsers, and live basemap tile availability were not tested.
The old optional jsdom and Puppeteer suites were not rerun because their extra
Node dependencies were unavailable; they must not be counted as passes.

Chromium loaded the generated HTML with `page.set_content`. Managed browser
file-navigation policy was not altered. External HTTP(S) requests were aborted
deliberately: polygons, points, chart libraries, and survey data remained local,
and tile failure notices appeared. This establishes offline rendering/error
handling, not Google/OSM uptime. Actual clipboard access and localStorage
persistence across real file openings were not established on this in-memory
origin. No real firm data were supplied or processed.

Display boundaries are simplified. Outside-boundary checks use the simplified
geometry; points close to an edge require independent GIS verification. Neither
geopolitical classifications nor present-day administrative validity of the
supplied archive were audited. No claim is made that this design is universally
better than all Python/R visuals.

## Reproduction

From the extracted repository root:

```bash
bash build.sh
sh tests/run_all.sh
python3 tests/generate_admin2_review.py "/path/World Bank Official Boundaries - Admin 2_mac.zip" --replace
python3 tests/generate_admin2_review.py "/path/World Bank Official Boundaries - Admin 2_mac.zip" --output build/diagnostic-dashboard.html --diagnostics --replace
python3 tests/browser_review.py examples/surveye_admin2_review.html --diagnostics-html build/diagnostic-dashboard.html --out build/browser-qa --chromium /path/to/chromium

mkdir -p build/test-classes
javac --release 8 -cp surveye.jar -d build/test-classes tests/OfficialBoundaryMatrix.java
java -Xmx1g -cp surveye.jar:build/test-classes OfficialBoundaryMatrix "/path/World Bank Official Boundaries - Admin 2_mac.zip"
```

The optional browser helpers require Python Playwright and an installed Chromium
binary. They are not runtime dependencies of the Stata package. On Windows,
Java classpath entries are separated with `;` rather than `:`.

For the additional layout suite, first use `generate_admin2_review.py` with
`--uilanguage arabic --output build/review-arabic.html --replace` and
`--uilanguage urdu --output build/review-urdu.html --replace`; then run
`python3 tests/browser_layout_variants.py --chromium /path/to/chromium`.

## Evidence and review trail

`docs/review-2.3.2/` contains build/test logs, browser assertions in JSON,
screenshots, input/output SHA-256 values, and a focused source patch. The full
World Bank ZIP and standalone fonts are not redistributed. The runtime JAR keeps
its embedded assets; optional source builds validate their original checksums.
No GitHub commit, push, deployment, or SSC submission was performed.
