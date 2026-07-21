#!/usr/bin/env bash
set -euo pipefail

# Build clean source and SSC-ready archives without copying retired artifacts
# that may still exist in a developer's working directory.
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="2.1.3"
OUTPUT="${1:-$ROOT/../release}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/surveye-release.XXXXXX")"
SOURCE_NAME="surveye-$VERSION"
GITHUB_NAME="surveye-$VERSION-github"
SSC_NAME="surveye-$VERSION-ssc"

cleanup() {
  find "$WORK" -mindepth 1 -delete 2>/dev/null || true
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

"$ROOT/build.sh"
"$ROOT/tests/check_stata_source.sh"
"$ROOT/tests/check_package.sh"

mkdir -p "$OUTPUT" "$WORK/$SOURCE_NAME" "$WORK/$GITHUB_NAME" "$WORK/$SSC_NAME"

rsync -a \
  --exclude '/build/' \
  --exclude '/release/' \
  --exclude '/tests/node_modules/' \
  --exclude '/tests/qa-output/' \
  --exclude '/tests/design-output/' \
  --exclude 'suso_dashboard*' \
  --exclude 'surveydash*' \
  --exclude '/surveye_*.jar' \
  --exclude '*.class' \
  "$ROOT/" "$WORK/$SOURCE_NAME/"

# Keep exactly the current release-specific JAR even when an older versioned
# JAR remains in a developer's working tree for local compatibility testing.
cp -p "$ROOT/surveye_2_1_3.jar" "$WORK/$SOURCE_NAME/"

# GitHub accepts the same clean source tree, but the upload archive must be
# flat so README.md, stata.toc, and surveye.pkg land at the repository root.
rsync -a "$WORK/$SOURCE_NAME/" "$WORK/$GITHUB_NAME/"

cp -p "$ROOT/stata.toc" "$ROOT/surveye.pkg" "$WORK/$SSC_NAME/"
while IFS= read -r relative; do
  [ -n "$relative" ] || continue
  mkdir -p "$WORK/$SSC_NAME/$(dirname "$relative")"
  cp -p "$ROOT/$relative" "$WORK/$SSC_NAME/$relative"
done < <(awk '$1 == "f" || $1 == "F" { print $2 }' "$ROOT/surveye.pkg")

(
  cd "$WORK"
  zip -X -qr "$SOURCE_NAME.zip" "$SOURCE_NAME"
)
(
  cd "$WORK/$GITHUB_NAME"
  zip -X -qr "$WORK/$GITHUB_NAME.zip" .
)
(
  # SSC submission archives are flat: stata.toc, the package descriptor, and
  # the files named by that descriptor live at the archive root.
  cd "$WORK/$SSC_NAME"
  zip -X -qr "$WORK/$SSC_NAME.zip" .
)

if unzip -Z1 "$WORK/$SOURCE_NAME.zip" | grep -Eiq '(^|/)(suso_dashboard|surveydash)[^/]*$'; then
  echo "FAIL: retired installable filename entered the source release" >&2
  exit 1
fi
if unzip -Z1 "$WORK/$GITHUB_NAME.zip" | grep -Eiq '(^|/)(suso_dashboard|surveydash)'; then
  echo "FAIL: retired installable filename entered the GitHub release" >&2
  exit 1
fi
if unzip -Z1 "$WORK/$SSC_NAME.zip" | grep -Eiq '(^|/)(suso_dashboard|surveydash)'; then
  echo "FAIL: retired installable filename entered the SSC release" >&2
  exit 1
fi

for required in README.md stata.toc surveye.pkg surveye.ado surveye.sthlp \
  surveye.jar surveye_2_1_3.jar example.do LICENSE \
  THIRDPARTY-LICENSES.md CHANGELOG.md; do
  if ! unzip -Z1 "$WORK/$GITHUB_NAME.zip" | grep -Fxq "$required"; then
    echo "FAIL: GitHub archive is missing root file $required" >&2
    exit 1
  fi
done

EXPECTED="$WORK/ssc-expected.txt"
ACTUAL="$WORK/ssc-actual.txt"
{
  printf '%s\n' stata.toc surveye.pkg
  awk '$1 == "f" || $1 == "F" { print $2 }' "$ROOT/surveye.pkg"
} | LC_ALL=C sort -u > "$EXPECTED"
unzip -Z1 "$WORK/$SSC_NAME.zip" \
  | sed '/\/$/d' \
  | sed 's#^\./##' \
  | LC_ALL=C sort -u > "$ACTUAL"
if ! diff -u "$EXPECTED" "$ACTUAL"; then
  echo "FAIL: SSC archive inventory does not match surveye.pkg" >&2
  exit 1
fi

# Publish only archives that passed all checks, then retain matching,
# inspectable staging directories beside them.
mv -f "$WORK/$SOURCE_NAME.zip" "$OUTPUT/$SOURCE_NAME.zip"
mv -f "$WORK/$GITHUB_NAME.zip" "$OUTPUT/$GITHUB_NAME.zip"
mv -f "$WORK/$SSC_NAME.zip" "$OUTPUT/$SSC_NAME.zip"
rsync -a --delete "$WORK/$SOURCE_NAME/" "$OUTPUT/$SOURCE_NAME/"
rsync -a --delete "$WORK/$GITHUB_NAME/" "$OUTPUT/$GITHUB_NAME/"
rsync -a --delete "$WORK/$SSC_NAME/" "$OUTPUT/$SSC_NAME/"

echo "Created $OUTPUT/$SOURCE_NAME.zip"
echo "Created $OUTPUT/$GITHUB_NAME.zip"
echo "Created $OUTPUT/$SSC_NAME.zip"
sha256sum "$OUTPUT/$SOURCE_NAME.zip" "$OUTPUT/$GITHUB_NAME.zip" \
  "$OUTPUT/$SSC_NAME.zip"
