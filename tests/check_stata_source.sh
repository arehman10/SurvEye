#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ADO="$ROOT/surveye.ado"
SMOKE="$ROOT/tests/stata_smoke.do"
README="$ROOT/README.md"
PKG="$ROOT/surveye.pkg"
TOC="$ROOT/stata.toc"

INSTALL_URL='https://raw.githubusercontent.com/arehman10/SurvEye/main/'
if ! grep -Fq "net install surveye, from(\"$INSTALL_URL\") replace" "$README"; then
  echo "FAIL: README must contain the copy-ready public GitHub install command" >&2
  exit 1
fi
if grep -Fq 'from(...)' "$README"; then
  echo "FAIL: README still contains a placeholder GitHub installation URL" >&2
  exit 1
fi
if ! grep -Fq '**Author:** Attique Ur Rehman' "$README" ||
   ! grep -Fq 'https://github.com/fahad-mirza' "$README"; then
  echo "FAIL: README author or Fahad Mirza acknowledgment is missing" >&2
  exit 1
fi
if ! grep -Fxq 'v 3' "$TOC" || ! grep -Eq '^p surveye[[:space:]]' "$TOC" ||
   ! grep -Fxq 'v 3' "$PKG" || ! grep -Fq 'd Repository: https://github.com/arehman10/SurvEye' "$PKG"; then
  echo "FAIL: root Stata package metadata is incomplete" >&2
  exit 1
fi
while IFS= read -r package_file; do
  if [ ! -f "$ROOT/$package_file" ]; then
    echo "FAIL: package file must exist at repository root: $package_file" >&2
    exit 1
  fi
done <<EOF
$(awk '$1 == "f" || $1 == "F" { print $2 }' "$PKG")
EOF

# marksample expects a caller-local name and creates the temporary variable.
# Preallocating touse and passing its expansion caused r(111) in real Stata.
if grep -Eq '^[[:space:]]*tempvar[[:space:]]+touse([[:space:]]|$)' "$ADO"; then
  echo "FAIL: do not preallocate touse before marksample" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*marksample[[:space:]]+touse,[[:space:]]*novarlist([[:space:]]|$)' "$ADO"; then
  echo "FAIL: expected literal 'marksample touse, novarlist'" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*file[[:space:]]+write.*[[:space:]]_tab[[:space:]].*[[:space:]]_n([[:space:]]|$)' "$ADO"; then
  echo "FAIL: config records must use Stata's _tab and _n file directives" >&2
  exit 1
fi

if grep -Eq '^[[:space:]]*file[[:space:]]+write.*[[:space:]]tab[[:space:]]' "$ADO"; then
  echo "FAIL: bare 'tab' is invalid in Stata file write; use _tab" >&2
  exit 1
fi

if ! grep -Eq 'local value = subinstr.*macval\(value\).*char\(9\)' "$ADO"; then
  echo "FAIL: config values must be protected with macval()" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*capture[[:space:]]+unab[[:space:]]+weightvar[[:space:]]*:' "$ADO"; then
  echo "FAIL: weight abbreviations must be resolved before export" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*local[[:space:]]+exportvars.*touse' "$ADO"; then
  echo "FAIL: the analysis-sample sentinel must be included in the CSV" >&2
  exit 1
fi

if grep -Fq 'confirm number `"' "$ADO"; then
  echo "FAIL: confirm number requires an unquoted numeric token" >&2
  exit 1
fi

if grep -Eq 'if .*value.*== "" local value "[.]"' "$ADO"; then
  echo "FAIL: absent status numbers must not be sent to confirm number as '.'" >&2
  exit 1
fi

if ! grep -Eq 'if inlist\(`"`macval\(value\).*"", "[.]"\)' "$ADO"; then
  echo "FAIL: status reader must distinguish absent/missing numeric fields" >&2
  exit 1
fi

if ! grep -Eq 'return scalar .*real\(.*macval\(value\)' "$ADO"; then
  echo "FAIL: numeric status values must remain macro-safe during conversion" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*capture[[:space:]]+noisily[[:space:]]+javacall[[:space:]]+org[.]worldbank[.]surveye[.]StataPlugin[[:space:]]+stata' "$ADO"; then
  echo "FAIL: javacall must preserve JVM/class-loader diagnostics" >&2
  exit 1
fi

if ! grep -Fq 'local jarname "surveye_2_1_3.jar"' "$ADO" ||
   ! grep -Fq 'jars(`jarname'"'"')' "$ADO"; then
  echo "FAIL: javacall must use the release-specific ado-path JAR" >&2
  exit 1
