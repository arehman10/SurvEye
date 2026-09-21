{smcl}
{viewerjumpto "Quick start" "surveye##quickstart"}{...}
{viewerjumpto "Syntax" "surveye##syntax"}{...}
{viewerjumpto "Description and requirements" "surveye##description"}{...}
{viewerjumpto "Options" "surveye##options"}{...}
{viewerjumpto "Select indicators" "surveye##selection"}{...}
{viewerjumpto "Add custom variables" "surveye##customvars"}{...}
{viewerjumpto "Group related variables" "surveye##families"}{...}
{viewerjumpto "Compare binary indicators" "surveye##comparisons"}{...}
{viewerjumpto "Controls and chart choices" "surveye##charts"}{...}
{viewerjumpto "Weights" "surveye##weights"}{...}
{viewerjumpto "Local currency and USD" "surveye##currency"}{...}
{viewerjumpto "Profile table" "surveye##profiletable"}{...}
{viewerjumpto "Confidence intervals and statistics" "surveye##ci"}{...}
{viewerjumpto "GPS maps" "surveye##map"}{...}
{viewerjumpto "Themes and presentation" "surveye##presentation"}{...}
{viewerjumpto "Inspect a questionnaire" "surveye##describe"}{...}
{viewerjumpto "Command configurator" "surveye##configure"}{...}
{viewerjumpto "Simulated preview" "surveye##demo"}{...}
{viewerjumpto "Data rules and privacy" "surveye##data"}{...}
{viewerjumpto "Reader tools" "surveye##readertools"}{...}
{viewerjumpto "Examples" "surveye##examples"}{...}
{viewerjumpto "Stored results" "surveye##results"}{...}
{viewerjumpto "Troubleshooting" "surveye##troubleshooting"}{...}
{viewerjumpto "Author" "surveye##author"}{...}
{viewerjumpto "Acknowledgments" "surveye##acknowledgments"}{...}
{viewerjumpto "Also see" "surveye##alsosee"}{...}
{vieweralsosee "return" "help return"}{...}
{vieweralsosee "weight" "help weight"}{...}
{vieweralsosee "suso" "help suso"}{...}
{vieweralsosee "survEye" "help surveye"}{...}

{title:surveye - Interactive survey dashboards}

{pstd}
Turn a Survey Solutions or SurveyCTO questionnaire and its Stata data into a
single interactive HTML dashboard. Readers can filter, explore, and download
results in a browser without Stata.{p_end}

