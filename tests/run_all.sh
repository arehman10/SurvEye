#!/usr/bin/env sh
# Portable release gate. Run bash ./build.sh first. Optional jsdom interaction,
# real-browser, and licensed-Stata checks remain explicit, separate gates and
# are never reported as passed here. See README.md for their commands.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh "$ROOT/tests/check_stata_source.sh"
sh "$ROOT/tests/check_package.sh"
node "$ROOT/tests/test_statistics.js"
sh "$ROOT/tests/run_parser_tests.sh"
sh "$ROOT/tests/run_engine_smoke.sh"
sh "$ROOT/tests/run_boundary_tests.sh"
sh "$ROOT/tests/run_surveycto_tests.sh"
sh "$ROOT/tests/test_appearance.sh"
echo "PASS portable release gate (optional DOM, browser, and licensed-Stata QA are separate)"