fi

if ! grep -Fq 'CUSTOMVars(varlist) ADDToSections(string)' "$ADO" ||
   ! grep -Fq 'BASEMap(string)' "$ADO" ||
   [ "$(grep -Fc 'CI LEVel(numlist min=1 max=1)' "$ADO" || true)" -ne 2 ]; then
  echo "FAIL: custom-variable, basemap, or confidence-interval syntax is missing" >&2
  exit 1
fi

if ! grep -Fq 'USDVars(varlist) USDRate(numlist min=1 max=1)' "$ADO" ||
   ! grep -Fq 'TABLEBy(varname) TABLEVars(varlist)' "$ADO" ||
   ! grep -Fq 'TABLESTats(string asis) TABLELAbels(string asis)' "$ADO"; then
  echo "FAIL: USD-toggle or summary-table syntax is missing" >&2
  exit 1
fi

# comparelevels() is a single pipe-delimited string.  Stata's `string asis'
# descriptor preserves the user's outer quotes, turning "Male|Female" into
# literal values `"Male' and `Female"' in the engine.  The ordinary string
# descriptor removes only those syntax quotes and preserves spaces safely.
if ! grep -Fq 'COMPARETitle(string) COMPARELevels(string)' "$ADO" ||
   grep -Fq 'COMPARELevels(string asis)' "$ADO"; then
  echo "FAIL: comparelevels() must strip Stata's outer syntax quotes" >&2
  exit 1
fi

if ! grep -Fq 'VARGroups(string) NOAUTOGroups' "$ADO" ||
   grep -Fq 'VARGroups(string asis)' "$ADO"; then
  echo "FAIL: vargroups() must strip Stata's outer syntax quotes" >&2
  exit 1
fi

# Stata requires a default value for a real/integer descriptor when the option
# itself is optional.  A bare LEVel(real) therefore makes the entire syntax
# declaration fail with r(197), even when the user did not specify level().
# A one-value numlist keeps omission distinguishable from an explicit level.
if grep -Eq '\((real|integer)\)' "$ADO"; then
  echo "FAIL: bare numeric syntax descriptors require review; optional ones need defaults" >&2
  exit 1
fi

if grep -Eqi '(^|[[:space:]])NOCI([[:space:]]|$)' "$ADO"; then
  echo "FAIL: ci must be opt-in; the retired noci option remains in the ado" >&2
  exit 1
fi
if [ "$(grep -Ec '^[[:space:]]*local showciflag = .*ci' "$ADO" || true)" -ne 2 ]; then
  echo "FAIL: build and demo must default confidence intervals off and enable them only with ci" >&2
  exit 1
fi
if [ "$(grep -Fc 'level() requires ci' "$ADO" || true)" -ne 2 ]; then
  echo "FAIL: build and demo must reject level() without ci" >&2
  exit 1
fi

for key in customvars addtosections basemap showci cilevel usdvars usdrate currency tableby tablevars tablestats tablelabels tabletitle tablesubtitle tabletotal tableweightlabel; do
  if ! grep -Eq "_surveye_cfgline .* ${key}[[:space:]]" "$ADO"; then
    echo "FAIL: the ado does not pass ${key} to the Java engine" >&2
    exit 1
  fi
done

if ! grep -Eq 'local exportvars .*usdvars.*tableby.*tablevars.*weightvar' "$ADO" ||
   ! grep -Eq 'local metadatavars .*usdvars.*tableby.*tablevars' "$ADO"; then
  echo "FAIL: USD and summary-table variables must be exported with Stata metadata" >&2
  exit 1
fi

if ! grep -Eq 'local metadatavars .*customvars.*filters.*highlights.*mapby' "$ADO" ||
   ! grep -Fq 'foreach metaselected of local selectedvars' "$ADO" ||
   ! grep -Fq 'capture confirm variable `metaselected'"'"'' "$ADO" ||
   ! grep -Eq '^[[:space:]]*_surveye_custommeta[[:space:]]' "$ADO"; then
  echo "FAIL: custom-variable Stata metadata is not written to the engine config" >&2
  exit 1
fi
if grep -Fq ': label (`valuelabel' "$ADO" ||
   ! grep -Fq 'local labeltext ""' "$ADO" ||
   ! grep -Fq 'capture local labeltext : label `valuelabel' "$ADO" ||
   ! grep -Fq 'if _rc | strtrim' "$ADO" ||
   ! grep -Fq 'macval(labeltext)' "$ADO"; then
  echo "FAIL: custom value-label lookup must name the label definition, not treat it as a variable" >&2
  exit 1
