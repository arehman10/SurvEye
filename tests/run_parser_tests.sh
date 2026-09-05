#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
FIXTURES=${1:-"$ROOT/tests/fixtures"}
EXPECTED=${2:-"$ROOT/tests/parser_expected.tsv"}
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/surveye-parser.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT HUP INT TERM

if [ ! -d "$FIXTURES" ]; then
  echo "Fixture directory not found: $FIXTURES" >&2
  echo "Usage: tests/run_parser_tests.sh [fixture-directory [expected-counts.tsv]]" >&2
  exit 2
fi

java -m jdk.compiler/com.sun.tools.javac.Main --release 8 \
  -d "$BUILD" \
  "$ROOT"/src/*.java \
  "$ROOT"/tests/HtmlQuestionnaireParserTest.java

java -cp "$BUILD" HtmlQuestionnaireParserTest \
  "$EXPECTED" \
  "$FIXTURES"
