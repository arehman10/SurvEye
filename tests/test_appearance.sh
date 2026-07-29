#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp "${TMPDIR:-/tmp}/surveye-appearance.XXXXXX.html")
trap 'rm -f "$TMP"' EXIT HUP INT TERM

cat > "$TMP" <<'HTML'
<!doctype html>
<html><head><meta charset="utf-8"><style>body{margin:0}</style></head>
<body data-theme="worldbank"><main class="wrap"><section class="hero"><h1>Preview</h1></section><section class="story"><div class="panel">Panel</div></section></main></body></html>
HTML

java -cp "$ROOT/surveye.jar" org.worldbank.surveye.AppearancePlugin \
  "$TMP" editorial glow editorial rounded soft subtle 1280

grep -Fq 'data-surveye-finish="editorial"' "$TMP"
grep -Fq 'data-surveye-background="glow"' "$TMP"
grep -Fq 'data-surveye-page-width="1280"' "$TMP"
grep -Fq 'id="surveye-appearance-overrides"' "$TMP"
grep -Fq '.brandbar,.topline{display:none!important}' "$TMP"
grep -Fq 'id="surveye-appearance-motion"' "$TMP"
grep -Fq 'radial-gradient(circle at 12% -8%' "$TMP"
grep -Fq '.wrap{max-width:1280px}' "$TMP"
grep -Fq '@media (prefers-reduced-motion:reduce)' "$TMP"

# Applying the finish twice must replace the prior block rather than duplicate it.
java -cp "$ROOT/surveye.jar" org.worldbank.surveye.AppearancePlugin \
  "$TMP" clean auto auto auto auto none 0
[ "$(grep -Fc 'id="surveye-appearance-overrides"' "$TMP")" -eq 1 ]
[ "$(grep -Fc 'id="surveye-appearance-motion"' "$TMP" || true)" -eq 0 ]
grep -Fq 'data-surveye-finish="clean"' "$TMP"
grep -Fq 'data-surveye-background="auto"' "$TMP"

echo "Appearance tests passed."
