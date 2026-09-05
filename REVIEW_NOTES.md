# SurvEye 2.3.2 — local review build

Prepared 5 September 2026 from the supplied `SurvEye-2.3.1-review(1).zip`.
This is a local candidate, not a published release. No GitHub commit/push,
deployment, or SSC submission was made. The uploaded original ZIP is unchanged.

## Review the result

Open `examples/surveye_admin2_review.html`. It is a complete generated report,
not a mockup: 240 synthetic interviews, 120 indicators, 117 chart panels and
six sections. It embeds 25 named Sri Lankan district features from the supplied
World Bank boundary archive. Responses, weights and interview coordinates are
invented, not findings about Sri Lanka. The fixed LKR 300 per USD conversion is
an illustrative assumption, not a current exchange rate.

The boundary ZIP is not redistributed. The preview already embeds its selected
Sri Lanka geometry; the full archive is needed only when generating new maps.
A country-outline-only preview remains in `examples/surveye_review_dashboard.html`.

## What changed

### More readable, not a different report

The single-page structure remains: header, filters, highlights, profile table,
map, section navigation and chart grids. There is no new workspace, alternate
navigation architecture, or removal of the chart grid. Changes include a shorter
hero, balanced highlight/message rows, more readable filter chips and metadata,
stronger text contrast, quieter cards, and better mobile wrapping. Numeric card
headers keep the chart-type/Stats controls on one row and the longer statistical
summary on another. Binary cards are vertically balanced next to distributions.
Light/dark themes and existing customization/export controls remain available.

### Boundary archive compatibility and map polish

The loader now ignores macOS `__MACOSX` and `._*` resource-fork entries before
choosing a shapefile layer. The supplied archive's `UTF-8-SIG` CPG declaration
is normalized to UTF-8. Both issues blocked the previous candidate.

District/province labels are tied to original DBF row indices, so skipped null
shapes or deleted records cannot shift names onto another polygon. District
polygons have safe text tooltips and hover highlighting. The map adds **Fit
interviews** and **Country extent** buttons; individual points remain individual
and region colors remain stable under filters. Missing/outside counters now
follow the filtered sample. An empty sample removes old markers and restores the
country view. Tile failures expose a notice without hiding embedded geometry.

The archive is filtered using its NAM_0 / ISO_A3 / ISO_A2 / WB_A3 identifiers.
Consequently the Australian test includes Ashmore and Cartier, and the French
test includes Clipperton, as coded by this particular archive. This build does
not reinterpret geopolitical classifications or make a claim about whether the
archive's administrative definitions are current.

### Statistical meaning of binary highlights

Previously a binary highlight used the most common category. A card titled
“at least one woman among the owners” could therefore show the percentage for
**No**, then switch its meaning as filters changed. It now consistently shows
the affirmative share using the existing valid-response and weight rules.
In this synthetic weighted fixture, the card changes from 54.6% No to 45.4% Yes.
All-No samples give 0% Yes; samples with no valid responses give n/a.

General nonbinary modal summaries remain unchanged. The region profile table
still intentionally retains every region and its benchmark while applying the
other filters; its explanatory note states this exception.

## Install the candidate locally

Extract the complete ZIP. Point Stata to the **folder containing surveye.pkg**,
not the ZIP file or its parent folder. For example:

```stata
net install surveye, from("C:/SurvEye-review/surveye-2.3.2") replace
```

Restart Stata after installation when a previous Java engine has been loaded.
Regenerate your report under a new output name; older HTML files retain their
embedded older code. Existing command options still apply. A minimal example,
with paths and coordinate variable names replaced by your own, is:

```stata
use "C:/project/survey_data.dta", clear
surveye using "C:/project/questionnaire.html", ///
    saving("C:/project/dashboard_232_review.html") ///
    latitude(latitude) longitude(longitude) ///
    country("Sri Lanka") ///
    boundaries("C:/project/World Bank Official Boundaries - Admin 2_mac.zip") ///
    maplevel(admin2) maptype(points) basemap(google_hybrid) ///
    theme(worldbank) density(compact) replace
```

The explicit `replace` above applies only to the chosen new HTML filename.
Add your existing filters, highlights, mapby, weight and table options as usual.

## Tested versus unverified

**Executed successfully:** Java 8-targeted build; complete portable Java/Node
release gate; 77 new boundary regression assertions; eight-country real-archive
integration matrix; 73 real-Chromium report assertions; 21 additional theme/RTL
layout checks. Chromium instantiated all 117 real Chart.js charts and rendered
real Leaflet polygons/markers, with no uncaught JavaScript errors in those runs.
Both closed and expanded filters fit widths 320, 390, 600, 768, 1024, 1440 and
1920 pixels. See `QA_REPORT.md` and `docs/review-2.3.2/` for evidence.

**Not executed:** licensed Stata end-to-end runs, Windows/network-drive behavior,
Safari/Firefox/Edge runs, and the older optional jsdom/puppeteer suites (their
additional Node dependencies were unavailable). Real Chromium tests are separate
from those emulated/browser suites, not a claim that those suites ran.

**Not established:** live Google/OSM tile service availability, localStorage
persistence across file openings, all 246 NAM_0 country labels in the archive,
all real questionnaires, or visual superiority over every Python/R report.
The preview was loaded into Chromium with `page.set_content`; managed browser
file-navigation policy was not changed. External HTTP(S) was blocked deliberately
for offline map/error handling tests. No real firm data were supplied for testing.

Boundary geometry is simplified for display, and outside-boundary checks use
that display geometry. Near-edge points require authoritative GIS verification;
this is not a survey-grade boundary adjudication engine. Background tiles need
network access and may reveal the viewed map extent to the tile provider. No
survey-response upload was added.

## Three useful implementation details

1. The loader handles archive metadata and character encoding before matching
   records. Reading the right file is separate from interpreting its bytes.
2. The map footer and markers use the same selected row objects. Outside status
   is kept as configuration row indices, not a new field that could overwrite
   a survey variable.
3. The affirmative category is fixed by questionnaire metadata. Filtering changes
   a statistic's value, not what that statistic means.

Build/QA helpers are development utilities; the Stata/Java runtime does not gain
Python or R dependencies. Runtime assets remain embedded in the supplied JAR;
the rebuild script can reuse these when loose source assets are omitted.
