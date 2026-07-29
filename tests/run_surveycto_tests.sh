#!/usr/bin/env sh
# SurveyCTO questionnaire support: form-definition XML (precise) and
# printable HTML (best effort), exercised end to end with the generic jar.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAR="$ROOT/surveye.jar"
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/surveye-cto.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT HUP INT TERM

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS $1"; }

cfg() { printf '%s\t%s\n' "$1" "$2"; }

# ---------------------------------------------------------------- describe XML
{
  cfg mode describe
  cfg questionnaire "$ROOT/tests/surveycto_xform_questionnaire.xml"
  cfg status "$BUILD/describe_status.tsv"
} > "$BUILD/describe.tsv"
java -jar "$JAR" --config "$BUILD/describe.tsv" > "$BUILD/describe.out" 2>&1 ||
  fail "describe on the XForm definition errored"
grep -Fq "SurveyCTO form definition" "$BUILD/describe.out" ||
  fail "describe does not identify the SurveyCTO form definition"
grep -Fq "Informal Vendor Pilot" "$BUILD/describe.out" || fail "XForm title not read"
grep -Fq "Consent and profile" "$BUILD/describe.out" || fail "itext group label not resolved"
grep -Fq "Business operations" "$BUILD/describe.out" || fail "second section missing"
grep -Eq "Questions +11" "$BUILD/describe.out" || fail "expected 11 questions from the XForm"
grep -Fq "GPS        location" "$BUILD/describe.out" || fail "geopoint field not offered for maps"
pass "XForm describe (title, itext sections, counts, GPS candidate)"

# ------------------------------------------------------------------- build XML
{
  cfg mode build
  cfg questionnaire "$ROOT/tests/surveycto_xform_questionnaire.xml"
  cfg data "$ROOT/tests/surveycto_export.csv"
  cfg output "$BUILD/cto.html"
  cfg status "$BUILD/build_status.tsv"
  cfg title "SurveyCTO pilot"
  cfg filters owner_gender
  cfg replace 1
} > "$BUILD/build.tsv"
java -jar "$JAR" --config "$BUILD/build.tsv" > "$BUILD/build.out" 2>&1 ||
  fail "build from the XForm + split-column export errored"
grep -Fq '"services":["1","3"]' "$BUILD/cto.html" ||
  fail "select_multiple split columns (services_1..3) were not reassembled"
grep -Fq '"labels":{"1":"Mobile money","2":"Bank account","3":"Informal lender"}' "$BUILD/cto.html" ||
  fail "itemset choice labels did not reach the dashboard metadata"
grep -Fq '"labels":{"1":"Yes","0":"No"}' "$BUILD/cto.html" ||
  fail "inline select_one items lost their labels"
grep -Fq "Consent and profile" "$BUILD/cto.html" || fail "section titles missing from the dashboard"
grep -Fq '"sales_usd"' "$BUILD/cto.html" || fail "calculate field absent from the dashboard"
grep -Fq 'data-filter="owner_gender"' "$BUILD/cto.html" || fail "select_one filter chips missing"
grep -Fq "multi-select" "$BUILD/cto.html" || fail "reader-facing type captions missing"
pass "XForm build (multi reassembly, choice labels, calculate, filters, captions)"

# ------------------------------------------------------------------- demo XML
{
  cfg mode demo
  cfg questionnaire "$ROOT/tests/surveycto_xform_questionnaire.xml"
  cfg output "$BUILD/cto_demo.html"
  cfg status "$BUILD/demo_status.tsv"
  cfg n 60
  cfg seed 7
  cfg replace 1
} > "$BUILD/demo.tsv"
java -jar "$JAR" --config "$BUILD/demo.tsv" > "$BUILD/demo.out" 2>&1 ||
  fail "demo from the XForm definition errored"
test -s "$BUILD/cto_demo.html" || fail "demo produced no dashboard"
grep -Fq "Which services does the business use?" "$BUILD/cto_demo.html" ||
  fail "demo dashboard lost the itext question label"
pass "XForm demo (simulated preview without data)"