fi
if ! grep -Fq 'CALCULATED_VARIABLE_LABEL' "$ROOT/src/Util.java" ||
   [ "$(grep -R -h -F 'Util.displayLabel' "$ROOT/src/HtmlQuestionnaireParser.java" "$ROOT/src/DashboardBuilder.java" | wc -l | tr -d ' ')" -lt 5 ]; then
  echo "FAIL: calculated-variable placeholder labels are not normalized across dashboard paths" >&2
  exit 1
fi
if ! grep -Eq '_surveye_cfgline .* dataformat[.]' "$ADO"; then
  echo "FAIL: exact Stata date formats must be passed to the engine" >&2
  exit 1
fi

if awk 'NF && $0 == previous { duplicate=1; exit } { previous=$0 } END { exit duplicate ? 0 : 1 }' "$ADO"; then
  echo "FAIL: adjacent duplicate source lines found in the ado" >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*adopath[[:space:]]+\+\+' "$SMOKE" ||
   ! grep -Eq '^[[:space:]]*quietly[[:space:]]+do.*surveye[.]ado' "$SMOKE"; then
  echo "FAIL: licensed-Stata smoke test must force the source ado-path and ado" >&2
  exit 1
fi

# Weights must use Stata's standard command grammar.  A custom weight() option
# would be redundant, nonidiomatic, and incompatible with prefix parsing.
main_syntax=$(sed -n '/^[[:space:]]*syntax \[varlist/,/SAVing(string)/p' "$ADO" | tr '\n' ' ')
if ! printf '%s\n' "$main_syntax" | \
   grep -Eq 'using/[[:space:]/]*\[aweight fweight iweight pweight\]'; then
  echo "FAIL: the main syntax must accept native aweights, fweights, iweights, and pweights" >&2
  exit 1
fi
if grep -Eqi '(^|[[:space:]])WEIGHT[[:space:]]*\(' "$ADO"; then
  echo "FAIL: use native [aw=], [fw=], [iw=], or [pw=] syntax; do not add weight()" >&2
  exit 1
fi
for weight_doc in "$ROOT/README.md" "$ROOT/surveye.sthlp" "$ROOT/example.do" \
  "$ROOT/examples/README.md" "$SMOKE"; do
  if grep -Eq '\[(aw|fw|iw|pw)=[^]]+\][[:space:]]+using' "$weight_doc"; then
    echo "FAIL: stale weight-before-using example in $weight_doc" >&2
    exit 1
  fi
done
if ! grep -Eq 'surveye[[:space:]]+using.*\[aw=' "$SMOKE"; then
  echo "FAIL: licensed-Stata smoke test must exercise using filename [aw=weight], order" >&2
  exit 1
fi
if ! grep -Eq 'replace .*touse.*missing\(`weightvar'"'"'\).*weightvar'"'"' == 0' "$ADO"; then
  echo "FAIL: zero and missing weights must be excluded from the complete analysis sample" >&2
  exit 1
fi
if ! grep -Fq 'confidence intervals are disabled for iweights' "$ADO"; then
  echo "FAIL: confidence intervals must be disabled for iweights" >&2
  exit 1
fi

# CI whiskers belong only on ordinary categorical bars. Split binary and
# completion cards and donut charts must remain CI-free even when ci is set.
split_source=$(sed -n '/function buildSplit(/,/function buildYesNo(/p' "$ROOT/src/resources/dashboard.js")
yesno_source=$(sed -n '/function buildYesNo(/,/function buildDonut(/p' "$ROOT/src/resources/dashboard.js")
donut_source=$(sed -n '/function buildDonut(/,/function buildBar(/p' "$ROOT/src/resources/dashboard.js")
completion_source=$(sed -n '/function buildCompletion(/,/function build(canvas)/p' "$ROOT/src/resources/dashboard.js")
bar_source=$(sed -n '/function buildBar(/,/function numericValues(/p' "$ROOT/src/resources/dashboard.js")
if printf '%s\n' "$split_source$yesno_source$donut_source$completion_source" | grep -Fq 'confidenceInterval('; then
  echo "FAIL: binary, completion, and donut rendering paths must not compute confidence intervals" >&2
  exit 1
fi
if printf '%s\n' "$split_source" | grep -Fq 'plugins:[barConfidenceIntervals]'; then
  echo "FAIL: stacked split bars must not register a confidence-interval whisker plugin" >&2
  exit 1
fi
if ! printf '%s\n' "$bar_source" | grep -Fq 'plugins:[barConfidenceIntervals,barValueLabels]'; then
  echo "FAIL: ordinary categorical bars must retain opt-in confidence-interval whiskers" >&2
  exit 1
