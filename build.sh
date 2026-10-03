#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug "$@"
else
  echo "Gradle not found. Open this folder in Android Studio or use the included GitHub Actions workflow."
  exit 1
fi
