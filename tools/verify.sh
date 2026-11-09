#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -x ./gradlew ]]; then
  echo "ERROR: Gradle wrapper is missing or not executable" >&2
  exit 2
fi

TASKS="$(./gradlew :app:tasks --all --console=plain)"
has_task() { grep -Eq "^[[:space:]]*$1([[:space:]]|$)" <<<"$TASKS"; }

RUN=()
for task in compileDebugJavaWithJavac compileDebugKotlin; do
  if has_task "$task"; then RUN+=(":app:$task"); fi
done
for task in testDebugUnitTest lintDebug assembleDebug; do
  if has_task "$task"; then RUN+=(":app:$task"); fi
done

if ((${#RUN[@]} == 0)); then
  echo "ERROR: No applicable verification tasks were found" >&2
  exit 3
fi
printf 'Running verified Gradle tasks: %s\n' "${RUN[*]}"
./gradlew "${RUN[@]}" --console=plain
