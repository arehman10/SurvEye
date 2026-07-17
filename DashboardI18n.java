# Example output

Open `sample_dashboard.html` in any modern browser. It is a SurvEye preview with 300
simulated records, a bundled Australia outline, a filter, highlight cards,
messages, custom sections, an outlier-aware numeric Stats tab, and compact chart
types. The compact GPS summary expands to the Leaflet
country map with **Show map**. Leaflet, points, and the boundary are embedded;
the Google or OpenStreetMap background tiles require internet access when the
map opens.

The file contains no real survey responses. Its simulated GPS coordinates are
embedded only to demonstrate point rendering, and it is marked
`SIMULATED DATA — PREVIEW ONLY` in the header and footer.

Version 2.0.0 also supports localized right-to-left output. For a real Arabic
or Urdu questionnaire, `uilanguage(auto) direction(auto)` normally detects the
appropriate interface and layout. Explicit alternatives are:

```stata
surveye using "questionnaire_ar.html", saving("dashboard_ar.html") ///
    uilanguage(ar) direction(auto) replace

surveye using "questionnaire_ur.html", saving("dashboard_ur.html") ///
    uilanguage(ur) direction(rtl) replace
```

Weighted examples use native Stata syntax after the `using` filename and before
the comma, such as
`[aw=wmedian]`, `[fw=frequency]`, `[iw=importance]`, and `[pw=pop]`. There is no
`weight()` option. Zero and missing weights are excluded. Confidence intervals
are off by default; add `ci` for eligible categorical and multiselect bars, or
`ci level(90)` to request a different level. Binary, completion, and donut cards
remain CI-free. Importance weights produce descriptive results and suppress a
requested interval automatically.
