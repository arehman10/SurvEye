#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST="$ROOT/build/boundary-tests"
mkdir -p "$DEST"
javac --release 8 -encoding UTF-8 -cp "$ROOT/surveye.jar" -d "$DEST" "$ROOT/tests/BoundaryArchiveRegressionTest.java"
java -cp "$ROOT/surveye.jar:$DEST" BoundaryArchiveRegressionTest