# ------------------------------------------------------- printable HTML (best effort)
{
  cfg mode describe
  cfg questionnaire "$ROOT/tests/surveycto_print_questionnaire.html"
  cfg status "$BUILD/print_status.tsv"
} > "$BUILD/print.tsv"
java -jar "$JAR" --config "$BUILD/print.tsv" > "$BUILD/print.out" 2>&1 ||
  fail "describe on the printable form errored"
grep -Fq "SurveyCTO printable form" "$BUILD/print.out" ||
  fail "printable form not identified (structural detection, no SurveyCTO keyword in fixture)"
grep -Fq "Vendor Screening Form" "$BUILD/print.out" || fail "printable title not read"
grep -Eq "Questions +6" "$BUILD/print.out" || fail "expected 6 fields from the printable form"
grep -Fq "Owner interview" "$BUILD/print.out" || fail "dark group header did not open a section"
grep -Fq "Wrap up" "$BUILD/print.out" || fail "second top-level section missing"
grep -Fq "Flags      1" "$BUILD/print.out" ||
  fail "printable parsing must carry its type warning"

{
  cfg mode demo
  cfg questionnaire "$ROOT/tests/surveycto_print_questionnaire.html"
  cfg output "$BUILD/print_demo.html"
  cfg status "$BUILD/print_demo_status.tsv"
  cfg n 40
  cfg seed 3
  cfg replace 1
} > "$BUILD/print_demo.tsv"
java -jar "$JAR" --config "$BUILD/print_demo.tsv" > "$BUILD/print_demo.out" 2>&1 ||
  fail "demo from the printable form errored"
grep -Fq "Members \u00b7 member \u00b7 choice list" "$BUILD/print_demo.html" ||
  grep -Fq "Members · member · choice list" "$BUILD/print_demo.html" ||
  fail "breadcrumb subgroups and the choice-list caption must reach panel subtitles"
grep -Fq ">Female<" "$BUILD/print_demo.html" || fail "printed choice labels lost"
pass "printable table layout (sections, breadcrumb repeats, captions, choices)"

# ---------------------------------------------------------- format detection errors
{
  cfg mode describe
  cfg questionnaire "$ROOT/LICENSE"
  cfg status "$BUILD/bad_status.tsv"
} > "$BUILD/bad.tsv"
if java -jar "$JAR" --config "$BUILD/bad.tsv" > "$BUILD/bad.out" 2>&1; then
  fail "an unrecognized file must not parse as a questionnaire"
fi
grep -Fq "SurveyCTO form definition" "$BUILD/bad.out" ||
  fail "the unrecognized-file error must name every supported input"
pass "unrecognized files fail with the combined format guidance"

printf '<html><body><p>SurveyCTO monitoring report</p></body></html>\n' > "$BUILD/report.html"
{
  cfg mode describe
  cfg questionnaire "$BUILD/report.html"
  cfg status "$BUILD/report_status.tsv"
} > "$BUILD/report.tsv"
if java -jar "$JAR" --config "$BUILD/report.tsv" > "$BUILD/report.out" 2>&1; then
  fail "an unrecognized SurveyCTO page must not parse"
fi
grep -Fq "printable layout was not recognized" "$BUILD/report.out" ||
  fail "unrecognized SurveyCTO pages must point at the form definition XML"
pass "unrecognized SurveyCTO pages point to the XML definition"

# Survey Solutions detection is unchanged.
{
  cfg mode describe
  cfg questionnaire "$ROOT/tests/fixed_multi_questionnaire.html"
  cfg status "$BUILD/suso_status.tsv"
} > "$BUILD/suso.tsv"
java -jar "$JAR" --config "$BUILD/suso.tsv" > "$BUILD/suso.out" 2>&1 ||
  fail "Survey Solutions describe regressed"
grep -Fq "Survey Solutions questionnaire" "$BUILD/suso.out" ||
  fail "Survey Solutions format label regressed"
pass "Survey Solutions detection unchanged"

echo "SurveyCTO support checks passed."
