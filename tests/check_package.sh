#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$ROOT/scripts/version.sh"
PKG="$ROOT/surveye.pkg"
GENERIC_JAR="$ROOT/surveye.jar"
RELEASE_JAR="$ROOT/$SURVEYE_RELEASE_JAR"

for required in "$PKG" "$ROOT/stata.toc" "$GENERIC_JAR" "$RELEASE_JAR"; do
  if [ ! -f "$required" ]; then
    echo "FAIL: package file is missing: $required" >&2
    exit 1
  fi
done

if ! grep -Fq "d Version: $SURVEYE_VERSION" "$PKG" ||
   ! grep -Fq "F $SURVEYE_RELEASE_JAR" "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]ado$' "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]sthlp$' "$PKG" ||
   ! grep -Eq '^[fF] surveye[.]jar$' "$PKG"; then
  echo "FAIL: package manifest does not identify the v$SURVEYE_VERSION release JAR" >&2
  exit 1
fi

# Static Stata/help metadata cannot read a repository VERSION after SSC
# installation. Fail on drift instead of silently packaging mixed releases.
for file in "$ROOT/surveye.ado" "$ROOT/surveye.sthlp"; do
  if ! grep -Fq "version $SURVEYE_VERSION " "$file"; then
    echo "FAIL: stale version header in $(basename "$file")" >&2
    exit 1
  fi
done
if ! grep -Fq "local jarname \"$SURVEYE_RELEASE_JAR\"" "$ROOT/surveye.ado" ||
   ! grep -Fq "return local package_version \"$SURVEYE_VERSION\"" "$ROOT/surveye.ado" ||
   ! grep -Fxq "Implementation-Version: $SURVEYE_VERSION" "$ROOT/MANIFEST.MF"; then
  echo "FAIL: Stata bridge or source manifest version disagrees with VERSION" >&2
  exit 1
fi
if ! awk -v version="$SURVEYE_VERSION" -v jar="$SURVEYE_RELEASE_JAR" '
  $1 == "local" && $2 == "jarname" { gsub(/"/, "", $3); if ($3 != jar) exit 1 }
  $1 == "return" && $2 == "local" && $3 == "package_version" {
    gsub(/"/, "", $4); if ($4 != version) exit 1
  }
' "$ROOT/surveye.ado"; then
  echo "FAIL: mixed release references in surveye.ado" >&2
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

# GitHub's web "Upload files" adds and overwrites but never deletes, so a
# repository refreshed that way can keep a retired release jar (for example
# surveye_2_1_3.jar) alongside the current one. A stale jar then breaks the
# generic/release identity contract with a cryptic byte-level cmp message.
# Name the problem instead.
for jar in "$ROOT"/surveye_*.jar; do
  [ -e "$jar" ] || continue
  if [ "$jar" != "$RELEASE_JAR" ]; then
    echo "FAIL: stale release jar $(basename "$jar") found; only $(basename "$RELEASE_JAR") belongs in this version." >&2
    echo "      GitHub web uploads never delete files - remove it from the repository (web UI: open the file, Delete file, commit)." >&2
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
   ! printf '%s\n' "$manifest" | grep -Fq "Implementation-Version: $SURVEYE_VERSION"; then
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
  resources/configurator.js \
  resources/configurator.css \
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
  configurator.js \
  configurator.css \
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
  if [ -f "$ROOT/src/resources/$source" ]; then
    if ! cmp -s "$ROOT/src/resources/$source" "$RESOURCE_BUILD/$source"; then
      echo "FAIL: JAR resource is stale: resources/$source" >&2
      exit 1
    fi
  else
    # Source-only review bundles keep these licensed assets embedded in the JAR.
    # Verify exact original bytes against a maintained checksum, not mere presence.
    case "$source" in
      noto-sans-arabic-arabic-400-normal.woff2|noto-sans-arabic-arabic-700-normal.woff2)
        expected=$(awk -v name="$source" '$2 == name { print $1 }' "$ROOT/src/resources/embedded-assets.sha256")
        actual=$(sha256sum "$RESOURCE_BUILD/$source" | awk '{ print $1 }')
        if [ -z "$expected" ] || [ "$actual" != "$expected" ]; then
          echo "FAIL: embedded asset checksum differs: $source" >&2
          exit 1
        fi
        ;;
      *) echo "FAIL: missing maintained source asset: $source" >&2; exit 1 ;;
    esac
  fi
done

class_count=$(unzip -Z1 "$GENERIC_JAR" | awk '/[.]class$/ { count++ } END { print count+0 }')
if [ "$class_count" -lt 10 ]; then
  echo "FAIL: implausible engine class count: $class_count" >&2
  exit 1
fi

sha256=$(sha256sum "$GENERIC_JAR" | awk '{ print $1 }')
echo "PASS package manifest and JAR integrity  classes=$class_count sha256=$sha256"
