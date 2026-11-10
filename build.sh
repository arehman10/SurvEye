#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
. "$ROOT/scripts/version.sh"
BUILD="$ROOT/build/release"
CLASSES="$BUILD/classes"
GENERIC_JAR="$ROOT/surveye.jar"
STATA_JAR="$ROOT/$SURVEYE_RELEASE_JAR"

if ! grep -Fq "String VERSION = \"$SURVEYE_VERSION\"" "$ROOT/src/SurvEye.java"; then
  echo "FAIL: src/SurvEye.java VERSION does not match VERSION ($SURVEYE_VERSION)." >&2
  exit 1
fi

mkdir -p "$CLASSES"
# Remove both files and directories from earlier builds.  Keeping only files
# was enough to leave retired package-directory entries inside a later JAR.
find "$CLASSES" -mindepth 1 -delete
mkdir -p "$CLASSES/resources"

java -m jdk.compiler/com.sun.tools.javac.Main \
  --release 8 -encoding UTF-8 \
  -d "$CLASSES" \
  "$ROOT"/src/*.java

cp "$ROOT"/src/resources/dashboard.css "$CLASSES/resources/"
cp "$ROOT"/src/resources/dashboard.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/configurator.css "$CLASSES/resources/"
cp "$ROOT"/src/resources/configurator.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/chart.umd.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/CHARTJS-LICENSE.md "$CLASSES/resources/"
cp "$ROOT"/src/resources/leaflet.css "$CLASSES/resources/"
cp "$ROOT"/src/resources/leaflet.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/LEAFLET-LICENSE.txt "$CLASSES/resources/"
# Fonts stay embedded in the runtime artifact. Source-only review packages may
# omit loose font files; preserve the licensed embedded copies while rebuilding.
for font in noto-sans-arabic-arabic-400-normal.woff2 noto-sans-arabic-arabic-700-normal.woff2; do
  if [[ -f "$ROOT/src/resources/$font" ]]; then
    cp "$ROOT/src/resources/$font" "$CLASSES/resources/"
  elif [[ -f "$GENERIC_JAR" ]]; then
    (cd "$CLASSES" && java -m jdk.jartool/sun.tools.jar.Main xf "$GENERIC_JAR" "resources/$font")
    test -s "$CLASSES/resources/$font" || { echo "Missing embedded runtime asset: $font" >&2; exit 1; }
  else
    echo "Build requires the supplied surveye.jar or original resource assets." >&2
    exit 1
  fi
done
cp "$ROOT"/src/resources/NOTO-SANS-ARABIC-LICENSE.txt "$CLASSES/resources/"
cp "$ROOT"/src/resources/world50.tsv "$CLASSES/resources/"
cp "$ROOT"/src/resources/country_aliases.tsv "$CLASSES/resources/"

# Use VERSION for executable metadata; do not trust a stale checked-in
# manifest when assembling a release.
sed "s/^Implementation-Version:.*/Implementation-Version: $SURVEYE_VERSION/" \
  "$ROOT/MANIFEST.MF" > "$BUILD/MANIFEST.MF"
# Stamp every entry with one fixed date so that rebuilding the same source on
# the same machine gives the same file, byte for byte. (--date needs JDK 19+;
# older JDKs fall back to today's dates, which changes the file hash but not
# the content fingerprint printed below.)
JAR_DATE="2026-01-01T00:00:00Z"
if ! java -m jdk.jartool/sun.tools.jar.Main \
     --create --file "$GENERIC_JAR" --manifest "$BUILD/MANIFEST.MF" \
     --date="$JAR_DATE" \
     -C "$CLASSES" . 2>/dev/null; then
  java -m jdk.jartool/sun.tools.jar.Main \
    cfm "$GENERIC_JAR" "$BUILD/MANIFEST.MF" \
    -C "$CLASSES" .
fi

# Keep the conventional CLI filename and a release-specific Stata-plugin
# filename byte-for-byte identical.  The latter prevents an obsolete generic
# JAR earlier on the ado-path from shadowing the installed plugin.
cp -p "$GENERIC_JAR" "$STATA_JAR"

echo "Built $GENERIC_JAR"
echo "Built $STATA_JAR"

# Two identities for the release record. The file sha256 changes with every
# packing (dates, compression); the content fingerprint depends only on the
# classes and resources inside, so it is the one to compare across machines.
echo "sha256      $(sha256sum "$GENERIC_JAR" | awk '{ print $1 }')"
echo "content     $(java "$ROOT/scripts/JarFingerprint.java" "$GENERIC_JAR" | awk '{ print $1 }')"