{pstd}
{bf:Jump to:} {help surveye##quickstart:Quick start} |
{help surveye##syntax:Syntax} | {help surveye##options:Options} |
{help surveye##examples:Examples} | {help surveye##results:Stored results} |
{help surveye##troubleshooting:Troubleshooting}{p_end}

{marker quickstart}{...}
{title:Quick start}

{pstd}
Load your survey data, inspect the matching questionnaire, then build the
dashboard. Replace the input filenames below with your own files. These
commands write dashboard.html in the current working directory; choose a
different output name or add {opt replace} if that file already exists.{p_end}

{p 8 8 2}{cmd:use "survey_data.dta", clear}{p_end}
{p 8 8 2}{cmd:surveye describe using "questionnaire.html", detail}{p_end}
{p 8 8 2}{cmd:surveye using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("dashboard.html") open}{p_end}

{pstd}
The {cmd:use ..., clear} step replaces the data in memory. Save any unsaved
work first. {cmd:surveye} itself preserves the loaded data. In do-files,
{cmd:///} continues a command on the next physical line; in Stata's Command
window, enter each continued command on one line.{p_end}

{pstd}
No data yet? Use {cmd:surveye demo} for a clearly marked simulated preview.
To choose options in a browser and copy a command, use
{cmd:surveye configure}. See {help surveye##demo:Simulated preview} and
{help surveye##configure:Command configurator}.{p_end}

{marker syntax}{...}
{title:Syntax}

{pstd}{bf:Build from the data in memory}{p_end}

{p 8 12 2}
{cmd:surveye} [{varlist}] {ifin} {cmd:using} {it:questionnaire}
[{it:weight}]{cmd:,} {opt saving(filename)} [{it:options}]{p_end}

{p 8 12 2}
{cmd:surveye build} [{varlist}] {ifin} {cmd:using} {it:questionnaire}
[{it:weight}]{cmd:,} {opt saving(filename)} [{it:options}]{p_end}

{pstd}
Use one native weight specification, such as {cmd:[aw=wvar]},
{cmd:[fw=wvar]}, {cmd:[iw=wvar]}, or {cmd:[pw=wvar]}, after the input filename
and before the comma. The weight must name one numeric variable; there is no
{cmd:weight()} option. See {help surveye##weights:Weights}.{p_end}

{pstd}{bf:Inspect, configure, or simulate}{p_end}

{p 8 12 2}
{cmd:surveye describe using} {it:questionnaire}
[{cmd:,} {opt detail} {opt strict} {opt diagnostics(filename)}
{opt replace}]{p_end}

{p 8 12 2}
{cmd:surveye configure using} {it:questionnaire}{cmd:,}
{opt saving(filename)} [{opt replace} {opt open}]{p_end}

{p 8 12 2}
{cmd:surveye demo using} {it:questionnaire}{cmd:,} {opt saving(filename)}
[{opt n(#)} {opt seed(#)} {opt maxpanels(#)} {opt ci} {opt level(#)}
{opt replace} {opt open} {it:presentation_options}]{p_end}

{pstd}
Only the build forms accept a {it:varlist}, {cmd:if}, {cmd:in}, and weights.
Actions must be spelled in full. {cmd:build} is optional and is useful when
the first selected variable is named {cmd:build}, {cmd:describe},
{cmd:demo}, or {cmd:configure}: repeat that variable after the action,
as in {cmd:surveye build build sales using "questionnaire.html", saving("results.html")}.
Use the documented command {cmd:surveye}; the former commands
{cmd:suso_dashboard} and {cmd:surveydash} are not installed aliases.{p_end}

{marker description}{...}
{title:Description and requirements}

{pstd}
The questionnaire supplies question wording, response labels, and section
order. The main command matches those items to the current dataset and writes
an HTML dashboard containing the selected analysis values. File contents,
rather than extensions, identify supported questionnaire formats:{p_end}

{phang}
{bf:Survey Solutions questionnaire-preview HTML.}
Use the questionnaire preview exported from Survey Solutions.{p_end}

{phang}
{bf:SurveyCTO form-definition XML.}
The parser reads groups, repeats, choice lists, translations, and calculate
fields. Obtain the XML definition from the form files in SurveyCTO.{p_end}

{phang}
{bf:SurveyCTO printable-form HTML.}
Supported Field/Question/Answer tables retain group sections, nested and
repeated subgroups, choice lists, and relevance conditions. Printable files
do not encode field types: choices import as single-select questions and
open or note fields as text. Notes without a matching data column drop out.
Use the XML definition when the printable layout is not recognized.
Split multiple-select exports such as {cmd:field_1}, {cmd:field_2}, and so on
are reassembled automatically.{p_end}

{pstd}
The declared minimum is Stata 16 with a working Java integration. Install the
complete package so the ADO file and its matching engine JAR are both on the
ado-path. Readers need a modern browser. The dashboard's charts, controls,
points, and embedded boundaries work offline; background map tiles need
access to their provider. See {help surveye##map:GPS maps}.{p_end}

{pstd}
The main command works with one rectangular dataset at a time. Roster rows
must have a clearly defined analysis unit. For preservation and disclosure
details, see {help surveye##data:Data rules and privacy}.{p_end}

{marker options}{...}
{title:Options}

{pstd}
These options apply to the main command and {cmd:surveye build}.
The shorter action-specific lists appear under
{help surveye##describe:Inspect a questionnaire},
{help surveye##configure:Command configurator}, and
{help surveye##demo:Simulated preview}. Quote filenames or text containing spaces.
Write option names in full for reusable do-files.{p_end}

{dlgtab:Output}

{phang}
{opt saving(filename)} is required and names the HTML dashboard.  The
{cmd:.html} extension is appended unless the filename already ends in
{cmd:.html} or {cmd:.htm}. The destination directory must exist.{p_end}

{phang}
{opt replace} permits existing dashboard and diagnostics outputs to be replaced.
Use distinct filenames for the questionnaire, output, diagnostics, logo, and
boundaries; protected inputs may not be overwritten.{p_end}

{phang}
{opt open} opens the completed dashboard in the system browser.  The file is
still saved if Stata cannot open the browser.{p_end}

{phang}
{opt diagnostics(filename)} writes an optional technical log for troubleshooting.
It must differ from the questionnaire and other inputs or outputs.
An existing diagnostics file requires {opt replace}.{p_end}

{marker selection}{...}
{dlgtab:Select indicators}

{phang}
{it:varlist} selects ordinary Stata variables to chart.  If both {it:varlist}
and {opt questions()} are omitted, the command considers all chartable
questionnaire items present in the data.  Variables used only for filters,
highlights, weights, or maps are added to the temporary export automatically
and are not charted unless selected.  Variables used only by the optional
profile table or local-currency/USD switch are handled the same way.{p_end}

{phang}
{opt questions(question_names)} selects logical questionnaire question
names.  Separate names with spaces or commas and quote the list, for example
{cmd:questions("services_used certifications_used")}.  This option is useful
when a multiselect question {cmd:q} is stored as {cmd:q__1}, {cmd:q__2}, and so
on, with no variable named {cmd:q}.  Do not use wildcards.  {it:varlist} and
{opt questions()} may be combined; duplicate selections are removed.  Names
shown by {cmd:surveye describe ..., detail} are suitable inputs.{p_end}

{phang}
{opt exclude(names)} omits questionnaire items.  It accepts Stata varlists and
logical questionnaire question names.  A name may not be both selected or
chart-overridden and excluded.{p_end}

{phang}
{opt sections(numlist)} keeps questionnaire sections by number.  Use
{cmd:surveye describe ..., detail} to see the section numbers.{p_end}

{phang}
{opt sectionmatch(text)} keeps sections whose title contains any term in a
pipe-separated list, such as {cmd:sectionmatch("profile|employment")}.  It may
not be combined with {opt sections()}.{p_end}

{phang}
{opt customsections(spec)} reorganizes all selected charts.  Separate groups
with {cmd:|} and write each group as {cmd:Title: names}. Use literal names separated by spaces,
not Stata ranges or wildcards.  Unassigned charts
are placed in {it:Other indicators}.  Example:
{cmd:customsections("Firm profile: sector size|Performance: sales employment")}.
It may not be combined with {opt addtosections()}.{p_end}

{phang}
{opt showempty} retains chartable questionnaire items whose data columns are
absent, including during automatic selection. It is mainly useful for
checking coverage or making templates. Items with present but entirely
missing columns can already appear as empty cards.{p_end}

{phang}
{opt strict} stops on undeclared selected variables and promotes questionnaire
parser and malformed-CSV warnings to errors.  Variables deliberately declared in
{opt customvars()} are allowed under {cmd:strict}.{p_end}

{phang}
{opt maxpanels(#)} limits indicator charts while preserving questionnaire
order.  The default is 100; {cmd:maxpanels(0)} removes the limit.  Allowed
values are 0 through 5,000.  Explicit custom variables are processed first so
the cap does not silently place them behind a long questionnaire.  The command
stops if {opt customvars()} alone exceeds a positive {opt maxpanels()} limit.{p_end}

{marker customvars}{...}
{dlgtab:Variables not in the questionnaire}

{phang}
{opt customvars(varlist)} adds constructed indicators, quality-control fields,
administrative classifications, or other variables in memory even when they
do not exist in the questionnaire.  It is additive: normal questionnaire
selection still applies, and the custom variables are added to it.{p_end}

{pmore}
The variable label becomes the chart title by default; the variable name is
used when the label is empty.  Generic Survey Solutions labels such as
{it:Calculated variable of type String} also fall back to the variable name.
Numeric variables receive distributions.
Variables formatted as {cmd:%tc}, {cmd:%td}, {cmd:%tw}, {cmd:%tm}, {cmd:%tq},
{cmd:%th}, or {cmd:%ty} receive date charts.  Strings receive categorical
charts, and numeric variables with value labels receive categorical charts.
Observed Stata value-label text is retained when the number of levels does not
exceed {opt maxcategories()}.  Custom variables not otherwise placed appear in
{it:Additional indicators}.{p_end}

{pmore}
Business-calendar {cmd:%tb} values are not currently interpreted as dates
because their calendar rules are dataset-specific.  Convert or export them to
one of the supported date representations before including them in
{opt customvars()}.{p_end}

{phang}
{opt addtosections(spec)} places variables declared in {opt customvars()} in
any selected section.  Separate assignments with {cmd:|}.  The target before
the colon may be an exact section title (ignoring case), a number, or a new
text title.  A number first matches the questionnaire's original section
number; when that number is not selected, it matches the visible dashboard
position (1, 2, 3, and so on).  A new text title creates a new section; an
unmatched numeric target is an error.  Examples:{p_end}

{p 8 8 2}{cmd:addtosections("Firm profile: qc_score risk_band")}{p_end}

{p 8 8 2}{cmd:addtosections("3: qc_score|Quality checks: risk_band")}{p_end}

{pmore}
{opt addtosections()} requires {opt customvars()}.
Only variables listed in {opt customvars()} may be moved.  Unmentioned custom
variables remain in {it:Additional indicators}.  {opt addtosections()} may not
be combined with {opt customsections()}.{p_end}

{marker families}{...}
{dlgtab:Related-variable families}

{phang}
SurvEye automatically combines high-confidence families of related binary
variables into one compact comparison figure.  For example,
{cmd:srib8a}, {cmd:srib8b}, and {cmd:srib8c} can be displayed as three labelled
rows in a single card.  Automatic grouping is conservative: members must have
a recognizable letter suffix, compatible response categories, and the same
final dashboard section.  Survey Solutions multiselect storage columns such as
{cmd:q__1} and {cmd:q__2} are never treated as a suffix family.{p_end}

{phang}
{opt vargroups(spec)} defines groups manually.  Separate groups with {cmd:|}
and write each as {cmd:Title:: varlist}.  The double colon separates the
displayed title from an ordinary Stata varlist, so ranges and wildcards are
expanded by Stata. Every group requires 2 through 20 variables, and a
variable may occur in only one manual group. Members must have the same
compatible single-select chart kind, response categories, and final section.
Numeric, multiselect, and completion panels cannot be grouped this way.  Members must already be selected
by the main {it:varlist}, {opt questions()}, or {opt customvars()}.  Example:{p_end}

{p 8 8 2}{cmd:vargroups("Digital channels:: srib8a srib8b srib8c|Business support:: srib9a srib9b srib9c")}{p_end}

{pmore}
Questionnaire text supplies each row label.  For a custom variable, its Stata
variable label is used, with the variable name as the fallback.  Manual groups
take precedence over automatic grouping and inherit the final section shared
by their members.  Use {opt customsections()} or {opt addtosections()} first
when custom variables need to be placed together.{p_end}

{phang}
{opt ungroupvars(varlist)} keeps named variables as separate cards while
leaving automatic grouping available elsewhere.  Use {opt noautogroups} to
turn off all automatically detected families.  Explicit {opt vargroups()}
remain active with {opt noautogroups}.  A variable may not appear in both
{opt vargroups()} and {opt ungroupvars()}.{p_end}

{marker comparisons}{...}
{dlgtab:Binary comparisons}

{phang}
{opt compare(varlist)} combines 2 through 12 selected binary variables in one
grouped horizontal-bar comparison.  Each bar reports the affirmative response
share, and {opt compareby(varname)} supplies the comparison groups.  Both
options are required together.  The {opt compareby()} variable is exported
for the comparison but is not added as an indicator card unless it is also
selected normally.  Example:{p_end}

{p 8 8 2}{cmd:compare(srib8a srib8b srib8c) compareby(city)}{p_end}

{phang}
{opt comparetitle(text)} replaces the automatically generated comparison
title. It requires {opt compare()} and {opt compareby()}.{p_end}

{phang}
{opt comparelevels(levels)} limits and orders the displayed values of
{opt compareby()}.  Separate exact raw values or displayed value labels with
{cmd:|}; for example,
{cmd:comparelevels("01_Colombo|02_Kandy|03_Jaffna")}.  Without this option,
valid observed comparison levels are used in their natural order.  This option requires {opt compare()} and {opt compareby()}.
The grouping variable may not also be one of the compared indicators. The final
comparison must contain 2 through 5 valid levels; use {opt comparelevels()}
to select a subset when the grouping variable has more levels.{p_end}

{pmore}
Comparison variables must be selected and must have a recognizable binary
affirmative response.  Missing values, special response codes, {ifin}, and
weights follow the same rules as the corresponding binary cards.  Confidence
interval whiskers are never drawn on these binary comparison bars, including
when {opt ci} is specified.{p_end}

{marker charts}{...}
{dlgtab:Controls and chart choices}

{phang}
{opt filters(varlist)} creates dashboard filters from low-cardinality
variables.  If omitted, the engine may suggest up to three conservative
filters.  Use this option for required controls, particularly with translated
questionnaires.  A requested filter with more than {opt maxcategories()}
levels is rejected.{p_end}

{pmore}
Choices come only from observed valid values; codes named by
{opt missingcodes()} are never offered.  Numeric choices are compared by
numeric value, multiselect choices match when any selected option is present,
and privacy-reduced text or media questions offer localized
{it:Answered}/{it:Missing} choices.  An explicitly requested categorical
filter with no observed valid values stops with an explanatory error instead
of showing controls that can only produce zero results.{p_end}

{pmore}
The sticky search/filter panel is collapsed by default so it does not cover the
figures.  Select {bf:Show filters} to reveal its contents and {bf:Hide filters}
to return to the compact toolbar.  Collapsing the panel preserves the search
text and selected choices.  The toolbar keeps {bf:Reset all}, the live interview
count, and - when applicable - the number of active filter choices visible.
{bf:Reset all} clears both the response filters and the indicator search.{p_end}

{phang}
{opt highlights(varlist)} creates up to six summary cards. No cards are requested when it is omitted.{p_end}

{phang}
{opt keymessages(text)} adds up to six editorial messages.  Separate messages
with {cmd:|}.  An optional heading precedes {cmd:::}; for example,
{cmd:keymessages("Coverage::Completed interviews|Caution::Weighted estimates")}.{p_end}

{phang}
Automatic chart selection uses horizontal percentage bars for categorical and
multiselect questions, split bars for binary and answered/missing items,
histograms for numeric variables, and time distributions for dates.  Donuts
are not selected automatically.{p_end}

{phang}
{opt bars(varlist)}, {opt donuts(varlist)}, and {opt histograms(varlist)}
override compatible automatic chart choices.  A variable may appear in only
one override.  With a main {it:varlist} or {opt questions()} selection, an
override must also be selected there or in {opt customvars()}.{p_end}

{phang}
Small integer-valued numeric variables are recognized automatically as
discrete counts when their question text and observed support strongly look
like counts, such as number of workers or household members.  Detection is
conservative and uses the actual analysis sample after {ifin}, weights, and
missing-code exclusions.  Readable ranges display one bar for every integer,
including zero-frequency gaps.  Wider ranges automatically use equal-width,
integer-aligned bins.  A numeric x axis supplies regular round-number ticks.
Negative special-response codes are excluded; legitimate negative measurements
remain valid.  Every valid measurement
remains in the chart and summary.  A dotted mean-plus-three-standard-deviations
guide is drawn only when it falls strictly inside the plotted range.  The
numeric Stats tab remains available and updates immediately with filters.{p_end}

{phang}
{opt discrete(varlist)} forces numeric counts, bounded scores, or numeric
category codes to use this smart integer distribution.  It preserves exact
values when readable and bins only a wide integer range. At least one valid
observation must remain after exclusions, and all valid observations must be
integer-valued; otherwise, the command reports an error.
{opt continuous(varlist)} forces numeric variables to use a continuous
histogram and numeric Stats table, even when all observed values are integers
or a Stata value label is attached.  Both options require numeric, non-date
variables that are also selected for the dashboard.  Examples:{p_end}

{p 8 8 2}{cmd:discrete(employees visits) continuous(revenue productivity_score)}{p_end}

{phang}
{opt noautodiscrete} disables only automatic discrete-count detection.
Explicit {opt discrete()}, {opt continuous()}, and chart overrides still
apply.  {opt histograms()} explicitly requests continuous behavior and may be
combined redundantly with {opt continuous()}; it conflicts with
{opt discrete()}.  {opt bars()} and {opt donuts()} may accompany
{opt discrete()} but conflict with {opt continuous()}.{p_end}

{phang}
{opt maxcategories(#)} sets the maximum categories for filters and categorical
figures.  The default is 12; allowed values are 2 through 200.  Compact mode
shows at most seven displayed levels.  Less frequent levels are combined as
{it:Other}, while percentages continue to use the full valid denominator.
This option does not limit {opt discrete()} or continuous numeric distributions.{p_end}

{phang}
{opt missingcodes(numlist)} explicitly excludes extra numeric nonresponse or
sentinel codes from chart denominators and numeric/date calculations, for
example {cmd:missingcodes(-999 -998 999)}.  Questionnaire-defined special
responses remain visible and muted in categorical figures.  Negative special
codes are automatically excluded from numeric distributions, statistics,
and numeric filters because response codes such as
{cmd:-4} and {cmd:-9} are not measurements.  Nonnegative substantive
shortcuts and legitimate negative-valued questions remain valid.  Use
{opt missingcodes()} for additional undeclared sentinels.{p_end}

{marker weights}{...}
{dlgtab:Weights}

{pstd}
Weights use ordinary Stata command syntax; they are not options.  Place one
single-variable weight specification after the {cmd:using} filename and before
the comma:{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [aw=wmedian], ///}{p_end}
{p 12 12 2}{cmd:saving("analytic.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [fw=frequency], ///}{p_end}
{p 12 12 2}{cmd:saving("frequency.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [iw=importance], ///}{p_end}
{p 12 12 2}{cmd:saving("importance.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [pw=pop], ///}{p_end}
{p 12 12 2}{cmd:saving("population.html") replace}{p_end}

{pstd}
The weight must resolve to one numeric variable.  Negative values are rejected.
Frequency weights must contain integers.  Observations with zero or missing
weights are excluded for all four types, so every retained weight is positive.
The command stops if no observations remain after {cmd:if}, {cmd:in}, and
weight restrictions.  A weight-only variable is exported for calculation but
is not charted unless selected.  See {help weight}.{p_end}

{pstd}
Weights are applied to shares, histograms, means, medians, quantiles, standard
deviations, and numeric Stats tables.  They do not replace {cmd:svyset}, and
{cmd:surveye} does not estimate design-based standard errors.  When {opt ci}
is supplied, frequency-weighted Wilson intervals use the weighted count.
Analytic- and probability-weighted intervals use a labelled Kish effective-
sample-size approximation;
they do not account for strata, primary sampling units, finite-population
corrections, or other complex-design features.  Importance weights have no
general sampling interpretation, so {cmd:surveye} automatically suppresses a
requested interval for {cmd:iweight} and displays an explanatory note.{p_end}

{pstd}
Supplying any supported weight automatically adds a {bf:Weighted estimates}
switch to the dashboard.  It is on initially.  Turning it off recalculates
charts, comparisons, numeric summaries, and the optional profile table with
one unit per row; turning it on restores the supplied weight.  Raw sample
counts remain raw in either mode.  Both modes use the same exported analysis
sample, so the switch does not restore observations removed by {cmd:if},
{cmd:in}, or zero or missing weights when the file was built.{p_end}

{marker currency}{...}
{dlgtab:Local currency and USD}

{phang}
{opt usdvars(varlist)} identifies numeric, non-date monetary variables that
readers may display in local currency or US dollars.  It requires
{opt usdrate()}. These variables cannot belong to manual groups or binary
comparison panels. Selected monetary charts use continuous numeric distributions,
even when their observed values are integers.{p_end}

{phang}
{opt usdrate(#)} gives the fixed number of local-currency units per US dollar.
For example, with {cmd:usdrate(300)}, a stored local value of 30,000 is shown
as USD 100 when the USD switch is on.  The rate must be positive, and {opt usdvars()} and {opt usdrate()}
must be supplied together.{p_end}

{phang}
{opt currency(code)} gives the local-currency code printed with converted
variables, for example {cmd:currency(LKR)}, {cmd:currency(KES)}, or
{cmd:currency(PKR)}.  It requires {opt usdvars()}. When it is omitted, the label is
{it:Local currency}.{p_end}

{pmore}
The dashboard starts in local currency.  Its USD switch updates charts,
numeric Stats, and {cmd:mean}, {cmd:median}, or {cmd:sum} cells in the optional
profile table for variables named in {opt usdvars()}.  It does not change the
embedded source values.  SurvEye does not retrieve a current or historical
exchange rate; choose a rate appropriate to the survey reference period and
document it with {opt note()} or {opt source()}.{p_end}

{p 8 8 2}{cmd:surveye sales costs using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("financials.html") usdvars(sales costs) usdrate(300) ///}{p_end}
{p 12 12 2}{cmd:currency(LKR) replace open}{p_end}

{marker profiletable}{...}
{dlgtab:Side-by-side profile table}

{phang}
{opt tableby(varname)} supplies the grouping variable whose displayed levels
become table rows.  {opt tablevars(varlist)} supplies the indicator columns.
The two options are required together, and the grouping variable may not
also be a table indicator. All other {cmd:table*()} options require this pair.
Variables used only in the table are
exported automatically and do not become chart cards unless selected normally.
The responsive table appears near the top of the dashboard, before the
detailed chart sections.{p_end}

{phang}
{opt tablestats(spec)} chooses each indicator's statistic.  Give one entry per
{opt tablevars()} variable, in the same order, separated by {cmd:|}.  Allowed
entries are {cmd:auto}, {cmd:share}, {cmd:share:}{it:code}, {cmd:mean},
{cmd:median}, and {cmd:sum}.  {cmd:auto} uses the median for numeric
distribution variables and an affirmative share for other compatible
variables.  {cmd:share} also uses a recognized affirmative category;
{cmd:share:}{it:code} identifies the intended raw response code explicitly.
{cmd:mean}, {cmd:median}, and {cmd:sum} require numeric variables.  Omitting
{opt tablestats()} uses {cmd:auto} for every column.{p_end}

{phang}
{opt tablelabels(labels)} overrides the indicator column headings.  Supply one
label per {opt tablevars()} variable, in the same order, separated by {cmd:|}.
Without this option, SurvEye uses each variable's available label from the
questionnaire or Stata metadata; the variable name is the fallback when that
label is empty.{p_end}

{phang}
{opt tabletitle(text)} supplies the profile-table title.  The default is
{it:Summary table}.{p_end}

{phang}
{opt tablesubtitle(text)} supplies a short explanation immediately below the
table title.{p_end}

{phang}
{opt tabletotal(text)} changes the label of the all-group reference row, for
example {cmd:tabletotal("Sri Lanka (all surveyed locations)")}.  Every profile
table includes that reference; its default label is
{it:All filtered interviews}.  The table ignores only its own
{opt tableby()} dashboard filter,
so every grouping row and the reference remain visible for comparison;
selected grouping rows may be highlighted.  Every other active filter is
honored.  The reference, table rows, and cells also follow the current weight
and local-currency/USD switches.{p_end}

{phang}
{opt tableweightlabel(text)} changes the weighted-total column heading, for
example {cmd:tableweightlabel("Est. firms")}.  Raw {it:n} is always shown.
The weighted total is the sum of the active Stata weights.  Interpret it
according to the supplied weight type; only an appropriate expansion weight
should be described as an estimated population count.  The column appears
only when a Stata weight was supplied and {bf:Weighted estimates} is on; it is
hidden in unweighted mode.  Its default heading is {it:Weighted total}.{p_end}

{pmore}
Table shares, means, medians, sums, raw counts, and conditional weighted totals
recalculate immediately with the dashboard controls.  Use a concise set of
decision-relevant columns so the table remains readable on smaller screens.{p_end}

{p 8 8 2}{cmd:surveye women_led sales banked digital_channel using ///}{p_end}
{p 12 12 2}{cmd:"questionnaire.html" [pw=pop], saving("profile.html") filters(stratum ///}{p_end}
{p 12 12 2}{cmd:owner_type) usdvars(sales) usdrate(300) currency(LKR) tableby(stratum) ///}{p_end}
{p 12 12 2}{cmd:tablevars(women_led sales banked digital_channel) ///}{p_end}
{p 12 12 2}{cmd:tablestats("share:1|median|share:1|share:1") ///}{p_end}
{p 12 12 2}{cmd:tablelabels("Women-led|Median sales|Banked|Digital channel") ///}{p_end}
{p 12 12 2}{cmd:tabletitle("Stratum profile") ///}{p_end}
{p 12 12 2}{cmd:tablesubtitle("Key indicators side by side") ///}{p_end}
{p 12 12 2}{cmd:tabletotal("Sri Lanka (all surveyed locations)") ///}{p_end}
{p 12 12 2}{cmd:tableweightlabel("Est. firms") replace open}{p_end}

{marker ci}{...}
{dlgtab:Confidence intervals and numeric statistics}

{pstd}
Confidence intervals are off by default.  This keeps compact dashboards easy
to scan and avoids inferential marks that can be mistaken for ranges covering
part of a binary stacked bar.{p_end}

{phang}
{opt ci} requests pointwise Wilson confidence intervals for ordinary
categorical and multiselect horizontal bars.  Binary yes/no cards,
answered/missing completion cards, and donuts never display confidence-
interval text or whiskers, even when {opt ci} is supplied.{p_end}

{phang}
{opt level(#)} sets the requested interval's confidence level.  It requires
{opt ci}; the default with {opt ci} is 95, and the value must be greater than
50 and less than 100.{p_end}

{pmore}
Unweighted requested intervals use the valid raw count.  Frequency-weighted
intervals use the weighted count.  Analytic- and probability-weighted intervals use a
Kish effective-sample-size approximation.  They are descriptive pointwise
intervals, not design-based survey estimates, and do not adjust for strata,
clusters, finite-population corrections, or other complex-design features.
Importance weights automatically suppress requested intervals because they
have no general sampling interpretation.{p_end}

{pstd}
Each numeric card has {it:Distribution} and {it:Stats} tabs.  The distribution
includes every valid measurement on a regular numeric axis.  A dotted
{it:Mean + 3 SD} reference is shown only when the standard deviation is positive
and the reference lies strictly inside the plotted range.  The Stats tab
reports valid raw n, missing/excluded n, mean, standard deviation, minimum,
maximum, p25, median, p75, and the mean-plus-three-standard-deviations
reference.  It recalculates immediately whenever a dashboard filter changes,
including while the Stats tab is open.  Weighted dashboards identify these
statistics as descriptive weighted summaries and report the valid weighted
sum.  SurvEye does not classify or label observations as Tukey outliers.{p_end}

{marker map}{...}
{dlgtab:GPS map}

{phang}
{opt latitude(varname)} and {opt longitude(varname)} request a Leaflet map.
Both variables must be numeric and both options must be supplied.
All remaining map options require this coordinate pair. The coordinate columns
are exported automatically but do not become charts unless also selected.
Latitude must lie between -90 and 90 and longitude between -180 and 180.
Missing or out-of-range pairs are excluded from the map; no valid pairs is an
error. Valid points outside the selected boundary remain plotted and counted
separately.{p_end}

{phang}
{opt country(name_or_code)} is required for a GPS map.  Use a country name,
ISO-2 code, or ISO-3 code, such as {cmd:country(Kenya)} or {cmd:country(KEN)}.{p_end}

{phang}
{opt boundaries(filename)} supplies a ZIP with a polygon shapefile
({cmd:.shp}, {cmd:.dbf}, and preferably {cmd:.prj}) for detailed boundaries.
Coordinates and boundaries must use WGS84 longitude/latitude.  Without this
option, a bundled country outline is used.  Country and Admin-2 lines are drawn
as WGS84 Leaflet rings aligned with the points and tiles; antimeridian
geometries are normalized to one longitude branch. The .shp and .dbf must
belong to the same layer. Country matching needs one of {cmd:NAM_0},
{cmd:ISO_A3}, {cmd:ISO_A2}, or {cmd:WB_A3}; Admin-2 detail needs one of
{cmd:NAM_2}, {cmd:ADM2CD_C}, or {cmd:ADM2_CODE} in the DBF.{p_end}

{phang}
{opt maplevel(country|admin2)} selects boundary detail.  The default is
{cmd:country} without {opt boundaries()} and {cmd:admin2} with a boundary ZIP.
{cmd:admin2} requires a compatible ZIP. Explicit {cmd:maplevel(country)}
uses the bundled country geometry even when a ZIP is supplied. A projected
coordinate system is rejected; missing projection metadata produces a warning,
so verify that such a shapefile is already WGS84 longitude/latitude.{p_end}

{phang}
{opt maptype(points|cluster|heat)} selects the point display.  The default is
{cmd:points}: every valid row receives its own circle marker and nearby points
are not collapsed.  Exact duplicate coordinates are separated slightly on
screen so every marker remains selectable; popups show the original coordinate.
Each point can receive keyboard focus and opens its localized popup with Enter
or Space.
{cmd:cluster} and {cmd:heat} are explicit aggregated alternatives.{p_end}

{phang}
{opt basemap(name)} chooses the initial Leaflet base layer:
{cmd:google_hybrid}, {cmd:google_sat}, {cmd:google_road}, or {cmd:osm}.  The default is {cmd:google_hybrid}.  The options follow
{cmd:esqc_gps}: Google Hybrid uses satellite imagery and labels,
{cmd:google_sat} uses satellite imagery, {cmd:google_road} uses the road map,
and {cmd:osm} uses OpenStreetMap.  The map layer control can switch among all
four after the file opens.{p_end}

{pmore}
Leaflet, survey points, and boundary geometry are embedded in the dashboard.
Base-map tiles are fetched when the map opens, so a map-enabled dashboard needs
internet access to the selected provider.  The {cmd:esqc_gps}-compatible Google
choices use keyless compatibility tile endpoints; they are not an authenticated
Google Maps Platform integration, and their availability or access policy can
change.  Before distributing a dashboard, verify that the selected provider is
approved for the intended use and follow its current terms and attribution
rules.  Tile requests contain map coordinates, not other survey variables.{p_end}

{phang}
{opt mapby(varname)} colors or groups the map by a variable with at most ten
observed categories after {opt missingcodes()} exclusions.  A point whose map
group is an excluded code remains visible in the default point color but is
not added to the legend.{p_end}

{phang}
{opt maptitle(text)} replaces the default map title.{p_end}

{marker presentation}{...}
{dlgtab:Themes and presentation}

{phang}
{opt uilanguage(auto|english|arabic|urdu)} selects the language of interface
controls, summaries, map text, chart descriptions, and empty-state messages.  Technical
parser warnings, diagnostics, and Stata console messages remain in English.  The
default is {cmd:auto}.  The aliases {cmd:en}, {cmd:ar}, and {cmd:ur} are
accepted.  Auto gives priority to a questionnaire declaration of Arabic
({cmd:ar}) or Urdu ({cmd:ur}).  Otherwise, predominantly Arabic-script
questionnaire text selects Arabic, with Urdu-specific characters selecting
Urdu; incidental Arabic text in an otherwise non-Arabic instrument does not
change the interface.  Other text selects English.  Questionnaire wording and user-supplied
titles, notes, sources, and messages are never translated.{p_end}

{phang}
{opt direction(auto|ltr|rtl)} selects document and component direction.  The
default is {cmd:auto}, which follows the resolved interface language: Arabic
and Urdu use {cmd:rtl}, while English uses {cmd:ltr}.  An explicit {cmd:rtl} or
{cmd:ltr} overrides layout direction but does not translate text.  Thus,
{cmd:uilanguage(english) direction(auto)} stays left-to-right even for an
Arabic questionnaire.  The output
sets matching HTML {cmd:lang} and {cmd:dir} attributes for browsers and
assistive technology.  RTL mode mirrors navigation, controls, tables,
horizontal chart axes and direct labels, and Leaflet controls; it does not
merely align the surrounding text.{p_end}

{phang}
{opt title(text)} and {opt subtitle(text)} set the dashboard heading.  The
questionnaire title is used when {opt title()} is omitted.  A pair of asterisks marks emphasis:
{cmd:title("The shape of *informality*")} renders the marked words as a
cyan accent in the masthead, and marked words in {opt subtitle()} render in
bold.  Single asterisks pass through unchanged, and the marks never reach the
browser tab title.{p_end}

{phang}
{opt byline(text)} signs the masthead with up to four parts separated by
{cmd:|} in the order label, name, role, and email:
{cmd:byline("Task Team Leader|A. Rehman|Economist|arehman@worldbank.org")}.
Any part may be left empty and the email becomes a mailto link.  The byline
follows World Bank task-attribution practice and prints with the dashboard.{p_end}

{phang}
{opt theme(name)} selects the overall finish. Accepted values are {cmd:editorial},
{cmd:worldbank}, {cmd:clean}, {cmd:forest}, and {cmd:dark}.
The default is {cmd:editorial}: a warm paper canvas, subtle cyan/gold glow,
World Bank role colors, serif heading hierarchy, rounded cards, shallow shadows,
and reduced-motion-safe subtle entrance transitions.  The preset is entirely
survey-agnostic and does not change questions, calculations, filters, or chart
selection.  Explicit {cmd:worldbank}, {cmd:clean}, {cmd:forest}, and {cmd:dark}
use their own base appearance unless an individual layer below is overridden.{p_end}

{phang}
{opt background(auto|glow|paper|plain)} controls the page canvas.  The default is {cmd:auto} for non-editorial themes, which leaves
the base-theme canvas unchanged. The editorial default is
{cmd:glow}; it places restrained cyan and gold radial light over warm paper.{p_end}

{phang}
{opt typography(name)} controls heading hierarchy. Accepted values are
{cmd:auto}, {cmd:editorial}, {cmd:modern}, and {cmd:system}.
The default is {cmd:editorial} with the editorial theme and {cmd:auto} otherwise.
{cmd:editorial} uses a serif display stack for headings and key figures while
retaining a highly legible sans-serif body.  No web-font request is made.{p_end}

{phang}
{opt corners(auto|rounded|soft|square)} and
{opt shadow(auto|soft|lifted|none)} control card finishing independently of the
color theme. Editorial defaults to {cmd:rounded} corners and {cmd:soft}
shadows; both default to {cmd:auto} for the other themes.{p_end}

{phang}
{opt motion(none|subtle|reveal)} controls entrance motion.  Both animated modes
use an intersection observer so sections animate only when first visible and
honor the browser's reduced-motion preference.  The editorial default is
{cmd:subtle}; the other themes default to {cmd:none}.{p_end}

{phang}
{opt pagewidth(#)} sets the maximum content width in pixels.  It accepts 0 or an integer from 960 through 2,000.
For the editorial theme, omitted {opt pagewidth()} or {cmd:pagewidth(0)}
resolves to 1,280 pixels. For the other themes, 0 retains their base width.
Smaller screens remain responsive.{p_end}

{phang}
{opt density(compact|comfortable)} controls spacing.  The default,
{cmd:compact}, uses balanced rows of up to three panels, short charts, focused
labels, and a collapsed map summary.  Incomplete rows are repacked as equal
pairs or a full-width single panel, so card edges remain aligned without adding
page height.  {cmd:comfortable} uses a roomier two-column grid,
larger figures, and an initially open map.  Meanings and denominators do not
change.{p_end}

{phang}
{opt logo(filename)} embeds a local PNG, JPEG, GIF, or SVG logo.{p_end}

{phang}
{opt note(text)}, {opt source(text)}, and {opt disclaimer(text)} add context.
Document the universe, reference period, weighting, source, and disclosure
limitations.  Without {opt disclaimer()}, a conservative internal-working and
disclosure-review notice is shown.{p_end}

{marker describe}{...}
{title:Inspect a questionnaire}

{pstd}
{cmd:surveye describe} reads only the questionnaire and does not change
the dataset in memory.  {opt detail} displays numbered sections, chartable
variable names, possible filters, and possible GPS fields.  {opt strict}
promotes parser warnings to errors.  It does not test whether the listed items match the data in memory.
Omit {opt strict} while investigating warnings. {opt diagnostics(filename)}
writes a technical log, with {opt replace} required to overwrite that log.
No dashboard is created.{p_end}

{marker configure}{...}
{title:Command configurator}

{pstd}
{cmd:surveye configure} creates an interactive HTML form for selecting
dashboard options and copying the resulting Stata command.  It reads the
questionnaire and, when data are loaded, marks available columns and numeric
weight candidates.  It does not change the dataset in memory.
{opt saving(filename)} is required and is used exactly as supplied; include
{cmd:.html} for a browser-ready filename.  {opt replace} permits overwriting
an existing configurator, and {opt open} opens it in the system browser.{p_end}

{p 8 8 2}{cmd:surveye configure using "questionnaire.html", saving("configure.html") ///}{p_end}
{p 12 12 2}{cmd:replace open}{p_end}

{pstd}
The configurator copies a command for you to run in Stata; it does not build
from your data in the browser. Review the copied command with the intended
dataset loaded. If Worldbank is selected, include {cmd:theme(worldbank)}
explicitly: a copied command without {opt theme()} uses editorial.{p_end}

{marker demo}{...}
{title:Simulated preview}

{pstd}
{cmd:surveye demo} builds a dashboard without a dataset.  Every preview
is visibly marked {it:SIMULATED -- PREVIEW ONLY}.  {opt n(#)} sets the number
of simulated records (integer 1 through 100,000; default 240).
{opt seed(#)} accepts an integer seed; its default is 20260714.
Using the same questionnaire, seed, and options makes the simulation
reproducible. The loaded data are not used or changed.  {opt maxpanels(#)}, {opt ci}, and {opt level(#)}
work as described above.  The action does not accept selection, filter, weight, currency, profile-table,
or map options. Its presentation and diagnostic options are
{opt uilanguage()}, {opt direction()}, {opt title()}, {opt subtitle()}, {opt byline()},
{opt logo()}, {opt theme()}, {opt density()}, {opt background()},
{opt typography()}, {opt corners()}, {opt shadow()}, {opt motion()},
{opt pagewidth()}, {opt note()}, {opt source()}, {opt disclaimer()}, and
{opt diagnostics()}.{p_end}

{marker data}{...}
{title:Data rules and privacy}

{pstd}
The command works with one rectangular data file at a time.  Survey Solutions
rosters are exported separately; build one dashboard per roster level or merge
a carefully defined roster summary into the parent file.  An undefined join
can change denominators.{p_end}

{pstd}
The dataset in memory is preserved.  Selected columns are sent as a temporary
UTF-8 raw-code CSV so questionnaire category text and order remain authoritative.
For {opt customvars()}, the wrapper deliberately sends the Stata variable label,
type/format, and applicable observed value-label text.{p_end}

{pstd}
The HTML embeds selected analysis-level values needed for filtering and charting.
Text, picture, audio, linked text-list, and questionnaire-GPS completion items
are reduced to answered/not-answered flags instead of embedding original
contents.  A map embeds valid latitude and longitude so Leaflet can draw and
filter the display: six decimal places for {cmd:points}, four for
{cmd:cluster}, and three for {cmd:heat}.  Even reduced precision can identify
respondents or establishments.  Protect the HTML like the source data, review
disclosure risk, and do not publish row-level maps without authorization.
{cmd:cluster} and {cmd:heat} reduce precision and aggregate the display, but
they are not a substitute for a formal disclosure-control method.  The command
does not upload data.{p_end}

{marker readertools}{...}
{title:Reader tools inside the dashboard}

{pstd}
The following reader tools require no additional command options:{p_end}

{phang2}{bf:Theme switch.}  A control in the sticky top bar switches between
the built theme and the dark theme.  Charts, the profile table, and map
point colors re-skin immediately, and the choice is remembered across
reopenings when browser storage is available.{p_end}

{phang2}{bf:Copy view link.}  The control bar copies a link that reopens the
dashboard with the same filters, weighted/unweighted setting, currency
setting, and search query. Recipients also need access to that same HTML file
or a hosted copy; a local file link does not upload or share the dashboard.{p_end}

{phang2}{bf:Download data (CSV).}  Exports the currently filtered interviews.
Categorical codes are written with their questionnaire labels, multi-select
answers are joined with semicolons, and the weight column is appended for
weighted dashboards.  Only values already embedded in the file are exported,
so the same data-sharing and disclosure guidance applies to the downloaded CSV.{p_end}

{phang2}{bf:Customize any chart.}  The gear control on each card opens a
small editor: rewrite the chart title, switch between bars and a donut
where the two displays are statistically equivalent (single-select and
yes/no cards), split the chart by any filter variable ({bf:Compare by}:
categorical indicators become one composition bar per group and numeric
indicators the median per group, honoring weights and the currency
switch), choose an accent color, scale the chart fonts, and show or hide
value labels, and pick a chart size (Compact to Extra tall) {c -} the
chart area also grows automatically when a choice needs room, for example
a donut or a compare-by split on a slim yes/no card.  The accent recolors
the primary series of every chart family, including the affirmative side
of yes/no cards and their splits.  Changes are presentation-only {c -} no
estimate moves {c -} apply immediately, persist for that dashboard in the
reader's browser, and reset per chart.{p_end}

{phang2}{bf:Download chart (PNG).}  Each card downloads a presentation-ready
image at double resolution: the full wrapped question title, a line with the
variable name and the live filtered statistic, the chart as currently
customized, the yes/no headline percentage and legend where applicable, and
a source footer with the dashboard title and date.  Category labels are
written out in full: donut downloads carry a complete legend below the
chart, and bar downloads append any label the axis had to shorten.  The
image follows the active light or dark theme.{p_end}

{phang2}{bf:Navigation aids.}  {bf:Expand all} and {bf:Collapse all} in the
section navigation open or close every section; a floating button returns to
the top; a progress bar under the top bar shows reading position; pressing
{bf:/} focuses the indicator search and {bf:Esc} clears it.{p_end}

{marker examples}{...}
{title:Examples}

{pstd}
{bf:Recipes 1-18 use placeholder filenames and survey variables.} Replace them
with names in your questionnaire and data. Each build recipe assumes you have
loaded the matching dataset; they are alternatives, not a required sequence.
{opt replace} overwrites the named output, and {opt open} asks the browser to
open it. Copy continued lines together into a do-file, or remove {cmd:///}
and join them for the Command window.{p_end}

{pstd}{bf:1. Inspect a questionnaire}{p_end}

{p 8 8 2}{cmd:surveye describe using "English TRG_2025.html", detail}{p_end}

{pstd}{bf:2. Build a complete compact dashboard}{p_end}

{p 8 8 2}{cmd:use "TRG_2025.dta", clear}{p_end}

{p 8 8 2}{cmd:surveye using "English TRG_2025.html", saving("TRG_dashboard.html") ///}{p_end}
{p 12 12 2}{cmd:replace open}{p_end}

{pstd}{bf:3. Select indicators and observations}{p_end}

{p 8 8 2}{cmd:surveye legalstatus employment sales if consent==1 using ///}{p_end}
{p 12 12 2}{cmd:"questionnaire.html", saving("results.html") filters(region sector) ///}{p_end}
{p 12 12 2}{cmd:highlights(employment sales) title("Enterprise survey results") replace}{p_end}

{pstd}{bf:4. Add a logical expanded multiselect}{p_end}

{p 8 8 2}{cmd:surveye sales employment using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("services.html") questions("services_used") filters(region) ///}{p_end}
{p 12 12 2}{cmd:replace}{p_end}

{pstd}{bf:5. Add custom data variables with their Stata labels}{p_end}

{p 8 8 2}{cmd:label variable qc_score "Enumerator quality score"}{p_end}

{p 8 8 2}{cmd:surveye using "questionnaire.html", saving("quality.html") ///}{p_end}
{p 12 12 2}{cmd:customvars(qc_score risk_band) ///}{p_end}
{p 12 12 2}{cmd:addtosections("Interview quality: qc_score risk_band") ///}{p_end}
{p 12 12 2}{cmd:histograms(qc_score) bars(risk_band) replace open}{p_end}

{pstd}{bf:6. Put custom variables in existing and new sections}{p_end}

{p 8 8 2}{cmd:surveye sales using "questionnaire.html", saving("focused.html") ///}{p_end}
{p 12 12 2}{cmd:customvars(qc_score risk_band) ///}{p_end}
{p 12 12 2}{cmd:addtosections("3: qc_score|Quality checks: risk_band") replace}{p_end}

{pstd}{bf:7. Combine related suffix variables in one figure}{p_end}

{p 8 8 2}{cmd:surveye srib8a srib8b srib8c using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("digital.html") ///}{p_end}
{p 12 12 2}{cmd:vargroups("Digital channels:: srib8a srib8b srib8c") replace}{p_end}

{pstd}{bf:8. Compare affirmative shares across locations}{p_end}

{p 8 8 2}{cmd:surveye srib8a srib8b srib8c using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("digital_by_city.html") compare(srib8a srib8b srib8c) ///}{p_end}
{p 12 12 2}{cmd:compareby(city) comparelevels("01_Colombo|02_Kandy|03_Jaffna") ///}{p_end}
{p 12 12 2}{cmd:comparetitle("Digital access by city") replace}{p_end}

{pstd}{bf:9. Show integer distributions or force a continuous histogram}{p_end}

{p 8 8 2}{cmd:surveye employees visits revenue using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("numeric.html") discrete(employees visits) continuous(revenue) ///}{p_end}
{p 12 12 2}{cmd:replace}{p_end}

{pstd}{bf:10. Keep figures clean or request confidence intervals}{p_end}

{p 8 8 2}{cmd:surveye sector ownership using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("shares_clean.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector ownership using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("shares_90.html") ci level(90) replace}{p_end}

{pstd}{bf:11. Apply native Stata weights}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [aw=wmedian], ///}{p_end}
{p 12 12 2}{cmd:saving("analytic.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [fw=frequency], ///}{p_end}
{p 12 12 2}{cmd:saving("frequency.html") replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [iw=importance], ///}{p_end}
{p 12 12 2}{cmd:saving("importance.html") note("Descriptive importance weights.") ///}{p_end}
{p 12 12 2}{cmd:replace}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html" [pw=pop], ///}{p_end}
{p 12 12 2}{cmd:saving("weighted.html") filters(region) ///}{p_end}
{p 12 12 2}{cmd:note("Descriptive probability-weighted estimates.") replace}{p_end}

{pmore}
Each weighted dashboard starts with {bf:Weighted estimates} on.  Readers may
turn it off to recalculate the displayed results without weights.{p_end}

{pstd}{bf:12. Add a local-currency/USD switch}{p_end}

{p 8 8 2}{cmd:surveye sales costs profit using "questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("financials.html") usdvars(sales costs profit) usdrate(300) ///}{p_end}
{p 12 12 2}{cmd:currency(LKR) note("USD values use 300 LKR per USD.") replace open}{p_end}

{pstd}{bf:13. Add a live stratum profile table}{p_end}

{p 8 8 2}{cmd:surveye women_led sales banked digital_channel using ///}{p_end}
{p 12 12 2}{cmd:"questionnaire.html" [pw=pop], saving("profile.html") filters(stratum ///}{p_end}
{p 12 12 2}{cmd:owner_type) usdvars(sales) usdrate(300) currency(LKR) tableby(stratum) ///}{p_end}
{p 12 12 2}{cmd:tablevars(women_led sales banked digital_channel) ///}{p_end}
{p 12 12 2}{cmd:tablestats("share:1|median|share:1|share:1") ///}{p_end}
{p 12 12 2}{cmd:tablelabels("Women-led|Median sales|Banked|Digital channel") ///}{p_end}
{p 12 12 2}{cmd:tabletitle("Stratum profile") ///}{p_end}
{p 12 12 2}{cmd:tablesubtitle("Key indicators side by side") ///}{p_end}
{p 12 12 2}{cmd:tabletotal("Sri Lanka (all surveyed locations)") ///}{p_end}
{p 12 12 2}{cmd:tableweightlabel("Est. firms") replace open}{p_end}

{pstd}{bf:14. Build an Arabic right-to-left dashboard}{p_end}

{p 8 8 2}{cmd:surveye using "questionnaire_ar.html", saving("dashboard_ar.html") ///}{p_end}
{p 12 12 2}{cmd:uilanguage(ar) direction(auto) replace open}{p_end}

{pstd}{bf:15. Build an Urdu right-to-left dashboard}{p_end}

{p 8 8 2}{cmd:surveye using "questionnaire_ur.html", saving("dashboard_ur.html") ///}{p_end}
{p 12 12 2}{cmd:uilanguage(urdu) direction(rtl) replace open}{p_end}

{pstd}{bf:16. Show every GPS record over Google Hybrid}{p_end}

{p 8 8 2}{cmd:surveye sector sales using "questionnaire.html", saving("mapped.html") ///}{p_end}
{p 12 12 2}{cmd:latitude(gps_latitude) longitude(gps_longitude) country(KEN) ///}{p_end}
{p 12 12 2}{cmd:boundaries("World Bank Official Boundaries - Admin 2.zip") ///}{p_end}
{p 12 12 2}{cmd:maplevel(admin2) maptype(points) basemap(google_hybrid) mapby(sector) ///}{p_end}
{p 12 12 2}{cmd:maptitle("Interview locations") replace open}{p_end}

{pstd}{bf:17. Use OpenStreetMap or an aggregated point display}{p_end}

{p 8 8 2}{cmd:surveye sector using "questionnaire.html", saving("clustered.html") ///}{p_end}
{p 12 12 2}{cmd:latitude(gps_latitude) longitude(gps_longitude) country(KEN) ///}{p_end}
{p 12 12 2}{cmd:maptype(cluster) basemap(osm) replace}{p_end}

{pstd}{bf:18. Preview a translated questionnaire}{p_end}

{p 8 8 2}{cmd:surveye demo using "siNhl Global_informal2026.html", ///}{p_end}
{p 12 12 2}{cmd:saving("sinhala_preview.html") n(250) seed(42) theme(clean) replace open}{p_end}

{pstd}{bf:19. Run the bundled synthetic example from the source checkout}{p_end}

{pstd}
This recipe requires the source repository's {cmd:tests} directory; those
fixtures are not part of the installed command package. Set the working
directory to the SurvEye source root first. It preserves the current dataset,
imports only bundled synthetic records, and writes synthetic-dashboard.html
in that directory.{p_end}

{p 8 8 2}{cmd:preserve}{p_end}
{p 8 8 2}{cmd:import delimited "tests/review_demo_data.csv", ///}{p_end}
{p 12 12 2}{cmd:clear varnames(1) encoding("utf-8")}{p_end}
{p 8 8 2}{cmd:surveye sector sales employees ///}{p_end}
{p 12 12 2}{cmd:using "tests/review_demo_questionnaire.html", ///}{p_end}
{p 12 12 2}{cmd:saving("synthetic-dashboard.html") filters(region) ///}{p_end}
{p 12 12 2}{cmd:highlights(sales employees) discrete(employees) ///}{p_end}
{p 12 12 2}{cmd:theme(clean) replace}{p_end}
{p 8 8 2}{cmd:restore}{p_end}

{pstd}
If a command stops before {cmd:restore}, type {cmd:restore} to recover the
preserved dataset. This synthetic example is a demonstration, not real survey
evidence.{p_end}

{marker results}{...}
{title:Stored results}

{pstd}
Successful actions store results in {cmd:r()}. Type {cmd:return list}
immediately afterward. Later r-class commands can replace them. Fields not
supplied by an action are returned as missing numeric values or empty macros;
missing does not mean zero. {cmd:r(sample_N)} is added only by a build, and
{cmd:r(configurator)} only by configure.{p_end}

{pstd}{bf:Scalars: all actions}{p_end}

{phang}{cmd:r(success)}{break}
1 after a successful action.{p_end}

{phang}{cmd:r(k_sections)}{break}
Number of dashboard sections after a build or demo; parsed questionnaire
sections after describe or configure.{p_end}

{phang}{cmd:r(warnings)}{break}
Engine warning count for build, demo, and describe; missing for configure.{p_end}

{pstd}{bf:Scalars: build and demo}{p_end}

{phang}{cmd:r(N)}{break}
Number of observations in the dashboard, including simulated records for demo.{p_end}

{phang}{cmd:r(sample_N)}{break}
Build only: observations exported after {cmd:if}, {cmd:in}, and weight
restrictions.{p_end}

{phang}{cmd:r(k_charted)}{break}
Number of distinct charted indicators. In describe, this instead counts
potentially chartable questionnaire items without checking the dataset.{p_end}

{phang}{cmd:r(k_panels)}{break}
Number of chart panels. A family or comparison can put several indicators
inside one panel, so this need not equal {cmd:r(k_charted)}.{p_end}

{phang}{cmd:r(k_families)} and {cmd:r(k_comparisons)}{break}
Numbers of related-variable family panels and explicit binary comparison
panels, respectively.{p_end}

{phang}{cmd:r(k_skipped)} and {cmd:r(k_filters)}{break}
Numbers of skipped indicators and dashboard filters, respectively.{p_end}

{phang}{cmd:r(weighted)}{break}
1 when the dashboard was built with weights; 0 otherwise.{p_end}

{phang}{cmd:r(has_map)}{break}
1 if a map was created; 0 otherwise.{p_end}

{phang}{cmd:r(map_N)}, {cmd:r(map_missing)}, and {cmd:r(map_outside)}{break}
Counts of valid mapped rows, missing or out-of-range coordinate pairs, and
valid points outside the selected boundary, respectively. With no map these
counts are zero; describe and configure return missing.{p_end}

{pstd}{bf:Scalars: questionnaire inspection and configuration}{p_end}

{phang}{cmd:r(k_questions)}{break}
Number of parsed questions for describe and configure; missing after build
or demo.{p_end}

{pstd}{bf:Macros: text, names, and paths}{p_end}

{phang}{cmd:r(message)} and {cmd:r(title)}{break}
Engine message and dashboard or questionnaire title; all actions.{p_end}

{phang}{cmd:r(output)} and {cmd:r(filename)}{break}
Saved HTML path for build, demo, and configure.
{cmd:r(filename)} is an alias for {cmd:r(output)}; describe has no HTML output.{p_end}

{phang}{cmd:r(configurator)}{break}
Configure only: the {opt saving()} filename supplied to the configurator.{p_end}

{phang}{cmd:r(questionnaire)}{break}
Questionnaire path for build, demo, and describe; empty for configure.{p_end}

{phang}{cmd:r(chartvars)}{break}
Charted variable names for build and demo; chartable questionnaire names for
describe. Grouped-panel member names are included.{p_end}

{phang}{cmd:r(skippedvars)} and {cmd:r(filters)}{break}
Skipped indicator names and filter variable names after build or demo.{p_end}

{phang}{cmd:r(sections)}{break}
Section identifiers after build or demo. In describe, numbered section titles
are separated by {cmd:|}; empty for configure.{p_end}

{phang}{cmd:r(filter_candidates)} and {cmd:r(gps_candidates)}{break}
Describe only: possible questionnaire filter and GPS fields. These are
suggestions, not validation against the loaded data.{p_end}

{phang}{cmd:r(engine_version)} and {cmd:r(package_version)}{break}
Java engine and Stata wrapper identifiers, returned as text macros by all
actions. Use them when reporting a problem.{p_end}

{pstd}
Save a path as a local macro and a count as a scalar before another command
can replace {cmd:r()}. For example, immediately after a successful build:{p_end}

{p 8 8 2}{cmd:local dashboard_path "`r(output)'"}{p_end}
{p 8 8 2}{cmd:scalar dashboard_N = r(N)}{p_end}

{marker troubleshooting}{...}
{title:Troubleshooting}

{phang}{bf:Wrong or incomplete installed copy}{break}
Type {cmd:which surveye}, {cmd:findfile surveye.sthlp}, and {cmd:java query}.
The wrapper prints the required engine JAR name and installed path when
loading fails. Reinstall the complete package so the wrapper and that JAR
resolve together, then type {cmd:discard} or restart Stata. An older copy
earlier on the ado-path can mask a newer installation.{p_end}

{phang}{bf:Questionnaire not recognized or expected charts missing}{break}
Run {cmd:surveye describe using "questionnaire.html", detail} and compare the
reported names with your dataset. Use {opt questions()} for logical expanded
multiselect names and {opt customvars()} for constructed variables.
Use SurveyCTO XML if a printable layout is not recognized. To inspect parser
warnings, rerun without {opt strict}; add {opt diagnostics(filename)} for a
technical log.{p_end}

{phang}{bf:No observations or unusable filter choices}{break}
Check {cmd:if}, {cmd:in}, the selected weight, and missing-value exclusions.
Zero and missing weights remove rows. A requested categorical filter needs
at least one valid observed level and cannot exceed {opt maxcategories()}.
Use fewer or different filter variables when appropriate.{p_end}

{phang}{bf:File exists or file cannot be written}{break}
Choose another {opt saving()} or {opt diagnostics()} name, or use
{opt replace} when replacement is intended. The destination directory must
already exist and be writable. Quote paths containing spaces. Use distinct
input and output filenames.{p_end}

{phang}{bf:Browser does not open}{break}
The HTML remains saved if {opt open} cannot launch the browser. Open the
reported output file manually. The configurator uses {opt saving()} exactly
as written, so include its {cmd:.html} extension.{p_end}

{phang}{bf:Map points and boundaries appear but imagery is missing}{break}
Check the network connection, firewall, and selected tile provider.
Try {cmd:basemap(osm)} to identify a provider-specific problem.
The embedded charts, points, and boundaries remain usable offline.{p_end}

{phang}{bf:Sharing a view does not give the recipient the dashboard}{break}
Copy view link stores the reader's settings in a URL; it does not upload the
HTML. Give the recipient authorized access to the same file or hosted copy.
Review the {help surveye##data:embedded data} before distribution.{p_end}

{marker author}{...}
{title:Author}

{pstd}
{bf:Attique Ur Rehman}, Economist{break}
The World Bank {hline 1} Development Economics (DEC), Enterprise Surveys{break}
Email: {browse "mailto:attique@worldbank.org":attique@worldbank.org}{break}
Web: {browse "https://sites.google.com/view/attique-ur-rehman":https://sites.google.com/view/attique-ur-rehman}{p_end}

{marker acknowledgments}{...}
{title:Acknowledgments}

{pstd}
Thanks to {bf:Fahad Mirza} (World Bank / CERP,
{browse "https://github.com/fahad-mirza":github.com/fahad-mirza}) for his insights
and guidance, and for his self-contained Stata tooling ({cmd:sparkta},
{cmd:wordcloud2}) that helped shape this package's design.{p_end}

{marker alsosee}{...}
{title:Also see}

{pstd}
Related commands: {helpb return}, {helpb weight}, {helpb svyset},
{helpb import delimited}, {helpb export delimited}{p_end}

{pstd}
Author's SSC commands: {helpb suso}, {help surveye:survEye}{p_end}

{pstd}
The author-package links require the relevant help to be installed.
Use {cmd:ssc describe suso} or {cmd:ssc describe survEye} to inspect the
corresponding SSC entry; these packages are not additional dependencies
for generating a dashboard.{p_end}

{pstd}
Project: {browse "https://github.com/arehman10/SurvEye":SurvEye repository}{p_end}
