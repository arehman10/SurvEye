*! surveye licensed-Stata smoke test 2.1.2 20jul2026
version 16.0
clear all
set more off

args root
if `"`root'"' == "" local root `"`c(pwd)'"'

// Test this checkout, not a previously installed release on the ado-path.
// Changing to the source directory also makes the current-directory entry on
// Stata's search path resolve the source JAR even if another copy is installed.
local caller_pwd `"`c(pwd)'"'
quietly cd `"`macval(root)'"'
local root `"`c(pwd)'"'
confirm file `"`root'/surveye.ado"'
confirm file `"`root'/surveye_2_1_2.jar"'
adopath ++ `"`root'"'
discard
quietly do `"`root'/surveye.ado"'
findfile surveye_2_1_2.jar
display as text "Testing engine: " as result `"`r(fn)'"'

local questionnaire `"`root'/tests/fixed_multi_questionnaire.html"'
local datafile      `"`root'/tests/fixed_multi.csv"'
confirm file `"`questionnaire'"'
confirm file `"`datafile'"'

display as text "[1/18] sparse and malformed Java status records"
tempfile sparse_status malformed_status
tempname status_fh
file open `status_fh' using `"`sparse_status'"', write text replace
file write `status_fh' "success" _tab "1" _n
file write `status_fh' "message" _tab "Sparse status accepted." _n
file write `status_fh' "k_sections" _tab "1" _n
file write `status_fh' "k_questions" _tab "." _n
file close `status_fh'
_surveye_read_status using `"`sparse_status'"'
assert r(success) == 1
assert r(k_sections) == 1
assert missing(r(N))
assert missing(r(k_questions))
assert missing(r(map_N))

file open `status_fh' using `"`malformed_status'"', write text replace
file write `status_fh' "success" _tab "1" _n
file write `status_fh' "k_questions" _tab "not-a-number" _n
file close `status_fh'
capture noisily _surveye_read_status using `"`malformed_status'"'
assert _rc == 498

display as text "[2/18] describe mode"
surveye describe using `"`questionnaire'"', detail
assert r(success) == 1
assert r(k_sections) == 1
if `"`r(package_version)'"' != "2.1.2" {
    display as error "Wrong surveye version loaded: `r(package_version)'"
    exit 9
}

display as text "[3/18] demo mode"
tempfile demo_base
local demo_html `"`demo_base'.html"'
surveye demo using `"`questionnaire'"', ///
    saving(`"`demo_html'"') n(12) seed(42) replace
assert r(success) == 1
assert r(N) == 12
confirm file `"`demo_html'"'

display as text "[4/18] main mode, if restriction, and data preservation"
import delimited using `"`datafile'"', clear varnames(1)
local original_N = _N
tempfile main_base
local main_html `"`main_base'.html"'
surveye if case_id <= 3 using `"`questionnaire'"', ///
    saving(`"`main_html'"') questions("services_used") replace
assert r(success) == 1
assert r(sample_N) == 3
assert r(N) == 3
assert _N == `original_N'
confirm file `"`main_html'"'

display as text "[5/18] all-missing selected row is retained"
tempfile missing_base
local missing_html `"`missing_base'.html"'
surveye using `"`questionnaire'"', ///
    saving(`"`missing_html'"') questions("services_used") replace
assert r(sample_N) == 4
assert r(N) == 4

generate double wmedian = 1
replace wmedian = 2 in 2
replace wmedian = 0 in 3
replace wmedian = . in 4
generate double pop = 1
replace pop = . in 4
generate double freq = wmedian
generate double importance = wmedian
replace importance = 3 in 2
generate double allzero = 0

display as text "[6/18] native analytic weights and zero/missing exclusion"
tempfile aw_base
local aw_html `"`aw_base'.html"'
surveye using `"`questionnaire'"' [aw=wmedian], ///
    saving(`"`aw_html'"') questions("services_used") replace
assert r(sample_N) == 2
assert r(N) == 2
assert r(weighted) == 1

display as text "[7/18] native probability weights"
tempfile pw_base
local pw_html `"`pw_base'.html"'
surveye using `"`questionnaire'"' [pw=pop], ///
    saving(`"`pw_html'"') questions("services_used") replace
assert r(sample_N) == 3
assert r(N) == 3
assert r(weighted) == 1

display as text "[8/18] native frequency weights"
tempfile fw_base
local fw_html `"`fw_base'.html"'
surveye using `"`questionnaire'"' [fw=freq], ///
    saving(`"`fw_html'"') questions("services_used") replace
assert r(sample_N) == 2
assert r(N) == 2
assert r(weighted) == 1

