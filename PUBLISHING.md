#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build/release"
CLASSES="$BUILD/classes"
GENERIC_JAR="$ROOT/surveye.jar"
STATA_JAR="$ROOT/surveye_2_0_0.jar"

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
cp "$ROOT"/src/resources/chart.umd.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/CHARTJS-LICENSE.md "$CLASSES/resources/"
cp "$ROOT"/src/resources/leaflet.css "$CLASSES/resources/"
cp "$ROOT"/src/resources/leaflet.js "$CLASSES/resources/"
cp "$ROOT"/src/resources/LEAFLET-LICENSE.txt "$CLASSES/resources/"
cp "$ROOT"/src/resources/noto-sans-arabic-arabic-400-normal.woff2 "$CLASSES/resources/"
cp "$ROOT"/src/resources/noto-sans-arabic-arabic-700-normal.woff2 "$CLASSES/resources/"
cp "$ROOT"/src/resources/NOTO-SANS-ARABIC-LICENSE.txt "$CLASSES/resources/"
cp "$ROOT"/src/resources/world50.tsv "$CLASSES/resources/"
cp "$ROOT"/src/resources/country_aliases.tsv "$CLASSES/resources/"

java -m jdk.jartool/sun.tools.jar.Main \
  cfm "$GENERIC_JAR" "$ROOT/MANIFEST.MF" \
  -C "$CLASSES" .

# Keep the conventional CLI filename and a release-specific Stata-plugin
# filename byte-for-byte identical.  The latter prevents an obsolete generic
# JAR earlier on the ado-path from shadowing the installed plugin.
cp -p "$GENERIC_JAR" "$STATA_JAR"

echo "Built $GENERIC_JAR"
echo "Built $STATA_JAR"
