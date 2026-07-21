#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PKG="$ROOT/surveye.pkg"
GENERIC_JAR="$ROOT/surveye.jar"
RELEASE_JAR="$ROOT/surveye_2_1_3.jar"

for required in "$PKG" "$ROOT/stata.toc" "$GENERIC_JAR" "$RELEASE_JAR"; do
  if [ ! -f "$required" ]; then
    echo "FAIL: package file is missing: $required" >&2
    exit 1
  fi
done

if ! grep -Fq 'd Version: 2.1.3' "$PKG" ||
   ! grep -Fq 'F surveye_2_1_3.jar' "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]ado$' "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]sthlp$' "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]jar$' "$PKG"; then
  echo "FAIL: package manifest does not identify the v2.1.3 release JAR" >&2
  exit 1
fi

awk '$1 == "f" || $1 == "F" { print $2 }' "$PKG" | while IFS= read -r relative; do
  case "$relative" in
    ""|/*|*..*)
      echo "FAIL: unsafe package path: $relative" >&2
      exit 1
      ;;
  esac
  if [ ! -f "$ROOT/$relative" ]; then
    echo "FAIL: package manifest references a missing file: $relative" >&2
    exit 1
  fi
done

if ! cmp -s "$GENERIC_JAR" "$RELEASE_JAR"; then
  echo "FAIL: generic and release-specific JARs differ" >&2
  exit 1
fi

unzip -tqq "$GENERIC_JAR"
manifest=$(unzip -p "$GENERIC_JAR" META-INF/MANIFEST.MF)
if ! printf '%s\n' "$manifest" | grep -Fq 'Main-Class: SurvEye'; then
  echo "FAIL: executable JAR manifest has no SurvEye main class" >&2
  exit 1
fi
if ! printf '%s\n' "$manifest" | grep -Fq 'Implementation-Title: SurvEye' ||
   ! printf '%s\n' "$manifest" | grep -Fq 'Implementation-Version: 2.1.3'; then
  echo "FAIL: executable JAR manifest has stale product/version metadata" >&2
  exit 1
fi
if ! unzip -Z1 "$GENERIC_JAR" | grep -Fxq 'org/worldbank/surveye/StataPlugin.class'; then
  echo "FAIL: canonical Stata javacall bridge is missing from the JAR" >&2
  exit 1
fi
if unzip -Z1 "$GENERIC_JAR" | grep -Eiq '(^|/)surveydash([^/]*|/)|(^|/)SurveyDash[.]class$|org/worldbank/surveydash/'; then
  echo "FAIL: interim surveydash name or Java namespace remains in the JAR" >&2
  exit 1
fi
if unzip -Z1 "$GENERIC_JAR" | grep -Eq '(^|/)suso(_dashboard)?(/|$)|SusoDashboard'; then
  echo "FAIL: retired command or Java namespace remains in the JAR" >&2
  exit 1
fi

for resource in \
  resources/dashboard.js \
  resources/dashboard.css \
  resources/chart.umd.js \
  resources/CHARTJS-LICENSE.md \
  resources/leaflet.js \
  resources/leaflet.css \
  resources/LEAFLET-LICENSE.txt \
  resources/country_aliases.tsv \
  resources/world50.tsv \
  resources/noto-sans-arabic-arabic-400-normal.woff2 \
  resources/noto-sans-arabic-arabic-700-normal.woff2 \
  resources/NOTO-SANS-ARABIC-LICENSE.txt; do
  if ! unzip -Z1 "$GENERIC_JAR" | grep -Fxq "$resource"; then
    echo "FAIL: JAR resource is missing: $resource" >&2
    exit 1
  fi
done

# Presence alone is not enough: a late source edit must never leave the
# executable JAR carrying an older dashboard asset.  Compare every maintained
# web/font resource byte-for-byte with the source tree after each build.
RESOURCE_BUILD=$(mktemp -d "${TMPDIR:-/tmp}/surveye-resources.XXXXXX")
trap 'rm -rf "$RESOURCE_BUILD"' EXIT HUP INT TERM
for source in \
  dashboard.js \
  dashboard.css \
  chart.umd.js \
  CHARTJS-LICENSE.md \
  leaflet.js \
  leaflet.css \
  LEAFLET-LICENSE.txt \
  country_aliases.tsv \
  world50.tsv \
  noto-sans-arabic-arabic-400-normal.woff2 \
  noto-sans-arabic-arabic-700-normal.woff2 \
  NOTO-SANS-ARABIC-LICENSE.txt; do
  unzip -p "$GENERIC_JAR" "resources/$source" > "$RESOURCE_BUILD/$source"
  if ! cmp -s "$ROOT/src/resources/$source" "$RESOURCE_BUILD/$source"; then
    echo "FAIL: JAR resource is stale: resources/$source" >&2
    exit 1
  fi
done

class_count=$(unzip -Z1 "$GENERIC_JAR" | awk '/[.]class$/ { count++ } END { print count+0 }')
if [ "$class_count" -lt 10 ]; then
  echo "FAIL: implausible engine class count: $class_count" >&2
  exit 1
fi

sha256=$(sha256sum "$GENERIC_JAR" | awk '{ print $1 }')
echo "PASS package manifest and JAR integrity  classes=$class_count sha256=$sha256"