fi

# Motion is useful only when it is painted in front of the reader. Charts must
# wait for real viewport exposure (and a visible document), while reduced-motion
# users continue to receive a zero-duration transition.
dashboard_js="$ROOT/src/resources/dashboard.js"
if ! grep -Fq 'Chart.defaults.animation.duration=reducedMotion?0:800' "$dashboard_js" ||
   ! grep -Fq 'Chart.defaults.animation.easing="easeOutCubic"' "$dashboard_js"; then
  echo "FAIL: dashboard charts must use the reduced-motion-safe 800 ms easeOutCubic transition" >&2
  exit 1
fi

# The sticky controls must start compact and expose one accessible disclosure
# target. Reset and the matched count stay outside the hidden body.
renderer="$ROOT/src/DashboardRenderer.java"
i18n="$ROOT/src/DashboardI18n.java"
for token in 'id=\"dashboard-controls\"' 'id=\"controls-toggle\" aria-expanded=\"false\" aria-controls=\"controls-body\"' 'id=\"controls-body\" hidden'; do
  if ! grep -Fq "$token" "$renderer"; then
    echo "FAIL: collapsed dashboard-controls renderer contract is missing: $token" >&2
    exit 1
  fi
done
if ! grep -Fq 'function wireControls()' "$dashboard_js" ||
   ! grep -Fq 'function setControlsExpanded(expanded)' "$dashboard_js" ||
   ! grep -Fq 'input.addEventListener("input",applyIndicatorSearch)' "$dashboard_js"; then
  echo "FAIL: expandable controls or search/reset wiring is missing" >&2
  exit 1
fi
for language in 'Show filters' 'إظهار عوامل التصفية' 'فلٹرز دکھائیں'; do
  if ! grep -Fq "$language" "$i18n"; then
    echo "FAIL: filter-disclosure localization is missing: $language" >&2
    exit 1
  fi
done
if grep -Fq 'rootMargin:"180px"' "$dashboard_js" ||
   ! grep -Fq 'rootMargin:"0px",threshold:[0,.12]' "$dashboard_js"; then
  echo "FAIL: charts must be constructed at genuine viewport exposure, not 180px offscreen" >&2
  exit 1
fi
if grep -Fq 'section.querySelectorAll("canvas.chart").forEach' "$dashboard_js"; then
  echo "FAIL: opening a section must not eagerly construct every offscreen chart" >&2
  exit 1
fi
if ! grep -Fq 'document.hidden===true' "$dashboard_js" ||
   ! grep -Fq 'document.addEventListener("visibilitychange",scheduleChartVisibilityRefresh)' "$dashboard_js"; then
  echo "FAIL: chart construction must pause while the browser document is hidden" >&2
  exit 1
fi

# Main and demo mode both expose the same language/direction interface and pass
# the normalized values to the Java renderer.
rtl_syntax=$(grep -Fc 'UILANGuage(string) DIRection(string)' "$ADO" || true)
if [ "$rtl_syntax" -lt 2 ]; then
  echo "FAIL: uilanguage() and direction() must be available in build and demo modes" >&2
  exit 1
fi
for key in uilanguage direction; do
  count=$(grep -Ec "_surveye_cfgline .* ${key}[[:space:]]" "$ADO" || true)
  if [ "$count" -lt 2 ]; then
    echo "FAIL: ${key} must be sent to the renderer by build and demo modes" >&2
    exit 1
  fi
done
if ! grep -Fq '"auto", "english", "arabic", "urdu"' "$ADO" ||
   ! grep -Fq '"auto", "ltr", "rtl"' "$ADO"; then
  echo "FAIL: RTL language/direction validation is incomplete" >&2
  exit 1
fi

if grep -Eq '^program define [[:alnum:]_]+_dashboard([,[:space:]]|$)' "$ADO"; then
  echo "FAIL: the retired command name remains in the canonical ado" >&2
  exit 1
fi

if ! grep -Fq 'සිංහල' "$SMOKE"; then
  echo "FAIL: licensed-Stata smoke test must contain a literal Sinhala value" >&2
  exit 1
fi

programs=$(awk '/^program define / { n++ } END { print n+0 }' "$ADO")
ends=$(awk '/^end$/ { n++ } END { print n+0 }' "$ADO")
if [ "$programs" -ne "$ends" ]; then
  echo "FAIL: $programs Stata programs but $ends end statements" >&2
  exit 1
fi

echo "PASS Stata source contracts"
