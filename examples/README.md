# Example output

Open `sample_dashboard.html` in any modern browser. It is a SurvEye preview with
300 simulated records, a bundled Australia outline, weighted/unweighted and
AUD/USD switches, a live legal-status profile table, a filter, highlight cards,
messages, live-filtered numeric Stats tabs, conditional
mean-plus-three-standard-deviations guides, and compact chart types. The compact
GPS summary expands to the Leaflet country map with **Show map**. Leaflet, 300
individual points, and the boundary are embedded; Google or OpenStreetMap
background tiles require internet access when the map opens.

The sticky dashboard controls also start compact. Use **Show filters** to reveal
search and filter choices; **Hide filters** restores chart space without
clearing the current selections. **Reset all** remains available in the compact
toolbar and clears both filter choices and indicator search text.

The profile table keeps all legal-status rows and its all-firm benchmark visible
when that same filter is selected. Every other active filter would update the
table. **Weighted estimates** starts on, while **Values in USD** starts off; the
sample uses a clearly labelled fixed demonstration exchange rate.

The file contains no real survey responses. Its simulated GPS coordinates are
embedded only to demonstrate point rendering, and it is marked
`SIMULATED DATA — PREVIEW ONLY` in the header and footer.

SurvEye also supports localized right-to-left output. For a real Arabic
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