display as text "[9/18] native importance weights"
tempfile iw_base
local iw_html `"`iw_base'.html"'
surveye using `"`questionnaire'"' [iw=importance], ///
    saving(`"`iw_html'"') questions("services_used") replace
assert r(sample_N) == 2
assert r(N) == 2
assert r(weighted) == 1

display as text "[10/18] negative weights are rejected"
replace wmedian = -1 in 2
tempfile negative_base
local negative_html `"`negative_base'.html"'
capture noisily surveye using `"`questionnaire'"' [aw=wmedian], ///
    saving(`"`negative_html'"') questions("services_used") replace
assert _rc == 402
replace wmedian = 2 in 2

display as text "[11/18] fractional frequency weights are rejected"
replace freq = 1.5 in 2
tempfile fractional_base
local fractional_html `"`fractional_base'.html"'
capture noisily surveye using `"`questionnaire'"' [fw=freq], ///
    saving(`"`fractional_html'"') questions("services_used") replace
assert _rc == 401
replace freq = 2 in 2

display as text "[12/18] all-zero weights leave no analysis sample"
tempfile zero_base
local zero_html `"`zero_base'.html"'
capture noisily surveye using `"`questionnaire'"' [pw=allzero], ///
    saving(`"`zero_html'"') questions("services_used") replace
assert _rc == 2000

display as text "[13/18] showempty with no matching response columns"
keep case_id
tempfile empty_base
local empty_html `"`empty_base'.html"'
surveye using `"`questionnaire'"', ///
    saving(`"`empty_html'"') questions("services_used") showempty replace
assert r(sample_N) == 4
assert r(N) == 4

display as text "[14/18] explicit build, Unicode title, and RTL options"
import delimited using `"`datafile'"', clear varnames(1)
generate double demo = case_id
local special = "Cost " + char(36) + " and " + char(96) + ///
    "literal" + char(39) + " - සිංහල"
assert ustrpos(`"`macval(special)'"', "සිංහල") > 0
tempfile build_base
local build_html `"`build_base'.html"'
surveye build demo using `"`questionnaire'"', ///
    saving(`"`build_html'"') title(`"`macval(special)'"') ///
    uilanguage(ar) direction(auto) replace
assert r(N) == 4
mata: assert(st_global("r(title)") == st_local("special"))

display as text "[15/18] custom variable label, section placement, and chart override"
label variable demo "Field quality score"
tempfile custom_base
local custom_html `"`custom_base'.html"'
surveye using `"`questionnaire'"', ///
    saving(`"`custom_html'"') questions("services_used") ///
    customvars(demo) addtosections("Interview quality: demo") ///
    histograms(demo) ci level(90) replace
assert r(N) == 4
assert r(k_charted) == 2
confirm file `"`custom_html'"'

display as text "[16/18] custom numeric value-label definition is preserved"
generate byte quality_flag = mod(case_id, 3) + 1
label define _surveye_yesno 1 "Approved" 2 "Rejected" 3 "Needs review"
label values quality_flag _surveye_yesno
label variable quality_flag "Quality review outcome"
tempfile labeled_base
local labeled_html `"`labeled_base'.html"'
surveye using `"`questionnaire'"', ///
    saving(`"`labeled_html'"') questions("services_used") ///
    customvars(quality_flag) bars(quality_flag) replace
assert r(N) == 4
assert r(k_charted) == 2
confirm file `"`labeled_html'"'

display as text "[17/18] existing output is protected without replace"
capture noisily surveye build demo using `"`questionnaire'"', ///
    saving(`"`build_html'"')
assert _rc == 602

display as text "[18/18] reported Sri Lanka GPS invocation parses"
generate double gps__Latitude = 6.9271
generate double gps__Longitude = 79.8612
generate byte lf_responsive = 1
tempfile parser_stem parser_output
local parser_questionnaire `"`parser_stem' English Global_informal2026.html"'
local parser_boundaries `"`parser_stem' World Bank Official Boundaries - Admin 2.zip"'
local parser_html `"`parser_output'.html"'
copy `"`questionnaire'"' `"`parser_questionnaire'"', replace
capture confirm file `"`parser_boundaries'"'
assert _rc != 0
capture noisily surveye using `"`parser_questionnaire'"', ///
    saving(`"`parser_html'"') replace open maxpanels(400) sections(3) ///
    boundaries(`"`parser_boundaries'"') latitude( gps__Latitude ) ///
    longitude( gps__Longitude ) country(Sri Lanka) filters(lf_responsive)
local parser_rc = _rc
erase `"`parser_questionnaire'"'
assert `parser_rc' == 601

quietly cd `"`macval(caller_pwd)'"'
display as result "PASS licensed-Stata smoke test"
