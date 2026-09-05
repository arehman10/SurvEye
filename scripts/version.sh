#!/usr/bin/env sh
# Source after setting ROOT. VERSION is authoritative; generated/static
# consumer metadata is checked against it by tests/check_package.sh.
SURVEYE_VERSION=$(cat "$ROOT/VERSION")
case "$SURVEYE_VERSION" in
  ''|*[!0-9.]*|.*|*.) echo "Invalid SurvEye VERSION: $SURVEYE_VERSION" >&2; exit 1 ;;
esac
if ! printf '%s\n' "$SURVEYE_VERSION" | grep -Eq '^[0-9]+[.][0-9]+[.][0-9]+$'; then
  echo "VERSION must contain one major.minor.patch version." >&2
  exit 1
fi
SURVEYE_RELEASE_JAR="surveye_$(printf '%s' "$SURVEYE_VERSION" | tr . _).jar"
