# SurvEye: interactive Survey Solutions dashboards for Stata

**Author:** Attique Ur Rehman, Enterprise Analysis Unit, World Bank  
**Development assistance:** Developed with help of GPT-5.6 Sol Ultra.

[Repository](https://github.com/arehman10/SurvEye) ·
[Report a problem](https://github.com/arehman10/SurvEye/issues) ·
[Stata help](surveye.sthlp) ·
[Examples](example.do) ·
[Changelog](CHANGELOG.md)

SurvEye is a Stata 16+ tool, distributed as the command `surveye`, that turns a Survey Solutions questionnaire HTML file and the corresponding Stata data into a polished, interactive HTML dashboard. Questionnaire text supplies the labels, sections, response order, and categories; the command adds compact charts, smart related-variable families, explicit subgroup comparisons, filters, messages, custom data variables, native Stata weights, optional confidence intervals, live-filtered numeric summaries, optional profile tables, local-currency/USD switching, a localized right-to-left interface, and an optional Leaflet country map.

The command has deliberately useful defaults:

- the questionnaire determines the initial organization and chart types;
- compatible suffix families such as `srib8a srib8b srib8c` are combined automatically, with manual grouping and opt-out controls available;
- `density(compact)` keeps large instruments readable without excessive scrolling;
- categorical cards are kept uncluttered by default; add `ci` when intervals are useful on eligible bars;
- numeric cards combine a complete distribution, a conditional mean-plus-three-standard-deviations guide, and a detailed **Stats** tab that updates with every filter;
- supplying a Stata weight automatically adds a **Weighted estimates** switch, initially on, so readers can compare weighted and unweighted results without rebuilding the file;
- declared monetary variables can switch between their local currency and USD using one fixed, documented conversion rate;
- an optional profile table places selected indicators side by side by a grouping variable and follows the same filters, weight switch, and currency switch as the charts;
- `uilanguage(auto)` translates the interface and selects right-to-left layout for Arabic and Urdu questionnaires;
- GPS maps use individual points over Google Hybrid by default—nearby observations are not collapsed unless requested; and
- the dataset in memory is preserved while a temporary UTF-8 raw-code CSV is passed to the bundled Java engine through Stata's `javacall` interface.

Chart.js, Leaflet, styles, selected data, logos, and boundary geometry are embedded in the output. A dashboard without a map has no runtime network dependency. A map-enabled dashboard needs internet access to fetch the selected Google or OpenStreetMap tiles; it never uploads the survey data.

## Quick installation

Install the current release directly from the public GitHub repository:

```stata
net install surveye, from("https://raw.githubusercontent.com/arehman10/SurvEye/main/") replace
discard
```

After publication on SSC, the shorter equivalent will be:

```stata
ssc install surveye
```

Verify the wrapper, release-specific engine, and help file:

```stata
which surveye
findfile surveye_2_1_3.jar
help surveye
```

The package marks its JARs with uppercase `F` records so `net install` places both files on the ado-path. Stata uses `surveye_2_1_3.jar`; the byte-identical `surveye.jar` remains available for command-line and development use. After replacing an earlier copy, type `discard` or restart Stata.

### Requirements

- Stata 16 or newer with Java enabled (`java query` in Stata);
- the questionnaire-preview HTML exported by Survey Solutions;
- the corresponding Stata dataset in memory for a normal build; and
- internet access only when displaying a Google or OpenStreetMap background map.

The Java engine, Chart.js, Leaflet, fonts, and styles are bundled. Users do not
need to install a separate Stata dependency, Java library, web server, Node.js,
or browser extension.

### If GitHub installation returns `r(601)`

Confirm that the repository is public and that these two addresses open as
plain text in a browser:

- <https://raw.githubusercontent.com/arehman10/SurvEye/main/stata.toc>
- <https://raw.githubusercontent.com/arehman10/SurvEye/main/surveye.pkg>

Then retry the copy-ready command above. The package files must be at the
repository root, not inside a ZIP file or an enclosing `surveye-2.1.3` folder.
If an earlier SurvEye JAR was already used in the current Stata session, fully
restart Stata after reinstalling; the JVM can retain a loaded JAR until Stata
exits. Regenerate previously created dashboards to receive the new interface.

Version 2.0.0 introduces the SurvEye name and the public command/SSC package
`surveye`. Scripts written for the former `suso_dashboard` command or the
interim `surveydash` preview must replace that name with `surveye`; no legacy
compatibility command is installed.

## Two-minute start

Inspect a questionnaire without loading data:

```stata
surveye describe using "questionnaire.html", detail
return list
```

Build a dashboard from the dataset in memory:

```stata
use "survey_data.dta", clear
surveye using "questionnaire.html", ///
    saving("dashboard.html") replace open
```

Preview a questionnaire with clearly marked simulated data:

```stata
surveye demo using "questionnaire.html", ///
    saving("preview.html") n(300) seed(42) replace open
```

## Main syntax

```stata
surveye [varlist] [if] [in] using questionnaire.html [aw=wmedian], ///
    saving(filename) [options]

* Explicit build form, useful when the first variable is named build, describe, or demo
surveye build [varlist] [if] [in] using questionnaire.html [pw=pop], ///
    saving(filename) [options]

* Repeat a variable named build after the subcommand
surveye build build sales using questionnaire.html, saving(filename)
```

The weight specification is optional and must name one variable. Use Stata's native syntax—`[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, or `[pw=pop]`—after the `using` filename and before the comma. There is deliberately no `weight()` option.

| Goal | Options |
|---|---|
| Choose questionnaire content | `questions()`, `sections()`, `sectionmatch()`, `exclude()`, `maxpanels()`, `showempty`, `strict` |
| Add data-only variables | `customvars()`, `addtosections()` |
| Add controls | `filters()`, `highlights()`, `maxcategories()` |
| Add reader toggles | native Stata weight syntax; `usdvars()`, `usdrate()`, `currency()` |
| Add a profile table | `tableby()`, `tablevars()`, `tablestats()`, `tablelabels()`, `tabletitle()`, `tablesubtitle()`, `tabletotal()`, `tableweightlabel()` |
| Add narrative | `keymessages()`, `customsections()`, `note()`, `source()`, `disclaimer()` |
| Group related items | `vargroups()`, `ungroupvars()`, `noautogroups` |
| Compare binary outcomes | `compare()`, `compareby()`, `comparetitle()`, `comparelevels()` |
| Control figures | `bars()`, `donuts()`, `histograms()`, `discrete()`, `continuous()`, `noautodiscrete`, `missingcodes()`, `ci`, `level()` |
| Add a map | `latitude()`, `longitude()`, `country()`, `boundaries()`, `maplevel()`, `maptype()`, `basemap()`, `mapby()`, `maptitle()` |
| Set language and appearance | `uilanguage()`, `direction()`, `title()`, `subtitle()`, `logo()`, `theme()`, `density()` |
| Manage output | `saving()`, `replace`, `open`, `diagnostics()` |

Run `help surveye` in Stata for complete definitions and copy-ready examples.

## Selecting questionnaire variables

With neither a `varlist` nor `questions()`, the engine considers every chartable questionnaire item present in the data. A `varlist` selects ordinary Stata variables. `questions()` selects logical Survey Solutions names and is especially useful for a multiselect stored as expansion variables such as `services_used__1` and `services_used__2`. The selectors can be combined and are deduplicated.

```stata
surveye sales employment using "questionnaire.html", ///
    questions("services_used") saving("results.html") replace
```

Variables used only in `filters()`, `highlights()`, the profile table, USD conversion, map options, or as the weight variable are automatically included in the temporary export but are not charted. The dashboard is capped at 100 panels by default. Use focused selectors, raise `maxpanels()`, or specify `maxpanels(0)` to include all eligible panels.

Filter choices are drawn only from observed valid values. Numeric filters
compare normalized numeric values, multiselect filters use any-selected
semantics, and privacy-reduced text or media questions expose localized
Answered/Missing choices. Codes listed in `missingcodes()` are excluded from
the controls. Questionnaire-declared negative special response codes are also
excluded from numeric filters, so values such as Survey Solutions' `-4` and
`-9` are not offered as measurements. A requested categorical filter with no valid
observed choices returns an actionable error instead of creating a zero-result
control.

The search and filter area starts collapsed so the sticky header does not cover
the charts. Select **Show filters** to reveal search and filter choices, then
**Hide filters** to return to the compact toolbar. Active selections and search
text are preserved when the panel is collapsed. The toolbar always keeps
**Reset all** and the live interview count in view; the active-filter badge
appears when one or more filter choices are selected. **Reset all** clears both
the response filters and the indicator search.

## Put related variables together

SurvEye automatically recognizes conservative, compatible binary suffix families such
as `srib8a srib8b srib8c` or `service_a service_b service_c` in the same
questionnaire section. Their question text becomes the row labels in one compact
figure. Expanded multiselect storage variables such as `services__1` and
`services__2` are never mistaken for a family.

Define a family yourself with `Title:: varlist`; separate multiple definitions
with `|`:

```stata
surveye srib8a srib8b srib8c using "questionnaire.html", ///
    saving("digital.html") ///
    vargroups("Digital channels:: srib8a srib8b srib8c") replace
```

The variables must already be selected. Stata expands ranges and wildcards
inside each member list. `ungroupvars()` keeps selected variables as individual
cards while automatic grouping continues elsewhere; `noautogroups` disables
all automatic families but leaves explicit `vargroups()` intact. Custom
variables can participate after they are declared with `customvars()` and
placed together with `addtosections()` or `customsections()`.

For a direct subgroup comparison, `compare()` accepts up to 12 selected binary
variables and requires `compareby()`. It draws grouped horizontal bars of each
item's affirmative share. `comparelevels()` limits and orders the comparison
groups using exact raw values or displayed labels separated by `|`, and
`comparetitle()` supplies a concise heading:

```stata
surveye srib8a srib8b srib8c using "questionnaire.html", ///
    saving("digital_by_city.html") ///
    compare(srib8a srib8b srib8c) compareby(city) ///
    comparelevels("01_Colombo|02_Kandy|03_Jaffna") ///
    comparetitle("Digital access by city") replace
```

`compareby()` is included in the temporary export without becoming its own
indicator card. Comparisons use the normal sample, missing-code, and weight
rules. Because the displayed outcomes are binary affirmative shares, confidence
interval whiskers are not added to these bars even when `ci` is requested.

Automatic chart choices emphasize compact comparison: horizontal percentage bars for categorical and multiselect items, split bars for binary and answered/missing items, histograms for numeric variables, and time distributions for dates. Donuts are opt-in through `donuts()`. `maxcategories()` combines less frequent levels into **Other** without changing the valid-response denominator.

Small nonnegative whole-number variables are recognized as discrete counts
when their labels and observed support strongly look like counts (for example,
“How many workers?” or household size). Detection is conservative and uses the
analysis sample after missing-code restrictions. Readable ranges use one bar
per exact integer, including zero-frequency gaps. Wider ranges automatically
use equal-width, integer-aligned bins so an age, duration, or count plot never
turns into hundreds of subpixel bars. The x axis is numeric and uses regular
round-number ticks.

Every valid measurement remains in the distribution and summary. When the
weighted or unweighted mean plus three standard deviations falls strictly
inside the observed range, a dotted reference line marks that value. The guide
is omitted when the standard deviation is zero or the reference lies outside
the plotted range. Negative questionnaire response codes are excluded. Use `discrete()` to request
this smart integer display, `continuous()` to force a continuous histogram, or
`noautodiscrete` to disable only automatic detection:

```stata
surveye employees visits revenue using "questionnaire.html", ///
    saving("numeric.html") ///
    discrete(employees visits) continuous(revenue) replace
```

`histograms()` is compatible with `continuous()` and overrides automatic
discrete detection. It conflicts with `discrete()`. Conversely, `bars()` and
`donuts()` may accompany `discrete()` but conflict with `continuous()`.
`maxcategories()` does not control numeric distributions; it remains a limit
for categorical figures, filter controls, and preserved categorical labels.

The chart palette uses consistent roles rather than arbitrary rainbow coloring: ordinary comparisons are blue, numeric distributions purple, date trends navy, and the optional three-standard-deviation guide coral. Generic Yes/No cards use blue and a neutral stone so color does not imply that every Yes is good or every No is bad; answered/missing cards use green and gray. Special values remain visibly muted, and category/map colors are assigned from the full response order so filtering does not make a category change color. Cards are packed into balanced three-, two-, or one-card rows, with equal edges inside each row and no extra page height.

## Add variables that are not in the questionnaire

Use `customvars()` for constructed indicators, quality-control fields, administrative classifications, or any other variables in memory that are not questionnaire items. This option is additive: questionnaire charts are selected normally, then the declared variables are added. Their Stata variable labels become chart titles by default; if a variable has no label, its name is used. Survey Solutions' generic labels such as `Calculated variable of type String` are also replaced by the actual variable name, so calculated fields remain distinguishable.

The wrapper also sends enough Stata metadata to choose a sensible display:

- numeric storage becomes a numeric distribution;
- `%tc`, `%td`, `%tw`, `%tm`, `%tq`, `%th`, and `%ty` formats become date distributions;
- strings become categorical charts; and
- numeric variables with Stata value labels become categorical charts, with observed label text preserved when the number of levels is within `maxcategories()`.

Business-calendar `%tb` values are not currently interpreted as dates because
their calendar rules are dataset-specific. Convert or export them to one of the
supported date representations before including them in `customvars()`.

Unplaced custom variables appear under **Additional indicators**. Use `addtosections()` to move them into an existing selected questionnaire section by exact title (case is ignored) or number, or to create a clearly named new section. A numeric target first matches the questionnaire's original section number; if that number is not selected, it matches the visible dashboard position (1, 2, 3, ...). Separate assignments with `|`:

```stata
label variable qc_score "Enumerator quality score"
label variable risk_band "Review priority"

surveye using "questionnaire.html", ///
    saving("quality_dashboard.html") ///
    customvars(qc_score risk_band) ///
    addtosections("Interview quality: qc_score risk_band") ///
    histograms(qc_score) bars(risk_band) replace open
```

```stata
* Add one variable to section 3 and create a new Quality checks section
surveye sales using "questionnaire.html", ///
    saving("focused.html") customvars(qc_score risk_band) ///
    addtosections("3: qc_score|Quality checks: risk_band") replace
```

Only variables declared in `customvars()` may be named in `addtosections()`. An unmatched numeric target is an error; an unmatched text target creates a new section. `addtosections()` cannot be combined with `customsections()`, which reorganizes the whole selected dashboard.

## Arabic, Urdu, and right-to-left dashboards

`uilanguage(auto)` is the default. It gives priority to a questionnaire language declaration of Arabic (`ar`) or Urdu (`ur`). Otherwise, it inspects questionnaire titles, section headings, questions, and response labels: predominantly Arabic-script text selects Arabic, with Urdu-specific characters selecting Urdu; incidental Arabic text in an otherwise non-Arabic questionnaire does not change the interface. Other content selects English. The dashboard interface—not the questionnaire text—is translated. Accepted values are `auto`, `english`, `arabic`, and `urdu`; the short aliases `en`, `ar`, and `ur` are also accepted.

`direction(auto)` is also the default and follows the resolved interface language: Arabic and Urdu use right-to-left, while English uses left-to-right. Use `direction(rtl)` or `direction(ltr)` to override that layout independently. For example, explicitly choosing `uilanguage(english)` keeps the automatic direction left-to-right even when the questionnaire text is Arabic; add `direction(rtl)` if that combination is intentional. An explicit direction does not translate interface text.

```stata
* Arabic interface and right-to-left layout
surveye using "questionnaire_ar.html", saving("dashboard_ar.html") ///
    uilanguage(ar) direction(auto) replace open

* Urdu is normally detected automatically; this makes the choice explicit
surveye using "questionnaire_ur.html", saving("dashboard_ur.html") ///
    uilanguage(urdu) direction(rtl) replace open
```

Arabic and Urdu interface labels, controls, summaries, map text, chart descriptions, and empty-state messages are embedded in the HTML. Variable names, supplied titles, notes, sources, key messages, and questionnaire wording remain exactly as provided. Technical parser warnings, diagnostics, and Stata console messages remain in English. The generated document sets the matching `lang` and `dir` attributes so browsers and assistive technologies use the correct reading order. RTL mode also mirrors horizontal chart scales, category axes, direct labels, navigation, tables, and Leaflet controls rather than merely aligning the surrounding text.

## Stata weights

Supply one numeric weight variable with the normal Stata weight specification; do not put a weight inside the option list:

```stata
* Analytic weight
surveye sector sales using "questionnaire.html" [aw=wmedian], ///
    saving("weighted_aw.html") replace

* Frequency weight
surveye sector sales using "questionnaire.html" [fw=frequency], ///
    saving("weighted_fw.html") replace

* Importance weight (descriptive)
surveye sector sales using "questionnaire.html" [iw=importance], ///
    saving("weighted_iw.html") replace

* Probability weight
surveye sector sales using "questionnaire.html" [pw=pop], ///
    saving("weighted_pw.html") filters(region) replace
```

The weight must resolve to one numeric variable. Negative values are rejected, and frequency weights must contain integers. Observations with zero or missing weights are excluded for all four weight types, so every retained weight is positive. The command stops if no observations remain after `if`, `in`, and weight restrictions. The weight variable is exported automatically for calculation but is not charted unless selected.

Weights affect shares, histograms, means, medians, quantiles, standard deviations, and the numeric Stats table. They do not replace `svyset`, and `surveye` does not estimate design-based standard errors. When `ci` is supplied, frequency-weighted Wilson intervals use the weighted count; analytic- and probability-weighted intervals use a clearly labelled Kish effective-sample-size approximation. Importance weights have no general sampling interpretation, so a requested interval is suppressed automatically and the dashboard explains why. The remaining weighted intervals do not account for strata, primary sampling units, finite-population corrections, or other survey-design features.

Supplying any supported Stata weight also adds a **Weighted estimates** switch
to the dashboard. It starts on, so the initial display is weighted. Turning it
off recalculates charts, comparisons, numeric summaries, and the optional
profile table from the same exported analysis sample using one unit per row;
turning it on restores the supplied weight. Raw sample counts remain raw in
both modes. The switch does not restore observations excluded by `if`, `in`,
or invalid, zero, or missing weights when the dashboard was built.

## Local currency and USD

The typical complete specification uses all three currency options to let
readers switch selected monetary variables between local currency and USD:

```stata
surveye sales costs profit using "questionnaire.html", ///
    saving("financials.html") ///
    usdvars(sales costs profit) usdrate(300) currency(LKR) ///
    replace open
```

`usdvars()` names numeric, non-date variables to convert and requires
`usdrate()`. `usdrate(#)` is the number of local
currency units per US dollar, so the USD display is the stored local value
divided by that rate. `currency(code)` supplies the local-currency code shown
in labels, such as `LKR`, `KES`, or `PKR`; if it is omitted, the label is
**Local currency**. The dashboard starts in local currency and adds a USD
switch. The switch updates charts,
numeric Stats, and profile-table `mean`, `median`, and `sum` cells that use a
variable in `usdvars()`; it does not alter the embedded source values.

SurvEye does not download a live or historical exchange rate. Choose a rate
appropriate to the data's reference period and document it in `note()` or
`source()` when the dashboard will be shared.

## Side-by-side profile table

`tableby()` and `tablevars()` add a compact, responsive table near the top of
the dashboard, before the detailed chart sections. The grouping variable
supplies the rows, and the variables in `tablevars()` supply the indicator
columns. Use the table for a small set of decision-relevant measures rather
than reproducing every chart:

```stata
surveye women_led sales banked digital_channel practice_index ///
    using "questionnaire.html" [pw=pop], ///
    saving("stratum_profile.html") filters(stratum owner_type) ///
    usdvars(sales) usdrate(300) currency(LKR) ///
    tableby(stratum) ///
    tablevars(women_led sales banked digital_channel practice_index) ///
    tablestats("share:1|median|share:1|share:1|mean") ///
    tablelabels("Women-led|Median sales|Banked|Digital channel|Practice index") ///
    tabletitle("Stratum profile") ///
    tablesubtitle("Key indicators side by side") ///
    tabletotal("Sri Lanka (all surveyed locations)") ///
    tableweightlabel("Est. firms") replace open
```

Both `tableby()` and `tablevars()` are required when a profile table is
requested. By default, each column heading is the variable label, with the
variable name used only when no label exists. `tablelabels()` overrides those
headings with a pipe-separated list in the same order as `tablevars()`.
`tabletitle()` and `tablesubtitle()` provide the table heading and one-line
explanation.

`tablestats()` is an optional pipe-separated list with one entry per table
variable. The choices are `auto`, `share`, `share:<code>`, `mean`, `median`,
and `sum`. `auto` uses the median for numeric distribution variables and an
affirmative share for other compatible variables. `share` also uses the
recognized affirmative category; use `share:<code>` when the intended raw
response code must be explicit. `mean`, `median`, and `sum` require numeric
variables. Omit `tablestats()` to use `auto` for every column.

The table always shows raw `n`. In weighted mode it also shows the sum of the
active weights; `tableweightlabel()` changes that column heading, for
example to **Est. firms**. The weighted-total column is hidden in unweighted
mode and returns when **Weighted estimates** is switched back on. Table
shares, means, medians, sums, and currency values recalculate immediately with
the dashboard controls. Interpret that total according to the supplied Stata
weight type; only an appropriate expansion weight should be described as an
estimated population count.

The table always ends with an all-group reference row; its default label is
**All filtered interviews**. `tabletotal("label")` replaces that row label,
for example with a country benchmark. The table deliberately ignores only its
own `tableby()` filter: every grouping row and the all-group benchmark remain
available for comparison, while selected grouping rows can be highlighted.
Every other active dashboard filter is honored, as are the current weight and
local-currency/USD settings. This prevents a stratum selection from silently
turning the benchmark into a copy of the selected stratum.

## Confidence intervals and numeric distributions

Confidence intervals are off by default. This keeps compact dashboards easy to scan and avoids placing inferential marks on binary composition bars, where they can be mistaken for a range covering part of the stacked bar.

Add `ci` to show pointwise Wilson intervals on ordinary categorical and multiselect horizontal bars. `level(#)` sets their confidence level (greater than 50 and less than 100) and requires `ci`; the default with `ci` is 95. Binary yes/no cards, answered/missing completion cards, and donuts never show confidence-interval text or whiskers, even when `ci` is requested.

```stata
surveye sector ownership using "questionnaire.html", ///
    saving("shares_clean.html") replace

surveye sector ownership using "questionnaire.html", ///
    saving("shares_90.html") ci level(90) replace
```

For unweighted data, requested intervals use the valid raw count. Frequency weights use the weighted count. Analytic and probability weights use a Kish effective-sample-size approximation. These are descriptive, pointwise intervals—not design-based survey estimates—and do not account for strata, clusters, finite-population corrections, or other complex-design features. Importance weights automatically suppress requested intervals because they have no general sampling interpretation.

Every numeric card has two views:

- **Distribution** shows all valid measurements on a regular numeric axis. Integer counts use exact bars while readable and switch automatically to equal-width, integer-aligned bins for wider ranges. A dotted **Mean + 3 SD** guide appears only when the standard deviation is positive and the reference falls strictly inside the plotted range.
- **Stats** reports valid raw n, missing/excluded n, mean, standard deviation, minimum, maximum, p25, median, p75, and the mean-plus-three-standard-deviations reference. The table is recalculated immediately whenever a dashboard filter changes, including while the Stats tab is open.

No observation is classified or labelled as a Tukey outlier. All valid values remain in the chart and statistics. With weights, the mean, standard deviation, quantiles, guide, and distribution use the supplied weights; the card identifies them as descriptive weighted statistics and reports the valid weighted sum.

## Country maps

Supply numeric latitude and longitude variables and a country name, ISO-2 code, or ISO-3 code:

```stata
surveye sector sales using "questionnaire.html", ///
    saving("mapped.html") ///
    latitude(gps_latitude) longitude(gps_longitude) country(KEN) ///
    boundaries("World Bank Official Boundaries - Admin 2.zip") ///
    maplevel(admin2) maptype(points) basemap(google_hybrid) ///
    mapby(sector) maptitle("Location of completed interviews") ///
    replace open
```

The map uses embedded Leaflet 1.9.4 and the same base-layer choices and option names as `esqc_gps`:

| `basemap()` | Leaflet base layer |
|---|---|
| `google_hybrid` | Google satellite imagery with labels; default |
| `google_sat` | Google satellite imagery |
| `google_road` | Google road map |
| `osm` | OpenStreetMap |

The layer control in the map can switch among all four. Base tiles are fetched when the map opens, so map-enabled dashboards require internet access and the tile provider must be reachable. Leaflet, survey points, and boundary geometry remain embedded.

The `esqc_gps`-compatible Google choices use keyless compatibility tile endpoints; they are not an authenticated Google Maps Platform integration and their availability or access policy can change. Before distributing a dashboard, confirm that the selected provider is approved for the intended use and comply with its current terms and attribution rules. Use `basemap(osm)` when OpenStreetMap is the approved provider. The GPS points and boundary remain usable if a background provider is unavailable.

`maptype(points)` is the default. Every valid observation is rendered as its own circle marker, without clustering. Each point is focusable by keyboard and opens its localized popup with Enter or Space. If several rows share exactly the same coordinates, their markers are offset slightly on screen so each can be selected; the popup retains the original coordinate and notes the separation. `maptype(cluster)` and `maptype(heat)` are explicit, aggregated alternatives for very dense or disclosure-sensitive maps.

`mapby()` accepts up to ten observed valid groups. A grouping value named in
`missingcodes()` is left out of the group cap and legend, while its GPS point
remains visible in the default point color.

If `boundaries()` is omitted, the engine uses a bundled 1:50m country outline and `maplevel(country)`. A boundary ZIP should contain a polygon `.shp`, matching `.dbf`, and preferably a `.prj`; WGS84 longitude/latitude is required. Supplying a compatible ZIP defaults to `maplevel(admin2)`. Country and Admin-2 lines are drawn as WGS84 Leaflet rings so they align with the tiles and GPS points; antimeridian geometries are normalized to one longitude branch. Invalid coordinate pairs are omitted and reported in `r(map_missing)`; valid points outside the selected boundary are reported in `r(map_outside)`.

In compact dashboards, the map starts as a concise summary and opens with **Show map**. Comfortable dashboards open it initially.

### Map privacy

A map-enabled HTML file embeds valid latitude and longitude so Leaflet can draw and filter the map: six decimal places for the default `points`, four for `cluster`, and three for `heat`. Even reduced precision can identify respondents or establishments. Protect the HTML like the source data, conduct disclosure review, and do not publish row-level maps without authorization. `cluster` and `heat` reduce coordinate precision and aggregate the visual display, but they are not a substitute for a formal disclosure-control method.

## Data rules and privacy

One invocation represents one rectangular unit of observation. Survey Solutions roster exports are separate files; create one dashboard per roster level, or merge a carefully defined roster summary into the parent data first. Blind joins can change denominators.

The HTML embeds the selected analysis-level values needed by filters and figures. Text, picture, audio, linked text-list, and questionnaire-GPS completion items are reduced to answered/not-answered flags instead of embedding their original contents. Raw response codes are sent to the engine so questionnaire categories remain authoritative; `customvars()` is the exception where Stata variable labels and, when applicable, observed value labels are intentionally used. Questionnaire-defined special responses remain visible and muted in categorical figures. Negative special codes are automatically excluded from numeric histograms, statistics, and numeric filters because they are not measurements; nonnegative substantive shortcuts remain valid. This removes Survey Solutions responses such as `-4` and `-9` without discarding legitimate negative-valued questions. Use `missingcodes()` for additional sentinels not declared by the questionnaire, for example `missingcodes(-999 -998 999)`.

The command does not upload data. Nevertheless, the finished file is an interactive data product, not a static image, and should be stored and shared under the same controls as its inputs.

## Returned results

The command is `rclass`. Important results include `r(N)`, `r(k_charted)`, `r(k_sections)`, `r(k_filters)`, `r(chartvars)`, `r(skippedvars)`, `r(warnings)`, `r(weighted)`, `r(has_map)`, `r(map_N)`, `r(map_missing)`, `r(map_outside)`, `r(output)`, `r(questionnaire)`, and `r(engine_version)`.

```stata
return list
```

## Development and release checks

The portable tests require a JDK and, for the complete parser regression, the directory containing the supplied questionnaire HTML files:

```bash
./build.sh
./tests/check_stata_source.sh
./tests/check_package.sh
node tests/test_statistics.js
./tests/run_engine_smoke.sh tests
./tests/run_parser_tests.sh /path/to/questionnaire-html-files
./tests/run_engine_smoke.sh /path/to/questionnaire-html-files
./release.sh /path/to/release-directory
```

A licensed Stata smoke test remains required before SSC submission:

```stata
do "tests/stata_smoke.do" "C:/path/to/surveye"
```

Responsive browser QA uses Node.js 18+ and the pinned dependencies in `tests/package.json`:

```bash
cd tests
npm install
npm run qa -- ../dashboard.html --out qa-output/dashboard
```

For a map-enabled dashboard, the harness should allow or deliberately stub the documented Google/OpenStreetMap tile requests while continuing to reject unexpected external traffic. Verify point count, coincident-point visibility, base-layer switching, filtering, live numeric Stats tabs, conditional mean-plus-three-standard-deviations guides, default CI absence, `ci level(#)` opt-in behavior on eligible bars, responsive layout, keyboard operation, and horizontal overflow.

## Files

- `surveye.ado` — Stata command and Java bridge
- `surveye.sthlp` — complete Stata help
- `surveye_2_1_3.jar` — release-specific JAR used by Stata
- `surveye.jar` — byte-identical conventional JAR
- `surveye.pkg` and `stata.toc` — Stata package metadata
- `example.do` — runnable starter and recipes
- `tests/stata_smoke.do` — licensed-Stata integration checks
- `examples/sample_dashboard.html` — simulated browser-ready feature preview
- `tests/sample_dashboard_config.tsv` — reproducible configuration for that preview
- `GITHUB_UPLOAD.md`, `PUBLISHING.md`, and `release.sh` — GitHub/SSC release instructions and clean archive builder
- `LICENSE` and `THIRDPARTY-LICENSES.md` — licensing
- `CHANGELOG.md` — release history

## Author

Attique Ur Rehman  
Enterprise Analysis Unit, World Bank

- Repository: <https://github.com/arehman10/SurvEye>
- Support and bug reports: <https://github.com/arehman10/SurvEye/issues>

## Acknowledgments

Thanks to [Fahad Mirza](https://github.com/fahad-mirza) (World Bank / CERP) for his
insights and guidance, and for his self-contained Stata tooling
([sparkta](https://github.com/fahad-mirza/sparkta_stata),
[wordcloud2](https://github.com/fahad-mirza/wordcloud2_stata)), which helped shape the
design of this package.

## License

Released under the [MIT License](LICENSE). This is an independent utility; the
views and dashboards produced with it do not necessarily represent the views
of the World Bank, its Board of Executive Directors, or the governments they
represent. Survey Solutions is a World Bank data-collection platform.
